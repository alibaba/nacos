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

import lombok.Data;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Table;
import java.io.Serializable;
import java.util.Date;

import static com.alibaba.nacos.config.server.constant.Constants.HIS_CONFIG_INFO_TABLE_NAME;

/**
 * HisConfigInfoEntity.
 *
 * @author Nacos
 */
@Table(name = HIS_CONFIG_INFO_TABLE_NAME)
@Entity
@Data
public class HisConfigInfoEntity implements Serializable {
    
    @Id
    @Column(name = "nid")
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long nid;
    
    @Column(name = "id")
    private Long id;
    
    @Column(name = "data_id")
    private String dataId;
    
    @Column(name = "group_id")
    private String groupId;
    
    @Column(name = "app_name")
    private String appName;
    
    @Column(name = "content")
    private String content;
    
    @Column(name = "md5")
    private String md5;
    
    @Column(name = "gmt_create", insertable = false)
    private Date gmtCreate;
    
    @Column(name = "gmt_modified")
    private Date gmtModified;
    
    @Column(name = "src_user")
    private String srcUser;
    
    @Column(name = "src_ip")
    private String srcIp;
    
    @Column(name = "op_type")
    private String opType;
    
    @Column(name = "tenant_id")
    private String tenantId;
    
}
