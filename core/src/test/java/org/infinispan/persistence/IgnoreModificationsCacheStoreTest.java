package org.infinispan.persistence;

import org.infinispan.commons.marshall.StreamingMarshaller;
import org.infinispan.configuration.cache.ConfigurationBuilder;
import org.infinispan.persistence.dummy.DummyInMemoryStoreConfigurationBuilder;
import org.infinispan.persistence.CacheLoaderException;
import org.infinispan.persistence.MarshalledEntryImpl;
import org.infinispan.persistence.dummy.DummyInMemoryStoreConfigurationBuilder;
import org.infinispan.persistence.manager.PersistenceManager;
import org.infinispan.persistence.spi.AdvancedLoadWriteStore;
import org.infinispan.manager.EmbeddedCacheManager;
import org.infinispan.test.SingleCacheManagerTest;
import org.infinispan.test.TestingUtil;
import org.infinispan.test.fwk.CleanupAfterMethod;
import org.infinispan.test.fwk.TestCacheManagerFactory;
import org.testng.annotations.Test;

/**
 * Test read only store,
 * i.e. test proper functionality of setting ignoreModifications(true) for cache store.
 *
 * @author Tomas Sykora
 */
@Test(testName = "persistence.IgnoreModificationsCacheLoaderTest", groups = "functional", sequential = true)
@CleanupAfterMethod
public class IgnoreModificationsCacheStoreTest extends SingleCacheManagerTest {
   AdvancedLoadWriteStore store;
   private StreamingMarshaller marshaller;

   @Override
   protected EmbeddedCacheManager createCacheManager() throws Exception {
      ConfigurationBuilder cfg = getDefaultStandaloneCacheConfig(true);
      cfg
         .invocationBatching().enable()
         .persistence()
            .addStore(DummyInMemoryStoreConfigurationBuilder.class)
               .ignoreModifications(true);

      return TestCacheManagerFactory.createCacheManager(cfg);
   }

   @Override
   protected void setup() throws Exception {
      super.setup();
      store = (AdvancedLoadWriteStore) TestingUtil.getFirstLoader(cache);
      marshaller = cache.getAdvancedCache().getComponentRegistry().getCacheMarshaller();
   }

   public void testReadOnlyCacheStore() throws CacheLoaderException {

      PersistenceManager pm = cache.getAdvancedCache().getComponentRegistry().getComponent(PersistenceManager.class);
      // ignore modifications
      pm.writeToAllStores(new MarshalledEntryImpl("k1", "v1", null, marshaller), false);
      pm.writeToAllStores(new MarshalledEntryImpl("k2", "v2", null, marshaller), false);


      assert !store.contains("k1") : "READ ONLY - Store should NOT contain k1 key.";
      assert !store.contains("k2") : "READ ONLY - Store should NOT contain k2 key.";

      // put into cache but not into read only store
      cache.put("k1", "v1");
      cache.put("k2", "v2");
      assert "v1".equals(cache.get("k1"));
      assert "v2".equals(cache.get("k2"));

      assert !store.contains("k1") : "READ ONLY - Store should NOT contain k1 key.";
      assert !store.contains("k2") : "READ ONLY - Store should NOT contain k2 key.";

      assert !pm.deleteFromAllStores("k1", false) : "READ ONLY - Remove operation should return false (no op)";
      assert !pm.deleteFromAllStores("k2", false) : "READ ONLY - Remove operation should return false (no op)";
      assert !pm.deleteFromAllStores("k3", false) : "READ ONLY - Remove operation should return false (no op)";

      assert "v1".equals(cache.get("k1"));
      assert "v2".equals(cache.get("k2"));
      cache.remove("k1");
      cache.remove("k2");
      assert cache.get("k1") == null;
      assert cache.get("k2") == null;
   }
}
