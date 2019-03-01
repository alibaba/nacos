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
package com.alibaba.nacos.client.naming;

import com.alibaba.nacos.api.PropertyKeyConst;
import com.alibaba.nacos.api.common.Constants;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.naming.NamingService;
import com.alibaba.nacos.api.naming.listener.EventListener;
import com.alibaba.nacos.api.naming.pojo.Instance;
import com.alibaba.nacos.api.naming.pojo.ListView;
import com.alibaba.nacos.api.naming.pojo.ServiceInfo;
import com.alibaba.nacos.api.selector.AbstractSelector;
import com.alibaba.nacos.client.naming.beat.BeatInfo;
import com.alibaba.nacos.client.naming.beat.BeatReactor;
import com.alibaba.nacos.client.naming.core.Balancer;
import com.alibaba.nacos.client.naming.core.EventDispatcher;
import com.alibaba.nacos.client.naming.core.HostReactor;
import com.alibaba.nacos.client.naming.net.NamingProxy;
import com.alibaba.nacos.client.naming.utils.CollectionUtils;
import com.alibaba.nacos.client.naming.utils.StringUtils;
import com.alibaba.nacos.client.naming.utils.UtilAndComs;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.math.NumberUtils;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Properties;

/**
 * @author <a href="mailto:zpf.073@gmail.com">nkorange</a>
 */
@SuppressWarnings("PMD.ServiceOrDaoClassShouldEndWithImplRule")
public class NacosNamingService implements NamingService {

    /**
     * Each Naming instance should have different namespace.
     */
    private String namespace;

    private String endpoint;

    private String serverList;

    private String cacheDir;

    private String logName;

    private HostReactor hostReactor;

    private BeatReactor beatReactor;

    private EventDispatcher eventDispatcher;

    private NamingProxy serverProxy;

    private void init() {

        namespace = System.getProperty(PropertyKeyConst.NAMESPACE);

        if (StringUtils.isEmpty(namespace)) {
            namespace = UtilAndComs.DEFAULT_NAMESPACE_ID;
        }

        logName = System.getProperty(UtilAndComs.NACOS_NAMING_LOG_NAME);
        if (StringUtils.isEmpty(logName)) {
            logName = "naming.log";
        }

        cacheDir = System.getProperty("com.alibaba.nacos.naming.cache.dir");
        if (StringUtils.isEmpty(cacheDir)) {
            cacheDir = System.getProperty("user.home") + "/nacos/naming/" + namespace;
        }
    }

    public NacosNamingService(String serverList) {

        this.serverList = serverList;
        init();
        eventDispatcher = new EventDispatcher();
        serverProxy = new NamingProxy(namespace, endpoint, serverList);
        beatReactor = new BeatReactor(serverProxy);
        hostReactor = new HostReactor(eventDispatcher, serverProxy, cacheDir);
    }

    public NacosNamingService(Properties properties) {

        init();

        serverList = properties.getProperty(PropertyKeyConst.SERVER_ADDR);

        if (StringUtils.isNotEmpty(properties.getProperty(PropertyKeyConst.NAMESPACE))) {
            namespace = properties.getProperty(PropertyKeyConst.NAMESPACE);
        }

        if (StringUtils.isNotEmpty(properties.getProperty(UtilAndComs.NACOS_NAMING_LOG_NAME))) {
            logName = properties.getProperty(UtilAndComs.NACOS_NAMING_LOG_NAME);
        }

        if (StringUtils.isNotEmpty(properties.getProperty(PropertyKeyConst.ENDPOINT))) {
            endpoint = properties.getProperty(PropertyKeyConst.ENDPOINT) + ":" +
                properties.getProperty("address.server.port", "8080");
        }

        cacheDir = System.getProperty("user.home") + "/nacos/naming/" + namespace;

        boolean loadCacheAtStart = false;
        if (StringUtils.isNotEmpty(properties.getProperty(PropertyKeyConst.NAMING_LOAD_CACHE_AT_START))) {
            loadCacheAtStart = BooleanUtils.toBoolean(
                properties.getProperty(PropertyKeyConst.NAMING_LOAD_CACHE_AT_START));
        }

        int clientBeatThreadCount = NumberUtils.toInt(
            properties.getProperty(PropertyKeyConst.NAMING_CLIENT_BEAT_THREAD_COUNT),
            UtilAndComs.DEFAULT_CLIENT_BEAT_THREAD_COUNT);

        int pollingThreadCount = NumberUtils.toInt(properties.getProperty(PropertyKeyConst.NAMING_POLLING_THREAD_COUNT),
            UtilAndComs.DEFAULT_POLLING_THREAD_COUNT);

        eventDispatcher = new EventDispatcher();
        serverProxy = new NamingProxy(namespace, endpoint, serverList);
        beatReactor = new BeatReactor(serverProxy, clientBeatThreadCount);
        hostReactor = new HostReactor(eventDispatcher, serverProxy, cacheDir, loadCacheAtStart, pollingThreadCount);

    }

