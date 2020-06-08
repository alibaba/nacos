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

import static com.alibaba.nacos.config.server.constant.Constants.CONFIG_TAGS_RELATION_TABLE_NAME;

/**
 * @author Nacos
 */
@Table(name = CONFIG_TAGS_RELATION_TABLE_NAME)
@Entity
@Data
public class ConfigTagsRelation implements Serializable {


    @Id
    @Column(name = "nid")
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long nid;

    @Column(name = "id")
    private Long id;

    @Column(name = "tag_name")
    private String tagName;

    @Column(name = "tag_type")
    private String tagType;

    @Column(name = "data_id")
    private String dataId;

    @Column(name = "group_id")
    private String groupId;

    @Column(name = "tenant_id")
    private String tenantId;



}
