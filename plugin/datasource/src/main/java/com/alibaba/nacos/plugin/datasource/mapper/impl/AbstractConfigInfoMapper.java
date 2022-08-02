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
import com.alibaba.nacos.config.server.model.Page;
import com.alibaba.nacos.plugin.datasource.constant.TableConstant;
import com.alibaba.nacos.plugin.datasource.manager.DataSourceManager;
import com.alibaba.nacos.plugin.datasource.mapper.base.ConfigInfoMapper;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Timestamp;
import java.util.List;
import java.util.Map;

/**
 * The abstract ConfigInfoMapper.
 * To implement the default method of the ConfigInfoMapper interface.
 *
 * @author hyx
 **/

public class AbstractConfigInfoMapper implements ConfigInfoMapper {
    
    /**
     * The JdbcTemplate.
     */
    private JdbcTemplate jdbcTemplate;
    
    public AbstractConfigInfoMapper() {
        this.jdbcTemplate = DataSourceManager.instance().getJdbcTemplate();
    }
    
    protected JdbcTemplate getJdbcTemplate() {
        return this.jdbcTemplate;
    }
    
    @Override
    public boolean insertOrUpdateConfigInfo(String srcIp, String srcUser, ConfigInfo configInfo, Timestamp time,
            Map<String, Object> configAdvanceInfo) {
        return false;
    }
    
    @Override
    public List<ConfigInfo> selectAll() {
        return null;
    }
    
    @Override
    public List<ConfigInfo> selectAll(String dataId, String group, String tenant) {
        return null;
    }
    
    @Override
    public Integer delete(Long id) {
        return null;
    }
    
    @Override
    public boolean delete(String dataId, String group, String tenant, String clientIp, String srcUser) {
        return false;
    }
    
    @Override
    public String tableName() {
        return TableConstant.CONFIG_INFO;
    }
    
    @Override
    public Integer insert(ConfigInfo var1) {
        return null;
    }
    
    @Override
    public Integer update(ConfigInfo var1) {
        return null;
    }
    
    @Override
    public ConfigInfo select(Long id) {
        return null;
    }
    
    @Override
    public Page<ConfigInfo> selectPage(int pageNo, int pageSize) {
        return null;
    }
    
    @Override
    public Integer selectCount() {
        return null;
    }
    
}
