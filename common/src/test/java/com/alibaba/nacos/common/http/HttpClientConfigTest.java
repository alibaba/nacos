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

package com.alibaba.nacos.common.http;

import org.junit.Test;

import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

public class HttpClientConfigTest {
    
    @Test
    public void testGetConTimeOutMillis() {
        HttpClientConfig config = HttpClientConfig.builder().setConTimeOutMillis(1000).build();
        assertEquals(1000, config.getConTimeOutMillis());
    }
    
    @Test
    public void testGetReadTimeOutMillis() {
        HttpClientConfig config = HttpClientConfig.builder().setReadTimeOutMillis(2000).build();
        assertEquals(2000, config.getReadTimeOutMillis());
    }
    
    @Test
    public void testGetConnTimeToLive() {
        HttpClientConfig config = HttpClientConfig.builder().setConnectionTimeToLive(3000, TimeUnit.MILLISECONDS)
                .build();
        assertEquals(3000, config.getConnTimeToLive());
    }
    
    @Test
    public void testGetConnTimeToLiveTimeUnit() {
        HttpClientConfig config = HttpClientConfig.builder().setConnectionTimeToLive(4000, TimeUnit.SECONDS).build();
        assertEquals(TimeUnit.SECONDS, config.getConnTimeToLiveTimeUnit());
    }
    
    @Test
    public void testGetConnectionRequestTimeout() {
        HttpClientConfig config = HttpClientConfig.builder().setConnectionRequestTimeout(5000).build();
        assertEquals(5000, config.getConnectionRequestTimeout());
    }
    
    @Test
    public void testGetMaxRedirects() {
        HttpClientConfig config = HttpClientConfig.builder().setMaxRedirects(60).build();
        assertEquals(60, config.getMaxRedirects());
    }
    
    @Test
    public void testGetMaxConnTotal() {
        HttpClientConfig config = HttpClientConfig.builder().setMaxConnTotal(70).build();
        assertEquals(70, config.getMaxConnTotal());
    }
    
    @Test
    public void testGetMaxConnPerRoute() {
        HttpClientConfig config = HttpClientConfig.builder().setMaxConnPerRoute(80).build();
        assertEquals(80, config.getMaxConnPerRoute());
    }
    
    @Test
    public void testGetContentCompressionEnabled() {
        HttpClientConfig config = HttpClientConfig.builder().setContentCompressionEnabled(false).build();
        assertFalse(config.getContentCompressionEnabled());
    }
    
    @Test
    public void testGetIoThreadCount() {
        HttpClientConfig config = HttpClientConfig.builder().setIoThreadCount(90).build();
        assertEquals(90, config.getIoThreadCount());
    }
    
    @Test
    public void testGetUserAgent() {
        HttpClientConfig config = HttpClientConfig.builder().setUserAgent("testUserAgent").build();
        assertEquals("testUserAgent", config.getUserAgent());
    }
}