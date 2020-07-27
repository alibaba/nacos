/*
 * Copyright 1999-2018 Alibaba Group Holding Ltd.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */

package com.alibaba.nacos.naming.consistency.persistent.impl;

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.exception.runtime.NacosRuntimeException;
import com.alibaba.nacos.common.notify.NotifyCenter;
import com.alibaba.nacos.common.utils.ByteUtils;
import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.consistency.SerializeFactory;
import com.alibaba.nacos.consistency.Serializer;
import com.alibaba.nacos.consistency.cp.CPProtocol;
import com.alibaba.nacos.consistency.cp.LogProcessor4CP;
import com.alibaba.nacos.consistency.cp.MetadataKey;
import com.alibaba.nacos.consistency.entity.GetRequest;
import com.alibaba.nacos.consistency.entity.Log;
import com.alibaba.nacos.consistency.entity.Response;
import com.alibaba.nacos.consistency.snapshot.SnapshotOperation;
import com.alibaba.nacos.core.distributed.raft.RaftConfig;
import com.alibaba.nacos.core.exception.ErrorCode;
import com.alibaba.nacos.core.exception.RocksStorageException;
import com.alibaba.nacos.core.storage.RocksStorage;
import com.alibaba.nacos.naming.consistency.Datum;
import com.alibaba.nacos.naming.consistency.RecordListener;
import com.alibaba.nacos.naming.consistency.persistent.JudgeClusterVersionJob;
import com.alibaba.nacos.naming.consistency.persistent.PersistentConsistencyService;
import com.alibaba.nacos.naming.consistency.persistent.PersistentNotifier;
import com.alibaba.nacos.naming.consistency.persistent.ValueChangeEvent;
import com.alibaba.nacos.naming.consistency.persistent.raft.RaftStore;
import com.alibaba.nacos.naming.misc.GlobalExecutor;
import com.alibaba.nacos.naming.misc.Loggers;
import com.alibaba.nacos.naming.misc.UtilsAndCommons;
import com.alibaba.nacos.naming.pojo.Record;
import com.alibaba.nacos.naming.utils.Constants;
import com.google.protobuf.ByteString;
import org.springframework.stereotype.Service;

import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 */
@Service
public class PersistentServiceProcessor extends LogProcessor4CP implements PersistentConsistencyService {
    
    enum Op {
        Write("Write"),
        
        Read("Read"),
        
        Delete("Delete");
        
        private String desc;
        
        Op(String desc) {
            this.desc = desc;
        }
    }
    
    private final CPProtocol<RaftConfig, LogProcessor4CP> protocol;
    
    private final RocksStorage rocksStorage;
    
    private final RaftStore oldStore;
    
    private final JudgeClusterVersionJob clusterVersionJob;
    
    private final Serializer serializer = SerializeFactory.getDefault();
    
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    
    private final ReentrantReadWriteLock.ReadLock readLock = lock.readLock();
    
    private final PersistentNotifier notifier;
    
    private volatile boolean hasLeader = false;
    
    public PersistentServiceProcessor(final CPProtocol<RaftConfig, LogProcessor4CP> protocol,
            final JudgeClusterVersionJob clusterVersionJob, final RaftStore oldStore) {
        this.protocol = protocol;
        this.oldStore = oldStore;
        this.clusterVersionJob = clusterVersionJob;
        this.rocksStorage = RocksStorage
                .createDefault("naming-persistent", Paths.get(UtilsAndCommons.DATA_BASE_DIR, "persistent").toString());
        this.notifier = new PersistentNotifier(key -> {
            try {
                byte[] data = rocksStorage.get(ByteUtils.toBytes(key));
                return serializer.deserialize(data);
            } catch (RocksStorageException ex) {
                throw new NacosRuntimeException(ex.getErrCode(), ex.getErrMsg());
            }
        });
        init();
    }
    
    private void init() {
        NotifyCenter.registerToPublisher(ValueChangeEvent.class, 16384);
        NotifyCenter.registerSubscriber(notifier);
        this.protocol.addLogProcessors(Collections.singletonList(this));
        this.startLoadOldData();
        this.protocol.protocolMetaData()
                .subscribe(Constants.NAMING_PERSISTENT_SERVICE_GROUP, MetadataKey.LEADER_META_DATA,
                        (o, arg) -> hasLeader = StringUtils.isNotBlank((String) arg));
    }
    
    @Override
    public Response onRequest(GetRequest request) {
        final List<byte[]> keys = serializer.deserialize(request.getData().toByteArray(), List.class);
        final Lock lock = readLock;
        lock.lock();
        try {
            final Map<byte[], byte[]> result = rocksStorage.batchGet(keys);
            return Response.newBuilder().setSuccess(true).setData(ByteString.copyFrom(serializer.serialize(result)))
                    .build();
        } catch (RocksStorageException e) {
            return Response.newBuilder().setSuccess(false).setErrMsg(e.getErrMsg()).build();
        } finally {
            lock.unlock();
        }
    }
    
