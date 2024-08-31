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

package com.alibaba.nacos.config.server.model.form;

import com.alibaba.nacos.api.exception.api.NacosApiException;
import com.alibaba.nacos.api.model.v2.ErrorCode;
import com.alibaba.nacos.common.utils.StringUtils;
import org.springframework.http.HttpStatus;

import java.io.Serializable;
import java.util.Objects;

/**
 * ConfigForm.
 *
 * @author dongyafei
 * @date 2022/7/24
 */
public class ConfigForm implements Serializable {
    
    private static final long serialVersionUID = 4124932564086863921L;
    
    private String dataId;
    
    private String group;
    
    private String namespaceId = StringUtils.EMPTY;
    
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
    
    private String encryptedDataKey;
    
    public ConfigForm() {
    }
    
    public ConfigForm(String dataId, String group, String namespaceId, String content, String tag, String appName,
            String srcUser, String configTags, String desc, String use, String effect, String type, String schema,
            String encryptedDataKey) {
        this.dataId = dataId;
        this.group = group;
        this.namespaceId = namespaceId;
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
        this.encryptedDataKey = encryptedDataKey;
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
    
    public String getNamespaceId() {
        return namespaceId;
    }
    
    public void setNamespaceId(String namespaceId) {
        this.namespaceId = namespaceId;
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
    
    public String getEncryptedDataKey() {
        return encryptedDataKey;
    }
    
    public void setEncryptedDataKey(String encryptedDataKey) {
        this.encryptedDataKey = encryptedDataKey;
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        ConfigForm configForm = (ConfigForm) o;
        return dataId.equals(configForm.dataId) && group.equals(configForm.group) && Objects.equals(namespaceId, configForm.namespaceId)
                && content.equals(configForm.content) && Objects.equals(tag, configForm.tag) && Objects
                .equals(appName, configForm.appName) && Objects.equals(srcUser, configForm.srcUser) && Objects
                .equals(configTags, configForm.configTags) && Objects.equals(desc, configForm.desc) && Objects.equals(
                use, configForm.use) && Objects.equals(effect, configForm.effect) && Objects.equals(type,
                configForm.type) && Objects.equals(schema, configForm.schema) && Objects.equals(encryptedDataKey,
                configForm.encryptedDataKey);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(dataId, group, namespaceId, content, tag, appName, srcUser, configTags, desc, use, effect, type,
                schema, encryptedDataKey);
    }
    
    @Override
    public String toString() {
        return "ConfigVo{" + "dataId='" + dataId + '\'' + ", group='" + group + '\'' + ", namespaceId='" + namespaceId + '\''
                + ", content='" + content + '\'' + ", tag='" + tag + '\'' + ", appName='" + appName + '\''
                + ", srcUser='" + srcUser + '\'' + ", configTags='" + configTags + '\'' + ", desc='" + desc + '\''
                + ", use='" + use + '\'' + ", effect='" + effect + '\'' + ", type='" + type + '\'' + ", schema='"
                + schema + '\'' + ", encryptedDataKey='" + encryptedDataKey + '\'' + '}';
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
