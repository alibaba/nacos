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

package com.alibaba.nacos.test.core.auth;

import com.alibaba.nacos.Nacos;
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.persistence.model.Page;
import com.alibaba.nacos.plugin.auth.api.Permission;
import com.alibaba.nacos.test.base.HttpClient4Test;
import com.alibaba.nacos.test.base.Params;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.web.server.LocalServerPort;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.junit4.SpringRunner;

import java.net.URL;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author nkorange
 * @since 1.2.0
 */
@RunWith(SpringRunner.class)
@SpringBootTest(classes = Nacos.class, properties = {
        "server.servlet.context-path=/nacos"}, webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT)
class Permission_ITCase extends HttpClient4Test {
    
    @LocalServerPort
    private int port;
    
    private String accessToken;
    
    @BeforeEach
    void init() throws Exception {
        TimeUnit.SECONDS.sleep(5L);
        String url = String.format("http://localhost:%d/", port);
        this.base = new URL(url);
    }
    
    @AfterEach
    void destroy() {
        
        // Delete permission:
        ResponseEntity<String> response = request("/nacos/v1/auth/permissions",
                Params.newParams().appendParam("role", "role1").appendParam("resource", "public:*:*").appendParam("action", "rw")
                        .appendParam("accessToken", accessToken).done(), String.class, HttpMethod.DELETE);
        
        assertTrue(response.getStatusCode().is2xxSuccessful());
        
        // Delete permission:
        response = request("/nacos/v1/auth/permissions",
                Params.newParams().appendParam("role", "role1").appendParam("resource", "test1:*:*").appendParam("action", "r")
                        .appendParam("accessToken", accessToken).done(), String.class, HttpMethod.DELETE);
        
        assertTrue(response.getStatusCode().is2xxSuccessful());
        
        // Delete role:
        response = request("/nacos/v1/auth/roles",
                Params.newParams().appendParam("role", "role1").appendParam("username", "username3").appendParam("accessToken", accessToken)
                        .done(), String.class, HttpMethod.DELETE);
        
        assertTrue(response.getStatusCode().is2xxSuccessful());
        
        // Delete a user:
        response = request("/nacos/v1/auth/users",
                Params.newParams().appendParam("username", "username3").appendParam("accessToken", accessToken).done(), String.class,
                HttpMethod.DELETE);
        
        assertTrue(response.getStatusCode().is2xxSuccessful());
    }
    
    @Test
    void login() {
        
        ResponseEntity<String> response = request("/nacos/v1/auth/users/login",
                Params.newParams().appendParam("username", "nacos").appendParam("password", "nacos").done(), String.class, HttpMethod.POST);
        
        assertTrue(response.getStatusCode().is2xxSuccessful());
        JsonNode json = JacksonUtils.toObj(response.getBody());
        assertTrue(json.has("accessToken"));
        accessToken = json.get("accessToken").textValue();
    }
    
