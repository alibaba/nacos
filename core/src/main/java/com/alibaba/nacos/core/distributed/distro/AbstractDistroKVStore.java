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

import com.alibaba.nacos.consistency.Config;
import com.alibaba.nacos.consistency.ConsistencyProtocol;
import com.alibaba.nacos.consistency.Log;
import com.alibaba.nacos.consistency.NLog;
import com.alibaba.nacos.consistency.ap.LogProcessor4AP;
import com.alibaba.nacos.consistency.request.GetRequest;
import com.alibaba.nacos.core.distributed.store.KVStore;
import com.alibaba.nacos.core.utils.SerializeFactory;
import com.alibaba.nacos.core.utils.Serializer;
import org.apache.commons.lang3.StringUtils;

import java.lang.reflect.ParameterizedType;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.function.BiConsumer;

/**
 * Eventual consistency key-value pair storage
 * Implementation class needs to be discovered by Spring
 *
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 */
@SuppressWarnings("all")
public abstract class AbstractDistroKVStore<T> extends KVStore<T> {

    public static final String BIZ = "Distro@";

    public static final String PUT_COMMAND = "PUT";

    public static final String REMOVE_COMMAND = "REMOVE";

    public static final String BATCH_PUT_COMMAND = "BATCH_PUT";

    public static final String BATCH_REMOVE_COMMAND = "BATCH_REMOVE";

    private final Class<T> genericClass;

    private final Object monitor = new Object();

    private boolean isLoad = false;

    private ConsistencyProtocol<? extends Config> protocol;

    private final KVLogProcessor logProcessor;

    public AbstractDistroKVStore(String name) {
        this(name, SerializeFactory.getDefault());
    }

    public AbstractDistroKVStore(String name, Serializer serializer) {
        super(name, serializer);
        this.genericClass = (Class<T>) ((ParameterizedType) getClass().getGenericSuperclass()).getActualTypeArguments()[0];
        this.logProcessor = new KVLogProcessor();
    }

    public final void useCustomerMap(Map<String, byte[]> customerMap) {
        this.dataStore = customerMap;
    }

    @Override
    public final boolean put(String key, T data) {
        final byte[] putData = serializer.serialize(data);
        dataStore.put(key, putData);

        final NLog log = NLog.builder()
                .key(key)
                .data(putData)
                .operation(PUT_COMMAND)
                .className(genericClass.getCanonicalName())
                .build();

        protocol.submitAsync(log);

        return true;
    }

    @Override
    public final boolean remove(String key) {
        dataStore.remove(key);

        final NLog log = NLog.builder()
                .key(key)
                .operation(REMOVE_COMMAND)
                .className(genericClass.getCanonicalName())
                .build();

        protocol.submitAsync(log);

        return true;
    }

    @Override
    public boolean batchPut(Map<String, T> data) {
        return false;
    }

    @Override
    public boolean batchRemove(Collection<String> keys) {
        return false;
    }

    @Override
    public final void load(Map<String, byte[]> remoteData) {
        dataStore.putAll(remoteData);
    }

    @Override
    public final Map<String, byte[]> batchGet(Collection<String> keys) {
        Map<String, byte[]> returnData = new HashMap<>(keys.size());
        for (String key : keys) {
            returnData.put(key, dataStore.get(key));
        }
        return returnData;
    }

    @Override
    public final byte[] getByKey(String key) {
        return dataStore.get(key);
    }

    @Override
    public final Collection<String> allKeys() {
        return dataStore.keySet();
    }

    public final T getByKeyAutoConvert(String key) {
        byte[] tmp = getByKey(key);
        if (tmp.length == 0) {
            return null;
        }
        return serializer.deSerialize(tmp, genericClass);
    }

    // will auto ignore data which byte[].length == 0

    public final Map<String, T> batchGetAutoConvert(Collection<String> keys) {
        Map<String, byte[]> tmpData = batchGet(keys);
        Map<String, T> result = new HashMap<>();
        tmpData.forEach(new BiConsumer<String, byte[]>() {
            @Override
            public void accept(String s, byte[] bytes) {
                if (bytes.length == 0) {
                    return;
                }
                result.put(s, serializer.deSerialize(bytes, genericClass));
            }
        });
        return result;
    }

    // Provide AP consistency capability for KV storage

    final class KVLogProcessor implements LogProcessor4AP {

        @Override
        public void injectProtocol(ConsistencyProtocol<? extends Config> protocol) {
            AbstractDistroKVStore.this.protocol = protocol;
        }

        @Override
        public T getData(GetRequest request) {
            final String key = request.getKey();
            return getByKeyAutoConvert(key);
        }

        @Override
        public boolean onApply(Log log) {
            final String operation = log.getOperation();
            if (StringUtils.equalsIgnoreCase(operation, PUT_COMMAND)) {
                dataStore.put(log.getKey(), log.getData());
                return true;
            }
            if (StringUtils.equalsIgnoreCase(operation, REMOVE_COMMAND)) {
                dataStore.remove(log.getKey());
                return true;
            }
            throw new UnsupportedOperationException();
        }

        @Override
        public String bizInfo() {
            return BIZ + biz();
        }

    }

    KVLogProcessor getKVLogProcessor() {
        return logProcessor;
    }

}
