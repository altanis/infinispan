package org.infinispan.topology;


import org.infinispan.commands.ReplicableCommand;
import org.infinispan.commons.CacheException;
import org.infinispan.commons.util.CollectionFactory;
import org.infinispan.configuration.global.GlobalConfiguration;
import org.infinispan.distribution.ch.ConsistentHash;
import org.infinispan.distribution.ch.ConsistentHashFactory;
import org.infinispan.factories.GlobalComponentRegistry;
import org.infinispan.factories.annotations.ComponentName;
import org.infinispan.factories.annotations.Inject;
import org.infinispan.factories.annotations.Start;
import org.infinispan.factories.annotations.Stop;
import org.infinispan.notifications.Listener;
import org.infinispan.notifications.cachemanagerlistener.CacheManagerNotifier;
import org.infinispan.notifications.cachemanagerlistener.annotation.Merged;
import org.infinispan.notifications.cachemanagerlistener.annotation.ViewChanged;
import org.infinispan.notifications.cachemanagerlistener.event.ViewChangedEvent;
import org.infinispan.remoting.responses.ExceptionResponse;
import org.infinispan.remoting.responses.Response;
import org.infinispan.remoting.responses.SuccessfulResponse;
import org.infinispan.remoting.rpc.ResponseMode;
import org.infinispan.remoting.transport.Address;
import org.infinispan.remoting.transport.Transport;
import org.infinispan.util.logging.Log;
import org.infinispan.util.logging.LogFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.infinispan.util.logging.LogFactory.CLUSTER;
import static org.infinispan.factories.KnownComponentNames.ASYNC_TRANSPORT_EXECUTOR;

/**
 * The {@code ClusterTopologyManager} implementation.
 *
 * @author Dan Berindei
 * @author Pedro Ruivo
 * @since 5.2
 */
public class ClusterTopologyManagerImpl implements ClusterTopologyManager {
   private static Log log = LogFactory.getLog(ClusterTopologyManagerImpl.class);

   private Transport transport;
   private RebalancePolicy rebalancePolicy;
   private GlobalConfiguration globalConfiguration;
   private GlobalComponentRegistry gcr;
   private CacheManagerNotifier cacheManagerNotifier;
   private ExecutorService asyncTransportExecutor;
   private volatile boolean isCoordinator;
   private volatile boolean isShuttingDown;
   private volatile int viewId = -1;
   private final Object viewUpdateLock = new Object();
   private final Object viewHandlingLock = new Object();


   private final ConcurrentMap<String, ClusterCacheStatus> cacheStatusMap = CollectionFactory.makeConcurrentMap();
   private ClusterViewListener viewListener;

   @Inject
   public void inject(Transport transport, RebalancePolicy rebalancePolicy,
                      @ComponentName(ASYNC_TRANSPORT_EXECUTOR) ExecutorService asyncTransportExecutor,
                      GlobalConfiguration globalConfiguration, GlobalComponentRegistry gcr,
                      CacheManagerNotifier cacheManagerNotifier) {
      this.transport = transport;
      this.rebalancePolicy = rebalancePolicy;
      this.asyncTransportExecutor = asyncTransportExecutor;
      this.globalConfiguration = globalConfiguration;
      this.gcr = gcr;
      this.cacheManagerNotifier = cacheManagerNotifier;
   }

   @Start(priority = 100)
   public void start() {
      isShuttingDown = false;
      isCoordinator = transport.isCoordinator();

      viewListener = new ClusterViewListener();
      cacheManagerNotifier.addListener(viewListener);
      // The listener already missed the initial view
      handleNewView(false, transport.getViewId());
   }

   @Stop(priority = 100)
   public void stop() {
      isShuttingDown = true;
      cacheManagerNotifier.removeListener(viewListener);

      // Stop blocking cache topology commands.
      // The synchronization also ensures that the listener has finished executing
      // so we don't get InterruptedExceptions when the notification thread pool shuts down
      synchronized (viewUpdateLock) {
         viewId = Integer.MAX_VALUE;
         viewUpdateLock.notifyAll();
      }
   }

   @Override
   public void triggerRebalance(final String cacheName) {
      asyncTransportExecutor.submit(new Callable<Object>() {
         @Override
         public Object call() throws Exception {
            try {
               startRebalance(cacheName);
               return null;
            } catch (Throwable t) {
               log.rebalanceStartError(cacheName, t);
               throw new Exception(t);
            }
         }
      });
   }

