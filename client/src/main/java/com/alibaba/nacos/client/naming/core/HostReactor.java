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
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.naming.pojo.Instance;
import com.alibaba.nacos.api.naming.pojo.ServiceInfo;
import com.alibaba.nacos.client.monitor.MetricsMonitor;
import com.alibaba.nacos.client.naming.backups.FailoverReactor;
import com.alibaba.nacos.client.naming.cache.DiskCache;
import com.alibaba.nacos.client.naming.net.NamingProxy;
import com.alibaba.nacos.client.naming.utils.UtilAndComs;
import com.alibaba.nacos.client.utils.StringUtils;

import java.util.*;
import java.util.concurrent.*;

import static com.alibaba.nacos.client.utils.LogUtils.NAMING_LOGGER;

/**
 * @author xuanyin
 */
public class HostReactor {

    private static final long DEFAULT_DELAY = 1000L;

    private static final long UPDATE_HOLD_INTERVAL = 5000L;

    private final Map<String, ScheduledFuture<?>> futureMap = new HashMap<String, ScheduledFuture<?>>();

    private Map<String, ServiceInfo> serviceInfoMap;

    private Map<String, Object> updatingMap;

    private PushReceiver pushReceiver;

    private EventDispatcher eventDispatcher;

    private NamingProxy serverProxy;

    private FailoverReactor failoverReactor;

    private String cacheDir;

    private ScheduledExecutorService executor;

    public HostReactor(EventDispatcher eventDispatcher, NamingProxy serverProxy, String cacheDir) {
        this(eventDispatcher, serverProxy, cacheDir, false, UtilAndComs.DEFAULT_POLLING_THREAD_COUNT);
    }

    /**
     *
     * @param eventDispatcher
     * @param serverProxy
     * @param cacheDir
     * @param loadCacheAtStart
     * @param pollingThreadCount
     */
    public HostReactor(EventDispatcher eventDispatcher, NamingProxy serverProxy, String cacheDir,
                       boolean loadCacheAtStart, int pollingThreadCount) {

        executor = new ScheduledThreadPoolExecutor(pollingThreadCount, new ThreadFactory() {
            @Override
            public Thread newThread(Runnable r) {
                Thread thread = new Thread(r);
                thread.setDaemon(true);
                thread.setName("com.alibaba.nacos.client.naming.updater");
                return thread;
            }
        });

        this.eventDispatcher = eventDispatcher;
        this.serverProxy = serverProxy;
        this.cacheDir = cacheDir;
        /**
         * 加载缓存文件中的数据到map
         */
        if (loadCacheAtStart) {
            this.serviceInfoMap = new ConcurrentHashMap<String, ServiceInfo>(DiskCache.read(this.cacheDir));
        } else {
            this.serviceInfoMap = new ConcurrentHashMap<String, ServiceInfo>(16);
        }

        this.updatingMap = new ConcurrentHashMap<String, Object>();
        /**
         * 容错
         */
        this.failoverReactor = new FailoverReactor(this, cacheDir);

        /**
         *
         */
        this.pushReceiver = new PushReceiver(this);
    }

    public Map<String, ServiceInfo> getServiceInfoMap() {
        return serviceInfoMap;
    }

    /**
     * 设置一次性调度任务
     * @param task
     * @return
     */
    public synchronized ScheduledFuture<?> addTask(UpdateTask task) {
        return executor.schedule(task, DEFAULT_DELAY, TimeUnit.MILLISECONDS);
    }

