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

package com.alibaba.nacos.plugin.datasource.constants;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class DatasourceConstantsTest {
    
    @Test
    void testConstantClassesCanBeLoaded() {
        assertNotNull(new CommonConstant());
        assertNotNull(new ContextConstant());
        assertNotNull(new DataSourceConstant());
        assertNotNull(new DatabaseTypeConstant());
        assertNotNull(new FieldConstant());
        assertNotNull(new PrimaryKeyConstant());
        assertNotNull(new TableConstant());
    }
    
    @Test
    void testRepresentativeConstants() {
        assertEquals("nacos.plugin.datasource.log.enabled",
            CommonConstant.NACOS_PLUGIN_DATASOURCE_LOG);
        assertEquals("needContent", ContextConstant.NEED_CONTENT);
        assertEquals("mysql", DataSourceConstant.MYSQL);
        assertEquals("postgresql", DatabaseTypeConstant.POSTGRESQL);
        assertEquals("tenantId", FieldConstant.TENANT_ID);
        assertEquals("config_info", TableConstant.CONFIG_INFO);
        assertArrayEquals(new String[] {"id"}, PrimaryKeyConstant.LOWER_RETURN_PRIMARY_KEYS);
        assertArrayEquals(new String[] {"ID"}, PrimaryKeyConstant.UPPER_RETURN_PRIMARY_KEYS);
    }
}
