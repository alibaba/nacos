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

package com.alibaba.nacos.common.http;

import com.alibaba.nacos.api.common.Constants;
import com.alibaba.nacos.api.exception.runtime.NacosRuntimeException;
import com.alibaba.nacos.common.constant.HttpHeaderConsts;
import com.alibaba.nacos.common.http.param.Header;
import com.alibaba.nacos.common.http.param.Query;
import org.apache.http.HttpEntity;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.conn.ConnectTimeoutException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

@ExtendWith(MockitoExtension.class)
class HttpUtilsTest {
    
    String exceptUrl = "http://127.0.0.1:8080/v1/api/test";
    
    @Test
    void testBuildHttpUrl1() {
        String targetUrl = HttpUtils.buildUrl(false, "127.0.0.1:8080", "/v1/api/test");
        assertEquals(exceptUrl, targetUrl);
        targetUrl = HttpUtils.buildUrl(false, "127.0.0.1:8080", "v1/api/test");
        assertEquals(exceptUrl, targetUrl);
        targetUrl = HttpUtils.buildUrl(false, "127.0.0.1:8080", "/v1", "/api/test");
        assertEquals(exceptUrl, targetUrl);
        targetUrl = HttpUtils.buildUrl(false, "127.0.0.1:8080", "/v1", "/api", "/test");
        assertEquals(exceptUrl, targetUrl);
        targetUrl = HttpUtils.buildUrl(false, "127.0.0.1:8080", "/v1", "/api/", "/test");
        assertEquals(exceptUrl, targetUrl);
        targetUrl = HttpUtils.buildUrl(false, "127.0.0.1:8080", "/v1", "", "/api/", "/test");
        assertEquals(exceptUrl, targetUrl);
        targetUrl = HttpUtils.buildUrl(false, "127.0.0.1:8080", "/v1", "", null, "/api/", "/test");
        assertEquals(exceptUrl, targetUrl);
        targetUrl = HttpUtils.buildUrl(false, "127.0.0.1:8080", "/v1", "/api/", "test");
        assertEquals(exceptUrl, targetUrl);
        targetUrl = HttpUtils.buildUrl(true, "127.0.0.1:8080", "/v1", "", null, "/api/", "/test");
        assertEquals("https://127.0.0.1:8080/v1/api/test", targetUrl);
    }
    
    @Test
    void testBuildHttpUrl2() {
        assertThrows(IllegalArgumentException.class, () -> {
            String targetUrl = HttpUtils.buildUrl(false, "127.0.0.1:8080", "//v1/api/test");
            assertNotEquals(exceptUrl, targetUrl);
        });
    }
    
    @Test
    void testBuildHttpUrl3() {
        assertThrows(IllegalArgumentException.class, () -> {
            String targetUrl = HttpUtils.buildUrl(false, "127.0.0.1:8080", "/v1", "/api//", "test");
            assertNotEquals(exceptUrl, targetUrl);
        });
    }
    
    @Test
    void testInitRequestHeader() {
        BaseHttpMethod.HttpGetWithEntity httpRequest = new BaseHttpMethod.HttpGetWithEntity("");
        Header header = Header.newInstance();
        header.addParam("k", "v");
        
        HttpUtils.initRequestHeader(httpRequest, header);
        
        org.apache.http.Header[] headers = httpRequest.getHeaders("k");
        assertEquals(1, headers.length);
        assertEquals("k", headers[0].getName());
        assertEquals("v", headers[0].getValue());
    }
    
    @Test
    void testInitRequestEntity1() throws Exception {
        BaseHttpMethod.HttpGetWithEntity httpRequest = new BaseHttpMethod.HttpGetWithEntity("");
        Header header = Header.newInstance();
        header.addParam(HttpHeaderConsts.CONTENT_TYPE, "text/html");
        
        HttpUtils.initRequestEntity(httpRequest, new byte[] {0, 1, 0, 1}, header);
        
        HttpEntity entity = httpRequest.getEntity();
        InputStream contentStream = entity.getContent();
        byte[] bytes = new byte[contentStream.available()];
        contentStream.read(bytes);
        assertArrayEquals(new byte[] {0, 1, 0, 1}, bytes);
        assertEquals(HttpHeaderConsts.CONTENT_TYPE, entity.getContentType().getName());
        assertEquals("text/html; charset=UTF-8", entity.getContentType().getValue());
    }
    