    /**
     * 处理数据
     * @param json
     * @return
     */
    public ServiceInfo processServiceJSON(String json) {
        ServiceInfo serviceInfo = JSON.parseObject(json, ServiceInfo.class);
        ServiceInfo oldService = serviceInfoMap.get(serviceInfo.getKey());
        /**
         * 校验
         */
        if (serviceInfo.getHosts() == null || !serviceInfo.validate()) {
            //empty or error push, just ignore
            return oldService;
        }

        boolean changed = false;

        if (oldService != null) {
            if (oldService.getLastRefTime() > serviceInfo.getLastRefTime()) {
                NAMING_LOGGER.warn("out of date data received, old-t: " + oldService.getLastRefTime()
                    + ", new-t: " + serviceInfo.getLastRefTime());
            }

            serviceInfoMap.put(serviceInfo.getKey(), serviceInfo);

            /**
             * 获取oldHostMap中的地址信息
             */
            Map<String, Instance> oldHostMap = new HashMap<String, Instance>(oldService.getHosts().size());
            for (Instance host : oldService.getHosts()) {
                oldHostMap.put(host.toInetAddr(), host);
            }

            /**
             * 获取serviceInfo中的地址信息
             */
            Map<String, Instance> newHostMap = new HashMap<String, Instance>(serviceInfo.getHosts().size());
            for (Instance host : serviceInfo.getHosts()) {
                newHostMap.put(host.toInetAddr(), host);
            }

            /**
             * 变化的地址
             */
            Set<Instance> modHosts = new HashSet<Instance>();
            /**
             * 新增的地址
             */
            Set<Instance> newHosts = new HashSet<Instance>();
            /**
             * 删除的地址
             */
            Set<Instance> remvHosts = new HashSet<Instance>();

            List<Map.Entry<String, Instance>> newServiceHosts = new ArrayList<Map.Entry<String, Instance>>(
                newHostMap.entrySet());
            /**
             * 比对newHostMap和oldHostMap   获取新增或者修改的地址
             */
            for (Map.Entry<String, Instance> entry : newServiceHosts) {
                Instance host = entry.getValue();
                String key = entry.getKey();
                /**
                 * 修改的地址
                 */
                if (oldHostMap.containsKey(key) && !StringUtils.equals(host.toString(),
                    oldHostMap.get(key).toString())) {
                    modHosts.add(host);
                    continue;
                }

                /**
                 * 新增的地址
                 */
                if (!oldHostMap.containsKey(key)) {
                    newHosts.add(host);
                }
            }

            /**
             * 比对newHostMap和oldHostMap   获取删除的地址
             */
            for (Map.Entry<String, Instance> entry : oldHostMap.entrySet()) {
                Instance host = entry.getValue();
                String key = entry.getKey();
                if (newHostMap.containsKey(key)) {
                    continue;
                }

                /**
                 * 删除的地址
                 */
                if (!newHostMap.containsKey(key)) {
                    remvHosts.add(host);
                }

            }

            if (newHosts.size() > 0) {
                changed = true;
                NAMING_LOGGER.info("new ips(" + newHosts.size() + ") service: "
                    + serviceInfo.getKey() + " -> " + JSON.toJSONString(newHosts));
            }

            if (remvHosts.size() > 0) {
                changed = true;
                NAMING_LOGGER.info("removed ips(" + remvHosts.size() + ") service: "
                    + serviceInfo.getKey() + " -> " + JSON.toJSONString(remvHosts));
            }

            if (modHosts.size() > 0) {
                changed = true;
                NAMING_LOGGER.info("modified ips(" + modHosts.size() + ") service: "
                    + serviceInfo.getKey() + " -> " + JSON.toJSONString(modHosts));
            }

            serviceInfo.setJsonFromServer(json);

            if (newHosts.size() > 0 || remvHosts.size() > 0 || modHosts.size() > 0) {
                /**
                 * 通知监听器   服务有变化
                 */
                eventDispatcher.serviceChanged(serviceInfo);
                /**
                 * 将数据从内存写入磁盘
                 */
                DiskCache.write(serviceInfo, cacheDir);
            }

        } else {
            changed = true;
            NAMING_LOGGER.info("init new ips(" + serviceInfo.ipCount() + ") service: " + serviceInfo.getKey() + " -> " + JSON
                .toJSONString(serviceInfo.getHosts()));
            serviceInfoMap.put(serviceInfo.getKey(), serviceInfo);
            /**
             * 通知监听器   服务有变化
             */
            eventDispatcher.serviceChanged(serviceInfo);

            serviceInfo.setJsonFromServer(json);
            /**
             * 将数据从内存写入磁盘
             */
            DiskCache.write(serviceInfo, cacheDir);
        }

        /**
         * prometheus监控
         */
        MetricsMonitor.getServiceInfoMapSizeMonitor().set(serviceInfoMap.size());

        if (changed) {
            NAMING_LOGGER.info("current ips:(" + serviceInfo.ipCount() + ") service: " + serviceInfo.getKey() +
                " -> " + JSON.toJSONString(serviceInfo.getHosts()));
        }

        return serviceInfo;
    }

    /**
     * 从本地缓存中获取serviceInfo
     * @param serviceName
     * @param clusters
     * @return
     */
    private ServiceInfo getServiceInfo0(String serviceName, String clusters) {

        String key = ServiceInfo.getKey(serviceName, clusters);

        return serviceInfoMap.get(key);
    }

    /**
     * 向服务端查询实例
     * @param serviceName
     * @param clusters
     * @return
     * @throws NacosException
     */
    public ServiceInfo getServiceInfoDirectlyFromServer(final String serviceName, final String clusters) throws NacosException {
        /**
         * 向服务端查询实例
         */
        String result = serverProxy.queryList(serviceName, clusters, 0, false);
        if (StringUtils.isNotEmpty(result)) {
            return JSON.parseObject(result, ServiceInfo.class);
        }
        return null;
    }

