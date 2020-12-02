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

package com.alibaba.nacos.naming.consistency.persistent.impl;

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.exception.runtime.NacosRuntimeException;
import com.alibaba.nacos.common.notify.NotifyCenter;
import com.alibaba.nacos.common.utils.ByteUtils;
import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.consistency.DataOperation;
import com.alibaba.nacos.consistency.SerializeFactory;
import com.alibaba.nacos.consistency.Serializer;
import com.alibaba.nacos.consistency.cp.CPProtocol;
import com.alibaba.nacos.consistency.cp.RequestProcessor4CP;
import com.alibaba.nacos.consistency.cp.MetadataKey;
import com.alibaba.nacos.consistency.entity.ReadRequest;
import com.alibaba.nacos.consistency.entity.Response;
import com.alibaba.nacos.consistency.entity.WriteRequest;
import com.alibaba.nacos.consistency.snapshot.SnapshotOperation;
import com.alibaba.nacos.core.distributed.ProtocolManager;
import com.alibaba.nacos.core.exception.ErrorCode;
import com.alibaba.nacos.core.exception.KvStorageException;
import com.alibaba.nacos.core.storage.kv.KvStorage;
import com.alibaba.nacos.naming.consistency.Datum;
import com.alibaba.nacos.naming.consistency.KeyBuilder;
import com.alibaba.nacos.naming.consistency.RecordListener;
import com.alibaba.nacos.naming.consistency.ValueChangeEvent;
import com.alibaba.nacos.naming.consistency.persistent.ClusterVersionJudgement;
import com.alibaba.nacos.naming.consistency.persistent.PersistentConsistencyService;
import com.alibaba.nacos.naming.consistency.persistent.PersistentNotifier;
import com.alibaba.nacos.naming.misc.Loggers;
import com.alibaba.nacos.naming.misc.UtilsAndCommons;
import com.alibaba.nacos.naming.pojo.Record;
import com.alibaba.nacos.naming.utils.Constants;
import com.alibaba.nacos.sys.utils.ApplicationUtils;
import com.google.protobuf.ByteString;
import org.apache.commons.lang3.reflect.TypeUtils;
import org.springframework.stereotype.Service;

import java.lang.reflect.Type;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * New service data persistence handler.
 *
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 */
@SuppressWarnings("PMD.ServiceOrDaoClassShouldEndWithImplRule")
@Service
public class PersistentServiceProcessor extends RequestProcessor4CP implements PersistentConsistencyService {
    
    enum Op {
        /**
         * write ops.
         */
        Write("Write"),
        
        /**
         * read ops.
         */
        Read("Read"),
        
        /**
         * delete ops.
         */
        Delete("Delete");
        
        private final String desc;
        
        Op(String desc) {
            this.desc = desc;
        }
    }
    
    private final CPProtocol protocol;
    
    private final KvStorage kvStorage;
    
    private final ClusterVersionJudgement versionJudgement;
    
    private final Serializer serializer;
    
    /**
     * During snapshot processing, the processing of other requests needs to be paused.
     */
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    
    private final ReentrantReadWriteLock.ReadLock readLock = lock.readLock();
    
    private final PersistentNotifier notifier;
    
    /**
     * Is there a leader node currently.
     */
    private volatile boolean hasLeader = false;
    
    /**
     * Whether an unrecoverable error occurred.
     */
    private volatile boolean hasError = false;
    
    /**
     * If use old raft, should not notify listener even new listener add.
     */
    private volatile boolean startNotify = false;
    
    public PersistentServiceProcessor(final ProtocolManager protocolManager,
            final ClusterVersionJudgement versionJudgement) throws Exception {
        this.protocol = protocolManager.getCpProtocol();
        this.versionJudgement = versionJudgement;
        this.kvStorage = new NamingKvStorage(Paths.get(UtilsAndCommons.DATA_BASE_DIR, "data").toString());
        this.serializer = SerializeFactory.getSerializer("JSON");
        this.notifier = new PersistentNotifier(key -> {
            try {
                byte[] data = kvStorage.get(ByteUtils.toBytes(key));
                Datum datum = serializer.deserialize(data, getDatumTypeFromKey(key));
                return null != datum ? datum.value : null;
            } catch (KvStorageException ex) {
                throw new NacosRuntimeException(ex.getErrCode(), ex.getErrMsg());
            }
        });
        init();
    }
    
