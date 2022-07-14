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

package com.alibaba.nacos.address;

import com.alibaba.nacos.common.codec.Base64;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.RequestEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.util.LinkedMultiValueMap;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT, properties = {
        "spring.security.user.password=123456", "spring.security.user.name=user"})
public class AddressServerControllerTests {
    
    private static final String PRODUCT_CONFIG = "config";
    
    private static final String PRODUCT_NAMING = "naming";
    
    private static final String HTTP_BASIC_INFO = getHttpBasicInfo();
    
    @Autowired
    private TestRestTemplate restTemplate;
    
    @BeforeClass
    public static void before() {
        System.setProperty("nacos.standalone", "true");
        System.setProperty("embeddedStorage", "true");
    }
    
    @AfterClass
    public static void teardown() {
        System.clearProperty("nacos.standalone");
        System.clearProperty("embeddedStorage");
    }
    
    private static String getHttpBasicInfo() {
        String userName = "user";
        String password = "123456";
        
        String info = userName + ":" + password;
        
        final byte[] bytes = Base64.encodeBase64(info.getBytes(StandardCharsets.UTF_8));
        
        return "Basic " + new String(bytes, StandardCharsets.UTF_8);
    }
    
    @Test
    public void postClusterWithoutLogin() {
        
        String ips = "127.0.0.100,127.0.0.102,127.0.0.104";
        LinkedMultiValueMap<String, String> params = new LinkedMultiValueMap<>(1);
        params.add("ips", ips);
        
        final ResponseEntity<String> postClusterResponseEntity = restTemplate.exchange(
                RequestEntity.post("/nacos/v1/as/nodes").body(params), String.class);
        
        Assert.assertEquals(postClusterResponseEntity.getStatusCode(), HttpStatus.UNAUTHORIZED);
    }
    
    @Test
    public void postCluster() throws InterruptedException {
        
        String ips = "127.0.0.100,127.0.0.102,127.0.0.104";
        LinkedMultiValueMap<String, String> params = new LinkedMultiValueMap<>(1);
        params.add("ips", ips);
        
        final ResponseEntity<String> postClusterResponseEntity = restTemplate.exchange(
                RequestEntity.post("/nacos/v1/as/nodes").header(HttpHeaders.AUTHORIZATION, HTTP_BASIC_INFO)
                        .body(params), String.class);
        
        Assert.assertNotNull(postClusterResponseEntity);
        Assert.assertEquals(HttpStatus.OK.value(), postClusterResponseEntity.getStatusCodeValue());
        
        TimeUnit.MILLISECONDS.sleep(500L);
        
        final ResponseEntity<String> getClusterResponseEntity = restTemplate.exchange(
                RequestEntity.get("/nacos/serverlist").build(), String.class);
        
        Assert.assertNotNull(getClusterResponseEntity);
        Assert.assertEquals(HttpStatus.OK.value(), getClusterResponseEntity.getStatusCodeValue());
        
    }
    
    @Test
    public void deleteClusterWithoutLogin() {
        LinkedMultiValueMap<String, String> params = new LinkedMultiValueMap<>(1);
        params.add("ips", "127.0.0.104");
        
        final ResponseEntity<String> postClusterResponseEntity = restTemplate.exchange(
                RequestEntity.post("/nacos/v1/as/nodes").body(params), String.class);
        Assert.assertEquals(postClusterResponseEntity.getStatusCode(), HttpStatus.UNAUTHORIZED);
    }
    
    @Test
    public void deleteCluster() throws InterruptedException {
        
        LinkedMultiValueMap<String, String> params = new LinkedMultiValueMap<>(1);
        params.add("ips", "127.0.0.104");
        
        final ResponseEntity<String> postClusterResponseEntity = restTemplate.exchange(
                RequestEntity.post("/nacos/v1/as/nodes").header(HttpHeaders.AUTHORIZATION, HTTP_BASIC_INFO)
                        .body(params), String.class);
        
        Assert.assertNotNull(postClusterResponseEntity);
        Assert.assertEquals(HttpStatus.OK.value(), postClusterResponseEntity.getStatusCodeValue());
        
        TimeUnit.MILLISECONDS.sleep(500L);
        
        final ResponseEntity<String> deleteClusterResponseEntity = restTemplate.exchange(
                RequestEntity.delete("/nacos/v1/as/nodes?ips={ips}", "127.0.0.104")
                        .header(HttpHeaders.AUTHORIZATION, HTTP_BASIC_INFO).build(), String.class);
        
        Assert.assertNotNull(deleteClusterResponseEntity);
        Assert.assertEquals(HttpStatus.OK.value(), deleteClusterResponseEntity.getStatusCodeValue());
    }
    