    @Override
    public void registerInstance(String serviceName, String ip, int port) throws NacosException {
        registerInstance(serviceName, ip, port, Constants.NAMING_DEFAULT_CLUSTER_NAME);
    }

    @Override
    public void registerInstance(String serviceName, String ip, int port, String clusterName) throws NacosException {
        Instance instance = new Instance();
        instance.setIp(ip);
        instance.setPort(port);
        instance.setWeight(1.0);
        instance.setClusterName(clusterName);

        registerInstance(serviceName, instance);
    }

    @Override
    public void registerInstance(String serviceName, Instance instance) throws NacosException {

        BeatInfo beatInfo = new BeatInfo();
        beatInfo.setServiceName(serviceName);
        beatInfo.setIp(instance.getIp());
        beatInfo.setPort(instance.getPort());
        beatInfo.setCluster(instance.getClusterName());
        beatInfo.setWeight(instance.getWeight());
        beatInfo.setMetadata(instance.getMetadata());
        beatInfo.setScheduled(false);

        beatReactor.addBeatInfo(serviceName, beatInfo);

        serverProxy.registerService(serviceName, instance);
    }

    @Override
    public void deregisterInstance(String serviceName, String ip, int port) throws NacosException {
        deregisterInstance(serviceName, ip, port, Constants.NAMING_DEFAULT_CLUSTER_NAME);
    }

    @Override
    public void deregisterInstance(String serviceName, String ip, int port, String clusterName) throws NacosException {
        beatReactor.removeBeatInfo(serviceName, ip, port);
        serverProxy.deregisterService(serviceName, ip, port, clusterName);
    }

    @Override
    public List<Instance> getAllInstances(String serviceName) throws NacosException {
        return getAllInstances(serviceName, new ArrayList<String>());
    }

    @Override
    public List<Instance> getAllInstances(String serviceName, boolean subscribe) throws NacosException {
        return getAllInstances(serviceName, new ArrayList<String>(), subscribe);
    }

    @Override
    public List<Instance> getAllInstances(String serviceName, List<String> clusters) throws NacosException {
        return getAllInstances(serviceName, clusters, true);
    }

    @Override
    public List<Instance> getAllInstances(String serviceName, List<String> clusters, boolean subscribe)
        throws NacosException {

        ServiceInfo serviceInfo;
        if (subscribe) {
            serviceInfo = hostReactor.getServiceInfo(serviceName, StringUtils.join(clusters, ","));
        } else {
            serviceInfo = hostReactor.getServiceInfoDirectlyFromServer(serviceName, StringUtils.join(clusters, ","));
        }
        List<Instance> list;
        if (serviceInfo == null || CollectionUtils.isEmpty(list = serviceInfo.getHosts())) {
            return new ArrayList<Instance>();
        }
        return list;
    }

    @Override
    public List<Instance> selectInstances(String serviceName, boolean healthy) throws NacosException {
        return selectInstances(serviceName, new ArrayList<String>(), healthy);
    }

