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
import com.alibaba.nacos.naming.cluster.transport.Serializer;
import com.alibaba.nacos.naming.consistency.DataListener;
import com.alibaba.nacos.naming.consistency.Datum;
import com.alibaba.nacos.naming.consistency.KeyBuilder;
import com.alibaba.nacos.naming.consistency.ephemeral.EphemeralConsistencyService;
import com.alibaba.nacos.naming.core.DistroMapper;
import com.alibaba.nacos.naming.core.Instances;
import com.alibaba.nacos.naming.misc.Loggers;
import com.alibaba.nacos.naming.misc.NamingProxy;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A consistency protocol algorithm called <b>Partition</b>
 * <p>
 * Use a partition algorithm to divide data into many blocks. Each Nacos server node takes
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
@Service("partitionConsistencyService")
public class PartitionConsistencyServiceImpl implements EphemeralConsistencyService {

    @Autowired
    private DistroMapper distroMapper;

    @Autowired
    private DataStore dataStore;

    @Autowired
    private TaskDispatcher taskDispatcher;

    @Autowired
    private DataSyncer dataSyncer;

    @Autowired
    private Serializer serializer;

    private volatile Map<String, List<DataListener>> listeners = new ConcurrentHashMap<>();

    @Override
    public void put(String key, Object value) throws NacosException {
        onPut(key, value);
        taskDispatcher.addTask(key);
    }

    @Override
    public void remove(String key) throws NacosException {
        onRemove(key);
    }

    @Override
    public Datum<?> get(String key) throws NacosException {
        return dataStore.get(key);
    }

    public void onPut(String key, Object value) {

        if (KeyBuilder.matchEphemeralInstanceListKey(key)) {
            Datum<Instances> datum = new Datum<>();
            datum.value = (Instances) value;
            datum.key = key;
            datum.timestamp.incrementAndGet();
            dataStore.put(key, datum);
        }

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

    public void onRemove(String key) {

        dataStore.remove(key);

        if (!listeners.containsKey(key)) {
            return;
        }
        for (DataListener listener : listeners.get(key)) {
            try {
                listener.onDelete(key);
            } catch (Exception e) {
                Loggers.EPHEMERAL.error("notify " + listener + ", key:" + key + " failed.", e);
            }
        }
    }

    public void onReceiveTimestamps(Map<String, Long> timestamps, String server) {

        List<String> toUpdateKeys = new ArrayList<>();
        List<String> toRemoveKeys = new ArrayList<>();
        for (Map.Entry<String, Long> entry : timestamps.entrySet()) {
            if (isResponsible(entry.getKey())) {
                // this key should not be sent from remote server:
                Loggers.EPHEMERAL.error("receive responsible key timestamp of " + entry.getKey() + " from " + server);
                continue;
            }
            if (!dataStore.contains(entry.getKey()) || dataStore.get(entry.getKey()).timestamp.get() < entry.getValue()) {
                toUpdateKeys.add(entry.getKey());
            }
        }

        for (String key : dataStore.keys()) {
            if (!timestamps.containsKey(key)) {
                toRemoveKeys.add(key);
            }
        }

        Loggers.EPHEMERAL.info("to remove keys: {}, to update keys: {}, source: {}", toRemoveKeys, toUpdateKeys, server);

        for (String key : toRemoveKeys) {
            onRemove(key);
        }

        if (toUpdateKeys.isEmpty()) {
            return;
        }

        try {
            byte[] result = NamingProxy.getData(toUpdateKeys, server);
            if (result.length > 0) {
                Map<String, Datum<Instances>> datumMap =
                    serializer.deserializeMap(result, Instances.class);

                for (Map.Entry<String, Datum<Instances>> entry : datumMap.entrySet()) {
                    dataStore.put(entry.getKey(), entry.getValue());

                    if (!listeners.containsKey(entry.getKey())) {
                        return;
                    }
                    for (DataListener listener : listeners.get(entry.getKey())) {
                        try {
                            listener.onChange(entry.getKey(), entry.getValue().value);
                        } catch (Exception e) {
                            Loggers.EPHEMERAL.error("notify " + listener + ", key: " + entry.getKey() + " failed.", e);
                        }
                    }
                }
            }
        } catch (Exception e) {
            Loggers.EPHEMERAL.error("get data from " + server + " failed!", e);
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
        return distroMapper.responsible(KeyBuilder.getServiceName(key));
    }

    @Override
    public String getResponsibleServer(String key) {
        return distroMapper.mapSrv(KeyBuilder.getServiceName(key));
    }

    @Override
    public boolean isAvailable() {
        return dataSyncer.isInitialized();
    }
}
