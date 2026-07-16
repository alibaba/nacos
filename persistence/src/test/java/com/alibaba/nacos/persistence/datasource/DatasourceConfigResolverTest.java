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

package com.alibaba.nacos.persistence.datasource;

import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.core.env.SystemEnvironmentPropertySource;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class DatasourceConfigResolverTest {
    
    private static final String LEGACY_QUERY_TIMEOUT = "QUERYTIMEOUT";
    
    private MockEnvironment environment;
    
    private DatasourceConfigResolver resolver;
    
    @BeforeEach
    void setUp() {
        environment = new MockEnvironment();
        resolver = new DatasourceConfigResolver(environment);
    }
    
    @AfterEach
    void tearDown() {
        System.clearProperty(LEGACY_QUERY_TIMEOUT);
    }
    
    @Test
    void testResolvePrefersCanonicalValue() {
        environment.setProperty("db.num", "1");
        environment.setProperty("nacos.plugin.datasource.db.num", "2");
        assertEquals(2, resolver.resolve("num", Integer.class));
    }
    
    @Test
    void testResolveFallsBackToLegacyValue() {
        environment.setProperty("db.num", "1");
        environment.setProperty("db.user", "legacy-user");
        assertEquals(1, resolver.resolve("num", Integer.class));
        assertEquals("legacy-user", resolver.resolve("user", String.class));
        assertNull(resolver.resolve("password", String.class));
    }
    
    @Test
    void testResolveIndexedUsesCanonicalSourceBeforeLegacySource() {
        environment.setProperty("db.url.0", "legacy-url");
        environment.setProperty("nacos.plugin.datasource.db.url.0", "canonical-url");
        environment.setProperty("db.user.2", "legacy-index-user");
        environment.setProperty("nacos.plugin.datasource.db.user", "canonical-shared-user");
        environment.setProperty("db.password.2", "legacy-index-password");
        environment.setProperty("nacos.plugin.datasource.db.password.0",
            "canonical-first-password");
        assertEquals("canonical-url", resolver.resolveIndexed("url", 0, false));
        assertEquals("canonical-shared-user", resolver.resolveIndexed("user", 2, true));
        assertEquals("canonical-first-password",
            resolver.resolveIndexed("password", 2, true));
    }
    
    @Test
    void testResolveIndexedFallsBackToLegacyAndMissingValues() {
        environment.setProperty("db.url.1", "legacy-url");
        environment.setProperty("db.user", "legacy-shared-user");
        assertEquals("legacy-url", resolver.resolveIndexed("url", 1, false));
        assertEquals("legacy-shared-user", resolver.resolveIndexed("user", 1, true));
        assertNull(resolver.resolveIndexed("url", 2, false));
        assertNull(resolver.resolveIndexed("password", 2, true));
    }
    
    @Test
    void testResolveIndexedSupportsBracketAndSingleValueForms() {
        environment.setProperty("db.url[0]", "legacy-bracket-url");
        environment.setProperty("nacos.plugin.datasource.db.password[0]",
            "canonical-bracket-password");
        assertEquals("legacy-bracket-url", resolver.resolveIndexed("url", 0, false));
        assertEquals("canonical-bracket-password",
            resolver.resolveIndexed("password", 0, true));
        MockEnvironment singleValueEnvironment = new MockEnvironment();
        singleValueEnvironment.setProperty("db.url", "legacy-single-url");
        DatasourceConfigResolver singleValueResolver =
            new DatasourceConfigResolver(singleValueEnvironment);
        assertEquals("legacy-single-url", singleValueResolver.resolveIndexed("url", 0, false));
        assertNull(singleValueResolver.resolveIndexed("url", 1, false));
    }
    
    @Test
    void testBindPoolConfigPrefersCanonicalAndRetainsLegacyOnlyItems() {
        environment.setProperty("db.pool.config.connection-timeout", "10000");
        environment.setProperty("db.pool.config.maximum-pool-size", "30");
        environment.setProperty(
            "nacos.plugin.datasource.db.pool.config.connection-timeout", "20000");
        HikariDataSource dataSource = new HikariDataSource();
        resolver.bindPoolConfig(dataSource);
        assertEquals(20000L, dataSource.getConnectionTimeout());
        assertEquals(30, dataSource.getMaximumPoolSize());
    }
    
    @Test
    void testBindPoolConfigSupportsCanonicalOnly() {
        environment.setProperty(
            "nacos.plugin.datasource.db.pool.config.validation-timeout", "15000");
        HikariDataSource dataSource = new HikariDataSource();
        resolver.bindPoolConfig(dataSource);
        assertEquals(15000L, dataSource.getValidationTimeout());
    }
    
    @Test
    void testResolveCanonicalEnvironmentVariables() {
        Map<String, Object> values = new HashMap<>();
        values.put("NACOS_PLUGIN_DATASOURCE_DB_NUM", "2");
        values.put("NACOS_PLUGIN_DATASOURCE_DB_POOL_CONFIG_MAXIMUM_POOL_SIZE", "25");
        environment.getPropertySources().addFirst(
            new SystemEnvironmentPropertySource("datasourceEnvironment", values));
        HikariDataSource dataSource = new HikariDataSource();
        assertEquals(2, resolver.resolve("num", Integer.class));
        resolver.bindPoolConfig(dataSource);
        assertEquals(25, dataSource.getMaximumPoolSize());
    }
    
    @Test
    void testResolveQueryTimeoutPrecedenceAndDefaults() {
        System.setProperty(LEGACY_QUERY_TIMEOUT, "7");
        environment.setProperty("nacos.plugin.datasource.db.query-timeout", "9");
        assertEquals(9, resolver.resolveQueryTimeout(3));
        assertEquals(7,
            new DatasourceConfigResolver(new MockEnvironment()).resolveQueryTimeout(3));
        System.clearProperty(LEGACY_QUERY_TIMEOUT);
        assertEquals(3,
            new DatasourceConfigResolver(new MockEnvironment()).resolveQueryTimeout(3));
    }
}
