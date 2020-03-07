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

import java.util.Map;
import java.util.Observable;
import java.util.Observer;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Flow;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Consistent protocol metadata information, <Key, <Key, Value >> structure
 * Listeners that can register to listen to changes in value
 *
 * <ul>
 *     <li>
 *         <global, <cluster, List <String >> metadata information that exists by default, that is, all node information of the entire cluster
 *     </li>
 *     <li>
 *         <global, <self, String> The metadata information existing by default, that is, the IP: PORT information of the current node
 *     </li>
 * </ul>
 *
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 */
@SuppressWarnings("PMD.Rule:CollectionInitShouldAssignCapacityRule")
public final class ProtocolMetaData {

    public static final String GLOBAL = "global";

    public static final String CLUSTER_INFO = "cluster";

    public static final String SELF = "self";

    private Map<String, MetaData> metaDataMap = new ConcurrentHashMap<>(4);

    public Map<String, MetaData> getMetaDataMap() {
        return metaDataMap;
    }

    // Does not guarantee thread safety, there may be two updates of
    // time-1 and time-2 (time-1 <time-2), but time-1 data overwrites time-2

    public void load(final Map<String, Map<String, Object>> mapMap) {
        mapMap.forEach((s, map) -> {
            metaDataMap.computeIfAbsent(s, MetaData::new);
            final MetaData data = metaDataMap.get(s);
            map.forEach(data::put);
        });
    }

    public Object get(String group, String... subKey) {
        if (subKey == null || subKey.length == 0) {
            return metaDataMap.get(group);
        } else {
            final String key = subKey[0];
            if (metaDataMap.containsKey(group)) {
                return metaDataMap.get(group).get(key);
            }
            return null;
        }
    }

    // If MetaData does not exist, actively create a MetaData

    public void subscribe(final String group, final String key, final Observer observer) {
        metaDataMap.computeIfAbsent(group, s -> new MetaData(group));
        metaDataMap.get(group)
                .subscribe(key, observer);
    }

    @SuppressWarnings("PMD.ThreadPoolCreationRule")
    public static final class MetaData {

        // Each biz does not affect each other

        private transient final ExecutorService executor = Executors.newSingleThreadExecutor();

        private final Map<String, ValueItem> itemMap = new ConcurrentHashMap<>(8);

        private transient final String group;

        public MetaData(String group) {
            this.group = group;
        }

        public Map<String, ValueItem> getItemMap() {
            return itemMap;
        }

        void put(String key, Object value) {
            itemMap.computeIfAbsent(key, s -> {
                ValueItem item = new ValueItem(this, group + "/" + key);
                return item;
            });
            ValueItem item = itemMap.get(key);
            item.setData(value);
        }

        public ValueItem get(String key) {
            return itemMap.get(key);
        }

        // If ValueItem does not exist, actively create a ValueItem

        void subscribe(final String key, final Observer observer) {
            itemMap.computeIfAbsent(key, s -> {
                ValueItem item = new ValueItem(this, group + "/" + key);
                return item;
            });
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

        private volatile Object data;

        private transient final MetaData holder;
        private transient final String path;

        private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
        private final ReentrantReadWriteLock.ReadLock readLock = lock.readLock();
        private final ReentrantReadWriteLock.WriteLock writeLock = lock.writeLock();

        private BlockingQueue<Object> deferObject = new LinkedBlockingQueue<>();

        public ValueItem(MetaData holder, String path) {
            this.holder = holder;
            this.path = path;
            notifySubscriber();
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
                deferObject.offer(data);
            } finally {
                writeLock.unlock();
            }
        }

        private void notifySubscriber() {
            holder.executor.submit(() -> {
                if (countObservers() == 0) {
                    holder.executor.submit(this::notifySubscriber);
                    return;
                }
                try {
                    Object data = deferObject.take();
                    notifyObservers(data);
                } catch (InterruptedException ignore) {

                }
                holder.executor.submit(this::notifySubscriber);
            });
        }
    }
}
