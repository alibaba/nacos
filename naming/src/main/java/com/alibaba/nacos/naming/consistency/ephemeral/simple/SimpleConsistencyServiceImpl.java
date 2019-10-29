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

    private SimpleDataStore getDataStore() {
        return dataStore;
    }

    @Autowired
    private void setDataStore(SimpleDataStore dataStore) {
        this.dataStore = dataStore;
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
        throw new NacosException(NacosException.SERVER_ERROR, "NotImplemented");
    }

    @Override
    public void unlisten(String key, RecordListener listener) throws NacosException {
        throw new NacosException(NacosException.SERVER_ERROR, "NotImplemented");
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
    public void performOperation(Operation operation) throws NacosException {
        throw new NacosException(NacosException.SERVER_ERROR, "NotImplemented");
    }
}
