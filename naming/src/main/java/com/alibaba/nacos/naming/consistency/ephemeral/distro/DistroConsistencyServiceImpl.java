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
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.core.utils.SystemUtils;
import com.alibaba.nacos.naming.cluster.ServerListManager;
import com.alibaba.nacos.naming.cluster.ServerStatus;
import com.alibaba.nacos.naming.cluster.servers.Server;
import com.alibaba.nacos.naming.cluster.transport.Serializer;
import com.alibaba.nacos.naming.consistency.ApplyAction;
import com.alibaba.nacos.naming.consistency.Datum;
import com.alibaba.nacos.naming.consistency.KeyBuilder;
import com.alibaba.nacos.naming.consistency.RecordListener;
import com.alibaba.nacos.naming.consistency.ephemeral.EphemeralConsistencyService;
import com.alibaba.nacos.naming.core.DistroMapper;
import com.alibaba.nacos.naming.core.Instances;
import com.alibaba.nacos.naming.core.Service;
import com.alibaba.nacos.naming.misc.*;
import com.alibaba.nacos.naming.pojo.Record;
import org.apache.commons.lang3.StringUtils;
import org.javatuples.Pair;
import org.springframework.beans.factory.annotation.Autowired;

import javax.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;

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
@org.springframework.stereotype.Service("distroConsistencyService")
public class DistroConsistencyServiceImpl implements EphemeralConsistencyService {

    private ScheduledExecutorService executor = new ScheduledThreadPoolExecutor(1, new ThreadFactory() {
        @Override
        public Thread newThread(Runnable r) {
            Thread t = new Thread(r);

            t.setDaemon(true);
            t.setName("com.alibaba.nacos.naming.distro.notifier");

            return t;
        }
    });

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

    @Autowired
    private GlobalConfig globalConfig;

    private boolean initialized = false;

    public volatile Notifier notifier = new Notifier();

    private Map<String, CopyOnWriteArrayList<RecordListener>> listeners = new ConcurrentHashMap<>();

    private Map<String, String> syncChecksumTasks = new ConcurrentHashMap<>(16);

    @PostConstruct
    public void init() {
        GlobalExecutor.submit(new Runnable() {
            @Override
            public void run() {
                try {
                    load();
                } catch (Exception e) {
                    Loggers.DISTRO.error("load data failed.", e);
                }
            }
        });

        executor.submit(notifier);
    }

    /**
     * 初始化加载
     * @throws Exception
     */
    public void load() throws Exception {
        if (SystemUtils.STANDALONE_MODE) {
            initialized = true;
            return;
        }
        // size = 1 means only myself in the list, we need at least one another server alive:
        while (serverListManager.getHealthyServers().size() <= 1) {
            Thread.sleep(1000L);
            Loggers.DISTRO.info("waiting server list init...");
        }

        /**
         * 健康的nacos节点
         */
        for (Server server : serverListManager.getHealthyServers()) {
            /**
             * 过滤掉本机
             */
            if (NetUtils.localServer().equals(server.getKey())) {
                continue;
            }
            if (Loggers.DISTRO.isDebugEnabled()) {
                Loggers.DISTRO.debug("sync from " + server);
            }
            // try sync data from remote server:
            /**
             * 同步其他nacos节点数据   只要有一个节点返回正确数据  就终止循环
             */
            if (syncAllDataFromRemote(server)) {
                initialized = true;
                return;
            }
        }
    }

    /**
     * 新增临时节点
     * @param key   key of data, this key should be globally unique
     * @param value value of data   Instances
     * @throws NacosException
     */
    @Override
    public void put(String key, Record value) throws NacosException {
        /**
         * 将Instances存入存入dataStore缓存  并新增调度任务执行RecordListener得监听
         */
        onPut(key, value);

        /**
         * taskDispatcher调度   执行dataStore得同步  通知其他节点
         */
        taskDispatcher.addTask(key);
    }

    @Override
    public void remove(String key) throws NacosException {
        onRemove(key);
        listeners.remove(key);
    }

    /**
     * 获取临时key对应的Datum
     * @param key key of data
     * @return
     * @throws NacosException
     */
    @Override
    public Datum get(String key) throws NacosException {
        return dataStore.get(key);
    }

