package com.alibaba.nacos.console.service;

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.config.server.model.ConfigAdvanceInfo;
import com.alibaba.nacos.config.server.model.ConfigAllInfo;
import com.alibaba.nacos.config.server.model.ConfigInfoBase;
import com.alibaba.nacos.config.server.model.SameConfigPolicy;
import com.alibaba.nacos.config.server.modules.entity.*;
import com.alibaba.nacos.config.server.modules.repository.ConfigInfoBetaRepository;
import com.alibaba.nacos.config.server.modules.repository.ConfigInfoTagRepository;
import com.alibaba.nacos.config.server.modules.repository.ConfigTagsRelationRepository;
import com.alibaba.nacos.config.server.service.PersistServiceTmp;
import com.alibaba.nacos.console.BaseTest;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.test.context.junit4.SpringRunner;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;

@RunWith(SpringRunner.class)
@SpringBootTest
public class PersistServiceTest extends BaseTest {

    @Autowired
    private PersistServiceTmp persistService;

    @Autowired
    private ConfigInfoBetaRepository configInfoBetaRepository;

    @Autowired
    private ConfigInfoTagRepository configInfoTagRepository;

    @Autowired
    private ConfigTagsRelationRepository configTagsRelationRepository;

    private ConfigInfo configInfo;

    private ConfigInfoTag configInfoTag;

    private ConfigInfoBeta configInfoBeta;

    private ConfigTagsRelation configTagsRelation;

    private HisConfigInfo hisConfigInfo;

    private TenantInfo tenantInfo;

    @Before
    public void before() {
        String configInfoStr = readClassPath("test-data/config_info.json");
        configInfo = JacksonUtils.toObj(configInfoStr, ConfigInfo.class);
        String configInfoTagStr = readClassPath("test-data/config_info_tag.json");
        configInfoTag = JacksonUtils.toObj(configInfoTagStr, ConfigInfoTag.class);
        String configInfoBetaStr = readClassPath("test-data/config_info_beta.json");
        configInfoBeta = JacksonUtils.toObj(configInfoBetaStr, ConfigInfoBeta.class);
        String configTagsRelationStr = readClassPath("test-data/config_tags_relation.json");
        configTagsRelation = JacksonUtils.toObj(configTagsRelationStr, ConfigTagsRelation.class);
        String hisConfigInfoStr = readClassPath("test-data/his_config_info.json");
        hisConfigInfo = JacksonUtils.toObj(hisConfigInfoStr, HisConfigInfo.class);
        String tenantInfoStr = readClassPath("test-data/tenant_info.json");
        tenantInfo = JacksonUtils.toObj(tenantInfoStr, TenantInfo.class);
    }


    @Test
    public void findConfigInfoBaseTest() {
        ConfigInfoBase configInfoBase = persistService.findConfigInfoBase(configInfo.getDataId(), configInfo.getGroupId());
        Assert.assertNotNull(configInfoBase);
        Assert.assertEquals(configInfoBase.getDataId(), configInfo.getDataId());
        Assert.assertEquals(configInfoBase.getGroup(), configInfo.getGroupId());
    }

    @Test
    public void removeConfigInfoByIdsTest() {
        List<ConfigInfo> list = persistService.removeConfigInfoByIds(Arrays.asList(4l), configInfo.getSrcIp(), configInfo.getSrcUser());
        Assert.assertNotNull(list);
        Assert.assertTrue(list.size() > 0);
    }

    @Test
    public void insertOrUpdateTest() {
        persistService.insertOrUpdate(configInfo.getSrcIp(), configInfo.getSrcUser(),
            configInfo, Timestamp.from(Instant.now()), buildInsertOrUpdateMap(), true);
    }

    private Map<String, Object> buildInsertOrUpdateMap() {
        Map<String, Object> params = new HashMap<>();
        params.put("desc", configInfo.getCDesc());
        params.put("use", configInfo.getCUse());
        params.put("effect", configInfo.getEffect());
        params.put("type", configInfo.getType());
        params.put("schema", configInfo.getCSchema());
        params.put("config_tags", "tagA");
        return params;
    }

