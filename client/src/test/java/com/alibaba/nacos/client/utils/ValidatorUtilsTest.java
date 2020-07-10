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

import org.junit.Test;

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
    
}
