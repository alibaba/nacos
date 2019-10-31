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
package com.alibaba.nacos.naming.consistency.weak.tree;

import com.alibaba.nacos.naming.consistency.ApplyAction;
import com.alibaba.nacos.naming.consistency.KeyBuilder;
import com.alibaba.nacos.naming.consistency.RecordListener;
import com.alibaba.nacos.naming.misc.Loggers;
import org.apache.commons.lang3.StringUtils;
import org.javatuples.Pair;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.stream.Collectors;

/**
 * @author satjd
 */
@Component("subscriberManager")
public class SubscriberManager {
    @Autowired
    ProtocolConfig protocolConfig;

    @Autowired
    DatumStoreService datumStoreService;

    private volatile Map<String, List<RecordListener>> listeners = new ConcurrentHashMap<>();

    private LinkedBlockingQueue<Pair> notifyTasks = new LinkedBlockingQueue<>();

    private ConcurrentHashMap<String, String> activeKeys = new ConcurrentHashMap<>(10 * 1024);

    private Runnable taskProcessor = () -> {
        while (true) {
            try {
                Pair pair = notifyTasks.take();

                String datumKey = (String) pair.getValue0();
                ApplyAction action = (ApplyAction) pair.getValue1();

                activeKeys.remove(datumKey);

                Loggers.TREE.info("remove task {}", datumKey);

                processNow(datumKey,action);
            }
            catch (Exception e) {
                Loggers.RAFT.error("[NACOS-TREE] Error while handling notifying task", e);
            }
        }
    };

    private ScheduledExecutorService taskProcessorExecutor;

    @PostConstruct
    public void init() throws Exception {
        taskProcessorExecutor = new ScheduledThreadPoolExecutor(protocolConfig.getTaskProcessorCnt(), new ThreadFactory() {
            @Override
            public Thread newThread(Runnable r) {
                Thread t = new Thread(r);

                t.setDaemon(true);
                t.setName("com.alibaba.nacos.naming.tree.subscribemanager.taskprocesser");

                return t;
            }
        });
        for (int i = 1; i <= protocolConfig.getTaskProcessorCnt(); i++) {
            taskProcessorExecutor.submit(taskProcessor);
        }
    }

    public void addTask(String datumKey, ApplyAction action) {
        if (action == ApplyAction.CHANGE) {
            if (activeKeys.containsKey(datumKey)) {
                return;
            }
            activeKeys.put(datumKey, StringUtils.EMPTY);
        }

        Loggers.TREE.info("add task {}", datumKey);

        notifyTasks.add(Pair.with(datumKey, action));
    }

    public void processNow(String datumKey, ApplyAction action) {
        List<RecordListener> targetListeners = new ArrayList<>();

        // if datum key is key of service meta,
        // notify all listeners interested in "com.alibaba.nacos.naming.domains.meta."
        if (KeyBuilder.matchServiceMetaKey(datumKey) && !KeyBuilder.matchSwitchKey(datumKey)) {
            List<RecordListener> li = listeners.get(KeyBuilder.SERVICE_META_KEY_PREFIX);
            if (li != null && !li.isEmpty()) {
                targetListeners.addAll(li);
            }
        }
        // notify listeners interested in key of this datum key
        List<RecordListener> li = listeners.get(datumKey);
        if (li != null && !li.isEmpty()) {
            targetListeners.addAll(li);
        }

        for (RecordListener listener : targetListeners) {
            try {
                if (action == ApplyAction.CHANGE) {
                    listener.onChange(datumKey, datumStoreService.getDatumCache().get(datumKey).value);
                    continue;
                }

                if (action == ApplyAction.DELETE) {
                    listener.onDelete(datumKey);
                    continue;
                }
            } catch (Throwable e) {
                Loggers.TREE.error("[NACOS-TREE] error while notifying listener of key: {}", datumKey, e);
            }
        }
    }

    public int getNotifyTaskCount() {
        return notifyTasks.size();
    }

    public void registerListener(String interestedKey, RecordListener listener) {
        List<RecordListener> listenerList = listeners.get(interestedKey);
        if (listenerList != null && listenerList.contains(listener)) {
            return;
        }

        if (listenerList == null) {
            listenerList = new CopyOnWriteArrayList<>();
            listeners.put(interestedKey, listenerList);
        }

        Loggers.TREE.info("add listener: {}", interestedKey);

        listenerList.add(listener);
        /*
        // if data present, notify immediately
        for (Datum datum : datumStoreService.getDatumCache().values()) {
            if (!listener.interests(datum.key)) {
                continue;
            }

            try {
                listener.onChange(datum.key, datum.value);
            } catch (Exception e) {
                Loggers.TREE.error("NACOS-TREE failed to notify listener", e);
            }
        }*/
    }

    public void unregisterListener(String interestedKey, RecordListener recordListener) {
        List<RecordListener> li = listeners.get(interestedKey);
        if (li == null || li.isEmpty()) {
            return;
        }

        listeners.put(interestedKey,
            li.stream().filter((RecordListener lis)-> lis != recordListener).collect(Collectors.toList())
        );
    }

    public void unregisterAllListener(String interestedKey) {
        List<RecordListener> li = listeners.get(interestedKey);
        if (li == null || li.isEmpty()) {
            return;
        }

        li.clear();
    }
}
