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

package com.alibaba.nacos.core.distributed.distro;

import com.alibaba.nacos.common.SerializeFactory;
import com.alibaba.nacos.common.Serializer;
import com.alibaba.nacos.consistency.Config;
import com.alibaba.nacos.consistency.ConsistencyProtocol;
import com.alibaba.nacos.consistency.Log;
import com.alibaba.nacos.consistency.NLog;
import com.alibaba.nacos.consistency.ap.LogProcessor4AP;
import com.alibaba.nacos.consistency.request.GetRequest;
import com.alibaba.nacos.consistency.store.KVStore;
import org.apache.commons.lang3.StringUtils;
import org.javatuples.Triplet;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Eventual consistency key-value pair storage
 * Implementation class needs to be discovered by Spring
 *
 * <p>
 *      Provides AP consistency internally, that is, the KV data
 *      under each node will eventually be consistent, and the AP
 *      protocol is shielded. Users only need to use the provided
 *      data operation method to enjoy distributed KV based on final
 *      consistency
 * </p>
 *
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 */
@SuppressWarnings("all")
public class DistroKVStore<T> extends KVStore<T> {

    public static final String BIZ = "Distro@";

    private final KVLogProcessor logProcessor;

    private ConsistencyProtocol<? extends Config> protocol;

    DistroKVStore(String name) {
        this(name, SerializeFactory.getDefault());
    }

    DistroKVStore(String name, Serializer serializer) {
        super(name, serializer);
        this.logProcessor = new KVLogProcessor();
    }

    @Override
    public boolean contains(String key) {
        return dataStore.containsKey(key);
    }

    @Override
    public final boolean put(String key, T data) throws Exception {

        key = logProcessor.bizInfo() + key;

        final byte[] putData = serializer.serialize(data);

        final NLog log = NLog.builder()
                .key(key)
                .data(putData)
                .operation(PUT_COMMAND)
                .className(data.getClass().getCanonicalName())
                .addContextValue("source", data)
                .build();

        protocol.submit(log);
        return true;
    }

    @Override
    public final boolean remove(String key) throws Exception {

        key = logProcessor.bizInfo() + key;

        final NLog log = NLog.builder()
                .key(key)
                .operation(REMOVE_COMMAND)
                .build();

        protocol.submit(log);

        return true;
    }

    // Loading data, will not trigger AP consistent interface call

    @Override
    public final void load(Map<String, Item> remoteData) {
        remoteData.forEach(new BiConsumer<String, Item>() {
            @Override
            public void accept(String s, Item item) {
                final String key = s;
                final T source = serializer.deSerialize(item.getBytes(), item.getClassName());
                operate(Triplet.with(key, source, item.getBytes()), PUT_COMMAND);
            }
        });
    }

    @Override
    public Map<String, byte[]> batchGet(Collection<String> keys) {
        Map<String, byte[]> returnData = new HashMap<>(keys.size());
        for (String key : keys) {
            returnData.put(key, dataStore.get(key).getBytes());
        }
        return returnData;
    }

    @Override
    public T getByKeyAutoConvert(String key) {
        Item item = dataStore.get(key);
        if (item == null) {
            return null;
        }

        byte[] tmp = item.getBytes();

        if (tmp == null || tmp.length == 0) {
            return null;
        }

        return serializer.deSerialize(tmp, item.getClassName());
    }

    @Override
    public Item getItemByKey(String key) {
        return dataStore.get(key);
    }

    @Override
    public Map<String, T> batchGetAutoConvert(Collection<String> keys) {
        Map<String, T> result = new HashMap<>();

        keys.forEach(new Consumer<String>() {
            @Override
            public void accept(String s) {
                T data = getByKeyAutoConvert(s);
                result.put(s, data);
            }
        });
        return result;
    }

    @Override
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

    @Override
    public String getCheckSum(String key) {
        if (dataStore.containsKey(key)) {
            return dataStore.get(key).getCheckSum();
        }
        return null;
    }

    @Override
    public Collection<String> allKeys() {
        return dataStore.keySet();
    }

    @Override
    public Map<String, Item> getAll() {
        return dataStore;
    }

    @Override
    public byte[] getByKey(String key) {
        return dataStore.get(key).getBytes();
    }

    // Provide AP consistency capability for KV storage

    final class KVLogProcessor implements LogProcessor4AP {

        @Override
        public void injectProtocol(ConsistencyProtocol<? extends Config> protocol) {
            DistroKVStore.this.protocol = protocol;
        }

        @Override
        public T getData(GetRequest request) {
            final String key = request.getKey();
            return getByKeyAutoConvert(key);
        }

        @Override
        public boolean onApply(Log log) {
            final String operation = log.getOperation();
            final String originKey = getOriginKey(log.getKey());
            final NLog nLog = (NLog) log;
            if (StringUtils.equalsIgnoreCase(operation, PUT_COMMAND)) {
                final byte[] data = log.getData();
                final T source = (T) nLog.getContextValue("source");
                operate(Triplet.with(originKey, source, data), PUT_COMMAND);
                return true;
            }
            if (StringUtils.equalsIgnoreCase(operation, REMOVE_COMMAND)) {
                operate(originKey, REMOVE_COMMAND);
            }
            throw new UnsupportedOperationException();
        }

        @Override
        public String bizInfo() {
            return BIZ + storeName() + "@@";
        }

        String getOriginKey(String key) {
            return key.replace(bizInfo(), "");
        }

    }

    KVLogProcessor getKVLogProcessor() {
        return logProcessor;
    }

}
