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
import com.alibaba.nacos.config.server.model.ConfigHistoryInfo;
import com.alibaba.nacos.config.server.model.ConfigInfo;
import com.alibaba.nacos.config.server.model.ConfigInfo4Beta;
import com.alibaba.nacos.config.server.model.ConfigInfo4Tag;
import com.alibaba.nacos.config.server.model.ConfigInfoBase;
import com.alibaba.nacos.config.server.model.ConfigInfoBetaWrapper;
import com.alibaba.nacos.config.server.model.ConfigInfoChanged;
import com.alibaba.nacos.config.server.model.ConfigInfoTagWrapper;
import com.alibaba.nacos.config.server.model.ConfigInfoWrapper;
import com.alibaba.nacos.config.server.model.Page;
import com.alibaba.nacos.config.server.model.SameConfigPolicy;
import com.alibaba.nacos.config.server.model.TenantInfo;
import com.alibaba.nacos.config.server.modules.entity.ConfigInfoAggrEntity;
import com.alibaba.nacos.config.server.modules.entity.ConfigInfoBetaEntity;
import com.alibaba.nacos.config.server.modules.entity.ConfigInfoEntity;
import com.alibaba.nacos.config.server.modules.entity.ConfigInfoTagEntity;
import com.alibaba.nacos.config.server.modules.entity.ConfigTagsRelationEntity;
import com.alibaba.nacos.config.server.modules.entity.HisConfigInfoEntity;
import com.alibaba.nacos.config.server.modules.entity.QConfigInfoBetaEntity;
import com.alibaba.nacos.config.server.modules.entity.QConfigInfoEntity;
import com.alibaba.nacos.config.server.modules.entity.QConfigInfoTagEntity;
import com.alibaba.nacos.config.server.modules.entity.QTenantInfoEntity;
import com.alibaba.nacos.config.server.modules.entity.TenantInfoEntity;
import com.alibaba.nacos.config.server.modules.mapstruct.ConfigInfoMapStruct;
import com.alibaba.nacos.config.server.modules.repository.ConfigInfoAggrRepository;
import com.alibaba.nacos.config.server.modules.repository.ConfigInfoBetaRepository;
import com.alibaba.nacos.config.server.modules.repository.ConfigInfoRepository;
import com.alibaba.nacos.config.server.modules.repository.ConfigInfoTagRepository;
import com.alibaba.nacos.config.server.modules.repository.ConfigTagsRelationRepository;
import com.alibaba.nacos.config.server.modules.repository.HisConfigInfoRepository;
import com.alibaba.nacos.config.server.modules.repository.TenantInfoRepository;
import com.alibaba.nacos.config.server.service.repository.PersistService;
import com.alibaba.nacos.console.BaseTest;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.util.CollectionUtils;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RunWith(SpringRunner.class)
@SpringBootTest
public class PersistServiceTest extends BaseTest {
    
    @Autowired
    private PersistService persistService;
    
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
    
    private ConfigInfoEntity configInfoEntity;
    
    private ConfigInfoTagEntity configInfoTagEntity;
    
    private ConfigInfoBetaEntity configInfoBetaEntity;
    
    private ConfigTagsRelationEntity configTagsRelationEntity;
    
    private HisConfigInfoEntity hisConfigInfoEntity;
    
    private TenantInfoEntity tenantInfoEntity;
    
    private ConfigInfoAggrEntity configInfoAggrEntity;
    
    private ConfigInfo configInfo;
    
    @Before
    public void before() {
        initData();
        insertOrUpdateTest();
    }
    
