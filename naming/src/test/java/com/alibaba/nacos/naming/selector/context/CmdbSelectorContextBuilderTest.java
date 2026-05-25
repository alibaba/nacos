/*
 *  Copyright 1999-2026 Alibaba Group Holding Ltd.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */

package com.alibaba.nacos.naming.selector.context;

import com.alibaba.nacos.api.cmdb.pojo.Entity;
import com.alibaba.nacos.api.naming.pojo.Instance;
import com.alibaba.nacos.api.selector.context.CmdbContext;
import com.alibaba.nacos.cmdb.service.CmdbReader;
import com.alibaba.nacos.sys.utils.ApplicationUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ConfigurableApplicationContext;

import java.util.Arrays;

import static com.alibaba.nacos.api.cmdb.pojo.PreservedEntityTypes.ip;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(MockitoExtension.class)
class CmdbSelectorContextBuilderTest {
    
    @Mock
    private ConfigurableApplicationContext applicationContext;
    
    @Mock
    private CmdbReader cmdbReader;
    
    private CmdbSelectorContextBuilder<Instance> contextBuilder;
    
    @BeforeEach
    void setUp() {
        ApplicationUtils.injectContext(applicationContext);
        contextBuilder = new CmdbSelectorContextBuilder<>();
    }
    
    @AfterEach
    void tearDown() {
        ApplicationUtils.injectContext(null);
    }
    
    @Test
    void testBuildWithProviders() {
        Mockito.when(applicationContext.getBean(CmdbReader.class)).thenReturn(cmdbReader);
        Entity consumerEntity = new Entity();
        Entity providerEntity1 = new Entity();
        Entity providerEntity2 = new Entity();
        Mockito.when(cmdbReader.queryEntity("1.1.1.1", ip.name())).thenReturn(consumerEntity);
        Mockito.when(cmdbReader.queryEntity("2.2.2.2", ip.name())).thenReturn(providerEntity1);
        Mockito.when(cmdbReader.queryEntity("3.3.3.3", ip.name())).thenReturn(providerEntity2);
        Instance provider1 = instance("2.2.2.2");
        Instance provider2 = instance("3.3.3.3");
        
        CmdbContext<Instance> context =
            contextBuilder.build("1.1.1.1", Arrays.asList(provider1, provider2));
        
        assertEquals("1.1.1.1", context.getConsumer().getInstance().getIp());
        assertSame(consumerEntity, context.getConsumer().getEntity());
        assertEquals(2, context.getProviders().size());
        assertSame(provider1, context.getProviders().get(0).getInstance());
        assertSame(providerEntity1, context.getProviders().get(0).getEntity());
        assertSame(provider2, context.getProviders().get(1).getInstance());
        assertSame(providerEntity2, context.getProviders().get(1).getEntity());
    }
    
    @Test
    void testBuildWithNullProviders() {
        Mockito.when(applicationContext.getBean(CmdbReader.class)).thenReturn(cmdbReader);
        Entity consumerEntity = new Entity();
        Mockito.when(cmdbReader.queryEntity("1.1.1.1", ip.name())).thenReturn(consumerEntity);
        
        CmdbContext<Instance> context = contextBuilder.build("1.1.1.1", null);
        
        assertEquals("1.1.1.1", context.getConsumer().getInstance().getIp());
        assertSame(consumerEntity, context.getConsumer().getEntity());
        assertTrue(context.getProviders().isEmpty());
    }
    
    @Test
    void testGetContextType() {
        assertEquals("CMDB", contextBuilder.getContextType());
    }
    
    private Instance instance(String ip) {
        Instance result = new Instance();
        result.setIp(ip);
        return result;
    }
}
