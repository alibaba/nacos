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

package com.alibaba.nacos.plugin.datasource.mapper;

import com.alibaba.nacos.common.utils.CollectionUtils;
import com.alibaba.nacos.plugin.datasource.constants.FieldConstant;
import com.alibaba.nacos.plugin.datasource.constants.TableConstant;
import com.alibaba.nacos.plugin.datasource.model.MapperContext;
import com.alibaba.nacos.plugin.datasource.model.MapperResult;

import java.util.ArrayList;
import java.util.List;

/**
 * The mapper of config info.
 *
 * @author hyx
 **/

public interface ConfigInfoAggrMapper extends Mapper {
    
    /**
     * To delete aggregated data in bulk, you need to specify a size of datum list.
     * The default sql:
     * DELETE FROM config_info_aggr WHERE data_id=? AND group_id=? AND tenant_id=? AND datum_id IN (...)
     *
     * @param context The context of datum_id, data_id, group_id, tenant_id
     * @return The sql of deleting aggregated data in bulk.
     */
    default MapperResult batchRemoveAggr(MapperContext context) {
        final List<String> datumList = (List<String>) context.getWhereParameter(FieldConstant.DATUM_ID);
        final String dataId = (String) context.getWhereParameter(FieldConstant.DATA_ID);
        final String group = (String) context.getWhereParameter(FieldConstant.GROUP_ID);
        final String tenantTmp = (String) context.getWhereParameter(FieldConstant.TENANT_ID);
        
        List<Object> paramList = new ArrayList<>();
        paramList.add(dataId);
        paramList.add(group);
        paramList.add(tenantTmp);
        
        final StringBuilder placeholderString = new StringBuilder();
        for (int i = 0; i < datumList.size(); i++) {
            if (i != 0) {
                placeholderString.append(", ");
            }
            placeholderString.append('?');
            paramList.add(datumList.get(i));
        }
        
        String sql =
                "DELETE FROM config_info_aggr WHERE data_id = ? AND group_id = ? AND tenant_id = ? AND datum_id IN ("
                        + placeholderString + ")";
        
        return new MapperResult(sql, paramList);
    }
    
    /**
     * Get count of aggregation config info.
     * The default sql:
     * SELECT count(*) FROM config_info_aggr WHERE data_id = ? AND group_id = ? AND tenant_id = ?
     *
     * @param context The context of datum_id, isIn, data_id, group_id, tenant_id
     * @return The sql of getting count of aggregation config info.
     */
    default MapperResult aggrConfigInfoCount(MapperContext context) {
        final List<String> datumIds = (List<String>) context.getWhereParameter(FieldConstant.DATUM_ID);
        final Boolean isIn = (Boolean) context.getWhereParameter(FieldConstant.IS_IN);
        String dataId = (String) context.getWhereParameter(FieldConstant.DATA_ID);
        String group = (String) context.getWhereParameter(FieldConstant.GROUP_ID);
        String tenantTmp = (String) context.getWhereParameter(FieldConstant.TENANT_ID);
        
        List<Object> paramList = CollectionUtils.list(dataId, group, tenantTmp);
        
        StringBuilder sql = new StringBuilder(
                "SELECT count(*) FROM config_info_aggr WHERE data_id = ? AND group_id = ? AND tenant_id = ? AND datum_id");
        if (isIn) {
            sql.append(" IN (");
        } else {
            sql.append(" NOT IN (");
        }
        for (int i = 0; i < datumIds.size(); i++) {
            if (i > 0) {
                sql.append(", ");
            }
            sql.append('?');
            paramList.add(datumIds.get(i));
        }
        sql.append(')');
        
        return new MapperResult(sql.toString(), paramList);
    }
    
    /**
     * Find all data before aggregation under a dataId. It is guaranteed not to return NULL.
     * The default sql:
     * SELECT data_id,group_id,tenant_id,datum_id,app_name,content
     * FROM config_info_aggr WHERE data_id=? AND group_id=? AND tenant_id=? ORDER BY datum_id
     *
     * @param context The context of data_id, group_id, tenant_id
     * @return The sql of finding all data before aggregation under a dataId.
     */
    default MapperResult findConfigInfoAggrIsOrdered(MapperContext context) {
        String dataId = (String) context.getWhereParameter(FieldConstant.DATA_ID);
        String groupId = (String) context.getWhereParameter(FieldConstant.GROUP_ID);
        String tenantId = (String) context.getWhereParameter(FieldConstant.TENANT_ID);
        
        String sql = "SELECT data_id,group_id,tenant_id,datum_id,app_name,content FROM "
                + "config_info_aggr WHERE data_id = ? AND group_id = ? AND tenant_id = ? ORDER BY datum_id";
        List<Object> paramList = CollectionUtils.list(dataId, groupId, tenantId);
        
        return new MapperResult(sql, paramList);
    }
    
    /**
     * Query aggregation config info.
     * The default sql:
     * SELECT data_id,group_id,tenant_id,datum_id,app_name,content FROM config_info_aggr WHERE data_id=? AND
     * group_id=? AND tenant_id=? ORDER BY datum_id LIMIT startRow,pageSize
     *
     * @param context The context of startRow, pageSize, data_id, group_id, tenant_id
     * @return The sql of querying aggregation config info.
     */
    MapperResult findConfigInfoAggrByPageFetchRows(MapperContext context);
    
    /**
     * Find all aggregated data sets.
     * The default sql:
     * SELECT DISTINCT data_id, group_id, tenant_id FROM config_info_aggr
     *
     * @param context sql paramMap
     * @return The sql of finding all aggregated data sets.
     */
    default MapperResult findAllAggrGroupByDistinct(MapperContext context) {
        return new MapperResult("SELECT DISTINCT data_id, group_id, tenant_id FROM config_info_aggr",
                CollectionUtils.list());
    }
    
    /**
     * 获取返回表名.
     *
     * @return 表名
     */
    default String getTableName() {
        return TableConstant.CONFIG_INFO_AGGR;
    }
}