    private void initData() {
        configInfoEntity = JacksonUtils.toObj(TestData.CONFIG_INFO_JSON, ConfigInfoEntity.class);
        configInfoTagEntity = JacksonUtils.toObj(TestData.CONFIG_INFO_TAG_JSON, ConfigInfoTagEntity.class);
        configInfoBetaEntity = JacksonUtils.toObj(TestData.CONFIG_INFO_BETA_JSON, ConfigInfoBetaEntity.class);
        configTagsRelationEntity = JacksonUtils
                .toObj(TestData.CONFIG_TAGS_RELATION_JSON, ConfigTagsRelationEntity.class);
        hisConfigInfoEntity = JacksonUtils.toObj(TestData.HIS_CONFIG_INFO_JSON, HisConfigInfoEntity.class);
        tenantInfoEntity = JacksonUtils.toObj(TestData.TENANT_INFO_JSON, TenantInfoEntity.class);
        configInfoAggrEntity = JacksonUtils.toObj(TestData.CONFIG_INFO_AGGR_JSON, ConfigInfoAggrEntity.class);
        
        configInfo = ConfigInfoMapStruct.INSTANCE.convertConfigInfo(configInfoEntity);
        
    }
    
    @Test
    public void findConfigInfoBaseTest() {
        QConfigInfoBetaEntity qConfigInfoBeta = QConfigInfoBetaEntity.configInfoBetaEntity;
        ConfigInfoBetaEntity exist = configInfoBetaRepository.findOne(
                qConfigInfoBeta.dataId.eq(configInfoBetaEntity.getDataId())
                        .and(qConfigInfoBeta.groupId.eq(configInfoBetaEntity.getGroupId()))).orElse(null);
        if (exist == null) {
            configInfoBetaRepository.save(configInfoBetaEntity);
        }
        ConfigInfoBase configInfoBase = persistService
                .findConfigInfoBase(configInfoBetaEntity.getDataId(), configInfoBetaEntity.getGroupId());
        Assert.assertNotNull(configInfoBase);
        Assert.assertEquals(configInfoBase.getDataId(), configInfoBetaEntity.getDataId());
        Assert.assertEquals(configInfoBase.getGroup(), configInfoBetaEntity.getGroupId());
    }
    
    @Test
    public void removeConfigInfoByIdsTest() {
        QConfigInfoEntity qConfigInfo = QConfigInfoEntity.configInfoEntity;
        List<Long> idList = ((List<ConfigInfoEntity>) configInfoRepository.findAll(
                qConfigInfo.srcIp.eq(configInfoEntity.getSrcIp())
                        .and(qConfigInfo.srcUser.eq(configInfoEntity.getSrcUser())))).stream().map(s -> s.getId())
                .collect(Collectors.toList());
        if (CollectionUtils.isEmpty(idList)) {
            configInfoRepository.save(configInfoEntity);
            idList.add(configInfoEntity.getId());
        }
        
        List<ConfigInfo> list = persistService
                .removeConfigInfoByIds(idList, configInfoEntity.getSrcIp(), configInfoEntity.getSrcUser());
        Assert.assertNotNull(list);
        Assert.assertTrue(list.size() > 0);
    }
    
    @Test
    public void insertOrUpdateTest() {
        
        QConfigInfoEntity qConfigInfo = QConfigInfoEntity.configInfoEntity;
        configInfoRepository.findOne(qConfigInfo.dataId.eq(configInfoEntity.getDataId())
                .and(qConfigInfo.groupId.eq(configInfoEntity.getGroupId()))
                .and(qConfigInfo.tenantId.eq(configInfoEntity.getTenantId())))
                .ifPresent(s -> configInfoRepository.delete(s));
        
        persistService.insertOrUpdate(configInfoEntity.getSrcIp(), configInfoEntity.getSrcUser(), configInfo,
                Timestamp.from(Instant.now()), buildInsertOrUpdateMap(), true);
    }
    
    private Map<String, Object> buildInsertOrUpdateMap() {
        Map<String, Object> params = new HashMap<>();
        params.put("desc", configInfoEntity.getCDesc());
        params.put("use", configInfoEntity.getCUse());
        params.put("effect", configInfoEntity.getEffect());
        params.put("type", configInfoEntity.getType());
        params.put("schema", configInfoEntity.getCSchema());
        params.put("config_tags", "tagA");
        return params;
    }
    
