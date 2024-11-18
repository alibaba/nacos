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

package com.alibaba.nacos.api.selector;

import com.alibaba.nacos.api.cmdb.pojo.Entity;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.naming.pojo.Instance;
import com.alibaba.nacos.api.selector.context.CmdbContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static com.alibaba.nacos.api.common.Constants.Naming.CMDB_CONTEXT_TYPE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AbstractCmdbSelectorTest {
    
    private AtomicInteger counter;
    
    @BeforeEach
    void setUp() {
        counter = new AtomicInteger();
    }
    
    @Test
    void testSetExpression() {
        MockCmdbSelector cmdbSelector = new MockCmdbSelector();
        assertNull(cmdbSelector.getExpression());
        cmdbSelector.setExpression("test");
        assertEquals("test", cmdbSelector.getExpression());
    }
    
    @Test
    void testParse() throws NacosException {
        MockCmdbSelector cmdbSelector = new MockCmdbSelector();
        cmdbSelector.parse("test");
        assertEquals("test", cmdbSelector.getExpression());
        assertEquals(1, counter.get());
    }
    
    @Test
    void testSelect() {
        CmdbContext<Instance> context = new CmdbContext<>();
        CmdbContext.CmdbInstance<Instance> provider = new CmdbContext.CmdbInstance<>();
        provider.setInstance(new Instance());
        provider.setEntity(new Entity());
        context.setProviders(Collections.singletonList(provider));
        CmdbContext.CmdbInstance<Instance> consumer = new CmdbContext.CmdbInstance<>();
        consumer.setInstance(new Instance());
        consumer.setEntity(new Entity());
        context.setConsumer(consumer);
        List<Instance> actual = new MockCmdbSelector().select(context);
        assertNull(actual.get(0).getIp());
        assertTrue(actual.get(0).getMetadata().isEmpty());
        assertEquals("true", provider.getInstance().getMetadata().get("afterSelect"));
        assertEquals("true", provider.getEntity().getLabels().get("afterSelect"));
        assertEquals("true", consumer.getInstance().getMetadata().get("afterSelect"));
        assertEquals("true", consumer.getEntity().getLabels().get("afterSelect"));
    }
    
    @Test
    void testGetContextType() {
        assertEquals(CMDB_CONTEXT_TYPE, new MockCmdbSelector().getContextType());
    }
    
    @Test
    void testGetType() {
        assertEquals("mock", new MockCmdbSelector().getType());
    }
    
    private class MockCmdbSelector extends AbstractCmdbSelector<Instance> {
        
        @Override
        protected void doParse(String expression) throws NacosException {
            counter.incrementAndGet();
        }
        
        @Override
        protected List<Instance> doSelect(CmdbContext<Instance> context) {
            for (CmdbContext.CmdbInstance<Instance> each : context.getProviders()) {
                each.getInstance().getMetadata().put("afterSelect", "true");
                each.getEntity().setLabels(Collections.singletonMap("afterSelect", "true"));
            }
            context.getConsumer().getInstance().getMetadata().put("afterSelect", "true");
            context.getConsumer().getEntity().setLabels(Collections.singletonMap("afterSelect", "true"));
            return Collections.singletonList(new Instance());
        }
        
        @Override
        public String getType() {
            return "mock";
        }
    }
}