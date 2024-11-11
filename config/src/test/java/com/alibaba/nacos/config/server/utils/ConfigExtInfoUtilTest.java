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

