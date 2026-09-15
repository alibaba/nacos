/*
 * Copyright 1999-2023 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.persistence.datasource;

import com.alibaba.nacos.api.plugin.PluginStateCheckerHolder;
import com.alibaba.nacos.plugin.datasource.dialect.DatabaseDialect;
import com.alibaba.nacos.plugin.datasource.manager.DatabaseDialectManager;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class ExternalDataSourcePropertiesTest {
    
    @SuppressWarnings("checkstyle:linelength")
    public static final String JDBC_URL =
        "jdbc:mysql://127.0.0.1:3306/nacos_devtest?characterEncoding=utf8&connectTimeout=1000&socketTimeout=3000&autoReconnect=true&useUnicode=true&useSSL=false&serverTimezone=UTC";
    
    public static final String PASSWORD = "nacos";
    
    public static final String USERNAME = "nacos_devtest";
    
    private static final String MYSQL_COMPAT_DRIVER = "com.mysql.cj.jdbc.Driver";
    
    private static final String TEST_DIALECT_TYPE = "test-dialect";
    
    /**
     * Hikari loads the driver class eagerly, so the test dialect must return a class that exists on
     * the test classpath. Derby is a provided dependency of this module.
     */
    private static final String TEST_DIALECT_DRIVER = "org.apache.derby.jdbc.EmbeddedDriver";
    
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
    void driverClassNameUsesDialectDefaultWhenPoolConfigIsBlank() {
        dialectMap.put(TEST_DIALECT_TYPE, new TestDatabaseDialect(TEST_DIALECT_DRIVER));
        MockEnvironment environment = mysqlEnvironment();
        environment.setProperty("nacos.plugin.datasource-dialect.type", TEST_DIALECT_TYPE);
        
        List<HikariDataSource> dataSources =
            new ExternalDataSourceProperties().build(environment, dataSource -> {
            });
        
        assertEquals(TEST_DIALECT_DRIVER, dataSources.get(0).getDriverClassName());
    }
    
    @Test
    void driverClassNameUsesDialectDefaultSelectedByLegacyPlatformProperty() {
        dialectMap.put(TEST_DIALECT_TYPE, new TestDatabaseDialect(TEST_DIALECT_DRIVER));
        MockEnvironment environment = mysqlEnvironment();
        environment.setProperty("spring.sql.init.platform", TEST_DIALECT_TYPE);
        
        List<HikariDataSource> dataSources =
            new ExternalDataSourceProperties().build(environment, dataSource -> {
            });
        
        assertEquals(TEST_DIALECT_DRIVER, dataSources.get(0).getDriverClassName());
    }
    
    @Test
    void explicitPoolConfigDriverClassNameOverridesDialectDefault() {
        dialectMap.put(TEST_DIALECT_TYPE, new TestDatabaseDialect(TEST_DIALECT_DRIVER));
        MockEnvironment environment = mysqlEnvironment();
        environment.setProperty("nacos.plugin.datasource-dialect.type", TEST_DIALECT_TYPE);
        environment.setProperty("nacos.plugin.datasource.db.pool.config.driver-class-name",
            MYSQL_COMPAT_DRIVER);
        
        List<HikariDataSource> dataSources =
            new ExternalDataSourceProperties().build(environment, dataSource -> {
            });
        
        assertEquals(MYSQL_COMPAT_DRIVER, dataSources.get(0).getDriverClassName());
    }
    
    @Test
    void driverClassNameFallsBackToMysqlWhenDialectProvidesNone() {
        dialectMap.put(TEST_DIALECT_TYPE, new TestDatabaseDialect(null));
        MockEnvironment environment = mysqlEnvironment();
        environment.setProperty("nacos.plugin.datasource-dialect.type", TEST_DIALECT_TYPE);
        
        List<HikariDataSource> dataSources =
            new ExternalDataSourceProperties().build(environment, dataSource -> {
            });
        
        assertEquals(MYSQL_COMPAT_DRIVER, dataSources.get(0).getDriverClassName());
    }
    
    @Test
    void driverClassNameFallsBackToMysqlWhenDialectIsNotLoaded() {
        MockEnvironment environment = mysqlEnvironment();
        environment.setProperty("nacos.plugin.datasource-dialect.type", "unknown-dialect");
        
        List<HikariDataSource> dataSources =
            new ExternalDataSourceProperties().build(environment, dataSource -> {
            });
        
        assertEquals(MYSQL_COMPAT_DRIVER, dataSources.get(0).getDriverClassName());
    }
    
    @Test
    void driverClassNameFallsBackToMysqlWhenDialectPluginIsDisabled() {
        dialectMap.put(TEST_DIALECT_TYPE, new TestDatabaseDialect(TEST_DIALECT_DRIVER));
        PluginStateCheckerHolder.setInstance((pluginType, pluginName) -> false);
        MockEnvironment environment = mysqlEnvironment();
        environment.setProperty("nacos.plugin.datasource-dialect.type", TEST_DIALECT_TYPE);
        
        List<HikariDataSource> dataSources =
            new ExternalDataSourceProperties().build(environment, dataSource -> {
            });
        
        assertEquals(MYSQL_COMPAT_DRIVER, dataSources.get(0).getDriverClassName());
    }
    
    @Test
    void driverClassNameFallsBackToMysqlWhenNoDialectSelected() {
        // No dialect property and no mysql dialect registered: keep the historical behavior.
        List<HikariDataSource> dataSources =
            new ExternalDataSourceProperties().build(mysqlEnvironment(), dataSource -> {
            });
        
        assertEquals(MYSQL_COMPAT_DRIVER, dataSources.get(0).getDriverClassName());
    }
    
    private static MockEnvironment mysqlEnvironment() {
        MockEnvironment environment = new MockEnvironment();
        environment.setProperty("nacos.plugin.datasource.db.num", "1");
        environment.setProperty("nacos.plugin.datasource.db.user", USERNAME);
        environment.setProperty("nacos.plugin.datasource.db.password", PASSWORD);
        environment.setProperty("nacos.plugin.datasource.db.url.0", JDBC_URL);
        return environment;
    }
    
    @SuppressWarnings("unchecked")
    private static Map<String, DatabaseDialect> getDialectMap() throws Exception {
        Field field = DatabaseDialectManager.class.getDeclaredField("SUPPORT_DIALECT_MAP");
        field.setAccessible(true);
        return (Map<String, DatabaseDialect>) field.get(null);
    }
    
    @Test
    void externalDatasourceNormally() {
        HikariDataSource expectedDataSource = new HikariDataSource();
        expectedDataSource.setJdbcUrl(JDBC_URL);
        expectedDataSource.setUsername(USERNAME);
        expectedDataSource.setPassword(PASSWORD);
        MockEnvironment environment = new MockEnvironment();
        environment.setProperty("db.num", "1");
        environment.setProperty("db.user", USERNAME);
        environment.setProperty("db.password", PASSWORD);
        environment.setProperty("db.url.0", JDBC_URL);
        List<HikariDataSource> dataSources =
            new ExternalDataSourceProperties().build(environment, (dataSource -> {
                assertEquals(dataSource.getJdbcUrl(), expectedDataSource.getJdbcUrl());
                assertEquals(dataSource.getUsername(), expectedDataSource.getUsername());
                assertEquals(dataSource.getPassword(), expectedDataSource.getPassword());
                
            }));
        assertEquals(1, dataSources.size());
    }
    
    @Test
    void externalDatasourceSupportsCanonicalConfig() {
        MockEnvironment environment = new MockEnvironment();
        environment.setProperty("nacos.plugin.datasource.db.num", "1");
        environment.setProperty("nacos.plugin.datasource.db.user", USERNAME);
        environment.setProperty("nacos.plugin.datasource.db.password", PASSWORD);
        environment.setProperty("nacos.plugin.datasource.db.url.0", JDBC_URL);
        List<HikariDataSource> dataSources =
            new ExternalDataSourceProperties().build(environment, dataSource -> {
                assertEquals(JDBC_URL, dataSource.getJdbcUrl());
                assertEquals(USERNAME, dataSource.getUsername());
                assertEquals(PASSWORD, dataSource.getPassword());
            });
        assertEquals(1, dataSources.size());
    }
    
    @Test
    void externalDatasourceCanonicalConfigOverridesLegacyPerItem() {
        MockEnvironment environment = new MockEnvironment();
        environment.setProperty("db.num", "2");
        environment.setProperty("db.url.0", "legacy-url-0");
        environment.setProperty("db.url.1", "legacy-url-1");
        environment.setProperty("db.user.0", "legacy-user-0");
        environment.setProperty("db.user.1", "legacy-user-1");
        environment.setProperty("db.password", "legacy-password");
        environment.setProperty("nacos.plugin.datasource.db.url.0", "canonical-url-0");
        environment.setProperty("nacos.plugin.datasource.db.user", "canonical-user");
        environment.setProperty("nacos.plugin.datasource.db.password.1",
            "canonical-password-1");
        List<HikariDataSource> dataSources =
            new ExternalDataSourceProperties().build(environment, dataSource -> {
            });
        assertEquals(2, dataSources.size());
        assertEquals("canonical-url-0", dataSources.get(0).getJdbcUrl());
        assertEquals("legacy-url-1", dataSources.get(1).getJdbcUrl());
        assertEquals("canonical-user", dataSources.get(0).getUsername());
        assertEquals("canonical-user", dataSources.get(1).getUsername());
        assertEquals("legacy-password", dataSources.get(0).getPassword());
        assertEquals("canonical-password-1", dataSources.get(1).getPassword());
    }
    
    @Test
    void externalDatasourceToAssertMultiJdbcUrl() {
        
        HikariDataSource expectedDataSource = new HikariDataSource();
        expectedDataSource.setJdbcUrl(JDBC_URL);
        expectedDataSource.setUsername(USERNAME);
        expectedDataSource.setPassword(PASSWORD);
        MockEnvironment environment = new MockEnvironment();
        environment.setProperty("db.num", "2");
        environment.setProperty("db.user", USERNAME);
        environment.setProperty("db.password", PASSWORD);
        environment.setProperty("db.url.0", JDBC_URL);
        environment.setProperty("db.url.1", JDBC_URL);
        List<HikariDataSource> dataSources =
            new ExternalDataSourceProperties().build(environment, (dataSource -> {
                assertEquals(dataSource.getJdbcUrl(), expectedDataSource.getJdbcUrl());
                assertEquals(dataSource.getUsername(), expectedDataSource.getUsername());
                assertEquals(dataSource.getPassword(), expectedDataSource.getPassword());
                
            }));
        assertEquals(2, dataSources.size());
    }
    
    @Test
    void externalDatasourceToAssertMultiPasswordAndUsername() {
        
        HikariDataSource expectedDataSource = new HikariDataSource();
        expectedDataSource.setJdbcUrl(JDBC_URL);
        expectedDataSource.setUsername(USERNAME);
        expectedDataSource.setPassword(PASSWORD);
        MockEnvironment environment = new MockEnvironment();
        environment.setProperty("db.num", "2");
        environment.setProperty("db.user.0", USERNAME);
        environment.setProperty("db.user.1", USERNAME);
        environment.setProperty("db.password.0", PASSWORD);
        environment.setProperty("db.password.1", PASSWORD);
        environment.setProperty("db.url.0", JDBC_URL);
        environment.setProperty("db.url.1", JDBC_URL);
        List<HikariDataSource> dataSources =
            new ExternalDataSourceProperties().build(environment, (dataSource -> {
                assertEquals(dataSource.getJdbcUrl(), expectedDataSource.getJdbcUrl());
                assertEquals(dataSource.getUsername(), expectedDataSource.getUsername());
                assertEquals(dataSource.getPassword(), expectedDataSource.getPassword());
                
            }));
        assertEquals(2, dataSources.size());
    }
    
    @Test
    void externalDatasourceToAssertMinIdle() {
        MockEnvironment environment = new MockEnvironment();
        environment.setProperty("db.num", "1");
        environment.setProperty("db.user", USERNAME);
        environment.setProperty("db.password", PASSWORD);
        environment.setProperty("db.url.0", JDBC_URL);
        List<HikariDataSource> dataSources =
            new ExternalDataSourceProperties().build(environment, (dataSource -> {
                dataSource.validate();
                assertEquals(DataSourcePoolProperties.DEFAULT_MINIMUM_IDLE,
                    dataSource.getMinimumIdle());
            }));
        assertEquals(1, dataSources.size());
    }
    
    @Test
    void externalDatasourceFailureWithLarkInfo() {
        assertThrows(IllegalArgumentException.class, () -> {
            
            MockEnvironment environment = new MockEnvironment();
            new ExternalDataSourceProperties().build(environment, null);
            
        });
        
    }
    
    @Test
    void externalDatasourceFailureWithErrorInfo() {
        assertThrows(IllegalArgumentException.class, () -> {
            
            HikariDataSource expectedDataSource = new HikariDataSource();
            expectedDataSource.setJdbcUrl(JDBC_URL);
            expectedDataSource.setUsername(USERNAME);
            expectedDataSource.setPassword(PASSWORD);
            MockEnvironment environment = new MockEnvironment();
            // error num of db
            environment.setProperty("db.num", "2");
            environment.setProperty("db.user", USERNAME);
            environment.setProperty("db.password", PASSWORD);
            environment.setProperty("db.url.0", JDBC_URL);
            List<HikariDataSource> dataSources =
                new ExternalDataSourceProperties().build(environment, (dataSource -> {
                    assertEquals(dataSource.getJdbcUrl(), expectedDataSource.getJdbcUrl());
                    assertEquals(dataSource.getUsername(), expectedDataSource.getUsername());
                    assertEquals(dataSource.getPassword(), expectedDataSource.getPassword());
                    
                }));
        });
    }
    
    private static class TestDatabaseDialect implements DatabaseDialect {
        
        private final String defaultDriverClassName;
        
        private TestDatabaseDialect(String defaultDriverClassName) {
            this.defaultDriverClassName = defaultDriverClassName;
        }
        
        @Override
        public String getType() {
            return TEST_DIALECT_TYPE;
        }
        
        @Override
        public String getDefaultDriverClassName() {
            return defaultDriverClassName;
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
