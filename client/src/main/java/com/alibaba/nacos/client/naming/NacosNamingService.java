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
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.naming.NamingService;
import com.alibaba.nacos.api.naming.listener.EventListener;
import com.alibaba.nacos.api.naming.pojo.Cluster;
import com.alibaba.nacos.api.naming.pojo.Instance;
import com.alibaba.nacos.api.naming.pojo.ListView;
import com.alibaba.nacos.api.naming.pojo.ServiceInfo;
import com.alibaba.nacos.client.naming.beat.BeatInfo;
import com.alibaba.nacos.client.naming.beat.BeatReactor;
import com.alibaba.nacos.client.naming.core.Balancer;
import com.alibaba.nacos.client.naming.core.EventDispatcher;
import com.alibaba.nacos.client.naming.core.HostReactor;
import com.alibaba.nacos.client.naming.net.NamingProxy;
import com.alibaba.nacos.client.naming.utils.CollectionUtils;
import com.alibaba.nacos.client.naming.utils.LogUtils;
import com.alibaba.nacos.client.naming.utils.StringUtils;
import com.alibaba.nacos.client.naming.utils.UtilAndComs;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Properties;

/**
 * @author dungu.zpf
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

        String logLevel = System.getProperty(UtilAndComs.NACOS_NAMING_LOG_LEVEL);
        if (StringUtils.isEmpty(logLevel)) {
            logLevel = "INFO";
        }

        LogUtils.setLogLevel(logLevel);

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

        eventDispatcher = new EventDispatcher();
        serverProxy = new NamingProxy(namespace, endpoint, serverList);
        beatReactor = new BeatReactor(serverProxy);
        hostReactor = new HostReactor(eventDispatcher, serverProxy, cacheDir);

    }

    @Override
    public void registerInstance(String serviceName, String ip, int port) throws NacosException {
        registerInstance(serviceName, ip, port, StringUtils.EMPTY);
    }

    @Override
    public void registerInstance(String serviceName, String ip, int port, String clusterName) throws NacosException {
        Instance instance = new Instance();
        instance.setIp(ip);
        instance.setPort(port);
        instance.setWeight(1.0);
        instance.setCluster(new Cluster(clusterName));

        registerInstance(serviceName, instance);
    }

    @Override
    public void registerInstance(String serviceName, Instance instance) throws NacosException {

        BeatInfo beatInfo = new BeatInfo();
        beatInfo.setDom(serviceName);
        beatInfo.setIp(instance.getIp());
        beatInfo.setPort(instance.getPort());
        beatInfo.setCluster(instance.getCluster().getName());

        beatReactor.addBeatInfo(serviceName, beatInfo);

        serverProxy.registerService(serviceName, instance);
    }

    @Override
    public void deregisterInstance(String serviceName, String ip, int port) throws NacosException {
        deregisterInstance(serviceName, ip, port, StringUtils.EMPTY);
    }

    @Override
    public void deregisterInstance(String serviceName, String ip, int port, String clusterName) throws NacosException {
        beatReactor.removeBeatInfo(serviceName);
        serverProxy.deregisterService(serviceName, ip, port, clusterName);
    }

    @Override
    public List<Instance> getAllInstances(String serviceName) throws NacosException {
        return getAllInstances(serviceName, new ArrayList<String>());
    }

    @Override
    public List<Instance> getAllInstances(String serviceName, List<String> clusters) throws NacosException {

        ServiceInfo serviceInfo = hostReactor.getServiceInfo(serviceName, StringUtils.join(clusters, ","), StringUtils.EMPTY, false);
        List<Instance> list;
        if (serviceInfo == null || CollectionUtils.isEmpty(list = serviceInfo.getHosts())) {
            return new ArrayList<Instance>();
        }
        return list;
    }

    @Override
    public List<Instance> selectInstances(String serviceName, boolean healthyOnly) throws NacosException {
        return selectInstances(serviceName, new ArrayList<String>(), healthyOnly);
    }

    @Override
    public List<Instance> selectInstances(String serviceName, List<String> clusters, boolean healthy) throws NacosException {

        ServiceInfo serviceInfo = hostReactor.getServiceInfo(serviceName, StringUtils.join(clusters, ","), StringUtils.EMPTY, false);
        List<Instance> list;
        if (serviceInfo == null || CollectionUtils.isEmpty(list = serviceInfo.getHosts())) {
            return new ArrayList<Instance>();
        }

        if (healthy) {
            Iterator<Instance> iterator = list.iterator();
            while (iterator.hasNext()) {
                Instance instance = iterator.next();
                if (!instance.isHealthy()) {
                    iterator.remove();
                }
            }
        } else {
            Iterator<Instance> iterator = list.iterator();
            while (iterator.hasNext()) {
                Instance instance = iterator.next();
                if (instance.isHealthy()) {
                    iterator.remove();
                }
            }
        }

        return list;
    }

    @Override
    public Instance selectOneHealthyInstance(String serviceName) {
        return selectOneHealthyInstance(serviceName, new ArrayList<String>());
    }

    @Override
    public Instance selectOneHealthyInstance(String serviceName, List<String> clusters) {
        return Balancer.RandomByWeight.selectHost(hostReactor.getServiceInfo(serviceName, StringUtils.join(clusters, ",")));
    }

    @Override
    public void subscribe(String service, EventListener listener) {
        eventDispatcher.addListener(hostReactor.getServiceInfo(service, StringUtils.EMPTY), StringUtils.EMPTY, listener);
    }

    @Override
    public void subscribe(String service, List<String> clusters, EventListener listener) {
        eventDispatcher.addListener(hostReactor.getServiceInfo(service, StringUtils.join(clusters, ",")), StringUtils.join(clusters, ","), listener);
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
    public List<ServiceInfo> getSubscribeServices() {
        return new ArrayList<ServiceInfo>(hostReactor.getServiceInfoMap().values());
    }

    @Override
    public String getServerStatus() {
        return serverProxy.serverHealthy() ? "UP" : "DOWN";
    }
}
