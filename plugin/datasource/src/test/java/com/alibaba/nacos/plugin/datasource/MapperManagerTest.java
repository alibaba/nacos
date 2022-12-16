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

package com.alibaba.nacos.plugin.datasource;

import com.alibaba.nacos.plugin.datasource.constants.DataSourceConstant;
import com.alibaba.nacos.plugin.datasource.mapper.AbstractMapper;
import com.alibaba.nacos.plugin.datasource.mapper.Mapper;
import org.junit.Assert;
import org.junit.Test;

import java.lang.reflect.Field;
import java.util.Map;

public class MapperManagerTest {
    
    @Test
    public void testInstance() {
        MapperManager instance = MapperManager.instance(false);
        Assert.assertNotNull(instance);
    }
    
    @Test
    public void testLoadInitial() throws NoSuchFieldException, IllegalAccessException {
        MapperManager instance = MapperManager.instance(false);
        instance.loadInitial();
        Class<MapperManager> mapperManagerClass = MapperManager.class;
        Field declaredField = mapperManagerClass.getDeclaredField("MAPPER_SPI_MAP");
        declaredField.setAccessible(true);
        Map<String, Map<String, Mapper>> map = (Map<String, Map<String, Mapper>>) declaredField.get(instance);
        Assert.assertEquals(2, map.size());
    }
    
    @Test
    public void testJoin() {
        MapperManager.join(new AbstractMapper() {
            @Override
            public String getTableName() {
                return "test";
            }
            
            @Override
            public String getDataSource() {
                return DataSourceConstant.MYSQL;
            }
        });
        MapperManager instance = MapperManager.instance(false);
        Mapper mapper = instance.findMapper(DataSourceConstant.MYSQL, "test");
        Assert.assertNotNull(mapper);
    }
    
    @Test
    public void testFindMapper() {
        testJoin();
        MapperManager instance = MapperManager.instance(false);
        Mapper mapper = instance.findMapper(DataSourceConstant.MYSQL, "test");
        Assert.assertNotNull(mapper);
    }
}