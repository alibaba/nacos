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
import javax.persistence.Id;
import javax.persistence.Table;
import java.io.Serializable;

import static com.alibaba.nacos.config.server.constant.Constants.PERMISSIONS_TABLE_NAME;

/**
 * PermissionsEntity.
 *
 * @author Nacos
 */
@Table(name = PERMISSIONS_TABLE_NAME)
@Entity
public class PermissionsEntity implements Serializable {
    
    
    /**
     * role.
     */
    @Id
    @Column(name = "role")
    private String role;
    
    /**
     * resource.
     */
    @Column(name = "resource")
    private String resource;
    
    /**
     * action.
     */
    @Column(name = "action")
    private String action;
    
    public PermissionsEntity() {
    }
    
    public PermissionsEntity(String role, String resource, String action) {
        this.role = role;
        this.resource = resource;
        this.action = action;
    }
    
    public String getRole() {
        return role;
    }
    
    public void setRole(String role) {
        this.role = role;
    }
    
    public String getResource() {
        return resource;
    }
    
    public void setResource(String resource) {
        this.resource = resource;
    }
    
    public String getAction() {
        return action;
    }
    
    public void setAction(String action) {
        this.action = action;
    }
}
