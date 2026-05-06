/*
 * Copyright 1999-2021 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.api.naming.pojo.healthcheck;

import com.alibaba.nacos.api.naming.pojo.healthcheck.impl.Http;
import com.alibaba.nacos.api.naming.pojo.healthcheck.impl.Mysql;
import com.alibaba.nacos.api.naming.pojo.healthcheck.impl.Tcp;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class HealthCheckTypeTest {
    
    @Test
    void testOfHealthCheckerClassForBuildInType() {
        assertEquals(Tcp.class, HealthCheckType.ofHealthCheckerClass("TCP"));
        assertEquals(Http.class, HealthCheckType.ofHealthCheckerClass("HTTP"));
        assertEquals(Mysql.class, HealthCheckType.ofHealthCheckerClass("MYSQL"));
        assertEquals(AbstractHealthChecker.None.class, HealthCheckType.ofHealthCheckerClass("NONE"));
    }
    
    @Test
    void testOfHealthCheckerClassForExtendType() {
        HealthCheckType.registerHealthChecker(TestChecker.TYPE, TestChecker.class);
        assertEquals(TestChecker.class, HealthCheckType.ofHealthCheckerClass(TestChecker.TYPE));
    }
    
    @Test
    void testOfHealthCheckerClassForNonExistType() {
        assertNull(HealthCheckType.ofHealthCheckerClass("non-exist"));
    }
    
    @Test
    void testGetLoadedHealthCheckerClasses() {
        HealthCheckType.registerHealthChecker(TestChecker.TYPE, TestChecker.class);
        List<Class<? extends AbstractHealthChecker>> actual = HealthCheckType.getLoadedHealthCheckerClasses();
        assertEquals(5, actual.size());
    }
}