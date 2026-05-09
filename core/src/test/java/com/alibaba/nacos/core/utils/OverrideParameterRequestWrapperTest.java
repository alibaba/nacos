/*
 * Copyright 1999-2021 Alibaba Group Holding Ltd.
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

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * {@link OverrideParameterRequestWrapper} unit tests.
 *
 * @author chenglu
 * @date 2021-06-10 14:11
 */
class OverrideParameterRequestWrapperTest {
    
    @Test
    void testOverrideParameterRequestWrapper() {
        MockHttpServletRequest httpServletRequest = new MockHttpServletRequest();
        httpServletRequest.addParameter("test1", "value1");
        OverrideParameterRequestWrapper wrapper =
            OverrideParameterRequestWrapper.buildRequest(httpServletRequest);
        String value1 = wrapper.getParameter("test1");
        assertEquals("value1", value1);
        
        wrapper.addParameter("test2", "value2");
        assertEquals("value2", wrapper.getParameter("test2"));
    }
    
    @Test
    void testBuildRequestWithNameAndValue() {
        MockHttpServletRequest req = new MockHttpServletRequest();
        OverrideParameterRequestWrapper wrapper =
            OverrideParameterRequestWrapper.buildRequest(req, "k", "v");
        assertEquals("v", wrapper.getParameter("k"));
    }
    
    @Test
    void testBuildRequestWithMap() {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.addParameter("a", "1");
        Map<String, String[]> append = new HashMap<>();
        append.put("b", new String[] {"2"});
        OverrideParameterRequestWrapper wrapper =
            OverrideParameterRequestWrapper.buildRequest(req, append);
        assertEquals("1", wrapper.getParameter("a"));
        assertEquals("2", wrapper.getParameter("b"));
    }
    
    @Test
    void testGetParameterWhenValuesEmptyReturnsNull() {
        MockHttpServletRequest req = new MockHttpServletRequest();
        OverrideParameterRequestWrapper wrapper = OverrideParameterRequestWrapper.buildRequest(req);
        assertNull(wrapper.getParameter("absent"));
        assertNull(wrapper.getParameterValues("absent"));
    }
    
    @Test
    void testAddParameterWithNullValueDoesNotPut() {
        MockHttpServletRequest req = new MockHttpServletRequest();
        OverrideParameterRequestWrapper wrapper = OverrideParameterRequestWrapper.buildRequest(req);
        wrapper.addParameter("key", null);
        assertNull(wrapper.getParameter("key"));
    }
    
    @Test
    void testGetParameterMapReturnsWrapperParams() {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.addParameter("a", "1");
        req.addParameter("b", "2");
        OverrideParameterRequestWrapper wrapper = OverrideParameterRequestWrapper.buildRequest(req);
        Map<String, String[]> map = wrapper.getParameterMap();
        assertNotNull(map);
        assertEquals(2, map.size());
        assertEquals("1", map.get("a")[0]);
        assertEquals("2", map.get("b")[0]);
        wrapper.addParameter("c", "3");
        assertEquals(3, wrapper.getParameterMap().size());
        assertEquals("3", wrapper.getParameterMap().get("c")[0]);
    }
}
