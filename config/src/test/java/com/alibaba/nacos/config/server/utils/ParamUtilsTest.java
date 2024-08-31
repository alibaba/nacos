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

import com.alibaba.nacos.api.exception.NacosException;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

class ParamUtilsTest {
    
    @Test
    void testIsValid() {
        assertTrue(ParamUtils.isValid("test"));
        assertTrue(ParamUtils.isValid("test1234"));
        assertTrue(ParamUtils.isValid("test_-.:"));
        assertFalse(ParamUtils.isValid("test!"));
        assertFalse(ParamUtils.isValid("test~"));
    }
    
    @Test
    void testCheckParamV1() {
        //dataId is empty
        String dataId = "";
        String group = "test";
        String datumId = "test";
        String content = "test";
        try {
            ParamUtils.checkParam(dataId, group, datumId, content);
            fail();
        } catch (NacosException e) {
            System.out.println(e.toString());
        }
        //group is empty
        dataId = "test";
        group = "";
        datumId = "test";
        content = "test";
        try {
            ParamUtils.checkParam(dataId, group, datumId, content);
            fail();
        } catch (NacosException e) {
            System.out.println(e.toString());
        }
        //datumId is empty
        dataId = "test";
        group = "test";
        datumId = "";
        content = "test";
        try {
            ParamUtils.checkParam(dataId, group, datumId, content);
            fail();
        } catch (NacosException e) {
            System.out.println(e.toString());
        }
        //content is empty
        dataId = "test";
        group = "test";
        datumId = "test";
        content = "";
        try {
            ParamUtils.checkParam(dataId, group, datumId, content);
            fail();
        } catch (NacosException e) {
            System.out.println(e.toString());
        }
        //dataId invalid
        dataId = "test!";
        group = "test";
        datumId = "test";
        content = "test";
        try {
            ParamUtils.checkParam(dataId, group, datumId, content);
            fail();
        } catch (NacosException e) {
            System.out.println(e.toString());
        }
        //group invalid
        dataId = "test";
        group = "test!";
        datumId = "test";
        content = "test";
        try {
            ParamUtils.checkParam(dataId, group, datumId, content);
            fail();
        } catch (NacosException e) {
            System.out.println(e.toString());
        }
        //datumId invalid
        dataId = "test";
        group = "test";
        datumId = "test!";
        content = "test";
        try {
            ParamUtils.checkParam(dataId, group, datumId, content);
            fail();
        } catch (NacosException e) {
            System.out.println(e.toString());
        }
        //content over length
        dataId = "test";
        group = "test";
        datumId = "test";
        int maxContent = 10 * 1024 * 1024;
        StringBuilder contentBuilder = new StringBuilder();
        for (int i = 0; i < maxContent + 1; i++) {
            contentBuilder.append("t");
        }
        content = contentBuilder.toString();
        
        try {
            ParamUtils.checkParam(dataId, group, datumId, content);
            fail();
        } catch (NacosException e) {
            System.out.println(e.toString());
        }
    }
    
    @Test
    void testCheckParamV2() {
        //tag invalid
        String tag = "test!";
        try {
            ParamUtils.checkParam(tag);
            fail();
        } catch (IllegalArgumentException e) {
            System.out.println(e.toString());
        }
        
        //tag over length
        tag = "testtesttesttest1";
        try {
            ParamUtils.checkParam(tag);
            fail();
        } catch (IllegalArgumentException e) {
            System.out.println(e.toString());
        }
        
    }
    
    @Test
    void testCheckParamV3() {
        //tag size over 5
        Map<String, Object> configAdvanceInfo = new HashMap<>();
        configAdvanceInfo.put("config_tags", "test,test,test,test,test,test");
        try {
            ParamUtils.checkParam(configAdvanceInfo);
            fail();
        } catch (NacosException e) {
            System.out.println(e.toString());
        }
        //tag length over 5
        configAdvanceInfo.clear();
        StringBuilder tagBuilder = new StringBuilder();
        for (int i = 0; i < 65; i++) {
            tagBuilder.append("t");
        }
        configAdvanceInfo.put("config_tags", tagBuilder.toString());
        try {
            ParamUtils.checkParam(configAdvanceInfo);
            fail();
        } catch (NacosException e) {
            System.out.println(e.toString());
        }
        
        //desc length over 128
        configAdvanceInfo.clear();
        StringBuilder descBuilder = new StringBuilder();
        for (int i = 0; i < 129; i++) {
            descBuilder.append("t");
        }
        configAdvanceInfo.put("desc", descBuilder.toString());
        try {
            ParamUtils.checkParam(configAdvanceInfo);
            fail();
        } catch (NacosException e) {
            System.out.println(e.toString());
        }
        
        //use length over 32
        configAdvanceInfo.clear();
        StringBuilder useBuilder = new StringBuilder();
        for (int i = 0; i < 33; i++) {
            useBuilder.append("t");
        }
        configAdvanceInfo.put("use", useBuilder.toString());
        try {
            ParamUtils.checkParam(configAdvanceInfo);
            fail();
        } catch (NacosException e) {
            System.out.println(e.toString());
        }
        
        //effect length over 32
        configAdvanceInfo.clear();
        StringBuilder effectBuilder = new StringBuilder();
        for (int i = 0; i < 33; i++) {
            effectBuilder.append("t");
        }
        configAdvanceInfo.put("effect", effectBuilder.toString());
        try {
            ParamUtils.checkParam(configAdvanceInfo);
            fail();
        } catch (NacosException e) {
            System.out.println(e.toString());
        }
        
        //type length over 32
        configAdvanceInfo.clear();
        StringBuilder typeBuilder = new StringBuilder();
        for (int i = 0; i < 33; i++) {
            typeBuilder.append("t");
        }
        configAdvanceInfo.put("type", typeBuilder.toString());
        try {
            ParamUtils.checkParam(configAdvanceInfo);
            fail();
        } catch (NacosException e) {
            System.out.println(e.toString());
        }
        
        //schema length over 32768
        configAdvanceInfo.clear();
        StringBuilder schemaBuilder = new StringBuilder();
        for (int i = 0; i < 32769; i++) {
            schemaBuilder.append("t");
        }
        configAdvanceInfo.put("schema", schemaBuilder.toString());
        try {
            ParamUtils.checkParam(configAdvanceInfo);
            fail();
        } catch (NacosException e) {
            System.out.println(e.toString());
        }
        
        //invalid param
        configAdvanceInfo.clear();
        configAdvanceInfo.put("test", "test");
        try {
            ParamUtils.checkParam(configAdvanceInfo);
            fail();
        } catch (NacosException e) {
            System.out.println(e.toString());
        }
    }
    
    @Test
    void testCheckTenant() {
        //tag invalid
        String tenant = "test!";
        try {
            ParamUtils.checkTenant(tenant);
            fail();
        } catch (IllegalArgumentException e) {
            System.out.println(e.toString());
        }
        
        //tag over length
        int tanantMaxLen = 128;
        StringBuilder tenantBuilder = new StringBuilder();
        for (int i = 0; i < tanantMaxLen + 1; i++) {
            tenantBuilder.append("t");
        }
        tenant = tenantBuilder.toString();
        try {
            ParamUtils.checkTenant(tenant);
            fail();
        } catch (IllegalArgumentException e) {
            System.out.println(e.toString());
        }
    }
    
}
