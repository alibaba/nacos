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

package com.alibaba.nacos.config.server.modules.entity;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Table;
import java.io.Serializable;
import java.util.Date;

import static com.alibaba.nacos.config.server.constant.Constants.CONFIG_INFO_AGGR_TABLE_NAME;

/**
 * ConfigInfoAggrEntity.
 *
 * @author Nacos
 */
@Table(name = CONFIG_INFO_AGGR_TABLE_NAME)
@Entity
public class ConfigInfoAggrEntity implements Serializable {
    
    @Id
    @Column(name = "id")
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "data_id")
    private String dataId;
    
    @Column(name = "group_id")
    private String groupId;
    
    @Column(name = "datum_id")
    private String datumId;
    
    @Column(name = "content")
    private String content;
    
    @Column(name = "gmt_modified")
    private Date gmtModified;
    
    @Column(name = "app_name")
    private String appName;
    
    @Column(name = "tenant_id")
    private String tenantId;
    
    public Long getId() {
        return id;
    }
    
    public void setId(Long id) {
        this.id = id;
    }
    
    public String getDataId() {
        return dataId;
    }
    
    public void setDataId(String dataId) {
        this.dataId = dataId;
    }
    
    public String getGroupId() {
        return groupId;
    }
    
    public void setGroupId(String groupId) {
        this.groupId = groupId;
    }
    
    public String getDatumId() {
        return datumId;
    }
    
    public void setDatumId(String datumId) {
        this.datumId = datumId;
    }
    
    public String getContent() {
        return content;
    }
    
    public void setContent(String content) {
        this.content = content;
    }
    
    public Date getGmtModified() {
        return gmtModified;
    }
    
    public void setGmtModified(Date gmtModified) {
        this.gmtModified = gmtModified;
    }
    
    public String getAppName() {
        return appName;
    }
    
    public void setAppName(String appName) {
        this.appName = appName;
    }
    
    public String getTenantId() {
        return tenantId;
    }
    
    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }
}
