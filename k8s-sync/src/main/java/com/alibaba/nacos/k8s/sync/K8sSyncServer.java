/*
 * Copyright 1999-2022 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.k8s.sync;

import com.alibaba.nacos.api.common.Constants;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.naming.pojo.Instance;
import com.alibaba.nacos.common.utils.ThreadUtils;
import com.alibaba.nacos.naming.core.InstanceOperatorClientImpl;
import com.alibaba.nacos.naming.core.ServiceOperatorV2Impl;
import com.alibaba.nacos.naming.core.v2.ServiceManager;
import com.alibaba.nacos.naming.core.v2.pojo.Service;
import io.kubernetes.client.informer.ResourceEventHandler;
import io.kubernetes.client.informer.SharedIndexInformer;
import io.kubernetes.client.informer.SharedInformerFactory;
import io.kubernetes.client.informer.cache.Lister;
import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.Configuration;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.models.V1EndpointAddress;
import io.kubernetes.client.openapi.models.V1EndpointSubset;
import io.kubernetes.client.openapi.models.V1Endpoints;
import io.kubernetes.client.openapi.models.V1EndpointsList;
import io.kubernetes.client.openapi.models.V1Service;
import io.kubernetes.client.openapi.models.V1ServiceList;
import io.kubernetes.client.openapi.models.V1ServicePort;
import io.kubernetes.client.util.CallGeneratorParams;
import io.kubernetes.client.util.ClientBuilder;
import io.kubernetes.client.util.KubeConfig;
import okhttp3.OkHttpClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.io.FileReader;
import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Start and stop k8s-sync.
 *
 * @author EmanuelGi
 */
@Component
public class K8sSyncServer {
    
    @Autowired
    private K8sSyncConfig k8sSyncConfig;
    
    @Autowired
    private ServiceOperatorV2Impl serviceOperatorV2;
    
    @Autowired
    private InstanceOperatorClientImpl instanceOperatorClient;
    
    private SharedInformerFactory factory;
    
    /**
     * start.
     *
     * @throws IOException io exception
     */
    @EventListener(ApplicationReadyEvent.class)
    public void start() throws IOException {
        if (!k8sSyncConfig.isEnabled()) {
            Loggers.MAIN.info("The Nacos k8s-sync is disabled.");
            return;
        }
        Loggers.MAIN.info("Starting Nacos k8s-sync ...");
        startInformer();
        Runtime.getRuntime().addShutdownHook(new Thread() {
            @Override
            public void run() {
                Loggers.MAIN.info("Stopping Nacos k8s-sync ...");
                K8sSyncServer.this.stop();
                Loggers.MAIN.info("Nacos k8s-sync stopped...");
            }
        });
    }
    
