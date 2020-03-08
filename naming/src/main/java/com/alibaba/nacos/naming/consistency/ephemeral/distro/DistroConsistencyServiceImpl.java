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
import com.alibaba.nacos.naming.cluster.ServerStatus;
import com.alibaba.nacos.naming.consistency.ApplyAction;
import com.alibaba.nacos.naming.consistency.RecordListener;
import com.alibaba.nacos.naming.consistency.ephemeral.EphemeralConsistencyService;
import com.alibaba.nacos.naming.misc.GlobalConfig;
import com.alibaba.nacos.naming.misc.GlobalExecutor;
import com.alibaba.nacos.naming.misc.Loggers;
import com.alibaba.nacos.naming.misc.SwitchDomain;
import com.alibaba.nacos.naming.pojo.Record;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import javax.annotation.PostConstruct;
import org.apache.commons.lang3.StringUtils;
import org.javatuples.Pair;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

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

    @Autowired
    private GlobalConfig globalConfig;

    @Autowired
    private SwitchDomain switchDomain;

    @Autowired
    private Notifier notifier;

    @PostConstruct
    protected void init() {
        GlobalExecutor.executeNotifier(notifier);
    }

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
        return isInitialized() || ServerStatus.UP.name().equals(switchDomain.getOverriddenServerStatus());
    }

    public boolean isInitialized() {
        return dataStore.isStart() || !globalConfig.isDataWarmup();
    }

    @Component
    public class Notifier implements Runnable {

        private Map<String, String> services = new ConcurrentHashMap<>(10 * 1024);

        private BlockingQueue<Pair<String, ApplyAction>> tasks = new LinkedBlockingQueue<Pair<String, ApplyAction>>(1024 * 1024);

        public void addTask(String datumKey, ApplyAction action) {

            if (services.containsKey(datumKey) && action == ApplyAction.CHANGE) {
                return;
            }
            if (action == ApplyAction.CHANGE) {
                services.put(datumKey, StringUtils.EMPTY);
            }
            tasks.add(Pair.with(datumKey, action));
        }

        public int getTaskSize() {
            return tasks.size();
        }

        @Override
        public void run() {
            Loggers.DISTRO.info("distro notifier started");

            for ( ; ; ) {
                try {

                    Pair<String, ApplyAction> pair = tasks.take();

                    String datumKey = pair.getValue0();
                    ApplyAction action = pair.getValue1();

                    services.remove(datumKey);

                    int count = 0;

                    Map<String, Set<RecordListener>> listeners = dataStore.getListMap();

                    if (!listeners.containsKey(datumKey)) {
                        continue;
                    }

                    for (RecordListener listener : listeners.get(datumKey)) {

                        count++;

                        try {
                            if (action == ApplyAction.CHANGE) {
                                listener.onChange(datumKey, dataStore.get(datumKey));
                                continue;
                            }

                            if (action == ApplyAction.DELETE) {
                                listener.onDelete(datumKey);
                            }
                        } catch (Throwable e) {
                            Loggers.DISTRO.error("[NACOS-DISTRO] error while notifying listener of key: {}", datumKey, e);
                        }
                    }

                    if (Loggers.DISTRO.isDebugEnabled()) {
                        Loggers.DISTRO.debug("[NACOS-DISTRO] datum change notified, key: {}, listener count: {}, action: {}",
                                datumKey, count, action.name());
                    }
                } catch (Throwable e) {
                    Loggers.DISTRO.error("[NACOS-DISTRO] Error while handling notifying task", e);
                }
            }
        }
    }

}
