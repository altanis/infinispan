package org.infinispan.partitionhandling;

import org.infinispan.distribution.MagicKey;
import org.infinispan.partionhandling.impl.PartitionHandlingManager;
import org.infinispan.remoting.transport.Address;
import org.infinispan.test.AbstractInfinispanTest;
import org.infinispan.test.fwk.CleanupAfterMethod;
import org.infinispan.util.logging.Log;
import org.infinispan.util.logging.LogFactory;
import org.testng.annotations.Test;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import static org.testng.Assert.*;

@Test(groups = "functional", testName = "partitionhandling.ThreeWaySplitAndMergeTest")
@CleanupAfterMethod
public class ThreeWaySplitAndMergeTest extends BasePartitionHandlingTest {

   private static Log log = LogFactory.getLog(ThreeWaySplitAndMergeTest.class);

   public void testSplitAndMerge1() throws Exception {
      testSplitAndMerge(new PartitionDescriptor(0, 1), new PartitionDescriptor(2), new PartitionDescriptor(3));
   }

   public void testSplitAndMerge2() throws Exception {
      testSplitAndMerge(new PartitionDescriptor(1, 2), new PartitionDescriptor(0), new PartitionDescriptor(3));
   }

   public void testSplitAndMerge3() throws Exception {
      testSplitAndMerge(new PartitionDescriptor(2, 3), new PartitionDescriptor(0), new PartitionDescriptor(1));
   }

   public void testSplitAndMerge4() throws Exception {
      testSplitAndMerge(new PartitionDescriptor(2, 3), new PartitionDescriptor(1), new PartitionDescriptor(0));
   }

   private void testSplitAndMerge(PartitionDescriptor p0, PartitionDescriptor p1, PartitionDescriptor p2) throws Exception {
      Object k0 = new MagicKey(cache(p0.node(0)), cache(p0.node(1)));
      cache(0).put(k0, 0);

      Object k1 = new MagicKey(cache(p0.node(1)), cache(p1.node(0)));
      cache(1).put(k1, 1);

      Object k2 = new MagicKey(cache(p1.node(0)), cache(p2.node(0)));
      cache(2).put(k2, 2);

      Object k3 = new MagicKey(cache(p2.node(0)), cache(p0.node(0)));
      cache(3).put(k3, 3);

      log.trace("Before first split.");
      splitCluster(p0.getNodes(), p1.getNodes(), p2.getNodes());

      eventually(new AbstractInfinispanTest.Condition() {
         @Override
         public boolean isSatisfied() throws Exception {
            for (int i = 0; i < 4; i++)
               if (partitionHandlingManager(0).getState() != PartitionHandlingManager.PartitionState.DEGRADED_MODE)
                  return false;
            return true;
         }
      });

      //1. check key visibility in partition 0
      partition(0).assertKeyAvailableForRead(k0, 0);
      partition(0).assertKeysNotAvailable(k1, k2, k3);

      //2. check key visibility in partition 1
      partition(1).assertKeysNotAvailable(k0, k1, k2, k3);

      //3. check key visibility in partition 2
      partition(2).assertKeysNotAvailable(k0, k1, k2, k3);

      //4. check key ownership
      assertTrue(dataContainer(p0.node(0)).containsKey(k0));
      assertFalse(dataContainer(p0.node(0)).containsKey(k1));
      assertFalse(dataContainer(p0.node(0)).containsKey(k2));
      assertTrue(dataContainer(p0.node(0)).containsKey(k3));

      assertTrue(dataContainer(p0.node(1)).containsKey(k0));
      assertTrue(dataContainer(p0.node(1)).containsKey(k1));
      assertFalse(dataContainer(p0.node(1)).containsKey(k2));
      assertFalse(dataContainer(p0.node(1)).containsKey(k3));

      assertFalse(dataContainer(p1.node(0)).containsKey(k0));
      assertTrue(dataContainer(p1.node(0)).containsKey(k1));
      assertTrue(dataContainer(p1.node(0)).containsKey(k2));
      assertFalse(dataContainer(p1.node(0)).containsKey(k3));

      assertFalse(dataContainer(p2.node(0)).containsKey(k0));
      assertFalse(dataContainer(p2.node(0)).containsKey(k1));
      assertTrue(dataContainer(p2.node(0)).containsKey(k2));
      assertTrue(dataContainer(p2.node(0)).containsKey(k3));


      //5. check writes on partition 0
      partition(0).assertKeyAvailableForWrite(k0, -1);

      //5. check writes on partition 1
      partition(1).assertKeysNotAvailableForWrite(k0, k1, k2);

      //6. check writes on partition 2
      partition(2).assertKeysNotAvailableForWrite(k0, k1, k2);


      log.tracef("before_merge p0=%s, p1=%s", partition(0), partition(1));
      assertEquals(partitions.length, 3);
      partition(0).merge(partition(1));
      assertEquals(partitions.length, 2);

      partition(0).expectPartitionState(PartitionHandlingManager.PartitionState.AVAILABLE);
      partition(1).expectPartitionState(PartitionHandlingManager.PartitionState.DEGRADED_MODE);

      partition(0).assertKeyAvailableForRead(k0, -1);
      partition(0).assertKeyAvailableForRead(k1, 1);
      partition(0).assertKeyAvailableForRead(k2, 2);
      partition(0).assertKeyAvailableForRead(k3, 3);

      partition(0).assertKeyAvailableForWrite(k0, 10);
      partition(0).assertKeyAvailableForWrite(k1, 11);
      partition(0).assertKeyAvailableForWrite(k2, 12);
      partition(0).assertKeyAvailableForWrite(k3, 13);

      Set<Address> members = new HashSet<>(Arrays.asList(new Address[]{address(p0.node(0)), address(p0.node(1)), address(p1.node(0))}));
      assertEquals(new HashSet<>(advancedCache(p0.node(0)).getDistributionManager().getConsistentHash().getMembers()), members);
      assertEquals(new HashSet<>(advancedCache(p0.node(1)).getDistributionManager().getConsistentHash().getMembers()), members);
      assertEquals(new HashSet<>(advancedCache(p1.node(0)).getDistributionManager().getConsistentHash().getMembers()), members);

      partition(1).assertKeysNotAvailable(k0, k1, k2, k3);

      members = new HashSet<>(Arrays.asList(new Address[]{address(0), address(1), address(2), address(3)}));
      assertEquals(new HashSet<>(advancedCache(p2.node(0)).getDistributionManager().getConsistentHash().getMembers()), members);

      for (int i = 0; i < 100; i++)
         dataContainer(p2.node(0)).put(i, i, null);

      log.trace("Before the 2nd partition.");
      log.tracef("P0=%s, P1 = %s", partition(0), partition(1));
      partition(0).merge(partition(1));
      log.tracef("After P0=%s", partition(0));


      assertEquals(partitions.length, 1);
      partition(0).expectPartitionState(PartitionHandlingManager.PartitionState.AVAILABLE);

      partition(0).assertKeyAvailableForRead(k0, 10);
      partition(0).assertKeyAvailableForRead(k1, 11);
      partition(0).assertKeyAvailableForRead(k2, 12);
      partition(0).assertKeyAvailableForRead(k3, 13);

      for (int i = 0; i < 10000; i++) {
         partition(0).assertKeyAvailableForRead(i, null);
      }


      cache(0).put(k0, 10);
      cache(1).put(k1, 100);
      cache(2).put(k2, 1000);
      cache(3).put(k3, 10000);

      assertExpectedValue(10, k0);
      assertExpectedValue(100, k1);
      assertExpectedValue(1000, k2);
      assertExpectedValue(10000, k3);
   }

}
