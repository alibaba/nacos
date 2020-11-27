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

package com.alibaba.nacos.naming.core;

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.exception.runtime.NacosRuntimeException;
import com.alibaba.nacos.common.notify.NotifyCenter;
import com.alibaba.nacos.common.utils.ByteUtils;
import com.alibaba.nacos.consistency.DataOperation;
import com.alibaba.nacos.consistency.SerializeFactory;
import com.alibaba.nacos.consistency.Serializer;
import com.alibaba.nacos.consistency.entity.Response;
import com.alibaba.nacos.core.exception.ErrorCode;
import com.alibaba.nacos.core.exception.KvStorageException;
import com.alibaba.nacos.naming.consistency.Datum;
import com.alibaba.nacos.naming.consistency.KeyBuilder;
import com.alibaba.nacos.naming.consistency.RecordListener;
import com.alibaba.nacos.naming.consistency.ValueChangeEvent;
import com.alibaba.nacos.naming.consistency.persistent.PersistentConsistencyService;
import com.alibaba.nacos.naming.consistency.persistent.PersistentNotifier;
import com.alibaba.nacos.naming.consistency.persistent.impl.BatchReadResponse;
import com.alibaba.nacos.naming.consistency.persistent.impl.BatchWriteRequest;
import com.alibaba.nacos.naming.consistency.persistent.impl.Op;
import com.alibaba.nacos.naming.consistency.persistent.impl.PersistentServiceOperator;
import com.alibaba.nacos.naming.consistency.persistent.impl.PersistentServiceProcessor;
import com.alibaba.nacos.naming.misc.Loggers;
import com.alibaba.nacos.naming.pojo.Record;
import org.apache.commons.lang3.reflect.TypeUtils;
import org.springframework.stereotype.Component;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

/**
 * Nacos service discovery module V1 version of the operation.
 *
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 */
@Component
public class PersistentServiceV1Operator extends PersistentServiceOperator implements PersistentConsistencyService {
    
    private final PersistentNotifier notifier;
    
    private final Serializer serializer = SerializeFactory.getSerializer("JSON");
    
    private final PersistentServiceProcessor processor;
    
    public PersistentServiceV1Operator(PersistentServiceProcessor processor) {
        this.processor = processor;
        processor.registerOperator(this);
        this.notifier = new PersistentNotifier(key -> {
            try {
                byte[] data = processor.getKvStorage().get(ByteUtils.toBytes(key));
                Datum datum = serializer.deserialize(data, getDatumTypeFromKey(key));
                return null != datum ? datum.value : null;
            } catch (KvStorageException ex) {
                throw new NacosRuntimeException(ex.getErrCode(), ex.getErrMsg());
            }
        });
        NotifyCenter.registerSubscriber(notifier);
    }
    
    @Override
    protected void onApply(Op op, BatchWriteRequest request) {
        publishValueChangeEvent(op, request);
    }
    
    @Override
    protected String prefix() {
        return "";
    }
    
    @Override
    public void put(String key, Record value) throws NacosException {
        final BatchWriteRequest req = new BatchWriteRequest();
        Datum datum = Datum.createDatum(key, value);
        req.append(ByteUtils.toBytes(key), serializer.serialize(datum));
        try {
            write(req, Op.Write.getDesc());
        } catch (Exception e) {
            throw new NacosException(ErrorCode.ProtoSubmitError.getCode(), e.getMessage());
        }
    }
    
    @Override
    public void remove(String key) throws NacosException {
        final BatchWriteRequest req = new BatchWriteRequest();
        req.append(ByteUtils.toBytes(key), ByteUtils.EMPTY);
        try {
            write(req, Op.Delete.getDesc());
        } catch (Exception e) {
            throw new NacosException(ErrorCode.ProtoSubmitError.getCode(), e.getMessage());
        }
    }
    
    @Override
    public Datum get(String key) throws NacosException {
        final List<byte[]> keys = new ArrayList<>(1);
        keys.add(ByteUtils.toBytes(key));
        try {
            Response resp = read(keys);
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
        notifierDatumIfAbsent(key, listener);
    }
    
    @Override
    public void unListen(String key, RecordListener listener) throws NacosException {
        notifier.deregisterListener(key, listener);
    }
    
    @Override
    public boolean isAvailable() {
        return processor.isAvailable();
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
        for (byte[] each : processor.getKvStorage().allKeys()) {
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