   @Override
   public CacheTopology handleJoin(String cacheName, Address joiner, CacheJoinInfo joinInfo, int viewId) throws Exception {
      waitForView(viewId);
      if (isShuttingDown) {
         log.debugf("Ignoring join request from %s for cache %s, the local cache manager is shutting down",
               joiner, cacheName);
         return null;
      }

      ClusterCacheStatus cacheStatus = initCacheStatusIfAbsent(cacheName, joinInfo);
      boolean isFirstMember;
      CacheTopology topologyWithoutRebalance;
      synchronized (cacheStatus) {
         isFirstMember = cacheStatus.getCacheTopology().getMembers().isEmpty();
         cacheStatus.addMember(joiner, joinInfo.getCapacityFactor());
         if (isFirstMember) {
            // This node was the first to join. We need to install the initial CH
            int newTopologyId = cacheStatus.getCacheTopology().getTopologyId() + 1;
            List<Address> initialMembers = cacheStatus.getMembers();
            ConsistentHash initialCH = joinInfo.getConsistentHashFactory().create(
                  joinInfo.getHashFunction(), joinInfo.getNumOwners(), joinInfo.getNumSegments(),
                  initialMembers, cacheStatus.getCapacityFactors());
            CacheTopology initialTopology = new CacheTopology(newTopologyId, initialCH, null);
            cacheStatus.updateCacheTopology(initialTopology);
            // Don't need to broadcast the initial CH, just return the cache topology to the joiner
         } else {
            // Do nothing. The rebalance policy will trigger a rebalance later.
         }
         topologyWithoutRebalance = cacheStatus.getCacheTopology();
      }
      if (isFirstMember) {
         rebalancePolicy.initCache(cacheName, cacheStatus);
      } else {
         rebalancePolicy.updateCacheStatus(cacheName, cacheStatus);
      }

      return topologyWithoutRebalance;
   }

   @Override
   public void handleLeave(String cacheName, Address leaver, int viewId) throws Exception {
      if (isShuttingDown) {
         log.debugf("Ignoring leave request from %s for cache %s, the local cache manager is shutting down",
               leaver, cacheName);
         return;
      }

      ClusterCacheStatus cacheStatus = cacheStatusMap.get(cacheName);
      if (cacheStatus == null) {
         // This can happen if we've just become coordinator
         log.tracef("Ignoring leave request from %s for cache %s because it doesn't have a cache status entry");
         return;
      }
      boolean actualLeaver = cacheStatus.removeMember(leaver);
      if (!actualLeaver)
         return;

      cacheStatus.updateTopologyAndBroadcastChUpdate();
   }

   @Override
   public void handleRebalanceCompleted(String cacheName, Address node, int topologyId, Throwable throwable, int viewId) throws Exception {
      if (throwable != null) {
         // TODO We could try to update the pending CH such that nodes reporting errors are not considered to hold any state
         // For now we are just logging the error and proceeding as if the rebalance was successful everywhere
         log.rebalanceError(cacheName, node, throwable);
      }
      log.debugf("Finished local rebalance for cache %s on node %s, topology id = %d", cacheName, node,
            topologyId);
      ClusterCacheStatus cacheStatus = cacheStatusMap.get(cacheName);
      if (cacheStatus == null || !cacheStatus.isRebalanceInProgress()) {
         throw new CacheException(String.format("Received invalid rebalance confirmation from %s " +
               "for cache %s, we don't have a rebalance in progress", node, cacheName));
      }

      boolean rebalanceCompleted = cacheStatus.confirmRebalanceOnNode(node, topologyId);
      if (rebalanceCompleted) {
         cacheStatus.endRebalance();
         cacheStatus.broadcastConsistentHashUpdate();
         rebalancePolicy.updateCacheStatus(cacheName, cacheStatus);
      }
   }

