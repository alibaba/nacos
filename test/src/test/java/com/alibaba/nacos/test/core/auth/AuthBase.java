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

import com.alibaba.nacos.api.PropertyKeyConst;
import com.alibaba.nacos.auth.common.AuthConfigs;
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.test.base.HttpClient4Test;
import com.alibaba.nacos.test.base.Params;
import com.fasterxml.jackson.databind.JsonNode;

import org.junit.Assert;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;

import java.net.URL;
import java.util.Properties;
import java.util.concurrent.TimeUnit;


/**
 * @author nkorange
 * @since 1.2.0
 */
public class AuthBase extends HttpClient4Test {

    protected String accessToken;

    protected String username1 = "username1";
    protected String password1 = "password1";

    protected String username2 = "username2";
    protected String password2 = "password2";

    protected String username3 = "username3";
    protected String password3 = "password3";

    protected String role1 = "role1";
    protected String role2 = "role2";
    protected String role3 = "role3";

    protected Properties properties;

    protected String namespace1 = "namespace1";

    public String login() {

        ResponseEntity<String> response = request("/nacos/v1/auth/users/login",
            Params.newParams()
                .appendParam("username", "nacos")
                .appendParam("password", "nacos")
                .done(),
            String.class,
            HttpMethod.POST);

        Assert.assertTrue(response.getStatusCode().is2xxSuccessful());
        JsonNode json = JacksonUtils.toObj(response.getBody());
        Assert.assertTrue(json.has("accessToken"));
        return json.get("accessToken").textValue();
    }

    protected void init(int port) throws Exception {
        AuthConfigs.setCachingEnabled(false);
        TimeUnit.SECONDS.sleep(5L);
        String url = String.format("http://localhost:%d/", port);
        System.setProperty("nacos.core.auth.enabled", "true");
        this.base = new URL(url);
        accessToken = login();

        // Create a user:
        ResponseEntity<String> response = request("/nacos/v1/auth/users",
            Params.newParams()
                .appendParam("username", username1)
                .appendParam("password", password1)
                .appendParam("accessToken", accessToken)
                .done(),
            String.class,
            HttpMethod.POST);
        System.out.println(response);
        Assert.assertTrue(response.getStatusCode().is2xxSuccessful());

        // Create a user:
        response = request("/nacos/v1/auth/users",
            Params.newParams()
                .appendParam("username", username2)
                .appendParam("password", password2)
                .appendParam("accessToken", accessToken)
                .done(),
            String.class,
            HttpMethod.POST);

        System.out.println(response);
        Assert.assertTrue(response.getStatusCode().is2xxSuccessful());

        // Create a user:
        response = request("/nacos/v1/auth/users",
            Params.newParams()
                .appendParam("username", username3)
                .appendParam("password", password3)
                .appendParam("accessToken", accessToken)
                .done(),
            String.class,
            HttpMethod.POST);

        System.out.println(response);
        Assert.assertTrue(response.getStatusCode().is2xxSuccessful());

        // Create a role:
        response = request("/nacos/v1/auth/roles",
            Params.newParams()
                .appendParam("role", role1)
                .appendParam("username", username1)
                .appendParam("accessToken", accessToken)
                .done(),
            String.class,
            HttpMethod.POST);

        System.out.println(response);
        Assert.assertTrue(response.getStatusCode().is2xxSuccessful());

        // Create a role:
        response = request("/nacos/v1/auth/roles",
            Params.newParams()
                .appendParam("role", role2)
                .appendParam("username", username2)
                .appendParam("accessToken", accessToken)
                .done(),
            String.class,
            HttpMethod.POST);
        System.out.println(response);
        Assert.assertTrue(response.getStatusCode().is2xxSuccessful());

        // Create a role:
        response = request("/nacos/v1/auth/roles",
            Params.newParams()
                .appendParam("role", role3)
                .appendParam("username", username3)
                .appendParam("accessToken", accessToken)
                .done(),
            String.class,
            HttpMethod.POST);
        System.out.println(response);
        Assert.assertTrue(response.getStatusCode().is2xxSuccessful());

        // Add read permission of namespace1 to role1:
        response = request("/nacos/v1/auth/permissions",
            Params.newParams()
                .appendParam("role", role1)
                .appendParam("resource", namespace1 + ":*:*")
                .appendParam("action", "r")
                .appendParam("accessToken", accessToken)
                .done(),
            String.class,
            HttpMethod.POST);
        System.out.println(response);
        Assert.assertTrue(response.getStatusCode().is2xxSuccessful());

        // Add write permission of namespace1 to role2:
        response = request("/nacos/v1/auth/permissions",
            Params.newParams()
                .appendParam("role", role2)
                .appendParam("resource", namespace1 + ":*:*")
                .appendParam("action", "w")
                .appendParam("accessToken", accessToken)
                .done(),
            String.class,
            HttpMethod.POST);
        System.out.println(response);
        Assert.assertTrue(response.getStatusCode().is2xxSuccessful());

        // Add read/write permission of namespace1 to role3:
        response = request("/nacos/v1/auth/permissions",
            Params.newParams()
                .appendParam("role", role3)
                .appendParam("resource", namespace1 + ":*:*")
                .appendParam("action", "rw")
                .appendParam("accessToken", accessToken)
                .done(),
            String.class,
            HttpMethod.POST);
        System.out.println(response);
        Assert.assertTrue(response.getStatusCode().is2xxSuccessful());

        // Init properties:
        properties = new Properties();
        properties.put(PropertyKeyConst.NAMESPACE, namespace1);
        properties.put(PropertyKeyConst.SERVER_ADDR, "127.0.0.1" + ":" + port);
    }

