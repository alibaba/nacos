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

import com.alibaba.nacos.common.http.HttpUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.core.io.Resource;
import org.springframework.mock.web.MockMultipartHttpServletRequest;
import org.springframework.util.MultiValueMap;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * {@link ReuseUploadFileHttpServletRequest} unit tests.
 *
 * @author lynn.lqp
 * @date 2023/12/28
 */

class ReuseUploadFileHttpServletRequestTest {
    
    private ReuseUploadFileHttpServletRequest reuseUploadFileHttpServletRequest;
    
    private MockMultipartHttpServletRequest mockMultipartHttpServletRequest;
    
    @BeforeEach
    void setUp() throws MultipartException {
        mockMultipartHttpServletRequest = Mockito.mock(MockMultipartHttpServletRequest.class);
        when(mockMultipartHttpServletRequest.getParameterMap()).thenReturn(new HashMap<>());
        reuseUploadFileHttpServletRequest =
            new ReuseUploadFileHttpServletRequest(mockMultipartHttpServletRequest);
    }
    
    @Test
    void testGetParameterMapEmpty() {
        Map<String, String[]> parameterMap = reuseUploadFileHttpServletRequest.getParameterMap();
        assertEquals(0, parameterMap.size());
    }
    
    @Test
    void testGetParameterEmpty() {
        assertNull(reuseUploadFileHttpServletRequest.getParameter("nonExistentParam"));
    }
    
    @Test
    void testGetParameterValuesEmpty() {
        assertNull(reuseUploadFileHttpServletRequest.getParameterValues("nonExistentParam"));
    }
    
    @Test
    void testGetBodyWithoutFile() throws Exception {
        Object body = reuseUploadFileHttpServletRequest.getBody();
        assertEquals(HttpUtils.encodingParams(HttpUtils.translateParameterMap(new HashMap<>()),
            StandardCharsets.UTF_8.name()), body);
    }
    
    @Test
    void testGetParameterAndValuesWhenPresent() {
        Map<String, String[]> paramMap = new HashMap<>();
        paramMap.put("k", new String[] {"v1", "v2"});
        when(mockMultipartHttpServletRequest.getParameterMap()).thenReturn(paramMap);
        ReuseUploadFileHttpServletRequest wrapper =
            new ReuseUploadFileHttpServletRequest(mockMultipartHttpServletRequest);
        assertEquals("v1", wrapper.getParameter("k"));
        assertEquals(2, wrapper.getParameterValues("k").length);
        assertEquals("v1", wrapper.getParameterValues("k")[0]);
    }
    
    @Test
    void testGetBodyWithFileViaSpyGetFile() throws Exception {
        ReuseUploadFileHttpServletRequest real =
            new ReuseUploadFileHttpServletRequest(mockMultipartHttpServletRequest);
        ReuseUploadFileHttpServletRequest spy = Mockito.spy(real);
        MultipartFile mockFile = mock(MultipartFile.class);
        Resource mockResource = mock(Resource.class);
        when(mockFile.getResource()).thenReturn(mockResource);
        doReturn(mockFile).when(spy).getFile(eq("file"));
        Object body = spy.getBody();
        assertNotNull(body);
        assertTrue(body instanceof MultiValueMap);
        assertEquals(mockResource, ((MultiValueMap<String, Object>) body).getFirst("file"));
    }
}
