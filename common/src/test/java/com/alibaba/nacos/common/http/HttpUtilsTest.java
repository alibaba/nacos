package com.alibaba.nacos.common.http;

import org.junit.Assert;
import org.junit.Test;

public class HttpUtilsTest {


    @Test
    public void test_build_url() {
        String serverAddr = "127.0.0.1:8848";
        String url = HttpUtils.buildUrl(false, serverAddr, "/nacos", "v1/ns");
        Assert.assertEquals("http://127.0.0.1:8848/nacos/v1/ns", url);
        url = HttpUtils.buildUrl(true, serverAddr, "nacos", "/v1/ns");
        Assert.assertEquals("https://127.0.0.1:8848/nacos/v1/ns", url);
    }

}