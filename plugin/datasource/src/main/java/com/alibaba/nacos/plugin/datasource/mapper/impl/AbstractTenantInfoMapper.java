/*
 * Copyright 1999-2021 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.plugin.datasource.mapper.impl;

import com.alibaba.nacos.config.server.model.Page;
import com.alibaba.nacos.config.server.model.TenantInfo;
import com.alibaba.nacos.plugin.datasource.constant.TableConstant;
import com.alibaba.nacos.plugin.datasource.mapper.base.TenantInfoMapper;

import java.util.List;

/**
 * The abstract TenantInfoMapper.
 * To implement the default method of the TenantInfoMapper interface.
 * @author hyx
 **/

public class AbstractTenantInfoMapper implements TenantInfoMapper {
    
    @Override
    public boolean insertTenantInfoAtomic(String kp, String tenantId, String tenantName, String tenantDesc,
            String createResoure, long time) {
        return false;
    }
    
    @Override
    public void updateTenantNameAtomic(String kp, String tenantId, String tenantName, String tenantDesc) {
    
    }
    
    @Override
    public List<TenantInfo> findTenantByKp(String kp) {
        return null;
    }
    
    @Override
    public TenantInfo findTenantByKp(String kp, String tenantId) {
        return null;
    }
    
    @Override
    public boolean removeTenantInfoAtomic(String kp, String tenantId) {
        return false;
    }
    
    @Override
    public String tableName() {
        return TableConstant.TENANT_INFO;
    }
    
    @Override
    public Integer insert(TenantInfo var1) {
        return null;
    }
    
    @Override
    public Integer update(TenantInfo var1) {
        return null;
    }
    
    @Override
    public TenantInfo select(Long id) {
        return null;
    }
    
    @Override
    public List<TenantInfo> selectAll() {
        return null;
    }
    
    @Override
    public Page<TenantInfo> selectPage(int pageNo, int pageSize) {
        return null;
    }
    
    @Override
    public Integer delete(Long id) {
        return null;
    }
    
    @Override
    public Integer selectCount() {
        return null;
    }
}
