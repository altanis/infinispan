package org.infinispan.notifications.cachelistener.cluster;

import org.infinispan.Cache;
import org.infinispan.commands.FlagAffectedCommand;
import org.infinispan.configuration.cache.CacheMode;
import org.infinispan.configuration.cache.ConfigurationBuilder;
import org.infinispan.context.InvocationContext;
import org.infinispan.distribution.MagicKey;
import org.infinispan.filter.CollectionKeyFilter;
import org.infinispan.manager.CacheContainer;
import org.infinispan.metadata.Metadata;
import org.infinispan.filter.Converter;
import org.infinispan.filter.KeyValueFilter;
import org.infinispan.notifications.Listener;
import org.infinispan.notifications.cachelistener.CacheNotifier;
import org.infinispan.notifications.cachelistener.annotation.CacheEntryCreated;
import org.infinispan.notifications.cachelistener.annotation.CacheEntryModified;
import org.infinispan.notifications.cachelistener.annotation.CacheEntryRemoved;
import org.infinispan.notifications.cachelistener.event.CacheEntryEvent;
import org.infinispan.notifications.cachelistener.event.CacheEntryModifiedEvent;
import org.infinispan.notifications.cachelistener.event.Event;
import org.infinispan.filter.KeyFilterAsKeyValueFilter;
import org.infinispan.notifications.cachemanagerlistener.CacheManagerNotifier;
import org.infinispan.remoting.transport.Address;
import org.infinispan.statetransfer.StateProvider;
import org.infinispan.test.MultipleCacheManagersTest;
import org.infinispan.test.TestingUtil;
import org.infinispan.test.fwk.CheckPoint;
import org.infinispan.transaction.TransactionMode;
import org.mockito.AdditionalAnswers;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.testng.annotations.Test;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.anyList;
import static org.mockito.Matchers.anyObject;
import static org.mockito.Mockito.anyBoolean;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.eq;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;

/**
 * Base class to be used for cluster listener tests for both tx and nontx caches
 *
 * @author wburns
 * @since 7.0
 */
@Test(groups = "functional")
public abstract class AbstractClusterListenerTest extends AbstractClusterListenerUtilTest {
   protected AbstractClusterListenerTest(boolean tx, CacheMode cacheMode) {
      super(tx, cacheMode);
   }

   @Test
   public void testCreateFromNonOwnerWithListenerNotOwner() {
      Cache<Object, String> cache0 = cache(0, CACHE_NAME);
      Cache<Object, String> cache1 = cache(1, CACHE_NAME);
      Cache<Object, String> cache2 = cache(2, CACHE_NAME);

      ClusterListener clusterListener = new ClusterListener();
      cache0.addListener(clusterListener);

      MagicKey key = new MagicKey(cache1, cache2);
      verifySimpleInsertion(cache2, key, FIRST_VALUE, null, clusterListener, FIRST_VALUE);
   }

   @Test
   public void testCreateFromNonOwnerWithListenerAsBackupOwner() {
      Cache<Object, String> cache0 = cache(0, CACHE_NAME);
      Cache<Object, String> cache1 = cache(1, CACHE_NAME);
      Cache<Object, String> cache2 = cache(2, CACHE_NAME);

      ClusterListener clusterListener = new ClusterListener();
      cache0.addListener(clusterListener);

      MagicKey key = new MagicKey(cache1, cache0);

      verifySimpleInsertion(cache2, key, FIRST_VALUE, null, clusterListener, FIRST_VALUE);
   }

   @Test
   public void testLocalNodeOwnerAndClusterListener() {
      Cache<Object, String> cache0 = cache(0, CACHE_NAME);

      ClusterListener clusterListener = new ClusterListener();
      cache0.addListener(clusterListener);

      MagicKey key = new MagicKey(cache0);

      verifySimpleInsertion(cache0, key, FIRST_VALUE, null, clusterListener, FIRST_VALUE);
   }

   @Test
   public void testLocalNodeNonOwnerAndClusterListener() {
      Cache<Object, String> cache0 = cache(0, CACHE_NAME);
      Cache<Object, String> cache1 = cache(1, CACHE_NAME);
      Cache<Object, String> cache2 = cache(2, CACHE_NAME);

      ClusterListener clusterListener = new ClusterListener();
      cache0.addListener(clusterListener);

      MagicKey key = new MagicKey(cache1, cache2);

      verifySimpleInsertion(cache0, key, FIRST_VALUE, null, clusterListener, FIRST_VALUE);
   }

