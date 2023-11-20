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

package com.alibaba.nacos.plugin.datasource.proxy;

import com.alibaba.nacos.plugin.datasource.mapper.Mapper;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.lang.reflect.Field;
import java.util.List;

public class MapperProxyTest {
    
    private MapperProxy mapperProxy;
    
    @Before
    public void setup() {
        this.mapperProxy = new MapperProxy();
    }
    
    @Test
    public void testCreateProxy() {
        Mapper mapper = new Mapper() {
            @Override
            public String select(List<String> columns, List<String> where) {
                return "select-test";
            }
    
            @Override
            public String insert(List<String> columns) {
                return "insert-test";
            }
    
            @Override
            public String update(List<String> columns, List<String> where) {
                return "update-test";
            }
    
            @Override
            public String delete(List<String> params) {
                return "delete-test";
            }
    
            @Override
            public String count(List<String> where) {
                return "count-test";
            }
    
            @Override
            public String getTableName() {
                return "test";
            }
    
            @Override
            public String getDataSource() {
                return "test";
            }
    
            @Override
            public String[] getPrimaryKeyGeneratedKeys() {
                return new String[0];
            }
        };
        Mapper proxy = mapperProxy.createProxy(mapper);
        try {
            Field field = proxy.getClass().getSuperclass().getDeclaredField("h");
            field.setAccessible(true);
            MapperProxy mapperProxy = (MapperProxy) field.get(proxy);
            Field mapperField = mapperProxy.getClass().getDeclaredField("mapper");
            mapperField.setAccessible(true);
            Class<?> clazz = mapperField.getDeclaringClass();
            Assert.assertEquals(MapperProxy.class, clazz);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            e.printStackTrace();
        }
    }
}
