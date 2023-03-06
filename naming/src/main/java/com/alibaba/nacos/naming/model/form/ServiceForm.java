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
import org.springframework.http.HttpStatus;

import java.io.Serializable;
import java.util.Objects;

/**
 * ServiceForm.
 * @author dongyafei
 * @date 2022/9/7
 */
public class ServiceForm implements Serializable {
    
    private static final long serialVersionUID = -4905650083916616115L;
    
    private String namespaceId;
    
    private String serviceName;
    
    private String groupName;
    
    private Boolean ephemeral;
    
    private Float protectThreshold;
    
    private String metadata;
    
    private String selector;
    
    public ServiceForm() {
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
        if (ephemeral == null) {
            ephemeral = false;
        }
        if (protectThreshold == null) {
            protectThreshold = 0.0F;
        }
        if (StringUtils.isBlank(metadata)) {
            metadata = StringUtils.EMPTY;
        }
        if (StringUtils.isBlank(selector)) {
            selector = StringUtils.EMPTY;
        }
    }
    
    public String getNamespaceId() {
        return namespaceId;
    }
    
    public void setNamespaceId(String namespaceId) {
        this.namespaceId = namespaceId;
    }
    
    public String getServiceName() {
        return serviceName;
    }
    
    public void setServiceName(String serviceName) {
        this.serviceName = serviceName;
    }
    
    public String getGroupName() {
        return groupName;
    }
    
    public void setGroupName(String groupName) {
        this.groupName = groupName;
    }
    
    public Boolean getEphemeral() {
        return ephemeral;
    }
    
    public void setEphemeral(Boolean ephemeral) {
        this.ephemeral = ephemeral;
    }
    
    public Float getProtectThreshold() {
        return protectThreshold;
    }
    
    public void setProtectThreshold(Float protectThreshold) {
        this.protectThreshold = protectThreshold;
    }
    
    public String getMetadata() {
        return metadata;
    }
    
    public void setMetadata(String metadata) {
        this.metadata = metadata;
    }
    
    public String getSelector() {
        return selector;
    }
    
    public void setSelector(String selector) {
        this.selector = selector;
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        ServiceForm that = (ServiceForm) o;
        return Objects.equals(namespaceId, that.namespaceId) && Objects.equals(serviceName, that.serviceName) && Objects
                .equals(groupName, that.groupName) && Objects.equals(ephemeral, that.ephemeral) && Objects
                .equals(protectThreshold, that.protectThreshold) && Objects.equals(metadata, that.metadata) && Objects
                .equals(selector, that.selector);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(namespaceId, serviceName, groupName, ephemeral, protectThreshold, metadata, selector);
    }
    
    @Override
    public String toString() {
        return "ServiceForm{" + "namespaceId='" + namespaceId + '\'' + ", serviceName='" + serviceName + '\''
                + ", groupName='" + groupName + '\'' + ", ephemeral=" + ephemeral + ", protectThreshold="
                + protectThreshold + ", metadata='" + metadata + '\'' + ", selector='" + selector + '\'' + '}';
    }
}
