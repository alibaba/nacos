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
 * The config tag info mapper.
 *
 * @author hyx
 **/

public interface ConfigInfoTagMapper extends Mapper {
    
    /**
     * Update tag configuration information.
     * The default sql:
     * UPDATE config_info_tag SET content=?, md5 = ?, src_ip=?,src_user=?,gmt_modified=?,app_name=? WHERE
     * data_id=? AND group_id=? AND tenant_id=? AND tag_id=? AND (md5=? or md5 is null or md5='')
     *
     * @param context sql paramMap
     * @return The sql of updating tag configuration information.
     */
    default MapperResult updateConfigInfo4TagCas(MapperContext context) {
        Object content = context.getUpdateParameter(FieldConstant.CONTENT);
        Object md5 = context.getUpdateParameter(FieldConstant.MD5);
        Object srcIp = context.getUpdateParameter(FieldConstant.SRC_IP);
        Object srcUser = context.getUpdateParameter(FieldConstant.SRC_USER);
        Object gmtModified = context.getUpdateParameter(FieldConstant.GMT_MODIFIED);
        Object appName = context.getUpdateParameter(FieldConstant.APP_NAME);
        
        Object dataId = context.getWhereParameter(FieldConstant.DATA_ID);
        Object groupId = context.getWhereParameter(FieldConstant.GROUP_ID);
        Object tenantId = context.getWhereParameter(FieldConstant.TENANT_ID);
        Object tagId = context.getWhereParameter(FieldConstant.TAG_ID);
        Object oldMd5 = context.getWhereParameter(FieldConstant.MD5);
        String sql =
                "UPDATE config_info_tag SET content = ?, md5 = ?, src_ip = ?,src_user = ?,gmt_modified = ?,app_name = ? "
                        + "WHERE data_id = ? AND group_id = ? AND tenant_id = ? AND tag_id = ? AND (md5 = ? OR md5 IS NULL OR md5 = '')";
        return new MapperResult(sql,
                CollectionUtils.list(content, md5, srcIp, srcUser, gmtModified, appName, dataId, groupId, tenantId,
                        tagId, oldMd5));
    }
    
    /**
     * Query all tag config info for dump task.
     * The default sql:
     * SELECT t.id,data_id,group_id,tenant_id,tag_id,app_name,content,md5,gmt_modified
     * FROM (  SELECT id FROM config_info_tag  ORDER BY id LIMIT startRow,pageSize ) g,
     * config_info_tag t  WHERE g.id = t.id
     *
     * @param context The start index.
     * @return The sql of querying all tag config info for dump task.
     */
    MapperResult findAllConfigInfoTagForDumpAllFetchRows(MapperContext context);
    
    /**
     * 获取返回表名.
     *
     * @return 表名
     */
    default String getTableName() {
        return TableConstant.CONFIG_INFO_TAG;
    }
}
