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

import com.alibaba.nacos.api.exception.runtime.NacosDeserializationException;
import com.alibaba.nacos.api.exception.runtime.NacosSerializationException;
import com.alibaba.nacos.api.naming.pojo.healthcheck.impl.Tcp;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HealthCheckerFactoryTest {
    
    @BeforeAll
    static void beforeClass() {
        HealthCheckerFactory.registerSubType(new TestChecker());
    }
    
    @Test
    void testSerialize() {
        Tcp tcp = new Tcp();
        String actual = HealthCheckerFactory.serialize(tcp);
        assertTrue(actual.contains("\"type\":\"TCP\""));
    }
    
    @Test
    void testSerializeExtend() {
        TestChecker testChecker = new TestChecker();
        String actual = HealthCheckerFactory.serialize(testChecker);
        assertTrue(actual.contains("\"type\":\"TEST\""));
    }
    
    @Test
    void testDeserialize() {
        String tcpString = "{\"type\":\"TCP\"}";
        AbstractHealthChecker actual = HealthCheckerFactory.deserialize(tcpString);
        assertEquals(Tcp.class, actual.getClass());
    }
    
    @Test
    void testDeserializeExtend() {
        String tcpString = "{\"type\":\"TEST\",\"testValue\":null}";
        AbstractHealthChecker actual = HealthCheckerFactory.deserialize(tcpString);
        assertEquals(TestChecker.class, actual.getClass());
    }
    
    @Test
    void testSerializeNoRegister() {
        NoRegisterHealthChecker noRegister = new NoRegisterHealthChecker();
        assertFalse(HealthCheckerFactory.serialize(noRegister).contains("no register"));
    }
    
    @Test
    void testDeserializeNoRegister() {
        String tcpString = "{\"type\":\"no register\",\"testValue\":null}";
        AbstractHealthChecker actual = HealthCheckerFactory.deserialize(tcpString);
        assertEquals(AbstractHealthChecker.None.class, actual.getClass());
    }
    
    @Test
    void testSerializeFailure() {
        assertThrows(NacosSerializationException.class, () -> {
            SelfDependHealthChecker selfDependHealthChecker = new SelfDependHealthChecker();
            System.out.println(HealthCheckerFactory.serialize(selfDependHealthChecker));
        });
    }
    
    @Test
    void testDeserializeFailure() {
        assertThrows(NacosDeserializationException.class, () -> {
            String errorString = "{\"type\"=\"TCP\"}";
            System.out.println(HealthCheckerFactory.deserialize(errorString));
        });
    }
    
    @Test
    void testCreateNoneHealthChecker() {
        assertEquals(AbstractHealthChecker.None.class, HealthCheckerFactory.createNoneHealthChecker().getClass());
    }
    
    private static class NoRegisterHealthChecker extends AbstractHealthChecker {
        
        private static final long serialVersionUID = 9020783491111797559L;
        
        private String testValue;
        
        protected NoRegisterHealthChecker() {
            super("no register");
        }
        
        public String getTestValue() {
            return testValue;
        }
        
        public void setTestValue(String testValue) {
            this.testValue = testValue;
        }
        
        @Override
        public AbstractHealthChecker clone() throws CloneNotSupportedException {
            return null;
        }
    }
    
    private static class SelfDependHealthChecker extends AbstractHealthChecker {
        
        private static final long serialVersionUID = 876677992848225965L;
        
        public SelfDependHealthChecker self = this;
        
        protected SelfDependHealthChecker() {
            super("self depend");
        }
        
        @Override
        public AbstractHealthChecker clone() throws CloneNotSupportedException {
            return null;
        }
    }
}
