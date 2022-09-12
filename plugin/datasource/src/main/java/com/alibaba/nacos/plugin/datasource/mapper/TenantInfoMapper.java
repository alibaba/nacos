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

/**
 * The tenant info mapper.
 *
 * @author hyx
 **/

public interface TenantInfoMapper extends Mapper {
    
    /**
     * Get the numbers of tenant information by tenant_id.
     * The default sql:
     * SELECT count(*) FROM tenant_info WHERE tenant_id = ?
     *
     * @return The sql of getting the numbers of tenant information by tenant_id.
     */
    String tenantInfoCountByTenantId();
    
    /**
     * Insert tenant info.
     * The default sql:
     * INSERT INTO tenant_info(kp,tenant_id,tenant_name,tenant_desc,create_source,gmt_create,gmt_modified) VALUES(?,?,?,?,?,?,?)
     *
     * @return The sql of inserting tenant info.
     */
    String insertTenantInfoAtomic();
    
    /**
     * Update tenantInfo showname.
     * The default sql:
     * UPDATE tenant_info SET tenant_name = ?, tenant_desc = ?, gmt_modified= ? WHERE kp=? AND tenant_id=?
     *
     * @return The sql of updating tenantInfo showname.
     */
    String updateTenantNameAtomic();
    
    /**
     * Query tenant info.
     * The default sql:
     * SELECT tenant_id,tenant_name,tenant_desc FROM tenant_info WHERE kp=?
     *
     * @return The sql of querying tenant info.
     */
    String findTenantByKp();
    
    /**
     * Query tenant info.
     * The default sql:
     * SELECT tenant_id,tenant_name,tenant_desc FROM tenant_info WHERE kp=? AND tenant_id=?
     *
     * @return The sql of querying tenant info.
     */
    String findTenantByKpAndTenantId();
    
    /**
     * Remote tenant info.
     * The default sql:
     * DELETE FROM tenant_info WHERE kp=? AND tenant_id=?
     *
     * @return The sql of remoting tenant info.
     */
    String removeTenantInfoAtomic();
}
