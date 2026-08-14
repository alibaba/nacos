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

package com.alibaba.nacos.naming.healthcheck.v2.processor;

import com.alibaba.nacos.api.naming.pojo.healthcheck.impl.Mysql;
import com.alibaba.nacos.naming.core.v2.pojo.HealthCheckInstancePublishInfo;
import org.junit.jupiter.api.Test;

import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class MysqlHealthCheckProcessorTest {

    @Test
    void testBuildJdbcUrlWithoutConnectionProperties() {
        HealthCheckInstancePublishInfo instance = new HealthCheckInstancePublishInfo("127.0.0.1", 3306);

        String actual = MysqlHealthCheckProcessor.buildJdbcUrl(instance);

        assertEquals("jdbc:mysql://127.0.0.1:3306", actual);
    }

    @Test
    void testBuildConnectionProperties() {
        Mysql config = new Mysql();
        config.setUser("nacos");
        config.setPwd("nacos-password");

        Properties actual = MysqlHealthCheckProcessor.buildConnectionProperties(config);

        assertEquals("nacos", actual.getProperty("user"));
        assertEquals("nacos-password", actual.getProperty("password"));
        assertEquals("500", actual.getProperty("connectTimeout"));
        assertEquals("500", actual.getProperty("socketTimeout"));
        assertEquals("1", actual.getProperty("loginTimeout"));
        assertEquals("false", actual.getProperty("allowLoadLocalInfile"));
        assertEquals("false", actual.getProperty("allowUrlInLocalInfile"));
        assertEquals("false", actual.getProperty("allowMultiQueries"));
    }

    @Test
    void testBuildConnectionPropertiesWithoutCredentials() {
        Properties actual = MysqlHealthCheckProcessor.buildConnectionProperties(new Mysql());

        assertFalse(actual.containsKey("user"));
        assertFalse(actual.containsKey("password"));
    }
}
