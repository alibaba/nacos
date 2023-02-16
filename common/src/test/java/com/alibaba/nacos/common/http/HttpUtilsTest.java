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
import com.alibaba.nacos.common.constant.HttpHeaderConsts;
import com.alibaba.nacos.common.http.param.Header;
import org.apache.http.HttpEntity;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpRequestBase;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Map;

import static org.mockito.Mockito.mock;

@RunWith(MockitoJUnitRunner.class)
public class HttpUtilsTest {
    
    String exceptUrl = "http://127.0.0.1:8080/v1/api/test";
    
    @Test
    public void testBuildHttpUrl1() {
        String targetUrl = HttpUtils.buildUrl(false, "127.0.0.1:8080", "/v1/api/test");
        Assert.assertEquals(exceptUrl, targetUrl);
        targetUrl = HttpUtils.buildUrl(false, "127.0.0.1:8080", "v1/api/test");
        Assert.assertEquals(exceptUrl, targetUrl);
        targetUrl = HttpUtils.buildUrl(false, "127.0.0.1:8080", "/v1", "/api/test");
        Assert.assertEquals(exceptUrl, targetUrl);
        targetUrl = HttpUtils.buildUrl(false, "127.0.0.1:8080", "/v1", "/api", "/test");
        Assert.assertEquals(exceptUrl, targetUrl);
        targetUrl = HttpUtils.buildUrl(false, "127.0.0.1:8080", "/v1", "/api/", "/test");
        Assert.assertEquals(exceptUrl, targetUrl);
        targetUrl = HttpUtils.buildUrl(false, "127.0.0.1:8080", "/v1", "", "/api/", "/test");
        Assert.assertEquals(exceptUrl, targetUrl);
        targetUrl = HttpUtils.buildUrl(false, "127.0.0.1:8080", "/v1", "", null, "/api/", "/test");
        Assert.assertEquals(exceptUrl, targetUrl);
        targetUrl = HttpUtils.buildUrl(false, "127.0.0.1:8080", "/v1", "/api/", "test");
        Assert.assertEquals(exceptUrl, targetUrl);
        targetUrl = HttpUtils.buildUrl(true, "127.0.0.1:8080", "/v1", "", null, "/api/", "/test");
        Assert.assertEquals("https://127.0.0.1:8080/v1/api/test", targetUrl);
    }
    
    @Test(expected = IllegalArgumentException.class)
    public void testBuildHttpUrl2() {
        String targetUrl = HttpUtils.buildUrl(false, "127.0.0.1:8080", "//v1/api/test");
        Assert.assertNotEquals(exceptUrl, targetUrl);
    }
    
    @Test(expected = IllegalArgumentException.class)
    public void testBuildHttpUrl3() {
        String targetUrl = HttpUtils.buildUrl(false, "127.0.0.1:8080", "/v1", "/api//", "test");
        Assert.assertNotEquals(exceptUrl, targetUrl);
    }
    
    @Test
    public void testInitRequestHeader() {
        BaseHttpMethod.HttpGetWithEntity httpRequest = new BaseHttpMethod.HttpGetWithEntity("");
        Header header = Header.newInstance();
        header.addParam("k", "v");
        
        HttpUtils.initRequestHeader(httpRequest, header);
        
        org.apache.http.Header[] headers = httpRequest.getHeaders("k");
        Assert.assertEquals(1, headers.length);
        Assert.assertEquals("k", headers[0].getName());
        Assert.assertEquals("v", headers[0].getValue());
    }
    
    @Test
    public void testInitRequestEntity1() throws Exception {
        BaseHttpMethod.HttpGetWithEntity httpRequest = new BaseHttpMethod.HttpGetWithEntity("");
        Header header = Header.newInstance();
        header.addParam(HttpHeaderConsts.CONTENT_TYPE, "text/html");
        
        HttpUtils.initRequestEntity(httpRequest, new byte[] {0, 1, 0, 1}, header);
    
        HttpEntity entity = httpRequest.getEntity();
        InputStream contentStream = entity.getContent();
        byte[] bytes = new byte[contentStream.available()];
        contentStream.read(bytes);
        Assert.assertArrayEquals(new byte[] {0, 1, 0, 1}, bytes);
        Assert.assertEquals(HttpHeaderConsts.CONTENT_TYPE, entity.getContentType().getName());
        Assert.assertEquals("text/html; charset=UTF-8", entity.getContentType().getValue());
    }
    
    @Test
    public void testInitRequestEntity2() throws Exception {
        BaseHttpMethod.HttpGetWithEntity httpRequest = new BaseHttpMethod.HttpGetWithEntity("");
        Header header = Header.newInstance();
        header.addParam(HttpHeaderConsts.CONTENT_TYPE, "text/html");
        
        HttpUtils.initRequestEntity(httpRequest, Collections.singletonMap("k", "v"), header);
        
        HttpEntity entity = httpRequest.getEntity();
        InputStream contentStream = entity.getContent();
        byte[] bytes = new byte[contentStream.available()];
        contentStream.read(bytes);
        Assert.assertEquals("{\"k\":\"v\"}", new String(bytes, Constants.ENCODE));
        Assert.assertEquals(HttpHeaderConsts.CONTENT_TYPE, entity.getContentType().getName());
        Assert.assertEquals("text/html; charset=UTF-8", entity.getContentType().getValue());
    }
    