   @Test
   public void testSimpleFilterNotOwner() {
      testSimpleFilter(new MagicKey(cache(1, CACHE_NAME), cache(2, CACHE_NAME)));
   }

   @Test
   public void testSimpleFilterLocalOnly() {
      testSimpleFilter(new MagicKey(cache(0, CACHE_NAME)));
   }

   @Test
   public void testMetadataFilterNotOwner() {
      final String keyToFilterOut = "filter-me";
      testFilter(keyToFilterOut, new MagicKey(cache(1, CACHE_NAME), cache(2, CACHE_NAME)), 1000l, new LifespanFilter<Object, String>(100));
   }

   @Test
   public void testMetadataFilterLocalOnly() {
      final String keyToFilterOut = "filter-me";
      testFilter(keyToFilterOut, new MagicKey(cache(0, CACHE_NAME)), 1000l, new LifespanFilter<Object, String>(100));
   }

   protected void testSimpleFilter(Object key) {
      final String keyToFilterOut = "filter-me";
      testFilter(keyToFilterOut, key, null, new KeyFilterAsKeyValueFilter<Object, String>(
            new CollectionKeyFilter(Collections.singleton(key), true)));
   }

   protected void testFilter(Object keyToFilterOut, Object keyToUse, Long lifespan, KeyValueFilter<? super Object, ? super String> filter) {
      Cache<Object, String> cache0 = cache(0, CACHE_NAME);

      ClusterListener clusterListener = new ClusterListener();
      cache0.addListener(clusterListener, filter, null);

      cache0.put(keyToFilterOut, FIRST_VALUE);

      // We should not have gotten the message since it was filtered
      assertEquals(clusterListener.events.size(), 0);

      verifySimpleInsertion(cache0, keyToUse, FIRST_VALUE, lifespan, clusterListener, FIRST_VALUE);
   }

   @Test
   public void testSimpleConverterNotOwner() {
      testSimpleConverter(new MagicKey(cache(1, CACHE_NAME), cache(2, CACHE_NAME)));
   }

   @Test
   public void testSimpleConverterLocalOnly() {
      testSimpleConverter(new MagicKey(cache(0, CACHE_NAME)));
   }

   @Test
   public void testMetadataConverterSuccessNotOwner() {
      long lifespan = 25000;
      LifespanConverter converter = new LifespanConverter(true, 500);
      testConverter(new MagicKey(cache(1, CACHE_NAME), cache(2, CACHE_NAME)), FIRST_VALUE, lifespan, lifespan, converter);
   }

   @Test
   public void testMetadataConverterSuccessLocalOnly() {
      long lifespan = 25000;
      LifespanConverter converter = new LifespanConverter(true, 500);
      testConverter(new MagicKey(cache(0, CACHE_NAME)), FIRST_VALUE, lifespan, lifespan, converter);
   }

   @Test
   public void testMetadataConverterNoPassReturnOriginalNotOwner() {
      long lifespan = 25000;
      LifespanConverter converter = new LifespanConverter(true, Long.MAX_VALUE);
      testConverter(new MagicKey(cache(1, CACHE_NAME), cache(2, CACHE_NAME)), FIRST_VALUE, FIRST_VALUE, lifespan, converter);
   }

   @Test
   public void testMetadataConverterNoPassReturnOriginalLocalOnly() {
      long lifespan = 25000;
      LifespanConverter converter = new LifespanConverter(true, Long.MAX_VALUE);
      testConverter(new MagicKey(cache(0, CACHE_NAME)), FIRST_VALUE, FIRST_VALUE, lifespan, converter);
   }

   @Test
   public void testMetadataConverterNoPassReturnNullNotOwner() {
      long lifespan = 25000;
      LifespanConverter converter = new LifespanConverter(false, Long.MAX_VALUE);
      testConverter(new MagicKey(cache(1, CACHE_NAME), cache(2, CACHE_NAME)), FIRST_VALUE, null, lifespan, converter);
   }

   @Test
   public void testMetadataConverterNoPassReturnNullLocalOnly() {
      long lifespan = 25000;
      LifespanConverter converter = new LifespanConverter(false, Long.MAX_VALUE);
      testConverter(new MagicKey(cache(0, CACHE_NAME)), FIRST_VALUE, null, lifespan, converter);
   }

   protected void testSimpleConverter(Object key) {
      Cache<Object, String> cache0 = cache(0, CACHE_NAME);

      ClusterListener clusterListener = new ClusterListener();
      cache0.addListener(clusterListener, null, new StringTruncator(0, 2));

      verifySimpleInsertion(cache0, key, FIRST_VALUE, null, clusterListener, FIRST_VALUE.substring(0, 2));
   }

