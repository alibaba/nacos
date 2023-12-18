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

package com.alibaba.nacos.common.http.client;

import com.alibaba.nacos.common.http.client.handler.BeanResponseHandler;
import com.alibaba.nacos.common.http.client.handler.ResponseHandler;
import com.alibaba.nacos.common.http.client.handler.RestResultResponseHandler;
import com.alibaba.nacos.common.http.client.handler.StringResponseHandler;
import com.alibaba.nacos.common.model.RestResult;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.slf4j.Logger;

import java.lang.reflect.Type;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

@RunWith(MockitoJUnitRunner.class)
public class AbstractNacosRestTemplateTest {
    
    @Mock
    private ResponseHandler mockResponseHandler;
    
    MockNacosRestTemplate restTemplate;
    
    @Before
    public void setUp() throws Exception {
        restTemplate = new MockNacosRestTemplate(null);
        restTemplate.registerResponseHandler(MockNacosRestTemplate.class.getName(), mockResponseHandler);
    }
    
    @Test
    public void testSelectResponseHandlerForNull() {
        assertTrue(restTemplate.testFindResponseHandler(null) instanceof StringResponseHandler);
    }
    
    @Test
    public void testSelectResponseHandlerForRestResult() {
        assertTrue(restTemplate.testFindResponseHandler(RestResult.class) instanceof RestResultResponseHandler);
    }
    
    @Test
    public void testSelectResponseHandlerForDefault() {
        assertTrue(restTemplate
                .testFindResponseHandler(AbstractNacosRestTemplateTest.class) instanceof BeanResponseHandler);
    }
    
    @Test
    public void testSelectResponseHandlerForCustom() {
        assertEquals(mockResponseHandler, restTemplate.testFindResponseHandler(MockNacosRestTemplate.class));
    }
    
    private static class MockNacosRestTemplate extends AbstractNacosRestTemplate {
        
        public MockNacosRestTemplate(Logger logger) {
            super(logger);
        }
        
        private ResponseHandler testFindResponseHandler(Type responseType) {
            return super.selectResponseHandler(responseType);
        }
    }
}