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

package com.alibaba.nacos.common.utils;

import com.alibaba.nacos.api.common.Constants;
import com.alibaba.nacos.api.exception.runtime.NacosDeserializationException;
import com.alibaba.nacos.api.exception.runtime.NacosSerializationException;
import com.alibaba.nacos.common.model.RestResult;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.Charset;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JacksonUtilsTest {
    
    @Test
    void testToJson1() {
        assertEquals("null", JacksonUtils.toJson(null));
        assertEquals("\"string\"", JacksonUtils.toJson("string"));
        assertEquals("30", JacksonUtils.toJson(new BigDecimal(30)));
        assertEquals("{\"key\":\"value\"}", JacksonUtils.toJson(Collections.singletonMap("key", "value")));
        assertEquals("[{\"key\":\"value\"}]",
                JacksonUtils.toJson(Collections.singletonList(Collections.singletonMap("key", "value"))));
        assertEquals("{\"aLong\":0,\"aInteger\":1,\"aBoolean\":false}", JacksonUtils.toJson(new TestOfAtomicObject()));
        assertEquals("{\"date\":1626192000000}", JacksonUtils.toJson(new TestOfDate()));
        // only public
        assertEquals("{\"publicAccessModifier\":\"public\"}", JacksonUtils.toJson(new TestOfAccessModifier()));
        // getter is also recognized
        assertEquals("{\"value\":\"value\",\"key\":\"key\"}", JacksonUtils.toJson(new TestOfGetter()));
        // annotation available
        assertEquals(
                "{\"@type\":\"JacksonUtilsTest$TestOfAnnotationSub\",\"date\":\"2021-07-14\",\"subField\":\"subField\","
                        + "\"camelCase\":\"value\"}", JacksonUtils.toJson(new TestOfAnnotationSub()));
    }
    
    @Test
    void testToJson2() {
        assertThrows(NacosSerializationException.class, () -> {
            // object without field will throw exceptions
            JacksonUtils.toJson(new Object());
        });
    }
    
    @Test
    void testToJsonBytes1() {
        assertArrayEquals("null".getBytes(), JacksonUtils.toJsonBytes(null));
        assertArrayEquals("\"string\"".getBytes(), JacksonUtils.toJsonBytes("string"));
        assertArrayEquals("30".getBytes(), JacksonUtils.toJsonBytes(new BigDecimal(30)));
        assertArrayEquals("{\"key\":\"value\"}".getBytes(), JacksonUtils.toJsonBytes(Collections.singletonMap("key", "value")));
        assertArrayEquals("[{\"key\":\"value\"}]".getBytes(),
                JacksonUtils.toJsonBytes(Collections.singletonList(Collections.singletonMap("key", "value"))));
        assertArrayEquals("{\"aLong\":0,\"aInteger\":1,\"aBoolean\":false}".getBytes(),
                JacksonUtils.toJsonBytes(new TestOfAtomicObject()));
        assertArrayEquals("{\"date\":1626192000000}".getBytes(), JacksonUtils.toJsonBytes(new TestOfDate()));
        // only public
        assertArrayEquals("{\"publicAccessModifier\":\"public\"}".getBytes(),
                JacksonUtils.toJsonBytes(new TestOfAccessModifier()));
        // getter is also recognized
        assertArrayEquals("{\"value\":\"value\",\"key\":\"key\"}".getBytes(), JacksonUtils.toJsonBytes(new TestOfGetter()));
        // annotation available
        assertArrayEquals(
                ("{\"@type\":\"JacksonUtilsTest$TestOfAnnotationSub\",\"date\":\"2021-07-14\",\"subField\":\"subField\","
                        + "\"camelCase\":\"value\"}").getBytes(), JacksonUtils.toJsonBytes(new TestOfAnnotationSub()));
    }
    
    @Test
    void testToJsonBytes2() {
        assertThrows(NacosSerializationException.class, () -> {
            // object without field will throw exceptions
            JacksonUtils.toJsonBytes(new Object());
        });
    }
    
    /**
     * JacksonUtils.toObj(byte[], Class)
     */
    @Test
    void testToObject1() {
        assertNull(JacksonUtils.toObj("null".getBytes(), Object.class));
        assertEquals("string", JacksonUtils.toObj("\"string\"".getBytes(), String.class));
        assertEquals(new BigDecimal(30), JacksonUtils.toObj("30".getBytes(), BigDecimal.class));
        assertEquals(Collections.singletonMap("key", "value"), JacksonUtils.toObj("{\"key\":\"value\"}".getBytes(), Map.class));
        assertEquals(Collections.singletonList(Collections.singletonMap("key", "value")),
                JacksonUtils.toObj("[{\"key\":\"value\"}]".getBytes(), List.class));
        assertEquals(new TestOfAtomicObject(),
                JacksonUtils.toObj("{\"aLong\":0,\"aInteger\":1,\"aBoolean\":false}".getBytes(), TestOfAtomicObject.class));
        assertEquals(new TestOfDate(), JacksonUtils.toObj("{\"date\":1626192000000}".getBytes(), TestOfDate.class));
        assertEquals(new TestOfAccessModifier(),
                JacksonUtils.toObj("{\"publicAccessModifier\":\"public\"}".getBytes(), TestOfAccessModifier.class));
        assertEquals(new TestOfGetter(),
                JacksonUtils.toObj("{\"value\":\"value\",\"key\":\"key\"}".getBytes(), TestOfGetter.class));
        assertEquals(new TestOfAnnotationSub(), JacksonUtils.toObj(
                ("{\"@type\":\"JacksonUtilsTest$TestOfAnnotationSub\",\"date\":\"2021-07-14\","
                        + "\"subField\":\"subField\",\"camelCase\":\"value\"}").getBytes(), TestOfAnnotation.class));
    }
    
    /**
     * JacksonUtils.toObj(byte[], Class)
     */
    @Test
    void testToObject2() {
        assertThrows(Exception.class, () -> {
            JacksonUtils.toObj(("{not_A}Json:String}").getBytes(), TestOfAnnotationSub.class);
        });
    }
    
    /**
     * JacksonUtils.toObj(byte[], Type)
     */
    @Test
    void testToObject3() {
        assertEquals(Collections.singletonMap("key", "value"), JacksonUtils.toObj("{\"key\":\"value\"}".getBytes(),
                TypeUtils.parameterize(Map.class, String.class, String.class)));
        assertEquals(Collections.singletonList(Collections.singletonMap("key", "value")),
                JacksonUtils.toObj("[{\"key\":\"value\"}]".getBytes(),
                        TypeUtils.parameterize(List.class, TypeUtils.parameterize(Map.class, String.class, String.class))));
    }
    
    /**
     * JacksonUtils.toObj(byte[], Type)
     */
    @Test
    void testToObject4() {
        assertThrows(Exception.class, () -> {
            JacksonUtils.toObj("{not_A}Json:String}".getBytes(), TypeUtils.parameterize(Map.class, String.class, String.class));
        });
    }
    
    /**
     * JacksonUtils.toObj(byte[], Type)
     */
    @Test
    void testToObject5() {
        assertThrows(Exception.class, () -> {
            JacksonUtils.toObj("{\"key\":\"value\"}".getBytes(), Object.class.getGenericSuperclass());
        });
    }
    
    /**
     * JacksonUtils.toObj(InputStream, Class)
     */
    @Test
    void testToObject6() {
        assertNull(JacksonUtils.toObj(new ByteArrayInputStream("null".getBytes()), Object.class));
        assertEquals("string", JacksonUtils.toObj(new ByteArrayInputStream("\"string\"".getBytes()), String.class));
        assertEquals(new BigDecimal(30), JacksonUtils.toObj(new ByteArrayInputStream("30".getBytes()), BigDecimal.class));
        assertEquals(Collections.singletonMap("key", "value"),
                JacksonUtils.toObj(new ByteArrayInputStream("{\"key\":\"value\"}".getBytes()), Map.class));
        assertEquals(Collections.singletonList(Collections.singletonMap("key", "value")),
                JacksonUtils.toObj(new ByteArrayInputStream("[{\"key\":\"value\"}]".getBytes()), List.class));
        assertEquals(new TestOfAtomicObject(),
                JacksonUtils.toObj(new ByteArrayInputStream("{\"aLong\":0,\"aInteger\":1,\"aBoolean\":false}".getBytes()),
                        TestOfAtomicObject.class));
        assertEquals(new TestOfDate(),
                JacksonUtils.toObj(new ByteArrayInputStream("{\"date\":1626192000000}".getBytes()), TestOfDate.class));
        assertEquals(new TestOfAccessModifier(),
                JacksonUtils.toObj(new ByteArrayInputStream("{\"publicAccessModifier\":\"public\"}".getBytes()),
                        TestOfAccessModifier.class));
        assertEquals(new TestOfGetter(),
                JacksonUtils.toObj(new ByteArrayInputStream("{\"value\":\"value\",\"key\":\"key\"}".getBytes()),
                        TestOfGetter.class));
        assertEquals(new TestOfAnnotationSub(), JacksonUtils.toObj((new ByteArrayInputStream(
                        ("{\"@type\":\"JacksonUtilsTest$TestOfAnnotationSub\","
                                + "\"date\":\"2021-07-14\",\"subField\":\"subField\",\"camelCase\":\"value\"}").getBytes())),
                TestOfAnnotation.class));
    }
    
    /**
     * JacksonUtils.toObj(InputStream, Class)
     */
    @Test
    void testToObject7() {
        assertThrows(Exception.class, () -> {
            JacksonUtils.toObj((ByteArrayInputStream) null, BigDecimal.class);
        });
    }
    
    /**
     * JacksonUtils.toObj(InputStream, Class)
     */
    @Test
    void testToObject8() {
        assertThrows(Exception.class, () -> {
            JacksonUtils.toObj(new ByteArrayInputStream("{not_A}Json:String}".getBytes()), Object.class);
        });
    }
    
    /**
     * JacksonUtils.toObj(byte[], TypeReference)
     */
    @Test
    void testToObject9() {
        assertNull(JacksonUtils.toObj("null".getBytes(), new TypeReference<Object>() {
        }));
        assertEquals("string", JacksonUtils.toObj("\"string\"".getBytes(), new TypeReference<String>() {
        }));
        assertEquals(new BigDecimal(30), JacksonUtils.toObj("30".getBytes(), new TypeReference<BigDecimal>() {
        }));
        assertEquals(Collections.singletonMap("key", "value"),
                JacksonUtils.toObj("{\"key\":\"value\"}".getBytes(), new TypeReference<Map<String, String>>() {
                }));
        assertEquals(Collections.singletonList(Collections.singletonMap("key", "value")),
                JacksonUtils.toObj("[{\"key\":\"value\"}]".getBytes(), new TypeReference<List<Map<String, String>>>() {
                }));
        assertEquals(new TestOfAtomicObject(), JacksonUtils.toObj("{\"aLong\":0,\"aInteger\":1,\"aBoolean\":false}".getBytes(),
                new TypeReference<TestOfAtomicObject>() {
                }));
        assertEquals(new TestOfDate(), JacksonUtils.toObj("{\"date\":1626192000000}".getBytes(), new TypeReference<TestOfDate>() {
        }));
        assertEquals(new TestOfAccessModifier(),
                JacksonUtils.toObj("{\"publicAccessModifier\":\"public\"}".getBytes(), new TypeReference<TestOfAccessModifier>() {
                }));
        assertEquals(new TestOfGetter(),
                JacksonUtils.toObj("{\"value\":\"value\",\"key\":\"key\"}".getBytes(), new TypeReference<TestOfGetter>() {
                }));
        assertEquals(new TestOfAnnotationSub(), JacksonUtils.toObj(
                ("{\"@type\":\"JacksonUtilsTest$TestOfAnnotationSub\",\"date\":\"2021-07-14\","
                        + "\"subField\":\"subField\",\"camelCase\":\"value\"}").getBytes(),
                new TypeReference<TestOfAnnotation>() {
                }));
    }
    
    /**
     * JacksonUtils.toObj(byte[], TypeReference)
     */
    @Test
    void testToObject10() {
        assertThrows(Exception.class, () -> {
            JacksonUtils.toObj("{not_A}Json:String}".getBytes(), new TypeReference<Object>() {
            });
        });
    }
    
    /**
     * JacksonUtils.toObj(InputStream, Type)
     */
    @Test
    void testToObject11() {
        assertEquals(Collections.singletonMap("key", "value"),
                JacksonUtils.toObj(new ByteArrayInputStream("{\"key\":\"value\"}".getBytes()),
                        TypeUtils.parameterize(Map.class, String.class, String.class)));
        assertEquals(Collections.singletonList(Collections.singletonMap("key", "value")),
                JacksonUtils.toObj(new ByteArrayInputStream("[{\"key\":\"value\"}]".getBytes()),
                        TypeUtils.parameterize(List.class, TypeUtils.parameterize(Map.class, String.class, String.class))));
    }
    
    /**
     * JacksonUtils.toObj(InputStream, Type)
     */
    @Test
    void testToObject12() {
        assertThrows(Exception.class, () -> {
            JacksonUtils.toObj(new ByteArrayInputStream("{not_A}Json:String}".getBytes()),
                    TypeUtils.parameterize(Map.class, String.class, String.class));
        });
    }
    
    /**
     * JacksonUtils.toObj(InputStream, Type)
     */
    @Test
    void testToObject13() {
        assertThrows(Exception.class, () -> {
            JacksonUtils.toObj(new ByteArrayInputStream("{\"key\":\"value\"}".getBytes()), Object.class.getGenericSuperclass());
        });
    }
    
    /**
     * JacksonUtils.toObj(InputStream, Type)
     */
    @Test
    void testToObject14() {
        assertThrows(Exception.class, () -> {
            JacksonUtils.toObj((InputStream) null, Object.class.getGenericSuperclass());
        });
    }
    
    /**
     * JacksonUtils.toObj(String)
     */
    @Test
    void testToObject15() {
        assertEquals("null", JacksonUtils.toObj("null").asText());
        assertEquals("string", JacksonUtils.toObj("\"string\"").asText());
        assertEquals(30, JacksonUtils.toObj("30").asInt());
        assertEquals("value", JacksonUtils.toObj("{\"key\":\"value\"}").get("key").asText());
        assertEquals("value", JacksonUtils.toObj("[{\"key\":\"value\"}]").get(0).get("key").asText());
        
        JsonNode jsonNode = JacksonUtils.toObj("{\"aLong\":0,\"aInteger\":1,\"aBoolean\":false}");
        assertEquals(0L, jsonNode.get("aLong").asLong());
        assertEquals(1, jsonNode.get("aInteger").asInt());
    }
    
    /**
     * JacksonUtils.toObj(String)
     */
    @Test
    void testToObject16() {
        assertThrows(Exception.class, () -> {
            JacksonUtils.toObj("{not_A}Json:String}");
        });
    }
    
    @Test
    void testRegisterSubtype() {
        JacksonUtils.registerSubtype(TestOfChild.class, "JacksonUtilsTest$TestOfChild");
        
        assertEquals(new TestOfChild(), JacksonUtils.toObj(
                "{\"@type\":\"JacksonUtilsTest$TestOfChild\",\"parentField\":\"parentValue\"," + "\"childField\":\"childValue\"}",
                TestOfParent.class));
    }
    
    @Test
    void testCreateEmptyJsonNode() {
        assertEquals("", JacksonUtils.createEmptyJsonNode().asText());
        assertTrue(JacksonUtils.createEmptyJsonNode().isEmpty());
    }
    
    @Test
    void testCreateEmptyArrayNode() {
        assertEquals("", JacksonUtils.createEmptyJsonNode().asText());
        assertEquals(0, JacksonUtils.createEmptyArrayNode().size());
        assertTrue(JacksonUtils.createEmptyArrayNode().isEmpty());
    }
    
    @Test
    void testTransferToJsonNode() {
        JsonNode jsonNode1 = JacksonUtils.transferToJsonNode(Collections.singletonMap("key", "value"));
        assertEquals("value", jsonNode1.get("key").asText());
        
        JsonNode jsonNode2 = JacksonUtils.transferToJsonNode(new TestOfAtomicObject());
        assertEquals("0", jsonNode2.get("aLong").asText());
        assertEquals("1", jsonNode2.get("aInteger").asText());
        assertEquals("false", jsonNode2.get("aBoolean").asText());
    }
    
    @Test
    void testConstructJavaType() {
        assertEquals("java.lang.String", JacksonUtils.constructJavaType(String.class).getRawClass().getName());
        assertTrue(JacksonUtils.constructJavaType(String.class).isFinal());
    }
    
    @Test
    void testToJsonBytes() {
        Map<String, Object> map = new LinkedHashMap<String, Object>();
        map.put("string", "你好，中国！");
        map.put("integer", 999);
        RestResult<Map<String, Object>> restResult = new RestResult();
        restResult.setData(map);
        
        byte[] bytes = JacksonUtils.toJsonBytes(restResult);
        String jsonFromBytes = ByteUtils.toString(bytes);
        assertTrue(jsonFromBytes.contains("\"code\":0"));
        assertTrue(jsonFromBytes.contains("\"data\":{\"string\":\"你好，中国！\",\"integer\":999}"));
        // old `toJsonBytes` method implementation:
        //     public static byte[] toJsonBytes(Object obj) {
        //        try {
        //            return ByteUtils.toBytes(mapper.writeValueAsString(obj));
        //        } catch (JsonProcessingException e) {
        //            throw new NacosSerializationException(obj.getClass(), e);
        //        }
        //    }
        
        // here is a verification to compare with the old implementation
        byte[] bytesFromOldImplementation = ByteUtils.toBytes(JacksonUtils.toJson(restResult));
        String jsonFromBytesOldImplementation = new String(bytesFromOldImplementation, Charset.forName(Constants.ENCODE));
        assertTrue(jsonFromBytesOldImplementation.contains("\"code\":0"));
        assertTrue(jsonFromBytesOldImplementation.contains("\"data\":{\"string\":\"你好，中国！\",\"integer\":999}"));
    }
    
    @Test
    void testToObjFromBytes() {
        String json = "{\"code\":0,\"data\":{\"string\":\"你好，中国！\",\"integer\":999}}";
        
        RestResult<Map<String, Object>> restResult = JacksonUtils.toObj(json, RestResult.class);
        assertEquals(0, restResult.getCode());
        assertEquals("你好，中国！", restResult.getData().get("string"));
        assertEquals(999, restResult.getData().get("integer"));
        
        restResult = JacksonUtils.toObj(json, new TypeReference<RestResult<Map<String, Object>>>() {
        });
        assertEquals(0, restResult.getCode());
        assertEquals("你好，中国！", restResult.getData().get("string"));
        assertEquals(999, restResult.getData().get("integer"));
    }
    
    @Test
    void tesToObjForClassWithException() {
        assertThrows(NacosDeserializationException.class, () -> {
            JacksonUtils.toObj("aaa", JsonNode.class);
        });
    }
    
    @Test
    void tesToObjForTypeWithException() {
        assertThrows(NacosDeserializationException.class, () -> {
            JacksonUtils.toObj("aaa", TypeUtils.parameterize(JsonNode.class));
        });
    }
    
    @Test
    void tesToObjForTypeTypeReferenceWithException() {
        assertThrows(NacosDeserializationException.class, () -> {
            JacksonUtils.toObj("aaa", new TypeReference<JsonNode>() {
            });
        });
    }
    
    @JsonPropertyOrder({"aLong", "aInteger", "aBoolean"})
    static class TestOfAtomicObject {
        
        public AtomicLong aLong = new AtomicLong(0);
        
        public AtomicInteger aInteger = new AtomicInteger(1);
        
        public AtomicBoolean aBoolean = new AtomicBoolean(false);
        
        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            
            TestOfAtomicObject that = (TestOfAtomicObject) o;
            
            if (aLong != null ? !(aLong.longValue() == that.aLong.longValue()) : that.aLong != null) {
                return false;
            }
            if (aInteger != null ? !(aInteger.intValue() == that.aInteger.intValue()) : that.aInteger != null) {
                return false;
            }
            return aBoolean != null ? aBoolean.get() == that.aBoolean.get() : that.aBoolean == null;
        }
        
        @Override
        public int hashCode() {
            int result = aLong != null ? aLong.hashCode() : 0;
            result = 31 * result + (aInteger != null ? aInteger.hashCode() : 0);
            result = 31 * result + (aBoolean != null ? aBoolean.hashCode() : 0);
            return result;
        }
    }
    
    static class TestOfAccessModifier {
        
        public String publicAccessModifier = "public";
        
        protected String protectedAccessModifier = "protected";
        
        String defaultAccessModifier = "default";
        
        private String privateAccessModifier = "private";
        
        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            
            TestOfAccessModifier that = (TestOfAccessModifier) o;
            
            if (publicAccessModifier != null ? !publicAccessModifier.equals(that.publicAccessModifier)
                    : that.publicAccessModifier != null) {
                return false;
            }
            if (protectedAccessModifier != null ? !protectedAccessModifier.equals(that.protectedAccessModifier)
                    : that.protectedAccessModifier != null) {
                return false;
            }
            if (defaultAccessModifier != null ? !defaultAccessModifier.equals(that.defaultAccessModifier)
                    : that.defaultAccessModifier != null) {
                return false;
            }
            return privateAccessModifier != null ? privateAccessModifier.equals(that.privateAccessModifier)
                    : that.privateAccessModifier == null;
        }
        
        @Override
        public int hashCode() {
            int result = publicAccessModifier != null ? publicAccessModifier.hashCode() : 0;
            result = 31 * result + (protectedAccessModifier != null ? protectedAccessModifier.hashCode() : 0);
            result = 31 * result + (defaultAccessModifier != null ? defaultAccessModifier.hashCode() : 0);
            result = 31 * result + (privateAccessModifier != null ? privateAccessModifier.hashCode() : 0);
            return result;
        }
    }
    
    @JsonPropertyOrder({"value", "key"})
    static class TestOfGetter {
        
        public String getKey() {
            return "key";
        }
        
        public String getValue() {
            return "value";
        }
        
        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            
            TestOfGetter that = (TestOfGetter) o;
            
            if (getKey() != null ? !getKey().equals(that.getKey()) : that.getKey() != null) {
                return false;
            }
            return getValue() != null ? getValue().equals(that.getValue()) : that.getValue() == null;
        }
        
        @Override
        public int hashCode() {
            int result = getKey() != null ? getKey().hashCode() : 0;
            result = 31 * result + (getValue() != null ? getValue().hashCode() : 0);
            return result;
        }
        
    }
    
    static class TestOfDate {
        
        public Date date = new Date(1626192000000L);
        
        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            
            TestOfDate that = (TestOfDate) o;
            
            return date != null ? date.equals(that.date) : that.date == null;
        }
        
        @Override
        public int hashCode() {
            return date != null ? date.hashCode() : 0;
        }
    }
    
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME)
    @JsonSubTypes({@JsonSubTypes.Type(TestOfAnnotationSub.class)})
    static class TestOfAnnotation {
        
        @JsonFormat(pattern = "yyyy-MM-dd", timezone = "GMT+8")
        public Date date = new Date(1626192000000L);
        
        @JsonProperty(value = "camelCase")
        public String underScore = "value";
        
        @JsonIgnore
        public String ignore = "ignore";
        
        public String nullString = null;
        
        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            
            TestOfAnnotation that = (TestOfAnnotation) o;
            
            if (date != null ? !date.equals(that.date) : that.date != null) {
                return false;
            }
            if (underScore != null ? !underScore.equals(that.underScore) : that.underScore != null) {
                return false;
            }
            if (ignore != null ? !ignore.equals(that.ignore) : that.ignore != null) {
                return false;
            }
            return nullString != null ? nullString.equals(that.nullString) : that.nullString == null;
        }
        
        @Override
        public int hashCode() {
            int result = date != null ? date.hashCode() : 0;
            result = 31 * result + (underScore != null ? underScore.hashCode() : 0);
            result = 31 * result + (ignore != null ? ignore.hashCode() : 0);
            result = 31 * result + (nullString != null ? nullString.hashCode() : 0);
            return result;
        }
    }
    
    static class TestOfAnnotationSub extends TestOfAnnotation {
        
        public String subField = "subField";
        
        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            if (!super.equals(o)) {
                return false;
            }
            
            TestOfAnnotationSub that = (TestOfAnnotationSub) o;
            
            return subField != null ? subField.equals(that.subField) : that.subField == null;
        }
        
        @Override
        public int hashCode() {
            int result = super.hashCode();
            result = 31 * result + (subField != null ? subField.hashCode() : 0);
            return result;
        }
    }
    
    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME)
    static class TestOfParent {
        
        public String parentField = "parentValue";
        
        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            
            TestOfParent that = (TestOfParent) o;
            
            return parentField != null ? parentField.equals(that.parentField) : that.parentField == null;
        }
        
        @Override
        public int hashCode() {
            return parentField != null ? parentField.hashCode() : 0;
        }
    }
    
    static class TestOfChild extends TestOfParent {
        
        public String childField = "childValue";
        
        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            if (!super.equals(o)) {
                return false;
            }
            
            TestOfChild that = (TestOfChild) o;
            
            return childField != null ? childField.equals(that.childField) : that.childField == null;
        }
        
        @Override
        public int hashCode() {
            int result = super.hashCode();
            result = 31 * result + (childField != null ? childField.hashCode() : 0);
            return result;
        }
    }
}
