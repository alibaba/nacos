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

import com.alibaba.fastjson.TypeReference;
import com.alibaba.nacos.common.SerializeFactory;
import com.alibaba.nacos.common.Serializer;
import com.alibaba.nacos.consistency.Config;
import com.alibaba.nacos.consistency.ConsistencyProtocol;
import com.alibaba.nacos.consistency.Log;
import com.alibaba.nacos.consistency.NLog;
import com.alibaba.nacos.consistency.cp.CPKvStore;
import com.alibaba.nacos.consistency.cp.LogProcessor4CP;
import com.alibaba.nacos.consistency.request.GetRequest;
import com.alibaba.nacos.consistency.request.GetResponse;
import com.alibaba.nacos.consistency.snapshot.SnapshotOperate;
import com.alibaba.nacos.core.distributed.raft.exception.RaftKVStoreException;
import com.alibaba.nacos.core.utils.Loggers;
import org.apache.commons.lang3.StringUtils;
import org.javatuples.Pair;

import java.util.ArrayList;
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

    private static final TypeReference<Collection<String>> reference = new TypeReference<Collection<String>>(){};

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
        final byte[] putData = serializer.serialize(data);

        final NLog log = NLog.builder()
                .key(key)
                .data(putData)
                .operation(PUT_COMMAND)
                .className(data.getClass().getCanonicalName())
                .build();

        logProcessor.commitAutoSetBiz(log);
        return true;
    }

    @Override
    public boolean remove(String key) throws Exception {
        final NLog log = NLog.builder()
                .key(key)
                .operation(REMOVE_COMMAND)
                .build();

        logProcessor.commitAutoSetBiz(log);

        return true;
    }

    @Override
    public boolean contains(String key) {
        GetResponse<Boolean> response;
        try {
            final GetRequest request = GetRequest.builder()
                    .biz(logProcessor.bizInfo())
                    .ctx(serializer.serialize(Collections.singletonList(key)))
                    .addInfo("type", "contains")
                    .build();
            response = protocol.getData(request);
        } catch (Exception e) {
            Loggers.RAFT.error("execute raft read operation : [{}] has error : {}", "contains", e);
            throw new RuntimeException(e);
        }
        if (response.getData() != null) {
            return response.getData();
        }
        throw new RaftKVStoreException(response.getExceptionName(), response.getErrMsg());
    }

    @Override
    public byte[] getByKey(String key) {
        GetResponse<byte[]> response;
        try {
            final GetRequest request = GetRequest.builder()
                    .biz(logProcessor.bizInfo())
                    .ctx(serializer.serialize(Collections.singletonList(key)))
                    .addInfo("type", "getByKey")
                    .build();
            response = protocol.getData(request);
        } catch (Exception e) {
            Loggers.RAFT.error("execute raft read operation : [{}] has error : {}", "getByKey", e);
            throw new RuntimeException(e);
        }
        if (response.getData() != null) {
            return response.getData();
        }
        throw new RaftKVStoreException(response.getExceptionName(), response.getErrMsg());
    }

    @Override
    public T getByKeyAutoConvert(String key) {
        GetResponse<T> response;
        try {
            final GetRequest request = GetRequest.builder()
                    .biz(logProcessor.bizInfo())
                    .ctx(serializer.serialize(Collections.singletonList(key)))
                    .addInfo("type", "getByKeyAutoConvert")
                    .build();
            response = protocol.getData(request);
        } catch (Exception e) {
            Loggers.RAFT.error("execute raft read operation : [{}] has error : {}", "getByKeyAutoConvert", e);
            throw new RuntimeException(e);
        }
        if (response.getData() != null) {
            return response.getData();
        }
        throw new RaftKVStoreException(response.getExceptionName(), response.getErrMsg());
    }

    @Override
    public Item getItemByKey(String key) {
        GetResponse<Item> response;
        try {
            final GetRequest request = GetRequest.builder()
                    .biz(logProcessor.bizInfo())
                    .ctx(serializer.serialize(Collections.singletonList(key)))
                    .addInfo("type", "getItemByKey")
                    .build();
            response = protocol.getData(request);
        } catch (Exception e) {
            Loggers.RAFT.error("execute raft read operation : [{}] has error : {}", "getItemByKey", e);
            throw new RuntimeException(e);
        }
        if (response.getData() != null) {
            return response.getData();
        }
        throw new RaftKVStoreException(response.getExceptionName(), response.getErrMsg());
    }

    @Override
    public Map<String, T> batchGetAutoConvert(Collection<String> keys) {
        GetResponse<Map<String, T>> response;
        try {

            // Just as data routing analysis

            final GetRequest request = GetRequest.builder()
                    .biz(logProcessor.bizInfo())
                    .ctx(serializer.serialize(keys))
                    .addInfo("type", "batchGetAutoConvert")
                    .build();
            response = protocol.getData(request);
        } catch (Exception e) {
            Loggers.RAFT.error("execute raft read operation : [{}] has error : {}", "batchGetAutoConvert", e);
            throw new RuntimeException(e);
        }
        if (response.getData() != null) {
            return response.getData();
        }
        throw new RaftKVStoreException(response.getExceptionName(), response.getErrMsg());
    }

    @Override
    public Map<String, Item> getItemByBatch(Collection<String> keys) {
        GetResponse<Map<String, Item>> response;
        try {
            final GetRequest request = GetRequest.builder()
                    .biz(logProcessor.bizInfo())
                    .ctx(serializer.serialize(keys))
                    .addInfo("type", "getItemByBatch")
                    .build();
            response = protocol.getData(request);
        } catch (Exception e) {
            Loggers.RAFT.error("execute raft read operation : [{}] has error : {}", "getItemByBatch", e);
            throw new RuntimeException(e);
        }
        if (response.getData() != null) {
            return response.getData();
        }
        throw new RaftKVStoreException(response.getExceptionName(), response.getErrMsg());
    }

    @Override
    public String getCheckSum(String key) {
        GetResponse<String> response;
        try {
            final GetRequest request = GetRequest.builder()
                    .biz(logProcessor.bizInfo())
                    .ctx(serializer.serialize(Collections.singletonList(key)))
                    .addInfo("type", "getCheckSum")
                    .build();
            response = protocol.getData(request);
        } catch (Exception e) {
            Loggers.RAFT.error("execute raft read operation : [{}] has error : {}", "getCheckSum", e);
            throw new RuntimeException(e);
        }
        if (response.getData() != null) {
            return response.getData();
        }
        throw new RaftKVStoreException(response.getExceptionName(), response.getErrMsg());
    }

    @Override
    public Collection<String> allKeys() {
        GetResponse<Collection<String>> response;
        try {
            final GetRequest request = GetRequest.builder()
                    .biz(logProcessor.bizInfo())
                    .addInfo("type", "allKeys")
                    .build();
            response = protocol.getData(request);
        } catch (Exception e) {
            Loggers.RAFT.error("execute raft read operation : [{}] has error : {}", "allKeys", e);
            throw new RuntimeException(e);
        }
        if (response.getData() != null) {
            return response.getData();
        }
        throw new RaftKVStoreException(response.getExceptionName(), response.getErrMsg());
    }

    // This operation does not guarantee read consistency, and is only used for Raft snapshots

    @Override
    public Map<String, Item> getAll() {
        return dataStore;
    }

    @Override
    public Map<String, byte[]> batchGet(Collection keys) {
        GetResponse<Map<String, byte[]>> response;
        try {
            final GetRequest request = GetRequest.builder()
                    .biz(logProcessor.bizInfo())
                    .ctx(serializer.serialize(keys))
                    .addInfo("type", "batchGet")
                    .build();
            response = protocol.getData(request);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        if (response.getData() != null) {
            return response.getData();
        }
        throw new RaftKVStoreException(response.getExceptionName(), response.getErrMsg());
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
        public ConsistencyProtocol<? extends Config> getProtocol() {
            return RaftKVStore.this.protocol;
        }

        @Override
        public <D> GetResponse<D> getData(GetRequest request) {
            final Collection<String> keys = serializer.deSerialize(request.getCtx(), Collection.class);
            final String type = request.getValue("type");
            try {
                return GetResponse.<D>builder()
                        .data((D) funcCaller.execute(type, new ArrayList<>(keys)))
                        .build();
            } catch (Exception e) {
                return GetResponse.<D>builder()
                        .exceptionName(e.getClass().getName())
                        .errMsg(e.getMessage())
                        .build();
            }
        }

        @Override
        public boolean onApply(Log log) {
            final String operation = log.getOperation();
            final String originKey = log.getKey();
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

        Object execute(String type, List<String> keys) {

            switch (type) {
                case contains:
                    return RaftKVStore.super.contains(keys.get(0));
                case getByKey:
                    return RaftKVStore.super.getByKey(keys.get(0));
                case getByKeyAutoConvert:
                    return RaftKVStore.super.getByKeyAutoConvert(keys.get(0));
                case getItemByKey:
                    return RaftKVStore.super.getItemByKey(keys.get(0));
                case batchGetAutoConvert:
                    return RaftKVStore.super.batchGetAutoConvert(keys);
                case getItemByBatch:
                    return RaftKVStore.super.getItemByBatch(keys);
                case getCheckSum:
                    return RaftKVStore.super.getCheckSum(keys.get(0));
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
