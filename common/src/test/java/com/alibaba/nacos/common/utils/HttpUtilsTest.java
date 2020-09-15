package com.alibaba.nacos.common.utils;

import com.alibaba.nacos.common.http.HttpUtils;
import org.junit.Assert;
import org.junit.Test;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;

public class HttpUtilsTest {
    
    @Test
    public void decode() throws UnsupportedEncodingException {
        String str = "++--abc123,./';!@#$^&*()_+{}|";
        String encodeStr = HttpUtils.decode(str, "UTF-8");
        Assert.assertEquals(str, encodeStr);
    }
    
    @Test
    public void encodeAndDecode() throws UnsupportedEncodingException {
        String str = "++--abc123,./';!@#$^&*()_+{}|";
        String encodeStr = URLEncoder.encode(str, "UTF-8");
        String decodeStr = HttpUtils.decode(encodeStr, "UTF-8");
        Assert.assertEquals(str, decodeStr);
    }
    
    @Test
    public void multipleEncode() throws UnsupportedEncodingException {
        String str = "++--abc123,./';!@#$^&*()_+{}|";
        String encodeStr = URLEncoder.encode(URLEncoder.encode(str, "UTF-8"), "UTF-8");
        String decodeStr = HttpUtils.decode(encodeStr, "UTF-8");
        Assert.assertEquals(str, decodeStr);
    }
}
