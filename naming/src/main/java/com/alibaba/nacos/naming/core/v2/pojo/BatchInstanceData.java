package com.alibaba.nacos.naming.core.v2.pojo;

import java.util.List;

/**
 * batch instance data.
 * @ClassName: BatchInstanceData.
 * @Author: ChenHao26
 * @Date: 2022/6/24 16:25
 */
public class BatchInstanceData {
    
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