    @Test
    public void insertOrUpdateTagTest() {
        persistService.insertOrUpdateTag(configInfo, "tagB", configInfoEntity.getSrcIp(), configInfoEntity.getSrcUser(),
                Timestamp.from(Instant.now()), true);
    }
    
    @Test
    public void insertOrUpdateBetaTest() {
        persistService
                .insertOrUpdateBeta(configInfo, "127.0.0.1", configInfoEntity.getSrcIp(), configInfoEntity.getSrcUser(),
                        Timestamp.from(Instant.now()), true);
    }
    
    @Test
    public void updateConfigInfo4Beta() {
        QConfigInfoBetaEntity qConfigInfoBeta = QConfigInfoBetaEntity.configInfoBetaEntity;
        ConfigInfoBetaEntity result = configInfoBetaRepository.findOne(
                qConfigInfoBeta.dataId.eq(configInfoBetaEntity.getDataId())
                        .and(qConfigInfoBeta.groupId.eq(configInfoBetaEntity.getGroupId()))
                        .and(qConfigInfoBeta.tenantId.eq(configInfoBetaEntity.getTenantId()))).orElse(null);
        if (result == null) {
            result = new ConfigInfoBetaEntity();
            configInfoBetaRepository.save(configInfoBetaEntity);
            BeanUtils.copyProperties(result, configInfoBetaEntity);
        }
        result.setGmtModified(new Date());
        persistService
                .updateConfigInfo4Beta(configInfo, null, configInfoEntity.getSrcIp(), configInfoEntity.getSrcUser(),
                        Timestamp.from(Instant.now()), true);
    }
    
    @Test
    public void addConfigInfo4TagTest() {
        QConfigInfoTagEntity qConfigInfoTag = QConfigInfoTagEntity.configInfoTagEntity;
        ConfigInfoTagEntity result = configInfoTagRepository.findOne(
                qConfigInfoTag.dataId.eq(configInfoTagEntity.getDataId())
                        .and(qConfigInfoTag.groupId.eq(configInfoTagEntity.getGroupId()))
                        .and(qConfigInfoTag.tenantId.eq(configInfoTagEntity.getTenantId()))
                        .and(qConfigInfoTag.tagId.eq(configInfoTagEntity.getTagId()))).orElse(null);
        if (result != null) {
            configInfoTagRepository.delete(result);
        }
        persistService.addConfigInfo4Tag(configInfo, configInfoTagEntity.getTagId(), configInfoTagEntity.getSrcIp(),
                configInfoTagEntity.getSrcUser(), Timestamp.from(Instant.now()), true);
    }
    
    @Test
    public void addConfigInfo4BetaTest() {
        
        QConfigInfoBetaEntity qConfigInfoBeta = QConfigInfoBetaEntity.configInfoBetaEntity;
        ConfigInfoBetaEntity result = configInfoBetaRepository.findOne(
                qConfigInfoBeta.dataId.eq(configInfoBetaEntity.getDataId())
                        .and(qConfigInfoBeta.groupId.eq(configInfoBetaEntity.getGroupId()))
                        .and(qConfigInfoBeta.tenantId.eq(configInfoBetaEntity.getTenantId()))).orElse(null);
        if (result != null) {
            configInfoBetaRepository.delete(result);
        }
        persistService
                .addConfigInfo4Beta(configInfo, configInfoBetaEntity.getBetaIps(), configInfoBetaEntity.getSrcIp(),
                        configInfoBetaEntity.getSrcUser(), Timestamp.from(Instant.now()), true);
    }
    
    @Test
    public void updateConfigInfo4Tag() {
        QConfigInfoTagEntity qConfigInfoTag = QConfigInfoTagEntity.configInfoTagEntity;
        ConfigInfoTagEntity result = configInfoTagRepository.findOne(
                qConfigInfoTag.dataId.eq(configInfoTagEntity.getDataId())
                        .and(qConfigInfoTag.groupId.eq(configInfoTagEntity.getGroupId()))
                        .and(qConfigInfoTag.tenantId.eq(configInfoTagEntity.getTenantId()))
                        .and(qConfigInfoTag.tagId.eq(configInfoTagEntity.getTagId()))).orElse(null);
        if (result == null) {
            result = new ConfigInfoTagEntity();
            configInfoTagRepository.save(configInfoTagEntity);
            BeanUtils.copyProperties(result, configInfoTagEntity);
        }
        result.setGmtModified(new Date());
        persistService.updateConfigInfo4Tag(configInfo, configInfoTagEntity.getTagId(), configInfoTagEntity.getSrcIp(),
                configInfoTagEntity.getSrcUser(), Timestamp.from(Instant.now()), true);
    }
    
