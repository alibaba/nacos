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

import static com.alibaba.nacos.config.server.constant.Constants.CONFIG_INFO_BETA_TABLE_NAME;

/**
 * ConfigInfoBetaEntity.
 *
 * @author Nacos
 */
@Table(name = CONFIG_INFO_BETA_TABLE_NAME)
@Entity
public class ConfigInfoBetaEntity implements Serializable {
    
    @Id
    @Column(name = "id")
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "data_id")
    private String dataId;
    
    @Column(name = "group_id")
    private String groupId;
    
    @Column(name = "app_name")
    private String appName;
    
    @Column(name = "content")
    private String content;
    
    @Column(name = "beta_ips")
    private String betaIps;
    
    @Column(name = "md5")
    private String md5;
    
    @Column(name = "gmt_create")
    private Date gmtCreate;
    
    @Column(name = "gmt_modified")
    private Date gmtModified;
    
    @Column(name = "src_user")
    private String srcUser;
    
    @Column(name = "src_ip")
    private String srcIp;
    
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
    
    public String getAppName() {
        return appName;
    }
    
    public void setAppName(String appName) {
        this.appName = appName;
    }
    
    public String getContent() {
        return content;
    }
    
    public void setContent(String content) {
        this.content = content;
    }
    
    public String getBetaIps() {
        return betaIps;
    }
    
    public void setBetaIps(String betaIps) {
        this.betaIps = betaIps;
    }
    
    public String getMd5() {
        return md5;
    }
    
    public void setMd5(String md5) {
        this.md5 = md5;
    }
    
    public Date getGmtCreate() {
        return gmtCreate;
    }
    
    public void setGmtCreate(Date gmtCreate) {
        this.gmtCreate = gmtCreate;
    }
    
    public Date getGmtModified() {
        return gmtModified;
    }
    
    public void setGmtModified(Date gmtModified) {
        this.gmtModified = gmtModified;
    }
    
    public String getSrcUser() {
        return srcUser;
    }
    
    public void setSrcUser(String srcUser) {
        this.srcUser = srcUser;
    }
    
    public String getSrcIp() {
        return srcIp;
    }
    
    public void setSrcIp(String srcIp) {
        this.srcIp = srcIp;
    }
    
    public String getTenantId() {
        return tenantId;
    }
    
    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }
}
