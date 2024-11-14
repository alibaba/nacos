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
import com.alibaba.nacos.plugin.datasource.mapper.ConfigInfoGrayMapper;
import com.alibaba.nacos.plugin.datasource.model.MapperContext;
import com.alibaba.nacos.plugin.datasource.model.MapperResult;

import java.util.Collections;

/**
 * The mysql implementation of ConfigInfoGrayMapper.
 *
 * @author rong
 **/

public class ConfigInfoGrayMapperByMySql extends AbstractMapperByMysql implements ConfigInfoGrayMapper {
    
    @Override
    public MapperResult findAllConfigInfoGrayForDumpAllFetchRows(MapperContext context) {
        String sql = " SELECT t.id,data_id,group_id,tenant_id,gray_name,gray_rule,app_name,content,md5,gmt_modified "
                + " FROM (  SELECT id FROM config_info_gray  ORDER BY id LIMIT " + context.getStartRow() + ","
                + context.getPageSize() + " ) " + "g, config_info_gray t  WHERE g.id = t.id  ";
        return new MapperResult(sql, Collections.emptyList());
    }
    
    @Override
    public String getDataSource() {
        return DataSourceConstant.MYSQL;
    }
}
