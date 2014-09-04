package org.infinispan.cdi.test.event;

import org.infinispan.AdvancedCache;
import org.infinispan.cdi.test.DefaultTestEmbeddedCacheManagerProducer;
import org.infinispan.cdi.test.assertions.ObserverAssertion;
import org.infinispan.commons.equivalence.AnyEquivalence;
import org.infinispan.context.impl.NonTxInvocationContext;
import org.infinispan.notifications.cachelistener.CacheNotifier;
import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.testng.Arquillian;
import org.jboss.shrinkwrap.api.Archive;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.Test;

import javax.inject.Inject;

import static org.infinispan.cdi.test.testutil.Deployments.baseDeployment;

/**
 * Tests that the simple form of configuration works. This test is disabled due to a bug with parameterized events in
 * Weld.
 *
 * @author Pete Muir
 * @see Config
 */
@Test(groups = "functional", testName = "cdi.test.event.CacheEventTest")
public class CacheEventTest extends Arquillian {

   private NonTxInvocationContext invocationContext = new NonTxInvocationContext(AnyEquivalence.getInstance());

   @Inject
   @Cache1
   private AdvancedCache<String, String> cache1;

   @Inject
   @Cache2
   private AdvancedCache<String, String> cache2;

   @Inject
   private Cache1Observers observers1;

   @Inject
   private Cache2Observers observers2;

   @Deployment
   public static Archive<?> deployment() {
      return baseDeployment()
            .addPackage(CacheEventTest.class.getPackage())
            .addClass(DefaultTestEmbeddedCacheManagerProducer.class);
   }

   @AfterMethod
   public void afterMethod() {
      cache1.clear();
      cache2.clear();
      observers1.clear();
   }

   /*
    * Tests checks if started event was send. However in #afterMethod we are clearing all events from observers. We
    * need to ensure, that this test will be invoked firs.
    */
   @Test(priority = Integer.MIN_VALUE + 1)
   public void shouldFireStartedEventOnNewlyStartedCache() throws Exception {
      //then
      ObserverAssertion.assertThat(observers1, Cache1.class).hasProperName("cache1").isStarted();
   }

   public void shouldFireEntryCreatedEventWhenPuttingDataIntoCache() throws Exception {
      //when
      cache1.put("pete", "Edinburgh");

      //then
      ObserverAssertion.assertThat(observers1, Cache1.class).hasEntryCreatedEvent("pete");
   }

   public void shouldFireEntryRemovedEventWhenRemovingDataFromCache() throws Exception {
      //given
      cache1.put("pete", "Edinburgh");

      //when
      cache1.remove("pete");

      //then
      ObserverAssertion.assertThat(observers1, Cache1.class).hasEntryRemovedEvent("pete");
   }

   public void shouldFireEntryActivatedEventWhenLockingData() throws Exception {
      //given
      cache1.put("pete", "Edinburgh");

      CacheNotifier<String, String> cacheNotifier = cache1.getComponentRegistry().getComponent(CacheNotifier.class);

      //when
      cacheNotifier.notifyCacheEntryActivated("pete", "Edinburgh", true, invocationContext, null);

      //then
      ObserverAssertion.assertThat(observers1, Cache1.class).hasEntryActivatedEvent("pete");
   }

//   public void testSmallCache() {
//      // Put something into the cache, ensure it is started
//      cache1.put("pete", "Edinburgh");
//      assertEquals(cache1.get("pete"), "Edinburgh");
//      assertEquals(observers1.getCacheStartedEventCount(), 1);
//      assertEquals(observers1.getCacheStartedEvent().getCacheName(), "cache1");
//      assertEquals(observers1.getCacheEntryCreatedEventCount(), 1);
//      assertEquals(observers1.getCacheEntryCreatedEvent().getKey(), "pete");
//
//      // Check cache isolation for events
//      cache2.put("mircea", "London");
//      assertEquals(cache2.get("mircea"), "London");
//      assertEquals(observers2.getCacheStartedEventCount(), 1);
//      assertEquals(observers2.getCacheStartedEvent().getCacheName(), "cache2");
//
//      // Remove something
//      cache1.remove("pete");
//      assertEquals(observers1.getCacheEntryRemovedEventCount(), 1);
//      assertEquals(observers1.getCacheEntryRemovedEvent().getKey(), "pete");
//      assertEquals(observers1.getCacheEntryRemovedEvent().getValue(), "Edinburgh");
//
//      // Manually stop cache1 to check that we are notified
//      assertEquals(observers1.getCacheStoppedEventCount(), 0);
//      cache1.stop();
//      assertEquals(observers1.getCacheStoppedEventCount(), 1);
//      assertEquals(observers1.getCacheStoppedEvent().getCacheName(), "cache1");
//   }
}