    /**
     * start informer.
     *
     * @throws IOException io exception
     */
    @SuppressWarnings("PMD.MethodTooLongRule")
    public void startInformer() throws IOException {
        ApiClient apiClient;
        CoreV1Api coreV1Api;
        
        if (k8sSyncConfig.isOutsideCluster()) {
            apiClient = getOutsideApiClient();
        } else {
            apiClient = ClientBuilder.cluster().build();
        }
        // set the global default api-client
        Configuration.setDefaultApiClient(apiClient);
        coreV1Api = new CoreV1Api();

        OkHttpClient httpClient = apiClient.getHttpClient().newBuilder().build();
        apiClient.setHttpClient(httpClient);
    
        factory = new SharedInformerFactory(apiClient);
        SharedIndexInformer<V1Service> serviceInformer =
                factory.sharedIndexInformerFor(
                        (CallGeneratorParams params) -> {
                            CoreV1Api.APIlistServiceForAllNamespacesRequest request = coreV1Api.listServiceForAllNamespaces();
                            request.resourceVersion(params.resourceVersion);
                            request.timeoutSeconds(params.timeoutSeconds);
                            request.watch(params.watch);
                            return request.buildCall(null);
                        },
                        V1Service.class,
                        V1ServiceList.class);
        
        SharedIndexInformer<V1Endpoints> endpointInformer =
                factory.sharedIndexInformerFor(
                        (CallGeneratorParams params) -> {
                            CoreV1Api.APIlistEndpointsForAllNamespacesRequest request = coreV1Api.listEndpointsForAllNamespaces();
                            request.resourceVersion(params.resourceVersion);
                            request.timeoutSeconds(params.timeoutSeconds);
                            request.watch(params.watch);
                            return request.buildCall(null);
                        },
                        V1Endpoints.class,
                        V1EndpointsList.class);
        
        serviceInformer.addEventHandler(
                new ResourceEventHandler<V1Service>() {
                    @Override
                    public void onAdd(V1Service service) {
                        if (service.getMetadata() == null || service.getSpec() == null) {
                            return;
                        }
                        String serviceName = service.getMetadata().getName();
                        String namespace = service.getMetadata().getNamespace();
                        List<V1ServicePort> servicePorts = service.getSpec().getPorts();
                        try {
                            registerService(namespace, serviceName, servicePorts, false, endpointInformer);
                            Loggers.MAIN.info("add service, namespace:" + namespace + " serviceName: " + serviceName);
                        } catch (Exception e) {
                            Loggers.MAIN.warn("add service fail, message:" + e.getMessage() + " namespace:"
                                    + namespace + " serviceName: " + serviceName);
                        }
                    }
                
                    @Override
                    public void onUpdate(V1Service oldService, V1Service newService) {
                        if (oldService.getMetadata() == null || oldService.getSpec() == null
                                || newService.getMetadata() == null || newService.getSpec() == null) {
                            return;
                        }
                        List<V1ServicePort> oldServicePorts = oldService.getSpec().getPorts();
                        String serviceName = newService.getMetadata().getName();
                        String namespace = newService.getMetadata().getNamespace();
                        List<V1ServicePort> newServicePorts = newService.getSpec().getPorts();
                        boolean portChanged = compareServicePorts(oldServicePorts, newServicePorts);
                        try {
                            registerService(namespace, serviceName, newServicePorts, portChanged, endpointInformer);
                            Loggers.MAIN.info("update service, namespace: " + namespace + " serviceName: " + serviceName);
                        } catch (Exception e) {
                            Loggers.MAIN.warn("update service fail, message: " + e.getMessage() + " namespace: "
                                    + namespace + " serviceName: " + serviceName);
                        }
                    }
                
                    @Override
                    public void onDelete(V1Service service, boolean deletedFinalStateUnknown) {
                        if (service.getMetadata() == null) {
                            return;
                        }
                        String serviceName = service.getMetadata().getName();
                        String namespace = service.getMetadata().getNamespace();
                        try {
                            unregisterService(namespace, serviceName);
                            Loggers.MAIN.info("delete service, namespace:" + namespace + " serviceName:" + serviceName);
                        } catch (Exception e) {
                            Loggers.MAIN.warn("delete service fail, message: " + e.getMessage()
                                    + " namespace:" + namespace + " serviceName:" + serviceName);
                        }
                    }
                });
    
        endpointInformer.addEventHandler(new ResourceEventHandler<V1Endpoints>() {
            @Override
            public void onAdd(V1Endpoints obj) {
                if (obj.getMetadata() == null) {
                    return;
                }
                String serviceName = obj.getMetadata().getName();
                String namespace = obj.getMetadata().getNamespace();
                Set<String> addIpSet = getIpFromEndpoints(obj);

                //TODO 因为需要指定namespace，这里servicelister需要重新new，是否可以优化,比如说作为单例的放到map中
                Lister<V1Service> serviceLister = new Lister<>(serviceInformer.getIndexer(), namespace);
                V1Service service = serviceLister.get(serviceName);
                List<V1ServicePort> servicePorts = service.getSpec().getPorts();
                try {
                    registerInstances(addIpSet, namespace, serviceName, servicePorts);
                    Loggers.MAIN.info("add instances, namespace:" + namespace + " serviceName: " + serviceName);
                } catch (NacosException e) {
                    Loggers.MAIN.warn("add instances fail, message:" + e.getMessage() + " namespace:" + namespace + ", serviceName: " + serviceName);
                }
            }

            @Override
            public void onUpdate(V1Endpoints oldObj, V1Endpoints newObj) {
                if (newObj.getMetadata() == null) {
                    return;
                }
                String serviceName = newObj.getMetadata().getName();
                String namespace = newObj.getMetadata().getNamespace();
                Lister<V1Service> serviceLister = new Lister<>(serviceInformer.getIndexer(), namespace);
                V1Service service = serviceLister.get(serviceName);
                List<V1ServicePort> servicePorts = service.getSpec().getPorts();
                try {
                    registerService(namespace, serviceName, servicePorts, false, endpointInformer);
                    Loggers.MAIN.info("update instances, namespace:" + namespace + " serviceName: " + serviceName);
                } catch (NacosException e) {
                    Loggers.MAIN.warn("update instances fail, message:" + e.getMessage() + " namespace:"
                            + namespace + ", serviceName: " + serviceName);
                }
            }

            @Override
            public void onDelete(V1Endpoints obj, boolean deletedFinalStateUnknown) {
                if (obj.getMetadata() == null) {
                    return;
                }
                String serviceName = obj.getMetadata().getName();
                String namespace = obj.getMetadata().getNamespace();
                Set<String> deleteIpSet = getIpFromEndpoints(obj);
                try {
                    List<? extends Instance> oldInstanceList = instanceOperatorClient.listAllInstances(namespace, serviceName);
                    unregisterInstances(deleteIpSet, namespace, serviceName, oldInstanceList);
                    Loggers.MAIN.info("delete instances, namespace:" + namespace + ", serviceName: " + serviceName);
                } catch (NacosException e) {
                    Loggers.MAIN.info("delete instances fail, namespace:" + namespace + ", serviceName: " + serviceName);
                }
            }
        });

        // Wait until the cache of each informer has been fully synced before proceeding.
        // This ensures that the local cache contains the latest and complete resource data.
        long timeout = 30000L;
        long startTime = System.currentTimeMillis();
        serviceInformer.run();
        while (!serviceInformer.hasSynced()) {
            if (System.currentTimeMillis() - startTime > timeout) {
                throw new RuntimeException("Informer serviceInformer sync timed out");
            }
            ThreadUtils.sleep(100L);
        }
        startTime = System.currentTimeMillis();
        endpointInformer.run();
        while (!endpointInformer.hasSynced()) {
            if (System.currentTimeMillis() - startTime > timeout) {
                throw new RuntimeException("Informer endpointInformer sync timed out");
            }
            ThreadUtils.sleep(100L);
        }
    }
    
