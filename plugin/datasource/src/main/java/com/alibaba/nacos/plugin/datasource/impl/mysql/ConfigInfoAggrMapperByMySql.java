/*
 * Copyright 1999-2022 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.plugin.datasource.impl.mysql;

import com.alibaba.nacos.plugin.datasource.constants.DataSourceConstant;
import com.alibaba.nacos.plugin.datasource.constants.TableConstant;
import com.alibaba.nacos.plugin.datasource.mapper.AbstractMapper;
import com.alibaba.nacos.plugin.datasource.mapper.ConfigInfoAggrMapper;

import java.util.List;

/**
 * The mysql implementation of ConfigInfoAggrMapper.
 *
 * @author hyx
 **/

public class ConfigInfoAggrMapperByMySql extends AbstractMapper implements ConfigInfoAggrMapper {
    
    @Override
    public String batchRemoveAggr(List<String> datumList) {
        final StringBuilder datumString = new StringBuilder();
        for (String datum : datumList) {
            datumString.append('\'').append(datum).append("',");
        }
        datumString.deleteCharAt(datumString.length() - 1);
        return "DELETE FROM config_info_aggr WHERE data_id = ? AND group_id = ? AND tenant_id = ? AND datum_id IN ("
                + datumString.toString() + ")";
    }
    
    @Override
    public String aggrConfigInfoCount(int size, boolean isIn) {
        StringBuilder sql = new StringBuilder(
                " SELECT count(*) FROM config_info_aggr WHERE data_id = ? AND group_id = ? AND tenant_id = ? AND datum_id");
        if (isIn) {
            sql.append(" IN (");
        } else {
            sql.append(" NOT IN (");
        }
        for (int i = 0; i < size; i++) {
            if (i > 0) {
                sql.append(", ");
            }
            sql.append('?');
        }
        sql.append(')');
    
        return sql.toString();
    }
    
    @Override
    public String aggrConfigInfoCount() {
        return " SELECT count(*) FROM config_info_aggr WHERE data_id = ? AND group_id = ? AND tenant_id = ?";
    }
    
    @Override
    public String findConfigInfoAggr() {
        return "SELECT data_id,group_id,tenant_id,datum_id,app_name,content FROM "
                + "config_info_aggr WHERE data_id = ? AND group_id = ? AND tenant_id = ? ORDER BY datum_id";
    }
    
    @Override
    public String findConfigInfoAggrByPageFetchRows(int startRow, int pageSize) {
        return "SELECT data_id,group_id,tenant_id,datum_id,app_name,content FROM config_info_aggr WHERE data_id= ? AND "
                + "group_id= ? AND tenant_id= ? ORDER BY datum_id LIMIT ?,?";
    }
    
    @Override
    public String findAllAggrGroup() {
        return "SELECT DISTINCT data_id, group_id, tenant_id FROM config_info_aggr";
    }
    
    @Override
    public String getTableName() {
        return TableConstant.CONFIG_INFO_AGGR;
    }
    
    @Override
    public String getDataSource() {
        return DataSourceConstant.MYSQL;
    }
}
