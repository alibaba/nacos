/*
 *
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
 *
 */

package com.alibaba.nacos.client.utils;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TenantUtilTest {
    
    @AfterEach
    void tearDown() {
        System.clearProperty("acm.namespace");
        System.clearProperty("ans.namespace");
    }
    
    @Test
    void testGetUserTenantForAcm() {
        String expect = "test";
        System.setProperty("acm.namespace", expect);
        String actual = TenantUtil.getUserTenantForAcm();
        assertEquals(expect, actual);
    }
    
    @Test
    void testGetUserTenantForAns() {
        String expect = "test";
        System.setProperty("ans.namespace", expect);
        String actual = TenantUtil.getUserTenantForAns();
        assertEquals(expect, actual);
    }
    
}
