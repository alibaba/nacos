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
 * InstanceMetadataBatchOperationForm.
 * @author dongyafei
 * @date 2022/9/7
 */
public class InstanceMetadataBatchOperationForm implements Serializable {
    
    private static final long serialVersionUID = -1183494730406348717L;
    
    private String namespaceId;
    
    private String groupName;
    
    private String serviceName;
    
    private String consistencyType;
    
    private String instances;
    
    private String metadata;
    
    public InstanceMetadataBatchOperationForm() {
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
        if (StringUtils.isBlank(metadata)) {
            throw new NacosApiException(HttpStatus.BAD_REQUEST.value(), ErrorCode.PARAMETER_MISSING,
                    "Required parameter 'metadata' type String is not present");
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
        if (StringUtils.isBlank(consistencyType)) {
            consistencyType = "";
        }
        if (StringUtils.isBlank(instances)) {
            instances = "";
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
    
    public String getConsistencyType() {
        return consistencyType;
    }
    
    public void setConsistencyType(String consistencyType) {
        this.consistencyType = consistencyType;
    }
    
    public String getInstances() {
        return instances;
    }
    
    public void setInstances(String instances) {
        this.instances = instances;
    }
    
    public String getMetadata() {
        return metadata;
    }
    
    public void setMetadata(String metadata) {
        this.metadata = metadata;
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        InstanceMetadataBatchOperationForm that = (InstanceMetadataBatchOperationForm) o;
        return Objects.equals(namespaceId, that.namespaceId) && Objects.equals(groupName, that.groupName) && Objects
                .equals(serviceName, that.serviceName) && Objects.equals(consistencyType, that.consistencyType)
                && Objects.equals(instances, that.instances) && Objects.equals(metadata, that.metadata);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(namespaceId, groupName, serviceName, consistencyType, instances, metadata);
    }
    
    @Override
    public String toString() {
        return "InstanceMetadataBatchOperationForm{" + "namespaceId='" + namespaceId + '\'' + ", groupName='"
                + groupName + '\'' + ", serviceName='" + serviceName + '\'' + ", consistencyType='" + consistencyType
                + '\'' + ", instances='" + instances + '\'' + ", metadata='" + metadata + '\'' + '}';
    }
}
