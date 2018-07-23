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

import com.alibaba.fastjson.JSON;
import com.alibaba.nacos.api.naming.pojo.Instance;
import com.alibaba.nacos.client.naming.backups.FailoverReactor;
import com.alibaba.nacos.client.naming.cache.DiskCache;
import com.alibaba.nacos.client.naming.net.NamingProxy;
import com.alibaba.nacos.client.naming.utils.LogUtils;
import com.alibaba.nacos.client.naming.utils.NetUtils;
import com.alibaba.nacos.client.naming.utils.StringUtils;
import com.alibaba.nacos.client.naming.utils.UtilAndComs;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

import java.util.*;
import java.util.concurrent.*;

/**
 * @author xuanyin
 */
public class HostReactor {

    public static final long DEFAULT_DELAY = 1000L;

    public long updateHoldInterval = 5000L;

    private final Map<String, ScheduledFuture<?>> futureMap = new HashMap<String, ScheduledFuture<?>>();

    private Map<String, Domain> domMap;

    private PushReceiver pushRecver;

    private EventDispatcher eventDispatcher;

    private NamingProxy serverProxy;

    private FailoverReactor failoverReactor;

    private String cacheDir;

    public HostReactor(EventDispatcher eventDispatcher, NamingProxy serverProxy, String cacheDir) {
        this.eventDispatcher = eventDispatcher;
        this.serverProxy = serverProxy;
        this.cacheDir = cacheDir;
        this.domMap = new ConcurrentHashMap<>(DiskCache.read(this.cacheDir));
        this.failoverReactor = new FailoverReactor(this, cacheDir);
        this.pushRecver = new PushReceiver(this);
    }

    private ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(new ThreadFactory() {
        @Override
        public Thread newThread(Runnable r) {
            Thread thread = new Thread(r, "com.vipserver.client.updater");
            thread.setDaemon(true);

            return thread;
        }
    });

    public Map<String, Domain> getDomMap() {
        return domMap;
    }

    public synchronized ScheduledFuture<?> addTask(UpdateTask task) {
        return executor.schedule(task, DEFAULT_DELAY, TimeUnit.MILLISECONDS);
    }

    public Domain processDomJSON(String json) {
        Domain domObj = JSON.parseObject(json, Domain.class);
        Domain oldDom = domMap.get(domObj.getKey());
        if (domObj.getHosts() == null || !domObj.validate()) {
            //empty or error push, just ignore
            return oldDom;
        }

        if (oldDom != null) {
            if (oldDom.getLastRefTime() > domObj.getLastRefTime()) {
                LogUtils.LOG.warn("out of date data received, old-t: " + oldDom.getLastRefTime()
                        + ", new-t: " + domObj.getLastRefTime());
            }

            domMap.put(domObj.getKey(), domObj);

            Map<String, Instance> oldHostMap = new HashMap<String, Instance>(oldDom.getHosts().size());
            for (Instance host : oldDom.getHosts()) {
                oldHostMap.put(host.toInetAddr(), host);
            }

            Map<String, Instance> newHostMap = new HashMap<String, Instance>(domObj.getHosts().size());
            for (Instance host : domObj.getHosts()) {
                newHostMap.put(host.toInetAddr(), host);
            }

            Set<Instance> modHosts = new HashSet<Instance>();
            Set<Instance> newHosts = new HashSet<Instance>();
            Set<Instance> remvHosts = new HashSet<Instance>();

            List<Map.Entry<String, Instance>> newDomHosts = new ArrayList<Map.Entry<String, Instance>>(newHostMap.entrySet());
            for (Map.Entry<String, Instance> entry : newDomHosts) {
                Instance host = entry.getValue();
                String key = entry.getKey();
                if (oldHostMap.containsKey(key) && !StringUtils.equals(host.toString(), oldHostMap.get(key).toString())) {
                    modHosts.add(host);
                    continue;
                }

                if (!oldHostMap.containsKey(key)) {
                    newHosts.add(host);
                    continue;
                }

            }

            for (Map.Entry<String, Instance> entry : oldHostMap.entrySet()) {
                Instance host = entry.getValue();
                String key = entry.getKey();
                if (newHostMap.containsKey(key)) {
                    continue;
                }

                if (!newHostMap.containsKey(key)) {
                    remvHosts.add(host);
                    continue;
                }

            }

            if (newHosts.size() > 0) {
                LogUtils.LOG.info("new ips(" + newHosts.size() + ") dom: "
                        + domObj.getName() + " -> " + JSON.toJSONString(newHosts));
            }

            if (remvHosts.size() > 0) {
                LogUtils.LOG.info("removed ips(" + remvHosts.size() + ") dom: "
                        + domObj.getName() + " -> " + JSON.toJSONString(remvHosts));
            }

            if (modHosts.size() > 0) {
                LogUtils.LOG.info("modified ips(" + modHosts.size() + ") dom: "
                        + domObj.getName() + " -> " + JSON.toJSONString(modHosts));
            }


            domObj.setJsonFromServer(json);

            if (newHosts.size() > 0 || remvHosts.size() > 0 || modHosts.size() > 0) {
                eventDispatcher.domChanged(domObj);
                DiskCache.write(domObj, cacheDir);
            }

        } else {
            LogUtils.LOG.info("new ips(" + domObj.ipCount() + ") dom: " + domObj.getName() + " -> " + JSON.toJSONString(domObj.getHosts()));
            domMap.put(domObj.getKey(), domObj);
            eventDispatcher.domChanged(domObj);
            domObj.setJsonFromServer(json);
            DiskCache.write(domObj, cacheDir);
        }

        LogUtils.LOG.info("current ips:(" + domObj.ipCount() + ") dom: " + domObj.getName() +
                " -> " + JSON.toJSONString(domObj.getHosts()));

        return domObj;
    }

