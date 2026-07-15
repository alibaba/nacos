/*
 * Copyright 1999-2026 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.plugin.datasource.manager;

import com.alibaba.nacos.api.plugin.PluginStateCheckerHolder;
import com.alibaba.nacos.plugin.datasource.dialect.DatabaseDialect;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DatabaseDialectManagerTest {
    
    private Map<String, DatabaseDialect> dialectMap;
    
    private Map<String, DatabaseDialect> originalDialects;
    
    @BeforeEach
    void setUp() throws Exception {
        dialectMap = getDialectMap();
        originalDialects = new HashMap<>(dialectMap);
        dialectMap.clear();
        PluginStateCheckerHolder.setInstance(null);
    }
    
    @AfterEach
    void tearDown() {
        dialectMap.clear();
        dialectMap.putAll(originalDialects);
        PluginStateCheckerHolder.setInstance(null);
    }
    
    @Test
    void testGetDialectAndAllDialects() {
        DatabaseDialect mysql = new TestDatabaseDialect("mysql");
        dialectMap.put("mysql", mysql);
        
        DatabaseDialectManager manager = DatabaseDialectManager.getInstance();
        
        assertSame(mysql, manager.getDialect("mysql"));
        assertThrows(UnsupportedOperationException.class, () -> manager.getAllDialects().clear());
    }
    
    @Test
    void testGetDialectWhenDisabled() {
        dialectMap.put("mysql", new TestDatabaseDialect("mysql"));
        PluginStateCheckerHolder.setInstance((pluginType, pluginName) -> false);
        
        assertThrows(IllegalStateException.class,
            () -> DatabaseDialectManager.getInstance().getDialect("mysql"));
    }
    
    @Test
    void testGetDialectFallbackAndNoEnabledFallback() {
        DatabaseDialect mysql = new TestDatabaseDialect("mysql");
        dialectMap.put("mysql", mysql);
        PluginStateCheckerHolder.setInstance(
            (pluginType, pluginName) -> "mysql".equals(pluginName) || "unknown".equals(pluginName));
        
        assertSame(mysql, DatabaseDialectManager.getInstance().getDialect("unknown"));
        
        PluginStateCheckerHolder
            .setInstance((pluginType, pluginName) -> "unknown".equals(pluginName));
        assertThrows(IllegalStateException.class,
            () -> DatabaseDialectManager.getInstance().getDialect("unknown"));
    }
    
    private Map<String, DatabaseDialect> getDialectMap() throws Exception {
        Field field = DatabaseDialectManager.class.getDeclaredField("SUPPORT_DIALECT_MAP");
        field.setAccessible(true);
        return (Map<String, DatabaseDialect>) field.get(null);
    }
    
    private static class TestDatabaseDialect implements DatabaseDialect {
        
        private final String type;
        
        private TestDatabaseDialect(String type) {
            this.type = type;
        }
        
        @Override
        public String getType() {
            return type;
        }
        
        @Override
        public int getPagePrevNum(int page, int pageSize) {
            return 0;
        }
        
        @Override
        public int getPageLastNum(int page, int pageSize) {
            return 0;
        }
        
        @Override
        public String getLimitTopSqlWithMark(String sql) {
            return sql;
        }
        
        @Override
        public String getLimitPageSqlWithMark(String sql) {
            return sql;
        }
        
        @Override
        public String getLimitPageSql(String sql, int pageNo, int pageSize) {
            return sql;
        }
        
        @Override
        public String getLimitPageSqlWithOffset(String sql, int startOffset, int pageSize) {
            return sql;
        }
        
        @Override
        public String[] getReturnPrimaryKeys() {
            return new String[] {"id"};
        }
        
        @Override
        public String getFunction(String functionName) {
            return functionName;
        }
    }
}
