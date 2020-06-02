package com.alibaba.nacos.config.server.service;

import com.alibaba.fastjson.JSON;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.config.server.model.ConfigAdvanceInfo;
import com.alibaba.nacos.config.server.model.ConfigAllInfo;
import com.alibaba.nacos.config.server.model.SameConfigPolicy;
import com.alibaba.nacos.config.server.modules.entity.ConfigInfo;
import com.alibaba.nacos.config.server.modules.entity.ConfigInfoBeta;
import com.alibaba.nacos.config.server.modules.entity.ConfigInfoTag;
import com.alibaba.nacos.config.server.modules.entity.HisConfigInfo;
import com.alibaba.nacos.config.server.utils.MD5;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.test.context.junit4.SpringRunner;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;

@RunWith(SpringRunner.class)
@SpringBootTest
public class PersistServiceTest {

    @Autowired
    private PersistServiceTmp persistService;

    @Test
    public void findConfigInfo4PageTest() {
        Page<ConfigInfo> page = persistService.findConfigInfo4Page(0, 10,
            "userService", "DEFAULT_GROUP", "zhangsan", "userService");
        Assert.assertTrue(page.getContent().size() > 0);
    }

    @Test
    public void findConfigInfoLike4PageTest() {
        Page<ConfigInfo> page = persistService.findConfigInfoLike4Page(0, 10,
            "userService", "DEFAULT_GROUP", "zhangsan", "userService");
        Assert.assertTrue(page.getContent().size() > 0);
    }

    @Test
    public void findConfigInfo4BetaTest() {
        ConfigInfoBeta configInfoBeta = persistService.findConfigInfo4Beta("userService",
            "1", "1");
        Assert.assertNotNull(configInfoBeta);
    }


    @Test
    public void findConfigInfo4TagTest() {
        ConfigInfoTag configInfoTag = persistService.findConfigInfo4Tag("userService", "1",
            "1", "1");
        Assert.assertNotNull(configInfoTag);
    }

    @Test
    public void findConfigInfoTest() {
        ConfigInfo configInfo = persistService.
            findConfigInfo("userService", "DEFAULT_GROUP", "zhangsan");
        Assert.assertNotNull(configInfo);
    }

    @Test
    public void findConfigAllInfoTest() {
        ConfigAllInfo configAllInfo = persistService.findConfigAllInfo("userService",
            "DEFAULT_GROUP", "zhangsan");
        Assert.assertNotNull(configAllInfo);
    }

    @Test
    public void selectTagByConfigTest() {
        List<String> list = persistService.
            selectTagByConfig("test", "test", "test");

        Assert.assertTrue(list.size() > 0);
    }


    @Test
    public void remoteTagByIdAtomicTest() {
        persistService.removeTagByIdAtomic(1l);
    }

    @Test
    public void removeConfigInfoTest() {
        persistService.removeConfigInfo("", "",
            "", "", "");
    }

    @Test
    public void removeConfigInfoTagTest() {
        persistService.removeConfigInfoTag("userService", "1", "1", "1",
            "", "");
    }

    @Test
    public void findConfigInfosByIdsTest() {
        Iterable<ConfigInfo> iterable = persistService.findConfigInfosByIds(Arrays.asList(10l));
        Assert.assertTrue(((ArrayList) iterable).size() > 0);
    }

    @Test
    public void findConfigAdvanceInfoTest() {
        ConfigAdvanceInfo configAdvanceInfo = persistService.findConfigAdvanceInfo("userService", "DEFAULT_GROUP", "zhangsan");
        Assert.assertNotNull(configAdvanceInfo);
    }


    @Test
    public void removeConfigInfo4BetaTest() {
        persistService.removeConfigInfo4Beta("userService", "1", "1");
    }

    @Test
    public void findAllConfigInfo4ExportTest() {
        List<ConfigAllInfo> list = persistService.findAllConfigInfo4Export("userService", "DEFAULT_GROUP",
            "zhangsan", "userService", Arrays.asList(10l));
        Assert.assertTrue(list.size() > 0);
    }


