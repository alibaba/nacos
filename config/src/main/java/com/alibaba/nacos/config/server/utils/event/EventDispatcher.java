/*
 * Copyright 1999-2018 Alibaba Group Holding Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.alibaba.nacos.config.server.utils.event;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Event dispatcher.
 *
 * @author Nacos
 */
public class EventDispatcher {
    
    /**
     * add event listener.
     */
    public static void addEventListener(AbstractEventListener listener) {
        for (Class<? extends Event> type : listener.interest()) {
            getEntry(type).listeners.addIfAbsent(listener);
        }
    }
    
    /**
     * fire event, notify listeners.
     */
    public static void fireEvent(Event event) {
        if (null == event) {
            throw new IllegalArgumentException("event is null");
        }
        
        for (AbstractEventListener listener : getEntry(event.getClass()).listeners) {
            try {
                listener.onEvent(event);
            } catch (Exception e) {
                LOGGER.error(e.toString(), e);
            }
        }
    }
    
    /**
     * For only test purpose.
     */
    public static void clear() {
        LISTENER_HUB.clear();
    }
    
    /**
     * Get event listener for eventType. Add Entry if not exist.
     */
    static Entry getEntry(Class<? extends Event> eventType) {
        for (; ; ) {
            for (Entry entry : LISTENER_HUB) {
                if (entry.eventType == eventType) {
                    return entry;
                }
            }
            
            Entry tmp = new Entry(eventType);
            // false means already exists
            if (LISTENER_HUB.addIfAbsent(tmp)) {
                return tmp;
            }
        }
    }
    
    private static class Entry {
        
        final Class<? extends Event> eventType;
        
        final CopyOnWriteArrayList<AbstractEventListener> listeners;
        
        Entry(Class<? extends Event> type) {
            eventType = type;
            listeners = new CopyOnWriteArrayList<AbstractEventListener>();
        }
        
        @Override
        public boolean equals(Object obj) {
            if (null == obj || obj.getClass() != getClass()) {
                return false;
            }
            if (this == obj) {
                return true;
            }
            return eventType == ((Entry) obj).eventType;
        }
        
        @Override
        public int hashCode() {
            return super.hashCode();
        }
        
    }
    
    private static final Logger LOGGER = LoggerFactory.getLogger(EventDispatcher.class);
    
    static final CopyOnWriteArrayList<Entry> LISTENER_HUB = new CopyOnWriteArrayList<Entry>();
    
    public interface Event {
    
    }
    
    public abstract static class AbstractEventListener {
        
        public AbstractEventListener() {
            // automatic register
            EventDispatcher.addEventListener(this);
        }
        
        /**
         * List of events of interest.
         *
         * @return event list
         */
        public abstract List<Class<? extends Event>> interest();
        
        /**
         * Handle events.
         *
         * @param event event
         */
        public abstract void onEvent(Event event);
    }
    
}
