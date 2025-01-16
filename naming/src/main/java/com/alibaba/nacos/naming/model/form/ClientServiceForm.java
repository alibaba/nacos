/*
 * Copyright 1999-2025 Alibaba Group Holding Ltd.
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
import com.alibaba.nacos.core.model.form.NacosForm;
import org.springframework.http.HttpStatus;

import java.io.Serial;
import java.util.Objects;

/**
 * Client Info Service From.
 *
 * @author xiweng.yy
 */
public class ClientServiceForm implements NacosForm {
    
    @Serial
    private static final long serialVersionUID = 7382718107541271520L;
    
    private String namespaceId;
    
    private String groupName;
    
    private String serviceName;
    
    private String ip;
    
    private Integer port;
    
    private Boolean ephemeral;
    
    @Override
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
        if (null == ephemeral) {
            ephemeral = Boolean.TRUE;
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
    
    public Integer getPort() {
        return port;
    }
    
    public void setPort(Integer port) {
        this.port = port;
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
        ClientServiceForm that = (ClientServiceForm) o;
        return Objects.equals(namespaceId, that.namespaceId) && Objects.equals(groupName, that.groupName)
                && Objects.equals(serviceName, that.serviceName) && Objects.equals(ip, that.ip) && Objects.equals(port,
                that.port) && Objects.equals(ephemeral, that.ephemeral);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(namespaceId, groupName, serviceName, ip, port, ephemeral);
    }
}
