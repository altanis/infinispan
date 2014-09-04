package org.infinispan.cdi.test.event;

import org.infinispan.notifications.cachelistener.event.CacheEntryActivatedEvent;
import org.infinispan.notifications.cachelistener.event.CacheEntryCreatedEvent;
import org.infinispan.notifications.cachelistener.event.CacheEntryRemovedEvent;
import org.infinispan.notifications.cachemanagerlistener.event.CacheStartedEvent;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.event.Observes;

/**
 * @author Pete Muir
 */
@ApplicationScoped
public class Cache1Observers {

   CacheObserverEvents eventsMap = new CacheObserverEvents();

   void observeCache1CacheStatedEvent(@Observes @Cache1 CacheStartedEvent event) {
      eventsMap.addEvent(Cache1.class, CacheStartedEvent.class, event);
   }

   void observeCache2CacheStatedEvent(@Observes @Cache2 CacheStartedEvent event) {
      eventsMap.addEvent(Cache2.class, CacheStartedEvent.class, event);
   }

   void observeCache1CacheEntryCreatedEvent(@Observes @Cache1 CacheEntryCreatedEvent event) {
      eventsMap.addEvent(Cache1.class, CacheEntryCreatedEvent.class, event);
   }

   void observeCache2CacheEntryCreatedEvent(@Observes @Cache2 CacheEntryCreatedEvent event) {
      eventsMap.addEvent(Cache2.class, CacheEntryCreatedEvent.class, event);
   }

   void observeCache1CacheEntryRemovedEvent(@Observes @Cache1 CacheEntryRemovedEvent event) {
      eventsMap.addEvent(Cache1.class, CacheEntryRemovedEvent.class, event);
   }

   void observeCache2CacheEntryRemovedEvent(@Observes @Cache2 CacheEntryRemovedEvent event) {
      eventsMap.addEvent(Cache2.class, CacheEntryRemovedEvent.class, event);
   }

   void observeCache1CacheEntryActivatedEvent(@Observes @Cache1 CacheEntryActivatedEvent event) {
      eventsMap.addEvent(Cache1.class, CacheEntryActivatedEvent.class, event);
   }

   void observeCache2CacheEntryActivatedEvent(@Observes @Cache2 CacheEntryActivatedEvent event) {
      eventsMap.addEvent(Cache2.class, CacheEntryActivatedEvent.class, event);
   }


   public CacheObserverEvents getEventsMap() {
      return eventsMap;
   }

   public void clear() {
      eventsMap.clear();
   }
}
