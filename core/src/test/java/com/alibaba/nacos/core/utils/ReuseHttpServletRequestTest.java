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

package com.alibaba.nacos.core.utils;

import com.alibaba.nacos.common.http.param.MediaType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import javax.servlet.ServletInputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * {@link ReuseHttpServletRequest} unit tests.
 *
 * @author lynn.lqp
 * @date 2023/12/28
 */
class ReuseHttpServletRequestTest {
    
    private MockHttpServletRequest target;
    
    private ReuseHttpServletRequest reuseHttpServletRequest;
    
    @BeforeEach
    void setUp() throws IOException {
        target = new MockHttpServletRequest();
        target.setContentType("application/json");
        target.setParameter("name", "test");
        target.setParameter("value", "123");
        reuseHttpServletRequest = new ReuseHttpServletRequest(target);
    }
    
    @Test
    void testConstructor() throws IOException {
        try {
            new ReuseHttpServletRequest(null);
            fail("IllegalArgumentException expected");
        } catch (IllegalArgumentException e) {
            assertEquals("Request cannot be null", e.getMessage());
        }
        ReuseHttpServletRequest request = new ReuseHttpServletRequest(target);
        assertNotNull(request);
    }
    
    @Test
    void testGetBody() throws Exception {
        Object body = reuseHttpServletRequest.getBody();
        assertNotNull(body);
        assertEquals("name=test&value=123&", body.toString());
        
        target.setContentType(MediaType.MULTIPART_FORM_DATA);
        body = reuseHttpServletRequest.getBody();
        assertNotNull(body);
    }
    
    @Test
    void testGetReader() throws IOException {
        BufferedReader reader = reuseHttpServletRequest.getReader();
        assertNotNull(reader);
    }
    
    @Test
    void testGetParameterMap() {
        Map<String, String[]> parameterMap = reuseHttpServletRequest.getParameterMap();
        assertNotNull(parameterMap);
        assertEquals(2, parameterMap.size());
        assertEquals("test", parameterMap.get("name")[0]);
        assertEquals("123", parameterMap.get("value")[0]);
    }
    
    @Test
    void testGetParameter() {
        String name = reuseHttpServletRequest.getParameter("name");
        assertNotNull(name);
        assertEquals("test", name);
    }
    
    @Test
    void testGetParameterValues() {
        String[] values = reuseHttpServletRequest.getParameterValues("value");
        assertNotNull(values);
        assertEquals(1, values.length);
        assertEquals("123", values[0]);
    }
    
    @Test
    void testGetInputStream() throws IOException {
        ServletInputStream inputStream = reuseHttpServletRequest.getInputStream();
        assertNotNull(inputStream);
        int read = inputStream.read();
        while (read != -1) {
            read = inputStream.read();
        }
    }
}
