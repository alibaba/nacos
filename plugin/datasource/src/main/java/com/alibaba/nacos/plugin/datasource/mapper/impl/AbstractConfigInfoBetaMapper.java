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
import com.alibaba.nacos.config.server.model.ConfigInfoBetaWrapper;
import com.alibaba.nacos.plugin.datasource.mapper.base.ConfigInfoBetaMapper;

import java.sql.Timestamp;

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
}
