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
package com.alibaba.nacos.client.config.impl;

import com.alibaba.nacos.client.utils.LogUtils;
import org.slf4j.Logger;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Event subscription and publishing tools.
 *
 * @author Nacos
 */
public class EventDispatcher {

    private static final Logger LOGGER = LogUtils.logger(EventDispatcher.class);

    /**
     * 添加事件监听器
     */
    static public void addEventListener(AbstractEventListener listener) {
        for (Class<? extends AbstractEvent> type : listener.interest()) {
            getListenerList(type).addIfAbsent(listener);
        }
    }

    /**
     * 发布事件，首先发布该事件暗示的其他事件，最后通知所有对应的监听器。
     */
    static public void fireEvent(AbstractEvent abstractEvent) {
        if (null == abstractEvent) {
            return;
        }

        // 发布该事件暗示的其他事件
        for (AbstractEvent implyEvent : abstractEvent.implyEvents()) {
            try {
                // 避免死循环
                if (abstractEvent != implyEvent) {
                    fireEvent(implyEvent);
                }
            } catch (Exception e) {
                LOGGER.warn(e.toString(), e);
            }
        }

        for (AbstractEventListener listener : getListenerList(abstractEvent.getClass())) {
            try {
                listener.onEvent(abstractEvent);
            } catch (Exception e) {
                LOGGER.warn(e.toString(), e);
            }
        }
    }

    static synchronized CopyOnWriteArrayList<AbstractEventListener> getListenerList(
        Class<? extends AbstractEvent> eventType) {
        CopyOnWriteArrayList<AbstractEventListener> listeners = LISTENER_MAP.get(eventType);
        if (null == listeners) {
            listeners = new CopyOnWriteArrayList<AbstractEventListener>();
            LISTENER_MAP.put(eventType, listeners);
        }
        return listeners;
    }

    // ========================

    static final Map<Class<? extends AbstractEvent>, CopyOnWriteArrayList<AbstractEventListener>> LISTENER_MAP
        = new HashMap<Class<? extends AbstractEvent>, CopyOnWriteArrayList<AbstractEventListener>>();

    // ========================

    /**
     * Client事件。
     */
    static public abstract class AbstractEvent {

        @SuppressWarnings("unchecked")
        protected List<AbstractEvent> implyEvents() {
            return Collections.EMPTY_LIST;
        }
    }

    /**
     * 事件监听器。
     */
    static public abstract class AbstractEventListener {
        public AbstractEventListener() {
            /**
             * 自动注册给EventDispatcher
             */
            EventDispatcher.addEventListener(this);
        }

        /**
         * 感兴趣的事件列表
         *
         * @return event list
         */
        abstract public List<Class<? extends AbstractEvent>> interest();

        /**
         * 处理事件
         *
         * @param abstractEvent event to do
         */
        abstract public void onEvent(AbstractEvent abstractEvent);
    }

    /**
     * serverList has changed
     */
    static public class ServerlistChangeEvent extends AbstractEvent {
    }
}
