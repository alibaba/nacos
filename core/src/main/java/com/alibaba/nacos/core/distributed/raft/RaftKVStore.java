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
import com.alibaba.nacos.consistency.cp.CPKvStore;
import com.alibaba.nacos.consistency.cp.LogProcessor4CP;
import com.alibaba.nacos.consistency.request.GetRequest;
import com.alibaba.nacos.consistency.snapshot.SnapshotOperate;
import com.alibaba.nacos.core.utils.Loggers;
import org.apache.commons.lang3.StringUtils;
import org.javatuples.Pair;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;

/**
 * Strong consistency key-value pair storage
 * Implementation class needs to be discovered by Spring
 *
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 */
@SuppressWarnings("all")
class RaftKVStore<T> extends CPKvStore<T> {

    private KVLogProcessor logProcessor;

    private ConsistencyProtocol<? extends Config> protocol;

    private KvSuperFuncCaller funcCaller;

    public RaftKVStore(String name) {
        super(name, SerializeFactory.getDefault());
        this.logProcessor = new KVLogProcessor();
        this.funcCaller = new KvSuperFuncCaller();
    }

    public RaftKVStore(String name, SnapshotOperate snapshotOperate) {
        super(name, snapshotOperate);
        this.logProcessor = new KVLogProcessor();
        this.funcCaller = new KvSuperFuncCaller();
    }

    public RaftKVStore(String name, Serializer serializer, SnapshotOperate snapshotOperate) {
        super(name, serializer, snapshotOperate);
        this.logProcessor = new KVLogProcessor();
        this.funcCaller = new KvSuperFuncCaller();
    }

    @Override
    public boolean put(String key, Object data) throws Exception {
        key = buildKey(key);

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
        key = buildKey(key);

        final NLog log = NLog.builder()
                .key(key)
                .operation(REMOVE_COMMAND)
                .build();

        protocol.submit(log);

        return true;
    }

    @Override
    public boolean contains(String key) {
        try {
            key = buildKey(key);
            final GetRequest request = GetRequest.builder()
                    .key(key)
                    .addValue("type", "contains")
                    .build();
            return protocol.getData(request);
        } catch (Exception e) {
            Loggers.RAFT.error("execute raft read operation : [{}] has error : {}", "contains", e);
            return false;
        }
    }

    @Override
    public byte[] getByKey(String key) {
        try {
            key = buildKey(key);
            final GetRequest request = GetRequest.builder()
                    .key(key)
                    .addValue("type", "getByKey")
                    .build();
            return protocol.getData(request);
        } catch (Exception e) {
            Loggers.RAFT.error("execute raft read operation : [{}] has error : {}", "getByKey", e);
        }
        return null;
    }

    @Override
    public T getByKeyAutoConvert(String key) {
        try {
            key = buildKey(key);
            final GetRequest request = GetRequest.builder()
                    .key(key)
                    .addValue("type", "getByKeyAutoConvert")
                    .build();
            return protocol.getData(request);
        } catch (Exception e) {
            Loggers.RAFT.error("execute raft read operation : [{}] has error : {}", "getByKeyAutoConvert", e);
        }
        return null;
    }

    @Override
    public Item getItemByKey(String key) {
        try {
            key = buildKey(key);
            final GetRequest request = GetRequest.builder()
                    .key(key)
                    .addValue("type", "getItemByKey")
                    .build();
            return protocol.getData(request);
        } catch (Exception e) {
            Loggers.RAFT.error("execute raft read operation : [{}] has error : {}", "getItemByKey", e);
        }
        return null;
    }

    @Override
    public Map<String, T> batchGetAutoConvert(Collection<String> keys) {
        try {

            // Just as data routing analysis

            String key = buildKey("");
            final GetRequest request = GetRequest.builder()
                    .key(key)
                    .keys(keys)
                    .addValue("type", "batchGetAutoConvert")
                    .build();
            return protocol.getData(request);
        } catch (Exception e) {
            Loggers.RAFT.error("execute raft read operation : [{}] has error : {}", "batchGetAutoConvert", e);
        }
        return null;
    }

    @Override
    public Map<String, Item> getItemByBatch(Collection<String> keys) {
        try {
            // Just as data routing analysis

            String key = buildKey("");
            final GetRequest request = GetRequest.builder()
                    .key(key)
                    .keys(keys)
                    .addValue("type", "getItemByBatch")
                    .build();
            return protocol.getData(request);
        } catch (Exception e) {
            Loggers.RAFT.error("execute raft read operation : [{}] has error : {}", "getItemByBatch", e);
        }
        return null;
    }

    @Override
    public String getCheckSum(String key) {
        try {
            key = buildKey(key);
            final GetRequest request = GetRequest.builder()
                    .key(key)
                    .addValue("type", "getCheckSum")
                    .build();
            return protocol.getData(request);
        } catch (Exception e) {
            Loggers.RAFT.error("execute raft read operation : [{}] has error : {}", "getCheckSum", e);
        }
        return null;
    }

    @Override
    public Collection<String> allKeys() {
        try {
            // Just as data routing analysis

            String key = buildKey("");
            final GetRequest request = GetRequest.builder()
                    .key(key)
                    .addValue("type", "allKeys")
                    .build();
            return protocol.getData(request);
        } catch (Exception e) {
            Loggers.RAFT.error("execute raft read operation : [{}] has error : {}", "allKeys", e);
        }
        return null;
    }

    // This operation does not guarantee read consistency, and is only used for Raft snapshots

    @Override
    public Map<String, Item> getAll() {
        return dataStore;
    }

    @Override
    public Map<String, byte[]> batchGet(Collection keys) {
        try {
            // Just as data routing analysis

            String key = buildKey("");
            final GetRequest request = GetRequest.builder()
                    .key(key)
                    .keys(keys)
                    .addValue("type", "batchGet")
                    .build();
            return protocol.getData(request);
        } catch (Exception e) {

        }
        return null;
    }

    @Override
    public void load(Map<String, Item> remoteData) {
        remoteData.forEach(new BiConsumer<String, Item>() {
            @Override
            public void accept(String s, Item item) {
                final String key = s;
                final T source = serializer.deSerialize(item.getBytes(), item.getClassName());
                operate(key, Pair.with(source, item.getBytes()), PUT_COMMAND);
            }
        });
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
                operate(originKey, Pair.with(source, data), PUT_COMMAND);
                return true;
            }
            if (StringUtils.equalsIgnoreCase(operation, REMOVE_COMMAND)) {
                operate(originKey, null, REMOVE_COMMAND);
                return true;
            }
            return false;
        }

        @Override
        public List<SnapshotOperate> loadSnapshotOperate() {
            return RaftKVStore.this.snapshotOperate == null ? Collections.emptyList()
                    : Collections.singletonList(RaftKVStore.this.snapshotOperate);
        }

        @Override
        public String bizInfo() {
            return storeName();
        }
    }

    String buildKey(String originKey) {
        return logProcessor.bizInfo() + "-" + originKey;
    }

    String getOriginKey(String key) {
        return key.replace(logProcessor.bizInfo() + "-", "");
    }

    public KVLogProcessor getLogProcessor() {
        return logProcessor;
    }

    class KvSuperFuncCaller {

        private final String contains = "contains";

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
                case contains:
                    return RaftKVStore.super.contains(key);
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