   protected <C> void testConverter(Object key, String value, Object resultingValue, Long lifespan,
                                    Converter<Object, ? super String, C> converter) {
      Cache<Object, String> cache0 = cache(0, CACHE_NAME);

      ClusterListener clusterListener = new ClusterListener();
      cache0.addListener(clusterListener, null, converter);

      verifySimpleInsertion(cache0, key, value, lifespan, clusterListener, resultingValue);
   }

   @Test
   public void testClusterListenerNodeGoesDown() {
      Cache<Object, String> cache0 = cache(0, CACHE_NAME);
      Cache<Object, String> cache1 = cache(1, CACHE_NAME);
      Cache<Object, String> cache2 = cache(2, CACHE_NAME);

      ClusterListener clusterListener = new ClusterListener();
      cache0.addListener(clusterListener);

      int cache1ListenerSize = cache1.getAdvancedCache().getListeners().size();
      int cache2ListenerSize = cache2.getAdvancedCache().getListeners().size();

      log.info("Killing node 0 ..");
      TestingUtil.killCacheManagers(manager(0));
      cacheManagers.remove(0);
      log.info("Node 0 killed");

      TestingUtil.blockUntilViewsReceived(60000, false, cache1, cache2);
      TestingUtil.waitForRehashToComplete(cache1, cache2);

      assertEquals(cache1.getAdvancedCache().getListeners().size(), cache1ListenerSize -
            (cacheMode.isDistributed() ? 1 : 0));
      assertEquals(cache2.getAdvancedCache().getListeners().size(), cache2ListenerSize -
            (cacheMode.isDistributed() ? 1 : 0));
   }

   @Test
   public void testNodeComesUpWithClusterListenerAlreadyInstalled() {
      Cache<Object, String> cache0 = cache(0, CACHE_NAME);

      ClusterListener clusterListener = new ClusterListener();
      cache0.addListener(clusterListener);

      log.info("Adding a new node ..");
      addClusterEnabledCacheManager(builderUsed);
      log.info("Added a new node");

      Cache<Object, String> cache3 = cache(3, CACHE_NAME);
      MagicKey key = new MagicKey(cache3);

      verifySimpleInsertion(cache3, key, FIRST_VALUE, null, clusterListener, FIRST_VALUE);
   }

   @Test
   public void testNodeComesUpWithClusterListenerAlreadyInstalledFilterAndConverter() {
      final String keyToFilter = "filter-me";
      Cache<Object, String> cache0 = cache(0, CACHE_NAME);

      ClusterListener clusterListener = new ClusterListener();
      cache0.addListener(clusterListener, new KeyFilterAsKeyValueFilter<Object, String>(
            new CollectionKeyFilter<Object>(Collections.singleton(keyToFilter), true)), new StringTruncator(0, 3));

      log.info("Adding a new node ..");
      addClusterEnabledCacheManager(builderUsed);
      log.info("Added a new node");

      Cache<Object, String> cache3 = cache(3, CACHE_NAME);
      MagicKey key = new MagicKey(cache3);
      cache3.put(key, FIRST_VALUE);

      // Should be filtered
      assertEquals(clusterListener.events.size(), 0);

      verifySimpleInsertion(cache3, keyToFilter, FIRST_VALUE, null, clusterListener, FIRST_VALUE.substring(0, 3));
   }

   @Test
   public void testSimpleClusterListenerRemoved() {
      Cache<Object, String> cache0 = cache(0, CACHE_NAME);
      Cache<Object, String> cache1 = cache(1, CACHE_NAME);
      Cache<Object, String> cache2 = cache(2, CACHE_NAME);

      int initialCache0ListenerSize = cache0.getAdvancedCache().getListeners().size();
      int initialCache1ListenerSize = cache1.getAdvancedCache().getListeners().size();
      int initialCache2ListenerSize = cache2.getAdvancedCache().getListeners().size();

      ClusterListener clusterListener = new ClusterListener();
      cache0.addListener(clusterListener);

      // Adding a cluster listener should add to each node in cluster
      assertEquals(cache0.getAdvancedCache().getListeners().size(), initialCache0ListenerSize + 1);
      assertEquals(cache1.getAdvancedCache().getListeners().size(), initialCache1ListenerSize +
            (cacheMode.isDistributed() ? 1 : 0));
      assertEquals(cache2.getAdvancedCache().getListeners().size(), initialCache2ListenerSize +
            (cacheMode.isDistributed() ? 1 : 0));

      cache0.removeListener(clusterListener);

      assertEquals(cache0.getAdvancedCache().getListeners().size(), initialCache0ListenerSize);
      assertEquals(cache1.getAdvancedCache().getListeners().size(), initialCache1ListenerSize);
      assertEquals(cache2.getAdvancedCache().getListeners().size(), initialCache2ListenerSize);
   }