   protected void handleNewView(boolean mergeView, int newViewId) {
      synchronized (viewHandlingLock) {
         // check to ensure this is not an older view
         if (newViewId <= viewId) {
            log.tracef("Ignoring old cluster view notification: %s", newViewId);
            return;
         }

         boolean becameCoordinator = !isCoordinator && transport.isCoordinator();
         isCoordinator = transport.isCoordinator();
         log.tracef("Received new cluster view: %s, isCoordinator = %s, becameCoordinator = %s", newViewId,
               isCoordinator, becameCoordinator);

         if ((isCoordinator && mergeView) || becameCoordinator) {
            try {
               Map<String, List<CacheTopology>> clusterCacheMap = recoverClusterStatus(newViewId);

               for (Map.Entry<String, List<CacheTopology>> entry : clusterCacheMap.entrySet()) {
                  String cacheName = entry.getKey();
                  List<CacheTopology> topologyList = entry.getValue();
                  ClusterCacheStatus cacheStatus = cacheStatusMap.get(cacheName);
                  if (isCoordinator && mergeView) {
                     log.tracef("Initializing rebalance policy for cache %s, pre-existing partitions are %s",
                                cacheName, topologyList);
                     cacheStatus.reconcileCacheTopology(transport.getMembers(), topologyList, mergeView);
                  } else {
                     cacheStatus.reconcileCacheTopologyWhenBecomingCoordinator(transport.getMembers(), topologyList, mergeView);
                  }
               }
            } catch (InterruptedException e) {
               log.tracef("Cluster state recovery interrupted because the coordinator is shutting down");
               // the CTMI has already stopped, no need to update the view id or notify waiters
               return;
            } catch (Exception e) {
               // TODO Retry?
               log.failedToRecoverClusterState(e);
            }
         } else if (isCoordinator) {
            try {
               updateClusterMembers(transport.getMembers());
            } catch (Exception e) {
               log.errorUpdatingMembersList(e);
            }
         }

         // update the view id last, so join requests from other nodes wait until we recovered existing members' info
         synchronized (viewUpdateLock) {
            viewId = newViewId;
            viewUpdateLock.notifyAll();
         }
      }
   }

   private ClusterCacheStatus initCacheStatusIfAbsent(String cacheName, CacheJoinInfo joinInfo) {
      ClusterCacheStatus cacheStatus = cacheStatusMap.get(cacheName);
      if (cacheStatus == null) {
         ClusterCacheStatus newCacheStatus = new ClusterCacheStatus(cacheName, joinInfo, transport, globalConfiguration, asyncTransportExecutor, gcr, rebalancePolicy);
         cacheStatus = cacheStatusMap.putIfAbsent(cacheName, newCacheStatus);
         if (cacheStatus == null) {
            cacheStatus = newCacheStatus;
         }
      }
      return cacheStatus;
   }

   private void startRebalance(String cacheName) throws Exception {
      ClusterCacheStatus cacheStatus = cacheStatusMap.get(cacheName);

      synchronized (cacheStatus) {
         CacheTopology cacheTopology = cacheStatus.getCacheTopology();
         if (cacheStatus.isRebalanceInProgress()) {
            log.tracef("Ignoring request to rebalance cache %s, there's already a rebalance in progress: %s",
                  cacheName, cacheTopology);
            return;
         }

         List<Address> newMembers = new ArrayList<Address>(cacheStatus.getMembers());
         if (newMembers.isEmpty()) {
            log.tracef("Ignoring request to rebalance cache %s, it doesn't have any member", cacheName);
            return;
         }

         log.tracef("Rebalancing consistent hash for cache %s, members are %s", cacheName, newMembers);
         int newTopologyId = cacheTopology.getTopologyId() + 1;
         ConsistentHash currentCH = cacheTopology.getCurrentCH();
         if (currentCH == null) {
            // There was one node in the cache before, and it left after the rebalance was triggered
            // but before the rebalance actually started.
            log.tracef("Ignoring request to rebalance cache %s, it doesn't have a consistent hash", cacheName);
            return;
         }
         if (!newMembers.containsAll(currentCH.getMembers())) {
            newMembers.removeAll(currentCH.getMembers());
            log.tracef("Ignoring request to rebalance cache %s, we have new leavers: %s", cacheName, newMembers);
            return;
         }

         ConsistentHashFactory chFactory = cacheStatus.getJoinInfo().getConsistentHashFactory();
         // This update will only add the joiners to the CH, we have already checked that we don't have leavers
         ConsistentHash updatedMembersCH = chFactory.updateMembers(currentCH, newMembers, cacheStatus.getCapacityFactors());
         ConsistentHash balancedCH = chFactory.rebalance(updatedMembersCH);
         if (balancedCH.equals(currentCH)) {
            log.tracef("The balanced CH is the same as the current CH, not rebalancing");
            return;
         }
         CacheTopology newTopology = new CacheTopology(newTopologyId, currentCH, balancedCH, cacheStatus.isMissingData());
         log.tracef("Updating cache %s topology for rebalance: %s", cacheName, newTopology);
         newTopology.logRoutingTableInformation();
         cacheStatus.startRebalance(newTopology);
      }

      rebalancePolicy.updateCacheStatus(cacheName, cacheStatus);
      broadcastRebalanceStart(cacheName, cacheStatus);
   }