    @Test
    public void findConfigInfo4PageTest() {
        Map<String, Object> map = new HashMap<>();
        map.put("appName", "appName");
        map.put("config_tags", "config_tags");
        Page<ConfigInfo> page = persistService
                .findConfigInfo4Page(0, 10, configInfoEntity.getDataId(), configInfoEntity.getGroupId(),
                        configInfoEntity.getTenantId(), map);
        Assert.assertTrue(page.getPageItems().size() > 0);
    }
    
    @Test
    public void findConfigInfoLike4PageTest() {
        Map<String, Object> map = new HashMap<>();
        map.put("appName", "appName");
        map.put("config_tags", "config_tags");
        Page<ConfigInfo> page = persistService
                .findConfigInfoLike4Page(0, 10, configInfoEntity.getDataId(), configInfoEntity.getGroupId(),
                        configInfoEntity.getTenantId(), map);
        Assert.assertTrue(page.getPageItems().size() > 0);
    }
    
    @Test
    public void findConfigInfo4BetaTest() {
        ConfigInfo4Beta result = persistService
                .findConfigInfo4Beta(configInfoBetaEntity.getDataId(), configInfoBetaEntity.getGroupId(),
                        configInfoBetaEntity.getTenantId());
        Assert.assertNotNull(result);
    }
    
    @Test
    public void findConfigInfo4TagTest() {
        QConfigInfoTagEntity qConfigInfoTag = QConfigInfoTagEntity.configInfoTagEntity;
        ConfigInfoTagEntity result = configInfoTagRepository.findOne(
                qConfigInfoTag.dataId.eq(configInfoTagEntity.getDataId())
                        .and(qConfigInfoTag.groupId.eq(configInfoTagEntity.getGroupId()))
                        .and(qConfigInfoTag.tenantId.eq(configInfoTagEntity.getTenantId()))
                        .and(qConfigInfoTag.tagId.eq(configInfoTagEntity.getTagId()))).orElse(null);
        if (result == null) {
            configInfoTagRepository.save(configInfoTagEntity);
        }
        
        ConfigInfo4Tag getConfigInfoTag = persistService
                .findConfigInfo4Tag(configInfoTagEntity.getDataId(), configInfoTagEntity.getGroupId(),
                        configInfoTagEntity.getTenantId(), configInfoTagEntity.getTagId());
        Assert.assertNotNull(getConfigInfoTag);
    }
    
    @Test
    public void findConfigInfoTest() {
        ConfigInfo findConfigInfo = persistService
                .findConfigInfo(configInfoEntity.getDataId(), configInfoEntity.getGroupId(),
                        configInfoEntity.getTenantId());
        Assert.assertNotNull(findConfigInfo);
    }
    
    @Test
    public void findConfigAllInfoTest() {
        ConfigAllInfo configAllInfo = persistService
                .findConfigAllInfo(configInfoEntity.getDataId(), configInfoEntity.getGroupId(),
                        configInfoEntity.getTenantId());
        Assert.assertNotNull(configAllInfo);
    }
    
    @Test
    public void selectTagByConfigTest() {
        List<String> list = persistService
                .selectTagByConfig(configInfoEntity.getDataId(), configInfoEntity.getGroupId(),
                        configInfoEntity.getTenantId());
        
        Assert.assertTrue(list.size() > 0);
    }
    
