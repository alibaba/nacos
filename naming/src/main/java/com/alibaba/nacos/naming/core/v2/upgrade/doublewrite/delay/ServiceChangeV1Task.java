/*
 * Copyright 1999-2020 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.naming.core.v2.upgrade.doublewrite.delay;

import com.alibaba.nacos.api.naming.pojo.ServiceInfo;
import com.alibaba.nacos.api.naming.utils.NamingUtils;
import com.alibaba.nacos.common.task.AbstractDelayTask;
import com.alibaba.nacos.common.task.NacosTask;
import com.alibaba.nacos.common.task.NacosTaskProcessor;
import com.alibaba.nacos.naming.core.Cluster;
import com.alibaba.nacos.naming.core.Instance;
import com.alibaba.nacos.naming.core.Service;
import com.alibaba.nacos.naming.core.ServiceManager;
import com.alibaba.nacos.naming.core.v2.client.impl.IpPortBasedClient;
import com.alibaba.nacos.naming.core.v2.index.ServiceStorage;
import com.alibaba.nacos.naming.core.v2.metadata.ClusterMetadata;
import com.alibaba.nacos.naming.core.v2.metadata.ServiceMetadata;
import com.alibaba.nacos.naming.core.v2.upgrade.doublewrite.execute.DoubleWriteInstanceChangeToV2Task;
import com.alibaba.nacos.naming.core.v2.upgrade.doublewrite.execute.DoubleWriteMetadataChangeToV2Task;
import com.alibaba.nacos.naming.misc.Loggers;
import com.alibaba.nacos.naming.misc.NamingExecuteTaskDispatcher;
import com.alibaba.nacos.sys.utils.ApplicationUtils;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Double write delay task during upgrading.
 *
 * @author xiweng.yy
 */
public class ServiceChangeV1Task extends AbstractDelayTask {
    
    private final String namespace;
    
    private final String serviceName;
    
    private final boolean ephemeral;
    
    public ServiceChangeV1Task(String namespace, String serviceName, boolean ephemeral) {
        this.namespace = namespace;
        this.serviceName = serviceName;
        this.ephemeral = ephemeral;
        setLastProcessTime(System.currentTimeMillis());
        setTaskInterval(1000L);
    }
    
    @Override
    public void merge(AbstractDelayTask task) {
    }
    
    public String getNamespace() {
        return namespace;
    }
    
    public String getServiceName() {
        return serviceName;
    }
    
    public boolean isEphemeral() {
        return ephemeral;
    }
    
    public static String getKey(String namespace, String serviceName, boolean ephemeral) {
        return "v1:" + namespace + "_" + serviceName + "_" + ephemeral;
    }
    
    public static class ServiceChangeV1TaskProcessor implements NacosTaskProcessor {
        
        @Override
        public boolean process(NacosTask task) {
            ServiceChangeV1Task serviceTask = (ServiceChangeV1Task) task;
            Loggers.SRV_LOG.info("double write for service {}", serviceTask.getServiceName());
            ServiceManager serviceManager = ApplicationUtils.getBean(ServiceManager.class);
            Service service = serviceManager.getService(serviceTask.getNamespace(), serviceTask.getServiceName());
            ServiceMetadata serviceMetadata = parseServiceMetadata(service, serviceTask.isEphemeral());
            DoubleWriteMetadataChangeToV2Task metadataTask = new DoubleWriteMetadataChangeToV2Task(
                    service.getNamespaceId(), service.getName(), serviceTask.isEphemeral(), serviceMetadata);
            NamingExecuteTaskDispatcher.getInstance().dispatchAndExecuteTask(service.getName(), metadataTask);
            ServiceStorage serviceStorage = ApplicationUtils.getBean(ServiceStorage.class);
            ServiceInfo serviceInfo = serviceStorage.getPushData(transfer(service, serviceTask.isEphemeral()));
            List<Instance> newInstance = service.allIPs(serviceTask.isEphemeral());
            Set<String> instances = new HashSet<>();
            for (Instance each : newInstance) {
                instances.add(each.toIpAddr());
                DoubleWriteInstanceChangeToV2Task instanceTask = new DoubleWriteInstanceChangeToV2Task(
                        service.getNamespaceId(), service.getName(), each, true);
                NamingExecuteTaskDispatcher.getInstance().dispatchAndExecuteTask(
                        IpPortBasedClient.getClientId(each.toIpAddr(), serviceTask.isEphemeral()), instanceTask);
            }
            List<com.alibaba.nacos.api.naming.pojo.Instance> oldInstance = serviceInfo.getHosts();
            Loggers.SRV_LOG.info("[TMP_DEBUG] oldInstances {} \n new Instances {}", oldInstance, newInstance);
            for (com.alibaba.nacos.api.naming.pojo.Instance each : oldInstance) {
                if (!instances.contains(each.toInetAddr())) {
                    DoubleWriteInstanceChangeToV2Task instanceTask = new DoubleWriteInstanceChangeToV2Task(
                            service.getNamespaceId(), service.getName(), each, false);
                    NamingExecuteTaskDispatcher.getInstance().dispatchAndExecuteTask(
                            IpPortBasedClient.getClientId(each.toInetAddr(), serviceTask.isEphemeral()), instanceTask);
                }
            }
            return true;
        }
        
        private com.alibaba.nacos.naming.core.v2.pojo.Service transfer(Service service, boolean ephemeral) {
            return com.alibaba.nacos.naming.core.v2.pojo.Service
                    .newService(service.getNamespaceId(), service.getGroupName(),
                            NamingUtils.getServiceName(service.getName()), ephemeral);
        }
        
        private ServiceMetadata parseServiceMetadata(Service service, boolean ephemeral) {
            ServiceMetadata result = new ServiceMetadata();
            result.setEphemeral(ephemeral);
            result.setProtectThreshold(service.getProtectThreshold());
            result.setSelector(service.getSelector());
            result.setExtendData(service.getMetadata());
            for (Map.Entry<String, Cluster> entry : service.getClusterMap().entrySet()) {
                result.getClusters().put(entry.getKey(), parseClusterMetadata(entry.getValue()));
            }
            return result;
        }
        
        private ClusterMetadata parseClusterMetadata(Cluster cluster) {
            ClusterMetadata result = new ClusterMetadata();
            result.setHealthyCheckPort(cluster.getDefCkport());
            result.setUseInstancePortForCheck(cluster.isUseIPPort4Check());
            result.setExtendData(cluster.getMetadata());
            result.setHealthChecker(cluster.getHealthChecker());
            result.setHealthyCheckType(cluster.getHealthChecker().getType());
            return result;
        }
    }
}
