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
import com.alibaba.nacos.plugin.datasource.mapper.TenantCapacityMapper;

/**
 * The mysql implementation of TenantCapacityMapper.
 *
 * @author hyx
 **/

public class TenantCapacityMapperByMySql extends AbstractMapper implements TenantCapacityMapper {
    
    @Override
    public String getTableName() {
        return TableConstant.TENANT_CAPACITY;
    }
    
    @Override
    public String getDataSource() {
        return DataSourceConstant.MYSQL;
    }
    
    @Override
    public String incrementUsageWithDefaultQuotaLimit() {
        return "UPDATE tenant_capacity SET `usage` = `usage` + 1, gmt_modified = ? WHERE tenant_id = ? AND `usage` <"
                + " ? AND quota = 0";
    }
    
    @Override
    public String incrementUsageWithQuotaLimit() {
        return "UPDATE tenant_capacity SET `usage` = `usage` + 1, gmt_modified = ? WHERE tenant_id = ? AND `usage` < "
                + "quota AND quota != 0";
    }
    
    @Override
    public String incrementUsage() {
        return "UPDATE tenant_capacity SET `usage` = `usage` + 1, gmt_modified = ? WHERE tenant_id = ?";
    }
    
    @Override
    public String decrementUsage() {
        return "UPDATE tenant_capacity SET `usage` = `usage` - 1, gmt_modified = ? WHERE tenant_id = ? AND `usage` > 0";
    }
    
    @Override
    public String correctUsage() {
        return "UPDATE tenant_capacity SET `usage` = (SELECT count(*) FROM config_info WHERE tenant_id = ?), "
                + "gmt_modified = ? WHERE tenant_id = ?";
    }
    
    @Override
    public String getCapacityList4CorrectUsage() {
        return "SELECT id, tenant_id FROM tenant_capacity WHERE id>? LIMIT ?";
    }
    
    @Override
    public String insertTenantCapacity() {
        return "INSERT INTO tenant_capacity (tenant_id, quota, `usage`, `max_size`, max_aggr_count, max_aggr_size, "
                + "gmt_create, gmt_modified) SELECT ?, ?, count(*), ?, ?, ?, ?, ? FROM config_info WHERE tenant_id=?;";
    }
}
