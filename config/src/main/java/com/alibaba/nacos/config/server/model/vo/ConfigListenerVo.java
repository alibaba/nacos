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

package com.alibaba.nacos.config.server.model.vo;

import com.alibaba.nacos.api.exception.api.NacosApiBadRequestException;
import com.alibaba.nacos.api.model.v2.ErrorCode;
import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.config.server.constant.Constants;

import java.io.Serializable;
import java.util.Objects;

/**
 * Config Listener.
 * @author dongyafei
 * @date 2022/7/25
 */
public class ConfigListenerVo implements Serializable {
    
    private static final long serialVersionUID = 5825057639530885477L;
    
    private String dataId;
    
    private String group;
    
    private String contentMd5;
    
    private String tenant;
    
    public ConfigListenerVo() {
    }
    
    public ConfigListenerVo(String dataId, String group, String contentMd5, String tenant) {
        this.dataId = dataId;
        this.group = group;
        this.contentMd5 = contentMd5;
        this.tenant = tenant;
    }
    
    public String getDataId() {
        return dataId;
    }
    
    public void setDataId(String dataId) {
        this.dataId = dataId;
    }
    
    public String getGroup() {
        return group;
    }
    
    public void setGroup(String group) {
        this.group = group;
    }
    
    public String getContentMd5() {
        return contentMd5;
    }
    
    public void setContentMd5(String contentMd5) {
        this.contentMd5 = contentMd5;
    }
    
    public String getTenant() {
        return tenant;
    }
    
    public void setTenant(String tenant) {
        this.tenant = tenant;
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        ConfigListenerVo that = (ConfigListenerVo) o;
        return Objects.equals(dataId, that.dataId) && Objects.equals(group, that.group) && Objects
                .equals(contentMd5, that.contentMd5) && Objects.equals(tenant, that.tenant);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(dataId, group, contentMd5, tenant);
    }
    
    @Override
    public String toString() {
        return "ConfigListenerVo{" + "dataId='" + dataId + '\'' + ", group='" + group + '\'' + ", contentMd5='"
                + contentMd5 + '\'' + ", tenant='" + tenant + '\'' + '}';
    }
    
    /**
     * Validate.
     * @throws NacosApiBadRequestException NacosApiBadRequestException.
     */
    public void validate() throws NacosApiBadRequestException {
        if (StringUtils.isBlank(dataId)) {
            throw new NacosApiBadRequestException(ErrorCode.PARAMETER_MISSING, "Required parameter 'dataId' type String is not present");
        } else if (StringUtils.isBlank(group)) {
            throw new NacosApiBadRequestException(ErrorCode.PARAMETER_MISSING, "Required parameter 'group' type String is not present");
        } else if (null == contentMd5) {
            throw new NacosApiBadRequestException(ErrorCode.PARAMETER_MISSING, "Required parameter 'contentMd5' type String is not present");
        }
    }
    
    /**
     * Convert ConfigListenerVo to packet format.
     * @return packet string compatible with older versions
     */
    public String toPacketString() {
        StringBuilder packetString = new StringBuilder();
        packetString
                .append(dataId)
                .append(Constants.WORD_SEPARATOR)
                .append(group)
                .append(Constants.WORD_SEPARATOR)
                .append(contentMd5);
        if (null != tenant) {
            packetString
                    .append(Constants.WORD_SEPARATOR)
                    .append(tenant);
        }
        packetString.append(Constants.LINE_SEPARATOR);
        return packetString.toString();
    }
}
