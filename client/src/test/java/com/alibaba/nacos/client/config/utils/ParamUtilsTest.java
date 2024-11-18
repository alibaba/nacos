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

package com.alibaba.nacos.client.config.utils;

import com.alibaba.nacos.api.exception.NacosException;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

class ParamUtilsTest {
    
    @Test
    void testIsValid() {
        String content = "abcABC09.:_-";
        assertTrue(ParamUtils.isValid(content));
        
        content = null;
        assertFalse(ParamUtils.isValid(content));
        
        content = "@";
        assertFalse(ParamUtils.isValid(content));
        
        content = "+";
        assertFalse(ParamUtils.isValid(content));
        
        content = "/";
        assertFalse(ParamUtils.isValid(content));
    }
    
    @Test
    void testCheckTdg() throws NacosException {
        String tenant = "a";
        String dataId = "b";
        String group = "c";
        ParamUtils.checkTdg(tenant, dataId, group);
    }
    
    @Test
    void testCheckTdgFail1() throws NacosException {
        Throwable exception = assertThrows(NacosException.class, () -> {
            
            String tenant = "a";
            String dataId = "";
            String group = "c";
            ParamUtils.checkTdg(tenant, dataId, group);
        });
        assertTrue(exception.getMessage().contains("dataId invalid"));
    }
    
    @Test
    void testCheckTdgFail2() throws NacosException {
        Throwable exception = assertThrows(NacosException.class, () -> {
            
            String tenant = "a";
            String dataId = "b";
            String group = "";
            ParamUtils.checkTdg(tenant, dataId, group);
        });
        assertTrue(exception.getMessage().contains("group invalid"));
    }
    
    @Test
    void testCheckKeyParam1() throws NacosException {
        String dataId = "b";
        String group = "c";
        ParamUtils.checkKeyParam(dataId, group);
        
        try {
            dataId = "";
            group = "c";
            ParamUtils.checkKeyParam(dataId, group);
            fail();
        } catch (NacosException e) {
            assertEquals("dataId invalid", e.getMessage());
        }
        
        try {
            dataId = "b";
            group = "";
            ParamUtils.checkKeyParam(dataId, group);
            fail();
        } catch (NacosException e) {
            assertEquals("group invalid", e.getMessage());
        }
    }
    
    @Test
    void testCheckKeyParam2() throws NacosException {
        String dataId = "b";
        String group = "c";
        String datumId = "a";
        ParamUtils.checkKeyParam(dataId, group, datumId);
        
        try {
            dataId = "";
            group = "c";
            ParamUtils.checkKeyParam(dataId, group, datumId);
            
            fail();
        } catch (NacosException e) {
            assertEquals("dataId invalid", e.getMessage());
        }
        
        try {
            dataId = "b";
            group = "";
            ParamUtils.checkKeyParam(dataId, group, datumId);
            
            fail();
        } catch (NacosException e) {
            assertEquals("group invalid", e.getMessage());
        }
        
        try {
            dataId = "b";
            group = "c";
            datumId = "";
            ParamUtils.checkKeyParam(dataId, group, datumId);
            
            fail();
        } catch (NacosException e) {
            assertEquals("datumId invalid", e.getMessage());
        }
    }
    
    @Test
    void testCheckKeyParam3() throws NacosException {
        String dataId = "b";
        String group = "c";
        ParamUtils.checkKeyParam(Arrays.asList(dataId), group);
        
        try {
            group = "c";
            ParamUtils.checkKeyParam(new ArrayList<String>(), group);
            
            fail();
        } catch (NacosException e) {
            assertEquals("dataIds invalid", e.getMessage());
        }
        
        try {
            dataId = "";
            group = "c";
            ParamUtils.checkKeyParam(Arrays.asList(dataId), group);
            
            fail();
        } catch (NacosException e) {
            assertEquals("dataId invalid", e.getMessage());
        }
        
        try {
            dataId = "b";
            group = "";
            ParamUtils.checkKeyParam(Arrays.asList(dataId), group);
            
            fail();
        } catch (NacosException e) {
            assertEquals("group invalid", e.getMessage());
        }
    }
    
    @Test
    void testCheckParam() throws NacosException {
        String dataId = "b";
        String group = "c";
        String content = "a";
        ParamUtils.checkParam(dataId, group, content);
    }
    
    @Test
    void testCheckParamFail() throws NacosException {
        Throwable exception = assertThrows(NacosException.class, () -> {
            
            String dataId = "b";
            String group = "c";
            String content = "";
            ParamUtils.checkParam(dataId, group, content);
        });
        assertTrue(exception.getMessage().contains("content invalid"));
    }
    
    @Test
    void testCheckParam2() throws NacosException {
        String dataId = "b";
        String group = "c";
        String datumId = "d";
        String content = "a";
        ParamUtils.checkParam(dataId, group, datumId, content);
    }
    
    @Test
    void testCheckParam2Fail() throws NacosException {
        Throwable exception = assertThrows(NacosException.class, () -> {
            
            String dataId = "b";
            String group = "c";
            String datumId = "d";
            String content = "";
            ParamUtils.checkParam(dataId, group, datumId, content);
        });
        assertTrue(exception.getMessage().contains("content invalid"));
    }
    
    @Test
    void testCheckTenant() throws NacosException {
        String tenant = "a";
        ParamUtils.checkTenant(tenant);
    }
    
    @Test
    void testCheckTenantFail() throws NacosException {
        Throwable exception = assertThrows(NacosException.class, () -> {
            String tenant = "";
            ParamUtils.checkTenant(tenant);
        });
        assertTrue(exception.getMessage().contains("tenant invalid"));
    }
    
    @Test
    void testCheckBetaIps() throws NacosException {
        ParamUtils.checkBetaIps("127.0.0.1");
    }
    
    @Test
    void testCheckBetaIpsFail1() throws NacosException {
        Throwable exception = assertThrows(NacosException.class, () -> {
            
            ParamUtils.checkBetaIps("");
        });
        assertTrue(exception.getMessage().contains("betaIps invalid"));
    }
    
    @Test
    void testCheckBetaIpsFail2() throws NacosException {
        Throwable exception = assertThrows(NacosException.class, () -> {
            ParamUtils.checkBetaIps("aaa");
        });
        assertTrue(exception.getMessage().contains("betaIps invalid"));
    }
    
    @Test
    void testCheckContent() throws NacosException {
        ParamUtils.checkContent("aaa");
    }
    
    @Test
    void testCheckContentFail() throws NacosException {
        Throwable exception = assertThrows(NacosException.class, () -> {
            ParamUtils.checkContent("");
        });
        assertTrue(exception.getMessage().contains("content invalid"));
    }
}