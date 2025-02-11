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

package com.alibaba.nacos.api.config.model;

import java.io.Serializable;

/**
 * Nacos configuration basic information.
 *
 * @author xiweng.yy
 */
public class ConfigBasicInfo implements Serializable {
    
    private static final long serialVersionUID = 2662049844183052399L;
    
    /**
     * The actual storage identity of the configuration, which no actual meanings for usage.
     *
     * <p>
     *     Different storage datasource will have different id. Such as Relational Database the id is auto-generated table ids.
     * </p>
     */
    private String id;
    
    private String namespaceId;
    
    private String groupName;
    
    private String dataId;
    
    private String md5;
    
    private String type;
    
    private String appName;
    
    private long createTime;
    
    private long modifyTime;
    
    public String getId() {
        return id;
    }
    
    public void setId(String id) {
        this.id = id;
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
    
    public String getDataId() {
        return dataId;
    }
    
    public void setDataId(String dataId) {
        this.dataId = dataId;
    }
    
    public String getMd5() {
        return md5;
    }
    
    public void setMd5(String md5) {
        this.md5 = md5;
    }
    
    public String getType() {
        return type;
    }
    
    public void setType(String type) {
        this.type = type;
    }
    
    public String getAppName() {
        return appName;
    }
    
    public void setAppName(String appName) {
        this.appName = appName;
    }
    
    public long getCreateTime() {
        return createTime;
    }
    
    public void setCreateTime(long createTime) {
        this.createTime = createTime;
    }
    
    public long getModifyTime() {
        return modifyTime;
    }
    
    public void setModifyTime(long modifyTime) {
        this.modifyTime = modifyTime;
    }
}