    private Domain getDom0(String dom, String clusters, String env) {

        String key = Domain.getKey(dom, clusters, env, false);

        return domMap.get(key);
    }

    private Domain getDom0(String dom, String clusters, String env, boolean allIPs) {

        String key = Domain.getKey(dom, clusters, env, allIPs);
        return domMap.get(key);
    }

    public Domain getDom(String dom, String clusters, String env) {
        return getDom(dom, clusters, env, false);
    }

    public Domain getDom(String dom, String clusters) {
        String env = StringUtils.EMPTY;
        return getDom(dom, clusters, env, false);
    }

    public Domain getDom(final String dom, final String clusters, final String env, final boolean allIPs) {

        LogUtils.LOG.debug("failover-mode: " + failoverReactor.isFailoverSwitch());
        String key = Domain.getKey(dom, clusters, env, allIPs);
        if (failoverReactor.isFailoverSwitch()) {
            return failoverReactor.getDom(key);
        }

        Domain domObj = getDom0(dom, clusters, env, allIPs);

        if (null == domObj) {
            domObj = new Domain(dom, clusters, env);

            if (allIPs) {
                domObj.setAllIPs(allIPs);
            }

            domMap.put(domObj.getKey(), domObj);

            if (allIPs) {
                updateDom4AllIPNow(dom, clusters, env);
            } else {
                updateDomNow(dom, clusters, env);
            }
        } else if (domObj.getHosts().isEmpty()) {

            if (updateHoldInterval > 0) {
                // hold a moment waiting for update finish
                synchronized (domObj) {
                    try {
                        domObj.wait(updateHoldInterval);
                    } catch (InterruptedException e) {
                        LogUtils.LOG.error("[getDom]", "dom:" + dom + ", clusters:" + clusters + ", allIPs:" + allIPs, e);
                    }
                }
            }
        }

        scheduleUpdateIfAbsent(dom, clusters, env, allIPs);

        return domMap.get(domObj.getKey());
    }

    public void scheduleUpdateIfAbsent(String dom, String clusters, String env, boolean allIPs) {
        if (futureMap.get(Domain.getKey(dom, clusters, env, allIPs)) != null) {
            return;
        }

        synchronized (futureMap) {
            if (futureMap.get(Domain.getKey(dom, clusters, env, allIPs)) != null) {
                return;
            }

            ScheduledFuture<?> future = addTask(new UpdateTask(dom, clusters, env, allIPs));
            futureMap.put(Domain.getKey(dom, clusters, env, allIPs), future);
        }
    }

    public void updateDom4AllIPNow(String dom, String clusters, String env) {
        updateDom4AllIPNow(dom, clusters, env, -1L);
    }

    @SuppressFBWarnings("NN_NAKED_NOTIFY")
    public void updateDom4AllIPNow(String dom, String clusters, String env, long timeout) {
        try {
            Map<String, String> params = new HashMap<String, String>(8);
            params.put("dom", dom);
            params.put("clusters", clusters);
            params.put("udpPort", String.valueOf(pushRecver.getUDPPort()));

            Domain oldDom = getDom0(dom, clusters, env, true);
            if (oldDom != null) {
                params.put("checksum", oldDom.getChecksum());
            }

            String result = serverProxy.reqAPI(UtilAndComs.NACOS_URL_BASE + "/api/srvAllIP", params);
            if (StringUtils.isNotEmpty(result)) {
                Domain domain = processDomJSON(result);
                domain.setAllIPs(true);
            }

            if (oldDom != null) {
                synchronized (oldDom) {
                    oldDom.notifyAll();
                }
            }

            //else nothing has changed
        } catch (Exception e) {
            LogUtils.LOG.error("NA", "failed to update dom: " + dom, e);
        }
    }