    @SuppressWarnings("unchecked")
    private void init() {
        NotifyCenter.registerToPublisher(ValueChangeEvent.class, 16384);
        this.protocol.addLogProcessors(Collections.singletonList(this));
        this.protocol.protocolMetaData()
                .subscribe(Constants.NAMING_PERSISTENT_SERVICE_GROUP, MetadataKey.LEADER_META_DATA,
                        (o, arg) -> hasLeader = StringUtils.isNotBlank(String.valueOf(arg)));
        // If you choose to use the new RAFT protocol directly, there will be no compatible logical execution
        if (ApplicationUtils.getProperty(Constants.NACOS_NAMING_USE_NEW_RAFT_FIRST, Boolean.class, false)) {
            NotifyCenter.registerSubscriber(notifier);
            waitLeader();
            startNotify = true;
        } else {
            this.versionJudgement.registerObserver(isNewVersion -> {
                if (isNewVersion) {
                    NotifyCenter.registerSubscriber(notifier);
                    startNotify = true;
                }
            }, 10);
        }
    }
    
    private void waitLeader() {
        while (!hasLeader && !hasError) {
            Loggers.RAFT.info("Waiting Jraft leader vote ...");
            try {
                TimeUnit.MILLISECONDS.sleep(500);
            } catch (InterruptedException ignored) {
            }
        }
    }
    
    @Override
    public Response onRequest(ReadRequest request) {
        final List<byte[]> keys = serializer
                .deserialize(request.getData().toByteArray(), TypeUtils.parameterize(List.class, byte[].class));
        final Lock lock = readLock;
        lock.lock();
        try {
            final Map<byte[], byte[]> result = kvStorage.batchGet(keys);
            final BatchReadResponse response = new BatchReadResponse();
            result.forEach(response::append);
            return Response.newBuilder().setSuccess(true).setData(ByteString.copyFrom(serializer.serialize(response)))
                    .build();
        } catch (KvStorageException e) {
            return Response.newBuilder().setSuccess(false).setErrMsg(e.getErrMsg()).build();
        } finally {
            lock.unlock();
        }
    }
    
    @Override
    public Response onApply(WriteRequest request) {
        final byte[] data = request.getData().toByteArray();
        final BatchWriteRequest bwRequest = serializer.deserialize(data, BatchWriteRequest.class);
        final Op op = Op.valueOf(request.getOperation());
        final Lock lock = readLock;
        lock.lock();
        try {
            switch (op) {
                case Write:
                    kvStorage.batchPut(bwRequest.getKeys(), bwRequest.getValues());
                    break;
                case Delete:
                    kvStorage.batchDelete(bwRequest.getKeys());
                    break;
                default:
                    return Response.newBuilder().setSuccess(false).setErrMsg("unsupport operation : " + op).build();
            }
            publishValueChangeEvent(op, bwRequest);
            return Response.newBuilder().setSuccess(true).build();
        } catch (KvStorageException e) {
            return Response.newBuilder().setSuccess(false).setErrMsg(e.getErrMsg()).build();
        } finally {
            lock.unlock();
        }
    }
    
    private void publishValueChangeEvent(final Op op, final BatchWriteRequest request) {
        final List<byte[]> keys = request.getKeys();
        final List<byte[]> values = request.getValues();
        for (int i = 0; i < keys.size(); i++) {
            final String key = new String(keys.get(i));
            final Datum datum = serializer.deserialize(values.get(i), getDatumTypeFromKey(key));
            final Record value = null != datum ? datum.value : null;
            final ValueChangeEvent event = ValueChangeEvent.builder().key(key).value(value)
                    .action(Op.Delete.equals(op) ? DataOperation.DELETE : DataOperation.CHANGE).build();
            NotifyCenter.publishEvent(event);
        }
    }
    
    @Override
    public String group() {
        return Constants.NAMING_PERSISTENT_SERVICE_GROUP;
    }
    
    @Override
    public List<SnapshotOperation> loadSnapshotOperate() {
        return Collections.singletonList(new NamingSnapshotOperation(this.kvStorage, lock));
    }
    
