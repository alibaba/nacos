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

package com.alibaba.nacos.plugin.datasource.impl.oracle;

import com.alibaba.nacos.common.utils.CollectionUtils;
import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.plugin.datasource.constants.DataSourceConstant;
import com.alibaba.nacos.plugin.datasource.constants.FieldConstant;
import com.alibaba.nacos.plugin.datasource.mapper.HistoryConfigInfoMapper;
import com.alibaba.nacos.plugin.datasource.model.MapperContext;
import com.alibaba.nacos.plugin.datasource.model.MapperResult;

import java.util.List;

/**
 * The oracle implementation of HistoryConfigInfoMapper.
 *
 * @author liam.fu
 **/
public class HistoryConfigInfoMapperByOracle extends AbstractMapperByOracle
    implements HistoryConfigInfoMapper {
    
    @Override
    public MapperResult removeConfigHistory(MapperContext context) {
        String sql =
            "DELETE FROM his_config_info WHERE ROWID IN (SELECT ROWID FROM his_config_info WHERE gmt_modified < ? FETCH FIRST ? ROWS ONLY)";
        return new MapperResult(sql,
            CollectionUtils.list(context.getWhereParameter(FieldConstant.START_TIME),
                context.getWhereParameter(FieldConstant.LIMIT_SIZE)));
    }
    
    @Override
    public MapperResult findDeletedConfig(MapperContext context) {
        return new MapperResult(
            "SELECT id, nid, data_id, group_id, app_name, content, md5, gmt_create, gmt_modified, src_user, src_ip, op_type, tenant_id, "
                + "publish_type, gray_name, ext_info, encrypted_data_key FROM his_config_info WHERE op_type = 'D' AND "
                + "publish_type = ? and gmt_modified >= ? and nid > ? order by nid fetch first ? rows only",
            CollectionUtils.list(context.getWhereParameter(FieldConstant.PUBLISH_TYPE),
                context.getWhereParameter(FieldConstant.START_TIME),
                context.getWhereParameter(FieldConstant.LAST_MAX_ID),
                context.getWhereParameter(FieldConstant.PAGE_SIZE)));
    }
    
    @Override
    public MapperResult pageFindConfigHistoryFetchRows(MapperContext context) {
        String sql =
            "SELECT nid,data_id,group_id,tenant_id,app_name,src_ip,src_user,op_type,ext_info,publish_type,gray_name,gmt_create,gmt_modified "
                + "FROM his_config_info "
                + "WHERE data_id = ? AND group_id = ? AND tenant_id = ? ORDER BY nid DESC OFFSET "
                + context.getStartRow() + " ROWS FETCH NEXT " + context.getPageSize()
                + " ROWS ONLY";
        return new MapperResult(sql,
            CollectionUtils.list(context.getWhereParameter(FieldConstant.DATA_ID),
                context.getWhereParameter(FieldConstant.GROUP_ID),
                context.getWhereParameter(FieldConstant.TENANT_ID)));
    }
    
    @Override
    public MapperResult getNextHistoryInfo(MapperContext context) {
        String sql =
            "SELECT nid,data_id,group_id,tenant_id,app_name,content,md5,src_user,src_ip,op_type,publish_type,"
                + "gray_name,ext_info,gmt_create,gmt_modified,encrypted_data_key FROM his_config_info "
                + "WHERE data_id = ? AND group_id = ? AND tenant_id = ? AND publish_type = ? "
                + (StringUtils.isBlank(context.getContextParameter(FieldConstant.GRAY_NAME)) ? ""
                    : "AND gray_name = ? ")
                + "AND nid > ? ORDER BY nid FETCH FIRST 1 ROWS ONLY";
        
        List<Object> paramList = CollectionUtils.list(
            context.getWhereParameter(FieldConstant.DATA_ID),
            context.getWhereParameter(FieldConstant.GROUP_ID),
            context.getWhereParameter(FieldConstant.TENANT_ID),
            context.getWhereParameter(FieldConstant.PUBLISH_TYPE),
            context.getWhereParameter(FieldConstant.NID));
        if (!StringUtils.isEmpty(context.getContextParameter(FieldConstant.GRAY_NAME))) {
            paramList.add(4, context.getWhereParameter(FieldConstant.GRAY_NAME));
        }
        
        return new MapperResult(sql, paramList);
    }
    
    @Override
    public String getDataSource() {
        return DataSourceConstant.ORACLE;
    }
}
