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
import com.alibaba.nacos.api.model.NacosForm;
import org.springframework.http.HttpStatus;

/**
 * ConfigForm.
 *
 * @author dongyafei
 * @author xiweng.yy
 */
public class ConfigForm implements NacosForm, Cloneable {
    
    private static final long serialVersionUID = 4124932564086863921L;
    
    private String dataId;
    
    /**
     * Deprecated, please use {@link ConfigFormV3#groupName} replaced.
     */
    @Deprecated
    private String group;
    
    private String namespaceId = StringUtils.EMPTY;
    
    private String content;
    
    private String tag;
    
    private String appName;
    
    private String srcUser;
    
    private String configTags;
    
    private String encryptedDataKey;
    
    private String grayName;
    
    private String grayRuleExp;
    
    private String grayVersion;
    
    private int grayPriority;
    
    private String desc;
    
    private String use;
    
    private String effect;
    
    private String type;
    
    private String schema;
    
    public ConfigForm() {
    }
    
    public ConfigForm(String dataId, String group, String namespaceId, String content, String tag, String appName,
            String srcUser, String configTags, String desc, String use, String effect, String type, String schema) {
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
    }
    
    @Override
    public ConfigForm clone() {
        ConfigForm configForm = new ConfigForm();
        configForm.setDataId(this.dataId);
        configForm.setGroup(this.group);
        configForm.setNamespaceId(this.namespaceId);
        configForm.setContent(this.content);
        configForm.setTag(this.tag);
        configForm.setAppName(this.appName);
        configForm.setSrcUser(this.srcUser);
        configForm.setConfigTags(this.configTags);
        configForm.setDesc(this.desc);
        configForm.setUse(this.use);
        configForm.setEffect(this.effect);
        configForm.setType(this.type);
        configForm.setSchema(this.schema);
        configForm.setEncryptedDataKey(this.encryptedDataKey);
        configForm.setGrayName(this.grayName);
        configForm.setGrayRuleExp(this.grayRuleExp);
        configForm.setGrayVersion(this.grayVersion);
        configForm.setGrayPriority(this.grayPriority);
        return configForm;
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
    
    public String getGrayName() {
        return grayName;
    }
    
    public void setGrayName(String grayName) {
        this.grayName = grayName;
    }
    
    public String getGrayRuleExp() {
        return grayRuleExp;
    }
    
    public void setGrayRuleExp(String grayRuleExp) {
        this.grayRuleExp = grayRuleExp;
    }
    
    public String getGrayVersion() {
        return grayVersion;
    }
    
    public void setGrayVersion(String grayVersion) {
        this.grayVersion = grayVersion;
    }
    
    public int getGrayPriority() {
        return grayPriority;
    }
    
    public void setGrayPriority(int grayPriority) {
        this.grayPriority = grayPriority;
    }
    
    @Override
    public void validate() throws NacosApiException {
        if (StringUtils.isBlank(dataId)) {
            throw new NacosApiException(HttpStatus.BAD_REQUEST.value(), ErrorCode.PARAMETER_MISSING,
                    "Required parameter 'dataId' type String is not present");
        } else if (StringUtils.isBlank(group)) {
            throw new NacosApiException(HttpStatus.BAD_REQUEST.value(), ErrorCode.PARAMETER_MISSING,
                    "Required parameter 'group' type String is not present");
        }
    }
    
    /**
     * Validate form parameter and include validate `content` parameters.
     *
     * @throws NacosApiException NacosApiException
     */
    public void validateWithContent() throws NacosApiException {
        validate();
        if (StringUtils.isBlank(content)) {
            throw new NacosApiException(HttpStatus.BAD_REQUEST.value(), ErrorCode.PARAMETER_MISSING,
                    "Required parameter 'content' type String is not present");
        }
    }
}
