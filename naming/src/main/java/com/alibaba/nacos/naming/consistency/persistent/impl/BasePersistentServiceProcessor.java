/*
 *  Copyright 1999-2018 Alibaba Group Holding Ltd.
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
 */

package com.alibaba.nacos.naming.consistency.persistent.impl;

import com.alibaba.nacos.common.utils.Objects;
import com.alibaba.nacos.consistency.SerializeFactory;
import com.alibaba.nacos.consistency.Serializer;
import com.alibaba.nacos.consistency.cp.RequestProcessor4CP;
import com.alibaba.nacos.consistency.entity.ReadRequest;
import com.alibaba.nacos.consistency.entity.Response;
import com.alibaba.nacos.consistency.entity.WriteRequest;
import com.alibaba.nacos.consistency.snapshot.SnapshotOperation;
import com.alibaba.nacos.core.exception.KvStorageException;
import com.alibaba.nacos.core.storage.kv.KvStorage;
import com.alibaba.nacos.naming.misc.UtilsAndCommons;
import com.alibaba.nacos.naming.utils.Constants;
import com.google.protobuf.ByteString;
import org.apache.commons.lang3.reflect.TypeUtils;

import java.nio.file.Paths;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Persist the operation class of the service store.
 *
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 */
public abstract class BasePersistentServiceProcessor extends RequestProcessor4CP {
    
    private final Collection<PersistentServiceOperateAdaptor> operators = new CopyOnWriteArrayList<>();
    
    protected final KvStorage kvStorage;
    
    protected final Serializer serializer;
    
    /**
     * Whether an unrecoverable error occurred.
     */
    protected volatile boolean hasError = false;
    
    /**
     * During snapshot processing, the processing of other requests needs to be paused.
     */
    protected final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    
    protected final ReentrantReadWriteLock.ReadLock readLock = lock.readLock();
    
    public BasePersistentServiceProcessor() throws Exception {
        this.kvStorage = new NamingKvStorage(Paths.get(UtilsAndCommons.DATA_BASE_DIR, "data").toString());
        this.serializer = SerializeFactory.getSerializer("JSON");
    }
    
    public final void registerOperator(final PersistentServiceOperateAdaptor operator) {
        operator.setProcessor(this);
        this.operators.add(operator);
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
            PersistentServiceOperateAdaptor operator = null;
            for (PersistentServiceOperateAdaptor operateAdaptor : operators) {
                if (operateAdaptor.interested(request.getExtendInfoMap())) {
                    operator = operateAdaptor;
                    break;
                }
            }
            Objects.requireNonNull(operator, "PersistentServiceOperateAdaptor");
            boolean needSave = operator.handleApply(op, bwRequest);
            
            if (!needSave) {
                return Response.newBuilder().setSuccess(true).build();
            }
            
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
            
            return Response.newBuilder().setSuccess(true).build();
        } catch (KvStorageException e) {
            return Response.newBuilder().setSuccess(false).setErrMsg(e.getErrMsg()).build();
        } catch (Throwable e) {
            return Response.newBuilder().setSuccess(false).setErrMsg(e.getMessage()).build();
        } finally {
            lock.unlock();
        }
    }
    
    @Override
    public List<SnapshotOperation> loadSnapshotOperate() {
        return Collections.singletonList(new NamingSnapshotOperation(this.kvStorage, lock));
    }
    
    @Override
    public void onError(Throwable error) {
        hasError = true;
    }
    
    @Override
    public String group() {
        return Constants.NAMING_PERSISTENT_SERVICE_GROUP;
    }
    
    public abstract boolean isAvailable();
    
    public KvStorage getKvStorage() {
        return kvStorage;
    }
    
    protected abstract Response read(Object ctx) throws Exception;
    
    protected abstract Response write(final BatchWriteRequest request, final String op) throws Exception;
    
    protected abstract Response write(final String key, final String op, final BatchWriteRequest request)
            throws Exception;
    
    protected abstract Response write(final String key, final String op, final BatchWriteRequest request,
            final Map<String, String> extendInfo) throws Exception;
}