    @Test
    public void insertOrUpdateTagTest() {
        persistService.insertOrUpdateTag(configInfo, "tagB", configInfo.getSrcIp(),
            configInfo.getSrcUser(), Timestamp.from(Instant.now()), true);
    }

    @Test
    public void insertOrUpdateBetaTest() {
        persistService.insertOrUpdateBeta(configInfo, "127.0.0.1", configInfo.getSrcIp(),
            configInfo.getSrcUser(), Timestamp.from(Instant.now()), true);
    }

    @Test
    public void updateConfigInfo4Beta() {
        QConfigInfoBeta qConfigInfoBeta = QConfigInfoBeta.configInfoBeta;
        ConfigInfoBeta configInfoBase = configInfoBetaRepository.findOne(qConfigInfoBeta.dataId.eq(configInfo.getDataId())
            .and(qConfigInfoBeta.groupId.eq(configInfo.getGroupId()))
            .and(qConfigInfoBeta.tenantId.eq(configInfo.getTenantId())))
            .orElse(null);
        configInfoBase.setGmtModified(new Date());
        persistService.updateConfigInfo4Beta(configInfo, configInfoBase, configInfo.getSrcIp(),
            configInfo.getSrcUser(), Timestamp.from(Instant.now()), true);
    }

    @Test
    public void addConfigInfo4TagTest() {
        persistService.addConfigInfo4Tag(configInfo, "tagA", configInfo.getSrcIp(),
            configInfo.getSrcUser(), Timestamp.from(Instant.now()), true);
    }

    @Test
    public void addConfigInfo4BetaTest() {
        persistService.addConfigInfo4Beta(configInfo, "127.0.0.1", configInfo.getSrcIp(),
            configInfo.getSrcUser(), Timestamp.from(Instant.now()), true);
    }

    @Test
    public void updateConfigInfo4Tag() {
        QConfigInfoTag qConfigInfoTag = QConfigInfoTag.configInfoTag;
        ConfigInfoTag configInfoTag = configInfoTagRepository.findOne(qConfigInfoTag.dataId.eq(configInfo.getDataId())
            .and(qConfigInfoTag.groupId.eq(configInfo.getGroupId()))
            .and(qConfigInfoTag.tenantId.eq(configInfo.getTenantId()))
            .and(qConfigInfoTag.tagId.eq("tagA"))).orElse(null);
        configInfoTag.setGmtModified(new Date());
        persistService.updateConfigInfo4Tag(configInfo, configInfoTag, "tagB",
            configInfo.getSrcIp(), configInfo.getSrcUser(), Timestamp.from(Instant.now()), true);
    }


    @Test
    public void findConfigInfo4PageTest() {
        Page<ConfigInfo> page = persistService.findConfigInfo4Page(0, 10,
            configInfo.getDataId(), configInfo.getGroupId(), configInfo.getTenantId(), configInfo.getAppName());
        Assert.assertTrue(page.getContent().size() > 0);
    }

    @Test
    public void findConfigInfoLike4PageTest() {
        Page<ConfigInfo> page = persistService.findConfigInfoLike4Page(0, 10,
            configInfo.getDataId(), configInfo.getGroupId(), configInfo.getTenantId(), configInfo.getAppName());
        Assert.assertTrue(page.getContent().size() > 0);
    }

    @Test
    public void findConfigInfo4BetaTest() {
        ConfigInfoBeta configInfoBeta = persistService.findConfigInfo4Beta(configInfo.getDataId(),
            configInfo.getGroupId(), configInfo.getTenantId());
        Assert.assertNotNull(configInfoBeta);
    }


    @Test
    public void findConfigInfo4TagTest() {
        ConfigInfoTag configInfoTag = persistService.findConfigInfo4Tag(configInfo.getDataId(), configInfo.getGroupId(),
            configInfo.getTenantId(), "tagB");
        Assert.assertNotNull(configInfoTag);
    }

    @Test
    public void findConfigInfoTest() {
        ConfigInfo findConfigInfo = persistService.
            findConfigInfo(configInfo.getDataId(), configInfo.getGroupId(), configInfo.getTenantId());
        Assert.assertNotNull(findConfigInfo);
    }

    @Test
    public void findConfigAllInfoTest() {
        ConfigAllInfo configAllInfo = persistService.findConfigAllInfo(configInfo.getDataId(),
            configInfo.getGroupId(), configInfo.getTenantId());
        Assert.assertNotNull(configAllInfo);
    }