    @Test
    void testInitRequestEntity2() throws Exception {
        BaseHttpMethod.HttpGetWithEntity httpRequest = new BaseHttpMethod.HttpGetWithEntity("");
        Header header = Header.newInstance();
        header.addParam(HttpHeaderConsts.CONTENT_TYPE, "text/html");
        
        HttpUtils.initRequestEntity(httpRequest, Collections.singletonMap("k", "v"), header);
        
        HttpEntity entity = httpRequest.getEntity();
        InputStream contentStream = entity.getContent();
        byte[] bytes = new byte[contentStream.available()];
        contentStream.read(bytes);
        assertEquals("{\"k\":\"v\"}", new String(bytes, Constants.ENCODE));
        assertEquals(HttpHeaderConsts.CONTENT_TYPE, entity.getContentType().getName());
        assertEquals("text/html; charset=UTF-8", entity.getContentType().getValue());
    }
    
    @Test
    void testInitRequestEntity3() throws Exception {
        BaseHttpMethod.HttpGetWithEntity httpRequest = new BaseHttpMethod.HttpGetWithEntity("");
        Header header = Header.newInstance();
        header.addParam(HttpHeaderConsts.CONTENT_TYPE, "text/html");
        
        HttpUtils.initRequestEntity(httpRequest, "common text", header);
        
        HttpEntity entity = httpRequest.getEntity();
        InputStream contentStream = entity.getContent();
        byte[] bytes = new byte[contentStream.available()];
        contentStream.read(bytes);
        assertEquals("common text", new String(bytes, Constants.ENCODE));
        assertEquals(HttpHeaderConsts.CONTENT_TYPE, entity.getContentType().getName());
        assertEquals("text/html; charset=UTF-8", entity.getContentType().getValue());
    }
    
    @Test
    void testInitRequestEntity4() throws Exception {
        BaseHttpMethod.HttpGetWithEntity httpRequest = new BaseHttpMethod.HttpGetWithEntity("");
        
        HttpUtils.initRequestEntity(httpRequest, null, null);
        
        // nothing change
        assertEquals(new BaseHttpMethod.HttpGetWithEntity("").getEntity(), httpRequest.getEntity());
        assertArrayEquals(new BaseHttpMethod.HttpGetWithEntity("").getAllHeaders(), httpRequest.getAllHeaders());
    }
    
    @Test
    void testInitRequestEntity5() throws Exception {
        HttpDelete httpDelete = new HttpDelete("");
        
        HttpUtils.initRequestEntity(httpDelete, null, null);
        
        // nothing change
        assertEquals(new HttpDelete("").getMethod(), httpDelete.getMethod());
        assertArrayEquals(new HttpDelete("").getAllHeaders(), httpDelete.getAllHeaders());
    }
    
    @Test
    void testInitRequestFromEntity1() throws Exception {
        BaseHttpMethod.HttpGetWithEntity httpRequest = new BaseHttpMethod.HttpGetWithEntity("");
        
        HttpUtils.initRequestFromEntity(httpRequest, Collections.singletonMap("k", "v"), "UTF-8");
        
        HttpEntity entity = httpRequest.getEntity();
        InputStream contentStream = entity.getContent();
        byte[] bytes = new byte[contentStream.available()];
        contentStream.read(bytes);
        assertEquals("k=v", new String(bytes, StandardCharsets.UTF_8));
    }
    
    @Test
    void testInitRequestFromEntity2() throws Exception {
        BaseHttpMethod.HttpGetWithEntity httpRequest = new BaseHttpMethod.HttpGetWithEntity("");
        
        HttpUtils.initRequestFromEntity(httpRequest, null, "UTF-8");
        
        // nothing change
        assertEquals(new BaseHttpMethod.HttpGetWithEntity("").getEntity(), httpRequest.getEntity());
    }
    
    @Test
    void testInitRequestFromEntity3() throws Exception {
        BaseHttpMethod.HttpGetWithEntity httpRequest = new BaseHttpMethod.HttpGetWithEntity("");
        
        HttpUtils.initRequestFromEntity(httpRequest, Collections.emptyMap(), "UTF-8");
        
        // nothing change
        assertEquals(new BaseHttpMethod.HttpGetWithEntity("").getEntity(), httpRequest.getEntity());
    }
    