    @SuppressFBWarnings("NN_NAKED_NOTIFY")
    public void updateDomNow(String dom, String clusters, String env) {
        Domain oldDom = getDom0(dom, clusters, env);
        try {
            Map<String, String> params = new HashMap<String, String>(8);
            params.put("dom", dom);
            params.put("clusters", clusters);
            params.put("udpPort", String.valueOf(pushRecver.getUDPPort()));
            params.put("env", env);
            params.put("clientIP", NetUtils.localIP());

            StringBuilder stringBuilder = new StringBuilder();
            for (String string : Balancer.UNCONSISTENT_DOM_WITH_ADDRESS_SERVER) {
                stringBuilder.append(string).append(",");
            }

            Balancer.UNCONSISTENT_DOM_WITH_ADDRESS_SERVER.clear();
            params.put("unconsistentDom", stringBuilder.toString());

            String envSpliter = ",";
            if (!StringUtils.isEmpty(env) && !env.contains(envSpliter)) {
                params.put("useEnvId", "true");
            }

            if (oldDom != null) {
                params.put("checksum", oldDom.getChecksum());
            }

            String result = serverProxy.reqAPI(UtilAndComs.NACOS_URL_BASE + "/api/srvIPXT", params);
            if (StringUtils.isNotEmpty(result)) {
                processDomJSON(result);
            }
            //else nothing has changed
        } catch (Exception e) {
            LogUtils.LOG.error("NA", "failed to update dom: " + dom, e);
        } finally {
            if (oldDom != null) {
                synchronized (oldDom) {
                    oldDom.notifyAll();
                }
            }
        }
    }

    public void refreshOnly(String dom, String clusters, String env, boolean allIPs) {
        try {
            Map<String, String> params = new HashMap<String, String>(16);
            params.put("dom", dom);
            params.put("clusters", clusters);
            params.put("udpPort", String.valueOf(pushRecver.getUDPPort()));
            params.put("unit", env);
            params.put("clientIP", NetUtils.localIP());

            String domSpliter = ",";
            StringBuilder stringBuilder = new StringBuilder();
            for (String string : Balancer.UNCONSISTENT_DOM_WITH_ADDRESS_SERVER) {
                stringBuilder.append(string).append(domSpliter);
            }

            Balancer.UNCONSISTENT_DOM_WITH_ADDRESS_SERVER.clear();
            params.put("unconsistentDom", stringBuilder.toString());

            String envSpliter = ",";
            if (!env.contains(envSpliter)) {
                params.put("useEnvId", "true");
            }

            if (allIPs) {
                serverProxy.reqAPI(UtilAndComs.NACOS_URL_BASE + "/api/srvAllIP", params);
            } else {
                serverProxy.reqAPI(UtilAndComs.NACOS_URL_BASE + "/api/srvIPXT", params);
            }
        } catch (Exception e) {
            LogUtils.LOG.error("NA", "failed to update dom: " + dom, e);
        }
    }


    public class UpdateTask implements Runnable {
        long lastRefTime = Long.MAX_VALUE;
        private String clusters;
        private String dom;
        private String env;
        private boolean allIPs = false;

        public UpdateTask(String dom, String clusters, String env) {
            this.dom = dom;
            this.clusters = clusters;
            this.env = env;
        }

        public UpdateTask(String dom, String clusters, String env, boolean allIPs) {
            this.dom = dom;
            this.clusters = clusters;
            this.env = env;
            this.allIPs = allIPs;
        }

        @Override
        public void run() {
            try {
                Domain domObj = domMap.get(Domain.getKey(dom, clusters, env, allIPs));

                if (domObj == null) {
                    if (allIPs) {
                        updateDom4AllIPNow(dom, clusters, env);
                    } else {
                        updateDomNow(dom, clusters, env);
                        executor.schedule(this, DEFAULT_DELAY, TimeUnit.MILLISECONDS);
                    }
                    return;
                }

                if (domObj.getLastRefTime() <= lastRefTime) {
                    if (allIPs) {
                        updateDom4AllIPNow(dom, clusters, env);
                        domObj = domMap.get(Domain.getKey(dom, clusters, env, true));
                    } else {
                        updateDomNow(dom, clusters, env);
                        domObj = domMap.get(Domain.getKey(dom, clusters, env));
                    }

                } else {
                    // if dom already updated by push, we should not override it
                    // since the push data may be different from pull through force push
                    refreshOnly(dom, clusters, env, allIPs);
                }

                executor.schedule(this, domObj.getCacheMillis(), TimeUnit.MILLISECONDS);

                lastRefTime = domObj.getLastRefTime();
            } catch (Throwable e) {
                LogUtils.LOG.warn("NA", "failed to update dom: " + dom, e);
            }

        }
    }
}
