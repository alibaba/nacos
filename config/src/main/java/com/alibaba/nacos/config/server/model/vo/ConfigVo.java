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

import com.alibaba.nacos.api.exception.api.NacosApiException;
import com.alibaba.nacos.api.model.v2.ErrorCode;
import com.alibaba.nacos.common.utils.StringUtils;
import org.springframework.http.HttpStatus;

import java.io.Serializable;
import java.util.Objects;

/**
 * Config.
 *
 * @author dongyafei
 * @date 2022/7/24
 */
public class ConfigVo implements Serializable {
    
    private static final long serialVersionUID = 4124932564086863921L;
    
    private String dataId;
    
    private String group;
    
    private String tenant;
    
    private String content;
    
    private String tag;
    
    private String appName;
    
    private String srcUser;
    
    private String configTags;
    
    private String desc;
    
    private String use;
    
    private String effect;
    
    private String type;
    
    private String schema;
    
    public ConfigVo() {
    }
    
    public ConfigVo(String dataId, String group, String tenant, String content, String tag, String appName,
            String srcUser, String configTags, String desc, String use, String effect, String type, String schema) {
        this.dataId = dataId;
        this.group = group;
        this.tenant = tenant;
        this.content = content;
        this.tag = tag;
        this.appName = appName;
        this.srcUser = srcUser;
        this.configTags = configTags;
        this.desc = desc;
        this.use = use;
        this.effect = effect;
        this.type = type;
        this.schema = schema;
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
    
    public String getTenant() {
        return tenant;
    }
    
    public void setTenant(String tenant) {
        this.tenant = tenant;
    }
    
    public String getContent() {
        return content;
    }
    
    public void setContent(String content) {
        this.content = content;
    }
    
    public String getTag() {
        return tag;
    }
    
    public void setTag(String tag) {
        this.tag = tag;
    }
    
    public String getAppName() {
        return appName;
    }
    
    public void setAppName(String appName) {
        this.appName = appName;
    }
    
    public String getSrcUser() {
        return srcUser;
    }
    
    public void setSrcUser(String srcUser) {
        this.srcUser = srcUser;
    }
    
    public String getConfigTags() {
        return configTags;
    }
    
    public void setConfigTags(String configTags) {
        this.configTags = configTags;
    }
    
    public String getDesc() {
        return desc;
    }
    
    public void setDesc(String desc) {
        this.desc = desc;
    }
    
    public String getUse() {
        return use;
    }
    
    public void setUse(String use) {
        this.use = use;
    }
    
    public String getEffect() {
        return effect;
    }
    
    public void setEffect(String effect) {
        this.effect = effect;
    }
    
    public String getType() {
        return type;
    }
    
    public void setType(String type) {
        this.type = type;
    }
    
    public String getSchema() {
        return schema;
    }
    
    public void setSchema(String schema) {
        this.schema = schema;
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        ConfigVo configVo = (ConfigVo) o;
        return dataId.equals(configVo.dataId) && group.equals(configVo.group) && Objects.equals(tenant, configVo.tenant)
                && content.equals(configVo.content) && Objects.equals(tag, configVo.tag) && Objects
                .equals(appName, configVo.appName) && Objects.equals(srcUser, configVo.srcUser) && Objects
                .equals(configTags, configVo.configTags) && Objects.equals(desc, configVo.desc) && Objects
                .equals(use, configVo.use) && Objects.equals(effect, configVo.effect) && Objects
                .equals(type, configVo.type) && Objects.equals(schema, configVo.schema);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(dataId, group, tenant, content, tag, appName, srcUser, configTags, desc, use, effect, type,
                schema);
    }
    
    @Override
    public String toString() {
        return "ConfigVo{" + "dataId='" + dataId + '\'' + ", group='" + group + '\'' + ", tenant='" + tenant + '\''
                + ", content='" + content + '\'' + ", tag='" + tag + '\'' + ", appName='" + appName + '\''
                + ", srcUser='" + srcUser + '\'' + ", configTags='" + configTags + '\'' + ", desc='" + desc + '\''
                + ", use='" + use + '\'' + ", effect='" + effect + '\'' + ", type='" + type + '\'' + ", schema='"
                + schema + '\'' + '}';
    }
    
    /**
     * Validate.
     *
     * @throws NacosApiException NacosApiException.
     */
    public void validate() throws NacosApiException {
        if (StringUtils.isBlank(dataId)) {
            throw new NacosApiException(HttpStatus.BAD_REQUEST.value(), ErrorCode.PARAMETER_MISSING,
                    "Required parameter 'dataId' type String is not present");
        } else if (StringUtils.isBlank(group)) {
            throw new NacosApiException(HttpStatus.BAD_REQUEST.value(), ErrorCode.PARAMETER_MISSING,
                    "Required parameter 'group' type String is not present");
        } else if (StringUtils.isBlank(content)) {
            throw new NacosApiException(HttpStatus.BAD_REQUEST.value(), ErrorCode.PARAMETER_MISSING,
                    "Required parameter 'content' type String is not present");
        }
    }
}
