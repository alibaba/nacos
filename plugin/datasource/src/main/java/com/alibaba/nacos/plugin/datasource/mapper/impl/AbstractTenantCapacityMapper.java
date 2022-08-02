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
import com.alibaba.nacos.config.server.model.capacity.TenantCapacity;
import com.alibaba.nacos.plugin.datasource.constant.TableConstant;
import com.alibaba.nacos.plugin.datasource.mapper.base.TenantCapacityMapper;

import java.util.List;

/**
 * The abstract TenantCapacityMapper.
 * To implement the default method of the TenantCapacityMapper interface.
 * @author hyx
 **/

public class AbstractTenantCapacityMapper implements TenantCapacityMapper {
    
    @Override
    public TenantCapacity getTenantCapacity(String tenantId) {
        return null;
    }
    
    @Override
    public boolean insertTenantCapacity(TenantCapacity tenantCapacity) {
        return false;
    }
    
    @Override
    public boolean incrementUsageWithDefaultQuotaLimit(TenantCapacity tenantCapacity) {
        return false;
    }
    
    @Override
    public String tableName() {
        return TableConstant.TENANT_CAPACITY;
    }
    
    @Override
    public Integer insert(TenantCapacity var1) {
        return null;
    }
    
    @Override
    public Integer update(TenantCapacity var1) {
        return null;
    }
    
    @Override
    public TenantCapacity select(Long id) {
        return null;
    }
    
    @Override
    public List<TenantCapacity> selectAll() {
        return null;
    }
    
    @Override
    public Page<TenantCapacity> selectPage(int pageNo, int pageSize) {
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