    /**
     * create instance.
     *
     * @param ip instance ip
     * @param targetPort instance port
     * @param serviceName service name
     * @param port service port
     * @return instance
     */
    public Instance createInstance(String ip, int targetPort, String serviceName, int port) {
        Instance instance = new Instance();
        instance.setIp(ip);
        instance.setPort(targetPort);
        instance.setClusterName(serviceName);
        instance.setEphemeral(false);
        instance.setHealthy(true);
        instance.addMetadata("servicePort", String.valueOf(port));
        return instance;
    }
    
    /**
     * register service.
     *
     * @param namespace service namespace
     * @param serviceName service name
     * @param servicePorts service ports
     * @param portChanged port is changed or not
     * @throws NacosException nacos exception during registering
     */
    public void registerService(String namespace, String serviceName, List<V1ServicePort> servicePorts, boolean portChanged,
            SharedIndexInformer<V1Endpoints> endpointInformer) throws NacosException {
        //TODO defaultnamespace 常量
        
        Service service = Service.newService(namespace, Constants.DEFAULT_GROUP, serviceName, false);
        ServiceManager.getInstance().getSingleton(service);
        
        //NotifyCenter.publishEvent(new NamingTraceEvent.RegisterServiceTraceEvent(System.currentTimeMillis(),
        //        namespace, Constants.DEFAULT_GROUP, serviceName));
        
        Set<String> oldIpSet = new HashSet<>();
        List<? extends Instance> oldInstanceList = instanceOperatorClient.listAllInstances(namespace, serviceName);
        for (Instance instance:oldInstanceList) {
            oldIpSet.add(instance.getIp());
        }
        Lister<V1Endpoints> endpointLister = new Lister<>(endpointInformer.getIndexer(), namespace);
        V1Endpoints endpoints = endpointLister.get(serviceName);
        Set<String> newIpSet = getIpFromEndpoints(endpoints);
        
        //unregister deleted instance
        Set<String> deleteIpSet = new HashSet<>();
        deleteIpSet.addAll(oldIpSet);
        deleteIpSet.removeAll(newIpSet);
        unregisterInstances(deleteIpSet, namespace, serviceName, oldInstanceList);
        //register added instance
        Set<String> addIpSet = new HashSet<>();
        addIpSet.addAll(newIpSet);
        if (!portChanged) {
            addIpSet.removeAll(oldIpSet);
        }
        registerInstances(addIpSet, namespace, serviceName, servicePorts);
    }
    
