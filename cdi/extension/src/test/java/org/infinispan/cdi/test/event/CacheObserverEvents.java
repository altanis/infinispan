package org.infinispan.cdi.test.event;

import org.infinispan.notifications.cachemanagerlistener.event.Event;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * // TODO: Document this
 *
 * @author Sebastian Laskawiec
 * @since 4.0
 */
public class CacheObserverEvents {

   Map<Class<?>, Map<Class<?>, List<Object>>> eventMap = new HashMap<>();

   private void addEventClass(Class<?> cacheAnnotationClass, Class<?> eventClass) {
      if(!eventMap.containsKey(cacheAnnotationClass)) {
         eventMap.put(cacheAnnotationClass, new HashMap<Class<?>, List<Object>>());
      }
      if(!eventMap.get(cacheAnnotationClass).containsKey(eventClass)) {
         eventMap.get(cacheAnnotationClass).put(eventClass, new ArrayList<>());
      }
   }

   public <T extends org.infinispan.notifications.cachelistener.event.Event> void addEvent
         (Class<?>cacheAnnotationClass, Class<T> eventStaticClass, T event) {
      addEventClass(cacheAnnotationClass, eventStaticClass);
      eventMap.get(cacheAnnotationClass).get(eventStaticClass).add(event);
   }

   public <T extends Event> void addEvent(Class<?> cacheAnnotationClass, Class<T> eventStaticClass, T event) {
      addEventClass(cacheAnnotationClass,eventStaticClass);
      eventMap.get(cacheAnnotationClass).get(eventStaticClass).add(event);
   }

   public void clear() {
      eventMap.clear();
   }

   public <T> List<T> getEvents(Class<?> cacheAnnotation, Class<T> eventClass) {
      ArrayList<T> toBeReturned = new ArrayList<>();
      Map<Class<?>, List<Object>> eventsMapForGivenCache = eventMap.get(cacheAnnotation);
      if(eventsMapForGivenCache == null) {
         return toBeReturned;
      }
      List<Object> events = eventsMapForGivenCache.get(eventClass);
      if(events != null) {
         for (Object event : events) {
            toBeReturned.add(eventClass.cast(event));
         }
      }
      return toBeReturned;
   }
}
