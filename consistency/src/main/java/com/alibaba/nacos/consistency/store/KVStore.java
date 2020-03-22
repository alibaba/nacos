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

package com.alibaba.nacos.consistency.store;

import com.alibaba.nacos.consistency.SerializeFactory;
import com.alibaba.nacos.consistency.Serializer;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import org.apache.commons.lang3.StringUtils;

/**
 * Key-value pair data storage structure abstraction
 *
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 */
@SuppressWarnings("all")
public abstract class KVStore<T> extends BaseStore {

    public static final String PUT_COMMAND = "PUT";

    public static final String REMOVE_COMMAND = "REMOVE";

    protected final Map<String, Item> dataStore = new ConcurrentSkipListMap<>();

    private StartHook startHook;
    private BeforeHook beforeHook;
    private AfterHook afterHook;
    BiFunction<String, Object, Boolean> put = new BiFunction<String, Object, Boolean>() {
        @Override
        public Boolean apply(String key, Object data) {
            final boolean[] isCreate = new boolean[]{false};

            final Item item = new Item(data);

            before(key, data, item, true);

            dataStore.computeIfAbsent(key, s -> {
                isCreate[0] = true;
                return item;
            });

            Item currentItem = dataStore.get(key);

            if (!isCreate[0]) {

                // will auto update checkSum

                dataStore.get(key).setData(data);
            }

            after(key, data, currentItem, true);
            return true;
        }
    };
    BiFunction<String, Object, Boolean> remove = new BiFunction<String, Object, Boolean>() {
        @Override
        public Boolean apply(String key, Object data) {
            before(key, null, null, false);
            T source = get(key);
            Item item = dataStore.remove(key);
            after(key, source, item, false);
            return true;
        }
    };
    private ShutHook shutHook;
    private volatile boolean isStart = false;

    public KVStore(String name) {
        this(name, SerializeFactory.getDefault());
    }

    public KVStore(String name, Serializer serializer) {
        super(name, serializer);
        initCommandAnalyze(new KVCommandAnalyzer());
    }

    public final void registerHook(StartHook startHook, BeforeHook beforeHook, AfterHook afterHook) {
        this.startHook = startHook;
        this.beforeHook = beforeHook;
        this.afterHook = afterHook;
    }

    public final void start() throws Exception {
        if (startHook != null) {
            startHook.hook(dataStore, this);
        }
        isStart = true;
    }

    /**
     * put data
     *
     * @param key
     * @param data
     * @return
     */
    public abstract boolean put(String key, T data) throws Exception;

    /**
     * remove data
     *
     * @param key
     * @return
     */
    public abstract boolean remove(String key) throws Exception;

    /**
     * Hooks before data manipulation
     *
     * @param key
     * @param data  if operate == -1, the data is null
     * @param isPut is put operation
     */
    protected void before(String key, Object data, Item item, boolean isPut) {
        beforeHook.hook(key, data, item, isPut);
    }

    /**
     * Hooks after data manipulation
     *
     * @param key
     * @param data  remove source data
     * @param isPut is put operation
     */
    protected void after(String key, Object data, Item item, boolean isPut) {
        afterHook.hook(key, data, item, isPut);
    }

    /**
     * Download Data
     *
     * @param remoteData
     */
    public abstract void load(Map<String, Item> remoteData);

    public boolean contains(String key) {
        return dataStore.containsKey(key);
    }

    public Map<String, Object> batchGet(Collection<String> keys) {
        Map<String, Object> returnData = new HashMap<>(keys.size());
        for (String key : keys) {
            returnData.put(key, dataStore.get(key).getData());
        }
        return returnData;
    }

    public T get(String key) {
        Item item = dataStore.get(key);
        if (item == null) {
            return null;
        }

        return item.getData();
    }

    public Item getItemByKey(String key) {
        return dataStore.get(key);
    }

    public Map<String, T> batchGetAutoConvert(Collection<String> keys) {
        Map<String, T> result = new HashMap<>();

        keys.forEach(new Consumer<String>() {
            @Override
            public void accept(String s) {
                T data = get(s);
                result.put(s, data);
            }
        });
        return result;
    }

    public Map<String, Item> getItemByBatch(Collection<String> keys) {
        Map<String, Item> result = new HashMap<>();

        keys.forEach(new Consumer<String>() {
            @Override
            public void accept(String s) {
                result.put(s, dataStore.get(s));
            }
        });
        return result;
    }

    public Collection<String> allKeys() {
        return dataStore.keySet();
    }

    public Map<String, Item> getAll() {
        return dataStore;
    }

    // put operation

    public static class Item {

        Object data;
        String className;
        String checkSum;

        public Item() {
        }

        public Item(Object data) {
            this.data = data;
        }

        public Item(Object data, String className) {
            this.data = data;
            this.className = className;
        }

        public <T> T getData() {
            return (T) data;
        }

        public void setData(Object data) {
            this.data = data;
        }

        public String getCheckSum() {
            return checkSum;
        }

        public String getClassName() {
            return className;
        }

        public void setClassName(String className) {
            this.className = className;
        }
    }

    // remove operation

    class KVCommandAnalyzer implements CommandAnalyzer {

        @Override
        public <D> BiFunction<String, D, Boolean> analyze(String command) {
            if (StringUtils.equalsIgnoreCase(command, PUT_COMMAND)) {
                return (BiFunction<String, D, Boolean>) put;
            }
            if (StringUtils.equalsIgnoreCase(command, REMOVE_COMMAND)) {
                return (BiFunction<String, D, Boolean>) remove;
            }
            throw new UnsupportedOperationException();
        }
    }

}
