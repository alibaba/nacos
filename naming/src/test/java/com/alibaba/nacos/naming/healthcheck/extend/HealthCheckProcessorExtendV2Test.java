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

package com.alibaba.nacos.naming.healthcheck.extend;

import com.alibaba.nacos.naming.healthcheck.v2.processor.HealthCheckProcessorV2;
import com.alibaba.nacos.naming.healthcheck.v2.processor.MysqlHealthCheckProcessor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.config.SingletonBeanRegistry;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class HealthCheckProcessorExtendV2Test {
    
    @Mock
    private SingletonBeanRegistry registry;
    
    @Mock
    private MysqlHealthCheckProcessor mysqlProcessor;
    
    private HealthCheckProcessorExtendV2 healthCheckProcessorExtendV2;
    
    @BeforeEach
    void setUp() {
        healthCheckProcessorExtendV2 = new HealthCheckProcessorExtendV2();
        Collection<HealthCheckProcessorV2> processors = new ArrayList<>();
        processors.add(mysqlProcessor);
        
        ReflectionTestUtils.setField(healthCheckProcessorExtendV2, "processors", processors);
        ReflectionTestUtils.setField(healthCheckProcessorExtendV2, "registry", registry);
    }
    
    @Test
    void addProcessor() {
        Set<String> origin = new HashSet<>();
        origin.add("HTTP");
        healthCheckProcessorExtendV2.addProcessor(origin);
        
        verify(registry).registerSingleton(healthCheckProcessorExtendV2.lowerFirstChar(mysqlProcessor.getClass().getSimpleName()),
                mysqlProcessor);
    }
}