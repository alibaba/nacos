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

import com.alibaba.nacos.config.server.model.ConfigInfo;
import com.alibaba.nacos.config.server.model.ConfigInfo4Beta;
import com.alibaba.nacos.config.server.model.ConfigInfoBetaWrapper;
import com.alibaba.nacos.config.server.model.Page;
import com.alibaba.nacos.plugin.datasource.constant.TableConstant;
import com.alibaba.nacos.plugin.datasource.mapper.base.ConfigInfoBetaMapper;

import java.sql.Timestamp;
import java.util.List;

/**
 * The abstract ConfigInfoBetaMapper.
 * To implement the default method of the ConfigInfoBetaMapper interface.
 *
 * @author hyx
 **/

public class AbstractConfigInfoBetaMapper implements ConfigInfoBetaMapper {
    
    @Override
    public boolean addConfigInfo4Beta(ConfigInfo configInfo, String betaIps, String srcIp, String srcUser,
            Timestamp time) {
        return false;
    }
    
    @Override
    public boolean updateConfigInfo4Beta(ConfigInfo configInfo, String betaIps, String srcIp, String srcUser,
            Timestamp time) {
        return false;
    }
    
    @Override
    public boolean insertOrUpdateBeta(ConfigInfo configInfo, String betaIps, String srcIp, String srcUser,
            Timestamp time) {
        return false;
    }
    
    @Override
    public boolean removeConfigInfo4Beta(String dataId, String group, String tenant) {
        return false;
    }
    
    @Override
    public ConfigInfoBetaWrapper findConfigInfo4Beta(String dataId, String group, String tenant) {
        return null;
    }
    
    @Override
    public String tableName() {
        return TableConstant.CONFIG_INFO_BETA;
    }
    
    @Override
    public Integer insert(ConfigInfo4Beta var1) {
        return null;
    }
    
    @Override
    public Integer update(ConfigInfo4Beta var1) {
        return null;
    }
    
    @Override
    public ConfigInfo4Beta select(Long id) {
        return null;
    }
    
    @Override
    public List<ConfigInfo4Beta> selectAll() {
        return null;
    }
    
    @Override
    public Page<ConfigInfo4Beta> selectPage(int pageNo, int pageSize) {
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