    /**
     * 缓存key对应的Record
     * @param key
     * @param value   Instances
     */
    public void onPut(String key, Record value) {
        /**
         * 临时节点
         */
        if (KeyBuilder.matchEphemeralInstanceListKey(key)) {
            Datum<Instances> datum = new Datum<>();
            datum.value = (Instances) value;
            datum.key = key;
            datum.timestamp.incrementAndGet();
            /**
             * 存入缓存
             * 因为Instances是一个包含Instance的集合   所以当前操作  可能是新增或修改或删除
             */
            dataStore.put(key, datum);
        }

        if (!listeners.containsKey(key)) {
            return;
        }

        /**
         * 新增调度任务   执行RecordListener得监听
         */
        notifier.addTask(key, ApplyAction.CHANGE);
    }

    /**
     * 移除当前key
     * @param key
     */
    public void onRemove(String key) {

        dataStore.remove(key);

        if (!listeners.containsKey(key)) {
            return;
        }

        /**
         * 通知
         */
        notifier.addTask(key, ApplyAction.DELETE);
    }

    public void onReceiveChecksums(Map<String, String> checksumMap, String server) {

        /**
         * server已被锁定    正在处理
         */
        if (syncChecksumTasks.containsKey(server)) {
            // Already in process of this server:
            Loggers.DISTRO.warn("sync checksum task already in process with {}", server);
            return;
        }

        /**
         * 加锁
         */
        syncChecksumTasks.put(server, "1");

        try {

            List<String> toUpdateKeys = new ArrayList<>();
            List<String> toRemoveKeys = new ArrayList<>();
            for (Map.Entry<String, String> entry : checksumMap.entrySet()) {
                /**
                 * 是否由本地节点处理
                 */
                if (distroMapper.responsible(KeyBuilder.getServiceName(entry.getKey()))) {
                    // this key should not be sent from remote server:
                    Loggers.DISTRO.error("receive responsible key timestamp of " + entry.getKey() + " from " + server);
                    // abort the procedure:
                    return;
                }

                /**
                 * 本地不包含当前key  ||  本地key对应的数据为null  ||  本地key对应的Checksum和请求数据不同
                 */
                if (!dataStore.contains(entry.getKey()) ||
                    dataStore.get(entry.getKey()).value == null ||
                    !dataStore.get(entry.getKey()).value.getChecksum().equals(entry.getValue())) {
                    /**
                     * key对应的数据待更新
                     */
                    toUpdateKeys.add(entry.getKey());
                }
            }

            /**
             * 遍历本地dataStore
             */
            for (String key : dataStore.keys()) {

                /**
                 * 根据路由规则  当前key对应的节点   不是当前server
                 */
                if (!server.equals(distroMapper.mapSrv(KeyBuilder.getServiceName(key)))) {
                    continue;
                }

                /**
                 * 最新数据中  不包含本地key   则key待移除
                 */
                if (!checksumMap.containsKey(key)) {
                    toRemoveKeys.add(key);
                }
            }

            if (Loggers.DISTRO.isDebugEnabled()) {
                Loggers.DISTRO.info("to remove keys: {}, to update keys: {}, source: {}", toRemoveKeys, toUpdateKeys, server);
            }

            /**
             * 移除key
             */
            for (String key : toRemoveKeys) {
                onRemove(key);
            }

            if (toUpdateKeys.isEmpty()) {
                return;
            }

            try {
                /**
                 * 向server发起请求  查询toUpdateKeys中的key   对应的Datum
                 */
                byte[] result = NamingProxy.getData(toUpdateKeys, server);
                /**
                 * 处理server节点返回的Datum
                 */
                processData(result);
            } catch (Exception e) {
                Loggers.DISTRO.error("get data from " + server + " failed!", e);
            }
        } finally {
            // Remove this 'in process' flag:
            /**
             * 解锁
             */
            syncChecksumTasks.remove(server);
        }

    }

    /**
     * 同步Server的数据
     * @param server
     * @return
     */
    public boolean syncAllDataFromRemote(Server server) {

        try {
            /**
             * 获取server对应的dataStore数据
             */
            byte[] data = NamingProxy.getAllData(server.getKey());
            /**
             * 处理数据
             */
            processData(data);
            return true;
        } catch (Exception e) {
            Loggers.DISTRO.error("sync full data from " + server + " failed!", e);
            return false;
        }
    }