    @Test
    public void remoteTagByIdAtomicTest() {
        Iterable<ConfigTagsRelationEntity> list = configTagsRelationRepository.findAll();
        persistService.removeTagByIdAtomic(list.iterator().next().getNid());
    }
    
    @Test
    public void removeConfigInfoTest() {
        persistService.removeConfigInfo(configInfoEntity.getDataId(), configInfoEntity.getGroupId(),
                configInfoEntity.getTenantId(), configInfoEntity.getSrcIp(), configInfoEntity.getSrcUser());
    }
    
    @Test
    public void removeConfigInfoTagTest() {
        persistService.removeConfigInfoTag(configInfoTagEntity.getDataId(), configInfoTagEntity.getGroupId(),
                configInfoTagEntity.getTenantId(), configInfoTagEntity.getTagId(), configInfoTagEntity.getSrcIp(),
                configInfoTagEntity.getSrcUser());
    }
    
    @Test
    public void findConfigInfosByIdsTest() {
        QConfigInfoEntity qConfigInfo = QConfigInfoEntity.configInfoEntity;
        List<ConfigInfoEntity> result = (List<ConfigInfoEntity>) configInfoRepository.findAll(
                qConfigInfo.dataId.eq(configInfoEntity.getDataId())
                        .and(qConfigInfo.groupId.eq(configInfoEntity.getGroupId()))
                        .and(qConfigInfo.tenantId.eq(configInfoEntity.getTenantId())));
        String id = "";
        if (CollectionUtils.isEmpty(result)) {
            configInfoRepository.save(configInfoEntity);
            id = String.valueOf(configInfoEntity.getId());
        } else {
            id = String.valueOf(result.get(0).getId());
        }
        List<ConfigInfo> list = persistService.findConfigInfosByIds(id);
        Assert.assertTrue(list.size() > 0);
    }
    
    @Test
    public void findConfigAdvanceInfoTest() {
        ConfigAdvanceInfo configAdvanceInfo = persistService
                .findConfigAdvanceInfo(configInfoEntity.getDataId(), configInfoEntity.getGroupId(),
                        configInfoEntity.getTenantId());
        Assert.assertNotNull(configAdvanceInfo);
    }
    
    @Test
    public void removeConfigInfo4BetaTest() {
        persistService.removeConfigInfo4Beta(configInfoBetaEntity.getDataId(), configInfoBetaEntity.getGroupId(),
                configInfoBetaEntity.getTenantId());
    }
    
    @Test
    public void findAllConfigInfo4ExportTest() {
        List<ConfigAllInfo> list = persistService
                .findAllConfigInfo4Export(configInfoEntity.getDataId(), configInfoEntity.getGroupId(),
                        configInfoEntity.getTenantId(), configInfoEntity.getAppName(), null);
        Assert.assertTrue(list.size() > 0);
    }
    
    @Test
    public void tenantInfoCountByTenantIdTest() {
        List<TenantInfoEntity> getTenantInfo = (List<TenantInfoEntity>) tenantInfoRepository
                .findAll(QTenantInfoEntity.tenantInfoEntity.tenantId.eq(tenantInfoEntity.getTenantId()));
        if (CollectionUtils.isEmpty(getTenantInfo)) {
            tenantInfoRepository.save(tenantInfoEntity);
        }
        int countResult = persistService.tenantInfoCountByTenantId(tenantInfoEntity.getTenantId());
        Assert.assertTrue(countResult > 0);
    }
    
    @Test
    public void addConfigInfoTest() {
        persistService.addConfigInfo(configInfoEntity.getSrcIp(), configInfoEntity.getSrcUser(), configInfo,
                Timestamp.from(Instant.now()), buildMap(), false);
    }
    
    @Test
    public void addConfiTagsRelationAtomicTest() {
        persistService.addConfigTagsRelation(configTagsRelationEntity.getId(), configTagsRelationEntity.getTagName(),
                configTagsRelationEntity.getDataId(), configTagsRelationEntity.getGroupId(),
                configTagsRelationEntity.getTenantId());
    }
    
