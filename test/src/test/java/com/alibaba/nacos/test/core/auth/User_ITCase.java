package com.alibaba.nacos.test.core.auth;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson.TypeReference;
import com.alibaba.nacos.config.server.model.Page;
import com.alibaba.nacos.config.server.model.User;
import com.alibaba.nacos.console.utils.PasswordEncoderUtil;
import com.alibaba.nacos.naming.NamingApp;
import com.alibaba.nacos.test.base.HttpClient4Test;
import com.alibaba.nacos.test.base.Params;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.web.server.LocalServerPort;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.junit4.SpringRunner;

import java.net.URL;

/**
 * @author nkorange
 * @since 1.2.0
 */
@RunWith(SpringRunner.class)
@SpringBootTest(classes = NamingApp.class, properties = {"server.servlet.context-path=/nacos"},
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class User_ITCase extends HttpClient4Test {

    protected URL base;

    @LocalServerPort
    private int port;

    private String accessToken;

    @Before
    public void init() throws Exception {
        String url = String.format("http://localhost:%d/", port);
        this.base = new URL(url);
        login();
    }


    @Test
    public void login() {
        ResponseEntity<String> response = request("/nacos/v1/ns/auth/users/login",
            Params.newParams()
                .appendParam("username", "username1")
                .appendParam("password", "password1")
                .done(),
            String.class,
            HttpMethod.POST);

        Assert.assertTrue(response.getStatusCode().is2xxSuccessful());
        JSONObject json = JSON.parseObject(response.getBody());
        Assert.assertTrue(json.containsKey("accessToken"));
        accessToken = json.getString("accessToken");
    }

    @Test
    public void createUpdateDeleteUser() {

        // Create a user:
        ResponseEntity<String> response = request("/nacos/v1/ns/auth/users",
            Params.newParams()
                .appendParam("username", "username1")
                .appendParam("password", "password1")
                .appendParam("accessToken", accessToken)
                .done(),
            String.class,
            HttpMethod.POST);

        Assert.assertTrue(response.getStatusCode().is2xxSuccessful());

        // Query a user:
        response = request("/nacos/v1/ns/auth/users",
            Params.newParams()
                .appendParam("pageNo", "1")
                .appendParam("pageSize", String.valueOf(Integer.MAX_VALUE))
                .appendParam("accessToken", accessToken)
                .done(),
            String.class);

        Assert.assertTrue(response.getStatusCode().is2xxSuccessful());

        Page<User> userPage = JSON.parseObject(response.getBody(), new TypeReference<Page<User>>(){});

        Assert.assertNotNull(userPage);
        Assert.assertNotNull(userPage.getPageItems());
        Assert.assertTrue(userPage.getPageItems().size() > 0);

        boolean found = false;
        for (User user : userPage.getPageItems()) {
            if ("username1".equals(user.getUsername()) &&
                PasswordEncoderUtil.encode("password1").equals(user.getPassword())) {
                found = true;
                break;
            }
        }
        Assert.assertTrue(found);

        // Update a user:
        response = request("/nacos/v1/ns/auth/users",
            Params.newParams()
                .appendParam("username", "username1")
                .appendParam("newPassword", "password2")
                .appendParam("accessToken", accessToken)
                .done(),
            String.class,
            HttpMethod.PUT);

        Assert.assertTrue(response.getStatusCode().is2xxSuccessful());

        // Query a user:
        response = request("/nacos/v1/ns/auth/users",
            Params.newParams()
                .appendParam("pageNo", "1")
                .appendParam("pageSize", String.valueOf(Integer.MAX_VALUE))
                .appendParam("accessToken", accessToken)
                .done(),
            String.class);

        userPage = JSON.parseObject(response.getBody(), new TypeReference<Page<User>>(){});

        Assert.assertNotNull(userPage);
        Assert.assertNotNull(userPage.getPageItems());
        Assert.assertTrue(userPage.getPageItems().size() > 0);

        found = false;
        for (User user : userPage.getPageItems()) {
            if ("username1".equals(user.getUsername()) &&
                PasswordEncoderUtil.encode("password2").equals(user.getPassword())) {
                found = true;
                break;
            }
        }
        Assert.assertTrue(found);

        // Delete a user:
        response = request("/nacos/v1/ns/auth/users",
            Params.newParams()
                .appendParam("username", "username1")
                .appendParam("accessToken", accessToken)
                .done(),
            String.class,
            HttpMethod.DELETE);

        Assert.assertTrue(response.getStatusCode().is2xxSuccessful());

        // Query a user:
        response = request("/nacos/v1/ns/auth/users",
            Params.newParams()
                .appendParam("pageNo", "1")
                .appendParam("pageSize", String.valueOf(Integer.MAX_VALUE))
                .appendParam("accessToken", accessToken)
                .done(),
            String.class);

        Assert.assertTrue(response.getStatusCode().is2xxSuccessful());

        userPage = JSON.parseObject(response.getBody(), new TypeReference<Page<User>>(){});

        Assert.assertNotNull(userPage);
        Assert.assertNotNull(userPage.getPageItems());
        Assert.assertTrue(userPage.getPageItems().size() > 0);

        found = false;
        for (User user : userPage.getPageItems()) {
            if ("username1".equals(user.getUsername())) {
                found = true;
                break;
            }
        }
        Assert.assertFalse(found);
    }

}
