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
import com.alibaba.nacos.plugin.datasource.constants.FieldConstant;
import com.alibaba.nacos.plugin.datasource.constants.TableConstant;
import com.alibaba.nacos.plugin.datasource.model.MapperContext;
import com.alibaba.nacos.plugin.datasource.model.MapperResult;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class UserMapperByDerbyTest {
    
    private UserMapperByDerby userMapperByDerby;
    
    int startRow = 0;
    
    int pageSize = 5;
    
    String username = "nacos";
    
    MapperContext context;
    
    @Before
    public void setUp() throws Exception {
        userMapperByDerby = new UserMapperByDerby();
        context = new MapperContext(startRow, pageSize);
    }
    
    @Test
    public void testGetTableName() {
        String tableName = userMapperByDerby.getTableName();
        Assert.assertEquals(tableName, TableConstant.USERS);
    }
    
    @Test
    public void testGetDataSource() {
        String dataSource = userMapperByDerby.getDataSource();
        Assert.assertEquals(dataSource, DataSourceConstant.DERBY);
    }
    
    @Test
    public void testGetUsers() {
        context = new MapperContext(startRow, pageSize);
        context.putWhereParameter(FieldConstant.USER_NAME, username);
        MapperResult mapperResult = userMapperByDerby.getUsers(context);
        
        Assert.assertEquals(mapperResult.getSql(),
                "SELECT username,password FROM users  " + "WHERE 1 = 1  AND username = ?" + "  OFFSET " + startRow
                        + " ROWS FETCH NEXT " + pageSize + " ROWS ONLY");
        Assert.assertArrayEquals(mapperResult.getParamList().toArray(), new Object[] {username});
    }
}