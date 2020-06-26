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
package com.alibaba.nacos.console.service;

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.config.server.model.ConfigAdvanceInfo;
import com.alibaba.nacos.config.server.model.ConfigAllInfo;
import com.alibaba.nacos.config.server.model.ConfigInfoBase;
import com.alibaba.nacos.config.server.model.SameConfigPolicy;
import com.alibaba.nacos.config.server.modules.entity.*;
import com.alibaba.nacos.config.server.modules.repository.*;
import com.alibaba.nacos.config.server.service.PersistServiceTmp;
import com.alibaba.nacos.console.BaseTest;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.util.CollectionUtils;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

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

    @Autowired
    private TenantInfoRepository tenantInfoRepository;

    @Autowired
    private ConfigInfoRepository configInfoRepository;

    @Autowired
    private ConfigInfoAggrRepository configInfoAggrRepository;

    @Autowired
    private HisConfigInfoRepository hisConfigInfoRepository;

    private ConfigInfo configInfo;

    private ConfigInfoTag configInfoTag;

    private ConfigInfoBeta configInfoBeta;

    private ConfigTagsRelation configTagsRelation;

    private HisConfigInfo hisConfigInfo;

    private TenantInfo tenantInfo;

    private ConfigInfoAggr configInfoAggr;

    @Before
    public void before() {
        initData();
        insertOrUpdateTest();
    }

    private void initData() {
        configInfo = JacksonUtils.toObj(TestData.CONFIG_INFO_JSON, ConfigInfo.class);
        configInfoTag = JacksonUtils.toObj(TestData.CONFIG_INFO_TAG_JSON, ConfigInfoTag.class);
        configInfoBeta = JacksonUtils.toObj(TestData.CONFIG_INFO_BETA_JSON, ConfigInfoBeta.class);
        configTagsRelation = JacksonUtils.toObj(TestData.CONFIG_TAGS_RELATION_JSON, ConfigTagsRelation.class);
        hisConfigInfo = JacksonUtils.toObj(TestData.HIS_CONFIG_INFO_JSON, HisConfigInfo.class);
        tenantInfo = JacksonUtils.toObj(TestData.TENANT_INFO_JSON, TenantInfo.class);
        configInfoAggr = JacksonUtils.toObj(TestData.CONFIG_INFO_AGGR_JSON, ConfigInfoAggr.class);
    }


    @Test
    public void findConfigInfoBaseTest() {
        QConfigInfoBeta qConfigInfoBeta = QConfigInfoBeta.configInfoBeta;
        ConfigInfoBeta exist = configInfoBetaRepository.findOne(qConfigInfoBeta.dataId.eq(configInfoBeta.getDataId())
            .and(qConfigInfoBeta.groupId.eq(configInfoBeta.getGroupId())))
            .orElse(null);
        if (exist == null) {
            configInfoBetaRepository.save(configInfoBeta);
        }

        ConfigInfoBase configInfoBase = persistService.findConfigInfoBase(configInfoBeta.getDataId(), configInfoBeta.getGroupId());
        Assert.assertNotNull(configInfoBase);
        Assert.assertEquals(configInfoBase.getDataId(), configInfoBeta.getDataId());
        Assert.assertEquals(configInfoBase.getGroup(), configInfoBeta.getGroupId());
    }

    @Test
    public void removeConfigInfoByIdsTest() {
        QConfigInfo qConfigInfo = QConfigInfo.configInfo;
        List<Long> idList = ((List<ConfigInfo>) configInfoRepository.findAll(qConfigInfo.srcIp.eq(configInfo.getSrcIp())
            .and(qConfigInfo.srcUser.eq(configInfo.getSrcUser())))).stream().map(s -> s.getId()).collect(Collectors.toList());
        if (CollectionUtils.isEmpty(idList)) {
            configInfoRepository.save(configInfo);
            idList.add(configInfo.getId());
        }

        List<ConfigInfo> list = persistService.removeConfigInfoByIds(idList, configInfo.getSrcIp(), configInfo.getSrcUser());
        Assert.assertNotNull(list);
        Assert.assertTrue(list.size() > 0);
    }

    //    @Test
    public void insertOrUpdateTest() {

        QConfigInfo qConfigInfo = QConfigInfo.configInfo;
        configInfoRepository.findOne(qConfigInfo.dataId.eq(configInfo.getDataId())
            .and(qConfigInfo.groupId.eq(configInfo.getGroupId()))
            .and(qConfigInfo.tenantId.eq(configInfo.getTenantId())))
            .ifPresent(s -> configInfoRepository.delete(s));

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
        ConfigInfoBeta result = configInfoBetaRepository.findOne(qConfigInfoBeta.dataId.eq(configInfoBeta.getDataId())
            .and(qConfigInfoBeta.groupId.eq(configInfoBeta.getGroupId()))
            .and(qConfigInfoBeta.tenantId.eq(configInfoBeta.getTenantId())))
            .orElse(null);
        if (result == null) {
            result = new ConfigInfoBeta();
            configInfoBetaRepository.save(configInfoBeta);
            BeanUtils.copyProperties(result, configInfoBeta);
        }
        result.setGmtModified(new Date());
        persistService.updateConfigInfo4Beta(configInfo, result, configInfo.getSrcIp(),
            configInfo.getSrcUser(), Timestamp.from(Instant.now()), true);
    }

    @Test
    public void addConfigInfo4TagTest() {
        QConfigInfoTag qConfigInfoTag = QConfigInfoTag.configInfoTag;
        ConfigInfoTag result = configInfoTagRepository.findOne(qConfigInfoTag.dataId.eq(configInfoTag.getDataId())
            .and(qConfigInfoTag.groupId.eq(configInfoTag.getGroupId()))
            .and(qConfigInfoTag.tenantId.eq(configInfoTag.getTenantId()))
            .and(qConfigInfoTag.tagId.eq(configInfoTag.getTagId())))
            .orElse(null);
        if (result != null) {
            configInfoTagRepository.delete(result);
        }
        persistService.addConfigInfo4Tag(configInfo, configInfoTag.getTagId(), configInfoTag.getSrcIp(),
            configInfoTag.getSrcUser(), Timestamp.from(Instant.now()), true);
    }

    @Test
    public void addConfigInfo4BetaTest() {

        QConfigInfoBeta qConfigInfoBeta = QConfigInfoBeta.configInfoBeta;
        ConfigInfoBeta result = configInfoBetaRepository.findOne(qConfigInfoBeta.dataId.eq(configInfoBeta.getDataId())
            .and(qConfigInfoBeta.groupId.eq(configInfoBeta.getGroupId()))
            .and(qConfigInfoBeta.tenantId.eq(configInfoBeta.getTenantId())))
            .orElse(null);
        if (result != null) {
            configInfoBetaRepository.delete(result);
        }
        persistService.addConfigInfo4Beta(configInfo, configInfoBeta.getBetaIps(), configInfoBeta.getSrcIp(),
            configInfoBeta.getSrcUser(), Timestamp.from(Instant.now()), true);
    }

    @Test
    public void updateConfigInfo4Tag() {
        QConfigInfoTag qConfigInfoTag = QConfigInfoTag.configInfoTag;
        ConfigInfoTag result = configInfoTagRepository.findOne(qConfigInfoTag.dataId.eq(configInfoTag.getDataId())
            .and(qConfigInfoTag.groupId.eq(configInfoTag.getGroupId()))
            .and(qConfigInfoTag.tenantId.eq(configInfoTag.getTenantId()))
            .and(qConfigInfoTag.tagId.eq(configInfoTag.getTagId())))
            .orElse(null);
        if (result == null) {
            result = new ConfigInfoTag();
            configInfoTagRepository.save(configInfoTag);
            BeanUtils.copyProperties(result, configInfoTag);
        }
        result.setGmtModified(new Date());
        persistService.updateConfigInfo4Tag(configInfo, result, configInfoTag.getTagId(),
            configInfoTag.getSrcIp(), configInfoTag.getSrcUser(), Timestamp.from(Instant.now()), true);
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
        ConfigInfoBeta result = persistService.findConfigInfo4Beta(configInfoBeta.getDataId(),
            configInfoBeta.getGroupId(), configInfoBeta.getTenantId());
        Assert.assertNotNull(result);
    }


    @Test
    public void findConfigInfo4TagTest() {
        QConfigInfoTag qConfigInfoTag = QConfigInfoTag.configInfoTag;
        ConfigInfoTag result = configInfoTagRepository.findOne(qConfigInfoTag.dataId.eq(configInfoTag.getDataId())
            .and(qConfigInfoTag.groupId.eq(configInfoTag.getGroupId()))
            .and(qConfigInfoTag.tenantId.eq(configInfoTag.getTenantId()))
            .and(qConfigInfoTag.tagId.eq(configInfoTag.getTagId())))
            .orElse(null);
        if (result == null) {
            configInfoTagRepository.save(configInfoTag);
        }

        ConfigInfoTag getConfigInfoTag = persistService.findConfigInfo4Tag(configInfoTag.getDataId(), configInfoTag.getGroupId(),
            configInfoTag.getTenantId(), configInfoTag.getTagId());
        Assert.assertNotNull(getConfigInfoTag);
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
        List<Long> idList = new ArrayList<>();
        QConfigInfo qConfigInfo = QConfigInfo.configInfo;
        List<ConfigInfo> result = (List<ConfigInfo>) configInfoRepository.findAll(qConfigInfo.dataId.eq(configInfo.getDataId())
            .and(qConfigInfo.groupId.eq(configInfo.getGroupId()))
            .and(qConfigInfo.tenantId.eq(configInfo.getTenantId())));
        if (CollectionUtils.isEmpty(result)) {
            configInfoRepository.save(configInfo);
            idList.add(configInfo.getId());
        } else {
            idList.add(result.get(0).getId());
        }
        Iterable<ConfigInfo> iterable = persistService.findConfigInfosByIds(idList);
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
        List<TenantInfo> getTenantInfo = (List<TenantInfo>) tenantInfoRepository.findAll(QTenantInfo.tenantInfo.tenantId.eq(tenantInfo.getTenantId()));
        if (CollectionUtils.isEmpty(getTenantInfo)) {
            tenantInfoRepository.save(tenantInfo);
        }
        int countResult = persistService.tenantInfoCountByTenantId(tenantInfo.getTenantId());
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
        QConfigInfo qConfigInfo = QConfigInfo.configInfo;
        ConfigInfo result = configInfoRepository.findOne(qConfigInfo.dataId.eq(configInfo.getDataId())
            .and(qConfigInfo.groupId.eq(configInfo.getGroupId()))
            .and(qConfigInfo.tenantId.eq(configInfo.getTenantId())))
            .orElse(null);
        if (result != null) {
            configInfoRepository.delete(result);
        }

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

        Long id;
        List<HisConfigInfo> list = (List<HisConfigInfo>) hisConfigInfoRepository.findAll();
        if (CollectionUtils.isEmpty(list)) {
            hisConfigInfoRepository.save(hisConfigInfo);
            id = hisConfigInfo.getNid();
        } else {
            id = list.get(0).getNid();
        }
        HisConfigInfo hisConfigInfo = persistService
            .detailConfigHistory(id);
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
            tenantInfo.getTenantName() + LocalDateTime.now().toString(), tenantInfo.getTenantDesc() + LocalDateTime.now().toString());
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
        int result = persistService.configInfoCount();
        Assert.assertTrue(result > 0);
    }

    @Test
    public void configInfoCount2Test() {
        int result = persistService.configInfoCount(configInfo.getTenantId());
        Assert.assertTrue(result > 0);
    }

    @Test
    public void configInfoBetaCountTest() {
        int result = persistService.configInfoBetaCount();
        Assert.assertTrue(result > 0);
    }

    @Test
    public void configInfoTagCountTest() {
        int result = persistService.configInfoTagCount();
        Assert.assertTrue(result > 0);
    }

    @Test
    public void getGroupIdListTest() {
        List<String> list = persistService.getGroupIdList(0, 10);
        Assert.assertNotNull(list);
        Assert.assertTrue(list.size() > 0);
    }

    @Test
    public void getTenantIdListTest() {
        List<String> list = persistService.getTenantIdList(0, 10);
        Assert.assertNotNull(list);
        Assert.assertTrue(list.size() > 0);
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
        Page<ConfigInfo> page = persistService.findAllConfigInfoFragment(0, 100);
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
        List<ConfigInfoAggr> result = ((List<ConfigInfoAggr>) configInfoAggrRepository.findAll());
        if (result.isEmpty()) {
            configInfoAggrRepository.save(configInfoAggr);
        }

        List<ConfigInfoAggr> list = persistService.findAllAggrGroup();
        Assert.assertNotNull(list);
        Assert.assertTrue(list.size() > 0);
    }

    //
    @Test
    public void findChangeConfigTest() {

        Timestamp startTime;
        Timestamp endTime;
        List<ConfigInfo> result = (List<ConfigInfo>) configInfoRepository.findAll();
        if (CollectionUtils.isEmpty(result)) {
            configInfoRepository.save(configInfo);
            try {
                Thread.sleep(5000);
            } catch (InterruptedException e) {
            }
            startTime = new Timestamp(configInfo.getGmtModified().getTime());
            endTime = new Timestamp(configInfo.getGmtModified().getTime());
        } else {
            startTime = new Timestamp(result.get(0).getGmtModified().getTime());
            endTime = new Timestamp(result.get(0).getGmtModified().getTime());
        }

        List<ConfigInfo> list = persistService.findChangeConfig(startTime, endTime);
        Assert.assertNotNull(list);
        Assert.assertTrue(list.size() > 0);
    }

    //
    @Test
    public void findDeletedConfigTest() {
        List<HisConfigInfo> list = persistService.findDeletedConfig(Timestamp.valueOf(LocalDateTime.now().minusDays(30)), Timestamp.from(Instant.now()));
        Assert.assertNotNull(list);
        Assert.assertTrue(list.size() > 0);
    }
}
