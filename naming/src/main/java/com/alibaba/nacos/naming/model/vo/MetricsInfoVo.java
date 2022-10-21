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

package com.alibaba.nacos.naming.model.vo;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.io.Serializable;

/**
 * Metrics Information.
 * @author dongyafei
 * @date 2022/9/15
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class MetricsInfoVo implements Serializable {
    
    private static final long serialVersionUID = -5064297490423743871L;
    
    private String status;
    
    private Integer serviceCount;
    
    private Integer instanceCount;
    
    private Integer subscribeCount;
    
    private Integer clientCount;
    
    private Integer connectionBasedClientCount;
    
    private Integer ephemeralIpPortClientCount;
    
    private Integer persistentIpPortClientCount;
    
    private Integer responsibleClientCount;
    
    private Float cpu;

    private Float load;

    private Float mem;
    
    public MetricsInfoVo() {
    }
    
    public String getStatus() {
        return status;
    }
    
    public void setStatus(String status) {
        this.status = status;
    }
    
    public Integer getServiceCount() {
        return serviceCount;
    }
    
    public void setServiceCount(Integer serviceCount) {
        this.serviceCount = serviceCount;
    }
    
    public Integer getInstanceCount() {
        return instanceCount;
    }
    
    public void setInstanceCount(Integer instanceCount) {
        this.instanceCount = instanceCount;
    }
    
    public Integer getSubscribeCount() {
        return subscribeCount;
    }
    
    public void setSubscribeCount(Integer subscribeCount) {
        this.subscribeCount = subscribeCount;
    }
    
    public Integer getClientCount() {
        return clientCount;
    }
    
    public void setClientCount(Integer clientCount) {
        this.clientCount = clientCount;
    }
    
    public Integer getConnectionBasedClientCount() {
        return connectionBasedClientCount;
    }
    
    public void setConnectionBasedClientCount(Integer connectionBasedClientCount) {
        this.connectionBasedClientCount = connectionBasedClientCount;
    }
    
    public Integer getEphemeralIpPortClientCount() {
        return ephemeralIpPortClientCount;
    }
    
    public void setEphemeralIpPortClientCount(Integer ephemeralIpPortClientCount) {
        this.ephemeralIpPortClientCount = ephemeralIpPortClientCount;
    }
    
    public Integer getPersistentIpPortClientCount() {
        return persistentIpPortClientCount;
    }
    
    public void setPersistentIpPortClientCount(Integer persistentIpPortClientCount) {
        this.persistentIpPortClientCount = persistentIpPortClientCount;
    }
    
    public Integer getResponsibleClientCount() {
        return responsibleClientCount;
    }
    
    public void setResponsibleClientCount(Integer responsibleClientCount) {
        this.responsibleClientCount = responsibleClientCount;
    }
    
    public Float getCpu() {
        return cpu;
    }
    
    public void setCpu(Float cpu) {
        this.cpu = cpu;
    }
    
    public Float getLoad() {
        return load;
    }
    
    public void setLoad(Float load) {
        this.load = load;
    }
    
    public Float getMem() {
        return mem;
    }
    
    public void setMem(Float mem) {
        this.mem = mem;
    }
}