    @Test
    void testInitRequestFromEntity4() throws Exception {
        BaseHttpMethod.HttpGetWithEntity httpRequest = new BaseHttpMethod.HttpGetWithEntity("");
        
        HttpUtils.initRequestFromEntity(mock(HttpRequestBase.class), Collections.emptyMap(), "UTF-8");
        
        // nothing change
        assertEquals(new BaseHttpMethod.HttpGetWithEntity("").getEntity(), httpRequest.getEntity());
    }
    
    @Test
    void testInitRequestFromEntity5() throws Exception {
        HttpDelete httpDelete = new HttpDelete("");
        
        HttpUtils.initRequestFromEntity(httpDelete, Collections.singletonMap("k", "v"), "UTF-8");
        
        // nothing change
        assertEquals(new HttpDelete("").getMethod(), httpDelete.getMethod());
        assertArrayEquals(new HttpDelete("").getAllHeaders(), httpDelete.getAllHeaders());
    }
    
    @Test
    void testTranslateParameterMap() throws Exception {
        Map<String, String[]> map = Collections.singletonMap("K", new String[] {"V1", "V2"});
        Map<String, String> resultMap = HttpUtils.translateParameterMap(map);
        assertEquals(Collections.singletonMap("K", "V1"), resultMap);
    }
    
    @Test
    void testDecode() throws UnsupportedEncodingException {
        // % - %25, { - %7B, } - %7D
        assertEquals("{k,v}", HttpUtils.decode("%7Bk,v%7D", "UTF-8"));
        assertEquals("{k,v}", HttpUtils.decode("%257Bk,v%257D", "UTF-8"));
    }
    
    @Test
    void testEncodingParamsMapWithNullOrEmpty() throws UnsupportedEncodingException {
        assertNull(HttpUtils.encodingParams((Map<String, String>) null, "UTF-8"));
        assertNull(HttpUtils.encodingParams(Collections.emptyMap(), "UTF-8"));
    }
    
    @Test
    void testEncodingParamsMap() throws UnsupportedEncodingException {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("a", "");
        params.put("b", "x");
        params.put("uriChar", "=");
        params.put("chinese", "测试");
        assertEquals("b=x&uriChar=%3D&chinese=%E6%B5%8B%E8%AF%95&", HttpUtils.encodingParams(params, "UTF-8"));
    }
    
    @Test
    void testEncodingParamsListWithNull() throws UnsupportedEncodingException {
        assertNull(HttpUtils.encodingParams((List<String>) null, "UTF-8"));
    }
    
    @Test
    void testEncodingParamsList() throws UnsupportedEncodingException {
        List<String> params = new LinkedList<>();
        params.add("a");
        params.add("");
        params.add("b");
        params.add("x");
        params.add("uriChar");
        params.add("=");
        params.add("chinese");
        params.add("测试");
        assertEquals("a=&b=x&uriChar=%3D&chinese=%E6%B5%8B%E8%AF%95", HttpUtils.encodingParams(params, "UTF-8"));
    }
    
    @Test
    void testBuildUriForEmptyQuery() throws URISyntaxException {
        URI actual = HttpUtils.buildUri("www.aliyun.com", null);
        assertEquals("www.aliyun.com", actual.toString());
        actual = HttpUtils.buildUri("www.aliyun.com", new Query());
        assertEquals("www.aliyun.com", actual.toString());
    }
    
    @Test
    void testBuildUri() throws URISyntaxException {
        Query query = new Query();
        query.addParam("a", "");
        query.addParam("b", "x");
        query.addParam("uriChar", "=");
        query.addParam("chinese", "测试");
        URI actual = HttpUtils.buildUri("www.aliyun.com", query);
        assertEquals("www.aliyun.com?" + query.toQueryUrl(), actual.toString());
    }
    
    @Test
    void testIsTimeoutException() {
        assertFalse(HttpUtils.isTimeoutException(new NacosRuntimeException(0)));
        assertTrue(HttpUtils.isTimeoutException(new TimeoutException()));
        assertTrue(HttpUtils.isTimeoutException(new SocketTimeoutException()));
        assertTrue(HttpUtils.isTimeoutException(new ConnectTimeoutException()));
        assertTrue(HttpUtils.isTimeoutException(new NacosRuntimeException(0, new TimeoutException())));
    }
}