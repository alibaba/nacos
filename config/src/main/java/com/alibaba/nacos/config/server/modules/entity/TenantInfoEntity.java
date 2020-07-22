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

import javax.persistence.*;
import java.io.Serializable;

import static com.alibaba.nacos.config.server.constant.Constants.TENANT_INFO_TABLE_NAME;

/**
 * @author Nacos
 */
@Table(name = TENANT_INFO_TABLE_NAME)
@Entity
@Data
public class TenantInfoEntity implements Serializable {


    @Id
    @Column(name = "id")
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "kp")
    private String kp;

    @Column(name = "tenant_id")
    private String tenantId;

    @Column(name = "tenant_name")
    private String tenantName;

    @Column(name = "tenant_desc")
    private String tenantDesc;

    @Column(name = "create_source")
    private String createSource;

    @Column(name = "gmt_create")
    private Long gmtCreate;

    @Column(name = "gmt_modified")
    private Long gmtModified;

}
