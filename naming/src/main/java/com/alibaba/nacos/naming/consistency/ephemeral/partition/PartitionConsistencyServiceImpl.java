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
package com.alibaba.nacos.naming.consistency.ephemeral.partition;

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.naming.pojo.Instance;
import com.alibaba.nacos.naming.consistency.DataListener;
import com.alibaba.nacos.naming.consistency.KeyBuilder;
import com.alibaba.nacos.naming.consistency.ephemeral.EphemeralConsistencyService;
import com.alibaba.nacos.naming.core.DistroMapper;
import com.alibaba.nacos.naming.misc.Loggers;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A consistency protocol algorithm called <b>Partition</b>
 * <p>
 * Use a partition algorithm to divide data into many blocks. Each Nacos server node takes
 * responsibility for exactly one block of data. Each block of data is generated, removed
 * and synchronized by its associated server. So every Nacos only handles writings for a
 * subset of the total service data, and at mean time stores complete service data.
 *
 * @author nkorange
 * @since 1.0.0
 */
@Component("partitionConsistencyService")
public class PartitionConsistencyServiceImpl implements EphemeralConsistencyService {

    @Autowired
    private DistroMapper distroMapper;

    @Autowired
    private DataStore dataStore;

    @Autowired
    private TaskDispatcher taskDispatcher;

    private volatile Map<String, List<DataListener>> listeners = new ConcurrentHashMap<>();

    @Override
    public void put(String key, Object value) throws NacosException {
        if (KeyBuilder.matchEphemeralInstanceListKey(key)) {
            List<Instance> instances = (List<Instance>) value;
            dataStore.put(key, instances);
        }
        taskDispatcher.addTask(key);
        onPut(key, value);
    }

    @Override
    public void remove(String key) throws NacosException {
        dataStore.remove(key);
    }

    @Override
    public Object get(String key) throws NacosException {
        return dataStore.get(key);
    }

    public void onPut(String key, Object value) {
        if (!listeners.containsKey(key)) {
            return;
        }
        for (DataListener listener : listeners.get(key)) {
            try {
                listener.onChange(key, value);
            } catch (Exception e) {
                Loggers.EPHEMERAL.error("notify " + listener + ", key:" + key + " failed.", e);
            }
        }
    }

    @Override
    public void listen(String key, DataListener listener) throws NacosException {
        if (!listeners.containsKey(key)) {
            listeners.put(key, new ArrayList<>());
        }
        listeners.get(key).add(listener);
    }

    @Override
    public void unlisten(String key, DataListener listener) throws NacosException {
        if (!listeners.containsKey(key)) {
            return;
        }
        for (DataListener dataListener : listeners.get(key)) {
            if (dataListener.equals(listener)) {
                listeners.get(key).remove(listener);
                break;
            }
        }
    }

    @Override
    public boolean isResponsible(String key) {
        return distroMapper.responsible(key);
    }

    @Override
    public String getResponsibleServer(String key) {
        return distroMapper.mapSrv(key);
    }
}
