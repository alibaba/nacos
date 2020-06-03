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

package com.alibaba.nacos.api.naming.pojo.healthcheck;

import static org.junit.Assert.*;

import org.junit.Test;

import com.alibaba.nacos.api.naming.pojo.healthcheck.impl.Tcp;

public class HealthCheckerFactoryTest {

    @Test
    public void testSerialize() {
        Tcp tcp = new Tcp();
        String actual = HealthCheckerFactory.serialize(tcp);
        assertTrue(actual.contains("\"type\":\"TCP\""));
    }

    @Test
    public void testSerializeExtend() {
        HealthCheckerFactory.registerSubType(TestChecker.class, TestChecker.TYPE);
        TestChecker testChecker = new TestChecker();
        String actual = HealthCheckerFactory.serialize(testChecker);
        assertTrue(actual.contains("\"type\":\"TEST\""));
    }

    @Test
    public void testDeserialize() {
        String tcpString = "{\"type\":\"TCP\"}";
        AbstractHealthChecker actual = HealthCheckerFactory.deserialize(tcpString);
        assertEquals(Tcp.class, actual.getClass());
    }

    @Test
    public void testDeserializeExtend() {
        String tcpString = "{\"type\":\"TEST\",\"testValue\":null}";
        AbstractHealthChecker actual = HealthCheckerFactory.deserialize(tcpString);
        assertEquals(TestChecker.class, actual.getClass());
    }
}