    /**
     * 处理其他节点传送的Datum数据
     * @param data
     * @throws Exception
     */
    public void processData(byte[] data) throws Exception {
        if (data.length > 0) {
            /**
             * 反序列化
             */
            Map<String, Datum<Instances>> datumMap =
                serializer.deserializeMap(data, Instances.class);

            for (Map.Entry<String, Datum<Instances>> entry : datumMap.entrySet()) {
                /**
                 * 本地更新远程nacos节点的dataStore
                 */
                dataStore.put(entry.getKey(), entry.getValue());

                /**
                 * 尚未监听
                 */
                if (!listeners.containsKey(entry.getKey())) {
                    // pretty sure the service not exist:
                    if (switchDomain.isDefaultInstanceEphemeral()) {
                        // create empty service
                        Loggers.DISTRO.info("creating service {}", entry.getKey());
                        Service service = new Service();
                        String serviceName = KeyBuilder.getServiceName(entry.getKey());
                        String namespaceId = KeyBuilder.getNamespace(entry.getKey());
                        service.setName(serviceName);
                        service.setNamespaceId(namespaceId);
                        service.setGroupName(Constants.DEFAULT_GROUP);
                        // now validate the service. if failed, exception will be thrown
                        service.setLastModifiedMillis(System.currentTimeMillis());
                        /**
                         * 重新计算checksum
                         */
                        service.recalculateChecksum();
                        /**
                         * 监听  metadata  调用ServiceManager   并向listeners添加新的监听
                         */
                        listeners.get(KeyBuilder.SERVICE_META_KEY_PREFIX).get(0)
                            .onChange(KeyBuilder.buildServiceMetaKey(namespaceId, serviceName), service);
                    }
                }
            }

            for (Map.Entry<String, Datum<Instances>> entry : datumMap.entrySet()) {
                /**
                 * 只更新监听
                 */
                if (!listeners.containsKey(entry.getKey())) {
                    // Should not happen:
                    Loggers.DISTRO.warn("listener of {} not found.", entry.getKey());
                    continue;
                }

                try {
                    for (RecordListener listener : listeners.get(entry.getKey())) {
                        /**
                         * 更新监听
                         */
                        listener.onChange(entry.getKey(), entry.getValue().value);
                    }
                } catch (Exception e) {
                    Loggers.DISTRO.error("[NACOS-DISTRO] error while execute listener of key: {}", entry.getKey(), e);
                    continue;
                }

                // Update data store if listener executed successfully:
                dataStore.put(entry.getKey(), entry.getValue());
            }
        }
    }

    /**
     * 在listeners中添加key对应得listener
     * @param key      key of data
     * @param listener callback of data change
     * @throws NacosException
     */
    @Override
    public void listen(String key, RecordListener listener) throws NacosException {
        if (!listeners.containsKey(key)) {
            listeners.put(key, new CopyOnWriteArrayList<>());
        }

        if (listeners.get(key).contains(listener)) {
            return;
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
        return isInitialized() || ServerStatus.UP.name().equals(switchDomain.getOverriddenServerStatus());
    }

    public boolean isInitialized() {
        return initialized || !globalConfig.isDataWarmup();
    }

    public class Notifier implements Runnable {

        private ConcurrentHashMap<String, String> services = new ConcurrentHashMap<>(10 * 1024);

        private BlockingQueue<Pair> tasks = new LinkedBlockingQueue<Pair>(1024 * 1024);

        /**
         * 新增调度任务
         * @param datumKey
         * @param action
         */
        public void addTask(String datumKey, ApplyAction action) {

            /**
             * CHANGE任务   有一个就可以
             */
            if (services.containsKey(datumKey) && action == ApplyAction.CHANGE) {
                return;
            }

            /**
             * CHANGE任务   默认初始值
             */
            if (action == ApplyAction.CHANGE) {
                services.put(datumKey, StringUtils.EMPTY);
            }

            /**
             * 加入阻塞队列
             */
            tasks.add(Pair.with(datumKey, action));
        }

        public int getTaskSize() {
            return tasks.size();
        }

        @Override
        public void run() {
            Loggers.DISTRO.info("distro notifier started");

            while (true) {
                try {

                    Pair pair = tasks.take();

                    if (pair == null) {
                        continue;
                    }

                    String datumKey = (String) pair.getValue0();
                    ApplyAction action = (ApplyAction) pair.getValue1();

                    /**
                     * 移除   这样可以接受下一个change任务
                     */
                    services.remove(datumKey);

                    int count = 0;

                    if (!listeners.containsKey(datumKey)) {
                        continue;
                    }

                    /**
                     * 遍历datumKey对应的监听器
                     */
                    for (RecordListener listener : listeners.get(datumKey)) {

                        count++;

                        try {
                            if (action == ApplyAction.CHANGE) {
                                listener.onChange(datumKey, dataStore.get(datumKey).value);
                                continue;
                            }

                            if (action == ApplyAction.DELETE) {
                                listener.onDelete(datumKey);
                                continue;
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
