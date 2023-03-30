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
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.context.ConfigurableApplicationContext;

import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.Silent.class)
public class NacosMeterRegistryCenterTest {
    
    @Mock
    private ConfigurableApplicationContext context;
    
    @Before
    public void initMeterRegistry() {
        ApplicationUtils.injectContext(context);
        when(context.getBean(PrometheusMeterRegistry.class)).thenReturn(null);
    }
    
    @AfterClass
    public static void tearDown() {
        ApplicationUtils.injectContext(null);
    }
    
    @Test
    public void testGetMeterRegistry() {
        Assert.assertNotNull(NacosMeterRegistryCenter.getMeterRegistry(NacosMeterRegistryCenter.CORE_STABLE_REGISTRY));
        Assert.assertNotNull(
                NacosMeterRegistryCenter.getMeterRegistry(NacosMeterRegistryCenter.CONFIG_STABLE_REGISTRY));
        Assert.assertNotNull(
                NacosMeterRegistryCenter.getMeterRegistry(NacosMeterRegistryCenter.NAMING_STABLE_REGISTRY));
        Assert.assertNotNull(
                NacosMeterRegistryCenter.getMeterRegistry(NacosMeterRegistryCenter.TOPN_CONFIG_CHANGE_REGISTRY));
        Assert.assertNotNull(
                NacosMeterRegistryCenter.getMeterRegistry(NacosMeterRegistryCenter.TOPN_SERVICE_CHANGE_REGISTRY));
    }
    
}
