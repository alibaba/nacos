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

package com.alibaba.nacos.logger.adapter.logback12;

import ch.qos.logback.core.ContextBase;
import ch.qos.logback.core.joran.spi.ActionException;
import ch.qos.logback.core.joran.spi.InterpretationContext;
import ch.qos.logback.core.status.ErrorStatus;
import ch.qos.logback.core.status.Status;
import com.alibaba.nacos.common.logging.NacosLoggingProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.xml.sax.Attributes;

import java.util.List;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NacosClientPropertyActionTest {
    
    ContextBase context;
    
    private NacosLoggingProperties loggingProperties;
    
    private Properties properties;
    
    @BeforeEach
    void setUp() throws Exception {
        context = new ContextBase();
        properties = new Properties();
        loggingProperties = new NacosLoggingProperties("classpath:test.xml", properties);
    }
    
    @AfterEach
    void tearDown() throws Exception {
        context.stop();
    }
    
    @Test
    void testLookUpVar() throws ActionException {
        
        properties.setProperty("test.nacos.logging.action.lookup", "true");
        
        final InterpretationContext interpretationContext = new InterpretationContext(context, null);
        
        final Attributes mockAttr = Mockito.mock(AttributesForTest.class);
        Mockito.when(mockAttr.getValue(Mockito.eq("name"))).thenReturn("logPath");
        Mockito.when(mockAttr.getValue(Mockito.eq("source"))).thenReturn("test.nacos.logging.action.lookup");
        Mockito.when(mockAttr.getValue(Mockito.eq("scope"))).thenReturn("context");
        Mockito.when(mockAttr.getValue(Mockito.eq("defaultValue"))).thenReturn("/root");
        
        NacosClientPropertyAction nacosClientPropertyAction = new NacosClientPropertyAction(loggingProperties);
        nacosClientPropertyAction.setContext(context);
        
        nacosClientPropertyAction.begin(interpretationContext, "nacosClientProperty", mockAttr);
        
        final String actual = context.getProperty("logPath");
        assertEquals("true", actual);
        
    }
    
    @Test
    void testBeginWithoutName() throws ActionException {
        final InterpretationContext interpretationContext = new InterpretationContext(context, null);
        final Attributes mockAttr = Mockito.mock(AttributesForTest.class);
        Mockito.when(mockAttr.getValue(Mockito.eq("name"))).thenReturn("");
        Mockito.when(mockAttr.getValue(Mockito.eq("source"))).thenReturn("test.nacos.logging.action.lookup");
        Mockito.when(mockAttr.getValue(Mockito.eq("scope"))).thenReturn("context");
        Mockito.when(mockAttr.getValue(Mockito.eq("defaultValue"))).thenReturn("/root");
        NacosClientPropertyAction nacosClientPropertyAction = new NacosClientPropertyAction(loggingProperties);
        nacosClientPropertyAction.setContext(context);
        nacosClientPropertyAction.begin(interpretationContext, "nacosClientProperty", mockAttr);
        List<Status> statusList = context.getStatusManager().getCopyOfStatusList();
        assertEquals(1, statusList.size());
        assertTrue(statusList.get(0) instanceof ErrorStatus);
        assertEquals("The \"name\" and \"source\"  attributes of <nacosClientProperty> must be set", statusList.get(0).getMessage());
    }
    
    static class AttributesForTest implements Attributes {
        
        @Override
        public int getLength() {
            return 0;
        }
        
        @Override
        public String getURI(int index) {
            return null;
        }
        
        @Override
        public String getLocalName(int index) {
            return null;
        }
        
        @Override
        public String getQName(int index) {
            return null;
        }
        
        @Override
        public int getIndex(String uri, String localName) {
            return 0;
        }
        
        @Override
        public int getIndex(String qName) {
            return 0;
        }
        
        @Override
        public String getType(int index) {
            return null;
        }
        
        @Override
        public String getType(String uri, String localName) {
            return null;
        }
        
        @Override
        public String getType(String qName) {
            return null;
        }
        
        @Override
        public String getValue(int index) {
            return null;
        }
        
        @Override
        public String getValue(String uri, String localName) {
            return null;
        }
        
        @Override
        public String getValue(String qName) {
            return null;
        }
    }
    
}