    /**
     * 从本地缓存中获取serviceInfo
     * @param serviceName
     * @param clusters
     * @return
     */
    public ServiceInfo getServiceInfo(final String serviceName, final String clusters) {

        NAMING_LOGGER.debug("failover-mode: " + failoverReactor.isFailoverSwitch());

        /**
         * serviceName@@clusters
         */
        String key = ServiceInfo.getKey(serviceName, clusters);

        /**
         * 开启了容灾   则从容灾策略中获取
         */
        if (failoverReactor.isFailoverSwitch()) {
            return failoverReactor.getService(key);
        }

        /**
         * 从本地缓存中获取serviceInfo
         */
        ServiceInfo serviceObj = getServiceInfo0(serviceName, clusters);

        /**
         * 缓存中没有
         */
        if (null == serviceObj) {
            serviceObj = new ServiceInfo(serviceName, clusters);

            serviceInfoMap.put(serviceObj.getKey(), serviceObj);

            /**
             * 设置更新标志
             */
            updatingMap.put(serviceName, new Object());

            /**
             * 立即更新
             */
            updateServiceNow(serviceName, clusters);

            /**
             * 更新结束  删除标志
             */
            updatingMap.remove(serviceName);

        } else if (updatingMap.containsKey(serviceName)) {
            /**
             * 有其他线程在执行更新操作   且没有执行结束    所有updatingMap中含有serviceName
             */

            if (UPDATE_HOLD_INTERVAL > 0) {
                // hold a moment waiting for update finish
                synchronized (serviceObj) {
                    try {
                        /**
                         * 等待其他线程更新操作结果
                         */
                        serviceObj.wait(UPDATE_HOLD_INTERVAL);
                    } catch (InterruptedException e) {
                        NAMING_LOGGER.error("[getServiceInfo] serviceName:" + serviceName + ", clusters:" + clusters, e);
                    }
                }
            }
        }

        /**
         * 调度更新
         */
        scheduleUpdateIfAbsent(serviceName, clusters);

        return serviceInfoMap.get(serviceObj.getKey());
    }

    /**
     * 调度更新
     * @param serviceName
     * @param clusters
     */
    public void scheduleUpdateIfAbsent(String serviceName, String clusters) {
        if (futureMap.get(ServiceInfo.getKey(serviceName, clusters)) != null) {
            return;
        }

        synchronized (futureMap) {
            if (futureMap.get(ServiceInfo.getKey(serviceName, clusters)) != null) {
                return;
            }

            /**
             * 设置更新任务
             */
            ScheduledFuture<?> future = addTask(new UpdateTask(serviceName, clusters));
            futureMap.put(ServiceInfo.getKey(serviceName, clusters), future);
        }
    }

    /**
     * 立即更新
     * @param serviceName
     * @param clusters
     */
    public void updateServiceNow(String serviceName, String clusters) {
        /**
         * 从本地缓存中获取serviceInfo
         */
        ServiceInfo oldService = getServiceInfo0(serviceName, clusters);
        try {

            /**
             * 向服务端查询实例
             */
            String result = serverProxy.queryList(serviceName, clusters, pushReceiver.getUDPPort(), false);

            if (StringUtils.isNotEmpty(result)) {
                /**
                 * 处理数据
                 */
                processServiceJSON(result);
            }
        } catch (Exception e) {
            NAMING_LOGGER.error("[NA] failed to update serviceName: " + serviceName, e);
        } finally {
            if (oldService != null) {
                synchronized (oldService) {
                    oldService.notifyAll();
                }
            }
        }
    }

    /**
     * 刷新数据   但没有后续操作
     * @param serviceName
     * @param clusters
     */
    public void refreshOnly(String serviceName, String clusters) {
        try {
            /**
             * 向服务端查询实例
             */
            serverProxy.queryList(serviceName, clusters, pushReceiver.getUDPPort(), false);
        } catch (Exception e) {
            NAMING_LOGGER.error("[NA] failed to update serviceName: " + serviceName, e);
        }
    }

    public class UpdateTask implements Runnable {
        long lastRefTime = Long.MAX_VALUE;
        private String clusters;
        private String serviceName;

        public UpdateTask(String serviceName, String clusters) {
            this.serviceName = serviceName;
            this.clusters = clusters;
        }

        @Override
        public void run() {
            try {
                /**
                 * 缓存中获取
                 */
                ServiceInfo serviceObj = serviceInfoMap.get(ServiceInfo.getKey(serviceName, clusters));

                if (serviceObj == null) {
                    /**
                     * 立即更新
                     */
                    updateServiceNow(serviceName, clusters);
                    /**
                     * 下次调度
                     */
                    executor.schedule(this, DEFAULT_DELAY, TimeUnit.MILLISECONDS);
                    return;
                }

                /**
                 * 比较上次应答时间
                 */
                if (serviceObj.getLastRefTime() <= lastRefTime) {
                    /**
                     * 立即更新
                     */
                    updateServiceNow(serviceName, clusters);
                    serviceObj = serviceInfoMap.get(ServiceInfo.getKey(serviceName, clusters));
                } else {
                    // if serviceName already updated by push, we should not override it
                    // since the push data may be different from pull through force push
                    /**
                     * 只刷新
                     */
                    refreshOnly(serviceName, clusters);
                }

                /**
                 * 下次任务
                 */
                executor.schedule(this, serviceObj.getCacheMillis(), TimeUnit.MILLISECONDS);

                lastRefTime = serviceObj.getLastRefTime();
            } catch (Throwable e) {
                NAMING_LOGGER.warn("[NA] failed to update serviceName: " + serviceName, e);
            }

        }
    }
}
