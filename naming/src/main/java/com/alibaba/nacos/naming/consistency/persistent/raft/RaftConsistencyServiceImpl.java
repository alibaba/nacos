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
package com.alibaba.nacos.naming.consistency.persistent.raft;

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.consistency.cp.CPProtocol;
import com.alibaba.nacos.core.utils.SystemUtils;
import com.alibaba.nacos.naming.cluster.ServerStatus;
import com.alibaba.nacos.naming.consistency.ApplyAction;
import com.alibaba.nacos.naming.consistency.KeyBuilder;
import com.alibaba.nacos.naming.consistency.RecordListener;
import com.alibaba.nacos.naming.consistency.persistent.PersistentConsistencyService;
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
import org.springframework.stereotype.Service;

/**
 * Use simplified Raft protocol to maintain the consistency status of Nacos cluster.
 *
 * @author nkorange
 * @since 1.0.0
 */
@Service
public class RaftConsistencyServiceImpl implements PersistentConsistencyService {

    @Autowired
    private RaftStore raftStore;

    @Autowired
    private Notifier notifier;

    @Autowired
    private SwitchDomain switchDomain;

    @Autowired
    private CPProtocol protocol;

    // If it is in stand-alone mode, it succeeds directly

    private volatile boolean isOk = SystemUtils.STANDALONE_MODE;

    @PostConstruct
    protected void init() {
        GlobalExecutor.executeNotifier(notifier);

        protocol.protocolMetaData()
                .subscribe(RaftStore.STORE_NAME, "leader", (o, arg) -> {
                    isOk = true;
                });

    }

    @Override
    public void put(String key, Record value) throws NacosException {
        try {
            raftStore.put(key, value);
        } catch (Exception e) {
            throw new NacosException(NacosException.SERVER_ERROR, e.getMessage());
        }
    }

    @Override
    public void remove(String key) throws NacosException {
        try {
            raftStore.remove(key);
        } catch (Exception e) {
            throw new NacosException(NacosException.SERVER_ERROR, e.getMessage());
        }
    }

    @Override
    public <T extends Record> T get(String key) throws NacosException {
        return (T) raftStore.get(key);
    }

    @Override
    public void listen(String key, RecordListener listener) throws NacosException {
        raftStore.listener(key, listener);
    }

    @Override
    public void unlisten(String key, RecordListener listener) throws NacosException {
        raftStore.unlisten(key, listener);
    }

    public int getTaskSize() {
        return notifier.getTaskSize();
    }

    @Override
    public boolean isAvailable() {
        Loggers.RAFT.info("raft consistency service is ok : {}, raft store is ok : {}", isOk, raftStore.isInitialized());
        return isOk && (raftStore.isInitialized() || ServerStatus.UP.name().equals(switchDomain.getOverriddenServerStatus()));
    }

    @Component
    public class Notifier implements Runnable {

        private ConcurrentHashMap<String, String> services = new ConcurrentHashMap<>(10 * 1024);

        private BlockingQueue<Pair<String, ApplyAction>> tasks = new LinkedBlockingQueue<>(1024 * 1024);

        public void addTask(String key, ApplyAction action) {

            if (services.containsKey(key) && action == ApplyAction.CHANGE) {
                return;
            }
            if (action == ApplyAction.CHANGE) {
                services.put(key, StringUtils.EMPTY);
            }

            Loggers.RAFT.info("add task {}", key);

            tasks.add(Pair.with(key, action));
        }

        public int getTaskSize() {
            return tasks.size();
        }

        @Override
        public void run() {
            Loggers.RAFT.info("raft notifier started");

            for (; ; ) {
                try {

                    Pair<String, ApplyAction> pair = tasks.take();

                    String key = pair.getValue0();
                    ApplyAction action = pair.getValue1();

                    services.remove(key);

                    Loggers.RAFT.info("remove task {}", key);

                    int count = 0;

                    Map<String, Set<RecordListener>> listeners = raftStore.getListMap();

                    if (listeners.containsKey(KeyBuilder.SERVICE_META_KEY_PREFIX)) {

                        if (KeyBuilder.matchServiceMetaKey(key) && !KeyBuilder.matchSwitchKey(key)) {

                            for (RecordListener listener : listeners.get(KeyBuilder.SERVICE_META_KEY_PREFIX)) {
                                try {
                                    if (action == ApplyAction.CHANGE) {
                                        listener.onChange(key, raftStore.get(key));
                                        continue;
                                    }

                                    if (action == ApplyAction.DELETE) {
                                        listener.onDelete(key);
                                    }
                                } catch (Throwable e) {
                                    Loggers.RAFT.error("[NACOS-RAFT] error while notifying listener of key: {}", key, e);
                                }
                            }
                        }
                    }

                    if (!listeners.containsKey(key)) {
                        continue;
                    }

                    for (RecordListener listener : listeners.get(key)) {

                        count++;

                        try {
                            if (action == ApplyAction.CHANGE) {
                                listener.onChange(key, raftStore.get(key));
                                continue;
                            }

                            if (action == ApplyAction.DELETE) {
                                listener.onDelete(key);
                            }
                        } catch (Throwable e) {
                            Loggers.RAFT.error("[NACOS-RAFT] error while notifying listener of key: {}", key, e);
                        }
                    }

                    if (Loggers.RAFT.isDebugEnabled()) {
                        Loggers.RAFT.debug("[NACOS-RAFT] datum change notified, key: {}, listener count: {}", key, count);
                    }
                } catch (Throwable e) {
                    Loggers.RAFT.error("[NACOS-RAFT] Error while handling notifying task", e);
                }
            }
        }
    }

}
