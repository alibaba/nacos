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

package com.alibaba.nacos.config.server.utils;

import com.alibaba.nacos.config.server.model.ConfigInfo;
import org.junit.Assert;
import org.junit.Test;
import com.alibaba.nacos.config.server.utils.ConfigToPropertiesUtil.ConfigProperties;

public class ConfigToPropertiesUtilTest {
    
    @Test
    public void testYamlToProperties() {
        ConfigInfo configInfo = new ConfigInfo("test", "test", "server:\n" + "    port: 8848\n");
        configInfo.setType("yaml");
    
        ConfigProperties properties = ConfigToPropertiesUtil.configToProperties(configInfo);
        
        Assert.assertTrue(properties.containsConfig("server.port"));
        Assert.assertEquals(properties.getProperty("server.port"), "8848");
    }
    
    @Test
    public void testXmlToProperties() {
        ConfigInfo configInfo = new ConfigInfo("test", "test", "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"no\"?>\n"
                + "<!DOCTYPE properties SYSTEM \"http://java.sun.com/dtd/properties.dtd\">\n" + "<properties>\n"
                + "    <comment>application.properties</comment>\n"
                + "    <entry key=\"server.port\">8848</entry>\n" + "</properties>");
        configInfo.setType("xml");
    
        ConfigProperties properties = ConfigToPropertiesUtil.configToProperties(configInfo);
    
        Assert.assertTrue(properties.containsConfig("server.port"));
        Assert.assertEquals(properties.getProperty("server.port"), "8848");
    }
    
    @Test
    public void testPropertiesToProperties() {
        ConfigInfo configInfo = new ConfigInfo("test", "test", "server.port=8848");
        configInfo.setType("properties");
    
        ConfigProperties properties = ConfigToPropertiesUtil.configToProperties(configInfo);
    
        Assert.assertTrue(properties.containsConfig("server.port"));
        Assert.assertEquals(properties.getProperty("server.port"), "8848");
    }
    
    @Test
    public void testJsonToProperties() {
        ConfigInfo configInfo = new ConfigInfo("test", "test", "{\n" + "    \"server\": {\n" + "        \"port\": 8848\n"
                + "    }\n" + "}");
        configInfo.setType("json");
    
        ConfigProperties properties = ConfigToPropertiesUtil.configToProperties(configInfo);
    
        Assert.assertTrue(properties.containsConfig("server.port"));
        Assert.assertEquals(properties.getProperty("server.port"), "8848");
    }
    
    @Test
    public void testTextToProperties() {
        ConfigInfo configInfo = new ConfigInfo("test", "test", "hello world server.port");
        configInfo.setType("text");
    
        ConfigProperties properties = ConfigToPropertiesUtil.configToProperties(configInfo);
        
        Assert.assertTrue(properties.containsConfig("server.port"));
    }
    
    @Test
    public void testHtmlToProperties() {
        ConfigInfo configInfo = new ConfigInfo("test", "test", "<body>server.port</body>");
        configInfo.setType("html");
        
        ConfigProperties properties = ConfigToPropertiesUtil.configToProperties(configInfo);
        
        Assert.assertTrue(properties.containsConfig("server.port"));
    }
    
}
