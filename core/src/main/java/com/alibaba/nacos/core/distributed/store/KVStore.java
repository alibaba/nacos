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

package com.alibaba.nacos.core.distributed.store;

import com.alibaba.nacos.core.utils.SerializeFactory;
import com.alibaba.nacos.core.utils.Serializer;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentSkipListMap;

/**
 * Key-value pair data storage structure abstraction
 *
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 */
@SuppressWarnings("all")
public abstract class KVStore<T> extends BaseStore {

    private volatile boolean canSubscribe = false;

    protected Map<String, byte[]> dataStore;

    public KVStore(String name) {
        this(name, SerializeFactory.getDefault());
    }

    public KVStore(String name, Serializer serializer) {
        super(name, serializer);
        this.dataStore = new ConcurrentSkipListMap<>();
    }

    public void openSubscribe() {
        canSubscribe = true;
    }

    public void closeSubscribe() {
        canSubscribe = false;
    }

    /**
     * put data
     *
     * @param key
     * @param data
     * @return
     */
    public abstract boolean put(String key, T data);

    /**
     * remove data
     *
     * @param key
     * @return
     */
    public abstract boolean remove(String key);

    /**
     * put data
     *
     * @param key
     * @param data
     * @return
     */
    public abstract boolean batchPut(Map<String, T> data);

    /**
     * remove data
     *
     * @param keys
     * @return
     */
    public abstract boolean batchRemove(Collection<String> keys);

    /**
     *
     * @param remoteData
     */
    public abstract void load(Map<String, byte[]> remoteData);

    /**
     * get batch data by key list
     *
     * @param keys {@link Collection <String>}
     * @return {@link Map <String, ? extends Record>}
     */
    public abstract Map<String, byte[]> batchGet(Collection<String> keys);

    /**
     * get data by key
     *
     * @param key data key
     * @return target data {@link <T extends Record>}
     */
    public abstract byte[] getByKey(String key);

    /**
     * all data keys
     *
     * @return {@link Collection <String>}
     */
    public abstract Collection<String> allKeys();

    private void isOpenSubscribe() {
        if (!canSubscribe) {
            throw new IllegalStateException();
        }
    }

}
