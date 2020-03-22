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
import com.alibaba.nacos.consistency.Log;
import com.alibaba.nacos.consistency.LogFuture;
import com.alibaba.nacos.consistency.Serializer;
import com.alibaba.nacos.consistency.Config;
import com.alibaba.nacos.consistency.ConsistencyProtocol;
import com.alibaba.nacos.consistency.cp.CPKvStore;
import com.alibaba.nacos.consistency.cp.LogProcessor4CP;
import com.alibaba.nacos.consistency.exception.KvException;
import com.alibaba.nacos.consistency.request.GetRequest;
import com.alibaba.nacos.consistency.request.GetResponse;
import com.alibaba.nacos.consistency.snapshot.SnapshotOperation;
import com.alibaba.nacos.core.distributed.raft.exception.RaftKVStoreException;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;

import org.apache.commons.lang3.StringUtils;

/**
 * Strong consistency key-value pair storage
 * Implementation class needs to be discovered by Spring
 *
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 */
@SuppressWarnings("all")
class RaftKVStore<T> extends CPKvStore<T> {

    private final TypeReference<Collection<String>> reference = new TypeReference<Collection<String>>() {
    };

    private KVLogProcessor logProcessor;

    private ConsistencyProtocol<? extends Config> protocol;

    private KvSuperFuncCaller funcCaller;

    public RaftKVStore(String name, Serializer serializer, SnapshotOperation snapshotOperation) {
        super(name, serializer, snapshotOperation);
        this.logProcessor = new KVLogProcessor();
        this.funcCaller = new KvSuperFuncCaller();
    }

    @Override
    public boolean put(String key, Object data) throws Exception {
        final Log log = Log.newBuilder()
                .key(key)
                .data(data)
                .operation(PUT_COMMAND)
                .className(data.getClass().getCanonicalName())
                .build();
        LogFuture future = logProcessor.commitAutoSetGroup(log);
        if (future.isOk()) {
            return true;
        }
        throw new KvException(future.getError());
    }

    @Override
    public boolean remove(String key) throws Exception {
        final Log log = Log.newBuilder()
                .key(key)
                .operation(REMOVE_COMMAND)
                .build();
        LogFuture future = logProcessor.commitAutoSetGroup(log);
        if (future.isOk()) {
            return true;
        }
        throw new KvException(future.getError());
    }

    @Override
    public boolean contains(String key) {
        GetResponse<Boolean> response = request(key, "contains");
        if (StringUtils.isNotEmpty(response.getErrMsg())) {
            throw new RaftKVStoreException(response.getExceptionName(), response.getErrMsg());
        }
        return response.getData();
    }

    @Override
    public T get(String key) {
        GetResponse<T> response = request(key, "getByKeyAutoConvert");
        if (StringUtils.isNotEmpty(response.getErrMsg())) {
            throw new RaftKVStoreException(response.getExceptionName(), response.getErrMsg());
        }
        return response.getData();
    }

    @Override
    public Item getItemByKey(String key) {
        GetResponse<Item> response = request(key, "getItemByKey");
        if (StringUtils.isNotEmpty(response.getErrMsg())) {
            throw new RaftKVStoreException(response.getExceptionName(), response.getErrMsg());
        }
        return response.getData();
    }

    @Override
    public Map<String, T> batchGetAutoConvert(Collection<String> keys) {
        GetResponse<Map<String, T>> response = request(keys, "batchGetAutoConvert");
        if (StringUtils.isNotEmpty(response.getErrMsg())) {
            throw new RaftKVStoreException(response.getExceptionName(), response.getErrMsg());
        }
        return response.getData();
    }

    @Override
    public Map<String, Item> getItemByBatch(Collection<String> keys) {
        GetResponse<Map<String, Item>> response = request(keys, "getItemByBatch");
        if (StringUtils.isNotEmpty(response.getErrMsg())) {
            throw new RaftKVStoreException(response.getExceptionName(), response.getErrMsg());
        }
        return response.getData();
    }

    @Override
    public Collection<String> allKeys() {
        GetResponse<Collection<String>> response = request(null, "allKeys");
        if (StringUtils.isNotEmpty(response.getErrMsg())) {
            throw new RaftKVStoreException(response.getExceptionName(), response.getErrMsg());
        }
        return response.getData();
    }

