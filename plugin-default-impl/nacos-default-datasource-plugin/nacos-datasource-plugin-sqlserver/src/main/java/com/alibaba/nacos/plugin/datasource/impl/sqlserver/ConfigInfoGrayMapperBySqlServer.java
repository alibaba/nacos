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
import com.alibaba.nacos.plugin.datasource.mapper.ConfigInfoGrayMapper;
import com.alibaba.nacos.plugin.datasource.model.MapperContext;
import com.alibaba.nacos.plugin.datasource.model.MapperResult;

import java.util.Collections;

/**
 * The SQL Server implementation of ConfigInfoGrayMapper.
 *
 * @author ThinkGem
 **/
public class ConfigInfoGrayMapperBySqlServer extends AbstractMapperBySqlServer implements ConfigInfoGrayMapper {
    
    /**
     * Find changed gray config.
     *
     * @param context the mapper context
     * @return the mapper result
     */
    @Override
    public MapperResult findChangeConfig(MapperContext context) {
        String sql = getLimitTopSqlWithMark(
                "SELECT id, data_id, group_id, tenant_id, app_name,content,gray_name,gray_rule,md5, gmt_modified, encrypted_data_key "
                        + "FROM config_info_gray WHERE " + "gmt_modified >= ? and id > ? ");
        return new MapperResult(sql, CollectionUtils.list(context.getWhereParameter(FieldConstant.START_TIME),
                context.getWhereParameter(FieldConstant.LAST_MAX_ID),
                context.getWhereParameter(FieldConstant.PAGE_SIZE)));
    }
    
    /**
     * Find all gray config info for dump all with pagination.
     *
     * @param context the mapper context
     * @return the mapper result
     */
    @Override
    public MapperResult findAllConfigInfoGrayForDumpAllFetchRows(MapperContext context) {
        String sql = getLimitPageSqlWithOffset(
                "SELECT id,data_id,group_id,tenant_id,gray_name,app_name,content,md5,gmt_modified "
                        + " from config_info_gray ", context.getStartRow(), context.getPageSize());
        
        return new MapperResult(sql, Collections.emptyList());
    }
    
    private String getLimitPageSqlWithOffset(String sql, int startOffset, int pageSize) {
        return getDatabaseDialect().getLimitPageSqlWithOffset(sql, startOffset, pageSize);
    }
    
    private String getLimitTopSqlWithMark(String sql) {
        return getDatabaseDialect().getLimitTopSqlWithMark(sql);
    }
    
    @Override
    public String getDataSource() {
        return DatabaseTypeConstant.SQLSERVER;
    }
}
