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

import com.alibaba.nacos.common.utils.CollectionUtils;
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.config.server.model.ConfigAdvanceInfo;
import com.alibaba.nacos.config.server.model.ConfigAllInfo;
import com.alibaba.nacos.config.server.model.ConfigInfo;
import com.alibaba.nacos.config.server.model.ConfigInfo4Beta;
import com.alibaba.nacos.config.server.model.ConfigInfo4Tag;
import com.alibaba.nacos.config.server.model.ConfigInfoBase;
import com.alibaba.nacos.config.server.model.Page;
import com.alibaba.nacos.config.server.modules.entity.ConfigInfoBetaEntity;
import com.alibaba.nacos.config.server.modules.entity.ConfigInfoEntity;
import com.alibaba.nacos.config.server.modules.entity.ConfigInfoTagEntity;
import com.alibaba.nacos.config.server.modules.entity.ConfigTagsRelationEntity;
import com.alibaba.nacos.config.server.modules.entity.QConfigInfoEntity;
import com.alibaba.nacos.config.server.modules.entity.QConfigInfoTagEntity;
import com.alibaba.nacos.config.server.modules.mapstruct.ConfigInfoMapStruct;
import com.alibaba.nacos.config.server.modules.repository.ConfigInfoBetaRepository;
import com.alibaba.nacos.config.server.modules.repository.ConfigInfoRepository;
import com.alibaba.nacos.config.server.modules.repository.ConfigInfoTagRepository;
import com.alibaba.nacos.config.server.modules.repository.ConfigTagsRelationRepository;
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

import java.sql.Timestamp;
import java.time.Instant;
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
    private ConfigInfoRepository configInfoRepository;
    
    @Autowired
    private ConfigTagsRelationRepository configTagsRelationRepository;
    
    private ConfigInfoEntity configInfoEntity;
    
    private ConfigInfoBetaEntity configInfoBetaEntity;
    
    private ConfigInfoTagEntity configInfoTagEntity;
    
    private ConfigInfo configInfo;
    
    @Before
    public void before() {
        initData();
        insertOrUpdateTest();
    }
    
    private void initData() {
        configInfoEntity = JacksonUtils.toObj(TestData.CONFIG_INFO_JSON, ConfigInfoEntity.class);
        configInfoBetaEntity = JacksonUtils.toObj(TestData.CONFIG_INFO_BETA_JSON, ConfigInfoBetaEntity.class);
        configInfoTagEntity = JacksonUtils.toObj(TestData.CONFIG_INFO_TAG_JSON, ConfigInfoTagEntity.class);
        configInfo = ConfigInfoMapStruct.INSTANCE.convertConfigInfo(configInfoEntity);
        
    }
    
    @Test
    public void findConfigInfoBaseTest() {
        QConfigInfoEntity qConfigInfo = QConfigInfoEntity.configInfoEntity;
        ConfigInfoEntity exist = configInfoRepository.findOne(qConfigInfo.dataId.eq(configInfoBetaEntity.getDataId())
                .and(qConfigInfo.groupId.eq(configInfoBetaEntity.getGroupId()))).orElse(null);
        if (exist == null) {
            configInfoRepository.save(configInfoEntity);
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
        configInfoRepository.deleteAll();
        configInfoBetaRepository.deleteAll();
        QConfigInfoEntity qConfigInfo = QConfigInfoEntity.configInfoEntity;
        configInfoRepository.findOne(qConfigInfo.dataId.eq(configInfoEntity.getDataId())
                .and(qConfigInfo.groupId.eq(configInfoEntity.getGroupId()))
                .and(qConfigInfo.tenantId.eq(configInfoEntity.getTenantId())))
                .ifPresent(s -> configInfoRepository.delete(s));
        
        persistService.insertOrUpdate(configInfoEntity.getSrcIp(), configInfoEntity.getSrcUser(), configInfo,
                Timestamp.from(Instant.now()), buildInsertOrUpdateMap(), true);
        
        configInfoBetaRepository.save(configInfoBetaEntity);
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
        configInfoBetaRepository.deleteAll();
        ConfigInfoBetaEntity result = new ConfigInfoBetaEntity();
        configInfoBetaRepository.save(configInfoBetaEntity);
        BeanUtils.copyProperties(result, configInfoBetaEntity);
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
        configInfoBetaRepository.deleteAll();
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
        map.put("appName", "userService");
        map.put("config_tags", "config_tags");
        Page<ConfigInfo> page = persistService
                .findConfigInfo4Page(0, 10, configInfoEntity.getDataId(), configInfoEntity.getGroupId(),
                        configInfoEntity.getTenantId(), map);
        Assert.assertTrue(page.getPageItems().size() > 0);
    }
    
    @Test
    public void findConfigInfoLike4PageTest() {
        Map<String, Object> map = new HashMap<>();
        map.put("appName", "userService");
        map.put("config_tags", "config_tags");
        Page<ConfigInfo> page = persistService
                .findConfigInfoLike4Page(0, 10, configInfoEntity.getDataId(), configInfoEntity.getGroupId(),
                        configInfoEntity.getTenantId(), map);
        Assert.assertTrue(page.getPageItems().size() > 0);
    }
    
    @Test
    public void findConfigInfo4BetaTest() {
        configInfoBetaRepository.deleteAll();
        configInfoBetaRepository.save(configInfoBetaEntity);
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
}
