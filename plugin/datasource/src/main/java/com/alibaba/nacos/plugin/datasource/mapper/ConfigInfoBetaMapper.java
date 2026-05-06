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

import com.alibaba.nacos.plugin.datasource.constants.FieldConstant;
import com.alibaba.nacos.plugin.datasource.constants.TableConstant;
import com.alibaba.nacos.plugin.datasource.model.MapperContext;
import com.alibaba.nacos.plugin.datasource.model.MapperResult;

import java.util.ArrayList;
import java.util.List;

/**
 * The beta config info mapper.
 *
 * @author hyx
 **/

public interface ConfigInfoBetaMapper extends Mapper {
    
    /**
     * Update beta configuration information.
     * UPDATE config_info_beta SET content=?, md5=?, beta_ips=?, src_ip=?,src_user=?,gmt_modified=?,app_name=?
     * WHERE data_id=? AND group_id=? AND tenant_id=? AND (md5=? or md5 is null or md5='')
     *
     * @param context The context of content, md5, beta_ips, src_ip, src_user, gmt_modified, app_name,
     *                data_id, group_id, tenant_id, md5
     * @return The result of updating beta configuration information.
     */
    default MapperResult updateConfigInfo4BetaCas(MapperContext context) {
        final String sql = "UPDATE config_info_beta SET content = ?,md5 = ?,beta_ips = ?,src_ip = ?,src_user = ?,gmt_modified = "
                + getFunction("NOW()")
                + ",app_name = ? "
                + "WHERE data_id = ? AND group_id = ? AND tenant_id = ? AND (md5 = ? OR md5 is null OR md5 = '')";

        List<Object> paramList = new ArrayList<>();

        paramList.add(context.getUpdateParameter(FieldConstant.CONTENT));
        paramList.add(context.getUpdateParameter(FieldConstant.MD5));
        paramList.add(context.getUpdateParameter(FieldConstant.BETA_IPS));
        paramList.add(context.getUpdateParameter(FieldConstant.SRC_IP));
        paramList.add(context.getUpdateParameter(FieldConstant.SRC_USER));
        paramList.add(context.getUpdateParameter(FieldConstant.APP_NAME));
    
        paramList.add(context.getWhereParameter(FieldConstant.DATA_ID));
        paramList.add(context.getWhereParameter(FieldConstant.GROUP_ID));
        paramList.add(context.getWhereParameter(FieldConstant.TENANT_ID));
        paramList.add(context.getWhereParameter(FieldConstant.MD5));
        
        return new MapperResult(sql, paramList);
    }
    
    /**
     * Query all beta config info for dump task.
     * The default sql:
     * SELECT t.id,data_id,group_id,tenant_id,app_name,content,md5,gmt_modified,beta_ips,encrypted_data_key
     * FROM ( SELECT id FROM config_info_beta  ORDER BY id LIMIT startRow,pageSize  ) g, config_info_beta t WHERE g.id = t.id
     *
     * @param context The context of startRow, pageSize
     * @return The result of querying all beta config info for dump task.
     */
    MapperResult findAllConfigInfoBetaForDumpAllFetchRows(MapperContext context);
    
    /**
     * 获取返回表名.
     *
     * @return 表名
     */
    default String getTableName() {
        return TableConstant.CONFIG_INFO_BETA;
    }
}
