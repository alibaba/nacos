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

import com.alibaba.nacos.naming.healthcheck.HealthCheckProcessor;
import com.alibaba.nacos.naming.healthcheck.MysqlHealthCheckProcessor;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.beans.factory.config.SingletonBeanRegistry;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

import static org.mockito.Mockito.verify;

@RunWith(MockitoJUnitRunner.class)
public class HealthCheckProcessorExtendV1Test {

    @Mock
    private SingletonBeanRegistry registry;

    private HealthCheckProcessorExtendV1 healthCheckProcessorExtendV1;

    private HealthCheckProcessor mysqlProcessor;

    @Before
    public void setUp() {
        healthCheckProcessorExtendV1 = new HealthCheckProcessorExtendV1();
        Collection<HealthCheckProcessor> processors = new ArrayList<>();
        mysqlProcessor = new MysqlHealthCheckProcessor();
        processors.add(mysqlProcessor);

        ReflectionTestUtils.setField(healthCheckProcessorExtendV1, "processors", processors);
        ReflectionTestUtils.setField(healthCheckProcessorExtendV1, "registry", registry);
    }

    @Test
    public void addProcessor() {
        Set<String> origin = new HashSet<>();
        origin.add("HTTP");
        healthCheckProcessorExtendV1.addProcessor(origin);

        verify(registry).registerSingleton(healthCheckProcessorExtendV1
                .lowerFirstChar(mysqlProcessor.getClass().getSimpleName()), mysqlProcessor);
    }
}