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

package com.alibaba.nacos.naming.model.form;

import com.alibaba.nacos.api.common.Constants;
import com.alibaba.nacos.api.exception.api.NacosApiException;
import com.alibaba.nacos.api.model.v2.ErrorCode;
import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.naming.misc.UtilsAndCommons;
import org.springframework.http.HttpStatus;

import java.io.Serializable;
import java.util.Objects;

/**
 * InstanceForm.
 * @author dongyafei
 * @date 2022/9/7
 */
public class InstanceForm implements Serializable {
    
    private static final long serialVersionUID = -3760300561436525429L;
    
    private String namespaceId;
    
    private String groupName;
    
    private String serviceName;
    
    private String ip;
    
    private String clusterName;
    
    private Integer port;
    
    private Boolean healthy;
    
    private Double weight;
    
    private Boolean enabled;
    
    private String metadata;
    
    private Boolean ephemeral;
    
    public InstanceForm() {
    }
    
    /**
     * check param.
     *
     * @throws NacosApiException NacosApiException
     */
    public void validate() throws NacosApiException {
        fillDefaultValue();
        if (StringUtils.isBlank(serviceName)) {
            throw new NacosApiException(HttpStatus.BAD_REQUEST.value(), ErrorCode.PARAMETER_MISSING,
                    "Required parameter 'serviceName' type String is not present");
        }
        if (StringUtils.isBlank(ip)) {
            throw new NacosApiException(HttpStatus.BAD_REQUEST.value(), ErrorCode.PARAMETER_MISSING,
                    "Required parameter 'ip' type String is not present");
        }
        if (port == null) {
            throw new NacosApiException(HttpStatus.BAD_REQUEST.value(), ErrorCode.PARAMETER_MISSING,
                    "Required parameter 'port' type Integer is not present");
        }
    }
    
    /**
     * fill default value.
     */
    public void fillDefaultValue() {
        if (StringUtils.isBlank(namespaceId)) {
            namespaceId = Constants.DEFAULT_NAMESPACE_ID;
        }
        if (StringUtils.isBlank(groupName)) {
            groupName = Constants.DEFAULT_GROUP;
        }
        if (StringUtils.isBlank(clusterName)) {
            clusterName = UtilsAndCommons.DEFAULT_CLUSTER_NAME;
        }
        if (healthy == null) {
            healthy = true;
        }
        if (weight == null) {
            weight = 1.0;
        }
        if (enabled == null) {
            enabled = true;
        }
    }
    
    public String getNamespaceId() {
        return namespaceId;
    }
    
    public void setNamespaceId(String namespaceId) {
        this.namespaceId = namespaceId;
    }
    
    public String getGroupName() {
        return groupName;
    }
    
    public void setGroupName(String groupName) {
        this.groupName = groupName;
    }
    
    public String getServiceName() {
        return serviceName;
    }
    
    public void setServiceName(String serviceName) {
        this.serviceName = serviceName;
    }
    
    public String getIp() {
        return ip;
    }
    
    public void setIp(String ip) {
        this.ip = ip;
    }
    
    public String getClusterName() {
        return clusterName;
    }
    
    public void setClusterName(String clusterName) {
        this.clusterName = clusterName;
    }
    
    public Integer getPort() {
        return port;
    }
    
    public void setPort(Integer port) {
        this.port = port;
    }
    
    public Boolean getHealthy() {
        return healthy;
    }
    
    public void setHealthy(Boolean healthy) {
        this.healthy = healthy;
    }
    
    public Double getWeight() {
        return weight;
    }
    
    public void setWeight(Double weight) {
        this.weight = weight;
    }
    
    public Boolean getEnabled() {
        return enabled;
    }
    
    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }
    
    public String getMetadata() {
        return metadata;
    }
    
    public void setMetadata(String metadata) {
        this.metadata = metadata;
    }
    
    public Boolean getEphemeral() {
        return ephemeral;
    }
    
    public void setEphemeral(Boolean ephemeral) {
        this.ephemeral = ephemeral;
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        InstanceForm that = (InstanceForm) o;
        return Objects.equals(namespaceId, that.namespaceId) && Objects.equals(groupName, that.groupName) && Objects
                .equals(serviceName, that.serviceName) && Objects.equals(ip, that.ip) && Objects
                .equals(clusterName, that.clusterName) && Objects.equals(port, that.port) && Objects
                .equals(healthy, that.healthy) && Objects.equals(weight, that.weight) && Objects
                .equals(enabled, that.enabled) && Objects.equals(metadata, that.metadata) && Objects
                .equals(ephemeral, that.ephemeral);
    }
    
    @Override
    public int hashCode() {
        return Objects
                .hash(namespaceId, groupName, serviceName, ip, clusterName, port, healthy, weight, enabled, metadata,
                        ephemeral);
    }
    
    @Override
    public String toString() {
        return "InstanceForm{" + "namespaceId='" + namespaceId + '\'' + ", groupName='" + groupName + '\''
                + ", serviceName='" + serviceName + '\'' + ", ip='" + ip + '\'' + ", clusterName='" + clusterName + '\''
                + ", port=" + port + ", healthy=" + healthy + ", weight=" + weight + ", enabled=" + enabled
                + ", metadata='" + metadata + '\'' + ", ephemeral=" + ephemeral + '}';
    }
}