    @Test
    public void postClusterWithProduct() throws InterruptedException {
        
        LinkedMultiValueMap<String, String> params = new LinkedMultiValueMap<>(2);
        
        String ips = "127.0.0.101,127.0.0.102,127.0.0.103";
        params.add("ips", ips);
        params.add("product", PRODUCT_CONFIG);
        
        final ResponseEntity<String> postClusterResponseEntity = restTemplate.exchange(
                RequestEntity.post("/nacos/v1/as/nodes").header(HttpHeaders.AUTHORIZATION, HTTP_BASIC_INFO)
                        .body(params), String.class);
        Assert.assertNotNull(postClusterResponseEntity);
        Assert.assertEquals(HttpStatus.OK.value(), postClusterResponseEntity.getStatusCodeValue());
        
        TimeUnit.MILLISECONDS.sleep(500L);
        
        final ResponseEntity<String> getClusterResponseEntity = restTemplate.exchange(
                RequestEntity.get("/{product}/serverlist", PRODUCT_CONFIG).build(), String.class);
        
        Assert.assertNotNull(getClusterResponseEntity);
        Assert.assertEquals(HttpStatus.OK.value(), getClusterResponseEntity.getStatusCodeValue());
        
        final String body = getClusterResponseEntity.getBody();
        Assert.assertNotNull(body);
    }
    
    @Test
    public void deleteClusterWithProduct() throws InterruptedException {
        
        LinkedMultiValueMap<String, String> params = new LinkedMultiValueMap<>(1);
        params.add("ips", "127.0.0.104");
        params.add("product", PRODUCT_CONFIG);
        
        final ResponseEntity<String> postClusterResponseEntity = restTemplate.exchange(
                RequestEntity.post("/nacos/v1/as/nodes").header(HttpHeaders.AUTHORIZATION, HTTP_BASIC_INFO)
                        .body(params), String.class);
        Assert.assertNotNull(postClusterResponseEntity);
        Assert.assertEquals(HttpStatus.OK.value(), postClusterResponseEntity.getStatusCodeValue());
        
        TimeUnit.MILLISECONDS.sleep(500L);
        
        final ResponseEntity<String> deleteClusterResponseEntity = restTemplate.exchange(
                RequestEntity.delete("/nacos/v1/as/nodes?product={product}&ips={ips}", PRODUCT_CONFIG, "127.0.0.104")
                        .header(HttpHeaders.AUTHORIZATION, HTTP_BASIC_INFO).build(), String.class);
        
        Assert.assertNotNull(deleteClusterResponseEntity);
        Assert.assertEquals(HttpStatus.OK.value(), deleteClusterResponseEntity.getStatusCodeValue());
    }
    
    @Test
    public void postClusterWithProductAndCluster() throws InterruptedException {
        LinkedMultiValueMap<String, String> params = new LinkedMultiValueMap<>(1);
        
        String ips = "127.0.0.100,127.0.0.200,127.0.0.31";
        
        params.add("ips", ips);
        params.add("product", PRODUCT_NAMING);
        params.add("cluster", "cluster01");
        
        final ResponseEntity<String> postClusterResponseEntity = restTemplate.exchange(
                RequestEntity.post("/nacos/v1/as/nodes").header(HttpHeaders.AUTHORIZATION, HTTP_BASIC_INFO)
                        .body(params), String.class);
        Assert.assertNotNull(postClusterResponseEntity);
        Assert.assertEquals(HttpStatus.OK.value(), postClusterResponseEntity.getStatusCodeValue());
        
        TimeUnit.MILLISECONDS.sleep(500L);
        
        final ResponseEntity<String> getClusterResponseEntity = restTemplate.exchange(
                RequestEntity.get("/{product}/{cluster}", PRODUCT_NAMING, "cluster01").build(), String.class);
        
        Assert.assertNotNull(getClusterResponseEntity);
        Assert.assertEquals(HttpStatus.OK.value(), getClusterResponseEntity.getStatusCodeValue());
        
        final String body = getClusterResponseEntity.getBody();
        Assert.assertNotNull(body);
    }
    
    @Test
    public void deleteClusterWithProductAndCluster() throws InterruptedException {
        
        LinkedMultiValueMap<String, String> params = new LinkedMultiValueMap<>(1);
        params.add("ips", "127.0.0.104");
        params.add("product", PRODUCT_NAMING);
        params.add("cluster", "cluster01");
        
        final ResponseEntity<String> postClusterResponseEntity = restTemplate.exchange(
                RequestEntity.post("/nacos/v1/as/nodes").header(HttpHeaders.AUTHORIZATION, HTTP_BASIC_INFO)
                        .body(params), String.class);
        Assert.assertNotNull(postClusterResponseEntity);
        Assert.assertEquals(HttpStatus.OK.value(), postClusterResponseEntity.getStatusCodeValue());
        
        TimeUnit.MILLISECONDS.sleep(500L);
        
        final ResponseEntity<String> deleteClusterResponseEntity = restTemplate.exchange(
                RequestEntity.delete("/nacos/v1/as/nodes?product={product}&cluster={cluster}&ips={ips}", PRODUCT_NAMING,
                        "cluster01", "127.0.0.104").header(HttpHeaders.AUTHORIZATION, HTTP_BASIC_INFO).build(),
                String.class);
        
        Assert.assertNotNull(deleteClusterResponseEntity);
        Assert.assertEquals(HttpStatus.OK.value(), deleteClusterResponseEntity.getStatusCodeValue());
    }
    
}