    @Test
    public void tenantInfoCountByTenantIdTest() {
        int countResult = persistService.tenantInfoCountByTenantId("1");
        Assert.assertTrue(countResult > 0);
    }

    @Test
    public void addConfigInfoTest() {
        persistService.addConfigInfo("127.0.0.1", "zhangsan",
            buildConfigInfo(), Timestamp.from(Instant.now()), buildMap(), false);
    }

    @Test
    public void addConfiTagsRelationAtomicTest() {
        persistService.addConfiTagsRelationAtomic(1l, "tagA,tagB",
            "userService", "DEFAULT_GROUP", "1");
    }

    @Test
    public void updateConfigInfoTest() {
//        ConfigInfo configInfo = buildConfigInfo();
//        configInfo.setId(14l);
//        persistService.updateConfigInfo(configInfo, "127.0.0.1",
//            "zhangsan", Timestamp.from(Instant.now()), buildMap(), false);
    }

    @Test
    public void batchInsertOrUpdateTest() throws NacosException {
        List<ConfigAllInfo> list = new ArrayList<>();
        list.add(buildConfigAllInfo());

        persistService.batchInsertOrUpdate(list, "zhangsan", "127.0.0.1",
            buildMap(), Timestamp.from(Instant.now()), false, SameConfigPolicy.ABORT);
    }

    @Test
    public void findConfigHistoryTest() {
        Page<HisConfigInfo> page = persistService.findConfigHistory("1", "1", "1",
            0, 10);
        Assert.assertTrue(page.getContent().size() > 0);
    }

    @Test
    public void detailConfigHistoryTest() {
        HisConfigInfo hisConfigInfo = persistService
            .detailConfigHistory(1l);
        Assert.assertNotNull(hisConfigInfo);
    }

    private ConfigAllInfo buildConfigAllInfo() {
        ConfigInfo configInfo = buildConfigInfo();
        ConfigAllInfo configAllInfo = new ConfigAllInfo();
        configAllInfo.setCreateTime(configInfo.getGmtCreate().getTime());
        configAllInfo.setModifyTime(configInfo.getGmtModified().getTime());
        configAllInfo.setCreateUser(configInfo.getSrcUser());
        configAllInfo.setCreateIp(configInfo.getSrcIp());
        configAllInfo.setDesc(configInfo.getCDesc());
        configAllInfo.setUse(configInfo.getCUse());
        configAllInfo.setEffect(configInfo.getEffect());
        configAllInfo.setType(configInfo.getType());
        configAllInfo.setSchema(configInfo.getCSchema());
        configAllInfo.setTenant(configInfo.getTenantId());
        configAllInfo.setAppName(configInfo.getAppName());
        configAllInfo.setType(configInfo.getType());
        configAllInfo.setDataId(configInfo.getDataId());
        configAllInfo.setGroup(configInfo.getGroupId());
        configAllInfo.setContent(configInfo.getContent());
        configAllInfo.setMd5(configInfo.getMd5());
        return configAllInfo;
    }

    private Map<String, Object> buildMap() {
        Map<String, Object> params = new HashMap<>();
        params.put("desc", "描述");
        params.put("use", "lisi");
        params.put("effect", "biaoji");
        params.put("type", "testType");
        params.put("schema", "ahahaha");
        return params;
    }

    private ConfigInfo buildConfigInfo() {
        ConfigInfo configInfo = new ConfigInfo();
        configInfo.setDataId("userService1");
        configInfo.setGroupId("DEFAULT_GROUP1");
        configInfo.setContent("logEnable=true1");
        configInfo.setMd5(MD5.getInstance().getMD5String("logEnable=true"));
        configInfo.setGmtCreate(new Date());
        configInfo.setGmtModified(new Date());
        configInfo.setSrcUser("zhangsan");
        configInfo.setSrcIp("127.0.0.1");
        configInfo.setAppName("userService1");
        configInfo.setTenantId("zhangsan");
        configInfo.setCDesc("用户系统");
        configInfo.setCUse("");
        configInfo.setEffect("");
        configInfo.setType("properties");
        configInfo.setCSchema("");
        JSON.toJSONString(configInfo);
        return configInfo;
    }

}
