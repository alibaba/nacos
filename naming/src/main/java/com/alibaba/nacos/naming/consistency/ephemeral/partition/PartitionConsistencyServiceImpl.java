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

import com.alibaba.nacos.api.common.Constants;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.core.utils.SystemUtils;
import com.alibaba.nacos.naming.cluster.ServerListManager;
import com.alibaba.nacos.naming.cluster.ServerMode;
import com.alibaba.nacos.naming.cluster.servers.Server;
import com.alibaba.nacos.naming.cluster.transport.Serializer;
import com.alibaba.nacos.naming.consistency.Datum;
import com.alibaba.nacos.naming.consistency.KeyBuilder;
import com.alibaba.nacos.naming.consistency.RecordListener;
import com.alibaba.nacos.naming.consistency.ephemeral.EphemeralConsistencyService;
import com.alibaba.nacos.naming.core.DistroMapper;
import com.alibaba.nacos.naming.core.Instances;
import com.alibaba.nacos.naming.core.Service;
import com.alibaba.nacos.naming.misc.Loggers;
import com.alibaba.nacos.naming.misc.NamingProxy;
import com.alibaba.nacos.naming.misc.NetUtils;
import com.alibaba.nacos.naming.misc.SwitchDomain;
import com.alibaba.nacos.naming.pojo.Record;
import org.springframework.beans.factory.annotation.Autowired;

import javax.annotation.PostConstruct;
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
@org.springframework.stereotype.Service("partitionConsistencyService")
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

    @Autowired
    private ServerListManager serverListManager;

    @Autowired
    private SwitchDomain switchDomain;

    private boolean initialized = false;

    private volatile Map<String, List<RecordListener>> listeners = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() throws Exception {

        if (SystemUtils.STANDALONE_MODE) {
            initialized = true;
            return;
        }
        while (serverListManager.getHealthyServers().isEmpty()) {
            Thread.sleep(1000L);
            Loggers.EPHEMERAL.info("waiting server list init...");
        }

        for (Server server : serverListManager.getHealthyServers()) {
            if (NetUtils.localServer().equals(server.getKey())) {
                continue;
            }
            // try sync data from remote server:
            if (syncAllDataFromRemote(server)) {
                initialized = true;
                break;
            }
        }

        if (!initialized) {
            // init failed, exit:
            throw new RuntimeException("init local server failed! Abort.");
        }

    }

    @Override
    public void put(String key, Record value) throws NacosException {
        onPut(key, value);
        taskDispatcher.addTask(key);
    }

    @Override
    public void remove(String key) throws NacosException {
        onRemove(key);
    }

    @Override
    public Datum get(String key) throws NacosException {
        return dataStore.get(key);
    }

    public void onPut(String key, Record value) {

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
        for (RecordListener listener : listeners.get(key)) {
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
        for (RecordListener listener : listeners.get(key)) {
            try {
                listener.onDelete(key);
            } catch (Exception e) {
                Loggers.EPHEMERAL.error("notify " + listener + ", key:" + key + " failed.", e);
            }
        }
    }

    public void onReceiveChecksums(Map<String, String> checksumMap, String server) {

        List<String> toUpdateKeys = new ArrayList<>();
        List<String> toRemoveKeys = new ArrayList<>();
        for (Map.Entry<String, String> entry : checksumMap.entrySet()) {
            if (distroMapper.responsible(KeyBuilder.getServiceName(entry.getKey()))) {
                // this key should not be sent from remote server:
                Loggers.EPHEMERAL.error("receive responsible key timestamp of " + entry.getKey() + " from " + server);
                // abort the procedure:
                return;
            }
            if (!dataStore.contains(entry.getKey()) ||
                dataStore.get(entry.getKey()).value == null ||
                !dataStore.get(entry.getKey()).value.getChecksum().equals(entry.getValue())) {
                toUpdateKeys.add(entry.getKey());
            }
        }

        for (String key : dataStore.keys()) {

            if (!server.equals(distroMapper.mapSrv(KeyBuilder.getServiceName(key)))) {
                continue;
            }

            if (!checksumMap.containsKey(key)) {
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
            processData(result);
        } catch (Exception e) {
            Loggers.EPHEMERAL.error("get data from " + server + " failed!", e);
        }

    }

    public boolean syncAllDataFromRemote(Server server) {

        try {
            byte[] data = NamingProxy.getAllData(server.getKey());
            processData(data);
            return true;
        } catch (Exception e) {
            Loggers.EPHEMERAL.error("sync full data from " + server + " failed!");
            return false;
        }
    }

    public void processData(byte[] data) throws Exception {
        if (data.length > 0) {
            Map<String, Datum<Instances>> datumMap =
                serializer.deserializeMap(data, Instances.class);


            for (Map.Entry<String, Datum<Instances>> entry : datumMap.entrySet()) {
                dataStore.put(entry.getKey(), entry.getValue());

                if (!listeners.containsKey(entry.getKey())) {
                    // pretty sure the service not exist:
                    if (ServerMode.AP.name().equals(switchDomain.getServerMode())) {
                        // create empty service
                        Service service = new Service();
                        String serviceName = KeyBuilder.getServiceName(entry.getKey());
                        String namespaceId = KeyBuilder.getNamespace(entry.getKey());
                        service.setName(serviceName);
                        service.setNamespaceId(namespaceId);
                        service.setGroupName(Constants.DEFAULT_GROUP);
                        // now validate the service. if failed, exception will be thrown
                        service.setLastModifiedMillis(System.currentTimeMillis());
                        service.recalculateChecksum();
                        listeners.get(KeyBuilder.SERVICE_META_KEY_PREFIX).get(0)
                            .onChange(KeyBuilder.buildServiceMetaKey(namespaceId, serviceName), service);
                    }
                }
            }

            for (Map.Entry<String, Datum<Instances>> entry : datumMap.entrySet()) {
                dataStore.put(entry.getKey(), entry.getValue());

                if (!listeners.containsKey(entry.getKey())) {
                    Loggers.EPHEMERAL.warn("listener not found: {}", entry.getKey());
                    continue;
                }
                for (RecordListener listener : listeners.get(entry.getKey())) {
                    try {
                        listener.onChange(entry.getKey(), entry.getValue().value);
                    } catch (Exception e) {
                        Loggers.EPHEMERAL.error("notify " + listener + ", key: " + entry.getKey() + " failed.", e);
                    }
                }
            }
        }
    }

    @Override
    public void listen(String key, RecordListener listener) throws NacosException {
        if (!listeners.containsKey(key)) {
            listeners.put(key, new ArrayList<>());
        }
        listeners.get(key).add(listener);
    }

    @Override
    public void unlisten(String key, RecordListener listener) throws NacosException {
        if (!listeners.containsKey(key)) {
            return;
        }
        for (RecordListener recordListener : listeners.get(key)) {
            if (recordListener.equals(listener)) {
                listeners.get(key).remove(listener);
                break;
            }
        }
    }

    @Override
    public boolean isAvailable() {
        return initialized;
    }
}
