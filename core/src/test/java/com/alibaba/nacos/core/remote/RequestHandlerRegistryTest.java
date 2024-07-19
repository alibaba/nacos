/*
 *  Copyright 1999-2021 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.core.remote;

import com.alibaba.nacos.api.remote.request.HealthCheckRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.event.ContextRefreshedEvent;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * {@link RequestHandlerRegistry} unit test.
 *
 * @author chenglu
 * @date 2021-07-02 19:22
 */
@ExtendWith(MockitoExtension.class)
class RequestHandlerRegistryTest {
    
    @InjectMocks
    private RequestHandlerRegistry registry;
    
    @InjectMocks
    private ContextRefreshedEvent contextRefreshedEvent;
    
    @Mock
    private AnnotationConfigApplicationContext applicationContext;
    
    @BeforeEach
    void setUp() {
        Map<String, Object> handlerMap = new HashMap<>();
        handlerMap.put(HealthCheckRequestHandler.class.getSimpleName(), new HealthCheckRequestHandler());
        Mockito.when(applicationContext.getBeansOfType(Mockito.any())).thenReturn(handlerMap);
        
        registry.onApplicationEvent(contextRefreshedEvent);
    }
    
    @Test
    void testGetByRequestType() {
        assertNotNull(registry.getByRequestType(HealthCheckRequest.class.getSimpleName()));
    }
}
