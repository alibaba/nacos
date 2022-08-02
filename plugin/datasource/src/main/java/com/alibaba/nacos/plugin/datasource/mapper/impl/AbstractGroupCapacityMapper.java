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
import com.alibaba.nacos.config.server.model.capacity.GroupCapacity;
import com.alibaba.nacos.plugin.datasource.constant.TableConstant;
import com.alibaba.nacos.plugin.datasource.mapper.base.GroupCapacityMapper;

import java.util.List;

/**
 * The abstract GroupCapacityMapper.
 * To implement the default method of the GroupCapacityMapper interface.
 *
 * @author hyx
 **/

public class AbstractGroupCapacityMapper implements GroupCapacityMapper {
    
    @Override
    public GroupCapacity getGroupCapacity(String groupId) {
        return null;
    }
    
    @Override
    public boolean insertGroupCapacity(GroupCapacity capacity) {
        return false;
    }
    
    @Override
    public boolean incrementUsageWithDefaultQuotaLimit(GroupCapacity groupCapacity) {
        return false;
    }
    
    @Override
    public boolean incrementUsageWithQuotaLimit(GroupCapacity groupCapacity) {
        return false;
    }
    
    @Override
    public boolean incrementUsage(GroupCapacity groupCapacity) {
        return false;
    }
    
    @Override
    public boolean decrementUsage(GroupCapacity groupCapacity) {
        return false;
    }
    
    @Override
    public boolean updateGroupCapacity(String group, Integer quota, Integer maxSize, Integer maxAggrCount,
            Integer maxAggrSize) {
        return false;
    }
    
    @Override
    public boolean deleteGroupCapacity(String group) {
        return false;
    }
    
    @Override
    public String tableName() {
        return TableConstant.GROUP_CAPACITY;
    }
    
    @Override
    public Integer insert(GroupCapacity var1) {
        return null;
    }
    
    @Override
    public Integer update(GroupCapacity var1) {
        return null;
    }
    
    @Override
    public GroupCapacity select(Long id) {
        return null;
    }
    
    @Override
    public List<GroupCapacity> selectAll() {
        return null;
    }
    
    @Override
    public Page<GroupCapacity> selectPage(int pageNo, int pageSize) {
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
