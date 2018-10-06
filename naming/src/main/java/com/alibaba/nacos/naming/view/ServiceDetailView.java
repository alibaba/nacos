package com.alibaba.nacos.naming.view;

import com.alibaba.fastjson.JSON;
import com.alibaba.nacos.api.naming.pojo.Cluster;
import com.alibaba.nacos.api.naming.pojo.Instance;
import com.alibaba.nacos.api.naming.pojo.Service;

import java.util.List;
import java.util.Map;

/**
 * @author dungu.zpf
 */
public class ServiceDetailView {

    private Service service;

    private List<Cluster> clusters;

    private Map<String, List<Instance>> instances;

    public Service getService() {
        return service;
    }

    public void setService(Service service) {
        this.service = service;
    }

    public List<Cluster> getClusters() {
        return clusters;
    }

    public void setClusters(List<Cluster> clusters) {
        this.clusters = clusters;
    }

    public Map<String, List<Instance>> getInstances() {
        return instances;
    }

    public void setInstances(Map<String, List<Instance>> instances) {
        this.instances = instances;
    }

    @Override
    public String toString() {
        return JSON.toJSONString(this);
    }
}
