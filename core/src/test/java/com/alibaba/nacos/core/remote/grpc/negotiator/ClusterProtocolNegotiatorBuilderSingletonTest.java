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

package com.alibaba.nacos.core.remote.grpc.negotiator;

import com.alibaba.nacos.common.utils.Pair;
import com.alibaba.nacos.sys.env.EnvUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.mock.env.MockEnvironment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * Test ClusterProtocolNegotiatorBuilderSingleton.
 *
 * @author stone-98
 * @date 2024/2/21
 */
class ClusterProtocolNegotiatorBuilderSingletonTest {
    
    @BeforeEach
    void setUp() throws Exception {
        ConfigurableEnvironment environment = new MockEnvironment();
        EnvUtil.setEnvironment(environment);
    }
    
    @AfterEach
    void tearDown() throws Exception {
    }
    
    @Test
    void testSingletonInstance() {
        AbstractProtocolNegotiatorBuilderSingleton singleton1 = ClusterProtocolNegotiatorBuilderSingleton.getSingleton();
        AbstractProtocolNegotiatorBuilderSingleton singleton2 = ClusterProtocolNegotiatorBuilderSingleton.getSingleton();
        assertSame(singleton1, singleton2);
    }
    
    @Test
    void testDefaultBuilderPair() {
        Pair<String, ProtocolNegotiatorBuilder> defaultPair = ClusterProtocolNegotiatorBuilderSingleton.getSingleton().defaultBuilderPair();
        assertNotNull(defaultPair);
        assertEquals(ClusterProtocolNegotiatorBuilderSingleton.TYPE_PROPERTY_KEY, defaultPair.getFirst());
        assertNotNull(defaultPair.getSecond());
    }
    
    @Test
    void testType() {
        String type = ClusterProtocolNegotiatorBuilderSingleton.getSingleton().type();
        assertNotNull(type);
        assertEquals(ClusterProtocolNegotiatorBuilderSingleton.TYPE_PROPERTY_KEY, type);
    }
}
