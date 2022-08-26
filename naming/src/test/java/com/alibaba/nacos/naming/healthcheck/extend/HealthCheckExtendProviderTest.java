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

import com.alibaba.nacos.api.naming.pojo.healthcheck.AbstractHealthChecker;
import com.alibaba.nacos.naming.healthcheck.v2.processor.HealthCheckProcessorV2;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.beans.factory.config.SingletonBeanRegistry;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.Collection;

@RunWith(MockitoJUnitRunner.class)
public class HealthCheckExtendProviderTest {

    @Mock
    private SingletonBeanRegistry registry;

    private HealthCheckExtendProvider healthCheckExtendProvider;

    @Before
    public void setUp() throws Exception {
        healthCheckExtendProvider = new HealthCheckExtendProvider();

        AbstractHealthCheckProcessorExtend checkProcessorExtend = new HealthCheckProcessorExtendV2();
        Collection<HealthCheckProcessorV2> processors = new ArrayList<>();
        processors.add(new TestHealthCheckProcessor());
        ReflectionTestUtils.setField(checkProcessorExtend, "processors", processors);
        ReflectionTestUtils.setField(checkProcessorExtend, "registry", registry);
        healthCheckExtendProvider.setHealthCheckProcessorExtend(checkProcessorExtend);

        Collection<AbstractHealthChecker> checkers = new ArrayList<>();
        checkers.add(new TestChecker());
        ReflectionTestUtils.setField(healthCheckExtendProvider, "checkers", checkers);
    }

    @Test
    public void init() {
        healthCheckExtendProvider.init();
    }
}