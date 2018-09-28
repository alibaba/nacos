package com.alibaba.nacos.naming.view;

import com.alibaba.fastjson.JSON;

/**
 * @author dungu.zpf
 */
public class ServiceView {

    private String name;
    private int clusterCount;
    private int ipCount;
    private String status;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getClusterCount() {
        return clusterCount;
    }

    public void setClusterCount(int clusterCount) {
        this.clusterCount = clusterCount;
    }

    public int getIpCount() {
        return ipCount;
    }

    public void setIpCount(int ipCount) {
        this.ipCount = ipCount;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    @Override
    public String toString() {
        return JSON.toJSONString(this);
    }
}
