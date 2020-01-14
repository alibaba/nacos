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
import com.alibaba.nacos.api.PropertyKeyConst;
import com.alibaba.nacos.test.base.Params;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.web.server.LocalServerPort;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.junit4.SpringRunner;

import java.util.Properties;

/**
 * @author nkorange
 * @since 1.2.0
 */
@RunWith(SpringRunner.class)
@SpringBootTest(classes = Nacos.class, properties = {"server.servlet.context-path=/nacos", "server.port=7001"},
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class NamingAuth_ITCase extends AuthBase {

    @LocalServerPort
    private int port;

    private String accessToken;

    private String username = "username1";
    private String password = "password1";
    private String role = "role1";

    private Properties properties;

    private String namespace1 = "namespace1";
    private String namespace2 = "namespace2";

    @Before
    public void init() {
        accessToken = login();
        // Create a user:
        ResponseEntity<String> response = request("/nacos/v1/auth/users",
            Params.newParams()
                .appendParam("username", username)
                .appendParam("password", password)
                .appendParam("accessToken", accessToken)
                .done(),
            String.class,
            HttpMethod.POST);

        Assert.assertTrue(response.getStatusCode().is2xxSuccessful());

        // Create a role:
        response = request("/nacos/v1/auth/roles",
            Params.newParams()
                .appendParam("role", role)
                .appendParam("username", username)
                .appendParam("accessToken", accessToken)
                .done(),
            String.class,
            HttpMethod.POST);

        Assert.assertTrue(response.getStatusCode().is2xxSuccessful());

        // Add read permission for namespace1:
        response = request("/nacos/v1/auth/permissions",
            Params.newParams()
                .appendParam("role", role)
                .appendParam("resource", namespace1 + ":*:*")
                .appendParam("action", "r")
                .appendParam("accessToken", accessToken)
                .done(),
            String.class,
            HttpMethod.POST);

        Assert.assertTrue(response.getStatusCode().is2xxSuccessful());

        // Add read/write permission for namespace2:
        response = request("/nacos/v1/auth/permissions",
            Params.newParams()
                .appendParam("role", role)
                .appendParam("resource", namespace2 + ":*:*")
                .appendParam("action", "rw")
                .appendParam("accessToken", accessToken)
                .done(),
            String.class,
            HttpMethod.POST);

        Assert.assertTrue(response.getStatusCode().is2xxSuccessful());

        // Init properties:
        properties = new Properties();
        properties.put(PropertyKeyConst.USERNAME, username);
        properties.put(PropertyKeyConst.PASSWORD, password);
        properties.put(PropertyKeyConst.SERVER_ADDR, "127.0.0.1" + ":" + port);
    }

    @After
    public void destroy() {

        // Delete permission:
        ResponseEntity<String> response = request("/nacos/v1/auth/permissions",
            Params.newParams()
                .appendParam("role", role)
                .appendParam("resource", namespace1 + ":*:*")
                .appendParam("action", "r")
                .appendParam("accessToken", accessToken)
                .done(),
            String.class,
            HttpMethod.DELETE);

        Assert.assertTrue(response.getStatusCode().is2xxSuccessful());

        // Delete permission:
        response = request("/nacos/v1/auth/permissions",
            Params.newParams()
                .appendParam("role", role)
                .appendParam("resource", namespace2 + ":*:*")
                .appendParam("action", "rw")
                .appendParam("accessToken", accessToken)
                .done(),
            String.class,
            HttpMethod.DELETE);

        Assert.assertTrue(response.getStatusCode().is2xxSuccessful());

        // Delete a role:
        response = request("/nacos/v1/auth/roles",
            Params.newParams()
                .appendParam("role", role)
                .appendParam("username", username)
                .appendParam("accessToken", accessToken)
                .done(),
            String.class,
            HttpMethod.DELETE);

        Assert.assertTrue(response.getStatusCode().is2xxSuccessful());

        // Delete a user:
        response = request("/nacos/v1/auth/users",
            Params.newParams()
                .appendParam("username", username)
                .appendParam("password", password)
                .appendParam("accessToken", accessToken)
                .done(),
            String.class,
            HttpMethod.DELETE);

        Assert.assertTrue(response.getStatusCode().is2xxSuccessful());
    }

    @Test
    public void writeWithReadPermission() {

        properties.put(PropertyKeyConst.NAMESPACE, namespace1);
    }

}
