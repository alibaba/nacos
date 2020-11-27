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

import com.alibaba.nacos.common.notify.NotifyCenter;
import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.consistency.SerializeFactory;
import com.alibaba.nacos.consistency.Serializer;
import com.alibaba.nacos.consistency.cp.CPProtocol;
import com.alibaba.nacos.consistency.cp.MetadataKey;
import com.alibaba.nacos.consistency.cp.RequestProcessor4CP;
import com.alibaba.nacos.consistency.entity.ReadRequest;
import com.alibaba.nacos.consistency.entity.Response;
import com.alibaba.nacos.consistency.entity.WriteRequest;
import com.alibaba.nacos.consistency.snapshot.SnapshotOperation;
import com.alibaba.nacos.core.distributed.ProtocolManager;
import com.alibaba.nacos.core.exception.KvStorageException;
import com.alibaba.nacos.core.storage.kv.KvStorage;
import com.alibaba.nacos.naming.consistency.ValueChangeEvent;
import com.alibaba.nacos.naming.misc.Loggers;
import com.alibaba.nacos.naming.misc.UtilsAndCommons;
import com.alibaba.nacos.naming.utils.Constants;
import com.google.protobuf.ByteString;
import org.apache.commons.lang3.reflect.TypeUtils;
import org.springframework.stereotype.Service;

import java.nio.file.Paths;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * New service data persistence handler.
 *
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 */
@SuppressWarnings({"PMD.ServiceOrDaoClassShouldEndWithImplRule", "unchecked"})
@Service
public class PersistentServiceProcessor extends RequestProcessor4CP {
    
    private final Collection<PersistentServiceOperator> operators = new CopyOnWriteArrayList<>();
    
    private final CPProtocol protocol;
    
    private final KvStorage kvStorage;
    
    private final Serializer serializer;
    
    /**
     * During snapshot processing, the processing of other requests needs to be paused.
     */
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    
    private final ReentrantReadWriteLock.ReadLock readLock = lock.readLock();
    
    /**
     * Is there a leader node currently.
     */
    private volatile boolean hasLeader = false;
    
    /**
     * Whether an unrecoverable error occurred.
     */
    private volatile boolean hasError = false;
    
    public PersistentServiceProcessor() throws Exception {
        this.protocol = ProtocolManager.getCpProtocol();
        this.kvStorage = new NamingKvStorage(Paths.get(UtilsAndCommons.DATA_BASE_DIR, "data").toString());
        this.serializer = SerializeFactory.getSerializer("JSON");
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
        waitLeader();
    }
    
    public void registerOperator(final PersistentServiceOperator operator) {
        operator.setProcessor(this);
        this.operators.add(operator);
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
    
    protected Response read(Object ctx) throws Exception {
        final ReadRequest req = ReadRequest.newBuilder().setGroup(Constants.NAMING_PERSISTENT_SERVICE_GROUP)
                .setData(ByteString.copyFrom(serializer.serialize(ctx))).build();
        return protocol.getData(req);
    }
    
    protected Response write(final PersistentServiceOperator operator, final BatchWriteRequest request, final String op)
            throws Exception {
        return write(operator, StringUtils.EMPTY, op, request);
    }
    
    protected Response write(final PersistentServiceOperator operator, final String key, final String op,
            final BatchWriteRequest request) throws Exception {
        return protocol.submit(WriteRequest.newBuilder().setKey(operator.prefix() + key)
                .setData(ByteString.copyFrom(serializer.serialize(request))).setGroup(group()).setOperation(op)
                .build());
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
            operators.parallelStream().filter(operator -> operator.interested(request.getKey()))
                    .forEach(operator -> operator.onApply(op, bwRequest));
            return Response.newBuilder().setSuccess(true).build();
        } catch (KvStorageException e) {
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
        return Collections.singletonList(new NamingSnapshotOperation(this.kvStorage, lock));
    }
    
    @Override
    public void onError(Throwable error) {
        super.onError(error);
        hasError = true;
    }
    
    public boolean isAvailable() {
        return hasLeader && !hasError;
    }
    
    public KvStorage getKvStorage() {
        return kvStorage;
    }
    
}
