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
import com.alibaba.nacos.config.server.model.ConfigInfoTagWrapper;
import com.alibaba.nacos.plugin.datasource.mapper.base.ConfigInfoTagMapper;

import java.sql.Timestamp;
import java.util.List;

/**
 * The abstract ConfigInfoTagMapper.
 * To implement the default method of the ConfigInfoTagMapper interface.
 * @author hyx
 **/

public class AbstractConfigInfoTagMapper implements ConfigInfoTagMapper {
    
    @Override
    public boolean updateConfigInfo4Tag(ConfigInfo configInfo, String tag, String srcIp, String srcUser,
            Timestamp time) {
        return false;
    }
    
    @Override
    public boolean insertOrUpdateTag(ConfigInfo configInfo, String tag, String srcIp, String srcUser, Timestamp time) {
        return false;
    }
    
    @Override
    public ConfigInfoTagWrapper findConfigInfo4Tag(String dataId, String group, String tenant, String tag) {
        return null;
    }
    
    @Override
    public List<String> getConfigTagsByTenant(String tenant) {
        return null;
    }
    
    @Override
    public List<String> selectTagByConfig(String dataId, String group, String tenant) {
        return null;
    }
    
    @Override
    public boolean removeConfigInfoTag(String dataId, String group, String tenant, String tag, String srcIp,
            String srcUser) {
        return false;
    }
}
