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

package com.alibaba.nacos.naming.core.v2.pojo;

import java.io.Serializable;
import java.util.List;

/**
 * batch instance data.
 * @ClassName: BatchInstanceData.
 * @author : ChenHao26
 * @Date: 2022/6/24 16:25
 */
public class BatchInstanceData implements Serializable {

    private static final long serialVersionUID = 7845847904043098494L;
    
    private List<String> namespaces;
    
    private List<String> groupNames;
    
    private List<String> serviceNames;
    
    private List<BatchInstancePublishInfo> batchInstancePublishInfos;
    
    public BatchInstanceData() {
    }
    
    public BatchInstanceData(List<String> namespaces, List<String> groupNames, List<String> serviceNames,
            List<BatchInstancePublishInfo> batchInstancePublishInfos) {
        this.namespaces = namespaces;
        this.groupNames = groupNames;
        this.serviceNames = serviceNames;
        this.batchInstancePublishInfos = batchInstancePublishInfos;
    }
    
    public List<String> getNamespaces() {
        return namespaces;
    }
    
    public void setNamespaces(List<String> namespaces) {
        this.namespaces = namespaces;
    }
    
    public List<String> getGroupNames() {
        return groupNames;
    }
    
    public void setGroupNames(List<String> groupNames) {
        this.groupNames = groupNames;
    }
    
    public List<String> getServiceNames() {
        return serviceNames;
    }
    
    public void setServiceNames(List<String> serviceNames) {
        this.serviceNames = serviceNames;
    }
    
    public List<BatchInstancePublishInfo> getBatchInstancePublishInfos() {
        return batchInstancePublishInfos;
    }
    
    public void setBatchInstancePublishInfos(List<BatchInstancePublishInfo> batchInstancePublishInfos) {
        this.batchInstancePublishInfos = batchInstancePublishInfos;
    }
}
