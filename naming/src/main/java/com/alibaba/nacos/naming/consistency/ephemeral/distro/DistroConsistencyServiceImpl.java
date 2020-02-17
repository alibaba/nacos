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
package com.alibaba.nacos.naming.consistency.ephemeral.distro;

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.core.executor.ExecutorFactory;
import com.alibaba.nacos.core.executor.NameThreadFactory;
import com.alibaba.nacos.naming.consistency.RecordListener;
import com.alibaba.nacos.naming.consistency.ephemeral.EphemeralConsistencyService;
import com.alibaba.nacos.naming.pojo.Record;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.concurrent.ScheduledExecutorService;

/**
 * A consistency protocol algorithm called <b>Distro</b>
 * <p>
 * Use a distro algorithm to divide data into many blocks. Each Nacos server node takes
 * responsibility for exactly one block of data. Each block of data is generated, removed
 * and synchronized by its responsible server. So every Nacos server only handles writings
 * for a subset of the total service data.
 * <p>
 * At mean time every Nacos server receives data sync of other Nacos server, so every Nacos
 * server will eventually have a complete set of data.
 *
 * @author nkorange
 * @since 1.0.0
 */
@SuppressWarnings("all")
@org.springframework.stereotype.Service("distroConsistencyService")
public class DistroConsistencyServiceImpl implements EphemeralConsistencyService {

    @Autowired
    private DataStore dataStore;

    private ScheduledExecutorService executor = ExecutorFactory.newSingleScheduledExecutorService(
            "distroConsistencyService",
            new NameThreadFactory("com.alibaba.nacos.naming.distro.notifier")
    );

    @Override
    public void put(String key, Record value) throws NacosException {
        try {
            dataStore.put(key, value);
        } catch (Exception e) {
            throw new NacosException(NacosException.SERVER_ERROR, e.getMessage());
        }
    }

    @Override
    public void remove(String key) throws NacosException {
        try {
            dataStore.remove(key);
        } catch (Exception e) {
            throw new NacosException(NacosException.SERVER_ERROR, e.getMessage());
        }
    }

    @Override
    public <T extends Record> T get(String key) throws NacosException {
        return (T) dataStore.get(key);
    }

    @Override
    public void listen(String key, RecordListener listener) throws NacosException {
        dataStore.listener(key, listener);
    }

    @Override
    public void unlisten(String key, RecordListener listener) throws NacosException {
        dataStore.unlisten(key, listener);
    }

    @Override
    public boolean isAvailable() {
        return false;
    }

}