    @Test
    void createDeleteQueryPermission() {
        
        login();
        
        // Create a user:
        ResponseEntity<String> response = request("/nacos/v1/auth/users",
                Params.newParams().appendParam("username", "username3").appendParam("password", "password1")
                        .appendParam("accessToken", accessToken).done(), String.class, HttpMethod.POST);
        
        assertTrue(response.getStatusCode().is2xxSuccessful());
        
        // Create role:
        response = request("/nacos/v1/auth/roles",
                Params.newParams().appendParam("role", "role1").appendParam("username", "username3").appendParam("accessToken", accessToken)
                        .done(), String.class, HttpMethod.POST);
        
        assertTrue(response.getStatusCode().is2xxSuccessful());
        
        // Create permission:
        response = request("/nacos/v1/auth/permissions",
                Params.newParams().appendParam("role", "role1").appendParam("resource", "public:*:*").appendParam("action", "rw")
                        .appendParam("accessToken", accessToken).done(), String.class, HttpMethod.POST);
        
        assertTrue(response.getStatusCode().is2xxSuccessful());
        
        // Create another permission:
        response = request("/nacos/v1/auth/permissions",
                Params.newParams().appendParam("role", "role1").appendParam("resource", "test1:*:*").appendParam("action", "r")
                        .appendParam("accessToken", accessToken).done(), String.class, HttpMethod.POST);
        
        assertTrue(response.getStatusCode().is2xxSuccessful());
        
        // Query permission:
        response = request("/nacos/v1/auth/permissions",
                Params.newParams().appendParam("role", "role1").appendParam("pageNo", "1").appendParam("pageSize", "10")
                        .appendParam("accessToken", accessToken).done(), String.class, HttpMethod.GET);
        
        System.out.println(response);
        assertTrue(response.getStatusCode().is2xxSuccessful());
        
        Page<Permission> permissionPage = JacksonUtils.toObj(response.getBody(), new TypeReference<Page<Permission>>() {
        });
        
        assertNotNull(permissionPage);
        assertNotNull(permissionPage.getPageItems());
        
        boolean found1 = false, found2 = false;
        for (Permission permission : permissionPage.getPageItems()) {
            if (permission.getResource().equals("public:*:*") && permission.getAction().equals("rw")) {
                found1 = true;
            }
            if (permission.getResource().equals("test1:*:*") && permission.getAction().equals("r")) {
                found2 = true;
            }
            if (found1 && found2) {
                break;
            }
        }
        assertTrue(found1);
        assertTrue(found2);
        
        // Delete permission:
        response = request("/nacos/v1/auth/permissions",
                Params.newParams().appendParam("role", "role1").appendParam("resource", "public:*:*").appendParam("action", "rw")
                        .appendParam("accessToken", accessToken).done(), String.class, HttpMethod.DELETE);
        
        assertTrue(response.getStatusCode().is2xxSuccessful());
        
        // Query permission:
        response = request("/nacos/v1/auth/permissions",
                Params.newParams().appendParam("role", "role1").appendParam("pageNo", "1").appendParam("pageSize", "10")
                        .appendParam("accessToken", accessToken).done(), String.class, HttpMethod.GET);
        
        assertTrue(response.getStatusCode().is2xxSuccessful());
        
        permissionPage = JacksonUtils.toObj(response.getBody(), new TypeReference<Page<Permission>>() {
        });
        
        assertNotNull(permissionPage);
        assertNotNull(permissionPage.getPageItems());
        
        found1 = false;
        found2 = false;
        
        for (Permission permission : permissionPage.getPageItems()) {
            if (permission.getResource().equals("public:*:*") && permission.getAction().equals("rw")) {
                found1 = true;
            }
            if (permission.getResource().equals("test1:*:*") && permission.getAction().equals("r")) {
                found2 = true;
            }
        }
        assertFalse(found1);
        assertTrue(found2);
        
        // Delete permission:
        response = request("/nacos/v1/auth/permissions",
                Params.newParams().appendParam("role", "role1").appendParam("resource", "test1:*:*").appendParam("action", "r")
                        .appendParam("accessToken", accessToken).done(), String.class, HttpMethod.DELETE);
        
        assertTrue(response.getStatusCode().is2xxSuccessful());
        
        // Query permission:
        response = request("/nacos/v1/auth/permissions",
                Params.newParams().appendParam("role", "role1").appendParam("pageNo", "1").appendParam("pageSize", "10")
                        .appendParam("accessToken", accessToken).done(), String.class, HttpMethod.GET);
        
        assertTrue(response.getStatusCode().is2xxSuccessful());
        
        permissionPage = JacksonUtils.toObj(response.getBody(), new TypeReference<Page<Permission>>() {
        });
        
        assertNotNull(permissionPage);
        assertNotNull(permissionPage.getPageItems());
        
        found1 = false;
        found2 = false;
        
        for (Permission permission : permissionPage.getPageItems()) {
            if (permission.getResource().equals("public:*:*") && permission.getAction().equals("rw")) {
                found1 = true;
            }
            if (permission.getResource().equals("test1:*:*") && permission.getAction().equals("r")) {
                found2 = true;
            }
        }
        assertFalse(found1);
        assertFalse(found2);
    }
}
