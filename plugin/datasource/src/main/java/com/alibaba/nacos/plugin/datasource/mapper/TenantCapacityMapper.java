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
 * The tenant capacity info mapper.
 *
 * @author hyx
 **/

public interface TenantCapacityMapper extends Mapper {
    
    /**
     * Increment UsageWithDefaultQuotaLimit.
     * The default sql:
     * UPDATE tenant_capacity SET `usage` = `usage` + 1, gmt_modified = ? WHERE tenant_id = ? AND `usage` < ? AND quota
     * = 0
     *
     * @param context sql paramMap
     * @return The sql of increment UsageWithDefaultQuotaLimit.
     */
    default MapperResult incrementUsageWithDefaultQuotaLimit(MapperContext context) {
        return new MapperResult(
                "UPDATE tenant_capacity SET usage = usage + 1, gmt_modified = ? WHERE tenant_id = ? AND usage <"
                        + " ? AND quota = 0",
                CollectionUtils.list(context.getUpdateParameter(FieldConstant.GMT_MODIFIED),
                        context.getWhereParameter(FieldConstant.TENANT_ID),
                        context.getWhereParameter(FieldConstant.USAGE)));
    }
    
    /**
     * Increment UsageWithQuotaLimit.
     * The default sql:
     * UPDATE tenant_capacity SET `usage` = `usage` + 1, gmt_modified = ? WHERE tenant_id = ? AND `usage` < quota AND
     * quota != 0
     *
     * @param context sql paramMap
     * @return The sql of Increment UsageWithQuotaLimit.
     */
    default MapperResult incrementUsageWithQuotaLimit(MapperContext context) {
        return new MapperResult(
                "UPDATE tenant_capacity SET usage = usage + 1, gmt_modified = ? WHERE tenant_id = ? AND usage < "
                        + "quota AND quota != 0",
                CollectionUtils.list(context.getUpdateParameter(FieldConstant.GMT_MODIFIED),
                        context.getWhereParameter(FieldConstant.TENANT_ID)));
    }
    
    /**
     * Increment Usage.
     * The default sql:
     * UPDATE tenant_capacity SET `usage` = `usage` + 1, gmt_modified = ? WHERE tenant_id = ?
     *
     * @param context sql paramMap
     * @return The sql of increment UsageWithQuotaLimit.
     */
    default MapperResult incrementUsage(MapperContext context) {
        return new MapperResult("UPDATE tenant_capacity SET usage = usage + 1, gmt_modified = ? WHERE tenant_id = ?",
                CollectionUtils.list(context.getUpdateParameter(FieldConstant.GMT_MODIFIED),
                        context.getWhereParameter(FieldConstant.TENANT_ID)));
    }
    
    /**
     * DecrementUsage.
     * The default sql:
     * UPDATE tenant_capacity SET `usage` = `usage` - 1, gmt_modified = ? WHERE tenant_id = ? AND `usage` > 0
     *
     * @param context sql paramMap
     * @return The sql of decrementUsage.
     */
    default MapperResult decrementUsage(MapperContext context) {
        return new MapperResult(
                "UPDATE tenant_capacity SET usage = usage - 1, gmt_modified = ? WHERE tenant_id = ? AND usage > 0",
                CollectionUtils.list(context.getUpdateParameter(FieldConstant.GMT_MODIFIED),
                        context.getWhereParameter(FieldConstant.TENANT_ID)));
    }
    
    /**
     * Correct Usage.
     * The default sql:
     * UPDATE tenant_capacity SET `usage` = (SELECT count(*) FROM config_info WHERE tenant_id = ?), gmt_modified = ?
     * WHERE tenant_id = ?
     * `     * @param context sql paramMap`
     *
     * @return The sql of correcting Usage.
     */
    default MapperResult correctUsage(MapperContext context) {
        return new MapperResult(
                "UPDATE tenant_capacity SET usage = (SELECT count(*) FROM config_info WHERE tenant_id = ?), "
                        + "gmt_modified = ? WHERE tenant_id = ?",
                CollectionUtils.list(context.getWhereParameter(FieldConstant.TENANT_ID),
                        context.getUpdateParameter(FieldConstant.GMT_MODIFIED),
                        context.getWhereParameter(FieldConstant.TENANT_ID)));
    }
    
    /**
     * Get TenantCapacity List, only including id and tenantId value.
     * The default sql:
     * SELECT id, tenant_id FROM tenant_capacity WHERE id>? LIMIT ?
     *
     * @param context sql paramMap
     * @return The sql of getting TenantCapacity List, only including id and tenantId value.
     */
    MapperResult getCapacityList4CorrectUsage(MapperContext context);
    
    /**
     * Insert TenantCapacity.
     * The default sql:
     * INSERT INTO tenant_capacity (tenant_id, quota, `usage`, `max_size`, max_aggr_count, max_aggr_size,
     * gmt_create, gmt_modified) SELECT ?, ?, count(*), ?, ?, ?, ?, ? FROM config_info WHERE tenant_id=?;
     *
     * @param context sql paramMap
     * @return The sql of inserting TenantCapacity.
     */
    default MapperResult insertTenantCapacity(MapperContext context) {
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
                "INSERT INTO tenant_capacity (tenant_id, quota, usage, max_size, max_aggr_count, max_aggr_size, "
                        + "gmt_create, gmt_modified) SELECT ?, ?, count(*), ?, ?, ?, ?, ? FROM config_info WHERE tenant_id=?;",
                paramList);
    }
    
    /**
     * 获取返回表名.
     *
     * @return 表名
     */
    default String getTableName() {
        return TableConstant.TENANT_CAPACITY;
    }
}
