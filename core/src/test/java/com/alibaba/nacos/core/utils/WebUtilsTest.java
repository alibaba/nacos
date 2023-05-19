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

import com.alibaba.nacos.common.constant.HttpHeaderConsts;
import org.junit.Assert;
import org.junit.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import java.nio.charset.StandardCharsets;

/**
 * {@link WebUtils} unit tests.
 *
 * @author chenglu
 * @date 2021-06-10 13:33
 */
public class WebUtilsTest {
    
    @Test
    public void testRequired() {
        final String key = "key";
        MockHttpServletRequest servletRequest = new MockHttpServletRequest();
        try {
            WebUtils.required(servletRequest, key);
        } catch (Exception e) {
            Assert.assertTrue(e instanceof IllegalArgumentException);
        }
        
        servletRequest.addParameter(key, "value");
        String val = WebUtils.required(servletRequest, key);
        Assert.assertEquals("value", val);
    }
    
    @Test
    public void testOptional() {
        final String key = "key";
        MockHttpServletRequest servletRequest = new MockHttpServletRequest();
        String val1 = WebUtils.optional(servletRequest, key, "value");
        Assert.assertEquals("value", val1);
        
        servletRequest.addParameter(key, "value1");
        Assert.assertEquals("value1", WebUtils.optional(servletRequest, key, "value"));
    }
    
    @Test
    public void testGetUserAgent() {
        MockHttpServletRequest servletRequest = new MockHttpServletRequest();
        String userAgent = WebUtils.getUserAgent(servletRequest);
        Assert.assertEquals("", userAgent);
        
        servletRequest.addHeader(HttpHeaderConsts.CLIENT_VERSION_HEADER, "0");
        Assert.assertEquals("0", WebUtils.getUserAgent(servletRequest));
        
        servletRequest.addHeader(HttpHeaderConsts.USER_AGENT_HEADER, "1");
        Assert.assertEquals("1", WebUtils.getUserAgent(servletRequest));
    }
    
    @Test
    public void testGetAcceptEncoding() {
        MockHttpServletRequest servletRequest = new MockHttpServletRequest();
        Assert.assertEquals(StandardCharsets.UTF_8.name(), WebUtils.getAcceptEncoding(servletRequest));
        
        servletRequest.addHeader(HttpHeaderConsts.ACCEPT_ENCODING, "gzip, deflate, br");
        Assert.assertEquals("gzip", WebUtils.getAcceptEncoding(servletRequest));
    }
}