    @Override
    public void put(String key, Record value) throws NacosException {
        final BatchWriteRequest req = new BatchWriteRequest();
        Datum datum = Datum.createDatum(key, value);
        req.append(ByteUtils.toBytes(key), serializer.serialize(datum));
        final WriteRequest request = WriteRequest.newBuilder().setData(ByteString.copyFrom(serializer.serialize(req)))
                .setGroup(Constants.NAMING_PERSISTENT_SERVICE_GROUP).setOperation(Op.Write.desc).build();
        try {
            protocol.submit(request);
        } catch (Exception e) {
            throw new NacosException(ErrorCode.ProtoSubmitError.getCode(), e.getMessage());
        }
    }
    
    @Override
    public void remove(String key) throws NacosException {
        final BatchWriteRequest req = new BatchWriteRequest();
        req.append(ByteUtils.toBytes(key), ByteUtils.EMPTY);
        final WriteRequest request = WriteRequest.newBuilder().setData(ByteString.copyFrom(serializer.serialize(req)))
                .setGroup(Constants.NAMING_PERSISTENT_SERVICE_GROUP).setOperation(Op.Delete.desc).build();
        try {
            protocol.submit(request);
        } catch (Exception e) {
            throw new NacosException(ErrorCode.ProtoSubmitError.getCode(), e.getMessage());
        }
    }
    
    @Override
    public Datum get(String key) throws NacosException {
        final List<byte[]> keys = new ArrayList<>(1);
        keys.add(ByteUtils.toBytes(key));
        final ReadRequest req = ReadRequest.newBuilder().setGroup(Constants.NAMING_PERSISTENT_SERVICE_GROUP)
                .setData(ByteString.copyFrom(serializer.serialize(keys))).build();
        try {
            Response resp = protocol.getData(req);
            if (resp.getSuccess()) {
                BatchReadResponse response = serializer
                        .deserialize(resp.getData().toByteArray(), BatchReadResponse.class);
                final List<byte[]> rValues = response.getValues();
                return rValues.isEmpty() ? null : serializer.deserialize(rValues.get(0), getDatumTypeFromKey(key));
            }
            throw new NacosException(ErrorCode.ProtoReadError.getCode(), resp.getErrMsg());
        } catch (Throwable e) {
            throw new NacosException(ErrorCode.ProtoReadError.getCode(), e.getMessage());
        }
    }
    
    @Override
    public void listen(String key, RecordListener listener) throws NacosException {
        notifier.registerListener(key, listener);
        if (startNotify) {
            notifierDatumIfAbsent(key, listener);
        }
    }
    
    @Override
    public void unListen(String key, RecordListener listener) throws NacosException {
        notifier.deregisterListener(key, listener);
    }
    
    @Override
    public void onError(Throwable error) {
        super.onError(error);
        hasError = true;
    }
    
    @Override
    public boolean isAvailable() {
        return hasLeader && !hasError;
    }
    
    private Type getDatumTypeFromKey(String key) {
        return TypeUtils.parameterize(Datum.class, getClassOfRecordFromKey(key));
    }
    
    private Class<? extends Record> getClassOfRecordFromKey(String key) {
        if (KeyBuilder.matchSwitchKey(key)) {
            return com.alibaba.nacos.naming.misc.SwitchDomain.class;
        } else if (KeyBuilder.matchServiceMetaKey(key)) {
            return com.alibaba.nacos.naming.core.Service.class;
        } else if (KeyBuilder.matchInstanceListKey(key)) {
            return com.alibaba.nacos.naming.core.Instances.class;
        }
        return Record.class;
    }
    
    private void notifierDatumIfAbsent(String key, RecordListener listener) throws NacosException {
        if (KeyBuilder.SERVICE_META_KEY_PREFIX.equals(key)) {
            notifierAllServiceMeta(listener);
        } else {
            Datum datum = get(key);
            if (null != datum) {
                notifierDatum(key, datum, listener);
            }
        }
    }
    
    /**
     * This notify should only notify once during startup. See {@link com.alibaba.nacos.naming.core.ServiceManager#init()}
     */
    private void notifierAllServiceMeta(RecordListener listener) throws NacosException {
        for (byte[] each : kvStorage.allKeys()) {
            String key = new String(each);
            if (listener.interests(key)) {
                Datum datum = get(key);
                if (null != datum) {
                    notifierDatum(key, datum, listener);
                }
            }
        }
    }
    
    private void notifierDatum(String key, Datum datum, RecordListener listener) {
        try {
            listener.onChange(key, datum.value);
        } catch (Exception e) {
            Loggers.RAFT.error("NACOS-RAFT failed to notify listener", e);
        }
    }
}
