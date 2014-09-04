package org.infinispan.cdi.test.assertions;

import org.infinispan.cdi.test.event.Cache1Observers;
import org.infinispan.notifications.cachelistener.event.CacheEntryActivatedEvent;
import org.infinispan.notifications.cachelistener.event.CacheEntryCreatedEvent;
import org.infinispan.notifications.cachelistener.event.CacheEntryRemovedEvent;
import org.infinispan.notifications.cachemanagerlistener.event.CacheStartedEvent;

import java.util.List;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

/**
 * Observer custom assertion.
 *
 * @author Sebastian Laskawiec
 */
public class ObserverAssertion {

   private Cache1Observers observer;
   private Class<?> cacheAnnotation;

   private ObserverAssertion(Cache1Observers observer, Class<?> cacheAnnotation) {
      this.cacheAnnotation = cacheAnnotation;
      this.observer = observer;
   }

   public static ObserverAssertion assertThat(Cache1Observers observer, Class<?> cacheAnnotation) {
      return new ObserverAssertion(observer, cacheAnnotation);
   }

   public ObserverAssertion hasProperName(String cacheName) {
      List<CacheStartedEvent> events = observer.getEventsMap().getEvents(cacheAnnotation, CacheStartedEvent.class);
      assertTrue(events.size() > 0);
      assertEquals(events.get(0).getCacheName(), cacheName);
      return this;
   }

   public ObserverAssertion isStarted() {
      List<CacheStartedEvent> events = observer.getEventsMap().getEvents(cacheAnnotation, CacheStartedEvent.class);
      assertEquals(events.size(), 1);
      return this;
   }

   public ObserverAssertion hasEntryCreatedEvent(String key) {
      List<CacheEntryCreatedEvent> events = observer.getEventsMap().getEvents(cacheAnnotation, CacheEntryCreatedEvent.class);
      assertTrue(events.size() > 0);
      assertEquals(events.get(0).getKey(), key);
      return this;
   }

   public ObserverAssertion hasEntryRemovedEvent(String key) {
      List<CacheEntryRemovedEvent> events = observer.getEventsMap().getEvents(cacheAnnotation,
            CacheEntryRemovedEvent.class);
      assertTrue(events.size() > 0);
      assertEquals(events.get(0).getKey(), key);
      return this;
   }

   public ObserverAssertion hasEntryActivatedEvent(String key) {
      List<CacheEntryActivatedEvent> events = observer.getEventsMap().getEvents(cacheAnnotation,
            CacheEntryActivatedEvent.class);
      assertTrue(events.size() > 0);
      assertEquals(events.get(0).getKey(), key);
      return this;
   }
}