    @Test
    public void testInitRequestEntity3() throws Exception {
        BaseHttpMethod.HttpGetWithEntity httpRequest = new BaseHttpMethod.HttpGetWithEntity("");
        Header header = Header.newInstance();
        header.addParam(HttpHeaderConsts.CONTENT_TYPE, "text/html");
        
        HttpUtils.initRequestEntity(httpRequest, "common text", header);
        
        HttpEntity entity = httpRequest.getEntity();
        InputStream contentStream = entity.getContent();
        byte[] bytes = new byte[contentStream.available()];
        contentStream.read(bytes);
        Assert.assertEquals("common text", new String(bytes, Constants.ENCODE));
        Assert.assertEquals(HttpHeaderConsts.CONTENT_TYPE, entity.getContentType().getName());
        Assert.assertEquals("text/html; charset=UTF-8", entity.getContentType().getValue());
    }
    
    @Test
    public void testInitRequestEntity4() throws Exception {
        BaseHttpMethod.HttpGetWithEntity httpRequest = new BaseHttpMethod.HttpGetWithEntity("");
        
        HttpUtils.initRequestEntity(httpRequest, null, null);
        
        // nothing change
        Assert.assertEquals(new BaseHttpMethod.HttpGetWithEntity("").getEntity(), httpRequest.getEntity());
        Assert.assertArrayEquals(new BaseHttpMethod.HttpGetWithEntity("").getAllHeaders(), httpRequest.getAllHeaders());
    }
    
    @Test
    public void testInitRequestEntity5() throws Exception {
        HttpDelete httpDelete = new HttpDelete("");
    
        HttpUtils.initRequestEntity(httpDelete, null, null);
        
        // nothing change
        Assert.assertEquals(new HttpDelete("").getMethod(), httpDelete.getMethod());
        Assert.assertArrayEquals(new HttpDelete("").getAllHeaders(), httpDelete.getAllHeaders());
    }
    
    @Test
    public void testInitRequestFromEntity1() throws Exception {
        BaseHttpMethod.HttpGetWithEntity httpRequest = new BaseHttpMethod.HttpGetWithEntity("");
        
        HttpUtils.initRequestFromEntity(httpRequest, Collections.singletonMap("k", "v"), "UTF-8");
    
        HttpEntity entity = httpRequest.getEntity();
        InputStream contentStream = entity.getContent();
        byte[] bytes = new byte[contentStream.available()];
        contentStream.read(bytes);
        Assert.assertEquals("k=v", new String(bytes, StandardCharsets.UTF_8));
    }
    
    @Test
    public void testInitRequestFromEntity2() throws Exception {
        BaseHttpMethod.HttpGetWithEntity httpRequest = new BaseHttpMethod.HttpGetWithEntity("");
    
        HttpUtils.initRequestFromEntity(httpRequest, null, "UTF-8");
    
        // nothing change
        Assert.assertEquals(new BaseHttpMethod.HttpGetWithEntity("").getEntity(), httpRequest.getEntity());
    }
    
    @Test
    public void testInitRequestFromEntity3() throws Exception {
        BaseHttpMethod.HttpGetWithEntity httpRequest = new BaseHttpMethod.HttpGetWithEntity("");
        
        HttpUtils.initRequestFromEntity(httpRequest, Collections.emptyMap(), "UTF-8");
        
        // nothing change
        Assert.assertEquals(new BaseHttpMethod.HttpGetWithEntity("").getEntity(), httpRequest.getEntity());
    }
    
    @Test
    public void testInitRequestFromEntity4() throws Exception {
        BaseHttpMethod.HttpGetWithEntity httpRequest = new BaseHttpMethod.HttpGetWithEntity("");
        
        HttpUtils.initRequestFromEntity(mock(HttpRequestBase.class), Collections.emptyMap(), "UTF-8");
        
        // nothing change
        Assert.assertEquals(new BaseHttpMethod.HttpGetWithEntity("").getEntity(), httpRequest.getEntity());
    }
    
    @Test
    public void testInitRequestFromEntity5() throws Exception {
        HttpDelete httpDelete = new HttpDelete("");
        
        HttpUtils.initRequestFromEntity(httpDelete, Collections.singletonMap("k", "v"), "UTF-8");
        
        // nothing change
        Assert.assertEquals(new HttpDelete("").getMethod(), httpDelete.getMethod());
        Assert.assertArrayEquals(new HttpDelete("").getAllHeaders(), httpDelete.getAllHeaders());
    }
    
    @Test
    public void testTranslateParameterMap() throws Exception {
        Map<String, String[]> map = Collections.singletonMap("K", new String[] {"V1", "V2"});
        Map<String, String> resultMap = HttpUtils.translateParameterMap(map);
        Assert.assertEquals(Collections.singletonMap("K", "V1"), resultMap);
    }
    
    @Test
    public void testDecode() throws UnsupportedEncodingException {
        // % - %25, { - %7B, } - %7D
        Assert.assertEquals("{k,v}", HttpUtils.decode("%7Bk,v%7D", "UTF-8"));
        Assert.assertEquals("{k,v}", HttpUtils.decode("%257Bk,v%257D", "UTF-8"));
    }
}