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
import com.alibaba.nacos.plugin.auth.impl.persistence.User;
import com.alibaba.nacos.plugin.auth.impl.utils.PasswordEncoderUtil;
import com.alibaba.nacos.test.base.HttpClient4Test;
import com.alibaba.nacos.test.base.Params;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.web.server.LocalServerPort;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.net.URL;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration tests for user management in Nacos, including user creation, deletion, updating, and permissions.
 *
 * @author nkorange
 * @since 1.2.0
 */
@SuppressWarnings("checkstyle:AbbreviationAsWordInName")
@ExtendWith(SpringExtension.class)
@SpringBootTest(classes = Nacos.class, properties = {
        "server.servlet.context-path=/nacos"}, webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT)
class UserCoreITCase extends HttpClient4Test {
    
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
        
        // Delete a user:
        ResponseEntity<String> response = request("/nacos/v1/auth/users",
                Params.newParams().appendParam("username", "username1").appendParam("accessToken", accessToken).done(),
                String.class, HttpMethod.DELETE);
        
        assertTrue(response.getStatusCode().is2xxSuccessful());
        
        // Delete a user:
        request("/nacos/v1/auth/users",
                Params.newParams().appendParam("username", "username2").appendParam("accessToken", accessToken).done(),
                String.class, HttpMethod.DELETE);
        
        assertTrue(response.getStatusCode().is2xxSuccessful());
        
        System.setProperty("nacos.core.auth.enabled", "false");
    }
    
    @Test
    void login() {
        ResponseEntity<String> response = login("nacos", "nacos");
        assertTrue(response.getStatusCode().is2xxSuccessful());
        JsonNode json = JacksonUtils.toObj(response.getBody());
        assertTrue(json.has("accessToken"));
        accessToken = json.get("accessToken").textValue();
    }
    
    private ResponseEntity<String> login(String username, String password) {
        return request("/nacos/v1/auth/users/login",
                Params.newParams().appendParam("username", username).appendParam("password", password).done(),
                String.class, HttpMethod.POST);
    }
    
    @Test
    void createUpdateDeleteUser() {
        
        login();
        
        // Create a user:
        ResponseEntity<String> response = request("/nacos/v1/auth/users",
                Params.newParams().appendParam("username", "username1").appendParam("password", "password1")
                        .appendParam("accessToken", accessToken).done(), String.class, HttpMethod.POST);
        
        assertTrue(response.getStatusCode().is2xxSuccessful());
        
        // Query a user:
        response = request("/nacos/v1/auth/users",
                Params.newParams().appendParam("pageNo", "1").appendParam("pageSize", String.valueOf(Integer.MAX_VALUE))
                        .appendParam("accessToken", accessToken).done(), String.class);
        
        assertTrue(response.getStatusCode().is2xxSuccessful());
        
        Page<User> userPage = JacksonUtils.toObj(response.getBody(), new TypeReference<Page<User>>() {
        });
        
        assertNotNull(userPage);
        assertNotNull(userPage.getPageItems());
        assertTrue(userPage.getPageItems().size() > 0);
        
        boolean found = false;
        for (User user : userPage.getPageItems()) {
            if ("username1".equals(user.getUsername()) && PasswordEncoderUtil.matches("password1",
                    user.getPassword())) {
                found = true;
                break;
            }
        }
        assertTrue(found);
        
        // Update a user:
        response = request("/nacos/v1/auth/users",
                Params.newParams().appendParam("username", "username1").appendParam("newPassword", "password2")
                        .appendParam("accessToken", accessToken).done(), String.class, HttpMethod.PUT);
        
        assertTrue(response.getStatusCode().is2xxSuccessful());
        
        // Query a user:
        response = request("/nacos/v1/auth/users",
                Params.newParams().appendParam("pageNo", "1").appendParam("pageSize", String.valueOf(Integer.MAX_VALUE))
                        .appendParam("accessToken", accessToken).done(), String.class);
        
        userPage = JacksonUtils.toObj(response.getBody(), new TypeReference<Page<User>>() {
        });
        
        assertNotNull(userPage);
        assertNotNull(userPage.getPageItems());
        assertTrue(userPage.getPageItems().size() > 0);
        
        found = false;
        for (User user : userPage.getPageItems()) {
            if ("username1".equals(user.getUsername()) && PasswordEncoderUtil.matches("password2",
                    user.getPassword())) {
                found = true;
                break;
            }
        }
        assertTrue(found);
        
        // Delete a user:
        response = request("/nacos/v1/auth/users",
                Params.newParams().appendParam("username", "username1").appendParam("accessToken", accessToken).done(),
                String.class, HttpMethod.DELETE);
        
        assertTrue(response.getStatusCode().is2xxSuccessful());
        
        // Query a user:
        response = request("/nacos/v1/auth/users",
                Params.newParams().appendParam("pageNo", "1").appendParam("pageSize", String.valueOf(Integer.MAX_VALUE))
                        .appendParam("accessToken", accessToken).done(), String.class);
        
        assertTrue(response.getStatusCode().is2xxSuccessful());
        
        userPage = JacksonUtils.toObj(response.getBody(), new TypeReference<Page<User>>() {
        });
        
        assertNotNull(userPage);
        assertNotNull(userPage.getPageItems());
        assertTrue(userPage.getPageItems().size() > 0);
        
        found = false;
        for (User user : userPage.getPageItems()) {
            if ("username1".equals(user.getUsername())) {
                found = true;
                break;
            }
        }
        assertFalse(found);
    }
    
    @Test
    void updateUserWithPermission() {
        System.setProperty("nacos.core.auth.enabled", "true");
        
        // admin login
        login();
        
        // create username1
        ResponseEntity<String> response = request("/nacos/v1/auth/users",
                Params.newParams().appendParam("username", "username1").appendParam("password", "password1")
                        .appendParam("accessToken", accessToken).done(), String.class, HttpMethod.POST);
        assertTrue(response.getStatusCode().is2xxSuccessful());
        // create username2
        response = request("/nacos/v1/auth/users",
                Params.newParams().appendParam("username", "username2").appendParam("password", "password2")
                        .appendParam("accessToken", accessToken).done(), String.class, HttpMethod.POST);
        assertTrue(response.getStatusCode().is2xxSuccessful());
        
        // user login
        response = login("username1", "password1");
        assertTrue(response.getStatusCode().is2xxSuccessful());
        
        response = login("username2", "password2");
        assertTrue(response.getStatusCode().is2xxSuccessful());
        
        // update by admin
        response = request("/nacos/v1/auth/users",
                Params.newParams().appendParam("username", "username1").appendParam("newPassword", "password3")
                        .appendParam("accessToken", accessToken).done(), String.class, HttpMethod.PUT);
        assertTrue(response.getStatusCode().is2xxSuccessful());
        
        // update by same user
        String user1AccessToken = JacksonUtils.toObj(response.getBody()).get("accessToken").textValue();
        response = request("/nacos/v1/auth/users",
                Params.newParams().appendParam("username", "username1").appendParam("newPassword", "password4")
                        .appendParam("accessToken", user1AccessToken).done(), String.class, HttpMethod.PUT);
        assertTrue(response.getStatusCode().is2xxSuccessful());
        
        // update by another user
        String user2AccessToken = JacksonUtils.toObj(response.getBody()).get("accessToken").textValue();
        response = request("/nacos/v1/auth/users",
                Params.newParams().appendParam("username", "username1").appendParam("newPassword", "password5")
                        .appendParam("accessToken", user2AccessToken).done(), String.class, HttpMethod.PUT);
        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
    }
}
