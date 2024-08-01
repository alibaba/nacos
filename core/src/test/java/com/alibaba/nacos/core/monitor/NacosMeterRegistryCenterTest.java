/*
 * Copyright 1999-2018 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.core.monitor;

import com.alibaba.nacos.sys.utils.ApplicationUtils;
import io.micrometer.prometheus.PrometheusMeterRegistry;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.ConfigurableApplicationContext;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.when;

@MockitoSettings(strictness = Strictness.LENIENT)
@ExtendWith(MockitoExtension.class)
class NacosMeterRegistryCenterTest {
    
    @Mock
    private ConfigurableApplicationContext context;
    
    @AfterAll
    static void tearDown() {
        ApplicationUtils.injectContext(null);
    }
    
    @BeforeEach
    void initMeterRegistry() {
        ApplicationUtils.injectContext(context);
        when(context.getBean(PrometheusMeterRegistry.class)).thenReturn(null);
    }
    
    @Test
    void testGetMeterRegistry() {
        assertNotNull(NacosMeterRegistryCenter.getMeterRegistry(NacosMeterRegistryCenter.CORE_STABLE_REGISTRY));
        assertNotNull(NacosMeterRegistryCenter.getMeterRegistry(NacosMeterRegistryCenter.CONFIG_STABLE_REGISTRY));
        assertNotNull(NacosMeterRegistryCenter.getMeterRegistry(NacosMeterRegistryCenter.NAMING_STABLE_REGISTRY));
        assertNotNull(NacosMeterRegistryCenter.getMeterRegistry(NacosMeterRegistryCenter.TOPN_CONFIG_CHANGE_REGISTRY));
        assertNotNull(NacosMeterRegistryCenter.getMeterRegistry(NacosMeterRegistryCenter.TOPN_SERVICE_CHANGE_REGISTRY));
    }
    
}
