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

package com.alibaba.nacos.core.distributed.raft;

import com.alibaba.nacos.common.SerializeFactory;
import com.alibaba.nacos.common.Serializer;
import com.alibaba.nacos.consistency.Config;
import com.alibaba.nacos.consistency.ConsistencyProtocol;
import com.alibaba.nacos.consistency.Log;
import com.alibaba.nacos.consistency.NLog;
import com.alibaba.nacos.consistency.cp.LogProcessor4CP;
import com.alibaba.nacos.consistency.request.GetRequest;
import com.alibaba.nacos.consistency.store.KVStore;
import org.apache.commons.lang3.StringUtils;
import org.javatuples.Triplet;

import java.util.Collection;
import java.util.Map;

/**
 * Strong consistency key-value pair storage
 * Implementation class needs to be discovered by Spring
 *
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 */
@SuppressWarnings("all")
class RaftKVStore<T> extends KVStore<T> {

    public static final String BIZ = "Raft@";

    private final KVLogProcessor logProcessor;

    private ConsistencyProtocol<? extends Config> protocol;

    private final KvSuperFuncCaller funcCaller;

    public RaftKVStore(String name) {
        this(name, SerializeFactory.getDefault());
    }

    public RaftKVStore(String name, Serializer serializer) {
        super(name, serializer);
        this.logProcessor = new KVLogProcessor();
        this.funcCaller = new KvSuperFuncCaller();
    }

    @Override
    public boolean contains(String key) {
        return dataStore.containsKey(key);
    }

    @Override
    public boolean put(String key, Object data) throws Exception {
        key = logProcessor.bizInfo() + key;

        final byte[] putData = serializer.serialize(data);

        final NLog log = NLog.builder()
                .key(key)
                .data(putData)
                .operation(PUT_COMMAND)
                .className(data.getClass().getCanonicalName())
                .build();

        protocol.submit(log);
        return true;
    }

    @Override
    public boolean remove(String key) throws Exception {
        key = logProcessor.bizInfo() + key;

        final NLog log = NLog.builder()
                .key(key)
                .operation(REMOVE_COMMAND)
                .build();

        protocol.submit(log);

        return true;
    }

    @Override
    public byte[] getByKey(String key) {
        try {
            final GetRequest request = GetRequest.builder()
                    .key(key)
                    .addValue("type", "getByKey")
                    .build();
            return protocol.getData(request);
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public T getByKeyAutoConvert(String key) {
        try {
            final GetRequest request = GetRequest.builder()
                    .key(key)
                    .addValue("type", "getByKeyAutoConvert")
                    .build();
            return protocol.getData(request);
        } catch (Exception e) {

        }
        return null;
    }

    @Override
    public Item getItemByKey(String key) {
        try {
            final GetRequest request = GetRequest.builder()
                    .key(key)
                    .addValue("type", "getItemByKey")
                    .build();
            return protocol.getData(request);
        } catch (Exception e) {

        }
        return null;
    }

    @Override
    public Map<String, T> batchGetAutoConvert(Collection<String> keys) {
        try {
            final GetRequest request = GetRequest.builder()
                    .keys(keys)
                    .addValue("type", "batchGetAutoConvert")
                    .build();
            return protocol.getData(request);
        } catch (Exception e) {

        }
        return null;
    }

    @Override
    public Map<String, Item> getItemByBatch(Collection<String> keys) {
        try {
            final GetRequest request = GetRequest.builder()
                    .keys(keys)
                    .addValue("type", "getItemByBatch")
                    .build();
            return protocol.getData(request);
        } catch (Exception e) {

        }
        return null;
    }

    @Override
    public String getCheckSum(String key) {
        try {
            final GetRequest request = GetRequest.builder()
                    .key(key)
                    .addValue("type", "getCheckSum")
                    .build();
            return protocol.getData(request);
        } catch (Exception e) {

        }
        return null;
    }

    @Override
    public Collection<String> allKeys() {
        try {
            final GetRequest request = GetRequest.builder()
                    .addValue("type", "allKeys")
                    .build();
            return protocol.getData(request);
        } catch (Exception e) {

        }
        return null;
    }

    @Override
    public Map<String, Item> getAll() {
        try {
            final GetRequest request = GetRequest.builder()
                    .addValue("type", "getAll")
                    .build();
            return protocol.getData(request);
        } catch (Exception e) {

        }
        return null;
    }

    @Override
    public Map<String, byte[]> batchGet(Collection keys) {
        try {
            final GetRequest request = GetRequest.builder()
                    .keys(keys)
                    .addValue("type", "batchGet")
                    .build();
            return protocol.getData(request);
        } catch (Exception e) {

        }
        return null;
    }

    @Override
    public void load(Map remoteData) {

    }

    class KVLogProcessor implements LogProcessor4CP {

        @Override
        public void injectProtocol(ConsistencyProtocol<? extends Config> protocol) {
            RaftKVStore.this.protocol = protocol;
        }

        @Override
        public <D> D getData(GetRequest request) {
            final String key = request.getKey();
            final Collection<String> keys = request.getKeys();
            final String type = request.getValue("type");
            return (D) funcCaller.execute(type, key, keys);
        }

        @Override
        public boolean onApply(Log log) {
            final String operation = log.getOperation();
            final String originKey = getOriginKey(log.getKey());
            final NLog nLog = (NLog) log;
            if (StringUtils.equalsIgnoreCase(operation, PUT_COMMAND)) {
                final byte[] data = log.getData();
                final T source = serializer.deSerialize(data, log.getClassName());
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

    public KVLogProcessor getLogProcessor() {
        return logProcessor;
    }

    class KvSuperFuncCaller {

        private final String getByKey = "getByKey";

        private final String getByKeyAutoConvert = "getByKeyAutoConvert";

        private final String getItemByKey = "getItemByKey";

        private final String batchGetAutoConvert = "batchGetAutoConvert";

        private final String getItemByBatch = "getItemByBatch";

        private final String getCheckSum = "getCheckSum";

        private final String allKeys = "allKeys";

        private final String getAll = "getAll";

        private final String batchGet = "batchGet";


        // Leave it to the KvStore implementation method to handle

        Object execute(String type, String key, Collection<String> keys) {

            switch (type) {
                case getByKey:
                    return RaftKVStore.super.getByKey(key);
                case getByKeyAutoConvert:
                    return RaftKVStore.super.getByKeyAutoConvert(key);
                case getItemByKey:
                    return RaftKVStore.super.getItemByKey(key);
                case batchGetAutoConvert:
                    return RaftKVStore.super.batchGetAutoConvert(keys);
                case getItemByBatch:
                    return RaftKVStore.super.getItemByBatch(keys);
                case getCheckSum:
                    return RaftKVStore.super.getCheckSum(key);
                case allKeys:
                    return RaftKVStore.super.allKeys();
                case getAll:
                    return RaftKVStore.super.getAll();
                case batchGet:
                    return RaftKVStore.super.batchGet(keys);
                default:
                    throw new UnsupportedOperationException();

            }

        }

    }

}
