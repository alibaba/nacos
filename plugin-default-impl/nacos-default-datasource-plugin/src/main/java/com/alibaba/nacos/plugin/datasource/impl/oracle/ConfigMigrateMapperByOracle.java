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
import com.alibaba.nacos.plugin.datasource.constants.DataSourceConstant;
import com.alibaba.nacos.plugin.datasource.constants.FieldConstant;
import com.alibaba.nacos.plugin.datasource.mapper.ConfigMigrateMapper;
import com.alibaba.nacos.plugin.datasource.model.MapperContext;
import com.alibaba.nacos.plugin.datasource.model.MapperResult;

/**
 * The oracle implementation of ConfigMigrateMapper.
 *
 * @author liam.fu
 **/
public class ConfigMigrateMapperByOracle extends AbstractMapperByOracle implements ConfigMigrateMapper {

    @Override
    public MapperResult findConfigIdNeedInsertMigrate(MapperContext context) {
        String sql = "SELECT ci.id FROM config_info ci WHERE ci.tenant_id = '' AND NOT EXISTS "
                + " ( SELECT 1 FROM config_info ci2  WHERE ci2.data_id = ci.data_id AND ci2.group_id = ci.group_id AND ci2.tenant_id = 'public' )"
                + " AND ci.id > ?" + " ORDER BY ci.id FETCH FIRST ? ROWS ONLY";
        return new MapperResult(sql,
                CollectionUtils.list(context.getWhereParameter(FieldConstant.ID), context.getPageSize()));
    }

    @Override
    public MapperResult findConfigNeedUpdateMigrate(MapperContext context) {
        String sql = "SELECT ci.id, ci.data_id, ci.group_id, ci.tenant_id"
                + " FROM config_info ci WHERE ci.tenant_id = ? AND "
                + " (ci.src_user <> ? OR ci.src_user IS NULL) AND EXISTS "
                + " ( SELECT 1 FROM config_info ci2 WHERE ci2.data_id = ci.data_id AND ci2.group_id = ci.group_id "
                + " AND ci2.tenant_id = ? AND ci2.src_user = ? AND ci2.md5 <> ci.md5 "
                + " AND ci2.gmt_modified < ci.gmt_modified )"
                + " AND id > ?" + " ORDER BY id FETCH FIRST ? ROWS ONLY";
        return new MapperResult(sql,
                CollectionUtils.list(context.getWhereParameter(FieldConstant.SRC_TENANT),
                        context.getWhereParameter(FieldConstant.SRC_USER),
                        context.getWhereParameter(FieldConstant.TARGET_TENANT),
                        context.getWhereParameter(FieldConstant.SRC_USER), context.getWhereParameter(FieldConstant.ID),
                        context.getPageSize()));
    }

    @Override
    public MapperResult findConfigGrayNeedUpdateMigrate(MapperContext context) {
        String sql = "SELECT ci.id, ci.data_id, ci.group_id, ci.tenant_id, ci.gray_name "
                + " FROM config_info_gray ci WHERE ci.tenant_id = ? AND "
                + " (ci.src_user <> ? OR ci.src_user IS NULL) AND EXISTS "
                + " ( SELECT 1 FROM config_info_gray ci2 WHERE ci2.data_id = ci.data_id AND ci2.group_id = ci.group_id "
                + " AND ci2.gray_name = ci.gray_name AND ci2.tenant_id = ? AND ci2.src_user = ? AND ci2.md5 <> ci.md5 "
                + " AND ci2.gmt_modified < ci.gmt_modified )"
                + " AND ci.id > ?" + " ORDER BY ci.id FETCH FIRST ? ROWS ONLY";
        return new MapperResult(sql,
                CollectionUtils.list(context.getWhereParameter(FieldConstant.SRC_TENANT),
                        context.getWhereParameter(FieldConstant.SRC_USER),
                        context.getWhereParameter(FieldConstant.TARGET_TENANT),
                        context.getWhereParameter(FieldConstant.SRC_USER), context.getWhereParameter(FieldConstant.ID),
                        context.getPageSize()));
    }

    @Override
    public MapperResult findConfigGrayIdNeedInsertMigrate(MapperContext context) {
        String sql = "SELECT ci.id FROM config_info_gray ci WHERE ci.tenant_id = '' AND NOT EXISTS "
                + " ( SELECT 1 FROM config_info_gray ci2  WHERE ci2.data_id = ci.data_id AND ci2.group_id = ci.group_id"
                + " AND ci2.tenant_id = 'public' AND ci2.gray_name = ci.gray_name )" + " AND ci.id > ?"
                + " ORDER BY ci.id FETCH FIRST ? ROWS ONLY";
        return new MapperResult(sql,
                CollectionUtils.list(context.getWhereParameter(FieldConstant.ID), context.getPageSize()));
    }

    @Override
    public String getDataSource() {
        return DataSourceConstant.ORACLE;
    }
}