    // This operation does not guarantee read consistency, and is only used for Raft snapshots

    @Override
    public Map<String, Item> getAll() {
        return dataStore;
    }

    @Override
    public Map<String, byte[]> batchGet(Collection keys) {
        GetResponse<Map<String, byte[]>> response = request(keys, "batchGet");
        if (StringUtils.isNotEmpty(response.getErrMsg())) {
            throw new RaftKVStoreException(response.getExceptionName(), response.getErrMsg());
        }
        return response.getData();
    }

    // This operation is limited to the Snapshot operation of Raft to
    // quickly recover data and is not available for other operations

    @Override
    public void load(Map<String, Item> remoteData) {
        remoteData.forEach(new BiConsumer<String, Item>() {
            @Override
            public void accept(String s, Item item) {
                final String key = s;
                final Object source = item.getData();
                operate(key, source, PUT_COMMAND);
            }
        });
    }

    private <R> GetResponse<R> request(Object arg, String type) {
        try {
            final GetRequest request = GetRequest.builder()
                    .group(logProcessor.group())
                    .addInfo("type", type)
                    .build();

            if (arg != null) {
                request.setCtx(serializer.serialize(arg));
            }

            return protocol.getData(request);
        } catch (Exception e) {
            throw new RaftKVStoreException(e, e.getClass().getCanonicalName());
        }
    }

    public KVLogProcessor getLogProcessor() {
        return logProcessor;
    }

    class KVLogProcessor extends LogProcessor4CP {

        @Override
        public <D> GetResponse<D> getData(GetRequest request) {
            final String type = request.getValue("type");
            try {
                return GetResponse.<D>builder()
                        .data((D) funcCaller.execute(type, request.getCtx()))
                        .build();
            } catch (Exception e) {
                return GetResponse.<D>builder()
                        .exceptionName(e.getClass().getCanonicalName())
                        .errMsg(e.getMessage())
                        .build();
            }
        }

        @Override
        public LogFuture onApply(Log log) {
            final String operation = log.getOperation();
            final String originKey = log.getKey();
            try {
                if (StringUtils.equalsIgnoreCase(operation, PUT_COMMAND)) {
                    final Object source = log.getData();
                    operate(originKey, source, PUT_COMMAND);
                    return LogFuture.success(true);
                }
                if (StringUtils.equalsIgnoreCase(operation, REMOVE_COMMAND)) {
                    operate(originKey, null, REMOVE_COMMAND);
                    return LogFuture.success(true);
                }
                return LogFuture.fail(new UnsupportedOperationException(
                        "This data operation is not supported : " + operation));
            } catch (Throwable t) {
                return LogFuture.fail(t);
            }
        }

        @Override
        public List<SnapshotOperation> loadSnapshotOperate() {
            return RaftKVStore.this.snapshotOperation == null ? Collections.emptyList()
                    : Collections.singletonList(RaftKVStore.this.snapshotOperation);
        }

        @Override
        public String group() {
            return storeName();
        }
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

        Object execute(String type, byte[] requestBody) {
            String key = null;
            Collection<String> keys = Collections.emptyList();
            switch (type) {
                case contains:
                    key = serializer.deserialize(requestBody, String.class);
                    return RaftKVStore.super.contains(key);
                case getByKeyAutoConvert:
                    key = serializer.deserialize(requestBody, String.class);
                    return RaftKVStore.super.get(key);
                case getItemByKey:
                    key = serializer.deserialize(requestBody, String.class);
                    return RaftKVStore.super.getItemByKey(key);
                case batchGetAutoConvert:
                    keys = serializer.deserialize(requestBody, Collection.class);
                    return RaftKVStore.super.batchGetAutoConvert(keys);
                case getItemByBatch:
                    keys = serializer.deserialize(requestBody, Collection.class);
                    return RaftKVStore.super.getItemByBatch(keys);
                case allKeys:
                    return RaftKVStore.super.allKeys();
                case getAll:
                    return RaftKVStore.super.getAll();
                case batchGet:
                    keys = serializer.deserialize(requestBody, Collection.class);
                    return RaftKVStore.super.batchGet(keys);
                default:
                    throw new UnsupportedOperationException();
            }

        }

    }

}
