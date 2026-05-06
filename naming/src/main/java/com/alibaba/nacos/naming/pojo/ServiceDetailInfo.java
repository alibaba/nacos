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

package com.alibaba.nacos.naming.pojo;

import com.alibaba.nacos.api.selector.Selector;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

/**
 * Service detial info.
 *
 * @author caogu.wyp
 * @version $Id: ServiceDetailInfo.java, v 0.1 2018-09-17 上午10:47 caogu.wyp Exp $$
 *
 * @deprecated use {@link com.alibaba.nacos.api.naming.pojo.maintainer.ServiceDetailInfo} replaced
 */
@Deprecated
public class ServiceDetailInfo implements Serializable {

    private static final long serialVersionUID = 6351606608785841722L;

    private String namespace;
    
    private String serviceName;
    
    private String groupName;
    
    private Map<String, ClusterInfo> clusterMap;
    
    private Map<String, String> metadata;
    
    private float protectThreshold;
    
    private Selector selector;
    
    private Boolean ephemeral;
    
    /**
     * Getter method for property <tt>serviceName</tt>.
     *
     * @return property value of serviceName
     */
    public String getServiceName() {
        return serviceName;
    }
    
    /**
     * Setter method for property <tt>serviceName </tt>.
     *
     * @param serviceName value to be assigned to property serviceName
     */
    public void setServiceName(String serviceName) {
        this.serviceName = serviceName;
    }
    
    public String getGroupName() {
        return groupName;
    }
    
    public void setGroupName(String groupName) {
        this.groupName = groupName;
    }
    
    /**
     * Getter method for property <tt>clusterMap</tt>.
     *
     * @return property value of clusterMap
     */
    public Map<String, ClusterInfo> getClusterMap() {
        return clusterMap;
    }
    
    /**
     * Setter method for property <tt>clusterMap </tt>.
     *
     * @param clusterMap value to be assigned to property clusterMap
     */
    public void setClusterMap(Map<String, ClusterInfo> clusterMap) {
        this.clusterMap = clusterMap;
    }
    
    /**
     * Getter method for property <tt>metadata</tt>.
     *
     * @return property value of metadata
     */
    public Map<String, String> getMetadata() {
        return metadata;
    }
    
    /**
     * Setter method for property <tt>metadata </tt>.
     *
     * @param metadata value to be assigned to property metadata
     */
    public void setMetadata(Map<String, String> metadata) {
        this.metadata = metadata;
    }
    
    public String getNamespace() {
        return namespace;
    }
    
    public void setNamespace(String namespace) {
        this.namespace = namespace;
    }
    
    public float getProtectThreshold() {
        return protectThreshold;
    }
    
    public void setProtectThreshold(float protectThreshold) {
        this.protectThreshold = protectThreshold;
    }
    
    public Selector getSelector() {
        return selector;
    }
    
    public void setSelector(Selector selector) {
        this.selector = selector;
    }
    
    public Boolean isEphemeral() {
        return ephemeral;
    }
    
    public void setEphemeral(boolean ephemeral) {
        this.ephemeral = ephemeral;
    }
    
    /**
     * For some old apis, from new {@link com.alibaba.nacos.api.naming.pojo.maintainer.ServiceDetailInfo} to this deprecated one.
     *
     * @param newServiceDetailInfo new {@link com.alibaba.nacos.api.naming.pojo.maintainer.ServiceDetailInfo}
     * @return this deprecated one
     */
    public static ServiceDetailInfo from(com.alibaba.nacos.api.naming.pojo.maintainer.ServiceDetailInfo newServiceDetailInfo) {
        ServiceDetailInfo serviceDetailInfo = new ServiceDetailInfo();
        serviceDetailInfo.setNamespace(newServiceDetailInfo.getNamespaceId());
        serviceDetailInfo.setServiceName(newServiceDetailInfo.getServiceName());
        serviceDetailInfo.setGroupName(newServiceDetailInfo.getGroupName());
        serviceDetailInfo.setMetadata(newServiceDetailInfo.getMetadata());
        serviceDetailInfo.setProtectThreshold(newServiceDetailInfo.getProtectThreshold());
        serviceDetailInfo.setSelector(newServiceDetailInfo.getSelector());
        serviceDetailInfo.setEphemeral(newServiceDetailInfo.isEphemeral());
        Map<String, ClusterInfo> clusterInfoMap = new HashMap<>(newServiceDetailInfo.getClusterMap().size());
        for (Map.Entry<String, com.alibaba.nacos.api.naming.pojo.maintainer.ClusterInfo> entry : newServiceDetailInfo.getClusterMap()
                .entrySet()) {
            clusterInfoMap.put(entry.getKey(), ClusterInfo.from(entry.getValue()));
        }
        serviceDetailInfo.setClusterMap(clusterInfoMap);
        return serviceDetailInfo;
    }
}
