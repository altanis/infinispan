package org.infinispan.query.dsl.embedded;

import org.infinispan.configuration.cache.CacheMode;
import org.infinispan.configuration.cache.ConfigurationBuilder;
import org.testng.annotations.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Verifies the functionality of Query DSL in clustered environment for ISPN directory provider.
 *
 * @author Dan Berindei
 * @since 7.0
 */
@Test(groups = "functional", testName = "query.dsl.embedded.TxClusteredQueryDslConditionsTest")
public class TxClusteredQueryDslConditionsTest extends ClusteredQueryDslConditionsTest {

   @Override
   protected ConfigurationBuilder initialCacheConfiguration() {
      ConfigurationBuilder cfg = getDefaultClusteredCacheConfig(CacheMode.REPL_SYNC, true);
      cfg.transaction().useSynchronization(false);
      return cfg;
   }
}
