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

import com.alibaba.nacos.api.common.Constants;
import com.alibaba.nacos.consistency.ap.APProtocol;
import com.alibaba.nacos.consistency.store.AfterHook;
import com.alibaba.nacos.consistency.store.BeforeHook;
import com.alibaba.nacos.consistency.store.KVStore;
import com.alibaba.nacos.core.utils.ConcurrentHashSet;
import com.alibaba.nacos.naming.consistency.ApplyAction;
import com.alibaba.nacos.naming.consistency.KeyBuilder;
import com.alibaba.nacos.naming.consistency.RecordListener;
import com.alibaba.nacos.naming.core.Service;
import com.alibaba.nacos.naming.core.ServiceManager;
import com.alibaba.nacos.naming.misc.Loggers;
import com.alibaba.nacos.naming.misc.SwitchDomain;
import com.alibaba.nacos.naming.pojo.Record;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.DependsOn;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

/**
 * Store of data
 *
 * @author nkorange
 * @since 1.0.0
 */
@Component
@DependsOn("serverMemberManager")
@SuppressWarnings("all")
public class DataStore {

    @Lazy
    @Autowired
    private ServiceManager serviceManager;

    @Autowired
    private SwitchDomain switchDomain;

    @Autowired
    private APProtocol protocol;

    @Autowired
    private DistroConsistencyServiceImpl.Notifier notifier;

    public static final String STORE_NAME = "ephemeral_services";

    private KVStore<Record> kvStore;

    private final Map<String, Set<RecordListener>> listMap = new ConcurrentHashMap<>();

    private boolean isStart = false;

    @PostConstruct
    protected void init() throws Exception {
        kvStore = protocol.createKVStore(STORE_NAME);
        kvStore.registerHook(null, new NBeforeHook(), new NAfterHook());
        kvStore.start();
        isStart = true;
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
        listMap.computeIfAbsent(key, s -> new ConcurrentHashSet<>());
        Set<RecordListener> set = listMap.get(key);
        if (!set.contains(listener)) {
            set.add(listener);
        }
    }

    void unlisten(String key, RecordListener listener) {
        if (listMap.containsKey(key)) {
            listMap.get(key).remove(listener);
        }
    }

    class NBeforeHook implements BeforeHook<Record> {

        @Override
        public void hook(String key, Record data, KVStore.Item item, boolean isPut) {
            String namespaceId = KeyBuilder.getNamespace(key);
            String serviceName = KeyBuilder.getServiceName(key);

            // This is usually triggered only when data is synchronized with other nodes

            if (!serviceManager.containService(namespaceId, serviceName)
                    && switchDomain.isDefaultInstanceEphemeral()) {
                try {
                    // create empty service
                    Loggers.DISTRO.info("creating service {}", key);
                    Service service = new Service();
                    service.setName(serviceName);
                    service.setNamespaceId(namespaceId);
                    service.setGroupName(Constants.DEFAULT_GROUP);
                    // now validate the service. if failed, exception will be thrown
                    service.setLastModifiedMillis(System.currentTimeMillis());
                    service.recalculateChecksum();
                    listMap.get(KeyBuilder.SERVICE_META_KEY_PREFIX).iterator().next()
                            .onChange(KeyBuilder.buildServiceMetaKey(namespaceId, serviceName), service);
                    serviceManager.createEmptyService(namespaceId, serviceName, true);
                } catch (Exception e) {
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
        public void hook(String key, Record data, KVStore.Item item, boolean isPut) {
            if (isPut) {
                notifier.addTask(key, ApplyAction.CHANGE);
            } else {
                notifier.addTask(key, ApplyAction.DELETE);
            }
        }
    }

    public boolean isStart() {
        return isStart;
    }

    public Map<String, Set<RecordListener>> getListMap() {
        return listMap;
    }
}