   private void broadcastRebalanceStart(String cacheName, ClusterCacheStatus cacheStatus) throws Exception {
      CacheTopology cacheTopology = cacheStatus.getCacheTopology();
      CLUSTER.startRebalance(cacheName, cacheTopology);
      ReplicableCommand command = new CacheTopologyControlCommand(cacheName,
            CacheTopologyControlCommand.Type.REBALANCE_START, transport.getAddress(), cacheTopology,
            transport.getViewId());
      cacheStatus.executeOnClusterAsync(command, getGlobalTimeout());
   }

   private HashMap<String, List<CacheTopology>> recoverClusterStatus(int newViewId) throws Exception {
      ReplicableCommand command = new CacheTopologyControlCommand(null,
            CacheTopologyControlCommand.Type.GET_STATUS, transport.getAddress(), newViewId);
      Map<Address, Object> statusResponses = executeOnClusterSync(command, getGlobalTimeout(), false, false);

      log.debugf("Got statusResponses %s. members are %s", statusResponses, transport.getMembers());

      HashMap<String, List<CacheTopology>> clusterCacheMap = new HashMap<String, List<CacheTopology>>();
      for (Map.Entry<Address, Object> responseEntry : statusResponses.entrySet()) {
         Address sender = responseEntry.getKey();
         Map<String, LocalTopologyManager.StatusResponse> nodeStatus = (Map<String, LocalTopologyManager.StatusResponse>) responseEntry.getValue();
         for (Map.Entry<String, LocalTopologyManager.StatusResponse> statusEntry : nodeStatus.entrySet()) {
            String cacheName = statusEntry.getKey();
            CacheJoinInfo joinInfo = statusEntry.getValue().getCacheJoinInfo();
            CacheTopology cacheTopology = statusEntry.getValue().getCacheTopology();

            List<CacheTopology> topologyList = clusterCacheMap.get(cacheName);
            if (topologyList == null) {
               // This is the first CacheJoinInfo we got for this cache, initialize its ClusterCacheStatus
               initCacheStatusIfAbsent(cacheName, joinInfo);

               topologyList = new ArrayList<CacheTopology>();
               clusterCacheMap.put(cacheName, topologyList);
            }

            // The cache topology could be null if the new node sent a join request to the old coordinator
            // but didn't get a response back yet
            if (cacheTopology != null) {
               topologyList.add(cacheTopology);

               // Add all the members of the topology that have sent responses first
               // If we only added the sender, we could end up with a different member order
               for (Address member : cacheTopology.getMembers()) {
                  if (statusResponses.containsKey(member)) {
                     // Search through all the responses to get the correct capacity factor
                     Map<String, LocalTopologyManager.StatusResponse> memberStatus = (Map<String, LocalTopologyManager.StatusResponse>) statusResponses.get(member);
                     LocalTopologyManager.StatusResponse cacheStatus = memberStatus.get(cacheName);
                     if (cacheStatus != null) {
                        CacheJoinInfo memberJoinInfo = cacheStatus.getCacheJoinInfo();
                        float capacityFactor = memberJoinInfo.getCapacityFactor();
                        cacheStatusMap.get(cacheName).addMember(member, capacityFactor);
                     }
                  }
               }
            }

            // This node may have joined, and still not be in the current or pending CH
            // because the old coordinator didn't manage to start the rebalance before shutting down
            cacheStatusMap.get(cacheName).addMember(sender, joinInfo.getCapacityFactor());
         }
      }
      log.debugf("Recovering running caches in the cluster: %s", clusterCacheMap);
      return clusterCacheMap;
   }

