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

package com.alibaba.nacos.consistency;

import com.alibaba.nacos.common.utils.Observable;
import com.alibaba.nacos.common.utils.Observer;
import com.alibaba.nacos.common.utils.StringUtils;
import org.javatuples.Pair;

import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Consistent protocol metadata information, &lt;Key, &lt;Key, Value &gt;&gt; structure Listeners that can register to
 * listen to changes in value.
 *
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 */
@SuppressWarnings("PMD.Rule:CollectionInitShouldAssignCapacityRule")
public final class ProtocolMetaData {
    
    private final Map<String, MetaData> metaDataMap = new ConcurrentHashMap<>(4);
    
    public Map<String, Map<Object, Object>> getMetaDataMap() {
        return metaDataMap.entrySet().stream().map(entry -> Pair.with(entry.getKey(),
                entry.getValue().getItemMap().entrySet().stream()
                        .collect(TreeMap::new, (m, e) -> m.put(e.getKey(), e.getValue().getData()), TreeMap::putAll)))
                .collect(TreeMap::new, (m, e) -> m.put(e.getValue0(), e.getValue1()), TreeMap::putAll);
    }
    
    // Does not guarantee thread safety, there may be two updates of
    // time-1 and time-2 (time-1 <time-2), but time-1 data overwrites time-2
    
    /**
     * save target consistency protocol metadata.
     *
     * @param mapMap {@link Map}
     */
    public void load(final Map<String, Map<String, Object>> mapMap) {
        mapMap.forEach((s, map) -> {
            metaDataMap.computeIfAbsent(s, MetaData::new);
            final MetaData data = metaDataMap.get(s);
            map.forEach(data::put);
        });
    }
    
    /**
     * get protocol metadata by group and key.
     *
     * @param group  group name
     * @param subKey key
     * @return target value
     */
    public Object get(String group, String subKey) {
        if (StringUtils.isBlank(subKey)) {
            return metaDataMap.get(group);
        } else {
            if (metaDataMap.containsKey(group)) {
                return metaDataMap.get(group).get(subKey);
            }
            return null;
        }
    }
    
    /**
     * If MetaData does not exist, actively create a MetaData.
     */
    public void subscribe(final String group, final String key, final Observer observer) {
        metaDataMap.computeIfAbsent(group, s -> new MetaData(group));
        metaDataMap.get(group).subscribe(key, observer);
    }
    
    public void unSubscribe(final String group, final String key, final Observer observer) {
        metaDataMap.computeIfAbsent(group, s -> new MetaData(group));
        metaDataMap.get(group).unSubscribe(key, observer);
    }
    
    @SuppressWarnings("PMD.ThreadPoolCreationRule")
    public static final class MetaData {
        
        private final Map<String, ValueItem> itemMap = new ConcurrentHashMap<>(8);
        
        private final transient String group;
        
        public MetaData(String group) {
            this.group = group;
        }
        
        public Map<String, ValueItem> getItemMap() {
            return itemMap;
        }
        
        void put(String key, Object value) {
            itemMap.computeIfAbsent(key, s -> new ValueItem(group + "/" + key));
            ValueItem item = itemMap.get(key);
            item.setData(value);
        }
        
        public ValueItem get(String key) {
            return itemMap.get(key);
        }
        
        // If ValueItem does not exist, actively create a ValueItem
        
        void subscribe(final String key, final Observer observer) {
            itemMap.computeIfAbsent(key, s -> new ValueItem(group + "/" + key));
            final ValueItem item = itemMap.get(key);
            item.addObserver(observer);
        }
        
        void unSubscribe(final String key, final Observer observer) {
            final ValueItem item = itemMap.get(key);
            if (item == null) {
                return;
            }
            item.deleteObserver(observer);
        }
        
    }
    
    public static final class ValueItem extends Observable {
        
        private final transient String path;
        
        private final transient ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
        
        private final transient ReentrantReadWriteLock.ReadLock readLock = lock.readLock();
        
        private final transient ReentrantReadWriteLock.WriteLock writeLock = lock.writeLock();
        
        private volatile Object data;
        
        public ValueItem(String path) {
            this.path = path;
        }
        
        public Object getData() {
            readLock.lock();
            try {
                return data;
            } finally {
                readLock.unlock();
            }
        }
        
        void setData(Object data) {
            writeLock.lock();
            try {
                this.data = data;
                setChanged();
                notifyObservers();
            } finally {
                writeLock.unlock();
            }
        }
        
        public String getPath() {
            return path;
        }
    }
}
