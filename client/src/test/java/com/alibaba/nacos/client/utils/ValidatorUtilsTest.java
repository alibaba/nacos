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
import com.alibaba.nacos.client.env.NacosClientProperties;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertThrows;

class ValidatorUtilsTest {
    
    @Test
    void testContextPathLegal() {
        String contextPath1 = "/nacos";
        ValidatorUtils.checkContextPath(contextPath1);
        String contextPath2 = "nacos";
        ValidatorUtils.checkContextPath(contextPath2);
        String contextPath3 = "/";
        ValidatorUtils.checkContextPath(contextPath3);
        String contextPath4 = "";
        ValidatorUtils.checkContextPath(contextPath4);
        // allow null
        ValidatorUtils.checkContextPath(null);
    }
    
    @Test
    void testContextPathIllegal1() {
        assertThrows(IllegalArgumentException.class, () -> {
            String contextPath1 = "//nacos/";
            ValidatorUtils.checkContextPath(contextPath1);
        });
    }
    
    @Test
    void testContextPathIllegal2() {
        assertThrows(IllegalArgumentException.class, () -> {
            String contextPath2 = "/nacos//";
            ValidatorUtils.checkContextPath(contextPath2);
        });
    }
    
    @Test
    void testContextPathIllegal3() {
        assertThrows(IllegalArgumentException.class, () -> {
            String contextPath3 = "///";
            ValidatorUtils.checkContextPath(contextPath3);
        });
    }
    
    @Test
    void testContextPathIllegal4() {
        assertThrows(IllegalArgumentException.class, () -> {
            String contextPath4 = "//";
            ValidatorUtils.checkContextPath(contextPath4);
        });
    }
    
    @Test
    void testCheckInitParam() {
        Assertions.assertDoesNotThrow(() -> {
            Properties properties = new Properties();
            properties.setProperty(PropertyKeyConst.CONTEXT_PATH, "test");
            final NacosClientProperties nacosClientProperties = NacosClientProperties.PROTOTYPE.derive(properties);
            ValidatorUtils.checkInitParam(nacosClientProperties);
        });
    }
}
