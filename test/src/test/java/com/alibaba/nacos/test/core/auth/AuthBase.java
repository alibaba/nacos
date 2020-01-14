package com.alibaba.nacos.test.core.auth;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.alibaba.nacos.test.base.HttpClient4Test;
import com.alibaba.nacos.test.base.Params;
import org.junit.Assert;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;

public class AuthBase extends HttpClient4Test {

    public String login() {

        ResponseEntity<String> response = request("/nacos/v1/auth/users/login",
            Params.newParams()
                .appendParam("username", "nacos")
                .appendParam("password", "nacos")
                .done(),
            String.class,
            HttpMethod.POST);

        Assert.assertTrue(response.getStatusCode().is2xxSuccessful());
        JSONObject json = JSON.parseObject(response.getBody());
        Assert.assertTrue(json.containsKey("accessToken"));
        return json.getString("accessToken");
    }
}
