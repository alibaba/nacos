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

package com.alibaba.nacos.plugin.datasource.impl.derby;

import com.alibaba.nacos.plugin.datasource.constants.DataSourceConstant;
import com.alibaba.nacos.plugin.datasource.mapper.AbstractMapper;
import com.alibaba.nacos.plugin.datasource.mapper.HistoryConfigInfoMapper;

/**
 * The derby implementation of ConfigInfoMapper.
 *
 * @author hyx
 **/

public class HistoryConfigInfoMapperByDerby extends AbstractMapper implements HistoryConfigInfoMapper {
    
    @Override
    public String removeConfigHistory() {
        return "DELETE FROM his_config_info WHERE id IN( "
                + "SELECT id FROM his_config_info WHERE gmt_modified < ? OFFSET 0 ROWS FETCH NEXT ? ROWS ONLY)";
    }

    @Override
    public String pageFindConfigHistoryFetchRows(int pageNo, int pageSize) {
        final int offset = (pageNo - 1) * pageSize;
        final int limit = pageSize;
        return "SELECT nid,data_id,group_id,tenant_id,app_name,src_ip,src_user,op_type,gmt_create,gmt_modified FROM his_config_info "
                + "WHERE data_id = ? AND group_id = ? AND tenant_id = ? ORDER BY nid DESC  OFFSET "
                + offset + " ROWS FETCH NEXT " + limit + " ROWS ONLY";
    }

    @Override
    public String getDataSource() {
        return DataSourceConstant.DERBY;
    }
}
