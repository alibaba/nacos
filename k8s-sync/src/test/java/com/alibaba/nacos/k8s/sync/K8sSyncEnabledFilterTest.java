/*
 * Copyright 1999-2025 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.k8s.sync;

import com.alibaba.nacos.sys.env.EnvUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class K8sSyncEnabledFilterTest {
    
    K8sSyncEnabledFilter k8sSyncEnabledFilter;
    
    MockEnvironment environment;
    
    @BeforeEach
    void setUp() {
        environment = new MockEnvironment();
        EnvUtil.setEnvironment(environment);
        k8sSyncEnabledFilter = new K8sSyncEnabledFilter();
    }
    
    @AfterEach
    void tearDown() {
        EnvUtil.setEnvironment(null);
        ReflectionTestUtils.setField(EnvUtil.class, "functionModeType", "");
    }
    
    @Test
    void getResponsiblePackagePrefix() {
        assertEquals("com.alibaba.nacos.k8s.sync", k8sSyncEnabledFilter.getResponsiblePackagePrefix());
    }
    
    @Test
    void isExcludedOnlyNamingFunction() {
        ReflectionTestUtils.setField(EnvUtil.class, "functionModeType", "naming");
        assertTrue(k8sSyncEnabledFilter.isExcluded("com.alibaba.nacos.k8s.sync.K8sSyncEnabledFilter", null));
    }
    
    @Test
    void isExcludedOnlyConfigFunction() {
        ReflectionTestUtils.setField(EnvUtil.class, "functionModeType", "config");
        assertTrue(k8sSyncEnabledFilter.isExcluded("com.alibaba.nacos.k8s.sync.K8sSyncEnabledFilter", null));
    }
    
    @Test
    void isExcludedDisabled() {
        environment.setProperty("nacos.k8s.sync.enabled", "false");
        assertTrue(k8sSyncEnabledFilter.isExcluded("com.alibaba.nacos.k8s.sync.K8sSyncEnabledFilter", null));
    }
    
    @Test
    void isExcludedEnabled() {
        environment.setProperty("nacos.k8s.sync.enabled", "true");
        assertFalse(k8sSyncEnabledFilter.isExcluded("com.alibaba.nacos.k8s.sync.K8sSyncEnabledFilter", null));
    }
}