   @Test
   public void testClusterListenerRemovedWithMultipleInstalledOnSameNode() {
      Cache<Object, String> cache0 = cache(0, CACHE_NAME);
      Cache<Object, String> cache1 = cache(1, CACHE_NAME);
      Cache<Object, String> cache2 = cache(2, CACHE_NAME);

      int initialCache0ListenerSize = cache0.getAdvancedCache().getListeners().size();
      int initialCache1ListenerSize = cache1.getAdvancedCache().getListeners().size();
      int initialCache2ListenerSize = cache2.getAdvancedCache().getListeners().size();

      ClusterListener clusterListener = new ClusterListener();
      cache0.addListener(clusterListener);

      // Adding a cluster listener should add to each node in cluster
      assertEquals(cache0.getAdvancedCache().getListeners().size(), initialCache0ListenerSize + 1);
      assertEquals(cache1.getAdvancedCache().getListeners().size(), initialCache1ListenerSize +
            (cacheMode.isDistributed() ? 1 : 0));
      assertEquals(cache2.getAdvancedCache().getListeners().size(), initialCache2ListenerSize +
            (cacheMode.isDistributed() ? 1 : 0));

      ClusterListener clusterListener2 = new ClusterListener();
      cache0.addListener(clusterListener2);

      // Adding a second cluster listener should add to each node in cluster as well
      assertEquals(cache0.getAdvancedCache().getListeners().size(), initialCache0ListenerSize + 2);
      assertEquals(cache1.getAdvancedCache().getListeners().size(), initialCache1ListenerSize +
            (cacheMode.isDistributed() ? 2 : 0));
      assertEquals(cache2.getAdvancedCache().getListeners().size(), initialCache2ListenerSize +
            (cacheMode.isDistributed() ? 2 : 0));

      MagicKey key = new MagicKey(cache2, cache1);
      cache1.put(key, FIRST_VALUE);

      // Both listeners should have been notified
      assertEquals(clusterListener.events.size(), 1);
      assertEquals(clusterListener2.events.size(), 1);

      verifySimpleInsertionEvents(clusterListener, key, FIRST_VALUE);
      verifySimpleInsertionEvents(clusterListener2, key, FIRST_VALUE);

      cache0.removeListener(clusterListener);

      assertEquals(cache0.getAdvancedCache().getListeners().size(), initialCache0ListenerSize + 1);
      assertEquals(cache1.getAdvancedCache().getListeners().size(), initialCache1ListenerSize +
            (cacheMode.isDistributed() ? 1 : 0));
      assertEquals(cache2.getAdvancedCache().getListeners().size(), initialCache2ListenerSize +
            (cacheMode.isDistributed() ? 1 : 0));

      // Change the value again to make sure other listener is still working properly
      cache2.put(key, SECOND_VALUE);

      assertEquals(clusterListener2.events.size(), 2);

      CacheEntryEvent event = clusterListener2.events.get(1);

      assertEquals(Event.Type.CACHE_ENTRY_MODIFIED, event.getType());
      assertEquals(key, event.getKey());
      assertEquals(SECOND_VALUE, ((CacheEntryModifiedEvent)event).getValue());
   }

   @Test
   public void testMemberLeavesThatClusterListenerNotNotified() {
      Cache<Object, String> cache0 = cache(0, CACHE_NAME);
      Cache<Object, String> cache1 = cache(1, CACHE_NAME);
      Cache<Object, String> cache2 = cache(2, CACHE_NAME);

      Object key = new MagicKey(cache1, cache2);
      cache1.put(key, "some-key");

      final ClusterListener clusterListener = new ClusterListener();
      cache0.addListener(clusterListener);

      log.info("Killing node 1 ..");
      TestingUtil.killCacheManagers(manager(1));
      cacheManagers.remove(1);
      log.info("Node 1 killed");

      TestingUtil.blockUntilViewsReceived(10000, false, cacheManagers);
      TestingUtil.waitForRehashToComplete(caches(CACHE_NAME));

      assertEquals(0, clusterListener.events.size());
   }
}
