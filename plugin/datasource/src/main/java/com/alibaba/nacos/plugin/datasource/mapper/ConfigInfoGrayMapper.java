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

/**
 * The config gray info mapper.
 *
 * @author rong
 **/

public interface ConfigInfoGrayMapper extends Mapper {
    
    /**
     * Update gray configuration information. The default sql: UPDATE config_info_gray SET content=?, md5 = ?,
     * src_ip=?,src_user=?,gmt_modified=?,app_name=?,gray_rule=? WHERE data_id=? AND group_id=? AND tenant_id=? AND
     * gray_name=? AND (md5=? or md5 is null or md5='')
     *
     * @param context sql paramMap
     * @return The sql of updating gray configuration information.
     */
    default MapperResult updateConfigInfo4GrayCas(MapperContext context) {
        Object content = context.getUpdateParameter(FieldConstant.CONTENT);
        Object md5 = context.getUpdateParameter(FieldConstant.MD5);
        Object srcIp = context.getUpdateParameter(FieldConstant.SRC_IP);
        Object srcUser = context.getUpdateParameter(FieldConstant.SRC_USER);
        Object gmtModified = context.getUpdateParameter(FieldConstant.GMT_MODIFIED);
        Object appName = context.getUpdateParameter(FieldConstant.APP_NAME);
        
        Object dataId = context.getWhereParameter(FieldConstant.DATA_ID);
        Object groupId = context.getWhereParameter(FieldConstant.GROUP_ID);
        Object tenantId = context.getWhereParameter(FieldConstant.TENANT_ID);
        Object grayName = context.getWhereParameter(FieldConstant.GRAY_NAME);
        Object grayRule = context.getWhereParameter(FieldConstant.GRAY_RULE);
        Object oldMd5 = context.getWhereParameter(FieldConstant.MD5);
        String sql = "UPDATE config_info_gray SET content = ?, md5 = ?, src_ip = ?,src_user = ?,gmt_modified = "
                + getFunction("NOW()") + ",app_name = ?, gray_rule = ?"
                + "WHERE data_id = ? AND group_id = ? AND tenant_id = ? AND gray_name = ? AND (md5 = ? OR md5 IS NULL OR md5 = '')";
        return new MapperResult(sql,
                CollectionUtils.list(content, md5, srcIp, srcUser, appName, grayRule, dataId, groupId,
                        tenantId, grayName, oldMd5));
    }
    
    /**
     * Query change config. <br/>The default sql: SELECT data_id, group_id, tenant_id, app_name, content,
     * gmt_modified,encrypted_data_key FROM config_info WHERE gmt_modified >=? AND gmt_modified <= ?
     *
     * @param context sql paramMap
     * @return The sql of querying change config.
     */
    default MapperResult findChangeConfig(MapperContext context) {
        String sql =
                "SELECT id, data_id, group_id, tenant_id, app_name,content,gray_name,gray_rule,md5, gmt_modified, encrypted_data_key "
                        + "FROM config_info_gray WHERE " + "gmt_modified >= ? and id > ? order by id  limit ? ";
        return new MapperResult(sql, CollectionUtils.list(context.getWhereParameter(FieldConstant.START_TIME),
                context.getWhereParameter(FieldConstant.LAST_MAX_ID),
                context.getWhereParameter(FieldConstant.PAGE_SIZE)));
    }
    
    /**
     * Query all gray config info for dump task. The default sql: SELECT
     * t.id,data_id,group_id,tenant_id,gray_name,app_name,content,md5,gmt_modified FROM (  SELECT id FROM
     * config_info_gray  ORDER BY id LIMIT startRow,pageSize ) g, config_info_gray t  WHERE g.id = t.id
     *
     * @param context The start index.
     * @return The sql of querying all gray config info for dump task.
     */
    MapperResult findAllConfigInfoGrayForDumpAllFetchRows(MapperContext context);
    
    /**
     * 获取返回表名.
     *
     * @return 表名
     */
    default String getTableName() {
        return TableConstant.CONFIG_INFO_GRAY;
    }
}