    @Override
    public Response onApply(Log log) {
        final byte[] data = log.getData().toByteArray();
        final BatchWriteRequest request = serializer.deserialize(data, BatchWriteRequest.class);
        final Op op = Op.valueOf(log.getOperation());
        final Lock lock = readLock;
        lock.lock();
        try {
            switch (op) {
                case Write:
                    rocksStorage.batchWrite(request.getKeys(), request.getValues());
                case Delete:
                    rocksStorage.batchDelete(request.getKeys());
            }
            return Response.newBuilder().setSuccess(true).build();
        } catch (RocksStorageException e) {
            return Response.newBuilder().setSuccess(false).setErrMsg(e.getErrMsg()).build();
        } finally {
            lock.unlock();
        }
    }
    
    @Override
    public String group() {
        return Constants.NAMING_PERSISTENT_SERVICE_GROUP;
    }
    
    @Override
    public List<SnapshotOperation> loadSnapshotOperate() {
        return Collections.singletonList(new NamingSnapshotOperation(this.rocksStorage, lock));
    }
    
    private void startLoadOldData() {
        GlobalExecutor.submitLoadOldData(this::loadFromOldData, TimeUnit.SECONDS.toMillis(5));
    }
    
    /**
     * Pull old data into the new data store.
     */
    public void loadFromOldData() {
        try {
            if (clusterVersionJob.isAllMemberIsNewVersion()) {
                return;
            }
            if (protocol.isLeader(Constants.NAMING_PERSISTENT_SERVICE_GROUP)) {
                Map<String, Datum> datumMap = new HashMap<>(64);
                oldStore.loadDatums(null, datumMap);
                int totalSize = datumMap.size();
                List<byte[]> keys = new ArrayList<>(totalSize);
                List<byte[]> values = new ArrayList<>(totalSize);
                int batchSize = 100;
                for (Map.Entry<String, Datum> entry : datumMap.entrySet()) {
                    totalSize--;
                    keys.add(ByteUtils.toBytes(entry.getKey()));
                    values.add(serializer.serialize(entry.getValue().value));
                    if (keys.size() == batchSize || totalSize == 0) {
                        BatchWriteRequest request = new BatchWriteRequest();
                        request.setKeys(keys);
                        request.setValues(values);
                        protocol.submitAsync(Log.newBuilder().setGroup(Constants.NAMING_PERSISTENT_SERVICE_GROUP)
                                .setData(ByteString.copyFrom(serializer.serialize(request))).build())
                                .whenComplete(((response, throwable) -> {
                                    if (throwable == null) {
                                        Loggers.RAFT.error("submit old raft data result : {}", response);
                                    } else {
                                        Loggers.RAFT.error("submit old raft data occur exception : {}", throwable);
                                    }
                                }));
                        keys.clear();
                        values.clear();
                    }
                }
            }
        } catch (Throwable ex) {
            Loggers.RAFT.error("load old raft data occur exception : {}", ex);
        }
        GlobalExecutor.submitLoadOldData(this::loadFromOldData, TimeUnit.SECONDS.toMillis(5));
    }
    
    @Override
    public void put(String key, Record value) throws NacosException {
        final BatchWriteRequest req = new BatchWriteRequest();
        req.append(ByteUtils.toBytes(key), serializer.serialize(value));
        final Log log = Log.newBuilder().setData(ByteString.copyFrom(serializer.serialize(req)))
                .setGroup(Constants.NAMING_PERSISTENT_SERVICE_GROUP).setOperation(Op.Write.desc).build();
        try {
            protocol.submit(log);
        } catch (Exception e) {
            throw new NacosException(ErrorCode.ProtoSubmitError.getCode(), e.getMessage());
        }
    }
    
    @Override
    public void remove(String key) throws NacosException {
        final BatchWriteRequest req = new BatchWriteRequest();
        req.append(ByteUtils.toBytes(key), ByteUtils.EMPTY);
        final Log log = Log.newBuilder().setData(ByteString.copyFrom(serializer.serialize(req)))
                .setGroup(Constants.NAMING_PERSISTENT_SERVICE_GROUP).setOperation(Op.Delete.desc).build();
        try {
            protocol.submit(log);
        } catch (Exception e) {
            throw new NacosException(ErrorCode.ProtoSubmitError.getCode(), e.getMessage());
        }
    }
    
    @Override
    public Datum get(String key) throws NacosException {
        final List<byte[]> keys = new ArrayList<>(1);
        keys.add(ByteUtils.toBytes(key));
        final GetRequest req = GetRequest.newBuilder().setGroup(Constants.NAMING_PERSISTENT_SERVICE_GROUP)
                .setData(ByteString.copyFrom(serializer.serialize(keys))).build();
        try {
            Response resp = protocol.getData(req);
            if (resp.getSuccess()) {
                return serializer.deserialize(resp.getData().toByteArray(), Datum.class);
            }
            throw new NacosException(ErrorCode.ProtoReadError.getCode(), resp.getErrMsg());
        } catch (Exception e) {
            throw new NacosException(ErrorCode.ProtoReadError.getCode(), e.getMessage());
        }
    }
    
    @Override
    public void listen(String key, RecordListener listener) throws NacosException {
        notifier.registerListener(key, listener);
    }
    
    @Override
    public void unListen(String key, RecordListener listener) throws NacosException {
        notifier.deregisterListener(key, listener);
    }
    
    @Override
    public boolean isAvailable() {
        return hasLeader;
    }
}
