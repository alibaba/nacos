/*
 * Copyright 1999-2022 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.common.utils.url;

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * HttpUrlComponentsBuilderTest.
 *
 * @author Weizhan▪Yun
 * @date 2022/12/29 22:38
 */
public class HttpUrlComponentsBuilderTest {
    
    private static final String CANONICAL_URL = "http://localhost:80";
    
    private HttpUrlComponentsBuilder builder;
    
    @Before
    public void setUp() throws Exception {
        builder = buildCanonicalUrl();
    }
    
    private HttpUrlComponentsBuilder buildCanonicalUrl() {
        return HttpUrlComponentsBuilder.fromHttpUrl(CANONICAL_URL);
    }
    
    @Test
    public void testFromHttpUrl() {
        HttpUrlComponents localhost = buildCanonicalUrl().build();
        
        assertEquals("http", localhost.getScheme());
        assertEquals("localhost", localhost.getHost());
        assertEquals("80", localhost.getPort());
        assertEquals("/", localhost.getPath());
    }
    
    @Test
    public void testFromHttpUrlWithHost() {
        HttpUrlComponents localhost = HttpUrlComponentsBuilder.fromHttpUrl("http://localhost").build();
        
        assertEquals("http", localhost.getScheme());
        assertEquals("localhost", localhost.getHost());
        assertEquals("80", localhost.getPort());
        assertEquals("/", localhost.getPath());
    }
    
    @Test
    public void testFromHttpUrlWithDomain() {
        HttpUrlComponents localhost = HttpUrlComponentsBuilder.fromHttpUrl("https://www.nacos.io/").build();
        
        assertEquals("https", localhost.getScheme());
        assertEquals("www.nacos.io", localhost.getHost());
        assertEquals("443", localhost.getPort());
        assertEquals("/", localhost.getPath());
    }
    
    @Test
    public void testFromHttpUrlWithDomainPort() {
        HttpUrlComponents localhost = HttpUrlComponentsBuilder.fromHttpUrl("https://www.nacos.io:8080//").build();
        
        assertEquals("https", localhost.getScheme());
        assertEquals("www.nacos.io", localhost.getHost());
        assertEquals("8080", localhost.getPort());
        assertEquals("/", localhost.getPath());
    }
    
    @Test
    public void testFromHttpUrlWithHttpsHost() {
        HttpUrlComponents localhost = HttpUrlComponentsBuilder.fromHttpUrl("https://localhost").build();
        
        assertEquals("https", localhost.getScheme());
        assertEquals("localhost", localhost.getHost());
        assertEquals("443", localhost.getPort());
        assertEquals("/", localhost.getPath());
    }
    
    @Test
    public void testFromHttpUrlWithHostPort() {
        HttpUrlComponents localhost = HttpUrlComponentsBuilder.fromHttpUrl("http://localhost:8080").build();
        
        assertEquals("http", localhost.getScheme());
        assertEquals("localhost", localhost.getHost());
        assertEquals("8080", localhost.getPort());
        assertEquals("/", localhost.getPath());
    }
    
    @Test
    public void testFromHttpUrlWithIpv4Port() {
        HttpUrlComponents localhost = HttpUrlComponentsBuilder.fromHttpUrl("http://127.0.0.1:8080").build();
        
        assertEquals("http", localhost.getScheme());
        assertEquals("127.0.0.1", localhost.getHost());
        assertEquals("8080", localhost.getPort());
        assertEquals("/", localhost.getPath());
    }
    
    @Test
    public void testFromHttpUrlWithIpv6Port() {
        HttpUrlComponents localhost = HttpUrlComponentsBuilder.fromHttpUrl("http://[::1]").build();
        
        assertEquals("http", localhost.getScheme());
        assertEquals("[::1]", localhost.getHost());
        assertEquals("80", localhost.getPort());
        assertEquals("/", localhost.getPath());
    }
    
    @Test
    public void testFromHttpUrlOnlyHost() {
        try {
            HttpUrlComponentsBuilder.fromHttpUrl("localhost");
        } catch (Exception e) {
            assertEquals(IllegalArgumentException.class, e.getClass());
        }
    }
    
    @Test
    public void testHasScheme() {
        assertTrue(builder.hasScheme());
    }
    
    @Test
    public void testHasHost() {
        assertTrue(builder.hasHost());
    }
    
    @Test
    public void testHasPort() {
        assertTrue(builder.hasPort());
        
        HttpUrlComponentsBuilder httpUrlComponentsBuilder = buildCanonicalUrl();
        
        httpUrlComponentsBuilder.port(-1);
        assertFalse(httpUrlComponentsBuilder.hasPort());
        
        httpUrlComponentsBuilder.port(1234);
        assertTrue(buildCanonicalUrl().hasPort());
        
        httpUrlComponentsBuilder.port("1111");
        assertEquals("1111", httpUrlComponentsBuilder.build().getPort());
    }
    
    @Test
    public void testAddPath() {
        HttpUrlComponentsBuilder httpUrlComponentsBuilder = buildCanonicalUrl();
        
        httpUrlComponentsBuilder.addPath("/a");
        assertEquals("/a", httpUrlComponentsBuilder.build().getPath());
        
        httpUrlComponentsBuilder.addPath("/b");
        assertEquals("/a/b", httpUrlComponentsBuilder.build().getPath());
        
        httpUrlComponentsBuilder.addPath("//c");
        assertEquals("/a/b/c", httpUrlComponentsBuilder.build().getPath());
        
        httpUrlComponentsBuilder.addPath("/d/");
        assertEquals("/a/b/c/d", httpUrlComponentsBuilder.build().getPath());
    }
    
    @Test
    public void testPath() {
        HttpUrlComponentsBuilder httpUrlComponentsBuilder = buildCanonicalUrl();
        
        httpUrlComponentsBuilder.path("/test");
        assertEquals("/test", httpUrlComponentsBuilder.build().getPath());
        
        httpUrlComponentsBuilder.path("/test2/");
        assertEquals("/test2", httpUrlComponentsBuilder.build().getPath());
    }
}