   public void updateClusterMembers(List<Address> newClusterMembers) throws Exception {
      log.tracef("Updating cluster members for all the caches. New list is %s", newClusterMembers);

      for (ClusterCacheStatus e : cacheStatusMap.values()) {
         e.processMembershipChange(newClusterMembers);
      }
   }


   private void waitForView(int viewId) throws InterruptedException {
      if (this.viewId < viewId) {
         log.tracef("Received a cache topology command with a higher view id: %s, our view id is %s", viewId, this.viewId);
      }
      synchronized (viewUpdateLock) {
         while (this.viewId < viewId) {
            // break out of the loop after state transfer timeout expires
            viewUpdateLock.wait(1000);
         }
      }
   }

   private Map<Address, Object> executeOnClusterSync(final ReplicableCommand command, final int timeout,
                                                     boolean totalOrder, boolean distributed)
         throws Exception {
      // first invoke remotely

      if (totalOrder) {
         Map<Address, Response> responseMap = transport.invokeRemotely(transport.getMembers(), command,
                                                                       ResponseMode.SYNCHRONOUS_IGNORE_LEAVERS,
                                                                       timeout, false, null, totalOrder, distributed);
         Map<Address, Object> responseValues = new HashMap<Address, Object>(transport.getMembers().size());
         for (Map.Entry<Address, Response> entry : responseMap.entrySet()) {
            Address address = entry.getKey();
            Response response = entry.getValue();
            if (!response.isSuccessful()) {
               Throwable cause = response instanceof ExceptionResponse ? ((ExceptionResponse) response).getException() : null;
               throw new CacheException("Unsuccessful response received from node " + address + ": " + response, cause);
            }
            responseValues.put(address, ((SuccessfulResponse) response).getResponseValue());
         }
         return responseValues;
      }

      Future<Map<Address, Response>> remoteFuture = asyncTransportExecutor.submit(new Callable<Map<Address, Response>>() {
         @Override
         public Map<Address, Response> call() throws Exception {
            return transport.invokeRemotely(null, command,
                  ResponseMode.SYNCHRONOUS_IGNORE_LEAVERS, timeout, true, null, false, false);
         }
      });

      // invoke the command on the local node
      gcr.wireDependencies(command);
      Response localResponse;
      try {
         if (log.isTraceEnabled()) log.tracef("Attempting to execute command on self: %s", command);
         localResponse = (Response) command.perform(null);
      } catch (Throwable throwable) {
         throw new Exception(throwable);
      }
      if (!localResponse.isSuccessful()) {
         throw new CacheException("Unsuccessful local response");
      }

      // wait for the remote commands to finish
      Map<Address, Response> responseMap = remoteFuture.get(timeout, TimeUnit.MILLISECONDS);

      // parse the responses
      Map<Address, Object> responseValues = new HashMap<Address, Object>(transport.getMembers().size());
      for (Map.Entry<Address, Response> entry : responseMap.entrySet()) {
         Address address = entry.getKey();
         Response response = entry.getValue();
         if (!response.isSuccessful()) {
            Throwable cause = response instanceof ExceptionResponse ? ((ExceptionResponse) response).getException() : null;
            throw new CacheException("Unsuccessful response received from node " + address + ": " + response, cause);
         }
         responseValues.put(address, ((SuccessfulResponse) response).getResponseValue());
      }

      responseValues.put(transport.getAddress(), ((SuccessfulResponse) localResponse).getResponseValue());

      return responseValues;
   }

   private int getGlobalTimeout() {
      // TODO Rename setting to something like globalRpcTimeout
      return (int) globalConfiguration.transport().distributedSyncTimeout();
   }

   @Listener(sync = true)
   public class ClusterViewListener {
      @SuppressWarnings("unused")
      @Merged
      @ViewChanged
      public void handleViewChange(final ViewChangedEvent e) {
         handleNewView(e);
      }
   }

   @Override
   public void handleNewView(final ViewChangedEvent e) {
      // need to recover existing caches asynchronously (in case we just became the coordinator)
      asyncTransportExecutor.submit(new Runnable() {
         @Override
         public void run() {
            log.tracef("handleNewView(oldView='%s', newView='%s', isMerge=%s", e.getOldMembers(), e.getNewMembers(), e.isMergeView());
            handleNewView(e.isMergeView(), e.getViewId());
         }
      });
   }
}