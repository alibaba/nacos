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

package com.alibaba.nacos.client.utils;

import com.alibaba.nacos.api.PropertyKeyConst;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.client.env.NacosClientProperties;
import org.junit.Assert;
import org.junit.Test;

import java.util.Properties;

public class ValidatorUtilsTest {
    
    @Test
    public void testContextPathLegal() {
        String contextPath1 = "/nacos";
        ValidatorUtils.checkContextPath(contextPath1);
        String contextPath2 = "nacos";
        ValidatorUtils.checkContextPath(contextPath2);
        String contextPath3 = "/";
        ValidatorUtils.checkContextPath(contextPath3);
        String contextPath4 = "";
        ValidatorUtils.checkContextPath(contextPath4);
    }
    
    @Test
    public void testValidUrlLegal() {
        String url1 = "http://localhost:4318/v1/metrics";
        String result1 = ValidatorUtils.checkValidUrl(url1);
        Assert.assertNotNull(result1);
        String url2 = "https://localhost:4318/v1/metrics";
        String result2 = ValidatorUtils.checkValidUrl(url2);
        Assert.assertNotNull(result2);
        String url3 = "http://127.0.0.1:4318";
        String result3 = ValidatorUtils.checkValidUrl(url3);
        Assert.assertNotNull(result3);
        String url4 = "https://127.0.0.1:4318";
        String result4 = ValidatorUtils.checkValidUrl(url4);
        Assert.assertNotNull(result4);
        String url5 = "http://127.0.0.1";
        String result5 = ValidatorUtils.checkValidUrl(url5);
        Assert.assertNotNull(result5);
        String url6 = "https://127.0.0.1";
        String result6 = ValidatorUtils.checkValidUrl(url6);
        Assert.assertNotNull(result6);
    }
    
    @Test
    public void testValidUrlIllegal() {
        String url1 = "htt://localhost:4318/v1/metrics";
        String result1 = ValidatorUtils.checkValidUrl(url1);
        Assert.assertNull(result1);
        String url2 = "https//localhost:4318/v1/metrics";
        String result2 = ValidatorUtils.checkValidUrl(url2);
        Assert.assertNull(result2);
        String url3 = "http://127.0.0.:4318";
        String result3 = ValidatorUtils.checkValidUrl(url3);
        Assert.assertNull(result3);
        String url4 = "https:/127.0.0.1:4318";
        String result4 = ValidatorUtils.checkValidUrl(url4);
        Assert.assertNull(result4);
        String url5 = "http://127.0.0.14318";
        String result5 = ValidatorUtils.checkValidUrl(url5);
        Assert.assertNull(result5);
        String url6 = "ftp://127.0.0.1:4318";
        String result6 = ValidatorUtils.checkValidUrl(url6);
        Assert.assertNull(result6);
        String url7 = "nacos";
        String result7 = ValidatorUtils.checkValidUrl(url7);
        Assert.assertNull(result7);
    }
    
    @Test(expected = IllegalArgumentException.class)
    public void testContextPathIllegal1() {
        String contextPath1 = "//nacos/";
        ValidatorUtils.checkContextPath(contextPath1);
    }
    
    @Test(expected = IllegalArgumentException.class)
    public void testContextPathIllegal2() {
        String contextPath2 = "/nacos//";
        ValidatorUtils.checkContextPath(contextPath2);
    }
    
    @Test(expected = IllegalArgumentException.class)
    public void testContextPathIllegal3() {
        String contextPath3 = "///";
        ValidatorUtils.checkContextPath(contextPath3);
    }
    
    @Test(expected = IllegalArgumentException.class)
    public void testContextPathIllegal4() {
        String contextPath4 = "//";
        ValidatorUtils.checkContextPath(contextPath4);
    }
    
    @Test
    public void testCheckInitParam() {
        try {
            Properties properties = new Properties();
            properties.setProperty(PropertyKeyConst.CONTEXT_PATH, "test");
            
            final NacosClientProperties nacosClientProperties = NacosClientProperties.PROTOTYPE.derive(properties);
            ValidatorUtils.checkInitParam(nacosClientProperties);
        } catch (NacosException e) {
            Assert.fail();
        }
    }
}
