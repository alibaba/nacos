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

import java.io.Serializable;
import java.util.Map;

/**
 * Service detial info.
 *
 * @author caogu.wyp
 * @version $Id: ServiceDetailInfo.java, v 0.1 2018-09-17 上午10:47 caogu.wyp Exp $$
 */
public class ServiceDetailInfo implements Serializable {
    
    private String serviceName;
    
    private String groupName;
    
    private Map<String, ClusterInfo> clusterMap;
    
    private Map<String, String> metadata;
    
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
}
