/*
 * Copyright 1999-2018 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.config.server.model;

import java.io.Serializable;
import java.sql.Timestamp;

/**
 * ConfigHistoryInfoPair.
 *
 * @author dirtybit
 */
public class ConfigHistoryInfoDetail implements Serializable {
    
    private static final long serialVersionUID = -7827521105376245603L;
    
    private long id;
    
    private long lastId = -1;
    
    private String dataId;
    
    private String group;
    
    private String tenant;
    
    /**
     * Operation type, include inserting, updating and deleting.
     */
    private String opType;
    
    private String publishType;
    
    private String grayName;
    
    private String appName;
    
    private String srcIp;
    
    private String srcUser;
    
    private String originalMd5;
    
    private String originalContent;
    
    private String originalEncryptedDataKey;
    
    private String originalExtInfo;
    
    private String updatedMd5;
    
    private String updatedContent;
    
    private String updatedEncryptedDataKey;
    
    private String updateExtInfo;
    
    private Timestamp createdTime;
    
    private Timestamp lastModifiedTime;
    
    public long getId() {
        return id;
    }
    
    public void setId(long id) {
        this.id = id;
    }
    
    public long getLastId() {
        return lastId;
    }
    
    public void setLastId(long lastId) {
        this.lastId = lastId;
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
    
    public String getOpType() {
        return opType;
    }
    
    public void setOpType(String opType) {
        this.opType = opType;
    }
    
    public String getPublishType() {
        return publishType;
    }
    
    public void setPublishType(String publishType) {
        this.publishType = publishType;
    }
    
    public String getGrayName() {
        return grayName;
    }
    
    public void setGrayName(String grayName) {
        this.grayName = grayName;
    }
    
    public String getAppName() {
        return appName;
    }
    
    public void setAppName(String appName) {
        this.appName = appName;
    }
    
    public String getSrcIp() {
        return srcIp;
    }
    
    public void setSrcIp(String srcIp) {
        this.srcIp = srcIp;
    }
    
    public String getSrcUser() {
        return srcUser;
    }
    
    public void setSrcUser(String srcUser) {
        this.srcUser = srcUser;
    }
    
    public String getOriginalMd5() {
        return originalMd5;
    }
    
    public void setOriginalMd5(String originalMd5) {
        this.originalMd5 = originalMd5;
    }
    
    public String getOriginalContent() {
        return originalContent;
    }
    
    public void setOriginalContent(String originalContent) {
        this.originalContent = originalContent;
    }
    
    public String getOriginalEncryptedDataKey() {
        return originalEncryptedDataKey;
    }
    
    public void setOriginalEncryptedDataKey(String originalEncryptedDataKey) {
        this.originalEncryptedDataKey = originalEncryptedDataKey;
    }
    
    public String getOriginalExtInfo() {
        return originalExtInfo;
    }
    
    public void setOriginalExtInfo(String originalExtInfo) {
        this.originalExtInfo = originalExtInfo;
    }
    
    public String getUpdatedMd5() {
        return updatedMd5;
    }
    
    public void setUpdatedMd5(String updatedMd5) {
        this.updatedMd5 = updatedMd5;
    }
    
    public String getUpdatedContent() {
        return updatedContent;
    }
    
    public void setUpdatedContent(String updatedContent) {
        this.updatedContent = updatedContent;
    }
    
    public String getUpdatedEncryptedDataKey() {
        return updatedEncryptedDataKey;
    }
    
    public void setUpdatedEncryptedDataKey(String updatedEncryptedDataKey) {
        this.updatedEncryptedDataKey = updatedEncryptedDataKey;
    }
    
    public String getUpdateExtInfo() {
        return updateExtInfo;
    }
    
    public void setUpdateExtInfo(String updateExtInfo) {
        this.updateExtInfo = updateExtInfo;
    }
    
    public Timestamp getCreatedTime() {
        return createdTime;
    }
    
    public void setCreatedTime(Timestamp createdTime) {
        this.createdTime = createdTime;
    }
    
    public Timestamp getLastModifiedTime() {
        return lastModifiedTime;
    }
    
    public void setLastModifiedTime(Timestamp lastModifiedTime) {
        this.lastModifiedTime = lastModifiedTime;
    }
}