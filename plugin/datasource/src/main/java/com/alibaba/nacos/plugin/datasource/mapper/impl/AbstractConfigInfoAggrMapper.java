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
import com.alibaba.nacos.plugin.datasource.constant.TableConstant;
import com.alibaba.nacos.plugin.datasource.mapper.base.ConfigInfoAggrMapper;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Timestamp;
import java.util.Map;

/**
 * The abstract ConfigInfoAggrMapper.
 * To implement the default method of the ConfigInfoAggrMapper interface.
 *
 * @author hyx
 **/

public abstract class AbstractConfigInfoAggrMapper extends AbstractMapper<ConfigInfoAggr> implements ConfigInfoAggrMapper {
    
    private final JdbcTemplate jdbcTemplate = getGetJdbcTemplate();
    
    @Override
    public Integer addAggrConfigInfo(String dataId, String group, String tenant, String datumId, String appName,
            String content) {
        final Timestamp now = new Timestamp(System.currentTimeMillis());
        String sql = "SELECT content FROM config_info_aggr WHERE data_id = ? AND group_id = ? AND tenant_id = ?  AND datum_id = ?";
        return jdbcTemplate.update(sql, dataId, group, tenant, datumId, appName, content, now);
    }
    
    @Override
    public Integer removeSingleAggrConfigInfo(String dataId, String group, String tenant, String datumId) {
        String sql = "DELETE FROM config_info_aggr WHERE data_id=? AND group_id=? AND tenant_id=? AND datum_id=?";
        return jdbcTemplate.update(sql, ps -> {
            int index = 1;
            ps.setString(index++, dataId);
            ps.setString(index++, group);
            ps.setString(index++, tenant);
            ps.setString(index, datumId);
        });
    }
    
    @Override
    public Integer removeAggrConfigInfo(String dataId, String group, String tenant) {
        String sql = "DELETE FROM config_info_aggr WHERE data_id=? AND group_id=? AND tenant_id=?";
        return jdbcTemplate.update(sql, ps -> {
            int index = 1;
            ps.setString(index++, dataId);
            ps.setString(index++, group);
            ps.setString(index, tenant);
        });
    }
    
    @Override
    public Integer batchRemoveAggr(String dataId, String group, String tenant, String datumString) {
        String sql =
                "DELETE FROM config_info_aggr WHERE data_id=? AND group_id=? AND tenant_id=? AND datum_id IN ("
                        + datumString + ")";
        return jdbcTemplate.update(sql, dataId, group, tenant);
    }
    
    @Override
    public boolean replaceAggr(String dataId, String group, String tenant, Map<String, String> datumMap,
            String appName) {
        String sql = "INSERT INTO config_info_aggr(data_id, group_id, tenant_id, datum_id, app_name, content, gmt_modified) VALUES(?,?,?,?,?,?,?) ";
        for (Map.Entry<String, String> datumEntry : datumMap.entrySet()) {
            jdbcTemplate.update(sql, dataId, group, tenant, datumEntry.getKey(), appName,
                    datumEntry.getValue(), new Timestamp(System.currentTimeMillis()));
        }
        return true;
    }
    
    @Override
    public String tableName() {
        return TableConstant.CONFIG_INFO_AGGR;
    }
    
}
