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
import com.alibaba.nacos.api.naming.pojo.healthcheck.AbstractHealthChecker.None;
import com.alibaba.nacos.api.naming.pojo.healthcheck.impl.Http;
import com.alibaba.nacos.api.naming.pojo.healthcheck.impl.Mysql;
import com.alibaba.nacos.api.naming.pojo.healthcheck.impl.Tcp;
import com.alibaba.nacos.api.utils.json.JsonUtilsTestHelper;
import com.alibaba.nacos.api.utils.json.NacosJsonAdapter;
import com.alibaba.nacos.api.utils.json.NacosJsonAdapterNames;
import com.alibaba.nacos.api.utils.json.NacosJsonSubtype;
import com.alibaba.nacos.api.utils.json.NacosTypeReference;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.jsontype.NamedType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HealthCheckerFactoryTest {
    
    @BeforeEach
    void setUp() {
        JsonUtilsTestHelper.reset();
        JsonUtilsTestHelper.useAdapter(new JacksonTestJsonAdapter());
        HealthCheckerFactory.registerSubType(Http.class, Http.TYPE);
        HealthCheckerFactory.registerSubType(Mysql.class, Mysql.TYPE);
        HealthCheckerFactory.registerSubType(Tcp.class, Tcp.TYPE);
        HealthCheckerFactory.registerSubType(None.class, None.TYPE);
        HealthCheckerFactory.registerSubType(new TestChecker());
    }
    
    @AfterEach
    void tearDown() {
        JsonUtilsTestHelper.reset();
    }
    
    @Test
    void testConstructor() {
        assertNotNull(new HealthCheckerFactory());
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
    
    private static class JacksonTestJsonAdapter implements NacosJsonAdapter {
        
        private final ObjectMapper mapper = createObjectMapper();
        
        @Override
        public String name() {
            return NacosJsonAdapterNames.JACKSON2;
        }
        
        @Override
        public boolean isAvailable() {
            return true;
        }
        
        @Override
        public String toJson(Object obj) {
            try {
                return mapper.writeValueAsString(obj);
            } catch (JsonProcessingException e) {
                throw new NacosSerializationException(obj.getClass(), e);
            }
        }
        
        @Override
        public byte[] toJsonBytes(Object obj) {
            return toJson(obj).getBytes(StandardCharsets.UTF_8);
        }
        
        @Override
        public String toCanonicalJson(Object obj) {
            return toJson(obj);
        }
        
        @Override
        public <T> T toObj(byte[] json, Class<T> cls) {
            try {
                return mapper.readValue(json, cls);
            } catch (IOException e) {
                throw new NacosDeserializationException(cls, e);
            }
        }
        
        @Override
        public <T> T toObj(byte[] json, Type type) {
            try {
                return mapper.readValue(json, constructJavaType(type));
            } catch (IOException e) {
                throw new NacosDeserializationException(type, e);
            }
        }
        
        @Override
        public <T> T toObj(byte[] json, NacosTypeReference<T> typeReference) {
            return toObj(json, typeReference.getType());
        }
        
        @Override
        public <T> T toObj(String json, Class<T> cls) {
            try {
                return mapper.readValue(json, cls);
            } catch (IOException e) {
                throw new NacosDeserializationException(cls, e);
            }
        }
        
        @Override
        public <T> T toObj(String json, Type type) {
            try {
                return mapper.readValue(json, constructJavaType(type));
            } catch (IOException e) {
                throw new NacosDeserializationException(type, e);
            }
        }
        
        @Override
        public <T> T toObj(String json, NacosTypeReference<T> typeReference) {
            return toObj(json, typeReference.getType());
        }
        
        @Override
        public <T> T toObj(InputStream inputStream, Class<T> cls) {
            try {
                return mapper.readValue(inputStream, cls);
            } catch (IOException e) {
                throw new NacosDeserializationException(cls, e);
            }
        }
        
        @Override
        public <T> T toObj(InputStream inputStream, Type type) {
            try {
                return mapper.readValue(inputStream, constructJavaType(type));
            } catch (IOException e) {
                throw new NacosDeserializationException(type, e);
            }
        }
        
        @Override
        public void registerSubtype(NacosJsonSubtype subtype) {
            mapper.registerSubtypes(new NamedType(subtype.getSubtype(), subtype.getTypeName()));
        }
        
        private JavaType constructJavaType(Type type) {
            return mapper.constructType(type);
        }
        
        private static ObjectMapper createObjectMapper() {
            ObjectMapper objectMapper = new ObjectMapper();
            objectMapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
            objectMapper.setSerializationInclusion(Include.NON_NULL);
            return objectMapper;
        }
    }
}
