/*
 * Copyright 1999-2026 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.api.utils.json;

import com.alibaba.nacos.api.model.Page;
import com.alibaba.nacos.api.model.v2.Result;
import org.junit.jupiter.api.Test;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NacosTypeReferenceTest {
    
    @Test
    void testCaptureListType() {
        NacosTypeReference<List<String>> typeReference =
            new NacosTypeReference<List<String>>() {
            };
        
        Type type = typeReference.getType();
        assertTrue(type instanceof ParameterizedType);
        ParameterizedType parameterizedType = (ParameterizedType) type;
        assertEquals(List.class, parameterizedType.getRawType());
        assertEquals(String.class, parameterizedType.getActualTypeArguments()[0]);
    }
    
    @Test
    void testCaptureMapType() {
        NacosTypeReference<Map<String, Object>> typeReference =
            new NacosTypeReference<Map<String, Object>>() {
            };
        
        Type type = typeReference.getType();
        assertTrue(type instanceof ParameterizedType);
        ParameterizedType parameterizedType = (ParameterizedType) type;
        assertEquals(Map.class, parameterizedType.getRawType());
        assertEquals(String.class, parameterizedType.getActualTypeArguments()[0]);
        assertEquals(Object.class, parameterizedType.getActualTypeArguments()[1]);
    }
    
    @Test
    void testCaptureNestedResultPageType() {
        NacosTypeReference<Result<Page<String>>> typeReference =
            new NacosTypeReference<Result<Page<String>>>() {
            };
        
        Type type = typeReference.getType();
        assertTrue(type instanceof ParameterizedType);
        ParameterizedType resultType = (ParameterizedType) type;
        assertEquals(Result.class, resultType.getRawType());
        assertTrue(resultType.getActualTypeArguments()[0] instanceof ParameterizedType);
        ParameterizedType pageType =
            (ParameterizedType) resultType.getActualTypeArguments()[0];
        assertEquals(Page.class, pageType.getRawType());
        assertEquals(String.class, pageType.getActualTypeArguments()[0]);
    }
    
    @Test
    void testRawTypeReferenceThrowsException() {
        assertThrows(IllegalArgumentException.class, RawTypeReference::new);
    }
    
    @SuppressWarnings("rawtypes")
    private static class RawTypeReference extends NacosTypeReference {
    }
}