    @Test
    public void batchInsertOrUpdateTest() throws NacosException {
        List<ConfigAllInfo> list = new ArrayList<>();
        list.add(buildConfigAllInfo());
        QConfigInfoEntity qConfigInfo = QConfigInfoEntity.configInfoEntity;
        ConfigInfoEntity result = configInfoRepository.findOne(qConfigInfo.dataId.eq(configInfoEntity.getDataId())
                .and(qConfigInfo.groupId.eq(configInfoEntity.getGroupId()))
                .and(qConfigInfo.tenantId.eq(configInfoEntity.getTenantId()))).orElse(null);
        if (result != null) {
            configInfoRepository.delete(result);
        }
    
        persistService
                .batchInsertOrUpdate(list, "zhangsan", "127.0.0.1", buildMap(), Timestamp.from(Instant.now()), false,
                        SameConfigPolicy.ABORT);
    }
    
    @Test
    public void findConfigHistoryTest() {
        Page<ConfigHistoryInfo> page = persistService
                .findConfigHistory(hisConfigInfoEntity.getDataId(), hisConfigInfoEntity.getGroupId(),
                        hisConfigInfoEntity.getTenantId(), 0, 10);
        Assert.assertTrue(page.getPageItems().size() > 0);
    }
    
    @Test
    public void detailConfigHistoryTest() {
        
        Long id;
        List<HisConfigInfoEntity> list = (List<HisConfigInfoEntity>) hisConfigInfoRepository.findAll();
        if (CollectionUtils.isEmpty(list)) {
            hisConfigInfoRepository.save(hisConfigInfoEntity);
            id = hisConfigInfoEntity.getNid();
        } else {
            id = list.get(0).getNid();
        }
        ConfigHistoryInfo hisConfigInfo = persistService.detailConfigHistory(id);
        Assert.assertNotNull(hisConfigInfo);
    }
    
    private ConfigAllInfo buildConfigAllInfo() {
        ConfigAllInfo configAllInfo = new ConfigAllInfo();
        configAllInfo.setCreateTime(configInfoEntity.getGmtCreate().getTime());
        configAllInfo.setModifyTime(configInfoEntity.getGmtModified().getTime());
        configAllInfo.setCreateUser(configInfoEntity.getSrcUser());
        configAllInfo.setCreateIp(configInfoEntity.getSrcIp());
        configAllInfo.setDesc(configInfoEntity.getCDesc());
        configAllInfo.setUse(configInfoEntity.getCUse());
        configAllInfo.setEffect(configInfoEntity.getEffect());
        configAllInfo.setType(configInfoEntity.getType());
        configAllInfo.setSchema(configInfoEntity.getCSchema());
        configAllInfo.setTenant(configInfoEntity.getTenantId());
        configAllInfo.setAppName(configInfoEntity.getAppName());
        configAllInfo.setType(configInfoEntity.getType());
        configAllInfo.setDataId(configInfoEntity.getDataId());
        configAllInfo.setGroup(configInfoEntity.getGroupId());
        configAllInfo.setContent(configInfoEntity.getContent());
        configAllInfo.setMd5(configInfoEntity.getMd5());
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
        persistService.insertTenantInfoAtomic(tenantInfoEntity.getKp(), tenantInfoEntity.getTenantId(),
                tenantInfoEntity.getTenantName(), tenantInfoEntity.getTenantDesc(), tenantInfoEntity.getCreateSource(),
                tenantInfoEntity.getGmtCreate());
    }
    
    @Test
    public void updateTenantNameAtomicTest() {
        persistService.updateTenantNameAtomic(tenantInfoEntity.getKp(), tenantInfoEntity.getTenantId(),
                tenantInfoEntity.getTenantName() + LocalDateTime.now().toString(),
                tenantInfoEntity.getTenantDesc() + LocalDateTime.now().toString());
    }
    
    @Test
    public void findTenantByKp1Test() {
        List<TenantInfo> list = persistService.findTenantByKp(tenantInfoEntity.getKp());
        Assert.assertNotNull(list);
        Assert.assertTrue(list.size() > 0);
    }
    
