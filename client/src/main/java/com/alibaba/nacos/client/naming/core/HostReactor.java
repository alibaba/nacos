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
import com.alibaba.nacos.api.naming.pojo.ServiceInfo;
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

    private Map<String, ServiceInfo> serviceInfoMap;

    private PushRecver pushRecver;

    private EventDispatcher eventDispatcher;

    private NamingProxy serverProxy;

    private FailoverReactor failoverReactor;

    private String cacheDir;

    public HostReactor(EventDispatcher eventDispatcher, NamingProxy serverProxy, String cacheDir) {
        this.eventDispatcher = eventDispatcher;
        this.serverProxy = serverProxy;
        this.cacheDir = cacheDir;
        this.serviceInfoMap = new ConcurrentHashMap<String, ServiceInfo>(DiskCache.read(this.cacheDir));
        this.failoverReactor = new FailoverReactor(this, cacheDir);
        this.pushRecver = new PushRecver(this);
    }

    private ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(new ThreadFactory() {
        @Override
        public Thread newThread(Runnable r) {
            Thread thread = new Thread(r, "com.vipserver.client.updater");
            thread.setDaemon(true);

            return thread;
        }
    });

    public Map<String, ServiceInfo> getServiceInfoMap() {
        return serviceInfoMap;
    }

    public synchronized ScheduledFuture<?> addTask(UpdateTask task) {
        return executor.schedule(task, DEFAULT_DELAY, TimeUnit.MILLISECONDS);
    }

    public ServiceInfo processServiceJSON(String json) {
        ServiceInfo serviceInfo = JSON.parseObject(json, ServiceInfo.class);
        ServiceInfo oldService = serviceInfoMap.get(serviceInfo.getKey());
        if (serviceInfo.getHosts() == null || !serviceInfo.validate()) {
            //empty or error push, just ignore
            return oldService;
        }

        if (oldService != null) {
            if (oldService.getLastRefTime() > serviceInfo.getLastRefTime()) {
                LogUtils.LOG.warn("out of date data received, old-t: " + oldService.getLastRefTime()
                        + ", new-t: " + serviceInfo.getLastRefTime());
            }

            serviceInfoMap.put(serviceInfo.getKey(), serviceInfo);

            Map<String, Instance> oldHostMap = new HashMap<String, Instance>(oldService.getHosts().size());
            for (Instance host : oldService.getHosts()) {
                oldHostMap.put(host.toInetAddr(), host);
            }

            Map<String, Instance> newHostMap = new HashMap<String, Instance>(serviceInfo.getHosts().size());
            for (Instance host : serviceInfo.getHosts()) {
                newHostMap.put(host.toInetAddr(), host);
            }

            Set<Instance> modHosts = new HashSet<Instance>();
            Set<Instance> newHosts = new HashSet<Instance>();
            Set<Instance> remvHosts = new HashSet<Instance>();

            List<Map.Entry<String, Instance>> newServiceHosts = new ArrayList<Map.Entry<String, Instance>>(newHostMap.entrySet());
            for (Map.Entry<String, Instance> entry : newServiceHosts) {
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
                LogUtils.LOG.info("new ips(" + newHosts.size() + ") service: "
                        + serviceInfo.getName() + " -> " + JSON.toJSONString(newHosts));
            }

            if (remvHosts.size() > 0) {
                LogUtils.LOG.info("removed ips(" + remvHosts.size() + ") service: "
                        + serviceInfo.getName() + " -> " + JSON.toJSONString(remvHosts));
            }

            if (modHosts.size() > 0) {
                LogUtils.LOG.info("modified ips(" + modHosts.size() + ") service: "
                        + serviceInfo.getName() + " -> " + JSON.toJSONString(modHosts));
            }


            serviceInfo.setJsonFromServer(json);

            if (newHosts.size() > 0 || remvHosts.size() > 0 || modHosts.size() > 0) {
                eventDispatcher.serviceChanged(serviceInfo);
                DiskCache.write(serviceInfo, cacheDir);
            }

        } else {
            LogUtils.LOG.info("new ips(" + serviceInfo.ipCount() + ") service: " + serviceInfo.getName() + " -> " + JSON.toJSONString(serviceInfo.getHosts()));
            serviceInfoMap.put(serviceInfo.getKey(), serviceInfo);
            eventDispatcher.serviceChanged(serviceInfo);
            serviceInfo.setJsonFromServer(json);
            DiskCache.write(serviceInfo, cacheDir);
        }

        LogUtils.LOG.info("current ips:(" + serviceInfo.ipCount() + ") service: " + serviceInfo.getName() +
                " -> " + JSON.toJSONString(serviceInfo.getHosts()));

        return serviceInfo;
    }

    private ServiceInfo getSerivceInfo0(String serviceName, String clusters, String env) {

        String key = ServiceInfo.getKey(serviceName, clusters, env, false);

        return serviceInfoMap.get(key);
    }

    private ServiceInfo getSerivceInfo0(String serviceName, String clusters, String env, boolean allIPs) {

        String key = ServiceInfo.getKey(serviceName, clusters, env, allIPs);
        return serviceInfoMap.get(key);
    }

    public ServiceInfo getServiceInfo(String serviceName, String clusters, String env) {
        return getServiceInfo(serviceName, clusters, env, false);
    }

    public ServiceInfo getServiceInfo(String serviceName, String clusters) {
        String env = StringUtils.EMPTY;
        return getServiceInfo(serviceName, clusters, env, false);
    }

    public ServiceInfo getServiceInfo(final String serviceName, final String clusters, final String env, final boolean allIPs) {

        LogUtils.LOG.debug("failover-mode: " + failoverReactor.isFailoverSwitch());
        String key = ServiceInfo.getKey(serviceName, clusters, env, allIPs);
        if (failoverReactor.isFailoverSwitch()) {
            return failoverReactor.getService(key);
        }

        ServiceInfo serviceObj = getSerivceInfo0(serviceName, clusters, env, allIPs);

        if (null == serviceObj) {
            serviceObj = new ServiceInfo(serviceName, clusters, env);

            if (allIPs) {
                serviceObj.setAllIPs(allIPs);
            }

            serviceInfoMap.put(serviceObj.getKey(), serviceObj);

            if (allIPs) {
                updateService4AllIPNow(serviceName, clusters, env);
            } else {
                updateServiceNow(serviceName, clusters, env);
            }
        } else if (serviceObj.getHosts().isEmpty()) {

            if (updateHoldInterval > 0) {
                // hold a moment waiting for update finish
                synchronized (serviceObj) {
                    try {
                        serviceObj.wait(updateHoldInterval);
                    } catch (InterruptedException e) {
                        LogUtils.LOG.error("[getServiceInfo]", "serviceName:" + serviceName + ", clusters:" + clusters + ", allIPs:" + allIPs, e);
                    }
                }
            }
        }

        scheduleUpdateIfAbsent(serviceName, clusters, env, allIPs);

        return serviceInfoMap.get(serviceObj.getKey());
    }

    public void scheduleUpdateIfAbsent(String serviceName, String clusters, String env, boolean allIPs) {
        if (futureMap.get(ServiceInfo.getKey(serviceName, clusters, env, allIPs)) != null) {
            return;
        }

        synchronized (futureMap) {
            if (futureMap.get(ServiceInfo.getKey(serviceName, clusters, env, allIPs)) != null) {
                return;
            }

            ScheduledFuture<?> future = addTask(new UpdateTask(serviceName, clusters, env, allIPs));
            futureMap.put(ServiceInfo.getKey(serviceName, clusters, env, allIPs), future);
        }
    }

    public void updateService4AllIPNow(String serviceName, String clusters, String env) {
        updateService4AllIPNow(serviceName, clusters, env, -1L);
    }

    @SuppressFBWarnings("NN_NAKED_NOTIFY")
    public void updateService4AllIPNow(String serviceName, String clusters, String env, long timeout) {
        try {
            Map<String, String> params = new HashMap<String, String>(8);
            params.put("dom", serviceName);
            params.put("clusters", clusters);
            params.put("udpPort", String.valueOf(pushRecver.getUDPPort()));

            ServiceInfo oldService = getSerivceInfo0(serviceName, clusters, env, true);
            if (oldService != null) {
                params.put("checksum", oldService.getChecksum());
            }

            String result = serverProxy.reqAPI(UtilAndComs.NACOS_URL_BASE + "/api/srvAllIP", params);
            if (StringUtils.isNotEmpty(result)) {
                ServiceInfo serviceInfo = processServiceJSON(result);
                serviceInfo.setAllIPs(true);
            }

            if (oldService != null) {
                synchronized (oldService) {
                    oldService.notifyAll();
                }
            }

            //else nothing has changed
        } catch (Exception e) {
            LogUtils.LOG.error("NA", "failed to update serviceName: " + serviceName, e);
        }
    }

    @SuppressFBWarnings("NN_NAKED_NOTIFY")
    public void updateServiceNow(String serviceName, String clusters, String env) {
        ServiceInfo oldService = getSerivceInfo0(serviceName, clusters, env);
        try {
            Map<String, String> params = new HashMap<String, String>(8);
            params.put("dom", serviceName);
            params.put("clusters", clusters);
            params.put("udpPort", String.valueOf(pushRecver.getUDPPort()));
            params.put("env", env);
            params.put("clientIP", NetUtils.localIP());

            StringBuilder stringBuilder = new StringBuilder();
            for (String string : Balancer.UNCONSISTENT_SERVICE_WITH_ADDRESS_SERVER) {
                stringBuilder.append(string).append(",");
            }

            Balancer.UNCONSISTENT_SERVICE_WITH_ADDRESS_SERVER.clear();
            params.put("unconsistentDom", stringBuilder.toString());

            String envSpliter = ",";
            if (!StringUtils.isEmpty(env) && !env.contains(envSpliter)) {
                params.put("useEnvId", "true");
            }

            if (oldService != null) {
                params.put("checksum", oldService.getChecksum());
            }

            String result = serverProxy.reqAPI(UtilAndComs.NACOS_URL_BASE + "/api/srvIPXT", params);
            if (StringUtils.isNotEmpty(result)) {
                processServiceJSON(result);
            }
            //else nothing has changed
        } catch (Exception e) {
            LogUtils.LOG.error("NA", "failed to update serviceName: " + serviceName, e);
        } finally {
            if (oldService != null) {
                synchronized (oldService) {
                    oldService.notifyAll();
                }
            }
        }
    }

    public void refreshOnly(String serviceName, String clusters, String env, boolean allIPs) {
        try {
            Map<String, String> params = new HashMap<String, String>(16);
            params.put("dom", serviceName);
            params.put("clusters", clusters);
            params.put("udpPort", String.valueOf(pushRecver.getUDPPort()));
            params.put("unit", env);
            params.put("clientIP", NetUtils.localIP());

            String serviceSpliter = ",";
            StringBuilder stringBuilder = new StringBuilder();
            for (String string : Balancer.UNCONSISTENT_SERVICE_WITH_ADDRESS_SERVER) {
                stringBuilder.append(string).append(serviceSpliter);
            }

            Balancer.UNCONSISTENT_SERVICE_WITH_ADDRESS_SERVER.clear();
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
            LogUtils.LOG.error("NA", "failed to update serviceName: " + serviceName, e);
        }
    }


    public class UpdateTask implements Runnable {
        long lastRefTime = Long.MAX_VALUE;
        private String clusters;
        private String serviceName;
        private String env;
        private boolean allIPs = false;

        public UpdateTask(String serviceName, String clusters, String env) {
            this.serviceName = serviceName;
            this.clusters = clusters;
            this.env = env;
        }

        public UpdateTask(String serviceName, String clusters, String env, boolean allIPs) {
            this.serviceName = serviceName;
            this.clusters = clusters;
            this.env = env;
            this.allIPs = allIPs;
        }

        @Override
        public void run() {
            try {
                ServiceInfo serviceObj = serviceInfoMap.get(ServiceInfo.getKey(serviceName, clusters, env, allIPs));

                if (serviceObj == null) {
                    if (allIPs) {
                        updateService4AllIPNow(serviceName, clusters, env);
                    } else {
                        updateServiceNow(serviceName, clusters, env);
                        executor.schedule(this, DEFAULT_DELAY, TimeUnit.MILLISECONDS);
                    }
                    return;
                }

                if (serviceObj.getLastRefTime() <= lastRefTime) {
                    if (allIPs) {
                        updateService4AllIPNow(serviceName, clusters, env);
                        serviceObj = serviceInfoMap.get(ServiceInfo.getKey(serviceName, clusters, env, true));
                    } else {
                        updateServiceNow(serviceName, clusters, env);
                        serviceObj = serviceInfoMap.get(ServiceInfo.getKey(serviceName, clusters, env));
                    }

                } else {
                    // if serviceName already updated by push, we should not override it
                    // since the push data may be different from pull through force push
                    refreshOnly(serviceName, clusters, env, allIPs);
                }

                executor.schedule(this, serviceObj.getCacheMillis(), TimeUnit.MILLISECONDS);

                lastRefTime = serviceObj.getLastRefTime();
            } catch (Throwable e) {
                LogUtils.LOG.warn("NA", "failed to update serviceName: " + serviceName, e);
            }

        }
    }
}
