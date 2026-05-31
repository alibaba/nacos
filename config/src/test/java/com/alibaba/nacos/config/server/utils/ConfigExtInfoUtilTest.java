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

import com.alibaba.nacos.api.config.ConfigType;
import com.alibaba.nacos.config.server.model.ConfigAllInfo;
import com.alibaba.nacos.config.server.model.gray.BetaGrayRule;
import com.alibaba.nacos.config.server.model.gray.ConfigGrayPersistInfo;
import com.alibaba.nacos.config.server.model.gray.GrayRuleManager;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static com.alibaba.nacos.config.server.model.gray.BetaGrayRule.PRIORITY;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigExtInfoUtilTest {
    
    @Test
    void testExt4Formal() {
        ConfigAllInfo configAllInfo = new ConfigAllInfo();
        configAllInfo.setDataId("dataId4567");
        configAllInfo.setGroup("group3456789");
        configAllInfo.setTenant("tenant4567890");
        configAllInfo.setAppName("old_app");
        configAllInfo.setMd5("old_md5");
        configAllInfo.setId(12345678765L);
        configAllInfo.setType(ConfigType.JSON.getType());
        configAllInfo.setSchema("testschema");
        configAllInfo.setCreateUser("testuser");
        configAllInfo.setEffect("online");
        configAllInfo.setDesc("desc");
        configAllInfo.setUse("use124");
        configAllInfo.setConfigTags("ctag1,ctag2");
        String result = ConfigExtInfoUtil.getExtInfoFromAllInfo(configAllInfo);
        assertNotNull(result);
        assertTrue(result.contains("json"));
    }
    
    @Test
    void testExt4FormalAllBlank() {
        ConfigAllInfo configAllInfo = new ConfigAllInfo();
        String result = ConfigExtInfoUtil.getExtInfoFromAllInfo(configAllInfo);
        assertNotNull(result);
    }
    
    @Test
    void testExt4Gray() {
        ConfigGrayPersistInfo configGrayPersistInfo =
            new ConfigGrayPersistInfo(BetaGrayRule.TYPE_BETA,
                BetaGrayRule.VERSION, "127.0.0.1,127.0.0.2", PRIORITY);
        String grayRule =
            GrayRuleManager.serializeConfigGrayPersistInfo(configGrayPersistInfo);
        String result =
            ConfigExtInfoUtil.getExtInfoFromGrayInfo("gray124", grayRule, "user132");
        assertNotNull(result);
        assertTrue(result.contains("gray_name"));
    }
    
    @Test
    void testExt4GrayWithBlanks() {
        String result = ConfigExtInfoUtil.getExtInfoFromGrayInfo("", "", "");
        assertNotNull(result);
    }
    
    @Test
    void testExt4GrayWithInvalidJson() {
        String result =
            ConfigExtInfoUtil.getExtInfoFromGrayInfo("gray", "not{json", "user");
        assertNull(result);
    }
    
    @Test
    void testGetExtraInfoFromAdvanceInfoMapNull() {
        assertNull(ConfigExtInfoUtil.getExtraInfoFromAdvanceInfoMap(null, "user"));
    }
    
    @Test
    void testGetExtraInfoFromAdvanceInfoMapEmpty() {
        assertNull(
            ConfigExtInfoUtil.getExtraInfoFromAdvanceInfoMap(new HashMap<>(), "user"));
    }
    
    @Test
    void testGetExtraInfoFromAdvanceInfoMapWithValues() {
        Map<String, Object> map = new HashMap<>();
        map.put("type", "json");
        map.put("desc", "test desc");
        map.put("config_tags", "tag1,tag2");
        map.put("use", "testUse");
        map.put("effect", "online");
        map.put("schema", "s");
        String result =
            ConfigExtInfoUtil.getExtraInfoFromAdvanceInfoMap(map, "srcUser");
        assertNotNull(result);
        assertTrue(result.contains("src_user"));
        assertTrue(result.contains("json"));
    }
    
    @Test
    void testGetExtraInfoFromAdvanceInfoMapWithBlankSrcUser() {
        Map<String, Object> map = new HashMap<>();
        map.put("type", "yaml");
        String result =
            ConfigExtInfoUtil.getExtraInfoFromAdvanceInfoMap(map, "");
        assertNotNull(result);
    }
    
    @Test
    void testGetExtraInfoFromAdvanceInfoMapWithNonStringValue() {
        Map<String, Object> map = new HashMap<>();
        map.put("type", 123);
        map.put("desc", "valid desc");
        String result =
            ConfigExtInfoUtil.getExtraInfoFromAdvanceInfoMap(map, "user");
        assertNotNull(result);
        assertTrue(result.contains("c_desc"));
        assertFalse(result.contains("123"));
    }
    
    @Test
    void testGetExtraInfoFromAdvanceInfoMapWithBlankStringValue() {
        Map<String, Object> map = new HashMap<>();
        map.put("type", "  ");
        map.put("desc", "desc");
        String result =
            ConfigExtInfoUtil.getExtraInfoFromAdvanceInfoMap(map, null);
        assertNotNull(result);
        assertTrue(result.contains("c_desc"));
    }
    
    @Test
    void testGetExtraInfoFromAdvanceInfoMapReturnsNullWhenMapThrowsException() {
        Map<String, Object> map = new HashMap<>() {
            
            @Override
            public boolean isEmpty() {
                return false;
            }
            
            @Override
            public Object get(Object key) {
                throw new IllegalStateException("broken map");
            }
        };
        
        assertNull(ConfigExtInfoUtil.getExtraInfoFromAdvanceInfoMap(map, "user"));
    }
    
    @Test
    void testExt4GrayWithPartialGrayRule() {
        String partialGrayRule = "{\"type\":\"tag\"}";
        String result =
            ConfigExtInfoUtil.getExtInfoFromGrayInfo("gray1", partialGrayRule, "user1");
        assertNotNull(result);
        assertTrue(result.contains("type"));
        assertTrue(result.contains("tag"));
    }
    
    @Test
    void testExt4GrayWithOnlyExprAndVersion() {
        String partialGrayRule =
            "{\"expr\":\"ip=1.1.1.1\",\"version\":\"v1\"}";
        String result =
            ConfigExtInfoUtil.getExtInfoFromGrayInfo("gray2", partialGrayRule, "user2");
        assertNotNull(result);
        assertTrue(result.contains("expr"));
        assertTrue(result.contains("version"));
    }
}