    @Test
    public void findTenantByKp2Test() {
        TenantInfo result = persistService.findTenantByKp(tenantInfoEntity.getKp(), tenantInfoEntity.getTenantId());
        Assert.assertNotNull(result);
    }
    
    @Test
    public void removeTenantInfoAtomicTest() {
        persistService.removeTenantInfoAtomic(tenantInfoEntity.getKp(), tenantInfoEntity.getTenantId());
    }
    
    @Test
    public void listAllGroupKeyMd5Test() {
        List<ConfigInfoWrapper> list = persistService.listAllGroupKeyMd5();
        Assert.assertTrue(list.size() > 0);
    }
    
    @Test
    public void queryConfigInfoTest() {
        ConfigInfoWrapper result = persistService
                .queryConfigInfo(configInfoEntity.getDataId(), configInfoEntity.getGroupId(),
                        configInfoEntity.getTenantId());
        Assert.assertNotNull(result);
    }
    
    //    @Test
    //    public void isConfigInfoBetaTest() {
    //        externalStoragePersistServiceImpl2.isConfigInfoTag();
    //    }
    //
    //    @Test
    //    public void isConfigInfoTagTest() {
    //        externalStoragePersistServiceImpl2.isConfigInfoTag();
    //    }
    
    @Test
    public void configInfoCount1Test() {
        int result = persistService.configInfoCount();
        Assert.assertTrue(result > 0);
    }
    
    @Test
    public void configInfoCount2Test() {
        int result = persistService.configInfoCount(configInfoEntity.getTenantId());
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
        int result = persistService.aggrConfigInfoCount(configInfoEntity.getDataId(), configInfoEntity.getGroupId(),
                configInfoEntity.getTenantId());
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
        Page<ConfigInfoWrapper> page = persistService.findAllConfigInfoFragment(0, 100);
        Assert.assertTrue(page.getPageItems().size() > 0);
    }
    
    @Test
    public void findAllConfigInfoBetaForDumpAllTest() {
        Page<ConfigInfoBetaWrapper> page = persistService.findAllConfigInfoBetaForDumpAll(0, 10);
        Assert.assertTrue(page.getPageItems().size() > 0);
    }
    
    @Test
    public void findAllConfigInfoTagForDumpAllTest() {
        Page<ConfigInfoTagWrapper> page = persistService.findAllConfigInfoTagForDumpAll(0, 10);
        Assert.assertTrue(page.getPageItems().size() > 0);
    }
    
    @Test
    public void findAllAggrGroupTest() {
        List<ConfigInfoAggrEntity> result = ((List<ConfigInfoAggrEntity>) configInfoAggrRepository.findAll());
        if (result.isEmpty()) {
            configInfoAggrRepository.save(configInfoAggrEntity);
        }
    
        List<ConfigInfoChanged> list = persistService.findAllAggrGroup();
        Assert.assertNotNull(list);
        Assert.assertTrue(list.size() > 0);
    }
    
    //
    @Test
    public void findChangeConfigTest() {
        List<ConfigInfoEntity> result = (List<ConfigInfoEntity>) configInfoRepository.findAll();
        if (CollectionUtils.isEmpty(result)) {
            configInfoRepository.save(configInfoEntity);
        }
        List<ConfigInfoWrapper> list = persistService
                .findChangeConfig(Timestamp.valueOf(LocalDateTime.now().minusDays(30)), Timestamp.from(Instant.now()));
        Assert.assertNotNull(list);
        Assert.assertTrue(list.size() > 0);
    }
    
    //
    @Test
    public void findDeletedConfigTest() {
        hisConfigInfoEntity.setGmtModified(new Date());
        hisConfigInfoRepository.save(hisConfigInfoEntity);
        List<ConfigInfo> list = persistService
                .findDeletedConfig(Timestamp.valueOf(LocalDateTime.now().minusDays(30)), Timestamp.from(Instant.now()));
        Assert.assertNotNull(list);
        Assert.assertTrue(list.size() > 0);
    }
}
