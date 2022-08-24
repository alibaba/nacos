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

package com.alibaba.nacos.plugin.config.model;

import java.io.Serializable;
import java.util.Map;

/**
 * ConfigChangeNotifyInfoV2.
 *
 * @author liyunfei
 */
public class ConfigChangeNotifyInfo implements Serializable {
    
    private static final long serialVersionUID = -4231243154144936421L;
    
    private String action;
    
    private String retVal;
    
    private long handleTime;
    
    private String srcIp;
    
    private String scrName;
    
    private String requestIp;
    
    private String appName;
    
    private String dataId;
    
    private String group;
    
    private Map<String, String> tenantItem;
    
    private Map<String, String> contentItem;
    
    private Map<String, Boolean> isBetaItem;
    
    private Map<String, String> descItem;
    
    private Map<String, String> typeItem;
    
    private Map<String, String> tagItem;
    
    public ConfigChangeNotifyInfo(String action, long handleTime, String dataId, String group) {
        this.action = action;
        this.handleTime = handleTime;
        this.dataId = dataId;
        this.group = group;
    }
    
    public String getAction() {
        return action;
    }
    
    public void setAction(String action) {
        this.action = action;
    }
    
    public String getRetVal() {
        return retVal;
    }
    
    public void setRetVal(String retVal) {
        this.retVal = retVal;
    }
    
    public long getHandleTime() {
        return handleTime;
    }
    
    public void setHandleTime(long handleTime) {
        this.handleTime = handleTime;
    }
    
    public String getSrcIp() {
        return srcIp;
    }
    
    public void setSrcIp(String srcIp) {
        this.srcIp = srcIp;
    }
    
    public String getScrName() {
        return scrName;
    }
    
    public void setScrName(String scrName) {
        this.scrName = scrName;
    }
    
    public String getRequestIp() {
        return requestIp;
    }
    
    public void setRequestIp(String requestIp) {
        this.requestIp = requestIp;
    }
    
    public String getAppName() {
        return appName;
    }
    
    public void setAppName(String appName) {
        this.appName = appName;
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
    
    public Map<String, String> getTenantItem() {
        return tenantItem;
    }
    
    public void setTenantItem(Map<String, String> tenantItem) {
        this.tenantItem = tenantItem;
    }
    
    public Map<String, String> getContentItem() {
        return contentItem;
    }
    
    public void setContentItem(Map<String, String> contentItem) {
        this.contentItem = contentItem;
    }
    
    public Map<String, Boolean> getIsBetaItem() {
        return isBetaItem;
    }
    
    public void setIsBetaItem(Map<String, Boolean> isBetaItem) {
        this.isBetaItem = isBetaItem;
    }
    
    public Map<String, String> getDescItem() {
        return descItem;
    }
    
    public void setDescItem(Map<String, String> descItem) {
        this.descItem = descItem;
    }
    
    public Map<String, String> getTypeItem() {
        return typeItem;
    }
    
    public void setTypeItem(Map<String, String> typeItem) {
        this.typeItem = typeItem;
    }
    
    public Map<String, String> getTagItem() {
        return tagItem;
    }
    
    public void setTagItem(Map<String, String> tagItem) {
        this.tagItem = tagItem;
    }
    
    @Override
    public String toString() {
        return "ConfigChangeNotifyInfoV2{" + "action='" + action + '\'' + ", retVal='" + retVal + '\'' + ", handleTime="
                + handleTime + ", srcIp='" + srcIp + '\'' + ", scrName='" + scrName + '\'' + ", requestIp='" + requestIp
                + '\'' + ", appName='" + appName + '\'' + ", dataId='" + dataId + '\'' + ", group='" + group + '\''
                + ", tenantItem=" + tenantItem + ", contentItem=" + contentItem + ", isBetaItem=" + isBetaItem
                + ", descItem=" + descItem + ", typeItem=" + typeItem + ", tagItem=" + tagItem + '}';
    }
}
