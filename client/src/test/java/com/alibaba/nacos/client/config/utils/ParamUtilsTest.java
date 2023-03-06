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
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import java.util.ArrayList;
import java.util.Arrays;

public class ParamUtilsTest {
    
    @Rule
    public ExpectedException exceptionRule = ExpectedException.none();
    
    @Test
    public void testIsValid() {
        String content = "abcABC09.:_-";
        Assert.assertTrue(ParamUtils.isValid(content));
        
        content = null;
        Assert.assertFalse(ParamUtils.isValid(content));
        
        content = "@";
        Assert.assertFalse(ParamUtils.isValid(content));
        
        content = "+";
        Assert.assertFalse(ParamUtils.isValid(content));
        
        content = "/";
        Assert.assertFalse(ParamUtils.isValid(content));
    }
    
    @Test
    public void testCheckTdg() throws NacosException {
        String tenant = "a";
        String dataId = "b";
        String group = "c";
        ParamUtils.checkTdg(tenant, dataId, group);
    }
    
    @Test
    public void testCheckTdgFail1() throws NacosException {
        exceptionRule.expect(NacosException.class);
        exceptionRule.expectMessage("dataId invalid");
        
        String tenant = "a";
        String dataId = "";
        String group = "c";
        ParamUtils.checkTdg(tenant, dataId, group);
    }
    
    @Test
    public void testCheckTdgFail2() throws NacosException {
        exceptionRule.expect(NacosException.class);
        exceptionRule.expectMessage("group invalid");
        
        String tenant = "a";
        String dataId = "b";
        String group = "";
        ParamUtils.checkTdg(tenant, dataId, group);
    }
    
    @Test
    public void testCheckKeyParam1() throws NacosException {
        String dataId = "b";
        String group = "c";
        ParamUtils.checkKeyParam(dataId, group);
        
        try {
            dataId = "";
            group = "c";
            ParamUtils.checkKeyParam(dataId, group);
            Assert.fail();
        } catch (NacosException e) {
            Assert.assertEquals("dataId invalid", e.getMessage());
        }
        
        try {
            dataId = "b";
            group = "";
            ParamUtils.checkKeyParam(dataId, group);
            Assert.fail();
        } catch (NacosException e) {
            Assert.assertEquals("group invalid", e.getMessage());
        }
    }
    
    @Test
    public void testCheckKeyParam2() throws NacosException {
        String dataId = "b";
        String group = "c";
        String datumId = "a";
        ParamUtils.checkKeyParam(dataId, group, datumId);
        
        try {
            dataId = "";
            group = "c";
            ParamUtils.checkKeyParam(dataId, group, datumId);
            
            Assert.fail();
        } catch (NacosException e) {
            Assert.assertEquals("dataId invalid", e.getMessage());
        }
        
        try {
            dataId = "b";
            group = "";
            ParamUtils.checkKeyParam(dataId, group, datumId);
            
            Assert.fail();
        } catch (NacosException e) {
            Assert.assertEquals("group invalid", e.getMessage());
        }
        
        try {
            dataId = "b";
            group = "c";
            datumId = "";
            ParamUtils.checkKeyParam(dataId, group, datumId);
            
            Assert.fail();
        } catch (NacosException e) {
            Assert.assertEquals("datumId invalid", e.getMessage());
        }
    }
    
    @Test
    public void testCheckKeyParam3() throws NacosException {
        String dataId = "b";
        String group = "c";
        ParamUtils.checkKeyParam(Arrays.asList(dataId), group);
        
        try {
            group = "c";
            ParamUtils.checkKeyParam(new ArrayList<String>(), group);
            
            Assert.fail();
        } catch (NacosException e) {
            Assert.assertEquals("dataIds invalid", e.getMessage());
        }
        
        try {
            dataId = "";
            group = "c";
            ParamUtils.checkKeyParam(Arrays.asList(dataId), group);
            
            Assert.fail();
        } catch (NacosException e) {
            Assert.assertEquals("dataId invalid", e.getMessage());
        }
        
        try {
            dataId = "b";
            group = "";
            ParamUtils.checkKeyParam(Arrays.asList(dataId), group);
            
            Assert.fail();
        } catch (NacosException e) {
            Assert.assertEquals("group invalid", e.getMessage());
        }
    }
    
    @Test
    public void testCheckParam() throws NacosException {
        String dataId = "b";
        String group = "c";
        String content = "a";
        ParamUtils.checkParam(dataId, group, content);
    }
 
    @Test
    public void testCheckParamFail() throws NacosException {
        exceptionRule.expect(NacosException.class);
        exceptionRule.expectMessage("content invalid");
        
        String dataId = "b";
        String group = "c";
        String content = "";
        ParamUtils.checkParam(dataId, group, content);
    }
    
    @Test
    public void testCheckParam2() throws NacosException {
        String dataId = "b";
        String group = "c";
        String datumId = "d";
        String content = "a";
        ParamUtils.checkParam(dataId, group, datumId, content);
    }
    
    @Test
    public void testCheckParam2Fail() throws NacosException {
        exceptionRule.expect(NacosException.class);
        exceptionRule.expectMessage("content invalid");
    
        String dataId = "b";
        String group = "c";
        String datumId = "d";
        String content = "";
        ParamUtils.checkParam(dataId, group, datumId, content);
    }
    
    @Test
    public void testCheckTenant() throws NacosException {
        String tenant = "a";
        ParamUtils.checkTenant(tenant);
    }
    
    @Test
    public void testCheckTenantFail() throws NacosException {
        exceptionRule.expect(NacosException.class);
        exceptionRule.expectMessage("tenant invalid");
        String tenant = "";
        ParamUtils.checkTenant(tenant);
    }
    
    @Test
    public void testCheckBetaIps() throws NacosException {
        ParamUtils.checkBetaIps("127.0.0.1");
    }
    
    @Test
    public void testCheckBetaIpsFail1() throws NacosException {
        exceptionRule.expect(NacosException.class);
        exceptionRule.expectMessage("betaIps invalid");
        
        ParamUtils.checkBetaIps("");
    }
    
    @Test
    public void testCheckBetaIpsFail2() throws NacosException {
        exceptionRule.expect(NacosException.class);
        exceptionRule.expectMessage("betaIps invalid");
        ParamUtils.checkBetaIps("aaa");
    }
    
    @Test
    public void testCheckContent() throws NacosException {
        ParamUtils.checkContent("aaa");
    }
    
    @Test
    public void testCheckContentFail() throws NacosException {
        exceptionRule.expect(NacosException.class);
        exceptionRule.expectMessage("content invalid");
        ParamUtils.checkContent("");
    }
}