    protected void destroy() {

        // Delete permission:
        ResponseEntity<String> response = request("/nacos/v1/auth/permissions",
            Params.newParams()
                .appendParam("role", role1)
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
                .appendParam("role", role2)
                .appendParam("resource", namespace1 + ":*:*")
                .appendParam("action", "w")
                .appendParam("accessToken", accessToken)
                .done(),
            String.class,
            HttpMethod.DELETE);

        Assert.assertTrue(response.getStatusCode().is2xxSuccessful());

        // Delete permission:
        response = request("/nacos/v1/auth/permissions",
            Params.newParams()
                .appendParam("role", role3)
                .appendParam("resource", namespace1 + ":*:*")
                .appendParam("action", "rw")
                .appendParam("accessToken", accessToken)
                .done(),
            String.class,
            HttpMethod.DELETE);

        Assert.assertTrue(response.getStatusCode().is2xxSuccessful());

        // Delete a role:
        response = request("/nacos/v1/auth/roles",
            Params.newParams()
                .appendParam("role", role1)
                .appendParam("username", username1)
                .appendParam("accessToken", accessToken)
                .done(),
            String.class,
            HttpMethod.DELETE);

        Assert.assertTrue(response.getStatusCode().is2xxSuccessful());

        // Delete a role:
        response = request("/nacos/v1/auth/roles",
            Params.newParams()
                .appendParam("role", role2)
                .appendParam("username", username2)
                .appendParam("accessToken", accessToken)
                .done(),
            String.class,
            HttpMethod.DELETE);

        Assert.assertTrue(response.getStatusCode().is2xxSuccessful());

        // Delete a role:
        response = request("/nacos/v1/auth/roles",
            Params.newParams()
                .appendParam("role", role3)
                .appendParam("username", username3)
                .appendParam("accessToken", accessToken)
                .done(),
            String.class,
            HttpMethod.DELETE);

        Assert.assertTrue(response.getStatusCode().is2xxSuccessful());

        // Delete a user:
        response = request("/nacos/v1/auth/users",
            Params.newParams()
                .appendParam("username", username1)
                .appendParam("password", password1)
                .appendParam("accessToken", accessToken)
                .done(),
            String.class,
            HttpMethod.DELETE);

        Assert.assertTrue(response.getStatusCode().is2xxSuccessful());

        // Delete a user:
        response = request("/nacos/v1/auth/users",
            Params.newParams()
                .appendParam("username", username2)
                .appendParam("password", password2)
                .appendParam("accessToken", accessToken)
                .done(),
            String.class,
            HttpMethod.DELETE);

        Assert.assertTrue(response.getStatusCode().is2xxSuccessful());

        // Delete a user:
        response = request("/nacos/v1/auth/users",
            Params.newParams()
                .appendParam("username", username3)
                .appendParam("password", password3)
                .appendParam("accessToken", accessToken)
                .done(),
            String.class,
            HttpMethod.DELETE);

        Assert.assertTrue(response.getStatusCode().is2xxSuccessful());

        System.setProperty("nacos.core.auth.enabled", "false");
    }
}