    @Test
    public void selectTagByConfigTest() {
        List<String> list = persistService.
            selectTagByConfig(configInfo.getDataId(), configInfo.getGroupId(), configInfo.getTenantId());

        Assert.assertTrue(list.size() > 0);
    }


    @Test
    public void remoteTagByIdAtomicTest() {
        Iterable<ConfigTagsRelation> list = configTagsRelationRepository.findAll();
        persistService.removeTagByIdAtomic(list.iterator().next().getNid());
    }

    @Test
    public void removeConfigInfoTest() {
        persistService.removeConfigInfo(configInfo.getDataId(), configInfo.getGroupId(),
            configInfo.getTenantId(), configInfo.getSrcIp(), configInfo.getSrcUser());
    }


    @Test
    public void removeConfigInfoTagTest() {
        persistService.removeConfigInfoTag(configInfoTag.getDataId(), configInfoTag.getGroupId(), configInfoTag.getTenantId(), configInfoTag.getTagId(),
            configInfoTag.getSrcIp(), configInfoTag.getSrcUser());
    }

    @Test
    public void findConfigInfosByIdsTest() {
        Iterable<ConfigInfo> iterable = persistService.findConfigInfosByIds(Arrays.asList(6l));
        Assert.assertTrue(((ArrayList) iterable).size() > 0);
    }

    @Test
    public void findConfigAdvanceInfoTest() {
        ConfigAdvanceInfo configAdvanceInfo = persistService.findConfigAdvanceInfo(configInfo.getDataId(), configInfo.getGroupId(), configInfo.getTenantId());
        Assert.assertNotNull(configAdvanceInfo);
    }


    @Test
    public void removeConfigInfo4BetaTest() {
        persistService.removeConfigInfo4Beta(configInfoBeta.getDataId(), configInfoBeta.getGroupId(), configInfoBeta.getTenantId());
    }

    @Test
    public void findAllConfigInfo4ExportTest() {
        List<ConfigAllInfo> list = persistService.findAllConfigInfo4Export(configInfo.getDataId(), configInfo.getGroupId(),
            configInfo.getTenantId(), configInfo.getAppName(), null);
        Assert.assertTrue(list.size() > 0);
    }


    @Test
    public void tenantInfoCountByTenantIdTest() {
        int countResult = persistService.tenantInfoCountByTenantId("test");
        Assert.assertTrue(countResult > 0);
    }

    @Test
    public void addConfigInfoTest() {
        persistService.addConfigInfo(configInfo.getSrcIp(), configInfo.getSrcUser(),
            configInfo, Timestamp.from(Instant.now()), buildMap(), false);
    }

    @Test
    public void addConfiTagsRelationAtomicTest() {
        persistService.addConfigTagsRelationAtomic(configTagsRelation.getId(), configTagsRelation.getTagName(),
            configTagsRelation.getDataId(), configTagsRelation.getGroupId(), configTagsRelation.getTenantId());
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
        Page<HisConfigInfo> page = persistService.findConfigHistory(hisConfigInfo.getDataId(), hisConfigInfo.getGroupId(),
            hisConfigInfo.getTenantId(), 0, 10);
        Assert.assertTrue(page.getContent().size() > 0);
    }

    @Test
    public void detailConfigHistoryTest() {
        HisConfigInfo hisConfigInfo = persistService
            .detailConfigHistory(4l);
        Assert.assertNotNull(hisConfigInfo);
    }

    private ConfigAllInfo buildConfigAllInfo() {
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
        params.put("type", "text");
        params.put("schema", "test");
        return params;
    }


    //
    @Test
    public void insertTenantInfoAtomicTest() {
        persistService.insertTenantInfoAtomic(tenantInfo.getKp(), tenantInfo.getTenantId(), tenantInfo.getTenantName(),
            tenantInfo.getTenantDesc(), tenantInfo.getCreateSource(), tenantInfo.getGmtCreate());
    }

    @Test
    public void updateTenantNameAtomicTest() {
        persistService.updateTenantNameAtomic(tenantInfo.getKp(), tenantInfo.getTenantId(),
            tenantInfo.getTenantName(), tenantInfo.getTenantDesc());
    }

