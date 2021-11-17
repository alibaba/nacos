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

import org.junit.Test;

import java.util.HashMap;

public class AddressServerControllerTests {
    
    private static final String PRODUCT_NACOS = "nacos";
    
    private static final String PRODUCT_CONFIG = "config";
    
    private static final String PRODUCT_NAMING = "naming";
    
    private static final String DEFAULT_URL_CLUSTER = "serverlist";
    
    private static final String GET_SERVERLIST_URL_FORMART = "http://127.0.0.1:8080/%s/%s";
    
    //-----------------product=nacos,cluster=DEFAULT -------------------//
    
    @Test
    public void postCluster() {
        
        String ips = "127.0.0.100,127.0.0.102,127.0.0.104";
        HashMap<String, String> params = new HashMap<>();
        params.put("ips", ips);
        String response = SimpleHttpTestUtils.doPost("http://127.0.0.1:8080/nacos/v1/as/nodes", params, "UTF-8");
        System.err.println(response);
    }
    
    @Test
    public void getCluster() {
        
        String getUrl = String.format(GET_SERVERLIST_URL_FORMART, PRODUCT_NACOS, DEFAULT_URL_CLUSTER);
        String response = SimpleHttpTestUtils.doGet(getUrl, new HashMap<>(), "UTF-8");
        System.err.println(response);
    }
    
    @Test
    public void deleteCluster() {
        HashMap<String, String> deleteIp = new HashMap<>();
        deleteIp.put("ips", "127.0.0.104");
        String response = SimpleHttpTestUtils.doDelete("http://127.0.0.1:8080/nacos/v1/as/nodes", deleteIp, "UTF-8");
        System.err.println(response);
    }
    
    @Test
    public void deleteClusterWithSpecIp() {
        HashMap<String, String> params = new HashMap<>();
        params.put("ips", "127.0.0.103");
        String response = SimpleHttpTestUtils.doDelete("http://127.0.0.1:8080/nacos/v1/as/nodes", params, "UTF-8");
        System.err.println(response);
    }
    
    @Test
    public void putCluster() {
        
        String ips = "127.0.0.114";
        HashMap<String, String> params = new HashMap<>();
        params.put("ips", ips);
        String response = SimpleHttpTestUtils.doPut("http://127.0.0.1:8080/nacos/v1/as/nodes", params, "UTF-8");
        System.err.println(response);
    }
    
    //-----------------product=config,cluster=cluster01 -------------------//
    
    @Test
    public void postClusterWithProduct() {
        
        String ips = "127.0.0.101,127.0.0.102,127.0.0.103";
        HashMap<String, String> params = new HashMap<>();
        params.put("ips", ips);
        params.put("product", PRODUCT_CONFIG);
        String response = SimpleHttpTestUtils.doPost("http://127.0.0.1:8080/nacos/v1/as/nodes", params, "UTF-8");
        System.err.println(response);
    }
    
    @Test
    public void getClusterWithProduct() {
        HashMap<String, String> params = new HashMap<>();
        String getUrl = String.format(GET_SERVERLIST_URL_FORMART, PRODUCT_CONFIG, DEFAULT_URL_CLUSTER);
        String response = SimpleHttpTestUtils.doGet(getUrl, params, "UTF-8");
        System.err.println(response);
    }
    
    @Test
    public void deleteClusterWithProduct() {
        HashMap<String, String> params = new HashMap<>();
        params.put("product", PRODUCT_CONFIG);
        String response = SimpleHttpTestUtils.doDelete("http://127.0.0.1:8080/nacos/v1/as/nodes", params, "UTF-8");
        System.err.println(response);
    }
    
    @Test
    public void deleteClusterWithProductAndIp() {
        HashMap<String, String> params = new HashMap<>();
        params.put("product", PRODUCT_CONFIG);
        params.put("ips", "127.0.0.196");
        String response = SimpleHttpTestUtils.doDelete("http://127.0.0.1:8080/nacos/v1/as/nodes", params, "UTF-8");
        System.err.println(response);
    }
    
    @Test
    public void putClusterWithProduct() {
        
        String ips = "127.0.0.196";
        HashMap<String, String> params = new HashMap<>();
        params.put("ips", ips);
        params.put("product", PRODUCT_CONFIG);
        String response = SimpleHttpTestUtils.doPut("http://127.0.0.1:8080/nacos/v1/as/nodes", params, "UTF-8");
        System.err.println(response);
    }
    
    //-----------------product=naming,cluster=cluster01 -------------------//
    
    @Test
    public void postClusterWithProductAndCluster() {
        
        String ips = "127.0.0.100,127.0.0.200,127.0.0.31";
        HashMap<String, String> params = new HashMap<>();
        params.put("ips", ips);
        params.put("product", PRODUCT_NAMING);
        params.put("cluster", "cluster01");
        String response = SimpleHttpTestUtils.doPost("http://127.0.0.1:8080/nacos/v1/as/nodes", params, "UTF-8");
        System.err.println(response);
    }
    
    @Test
    public void getClusterWithProductAndCluster() {
        HashMap<String, String> params = new HashMap<>();
        String getUrl = String.format(GET_SERVERLIST_URL_FORMART, PRODUCT_NAMING, "cluster01");
        String response = SimpleHttpTestUtils.doGet(getUrl, params, "UTF-8");
        System.err.println(response);
    }
    
    @Test
    public void deleteClusterWithProductAndCluster() {
        HashMap<String, String> params = new HashMap<>();
        params.put("product", PRODUCT_NAMING);
        params.put("cluster", "cluster01");
        String response = SimpleHttpTestUtils.doDelete("http://127.0.0.1:8080/nacos/v1/as/nodes", params, "UTF-8");
        System.err.println(response);
    }
    
    @Test
    public void deleteClusterWithProductAndClusterAndIp() {
        HashMap<String, String> params = new HashMap<>();
        params.put("product", PRODUCT_NAMING);
        params.put("cluster", "cluster01");
        params.put("ips", "127.0.0.200");
        String response = SimpleHttpTestUtils.doDelete("http://127.0.0.1:8080/nacos/v1/as/nodes", params, "UTF-8");
        System.err.println(response);
    }
    
    @Test
    public void putClusterWithProductAndCluster() {
        
        String ips = "127.0.0.171";
        HashMap<String, String> params = new HashMap<>();
        params.put("ips", ips);
        params.put("product", PRODUCT_NAMING);
        params.put("cluster", "cluster01");
        String response = SimpleHttpTestUtils.doPut("http://127.0.0.1:8080/nacos/v1/as/nodes", params, "UTF-8");
        System.err.println(response);
    }
}
