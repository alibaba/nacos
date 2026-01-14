/*
 * Copyright 1999-2024 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.core.plugin;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link CriticalPluginConfig} unit test.
 *
 * @author WangzJi
 */
class CriticalPluginConfigTest {

    @Test
    void isCriticalAuthNacosTest() {
        // Auth plugins are NOT critical - users can disable default auth to use custom plugins
        assertFalse(CriticalPluginConfig.isCritical("auth:nacos"));
    }

    @Test
    void isCriticalDatasourceDialectMysqlTest() {
        assertTrue(CriticalPluginConfig.isCritical("datasource-dialect:mysql"));
    }

    @Test
    void isCriticalDatasourceDialectDerbyTest() {
        assertTrue(CriticalPluginConfig.isCritical("datasource-dialect:derby"));
    }

    @Test
    void isCriticalDatasourceDialectPostgresqlTest() {
        assertTrue(CriticalPluginConfig.isCritical("datasource-dialect:postgresql"));
    }

    @Test
    void isCriticalNonCriticalPluginTest() {
        assertFalse(CriticalPluginConfig.isCritical("trace:test"));
        assertFalse(CriticalPluginConfig.isCritical("config:test"));
        assertFalse(CriticalPluginConfig.isCritical("encryption:test"));
    }

    @Test
    void isCriticalNonExistentPluginTest() {
        assertFalse(CriticalPluginConfig.isCritical("nonexistent:plugin"));
    }

    @Test
    void isCriticalNullPluginIdTest() {
        assertFalse(CriticalPluginConfig.isCritical(null));
    }

    @Test
    void isCriticalEmptyPluginIdTest() {
        assertFalse(CriticalPluginConfig.isCritical(""));
    }

    @Test
    void getCriticalPluginsTest() {
        Set<String> criticalPlugins = CriticalPluginConfig.getCriticalPlugins();

        assertNotNull(criticalPlugins);
        assertEquals(3, criticalPlugins.size());
        assertTrue(criticalPlugins.contains("datasource-dialect:mysql"));
        assertTrue(criticalPlugins.contains("datasource-dialect:derby"));
        assertTrue(criticalPlugins.contains("datasource-dialect:postgresql"));
    }

    @Test
    void getCriticalPluginsIsUnmodifiableTest() {
        Set<String> criticalPlugins = CriticalPluginConfig.getCriticalPlugins();

        try {
            criticalPlugins.add("test:plugin");
            assertTrue(false, "Expected UnsupportedOperationException");
        } catch (UnsupportedOperationException e) {
            assertTrue(true);
        }
    }

    @Test
    void isCriticalCaseSensitiveTest() {
        // Critical plugin check is case-sensitive
        assertTrue(CriticalPluginConfig.isCritical("datasource-dialect:mysql"));
        assertFalse(CriticalPluginConfig.isCritical("DATASOURCE-DIALECT:MYSQL"));
        assertFalse(CriticalPluginConfig.isCritical("Datasource-Dialect:Mysql"));
    }
}
