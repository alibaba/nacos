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

package com.alibaba.nacos.naming.consistency.persistent;

import com.alibaba.nacos.common.notify.Event;
import com.alibaba.nacos.common.notify.listener.Subscriber;
import com.alibaba.nacos.common.utils.ConcurrentHashSet;
import com.alibaba.nacos.consistency.DataOperation;
import com.alibaba.nacos.naming.consistency.KeyBuilder;
import com.alibaba.nacos.naming.consistency.RecordListener;
import com.alibaba.nacos.naming.consistency.ValueChangeEvent;
import com.alibaba.nacos.naming.misc.Loggers;
import com.alibaba.nacos.naming.pojo.Record;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * persistent notifier, It is responsible for notifying interested listeners of all write changes to the data.
 *
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 */
public final class PersistentNotifier extends Subscriber<ValueChangeEvent> {
    
    private final Map<String, ConcurrentHashSet<RecordListener>> listenerMap = new ConcurrentHashMap<>(32);
    
    private final Function<String, Record> find;
    
    public PersistentNotifier(Function<String, Record> find) {
        this.find = find;
    }
    
    /**
     * register listener with key.
     *
     * @param key      key
     * @param listener {@link RecordListener}
     */
    public void registerListener(final String key, final RecordListener listener) {
        listenerMap.computeIfAbsent(key, s -> new ConcurrentHashSet<>());
        listenerMap.get(key).add(listener);
    }
    
    /**
     * deregister listener by key.
     *
     * @param key      key
     * @param listener {@link RecordListener}
     */
    public void deregisterListener(final String key, final RecordListener listener) {
        if (!listenerMap.containsKey(key)) {
            return;
        }
        listenerMap.get(key).remove(listener);
    }
    
    /**
     * deregister all listener by key.
     *
     * @param key key
     */
    public void deregisterAllListener(final String key) {
        listenerMap.remove(key);
    }
    
    public Map<String, ConcurrentHashSet<RecordListener>> getListeners() {
        return listenerMap;
    }
    
    /**
     * notify value to listener with {@link DataOperation} and key.
     *
     * @param key    key
     * @param action {@link DataOperation}
     * @param value  value
     * @param <T>    type
     */
    public <T extends Record> void notify(final String key, final DataOperation action, final T value) {
        if (listenerMap.containsKey(KeyBuilder.SERVICE_META_KEY_PREFIX)) {
            if (KeyBuilder.matchServiceMetaKey(key) && !KeyBuilder.matchSwitchKey(key)) {
                for (RecordListener listener : listenerMap.get(KeyBuilder.SERVICE_META_KEY_PREFIX)) {
                    try {
                        if (action == DataOperation.CHANGE) {
                            listener.onChange(key, value);
                        }
                        if (action == DataOperation.DELETE) {
                            listener.onDelete(key);
                        }
                    } catch (Throwable e) {
                        Loggers.RAFT.error("[NACOS-RAFT] error while notifying listener of key: {}", key, e);
                    }
                }
            }
        }
        
        if (!listenerMap.containsKey(key)) {
            return;
        }
        
        for (RecordListener listener : listenerMap.get(key)) {
            try {
                if (action == DataOperation.CHANGE) {
                    listener.onChange(key, value);
                    continue;
                }
                if (action == DataOperation.DELETE) {
                    listener.onDelete(key);
                }
            } catch (Throwable e) {
                Loggers.RAFT.error("[NACOS-RAFT] error while notifying listener of key: {}", key, e);
            }
        }
    }
    
    @Override
    public void onEvent(ValueChangeEvent event) {
        notify(event.getKey(), event.getAction(), find.apply(event.getKey()));
    }
    
    @Override
    public Class<? extends Event> subscribeType() {
        return ValueChangeEvent.class;
    }
}
