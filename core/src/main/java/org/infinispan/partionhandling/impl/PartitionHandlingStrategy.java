package org.infinispan.partionhandling.impl;

/**
 * On membership changes, implementations decide whether to enter degraded mode or to rebalance.
 *
 * @author Mircea Markus
 * @since 7.0
 */
public interface PartitionHandlingStrategy {

   /**
    * Implementations might query the PartitionContext in order to determine if this is the primary partition, based on
    * quorum and mark the partition unavailable/readonly.
    */
   void onMembershipChanged(PartitionContext pc);
}

