/*
 * Copyright 1999-2020 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.test.register;

import com.alibaba.nacos.Nacos;
import com.alibaba.nacos.api.NacosFactory;
import com.alibaba.nacos.api.PropertyKeyConst;
import com.alibaba.nacos.api.config.ConfigService;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.metrics.register.PrometheusMetricsRegister;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.ImmutableTag;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.web.server.LocalServerPort;
import org.springframework.test.context.junit4.SpringRunner;

import java.util.Collections;
import java.util.Properties;

@RunWith(SpringRunner.class)
@SpringBootTest(classes = Nacos.class, properties = {"server.servlet.context-path=/nacos"},
        webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT)
public class PrometheusMetricsRegisterTest {
    
    @LocalServerPort
    private int port;
    
    private ConfigService configService;
    
    @Autowired
    private PrometheusMetricsRegister register;
    
    @Autowired
    private MeterRegistry meterRegistry;
    
    @Before
    public void init() throws NacosException {
        Properties properties = new Properties();
        properties.put(PropertyKeyConst.SERVER_ADDR, "127.0.0.1:" + port);
        properties.put(PropertyKeyConst.CONFIG_LONG_POLL_TIMEOUT, "20000");
        properties.put(PropertyKeyConst.CONFIG_RETRY_TIME, "3000");
        properties.put(PropertyKeyConst.MAX_RETRY, "5");
        configService = NacosFactory.createConfigService(properties);
    }
    
    @After
    public void destroy(){
        try {
            configService.shutDown();
        }catch (NacosException ex) {
        }
    }
    
    @Test
    public void testRegisterGauge() {
        register.registerGauge("nacos_test_gauge",
                Collections.singletonList(new ImmutableTag("test", "gauge")),"",  () -> 0);
        Assert.assertNotNull(meterRegistry.get("nacos_test_gauge").tag("test", "gauge"));
    }
    
    @Test
    public void testRegisterCounter() {
        register.registerCounter("nacos_test_counter",
                Collections.singletonList(new ImmutableTag("test", "counter")), "");
        Assert.assertNotNull(meterRegistry.get("nacos_test_counter").tag("test", "counter"));
    }
    
    @Test
    public void testRegisterTimer() {
        register.registerTimer("nacos_test_timer",
                Collections.singletonList(new ImmutableTag("test", "timer")), "");
        Assert.assertNotNull(meterRegistry.get("nacos_test_timer").tag("test", "timer"));
    }
    
    @Test
    public void testSummary() {
        register.summary("nacos_test_summary",
                Collections.singletonList(new ImmutableTag("test", "summary")), "");
        Assert.assertNotNull(meterRegistry.get("nacos_test_summary").tag("test", "summary"));
    }
    
    @Test
    public void testCounterIncrement() {
        register.registerCounter("nacos_test_counter",
                Collections.singletonList(new ImmutableTag("test", "counter")), "");
        Counter counter = meterRegistry.get("nacos_test_counter").counter();
        counter.increment();
        Assert.assertEquals(counter.count(), 1.0, 0.001);
        counter.increment(2.0);
        Assert.assertEquals(counter.count(), 3.0, 0.001);
    }
    
}
