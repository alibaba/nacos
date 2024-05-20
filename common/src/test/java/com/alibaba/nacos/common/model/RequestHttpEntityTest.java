/*
 * Copyright 1999-2023 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.common.model;

import com.alibaba.nacos.common.http.HttpClientConfig;
import com.alibaba.nacos.common.http.param.Header;
import com.alibaba.nacos.common.http.param.Query;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(MockitoExtension.class)
class RequestHttpEntityTest {
    
    Header header;
    
    Query query;
    
    HttpClientConfig clientConfig;
    
    Object body;
    
    @BeforeEach
    void setUp() throws Exception {
        header = Header.newInstance();
        header.addParam("testHeader", "test");
        query = Query.newInstance();
        query.addParam("testQuery", "test");
        clientConfig = HttpClientConfig.builder().build();
        body = new HashMap<>();
    }
    
    @Test
    void testConstructWithoutConfigAndBody() {
        RequestHttpEntity entity = new RequestHttpEntity(header, query);
        assertTrue(entity.isEmptyBody());
        assertNull(entity.getHttpClientConfig());
        assertNull(entity.getBody());
        assertEquals(header.toString(), entity.getHeaders().toString());
        assertEquals(query.toString(), entity.getQuery().toString());
    }
    
    @Test
    void testConstructWithoutConfigAndQuery() {
        RequestHttpEntity entity = new RequestHttpEntity(header, body);
        assertFalse(entity.isEmptyBody());
        assertNull(entity.getHttpClientConfig());
        assertNull(entity.getQuery());
        assertEquals(header.toString(), entity.getHeaders().toString());
        assertEquals(body, entity.getBody());
    }
    
    @Test
    void testConstructWithoutConfig() {
        RequestHttpEntity entity = new RequestHttpEntity(header, query, body);
        assertFalse(entity.isEmptyBody());
        assertNull(entity.getHttpClientConfig());
        assertEquals(query.toString(), entity.getQuery().toString());
        assertEquals(header.toString(), entity.getHeaders().toString());
        assertEquals(body, entity.getBody());
    }
    
    @Test
    void testConstructFull() {
        RequestHttpEntity entity = new RequestHttpEntity(clientConfig, header, query, body);
        assertFalse(entity.isEmptyBody());
        assertEquals(clientConfig, entity.getHttpClientConfig());
        assertEquals(query.toString(), entity.getQuery().toString());
        assertEquals(header.toString(), entity.getHeaders().toString());
        assertEquals(body, entity.getBody());
    }
}