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

import static com.alibaba.nacos.config.server.model.gray.BetaGrayRule.PRIORITY;

public class ConfigExtInfoUtilTest {
    
    @Test
    void testExt4Formal() {
        
        String dataId = "dataId4567";
        String group = "group3456789";
        String tenant = "tenant4567890";
        
        //mock exist config info
        ConfigAllInfo configAllInfo = new ConfigAllInfo();
        configAllInfo.setDataId(dataId);
        configAllInfo.setGroup(group);
        configAllInfo.setTenant(tenant);
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
        String extraInfoFromAllInfo = ConfigExtInfoUtil.getExtInfoFromAllInfo(configAllInfo);
        System.out.println(extraInfoFromAllInfo);
        
    }
    
    @Test
    void testExt4Gray() {
        String grayName = "gray124";
        ConfigGrayPersistInfo configGrayPersistInfo = new ConfigGrayPersistInfo(BetaGrayRule.TYPE_BETA,
                BetaGrayRule.VERSION, "127.0.0.1,127.0.0.2", PRIORITY);
        
        String grayRule = GrayRuleManager.serializeConfigGrayPersistInfo(configGrayPersistInfo);
        String oldSrcUser = "user132";
        String extraInfoFromAllInfo = ConfigExtInfoUtil.getExtInfoFromGrayInfo(grayName, grayRule, oldSrcUser);
        System.out.println(extraInfoFromAllInfo);
        
    }
}

