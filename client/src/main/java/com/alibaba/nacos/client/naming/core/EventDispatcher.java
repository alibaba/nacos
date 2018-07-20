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
package com.alibaba.nacos.client.naming.core;

import com.alibaba.nacos.api.naming.listener.EventListener;
import com.alibaba.nacos.api.naming.listener.NamingEvent;
import com.alibaba.nacos.api.naming.pojo.Instance;
import com.alibaba.nacos.client.naming.utils.CollectionUtils;
import com.alibaba.nacos.client.naming.utils.LogUtils;
import com.alibaba.nacos.client.naming.utils.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.*;

/**
 * @author xuanyin
 */
public class EventDispatcher {

    private ExecutorService executor = null;

    private BlockingQueue<Domain> changedDoms = new LinkedBlockingQueue<Domain>();

    private ConcurrentMap<String, List<EventListener>> observerMap = new ConcurrentHashMap<String, List<EventListener>>();

    public EventDispatcher() {

        executor = Executors.newSingleThreadExecutor(new ThreadFactory() {
            @Override
            public Thread newThread(Runnable r) {
                Thread thread = new Thread(r, "com.alibaba.nacos.naming.client.listener");
                thread.setDaemon(true);

                return thread;
            }
        });

        executor.execute(new Notifier());
    }

    public void addListener(Domain dom, String clusters, EventListener listener) {
        addListener(dom, clusters, StringUtils.EMPTY, listener);
    }

    public void addListener(Domain dom, String clusters, String env, EventListener listener) {
        List<EventListener> observers = Collections.synchronizedList(new ArrayList<EventListener>());
        observers.add(listener);

        observers = observerMap.putIfAbsent(Domain.getKey(dom.getName(), clusters, env), observers);
        if (observers != null) {
            observers.add(listener);
        }

        domChanged(dom);
    }

    public void removeListener(String dom, String clusters, EventListener listener) {
        String unit = "";

        List<EventListener> observers = observerMap.get(Domain.getKey(dom, clusters, unit));
        if (observers != null) {
            Iterator<EventListener> iter = observers.iterator();
            while (iter.hasNext()) {
                EventListener oldListener = iter.next();
                if (oldListener.equals(listener)) {
                    iter.remove();
                }
            }
        }
    }

    public void domChanged(Domain dom) {
        if (dom == null) {
            return;
        }

        changedDoms.add(dom);
    }

    private class Notifier implements Runnable {
        @Override
        public void run() {
            while (true) {
                Domain dom = null;
                try {
                    dom = changedDoms.poll(5, TimeUnit.MINUTES);
                } catch (Exception ignore) {
                }

                if (dom == null) {
                    continue;
                }

                try {
                    List<EventListener> listeners = observerMap.get(dom.getKey());

                    if (!CollectionUtils.isEmpty(listeners)) {
                        for (EventListener listener : listeners) {
                            List<Instance> hosts = Collections.unmodifiableList(dom.getHosts());
                            if (!CollectionUtils.isEmpty(hosts)) {
                                listener.onEvent(new NamingEvent(dom.getName(), hosts));
                            }
                        }
                    }

                } catch (Exception e) {
                    LogUtils.LOG.error("NA", "notify error for dom: "
                            + dom.getName() + ", clusters: " + dom.getClusters(), e);
                }
            }
        }
    }

    public void setExecutor(ExecutorService executor) {
        ExecutorService oldExecutor = this.executor;
        this.executor = executor;

        oldExecutor.shutdown();
    }
}
