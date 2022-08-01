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
import com.alibaba.nacos.plugin.datasource.mapper.base.HisConfigInfoMapper;

import java.sql.Timestamp;

/**
 * The abstract HisConfigInfoMapper.
 * To implement the default method of the HisConfigInfoMapper interface.
 * @author hyx
 **/

public class AbstractHisConfigInfoMapper implements HisConfigInfoMapper {
    
    @Override
    public Integer removeConfigHistory(Timestamp startTime, int limitSize) {
        return null;
    }
    
    @Override
    public Integer findConfigHistoryCountByTime(Timestamp startTime) {
        return null;
    }
    
    @Override
    public Integer insertConfigHistoryAtomic(long id, ConfigInfo configInfo, String srcIp, String srcUser,
            Timestamp time, String ops) {
        return null;
    }
}