    /**
     * unregister service.
     *
     * @param namespace service namespace
     * @param serviceName service name
     * @throws NacosException nacos exception during unregistering
     */
    public void unregisterService(String namespace, String serviceName) throws NacosException {
        List<? extends Instance> instancelist = instanceOperatorClient.listAllInstances(namespace, serviceName);
        for (Instance instance:instancelist) {
            instanceOperatorClient.removeInstance(namespace, serviceName, instance);
        }
        serviceOperatorV2.delete(namespace, serviceName);
    }
    
    /**
     * register instances.
     *
     * @param addIpSet add ip set
     * @param namespace service namespace
     * @param serviceName service name
     * @param servicePorts servie ports
     * @throws NacosException nacos exception during registering instances
     */
    public void registerInstances(Set<String> addIpSet, String namespace, String serviceName,
            List<V1ServicePort> servicePorts) throws NacosException {
        for (V1ServicePort servicePort:servicePorts) {
            int port = servicePort.getPort();
            if (!servicePort.getTargetPort().isInteger()) {
                continue;
            }
            int targetPort = servicePort.getTargetPort().getIntValue();
            for (String ip:addIpSet) {
                Instance instance = createInstance(ip, targetPort, serviceName, port);
                instanceOperatorClient.registerInstance(namespace, serviceName, instance);
            }
        }
        //TODO：register instance后是否需要发布事件
    }
    
    /**
     * unregister instances.
     *
     * @param deleteIpSet delete ip set
     * @param namespace service namespace
     * @param serviceName service name
     * @param oldInstanceList old instance list from nacos service
     */
    public void unregisterInstances(Set<String> deleteIpSet, String namespace, String serviceName,
            List<? extends Instance> oldInstanceList) throws NacosException {
        for (Instance instance:oldInstanceList) {
            if (deleteIpSet.contains(instance.getIp())) {
                instanceOperatorClient.removeInstance(namespace, serviceName, instance);
            }
        }
    }
    
    public Set<String> getIpFromEndpoints(V1Endpoints endpoints) {
        Set<String> ipSet = new HashSet<>();
        List<V1EndpointSubset> endpointSubsetList = endpoints.getSubsets();
        for (V1EndpointSubset endpointSubset:endpointSubsetList) {
            for (V1EndpointAddress endpointAddress:endpointSubset.getAddresses()) {
                ipSet.add(endpointAddress.getIp());
            }
        }
        return ipSet;
    }
    
    /**
     * compare oldServicePorts and newServicePorts.
     *
     * @param oldServicePorts old service ports list
     * @param newServicePorts new service ports list
     */
    public boolean compareServicePorts(List<V1ServicePort> oldServicePorts, List<V1ServicePort> newServicePorts) {
        if (oldServicePorts.size() != newServicePorts.size()) {
            return false;
        }
        return oldServicePorts.containsAll(newServicePorts) && newServicePorts.containsAll(oldServicePorts);
    }
    
    /**
     * use the Java API from an application outside a kubernetes cluster.
     * you should load a kubeConfig to generate apiClient instead of getting it from coreV1api.
     */
    public ApiClient getOutsideApiClient() throws IOException {
        String kubeConfigPath = k8sSyncConfig.getKubeConfig();

        // loading the out-of-cluster config, a kubeconfig from file-system
        ApiClient apiClient = ClientBuilder.kubeconfig(KubeConfig.loadKubeConfig(new FileReader(kubeConfigPath))).build();

        // set the global default api-client to the in-cluster one from above
        Configuration.setDefaultApiClient(apiClient);
        return apiClient;
    }
    
    /**
     * stop.
     */
    public void stop() {
        if (factory != null) {
            factory.stopAllRegisteredInformers();
        }
    }
}
