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

import java.util.Collections;

/**
 * The history config info mapper.
 *
 * @author hyx
 **/

public interface HistoryConfigInfoMapper extends Mapper {
    
    /**
     * Delete data before startTime. The default sql: DELETE FROM his_config_info WHERE gmt_modified < ? LIMIT ?
     *
     * @param context sql paramMap
     * @return The sql of deleting data before startTime.
     */
    MapperResult removeConfigHistory(MapperContext context);
    
    /**
     * Get the number of configurations before the specified time. The default sql: SELECT count(*) FROM his_config_info
     * WHERE gmt_modified < ?
     *
     * @param context sql paramMap
     * @return The sql of getting the number of configurations before the specified time.
     */
    default MapperResult findConfigHistoryCountByTime(MapperContext context) {
        return new MapperResult("SELECT count(*) FROM his_config_info WHERE gmt_modified < ?",
                Collections.singletonList(context.getWhereParameter(FieldConstant.START_TIME)));
    }
    
    /**
     * Query deleted config. The default sql: SELECT DISTINCT data_id, group_id, tenant_id FROM his_config_info WHERE
     * op_type = 'D' AND gmt_modified >=? AND gmt_modified <= ?
     *
     * @param context sql paramMap
     * @return The sql of querying deleted config.
     */
    default MapperResult findDeletedConfig(MapperContext context) {
        return new MapperResult(
                "SELECT id, nid, data_id, group_id, app_name, content, md5, gmt_create, gmt_modified, src_user, src_ip, op_type, tenant_id, "
                        + "publish_type, ext_info, encrypted_data_key FROM his_config_info WHERE op_type = 'D' AND "
                        + "publish_type = ? and gmt_modified >= ? and nid > ? order by nid limit ? ",
                CollectionUtils.list(context.getWhereParameter(FieldConstant.PUBLISH_TYPE),
                        context.getWhereParameter(FieldConstant.START_TIME),
                        context.getWhereParameter(FieldConstant.LAST_MAX_ID),
                        context.getWhereParameter(FieldConstant.PAGE_SIZE)));
    }
    
    /**
     * List configuration history change record. The default sql: SELECT
     * nid,data_id,group_id,tenant_id,app_name,src_ip,src_user,op_type,gmt_create,gmt_modified FROM his_config_info
     * WHERE data_id = ? AND group_id = ? AND tenant_id = ? ORDER BY nid DESC
     *
     * @param context sql paramMap
     * @return The sql of listing configuration history change record.
     */
    default MapperResult findConfigHistoryFetchRows(MapperContext context) {
        return new MapperResult(
                "SELECT nid,data_id,group_id,tenant_id,app_name,src_ip,src_user,op_type,gmt_create,gmt_modified FROM his_config_info "
                        + "WHERE data_id = ? AND group_id = ? AND tenant_id = ? ORDER BY nid DESC",
                CollectionUtils.list(context.getWhereParameter(FieldConstant.DATA_ID),
                        context.getWhereParameter(FieldConstant.GROUP_ID),
                        context.getWhereParameter(FieldConstant.TENANT_ID)));
    }
    
    /**
     * page search List configuration history. SELECT
     * nid,data_id,group_id,tenant_id,app_name,src_ip,src_user,op_type,gmt_create,gmt_modified FROM his_config_info
     * WHERE data_id = ? AND group_id = ? AND tenant_id = ? ORDER BY nid DESC limit ?,?
     *
     * @param context pageNo
     * @return
     */
    MapperResult pageFindConfigHistoryFetchRows(MapperContext context);
    
    /**
     * Get previous config detail. The default sql: SELECT
     * nid,data_id,group_id,tenant_id,app_name,content,md5,src_user,src_ip,op_type,gmt_create,gmt_modified FROM
     * his_config_info WHERE nid = (SELECT max(nid) FROM his_config_info WHERE id = ?)
     *
     * @param context sql paramMap
     * @return The sql of getting previous config detail.
     */
    default MapperResult detailPreviousConfigHistory(MapperContext context) {
        return new MapperResult(
                "SELECT nid,data_id,group_id,tenant_id,app_name,content,md5,src_user,src_ip,op_type,publish_type,ext_info,gmt_create"
                        + ",gmt_modified,encrypted_data_key FROM his_config_info WHERE nid = (SELECT max(nid) FROM his_config_info WHERE id = ?)",
                Collections.singletonList(context.getWhereParameter(FieldConstant.ID)));
    }
    
    /**
     * 获取返回表名.
     *
     * @return 表名
     */
    default String getTableName() {
        return TableConstant.HIS_CONFIG_INFO;
    }
}
