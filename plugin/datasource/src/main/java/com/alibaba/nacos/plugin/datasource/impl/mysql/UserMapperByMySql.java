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

import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.plugin.datasource.constants.DataSourceConstant;
import com.alibaba.nacos.plugin.datasource.constants.FieldConstant;
import com.alibaba.nacos.plugin.datasource.mapper.AbstractMapper;
import com.alibaba.nacos.plugin.datasource.mapper.UserMapper;
import com.alibaba.nacos.plugin.datasource.model.MapperContext;
import com.alibaba.nacos.plugin.datasource.model.MapperResult;

import java.util.ArrayList;
import java.util.List;

/**
 * The mysql implementation of UserMapper.
 *
 * @author hkm
 **/
public class UserMapperByMySql extends AbstractMapper implements UserMapper {
    
    @Override
    public String getDataSource() {
        return DataSourceConstant.MYSQL;
    }
    
    @Override
    public MapperResult getUsers(MapperContext context) {
        final String sqlFetchRows = "SELECT username,password FROM users ";
        final String username = context.getWhereParameter(FieldConstant.USER_NAME).toString();
        StringBuilder where = new StringBuilder(" WHERE 1 = 1 ");
        List<Object> paramList = new ArrayList<>();
        if (StringUtils.isNotBlank(username)) {
            where.append(" AND username = ? ");
            paramList.add(username);
        }
        
        String sql = sqlFetchRows + where + " LIMIT " + context.getStartRow() + "," + context.getPageSize();
        return new MapperResult(sql, paramList);
    }
}
