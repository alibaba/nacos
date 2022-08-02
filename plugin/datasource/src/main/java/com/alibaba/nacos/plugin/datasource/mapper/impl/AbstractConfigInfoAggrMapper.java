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

import com.alibaba.nacos.config.server.model.ConfigInfoAggr;
import com.alibaba.nacos.config.server.model.Page;
import com.alibaba.nacos.plugin.datasource.constant.TableConstant;
import com.alibaba.nacos.plugin.datasource.mapper.base.ConfigInfoAggrMapper;

import java.util.List;
import java.util.Map;

/**
 * The abstract ConfigInfoAggrMapper.
 * To implement the default method of the ConfigInfoAggrMapper interface.
 *
 * @author hyx
 **/

public class AbstractConfigInfoAggrMapper implements ConfigInfoAggrMapper {
    
    @Override
    public Integer addAggrConfigInfo(String dataId, String group, String tenant, String datumId, String appName,
            String content) {
        return null;
    }
    
    @Override
    public Integer removeSingleAggrConfigInfo(String dataId, String group, String tenant, String datumId) {
        return null;
    }
    
    @Override
    public Integer removeAggrConfigInfo(String dataId, String group, String tenant) {
        return null;
    }
    
    @Override
    public boolean batchRemoveAggr(String dataId, String group, String tenant, List<String> datumList) {
        return false;
    }
    
    @Override
    public boolean batchPublishAggr(String dataId, String group, String tenant, Map<String, String> datumMap,
            String appName) {
        return false;
    }
    
    @Override
    public boolean replaceAggr(String dataId, String group, String tenant, Map<String, String> datumMap,
            String appName) {
        return false;
    }
    
    @Override
    public String tableName() {
        return TableConstant.CONFIG_INFO_AGGR;
    }
    
    @Override
    public Integer insert(ConfigInfoAggr var1) {
        return null;
    }
    
    @Override
    public Integer update(ConfigInfoAggr var1) {
        return null;
    }
    
    @Override
    public ConfigInfoAggr select(Long id) {
        return null;
    }
    
    @Override
    public List<ConfigInfoAggr> selectAll() {
        return null;
    }
    
    @Override
    public Page<ConfigInfoAggr> selectPage(int pageNo, int pageSize) {
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
