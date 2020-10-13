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
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.MappedSuperclass;
import java.util.Date;

/**
 * CapacityEntity.
 *
 * @author Nacos
 */
@Data
@MappedSuperclass
public abstract class CapacityEntity {
    
    @Id
    @Column(name = "id")
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "quota")
    private Integer quota;
    
    //usage 是mysql中关键字所以需要转义
    @Column(name = "`usage`")
    private Integer usage;
    
    @Column(name = "max_size")
    private Integer maxSize;
    
    @Column(name = "max_aggr_count")
    private Integer maxAggrCount;
    
    @Column(name = "max_aggr_size")
    private Integer maxAggrSize;
    
    @Column(name = "max_history_count")
    private Integer maxHistoryCount;
    
    @Column(name = "gmt_create")
    private Date gmtCreate;
    
    @Column(name = "gmt_modified")
    private Date gmtModified;
}