    @Test
    public void findTenantByKp1Test() {
        List<TenantInfo> list = persistService.findTenantByKp(tenantInfo.getKp());
        Assert.assertNotNull(list);
        Assert.assertTrue(list.size() > 0);
    }


    @Test
    public void findTenantByKp2Test() {
        TenantInfo result = persistService.findTenantByKp(tenantInfo.getKp(), tenantInfo.getTenantId());
        Assert.assertNotNull(result);
    }


    @Test
    public void removeTenantInfoAtomicTest() {
        persistService.removeTenantInfoAtomic(tenantInfo.getKp(), tenantInfo.getTenantId());
    }

    @Test
    public void listAllGroupKeyMd5Test() {
        List<ConfigInfo> list = persistService.listAllGroupKeyMd5();
        Assert.assertTrue(list.size() > 0);
    }

    @Test
    public void queryConfigInfoTest() {
        ConfigInfo result = persistService.queryConfigInfo(configInfo.getDataId(), configInfo.getGroupId(), configInfo.getTenantId());
        Assert.assertNotNull(result);
    }

    @Test
    public void isConfigInfoBetaTest() {
        persistService.isConfigInfoTag();
    }

    @Test
    public void isConfigInfoTagTest() {
        persistService.isConfigInfoTag();
    }

    @Test
    public void configInfoCount1Test() {
        persistService.configInfoCount();
    }

    @Test
    public void configInfoCount2Test() {
        persistService.configInfoCount(configInfo.getTenantId());
    }

    @Test
    public void configInfoBetaCountTest() {
        persistService.configInfoBetaCount();
    }

    @Test
    public void configInfoTagCountTest() {
        persistService.configInfoTagCount();
    }

    @Test
    public void getGroupIdListTest() {
        persistService.getGroupIdList(0, 10);
    }

    @Test
    public void aggrConfigInfoCountTest() {
        int result = persistService.aggrConfigInfoCount(configInfo.getDataId(), configInfo.getGroupId(), configInfo.getTenantId());
        Assert.assertTrue(result > 0);
    }

    @Test
    public void removeConfigHistoryTest() {
        persistService.removeConfigHistory(Timestamp.from(Instant.now()), 100);
    }

    @Test
    public void findConfigHistoryCountByTimeTest() {
        int result = persistService.findConfigHistoryCountByTime(Timestamp.from(Instant.now()));
        Assert.assertTrue(result > 0);
    }

    @Test
    public void findConfigMaxIdTest() {
        long result = persistService.findConfigMaxId();
        Assert.assertTrue(result > 0);
    }

    @Test
    public void findAllConfigInfoFragmentTest() {
        Page<ConfigInfo> page = persistService.findAllConfigInfoFragment(100, 100);
        Assert.assertTrue(page.getContent().size() > 0);
    }

    @Test
    public void findAllConfigInfoBetaForDumpAllTest() {
        Page<ConfigInfoBeta> page = persistService.findAllConfigInfoBetaForDumpAll(0, 10);
        Assert.assertTrue(page.getContent().size() > 0);
    }

    @Test
    public void findAllConfigInfoTagForDumpAllTest() {
        Page<ConfigInfoTag> page = persistService.findAllConfigInfoTagForDumpAll(0, 10);
        Assert.assertTrue(page.getContent().size() > 0);
    }

    @Test
    public void findAllAggrGroupTest() {
        List<ConfigInfoAggr> list = persistService.findAllAggrGroup();
        Assert.assertNotNull(list);
        Assert.assertTrue(list.size() > 0);
    }

    @Test
    public void findChangeConfigTest() {
        List<ConfigInfo> list = persistService.findChangeConfig(Timestamp.from(Instant.now()), Timestamp.from(Instant.now()));
        Assert.assertNotNull(list);
        Assert.assertTrue(list.size() > 0);
    }

    @Test
    public void findDeletedConfigTest() {
        List<HisConfigInfo> list = persistService.findDeletedConfig(Timestamp.from(Instant.now()), Timestamp.from(Instant.now()));
        Assert.assertNotNull(list);
        Assert.assertTrue(list.size() > 0);
    }



}
