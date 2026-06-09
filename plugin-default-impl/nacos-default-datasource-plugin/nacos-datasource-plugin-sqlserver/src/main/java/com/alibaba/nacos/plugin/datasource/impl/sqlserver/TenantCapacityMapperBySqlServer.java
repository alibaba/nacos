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

package com.alibaba.nacos.plugin.datasource.impl.sqlserver;

import com.alibaba.nacos.common.utils.CollectionUtils;
import com.alibaba.nacos.plugin.datasource.constants.DatabaseTypeConstant;
import com.alibaba.nacos.plugin.datasource.constants.FieldConstant;
import com.alibaba.nacos.plugin.datasource.mapper.TenantCapacityMapper;
import com.alibaba.nacos.plugin.datasource.model.MapperContext;
import com.alibaba.nacos.plugin.datasource.model.MapperResult;

import java.util.ArrayList;
import java.util.List;

/**
 * The SQL Server implementation of TenantCapacityMapper.
 *
 * @author ThinkGem
 **/
public class TenantCapacityMapperBySqlServer extends AbstractMapperBySqlServer implements TenantCapacityMapper {
    
    @Override
    public MapperResult select(MapperContext context) {
        String sql = "select id,tenant_id,quota,`usage`,max_size,max_aggr_count,max_aggr_size "
                + " from tenant_capacity where tenant_id = ?";
        return new MapperResult(sql, CollectionUtils.list(context.getWhereParameter(FieldConstant.TENANT_ID)));
    }
    
    @Override
    public MapperResult incrementUsageWithDefaultQuotaLimit(MapperContext context) {
        String sql = "update tenant_capacity set `usage` = `usage` + 1, gmt_modified = ? "
                + " where tenant_id = ? and `usage` < ? and quota = 0";
        return new MapperResult(sql, CollectionUtils.list(context.getWhereParameter(FieldConstant.GMT_MODIFIED),
                context.getWhereParameter(FieldConstant.TENANT_ID), context.getWhereParameter(FieldConstant.USAGE)));
    }
    
    @Override
    public MapperResult incrementUsageWithQuotaLimit(MapperContext context) {
        String sql = "update tenant_capacity set `usage` = `usage` + 1, gmt_modified = ? "
                + " where tenant_id = ? and `usage` < quota and quota != 0";
        return new MapperResult(sql, CollectionUtils.list(context.getUpdateParameter(FieldConstant.GMT_MODIFIED),
                context.getWhereParameter(FieldConstant.TENANT_ID)));
    }
    
    @Override
    public MapperResult incrementUsage(MapperContext context) {
        String sql = "UPDATE tenant_capacity SET `usage` = `usage` + 1, gmt_modified = ? WHERE tenant_id = ?";
        return new MapperResult(sql, CollectionUtils.list(context.getUpdateParameter(FieldConstant.GMT_MODIFIED),
                context.getWhereParameter(FieldConstant.TENANT_ID)));
    }
    
    @Override
    public MapperResult decrementUsage(MapperContext context) {
        String sql = "UPDATE tenant_capacity SET `usage` = `usage` - 1, gmt_modified = ? WHERE tenant_id = ? AND `usage` > 0";
        return new MapperResult(sql, CollectionUtils.list(context.getUpdateParameter(FieldConstant.GMT_MODIFIED),
                context.getWhereParameter(FieldConstant.TENANT_ID)));
    }
    
    @Override
    public MapperResult correctUsage(MapperContext context) {
        String sql = "UPDATE tenant_capacity SET `usage` = (SELECT count(*) FROM config_info WHERE tenant_id = ?), "
                + "gmt_modified = ? WHERE tenant_id = ?";
        return new MapperResult(sql, CollectionUtils.list(context.getWhereParameter(FieldConstant.TENANT_ID),
                context.getUpdateParameter(FieldConstant.GMT_MODIFIED),
                context.getWhereParameter(FieldConstant.TENANT_ID)));
    }
    
    @Override
    public MapperResult getCapacityList4CorrectUsage(MapperContext context) {
        String sql = "SELECT id, tenant_id FROM tenant_capacity WHERE id > ? ORDER BY id "
                + " OFFSET 0 ROWS FETCH NEXT ? ROWS ONLY ";
        return new MapperResult(sql, CollectionUtils.list(context.getWhereParameter(FieldConstant.ID),
                context.getWhereParameter(FieldConstant.LIMIT_SIZE)));
    }
    
    @Override
    public MapperResult insertTenantCapacity(MapperContext context) {
        List<Object> paramList = new ArrayList<>();
        paramList.add(context.getUpdateParameter(FieldConstant.TENANT_ID));
        paramList.add(context.getUpdateParameter(FieldConstant.QUOTA));
        paramList.add(context.getUpdateParameter(FieldConstant.MAX_SIZE));
        paramList.add(context.getUpdateParameter(FieldConstant.MAX_AGGR_COUNT));
        paramList.add(context.getUpdateParameter(FieldConstant.MAX_AGGR_SIZE));
        paramList.add(context.getUpdateParameter(FieldConstant.GMT_CREATE));
        paramList.add(context.getUpdateParameter(FieldConstant.GMT_MODIFIED));
        paramList.add(context.getWhereParameter(FieldConstant.TENANT_ID));
        
        return new MapperResult(
                "INSERT INTO tenant_capacity (tenant_id, quota, `usage`, max_size, max_aggr_count, max_aggr_size, "
                        + "gmt_create, gmt_modified) SELECT ?, ?, count(*), ?, ?, ?, ?, ? FROM config_info WHERE tenant_id=?",
                paramList);
    }
    
    @Override
    public String getDataSource() {
        return DatabaseTypeConstant.SQLSERVER;
    }
    
}
