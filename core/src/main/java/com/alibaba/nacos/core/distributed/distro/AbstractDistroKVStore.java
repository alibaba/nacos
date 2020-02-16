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
import com.alibaba.nacos.core.distributed.distro.utils.DistroUtils;
import com.alibaba.nacos.core.distributed.store.CommandAnalyzer;
import com.alibaba.nacos.core.distributed.store.KVStore;
import com.alibaba.nacos.core.utils.SerializeFactory;
import com.alibaba.nacos.core.utils.Serializer;
import org.apache.commons.lang3.StringUtils;
import org.javatuples.Triplet;

import java.lang.reflect.ParameterizedType;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.function.BiConsumer;
import java.util.function.Function;

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
public abstract class AbstractDistroKVStore<T> extends KVStore<T> {

    public static final String BIZ = "Distro@";

    public static final String PUT_COMMAND = "PUT";

    public static final String REMOVE_COMMAND = "REMOVE";

    private final Class<T> genericClass;

    private final KVLogProcessor logProcessor;

    private final Map<String, Item> dataStore;

    private ConsistencyProtocol<? extends Config> protocol;

    public AbstractDistroKVStore(String name) {
        this(name, SerializeFactory.getDefault());
    }

    public AbstractDistroKVStore(String name, Serializer serializer) {
        super(name, serializer);
        this.genericClass = (Class<T>) ((ParameterizedType) getClass().getGenericSuperclass()).getActualTypeArguments()[0];
        this.logProcessor = new KVLogProcessor();
        this.dataStore = new ConcurrentSkipListMap<>();
    }

    @Override
    public boolean contains(String key) {
        return dataStore.containsKey(key);
    }

    @Override
    public final boolean put(String key, T data) {

        final byte[] putData = serializer.serialize(data);

        final NLog log = NLog.builder()
                .key(key)
                .data(putData)
                .operation(PUT_COMMAND)
                .className(genericClass.getCanonicalName())
                .addContextValue("source", data)
                .build();

        protocol.submitAsync(log);
        return true;
    }

    @Override
    public final boolean remove(String key) {

        final NLog log = NLog.builder()
                .key(key)
                .operation(REMOVE_COMMAND)
                .className(genericClass.getCanonicalName())
                .build();

        protocol.submitAsync(log);

        return true;
    }

    @Override
    public final void load(Map<String, byte[]> remoteData) {
        remoteData.forEach(new BiConsumer<String, byte[]>() {
            @Override
            public void accept(String s, byte[] bytes) {
                final String key = s;
                final T source = serializer.deSerialize(bytes, genericClass);
                operate(Triplet.with(key, source, bytes), PUT_COMMAND);
            }
        });
    }

    @Override
    public final Map<String, byte[]> batchGet(Collection<String> keys) {
        Map<String, byte[]> returnData = new HashMap<>(keys.size());
        for (String key : keys) {
            returnData.put(key, dataStore.get(key).bytes);
        }
        return returnData;
    }

    @Override
    public final byte[] getByKey(String key) {
        return dataStore.get(key).bytes;
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

    public final String getCheckSum(String key) {
        if (dataStore.containsKey(key)) {
            return dataStore.get(key).checkSum;
        }
        return null;
    }

    class Item {

        byte[] bytes;
        String checkSum;

        public Item(byte[] bytes) {
            this.bytes = bytes;
            this.checkSum = DistroUtils.checkSum(bytes);
        }

        public byte[] getBytes() {
            return bytes;
        }

        public void setBytes(byte[] bytes) {
            this.bytes = bytes;
            setCheckSum(DistroUtils.checkSum(bytes));
        }

        public String getCheckSum() {
            return checkSum;
        }

        void setCheckSum(String checkSum) {
            this.checkSum = checkSum;
        }
    }

    class KVCommandAnalyzer implements CommandAnalyzer {

        @Override
        public <D> Function<D, Boolean> analyze(String command) {
            if (StringUtils.equalsIgnoreCase(command, PUT_COMMAND)) {
                return (Function<D, Boolean>) put;
            }
            if (StringUtils.equalsIgnoreCase(command, REMOVE_COMMAND)) {
                return (Function<D, Boolean>) remove;
            }
            throw new UnsupportedOperationException();
        }
    }

    // put operation

    Function<Triplet<String, T, byte[]>, Boolean> put = new Function<Triplet<String, T, byte[]>, Boolean>() {
        @Override
        public Boolean apply(Triplet<String, T, byte[]> triplet) {
            final String key = triplet.getValue0();
            final T value = triplet.getValue1();
            final byte[] data = triplet.getValue2();
            before(key, value, (byte) 1);
            final boolean[] isCreate = new boolean[]{false};
            dataStore.computeIfAbsent(key, s -> {
                isCreate[0] = true;
                return new Item(data);
            });

            if (!isCreate[0]) {

                // will auto update checkSum

                dataStore.get(key).setBytes(data);
            }

            after(key, value, (byte) 1);
            return true;
        }
    };

    // remove operation

    Function<String, Boolean> remove = new Function<String, Boolean>() {
        @Override
        public Boolean apply(String key) {
            before(key, null, (byte) -1);
            final byte[] removeData = dataStore.remove(key).bytes;
            T source = serializer.deSerialize(removeData, genericClass);
            after(key, source, (byte) -1);
            return true;
        }
    };

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
            final String key = log.getKey();
            final NLog nLog = (NLog) log;
            if (StringUtils.equalsIgnoreCase(operation, PUT_COMMAND)) {
                final byte[] data = log.getData();

                // TODO 针对 AP 协议可以做一层优化，如果是本节点提交，本节点 Apply，可以做一层数据透传

                final T source = (T) nLog.getContextValue("source");
                operate(Triplet.with(key, source, data), PUT_COMMAND);
                return true;
            }
            if (StringUtils.equalsIgnoreCase(operation, REMOVE_COMMAND)) {
                operate(key, REMOVE_COMMAND);
            }
            throw new UnsupportedOperationException();
        }

        @Override
        public String bizInfo() {
            return BIZ + storeName();
        }

    }

    KVLogProcessor getKVLogProcessor() {
        return logProcessor;
    }

}
