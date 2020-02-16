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
import com.alibaba.nacos.consistency.ap.APProtocol;
import com.alibaba.nacos.consistency.store.AfterHook;
import com.alibaba.nacos.consistency.store.BeforeHook;
import com.alibaba.nacos.consistency.store.KVStore;
import com.alibaba.nacos.naming.consistency.KeyBuilder;
import com.alibaba.nacos.naming.consistency.RecordListener;
import com.alibaba.nacos.naming.core.ServiceManager;
import com.alibaba.nacos.naming.misc.Loggers;
import com.alibaba.nacos.naming.misc.SwitchDomain;
import com.alibaba.nacos.naming.pojo.Record;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Store of data
 *
 * @author nkorange
 * @since 1.0.0
 */
@Component
@SuppressWarnings("all")
public class DataStore {

    @Autowired
    private DistroConsistencyServiceImpl consistencyService;

    @Autowired
    private ServiceManager serviceManager;

    @Autowired
    private SwitchDomain switchDomain;

    @Autowired
    private APProtocol protocol;

    private KVStore<Record> kvStore;

    private final Map<String, List<RecordListener>> listMap = new ConcurrentHashMap<>();

    @PostConstruct
    protected void init() {
        kvStore = protocol.createKVStore("Naming");
        kvStore.registerHook(null, new NBeforeHook(), new NAfterHook());
        kvStore.start();
    }

    public Record get(String key) {
        return kvStore.getByKeyAutoConvert(key);
    }

    public void put(String key, Record record) throws Exception {
        kvStore.put(key, record);
    }

    public void remove(String key) throws Exception {
        kvStore.remove(key);
    }

    void listener(String key, RecordListener listener) {
        listMap.computeIfAbsent(key, s -> new CopyOnWriteArrayList<>());
        listMap.get(key).add(listener);
    }

    void unlisten(String key, RecordListener listener) {
        if (listMap.containsKey(key)) {
            listMap.get(key).remove(listener);
        }
    }

    class NBeforeHook implements BeforeHook {

        @Override
        public <T> void hook(String key, T data, boolean isPut) {
            String namespaceId = KeyBuilder.getNamespace(key);
            String serviceName = KeyBuilder.getServiceName(key);
            if (!serviceManager.containService(namespaceId, serviceName)
                    && switchDomain.isDefaultInstanceEphemeral()) {
                try {
                    serviceManager.createEmptyService(namespaceId, serviceName, true);
                } catch (NacosException e) {
                    Loggers.DISTRO.error("before data operation has error : {}, operation : {}, key : {}, data {}",
                            e,
                            isPut ? "put" : "remove",
                            key,
                            data);
                }
            }
        }
    }

    class NAfterHook implements AfterHook<Record> {

        @Override
        public void hook(String key, Record data, boolean isPut) {
            List<RecordListener> listeners = listMap.get(key);
            for (RecordListener listener : listeners) {

                try {

                    if (isPut) {
                        listener.onChange(key, data);
                    } else {
                        listener.onDelete(key);
                    }

                } catch (Exception e) {
                    Loggers.DISTRO.error("[NACOS-RAFT] error while notifying listener of key: {}, error : {}", key, e);
                }

            }
        }
    }

}
