/*
 * Copyright 1999-2019 Alibaba Group Holding Ltd.
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
package com.alibaba.nacos.naming.consistency.ephemeral.simple;

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.naming.consistency.Datum;
import com.alibaba.nacos.naming.consistency.KeyBuilder;
import com.alibaba.nacos.naming.consistency.Operation;
import com.alibaba.nacos.naming.consistency.RecordListener;
import com.alibaba.nacos.naming.consistency.ephemeral.EphemeralConsistencyService;
import com.alibaba.nacos.naming.core.Instances;
import com.alibaba.nacos.naming.pojo.Record;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Yet another simple consistency service.
 * A eventually-consistent service which supports performing atomic operation on data store.
 * It uses a simple broadcast overlay to synchronize data updates.
 *
 * @author lostcharlie
 */
@Service("simpleConsistencyService")
public class SimpleConsistencyServiceImpl implements EphemeralConsistencyService {
    private SimpleDataStore dataStore;
    private ConflictResolver conflictResolver;
    private Map<String, CopyOnWriteArrayList<RecordListener>> listeners;

    private SimpleDataStore getDataStore() {
        return dataStore;
    }

    @Autowired
    private void setDataStore(SimpleDataStore dataStore) {
        this.dataStore = dataStore;
    }

    public ConflictResolver getConflictResolver() {
        return conflictResolver;
    }

    @Autowired
    private void setConflictResolver(ConflictResolver conflictResolver) {
        this.conflictResolver = conflictResolver;
    }

    private Map<String, CopyOnWriteArrayList<RecordListener>> getListeners() {
        return listeners;
    }

    private void setListeners(Map<String, CopyOnWriteArrayList<RecordListener>> listeners) {
        this.listeners = listeners;
    }

    public SimpleConsistencyServiceImpl() {
        this.setListeners(new ConcurrentHashMap<>());
    }

    @Override
    public void put(String key, Record value) throws NacosException {
        if (KeyBuilder.matchEphemeralInstanceListKey(key)) {
            SimpleDatum<Instances> datum = new SimpleDatum<Instances>();
            datum.value = (Instances) value;
            datum.key = key;
            datum.timestamp.incrementAndGet();
            datum.realTime = System.nanoTime();
            this.getDataStore().put(key, datum);
        }
    }

    @Override
    public void remove(String key) throws NacosException {
        this.getDataStore().remove(key);
    }

    @Override
    public Datum get(String key) throws NacosException {
        return this.getDataStore().get(key);
    }

    @Override
    public void listen(String key, RecordListener listener) throws NacosException {
        if (!this.getListeners().containsKey(key)) {
            this.getListeners().put(key, new CopyOnWriteArrayList<>());
        }
        if (this.getListeners().get(key).contains(listener)) {
            return;
        }
        this.getListeners().get(key).add(listener);
    }

    @Override
    public void unlisten(String key, RecordListener listener) throws NacosException {
        if (!this.getListeners().containsKey(key)) {
            return;
        }
        for (RecordListener recordListener : this.getListeners().get(key)) {
            if (recordListener.equals(listener)) {
                this.getListeners().get(key).remove(listener);
                break;
            }
        }
    }

    @Override
    public boolean isAvailable() {
        return true;
    }

    @Override
    public boolean supportPerformOperation() {
        return true;
    }

    @Override
    public void performOperation(String key, Operation operation) throws NacosException {
        if (KeyBuilder.matchEphemeralInstanceListKey(key)) {
            synchronized (this) {
                if (!this.getDataStore().contains(key)) {
                    SimpleDatum<Instances> datum = new SimpleDatum<Instances>();
                    datum.value = (Instances) new Instances();
                    datum.key = key;
                    datum.timestamp.incrementAndGet();
                    datum.realTime = 0L;
                    this.getDataStore().put(key, datum);
                }
            }
            this.getConflictResolver().merge(this.getDataStore().get(key), operation);
        }
    }
}