    @Override
    public List<Instance> selectInstances(String serviceName, boolean healthy, boolean subscribe)
        throws NacosException {
        return selectInstances(serviceName, new ArrayList<String>(), healthy, subscribe);
    }

    @Override
    public List<Instance> selectInstances(String serviceName, List<String> clusters, boolean healthy)
        throws NacosException {
        return selectInstances(serviceName, clusters, healthy, true);
    }

    @Override
    public List<Instance> selectInstances(String serviceName, List<String> clusters, boolean healthy,
                                          boolean subscribe) throws NacosException {
        ServiceInfo serviceInfo;
        if (subscribe) {
            serviceInfo = hostReactor.getServiceInfo(serviceName, StringUtils.join(clusters, ","));
        } else {
            serviceInfo = hostReactor.getServiceInfoDirectlyFromServer(serviceName, StringUtils.join(clusters, ","));
        }
        return selectInstances(serviceInfo, healthy);
    }

    @Override
    public Instance selectOneHealthyInstance(String serviceName) throws NacosException {
        return selectOneHealthyInstance(serviceName, new ArrayList<String>());
    }

    @Override
    public Instance selectOneHealthyInstance(String serviceName, boolean subscribe) throws NacosException {
        return selectOneHealthyInstance(serviceName, new ArrayList<String>(), subscribe);
    }

    @Override
    public Instance selectOneHealthyInstance(String serviceName, List<String> clusters) throws NacosException {
        return selectOneHealthyInstance(serviceName, clusters, true);
    }

    @Override
    public Instance selectOneHealthyInstance(String serviceName, List<String> clusters, boolean subscribe)
        throws NacosException {

        if (subscribe) {
            return Balancer.RandomByWeight.selectHost(
                hostReactor.getServiceInfo(serviceName, StringUtils.join(clusters, ",")));
        } else {
            return Balancer.RandomByWeight.selectHost(
                hostReactor.getServiceInfoDirectlyFromServer(serviceName, StringUtils.join(clusters, ",")));
        }
    }

    @Override
    public void subscribe(String service, EventListener listener) {
        eventDispatcher.addListener(hostReactor.getServiceInfo(service, StringUtils.EMPTY), StringUtils.EMPTY,
            listener);
    }

    @Override
    public void subscribe(String service, List<String> clusters, EventListener listener) {
        eventDispatcher.addListener(hostReactor.getServiceInfo(service, StringUtils.join(clusters, ",")),
            StringUtils.join(clusters, ","), listener);
    }

    @Override
    public void unsubscribe(String service, EventListener listener) {
        eventDispatcher.removeListener(service, StringUtils.EMPTY, listener);
    }

    @Override
    public void unsubscribe(String service, List<String> clusters, EventListener listener) {
        eventDispatcher.removeListener(service, StringUtils.join(clusters, ","), listener);
    }

    @Override
    public ListView<String> getServicesOfServer(int pageNo, int pageSize) throws NacosException {
        return serverProxy.getServiceList(pageNo, pageSize);
    }

    @Override
    public ListView<String> getServicesOfServer(int pageNo, int pageSize, AbstractSelector selector)
        throws NacosException {
        return serverProxy.getServiceList(pageNo, pageSize, selector);
    }

    @Override
    public List<ServiceInfo> getSubscribeServices() {
        return eventDispatcher.getSubscribeServices();
    }

    @Override
    public String getServerStatus() {
        return serverProxy.serverHealthy() ? "UP" : "DOWN";
    }

    private List<Instance> selectInstances(ServiceInfo serviceInfo, boolean healthy) {
        List<Instance> list;
        if (serviceInfo == null || CollectionUtils.isEmpty(list = serviceInfo.getHosts())) {
            return new ArrayList<Instance>();
        }

        Iterator<Instance> iterator = list.iterator();
        while (iterator.hasNext()) {
            Instance instance = iterator.next();
            if (healthy != instance.isHealthy() || !instance.isEnabled() || instance.getWeight() <= 0) {
                iterator.remove();
            }
        }

        return list;
    }

    public BeatReactor getBeatReactor() {
        return beatReactor;
    }
}
