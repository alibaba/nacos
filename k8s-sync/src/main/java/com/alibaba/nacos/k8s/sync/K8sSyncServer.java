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
import com.alibaba.nacos.core.listener.StartingApplicationListener;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

@Component
public class K8sSyncServer {
    
    //@Autowired
    //private K8sSyncConfig k8sSyncConfig;
    
    @Autowired
    private ServiceOperatorV2Impl serviceOperatorV2;
    
    @Autowired
    private InstanceOperatorClientImpl instanceOperatorClient;
 
    private static final Logger LOGGER = LoggerFactory.getLogger(StartingApplicationListener.class);
    
    public static void main(String[] args) throws Exception {
        K8sSyncServer k8sSyncServer = new K8sSyncServer();
        k8sSyncServer.startInformer();
    }
    
    /**
     * Start.
     *
     * @throws IOException io exception
     */
    @SuppressWarnings("checkstyle:CommentsIndentation")
    @PostConstruct
    public void start() throws Exception {
//        TODO:加上全局开关
//        if (!k8sSyncConfig.isK8sSyncEnable()) {
////            Loggers.MAIN.info("The Nacos k8s-sync is disabled.");
//            return;
//        }
        startInformer();
        LOGGER.info("Starting Nacos k8s-sync ...");
        Runtime.getRuntime().addShutdownHook(new Thread() {
            @Override
            public void run() {
                System.out.println("Stopping Nacos k8s-sync...");
//                K8sSyncServer.this.stop();
                System.out.println("Nacos k8s-sync stopped...");
            }
        });
    }
    
    @SuppressWarnings({"checkstyle:MissingJavadocMethod", "checkstyle:CommentsIndentation"})
    public void startInformer() throws Exception {
        // file path to your KubeConfig
        String kubeConfigPath = System.getenv("HOME") + "/.kube/config";
    
        // loading the out-of-cluster config, a kubeconfig from file-system
        ApiClient apiClient = ClientBuilder.kubeconfig(KubeConfig.loadKubeConfig(new FileReader(kubeConfigPath))).build();
    
        // set the global default api-client to the in-cluster one from above
        Configuration.setDefaultApiClient(apiClient);
        //以上为本地调试
        
        CoreV1Api coreV1Api = new CoreV1Api();
        OkHttpClient httpClient =
                apiClient.getHttpClient().newBuilder().build();
        apiClient.setHttpClient(httpClient);
    
        SharedInformerFactory factory = new SharedInformerFactory(apiClient);
        SharedIndexInformer<V1Service> serviceInformer =
                factory.sharedIndexInformerFor(
                        (CallGeneratorParams params) -> {
                            return coreV1Api.listServiceForAllNamespacesCall(
                                    null,
                                    null,
                                    null,
                                    null,
                                    null,
                                    null,
                                    params.resourceVersion,
                                    null,
                                    params.timeoutSeconds,
                                    params.watch,
                                    null);
                        },
                        V1Service.class,
                        V1ServiceList.class);
        
        SharedIndexInformer<V1Endpoints> endpointInformer =
                factory.sharedIndexInformerFor(
                        (CallGeneratorParams params) -> {
                            return coreV1Api.listEndpointsForAllNamespacesCall(
                                    null,
                                    null,
                                    null,
                                    null,
                                    null,
                                    null,
                                    params.resourceVersion,
                                    null,
                                    params.timeoutSeconds,
                                    params.watch,
                                    null);
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
                            LOGGER.info("add service, namespace:" + namespace + " serviceName: " + serviceName);
                        } catch (Exception e) {
                            LOGGER.warn("add service fail, message:" + e.getMessage() + " namespace:" + namespace + " serviceName: " + serviceName);
                        }
                    }
                
                    @Override
                    public void onUpdate(V1Service oldService, V1Service newService) {
                        if (oldService.getMetadata() == null || oldService.getSpec() == null
                                || newService.getMetadata() == null || newService.getSpec() == null) {
                            return;
                        }
                        String oldServiceName = oldService.getMetadata().getName();
                        String oldNamespace = oldService.getMetadata().getNamespace();
                        List<V1ServicePort> oldServicePorts = oldService.getSpec().getPorts();
                        String newServiceName = newService.getMetadata().getName();
                        String newNamespace = newService.getMetadata().getNamespace();
                        List<V1ServicePort> newServicePorts = newService.getSpec().getPorts();
                        boolean portChanged = compareServicePorts(oldServicePorts, newServicePorts);
                        try {
                            if (newServiceName != null && newNamespace != null
                                    && newServiceName.equals(oldServiceName) && newNamespace.equals(oldNamespace)) {
                            } else {
                                unregisterService(oldNamespace, oldServiceName);
                                registerService(newNamespace, newServiceName, newServicePorts, portChange, endpointInformer);
                            }
                            LOGGER.info("update service, oldNamespace:" + oldNamespace + " oldServiceName: "
                                    + oldServiceName + " newNamespace:" + newNamespace + " newServiceName: "
                                    + newServiceName);
                            registerService(newNamespace, newServiceName, newServicePorts, portChanged, endpointInformer);
                        } catch (Exception e) {
                            LOGGER.warn("update service fail, message: " + e.getMessage() + " oldNamespace:" + oldNamespace
                                    + " oldServiceName: " + oldServiceName + " newNamespace:" + newNamespace
                                    + " newServiceName: " + newServiceName);
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
                            LOGGER.info("delete service, namespace:" + namespace + " serviceName:" + serviceName);
                        } catch (Exception e) {
                            LOGGER.warn("delete service fail, message: " + e.getMessage()
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
                    LOGGER.info("add instances, namespace:" + namespace + " serviceName: " + serviceName);
                } catch (NacosException e) {
                    LOGGER.warn("add instances fail, message:" + e.getMessage() + " namespace:" + namespace + ", serviceName: " + serviceName);
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
                    LOGGER.info("update instances, namespace:" + namespace + " serviceName: " + serviceName);
                } catch (NacosException e) {
                    LOGGER.warn("update instances fail, message:" + e.getMessage() + " namespace:" + namespace + ", serviceName: " + serviceName);
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
                    LOGGER.info("delete instances, namespace:" + namespace + ", serviceName: " + serviceName);
                } catch (NacosException e) {
                    LOGGER.info("delete instances fail, namespace:" + namespace + ", serviceName: " + serviceName);
                }
            }
        });
        factory.startAllRegisteredInformers();
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
                break;
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
     * @throws NacosException nacos exception during unregistering instances
     */
    public void unregisterInstances(Set<String> deleteIpSet, String namespace, String serviceName,
            List<? extends Instance> oldInstanceList) {
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
}
