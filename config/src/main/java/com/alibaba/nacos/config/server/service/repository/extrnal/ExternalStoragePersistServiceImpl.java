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

package com.alibaba.nacos.config.server.service.repository.extrnal;

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.common.utils.MD5Utils;
import com.alibaba.nacos.config.server.configuration.ConditionOnExternalStorage;
import com.alibaba.nacos.config.server.constant.Constants;
import com.alibaba.nacos.config.server.enums.FileTypeEnum;
import com.alibaba.nacos.config.server.model.ConfigAdvanceInfo;
import com.alibaba.nacos.config.server.model.ConfigAllInfo;
import com.alibaba.nacos.config.server.model.ConfigHistoryInfo;
import com.alibaba.nacos.config.server.model.ConfigInfo;
import com.alibaba.nacos.config.server.model.ConfigInfo4Beta;
import com.alibaba.nacos.config.server.model.ConfigInfo4Tag;
import com.alibaba.nacos.config.server.model.ConfigInfoAggr;
import com.alibaba.nacos.config.server.model.ConfigInfoBase;
import com.alibaba.nacos.config.server.model.ConfigInfoBetaWrapper;
import com.alibaba.nacos.config.server.model.ConfigInfoChanged;
import com.alibaba.nacos.config.server.model.ConfigInfoTagWrapper;
import com.alibaba.nacos.config.server.model.ConfigInfoWrapper;
import com.alibaba.nacos.config.server.model.ConfigKey;
import com.alibaba.nacos.config.server.model.Page;
import com.alibaba.nacos.config.server.model.SameConfigPolicy;
import com.alibaba.nacos.config.server.model.SubInfo;
import com.alibaba.nacos.config.server.model.TenantInfo;
import com.alibaba.nacos.config.server.modules.entity.ConfigInfoAggrEntity;
import com.alibaba.nacos.config.server.modules.entity.ConfigInfoBetaEntity;
import com.alibaba.nacos.config.server.modules.entity.ConfigInfoEntity;
import com.alibaba.nacos.config.server.modules.entity.ConfigInfoTagEntity;
import com.alibaba.nacos.config.server.modules.entity.ConfigTagsRelationEntity;
import com.alibaba.nacos.config.server.modules.entity.HisConfigInfoEntity;
import com.alibaba.nacos.config.server.modules.entity.QConfigInfoAggrEntity;
import com.alibaba.nacos.config.server.modules.entity.QConfigInfoBetaEntity;
import com.alibaba.nacos.config.server.modules.entity.QConfigInfoEntity;
import com.alibaba.nacos.config.server.modules.entity.QConfigInfoTagEntity;
import com.alibaba.nacos.config.server.modules.entity.QConfigTagsRelationEntity;
import com.alibaba.nacos.config.server.modules.entity.QHisConfigInfoEntity;
import com.alibaba.nacos.config.server.modules.entity.QTenantInfoEntity;
import com.alibaba.nacos.config.server.modules.entity.TenantInfoEntity;
import com.alibaba.nacos.config.server.modules.mapstruct.ConfigAdvanceInfoMapStruct;
import com.alibaba.nacos.config.server.modules.mapstruct.ConfigAllInfoMapStruct;
import com.alibaba.nacos.config.server.modules.mapstruct.ConfigHistoryInfoMapStruct;
import com.alibaba.nacos.config.server.modules.mapstruct.ConfigInfo4BetaMapStruct;
import com.alibaba.nacos.config.server.modules.mapstruct.ConfigInfo4TagMapStruct;
import com.alibaba.nacos.config.server.modules.mapstruct.ConfigInfoAggrMapStruct;
import com.alibaba.nacos.config.server.modules.mapstruct.ConfigInfoBetaWrapperMapStruct;
import com.alibaba.nacos.config.server.modules.mapstruct.ConfigInfoChangedMapStruct;
import com.alibaba.nacos.config.server.modules.mapstruct.ConfigInfoEntityMapStruct;
import com.alibaba.nacos.config.server.modules.mapstruct.ConfigInfoMapStruct;
import com.alibaba.nacos.config.server.modules.mapstruct.ConfigInfoTagWrapperMapStruct;
import com.alibaba.nacos.config.server.modules.mapstruct.ConfigInfoWrapperMapStruct;
import com.alibaba.nacos.config.server.modules.mapstruct.ConfigKeyMapStruct;
import com.alibaba.nacos.config.server.modules.mapstruct.TenantInfoMapStruct;
import com.alibaba.nacos.config.server.modules.repository.ConfigInfoAggrRepository;
import com.alibaba.nacos.config.server.modules.repository.ConfigInfoBetaRepository;
import com.alibaba.nacos.config.server.modules.repository.ConfigInfoRepository;
import com.alibaba.nacos.config.server.modules.repository.ConfigInfoTagRepository;
import com.alibaba.nacos.config.server.modules.repository.ConfigTagsRelationRepository;
import com.alibaba.nacos.config.server.modules.repository.HisConfigInfoRepository;
import com.alibaba.nacos.config.server.modules.repository.TenantInfoRepository;
import com.alibaba.nacos.config.server.service.repository.PaginationHelper;
import com.alibaba.nacos.config.server.service.repository.PersistService;
import com.alibaba.nacos.config.server.utils.LogUtil;
import com.alibaba.nacos.config.server.utils.ParamUtils;
import com.google.common.base.Joiner;
import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.types.dsl.BooleanExpression;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.util.Strings;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Conditional;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.jdbc.CannotGetJdbcConnectionException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.TransactionException;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.TransactionSystemException;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.Assert;

import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Predicate;
import javax.persistence.criteria.Root;
import java.io.IOException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * External Storage Persist Service.
 *
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 * @author klw
 */
@Slf4j
@SuppressWarnings(value = {"PMD.MethodReturnWrapperTypeRule", "checkstyle:linelength"})
@Conditional(value = ConditionOnExternalStorage.class)
@Component
public class ExternalStoragePersistServiceImpl implements PersistService {
    
    @Autowired
    private ConfigInfoRepository configInfoRepository;
    
    @Autowired
    private ConfigInfoBetaRepository configInfoBetaRepository;
    
    @Autowired
    private ConfigInfoTagRepository configInfoTagRepository;
    
    @Autowired
    private ConfigTagsRelationRepository configTagsRelationRepository;
    
    @Autowired
    private HisConfigInfoRepository hisConfigInfoRepository;
    
    @Autowired
    private TenantInfoRepository tenantInfoRepository;
    
    @Autowired
    private ConfigInfoAggrRepository configInfoAggrRepository;
    
    @Autowired
    private TransactionTemplate tjt;
    
    /**
     * constant variables.
     */
    public static final String SPOT = ".";
    
    @Override
    public <E> PaginationHelper<E> createPaginationHelper() {
        return null;
    }
    
    // ----------------------- config_info table insert update delete
    
    @Override
    public void addConfigInfo(final String srcIp, final String srcUser, final ConfigInfo configInfo,
            final Timestamp time, final Map<String, Object> configAdvanceInfo, final boolean notify) {
        ConfigInfoEntity configInfoEntity = ConfigInfoEntityMapStruct.INSTANCE.convertConfigInfoEntity(configInfo);
        tjt.execute(new TransactionCallback<Boolean>() {
            @Override
            public Boolean doInTransaction(TransactionStatus status) {
                try {
                    long configId = addConfigInfoAtomic(-1, srcIp, srcUser, configInfo, time, configAdvanceInfo);
                    String configTags =
                            configAdvanceInfo == null ? null : (String) configAdvanceInfo.get("config_tags");
                    addConfigTagsRelation(configId, configTags, configInfo.getDataId(), configInfoEntity.getGroupId(),
                            configInfoEntity.getTenantId());
                    insertConfigHistoryAtomic(0, configInfo, srcIp, srcUser, time, "I");
                    
                } catch (CannotGetJdbcConnectionException e) {
                    log.error("[db-error] " + e.toString(), e);
                    throw e;
                }
                return Boolean.TRUE;
            }
        });
    }
    
    @Override
    public void addConfigInfo4Beta(ConfigInfo configInfo, String betaIps, String srcIp, String srcUser, Timestamp time,
            boolean notify) {
        String appNameTmp = StringUtils.isBlank(configInfo.getAppName()) ? StringUtils.EMPTY : configInfo.getAppName();
        String tenantTmp = StringUtils.isBlank(configInfo.getTenant()) ? StringUtils.EMPTY : configInfo.getTenant();
        String md5 = MD5Utils.md5Hex(configInfo.getContent(), com.alibaba.nacos.api.common.Constants.ENCODE);
        try {
            ConfigInfoBetaEntity configInfoBeta = new ConfigInfoBetaEntity();
            configInfoBeta.setDataId(configInfo.getDataId());
            configInfoBeta.setGroupId(configInfo.getGroup());
            configInfoBeta.setAppName(appNameTmp);
            configInfoBeta.setContent(configInfo.getContent());
            configInfoBeta.setBetaIps(betaIps);
            configInfoBeta.setMd5(md5);
            configInfoBeta.setGmtCreate(time);
            configInfoBeta.setGmtModified(time);
            configInfoBeta.setSrcUser(srcUser);
            configInfoBeta.setSrcIp(srcIp);
            configInfoBeta.setTenantId(tenantTmp);
            configInfoBetaRepository.save(configInfoBeta);
        } catch (CannotGetJdbcConnectionException e) {
            log.error("[db-error] " + e.toString(), e);
            throw e;
        }
    }
    
    @Override
    public void addConfigInfo4Tag(ConfigInfo configInfo, String tag, String srcIp, String srcUser, Timestamp time,
            boolean notify) {
        String appNameTmp = StringUtils.isBlank(configInfo.getAppName()) ? StringUtils.EMPTY : configInfo.getAppName();
        String tenantTmp = StringUtils.isBlank(configInfo.getTenant()) ? StringUtils.EMPTY : configInfo.getTenant();
        String tagTmp = StringUtils.isBlank(tag) ? StringUtils.EMPTY : tag.trim();
        try {
            String md5 = MD5Utils.md5Hex(configInfo.getContent(), com.alibaba.nacos.api.common.Constants.ENCODE);
            ConfigInfoTagEntity configInfoTag = new ConfigInfoTagEntity();
            configInfoTag.setDataId(configInfo.getDataId());
            configInfoTag.setGroupId(configInfo.getGroup());
            configInfoTag.setTenantId(tenantTmp);
            configInfoTag.setTagId(tagTmp);
            configInfoTag.setAppName(appNameTmp);
            configInfoTag.setContent(configInfo.getContent());
            configInfoTag.setMd5(md5);
            configInfoTag.setGmtCreate(time);
            configInfoTag.setGmtModified(time);
            configInfoTag.setSrcUser(srcUser);
            configInfoTag.setSrcIp(srcIp);
            configInfoTagRepository.save(configInfoTag);
        } catch (CannotGetJdbcConnectionException e) {
            log.error("[db-error] " + e.toString(), e);
            throw e;
        }
    }
    
    @Override
    public void updateConfigInfo(final ConfigInfo configInfo, final String srcIp, final String srcUser,
            final Timestamp time, final Map<String, Object> configAdvanceInfo, final boolean notify) {
        tjt.execute(new TransactionCallback<Boolean>() {
            @Override
            public Boolean doInTransaction(TransactionStatus status) {
                try {
                    ConfigInfo oldConfigInfo = findConfigInfo(configInfo.getDataId(), configInfo.getGroup(),
                            configInfo.getTenant());
                    String appNameTmp = oldConfigInfo.getAppName();
                    // 用户传过来的appName不为空，则用持久化用户的appName，否则用db的;清空appName的时候需要传空串
                    if (configInfo.getAppName() == null) {
                        configInfo.setAppName(appNameTmp);
                    }
                    configInfo.setId(oldConfigInfo.getId());
                    updateConfigInfoAtomic(configInfo, srcIp, srcUser, time, configAdvanceInfo);
                    String configTags =
                            configAdvanceInfo == null ? null : (String) configAdvanceInfo.get("config_tags");
                    if (configTags != null) {
                        // 删除所有tag，然后再重新创建
                        removeTagByIdAtomic(oldConfigInfo.getId());
                        addConfigTagsRelation(oldConfigInfo.getId(), configTags, configInfo.getDataId(),
                                configInfo.getGroup(), configInfo.getTenant());
                    }
                    insertConfigHistoryAtomic(oldConfigInfo.getId(), oldConfigInfo, srcIp, srcUser, time, "U");
                } catch (CannotGetJdbcConnectionException e) {
                    log.error("[db-error] " + e.toString(), e);
                    throw e;
                }
                return Boolean.TRUE;
            }
        });
    }
    
    @Override
    public void updateConfigInfo4Beta(ConfigInfo configInfo, String betaIps, String srcIp, String srcUser,
            Timestamp time, boolean notify) {
        BooleanBuilder booleanBuilder = new BooleanBuilder();
        QConfigInfoBetaEntity qConfigInfoBeta = QConfigInfoBetaEntity.configInfoBetaEntity;
        if (StringUtils.isNotBlank(configInfo.getDataId())) {
            booleanBuilder.and(qConfigInfoBeta.dataId.eq(configInfo.getDataId()));
        }
        if (StringUtils.isNotBlank(configInfo.getGroup())) {
            booleanBuilder.and(qConfigInfoBeta.groupId.eq(configInfo.getGroup()));
        }
        if (StringUtils.isBlank(configInfo.getTenant())) {
            booleanBuilder.and(qConfigInfoBeta.tenantId.eq(StringUtils.EMPTY).or(qConfigInfoBeta.tenantId.isNull()));
        } else {
            booleanBuilder.and(qConfigInfoBeta.tenantId.eq(configInfo.getTenant()));
        }
        ConfigInfoBetaEntity configInfoBeta = configInfoBetaRepository.findOne(booleanBuilder).orElse(null);
        try {
            String appNameTmp =
                    StringUtils.isBlank(configInfo.getAppName()) ? StringUtils.EMPTY : configInfo.getAppName();
            String md5 = MD5Utils.md5Hex(configInfo.getContent(), com.alibaba.nacos.api.common.Constants.ENCODE);
            configInfoBeta.setAppName(appNameTmp);
            configInfoBeta.setContent(configInfo.getContent());
            configInfoBeta.setMd5(md5);
            configInfoBeta.setSrcIp(srcIp);
            configInfoBeta.setSrcUser(srcUser);
            configInfoBetaRepository.save(configInfoBeta);
        } catch (CannotGetJdbcConnectionException e) {
            log.error("[db-error] " + e.toString(), e);
            throw e;
        }
    }
    
    @Override
    public void updateConfigInfo4Tag(ConfigInfo configInfo, String tag, String srcIp, String srcUser, Timestamp time,
            boolean notify) {
        String tagTmp = StringUtils.isBlank(tag) ? StringUtils.EMPTY : tag.trim();
        BooleanBuilder booleanBuilder = new BooleanBuilder();
        QConfigInfoTagEntity qConfigInfoTag = QConfigInfoTagEntity.configInfoTagEntity;
        if (StringUtils.isBlank(configInfo.getTenant())) {
            booleanBuilder.and(qConfigInfoTag.tenantId.eq(StringUtils.EMPTY).or(qConfigInfoTag.tenantId.isNull()));
        } else {
            booleanBuilder.and(qConfigInfoTag.tenantId.eq(configInfo.getTenant()));
        }
        booleanBuilder.and(qConfigInfoTag.dataId.eq(configInfo.getDataId())
                        .and(qConfigInfoTag.groupId.eq(configInfo.getGroup()))
                .and(qConfigInfoTag.tagId.eq(tagTmp)));
        ConfigInfoTagEntity configInfoTag = configInfoTagRepository.findOne(booleanBuilder)
                .orElse(new ConfigInfoTagEntity());
        try {
            String appNameTmp = StringUtils.isBlank(configInfo.getAppName()) ? StringUtils.EMPTY : configInfo.getAppName();
            String tenantTmp = StringUtils.isBlank(configInfo.getTenant()) ? StringUtils.EMPTY : configInfo.getTenant();
            configInfoTag.setDataId(configInfo.getDataId());
            configInfoTag.setGroupId(configInfo.getGroup());
            configInfoTag.setTenantId(tenantTmp);
            configInfoTag.setTagId(tag);
            configInfoTag.setAppName(appNameTmp);
            configInfoTag.setContent(configInfo.getContent());
            String md5 = MD5Utils.md5Hex(configInfo.getContent(), com.alibaba.nacos.api.common.Constants.ENCODE);
            configInfoTag.setMd5(md5);
            configInfoTag.setGmtCreate(time);
            configInfoTag.setGmtModified(time);
            configInfoTag.setSrcUser(srcUser);
            configInfoTag.setSrcIp(srcIp);
            configInfoTagRepository.save(configInfoTag);
        } catch (CannotGetJdbcConnectionException e) {
            log.error("[db-error] " + e.toString(), e);
            throw e;
        }
    }
    
    @Override
    public void insertOrUpdateBeta(final ConfigInfo configInfo, final String betaIps, final String srcIp,
            final String srcUser, final Timestamp time, final boolean notify) {
        try {
            addConfigInfo4Beta(configInfo, betaIps, srcIp, null, time, notify);
        } catch (DataIntegrityViolationException ive) { // Unique constraint conflict
            updateConfigInfo4Beta(configInfo, betaIps, srcIp, null, time, notify);
        }
    }
    
    @Override
    public void insertOrUpdateTag(final ConfigInfo configInfo, final String tag, final String srcIp,
            final String srcUser, final Timestamp time, final boolean notify) {
        try {
            addConfigInfo4Tag(configInfo, tag, srcIp, null, time, notify);
        } catch (DataIntegrityViolationException ive) { // Unique constraint conflict
            updateConfigInfo4Tag(configInfo, tag, srcIp, null, time, notify);
        }
    }
    
    @Override
    public void updateMd5(String dataId, String group, String tenant, String md5, Timestamp lastTime) {
        QConfigInfoEntity qConfigInfo = QConfigInfoEntity.configInfoEntity;
        BooleanBuilder booleanBuilder = new BooleanBuilder();
        if (StringUtils.isNotBlank(dataId)) {
            booleanBuilder.and(qConfigInfo.dataId.eq(dataId));
        }
        if (StringUtils.isNotBlank(group)) {
            booleanBuilder.and(qConfigInfo.groupId.eq(group));
        }
        if (StringUtils.isBlank(tenant)) {
            booleanBuilder.and(qConfigInfo.tenantId.eq(StringUtils.EMPTY).or(qConfigInfo.tenantId.isNull()));
        } else {
            booleanBuilder.and(qConfigInfo.tenantId.eq(tenant));
        }
        if (lastTime != null) {
            booleanBuilder.and(qConfigInfo.gmtModified.eq(lastTime));
        }
        configInfoRepository.findOne(booleanBuilder).ifPresent(config -> {
            config.setMd5(md5);
            configInfoRepository.save(config);
        });
    }
    
    @Override
    public void insertOrUpdate(String srcIp, String srcUser, ConfigInfo configInfo, Timestamp time,
            Map<String, Object> configAdvanceInfo) {
        insertOrUpdate(srcIp, srcUser, configInfo, time, configAdvanceInfo, true);
    }
    
    @Override
    public void insertOrUpdate(String srcIp, String srcUser, ConfigInfo configInfo, Timestamp time,
            Map<String, Object> configAdvanceInfo, boolean notify) {
        try {
            addConfigInfo(srcIp, srcUser, configInfo, time, configAdvanceInfo, notify);
        } catch (DataIntegrityViolationException ive) { // Unique constraint conflict
            updateConfigInfo(configInfo, srcIp, srcUser, time, configAdvanceInfo, notify);
        }
    }
    
    @Override
    public void insertOrUpdateSub(SubInfo subInfo) {
        try {
            addConfigSubAtomic(subInfo.getDataId(), subInfo.getGroup(), subInfo.getAppName(), subInfo.getDate());
        } catch (DataIntegrityViolationException ive) { // Unique constraint conflict
            updateConfigSubAtomic(subInfo.getDataId(), subInfo.getGroup(), subInfo.getAppName(), subInfo.getDate());
        }
    }
    
    @Override
    public void removeConfigInfo(final String dataId, final String group, final String tenant, final String srcIp,
            final String srcUser) {
        tjt.execute(new TransactionCallback<Boolean>() {
            final Timestamp time = new Timestamp(System.currentTimeMillis());
            
            @Override
            public Boolean doInTransaction(TransactionStatus status) {
                try {
                    ConfigInfo configInfo = findConfigInfo(dataId, group, tenant);
                    if (configInfo != null) {
                        removeConfigInfoAtomic(dataId, group, tenant, srcIp, srcUser);
                        removeTagByIdAtomic(configInfo.getId());
                        insertConfigHistoryAtomic(configInfo.getId(), configInfo, srcIp, srcUser, time, "D");
                    }
                } catch (CannotGetJdbcConnectionException e) {
                    LogUtil.FATAL_LOG.error("[db-error] " + e.toString(), e);
                    throw e;
                }
                return Boolean.TRUE;
            }
        });
    }
    
    @Override
    public List<ConfigInfo> removeConfigInfoByIds(final List<Long> ids, final String srcIp, final String srcUser) {
        if (CollectionUtils.isEmpty(ids)) {
            return null;
        }
        ids.removeAll(Collections.singleton(null));
        List<ConfigInfo> result = tjt.execute(new TransactionCallback<List<ConfigInfo>>() {
            final Timestamp time = new Timestamp(System.currentTimeMillis());
            
            @Override
            public List<ConfigInfo> doInTransaction(TransactionStatus status) {
                try {
                    String idsStr = Joiner.on(",").join(ids);
                    List<ConfigInfo> configInfoList = findConfigInfosByIds(idsStr);
                    if (!CollectionUtils.isEmpty(configInfoList)) {
                        removeConfigInfoByIdsAtomic(idsStr);
                        for (ConfigInfo configInfo : configInfoList) {
                            removeTagByIdAtomic(configInfo.getId());
                            insertConfigHistoryAtomic(configInfo.getId(), configInfo, srcIp, srcUser, time, "D");
                        }
                    }
                    return configInfoList;
                } catch (CannotGetJdbcConnectionException e) {
                    LogUtil.FATAL_LOG.error("[db-error] " + e.toString(), e);
                    throw e;
                }
            }
        });
        return result;
    }
    
    @Override
    public void removeConfigInfo4Beta(final String dataId, final String group, final String tenant) {
        final String tenantTmp = StringUtils.isBlank(tenant) ? StringUtils.EMPTY : tenant;
        tjt.execute(new TransactionCallback<Boolean>() {
            @Override
            public Boolean doInTransaction(TransactionStatus status) {
                try {
                    ConfigInfo4Beta configInfo4Beta = findConfigInfo4Beta(dataId, group, tenant);
                    if (configInfo4Beta != null) {
                        configInfoBetaRepository.deleteById(configInfo4Beta.getId());
                    }
                } catch (CannotGetJdbcConnectionException e) {
                    log.error("[db-error] " + e.toString(), e);
                    throw e;
                }
                return Boolean.TRUE;
            }
        });
    }
    
    // ----------------------- config_aggr_info table insert update delete
    
    @Override
    public boolean addAggrConfigInfo(final String dataId, final String group, String tenant, final String datumId,
            String appName, final String content) {
        final Timestamp now = new Timestamp(System.currentTimeMillis());
        QConfigInfoAggrEntity qConfigInfoAggr = QConfigInfoAggrEntity.configInfoAggrEntity;
        BooleanBuilder booleanBuilder = new BooleanBuilder();
        if (StringUtils.isNotBlank(dataId)) {
            booleanBuilder.and(qConfigInfoAggr.dataId.eq(dataId));
        }
        if (StringUtils.isNotBlank(group)) {
            booleanBuilder.and(qConfigInfoAggr.groupId.eq(group));
        }
        if (StringUtils.isNotBlank(datumId)) {
            booleanBuilder.and(qConfigInfoAggr.datumId.eq(datumId));
        }
        if (StringUtils.isBlank(tenant)) {
            booleanBuilder.and(qConfigInfoAggr.tenantId.eq(StringUtils.EMPTY).or(qConfigInfoAggr.tenantId.isNull()));
        } else {
            booleanBuilder.and(qConfigInfoAggr.tenantId.eq(tenant));
        }
        try {
            try {
                ConfigInfoAggrEntity result = configInfoAggrRepository.findOne(booleanBuilder).orElse(null);
                
                if (result.getContent() != null && result.getContent().equals(content)) {
                    return true;
                } else {
                    result.setContent(content);
                    result.setGmtModified(now);
                    configInfoAggrRepository.save(result);
                    return true;
                }
            } catch (EmptyResultDataAccessException ex) { // no data, insert
                String tenantTmp = StringUtils.isBlank(tenant) ? StringUtils.EMPTY : tenant;
                String appNameTmp = StringUtils.isBlank(appName) ? StringUtils.EMPTY : appName;
                ConfigInfoAggrEntity configInfoAggrEntity = new ConfigInfoAggrEntity();
                configInfoAggrEntity.setDataId(dataId);
                configInfoAggrEntity.setGroupId(group);
                configInfoAggrEntity.setDatumId(datumId);
                configInfoAggrEntity.setContent(content);
                configInfoAggrEntity.setGmtModified(now);
                configInfoAggrEntity.setAppName(appNameTmp);
                configInfoAggrEntity.setTenantId(tenantTmp);
                configInfoAggrRepository.save(configInfoAggrEntity);
                return true;
                
            }
        } catch (DataAccessException e) {
            LogUtil.FATAL_LOG.error("[db-error] " + e.toString(), e);
            throw e;
        }
    }
    
    @Override
    public void removeSingleAggrConfigInfo(final String dataId, final String group, final String tenant,
            final String datumId) {
        QConfigInfoAggrEntity qConfigInfoAggr = QConfigInfoAggrEntity.configInfoAggrEntity;
        BooleanBuilder booleanBuilder = new BooleanBuilder();
        if (StringUtils.isBlank(tenant)) {
            booleanBuilder.and(qConfigInfoAggr.tenantId.eq(StringUtils.EMPTY).or(qConfigInfoAggr.tenantId.isNull()));
        } else {
            booleanBuilder.and(qConfigInfoAggr.tenantId.eq(tenant));
        }
        booleanBuilder.and(qConfigInfoAggr.dataId.eq(dataId)).and(qConfigInfoAggr.groupId.eq(group))
                .and(qConfigInfoAggr.datumId.eq(datumId));
        configInfoAggrRepository.findOne(booleanBuilder).ifPresent(aggr -> configInfoAggrRepository.delete(aggr));
    }
    
    @Override
    public void removeAggrConfigInfo(final String dataId, final String group, final String tenant) {
        QConfigInfoAggrEntity qConfigInfoAggr = QConfigInfoAggrEntity.configInfoAggrEntity;
        BooleanBuilder booleanBuilder = new BooleanBuilder();
        if (StringUtils.isNotBlank(dataId)) {
            booleanBuilder.and(qConfigInfoAggr.dataId.eq(dataId));
        }
        if (StringUtils.isNotBlank(group)) {
            booleanBuilder.and(qConfigInfoAggr.groupId.eq(group));
        }
        if (StringUtils.isBlank(tenant)) {
            booleanBuilder.and(qConfigInfoAggr.tenantId.eq(StringUtils.EMPTY).or(qConfigInfoAggr.tenantId.isNull()));
        } else {
            booleanBuilder.and(qConfigInfoAggr.tenantId.eq(tenant));
        }
        configInfoAggrRepository.findOne(booleanBuilder).ifPresent(aggr -> configInfoAggrRepository.delete(aggr));
    }
    
    @Override
    public boolean batchRemoveAggr(final String dataId, final String group, final String tenant,
            final List<String> datumList) {
        QConfigInfoAggrEntity qConfigInfoAggr = QConfigInfoAggrEntity.configInfoAggrEntity;
        BooleanBuilder booleanBuilder = new BooleanBuilder();
        if (StringUtils.isNotBlank(dataId)) {
            booleanBuilder.and(qConfigInfoAggr.dataId.eq(dataId));
        }
        if (StringUtils.isNotBlank(group)) {
            booleanBuilder.and(qConfigInfoAggr.groupId.eq(group));
        }
        booleanBuilder.and(qConfigInfoAggr.dataId.eq(dataId)).and(qConfigInfoAggr.groupId.eq(group))
                .and(qConfigInfoAggr.datumId.in(datumList));
        configInfoAggrRepository.findAll(booleanBuilder).forEach(aggr -> configInfoAggrRepository.delete(aggr));
        return true;
    }
    
    @Override
    public void removeConfigHistory(final Timestamp startTime, final int limitSize) {
        QHisConfigInfoEntity qHisConfigInfo = QHisConfigInfoEntity.hisConfigInfoEntity;
        Iterable<HisConfigInfoEntity> iterable = hisConfigInfoRepository
                .findAll(qHisConfigInfo.gmtModified.lt(startTime), PageRequest.of(0, limitSize));
        hisConfigInfoRepository.deleteAll(iterable);
    }
    
    @Override
    public int findConfigHistoryCountByTime(final Timestamp startTime) {
        QHisConfigInfoEntity qHisConfigInfo = QHisConfigInfoEntity.hisConfigInfoEntity;
        Long result = hisConfigInfoRepository.count(qHisConfigInfo.gmtModified.lt(startTime));
        if (result == null) {
            throw new IllegalArgumentException("configInfoBetaCount error");
        }
        return result.intValue();
    }
    
    @Override
    public long findConfigMaxId() {
        try {
            //TODO 关系型特性查询
            return configInfoRepository.findConfigMaxId();
        } catch (NullPointerException e) {
            return 0;
        }
    }
    
    @Override
    public boolean batchPublishAggr(final String dataId, final String group, final String tenant,
            final Map<String, String> datumMap, final String appName) {
        try {
            Boolean isPublishOk = tjt.execute(new TransactionCallback<Boolean>() {
                @Override
                public Boolean doInTransaction(TransactionStatus status) {
                    for (Map.Entry<String, String> entry : datumMap.entrySet()) {
                        try {
                            if (!addAggrConfigInfo(dataId, group, tenant, entry.getKey(), appName, entry.getValue())) {
                                throw new TransactionSystemException("error in addAggrConfigInfo");
                            }
                        } catch (Throwable e) {
                            throw new TransactionSystemException("error in addAggrConfigInfo");
                        }
                    }
                    return Boolean.TRUE;
                }
            });
            if (isPublishOk == null) {
                return false;
            }
            return isPublishOk;
        } catch (TransactionException e) {
            LogUtil.FATAL_LOG.error("[db-error] " + e.toString(), e);
            return false;
        }
    }
    
    @Override
    public boolean replaceAggr(final String dataId, final String group, final String tenant,
            final Map<String, String> datumMap, final String appName) {
        String appNameTmp = appName == null ? StringUtils.EMPTY : appName;
        String tenantTmp = StringUtils.isBlank(tenant) ? StringUtils.EMPTY : tenant;
        
        removeAggrConfigInfo(dataId, group, tenant);
        List<ConfigInfoAggrEntity> list = new ArrayList<>();
        
        for (Map.Entry<String, String> datumEntry : datumMap.entrySet()) {
            ConfigInfoAggrEntity configInfoAggrEntity = new ConfigInfoAggrEntity();
            configInfoAggrEntity.setDataId(dataId);
            configInfoAggrEntity.setGroupId(group);
            configInfoAggrEntity.setDatumId(datumEntry.getKey());
            configInfoAggrEntity.setTenantId(tenantTmp);
            configInfoAggrEntity.setAppName(appNameTmp);
            configInfoAggrEntity.setContent(datumEntry.getValue());
            configInfoAggrEntity.setGmtModified(new Timestamp(System.currentTimeMillis()));
            list.add(configInfoAggrEntity);
        }
        
        configInfoAggrRepository.saveAll(list);
        return true;
        
    }
    
    @Deprecated
    @Override
    public List<ConfigInfo> findAllDataIdAndGroup() {
        return null;
    }
    
    @Override
    public ConfigInfo4Beta findConfigInfo4Beta(final String dataId, final String group, final String tenant) {
        BooleanBuilder booleanBuilder = new BooleanBuilder();
        QConfigInfoBetaEntity qConfigInfoBeta = QConfigInfoBetaEntity.configInfoBetaEntity;
        if (StringUtils.isNotBlank(dataId)) {
            booleanBuilder.and(qConfigInfoBeta.dataId.eq(dataId));
        }
        if (StringUtils.isNotBlank(group)) {
            booleanBuilder.and(qConfigInfoBeta.groupId.eq(group));
        }
        if (StringUtils.isBlank(tenant)) {
            booleanBuilder.and(qConfigInfoBeta.tenantId.eq(StringUtils.EMPTY).or(qConfigInfoBeta.tenantId.isNull()));
        } else {
            booleanBuilder.and(qConfigInfoBeta.tenantId.eq(tenant));
        }
        ConfigInfoBetaEntity configInfoBetaEntity = configInfoBetaRepository.findOne(booleanBuilder).orElse(null);
        return ConfigInfo4BetaMapStruct.INSTANCE.convertConfigInfo4Beta(configInfoBetaEntity);
    }
    
    @Override
    public ConfigInfo4Tag findConfigInfo4Tag(final String dataId, final String group, final String tenant,
            final String tag) {
        BooleanBuilder booleanBuilder = new BooleanBuilder();
        QConfigInfoTagEntity qConfigInfoTag = QConfigInfoTagEntity.configInfoTagEntity;
        if (StringUtils.isNotBlank(dataId)) {
            booleanBuilder.and(qConfigInfoTag.dataId.eq(dataId));
        }
        if (StringUtils.isNotBlank(group)) {
            booleanBuilder.and(qConfigInfoTag.groupId.eq(group));
        }
        if (StringUtils.isBlank(tenant)) {
            booleanBuilder.and(qConfigInfoTag.tenantId.eq(StringUtils.EMPTY).or(qConfigInfoTag.tenantId.isNull()));
        } else {
            booleanBuilder.and(qConfigInfoTag.tenantId.eq(tenant));
        }
        if (StringUtils.isBlank(tag)) {
            booleanBuilder.and(qConfigInfoTag.tenantId.eq(StringUtils.EMPTY).or(qConfigInfoTag.tenantId.isNull()));
        } else {
            booleanBuilder.and(qConfigInfoTag.tenantId.eq(tag.trim()));
        }
        ConfigInfoTagEntity result = configInfoTagRepository.findOne(booleanBuilder).orElse(null);
        return ConfigInfo4TagMapStruct.INSTANCE.convertConfigInfo4Tag(result);
    }
    
    @Override
    public ConfigInfo findConfigInfoApp(final String dataId, final String group, final String tenant,
            final String appName) {
        QConfigInfoEntity qConfigInfo = QConfigInfoEntity.configInfoEntity;
        BooleanExpression expression = qConfigInfo.dataId.eq(dataId).and(qConfigInfo.groupId.eq(group))
                .and(qConfigInfo.appName.eq(appName));
        if (StringUtils.isBlank(tenant)) {
            expression = expression.and(qConfigInfo.tenantId.eq(StringUtils.EMPTY).or(qConfigInfo.tenantId.isNull()));
        } else {
            expression = expression.and(qConfigInfo.tenantId.eq(tenant));
        }
        ConfigInfoEntity configInfo = configInfoRepository.findOne(expression).orElse(null);
        return ConfigInfoMapStruct.INSTANCE.convertConfigInfo(configInfo);
    }
    
    @Override
    public ConfigInfo findConfigInfoAdvanceInfo(final String dataId, final String group, final String tenant,
            final Map<String, Object> configAdvanceInfo) {
        QConfigInfoEntity qConfigInfo = QConfigInfoEntity.configInfoEntity;
        final String appName = configAdvanceInfo == null ? null : (String) configAdvanceInfo.get("appName");
        final String configTags = configAdvanceInfo == null ? null : (String) configAdvanceInfo.get("config_tags");
        BooleanBuilder booleanBuilder = new BooleanBuilder();
        if (StringUtils.isNotBlank(dataId)) {
            booleanBuilder.and(qConfigInfo.dataId.eq(dataId));
        }
        if (StringUtils.isNotBlank(group)) {
            booleanBuilder.and(qConfigInfo.groupId.eq(group));
        }
        if (StringUtils.isBlank(tenant)) {
            booleanBuilder.and(qConfigInfo.tenantId.eq(StringUtils.EMPTY).or(qConfigInfo.tenantId.isNull()));
        } else {
            booleanBuilder.and(qConfigInfo.tenantId.eq(tenant));
        }
        if (StringUtils.isNotBlank(configTags)) {
            List<Long> configTagsRelationIds = new ArrayList<>();
            QConfigTagsRelationEntity qConfigTagsRelationEntity = QConfigTagsRelationEntity.configTagsRelationEntity;
            configTagsRelationRepository.findAll(qConfigTagsRelationEntity.tagName.in(configTags.split(",")))
                    .forEach(configTagsRelationEntity -> configTagsRelationIds.add(configTagsRelationEntity.getId()));
            booleanBuilder.and(qConfigInfo.id.in(configTagsRelationIds));
        } else {
            if (StringUtils.isNotBlank(appName)) {
                booleanBuilder.and(qConfigInfo.appName.eq(appName));
            }
        }
        ConfigInfoEntity configInfoEntity = configInfoRepository.findOne(booleanBuilder).orElse(null);
        return ConfigInfoMapStruct.INSTANCE.convertConfigInfo(configInfoEntity);
        
    }
    
    @Override
    public ConfigInfoBase findConfigInfoBase(final String dataId, final String group) {
        QConfigInfoEntity qConfigInfo = QConfigInfoEntity.configInfoEntity;
        return configInfoRepository.findOne(qConfigInfo.dataId.eq(dataId).and(qConfigInfo.groupId.eq(group)))
                .map(ConfigInfoEntityMapStruct.INSTANCE::convertConfigInfoBase)
                .orElse(null);
    }
    
    @Override
    public ConfigInfo findConfigInfo(long id) {
        ConfigInfoEntity configInfoEntity = configInfoRepository.findById(id).orElse(null);
        return ConfigInfoMapStruct.INSTANCE.convertConfigInfo(configInfoEntity);
    }
    
    @Override
    public ConfigInfo findConfigInfo(final String dataId, final String group, final String tenant) {
        BooleanBuilder booleanBuilder = new BooleanBuilder();
        QConfigInfoEntity qConfigInfo = QConfigInfoEntity.configInfoEntity;
        if (StringUtils.isNotBlank(dataId)) {
            booleanBuilder.and(qConfigInfo.dataId.eq(dataId));
        }
        if (StringUtils.isNotBlank(group)) {
            booleanBuilder.and(qConfigInfo.groupId.eq(group));
        }
        if (StringUtils.isBlank(tenant)) {
            booleanBuilder.and(qConfigInfo.tenantId.eq(StringUtils.EMPTY).or(qConfigInfo.tenantId.isNull()));
        } else {
            booleanBuilder.and(qConfigInfo.tenantId.eq(tenant));
        }
        ConfigInfoEntity result = configInfoRepository.findOne(booleanBuilder).orElse(null);
        return ConfigInfoMapStruct.INSTANCE.convertConfigInfo(result);
    }
    
    @Override
    public Page<ConfigInfo> findConfigInfoByDataId(final int pageNo, final int pageSize, final String dataId,
            final String tenant) {
        QConfigInfoEntity qConfigInfo = QConfigInfoEntity.configInfoEntity;
        BooleanExpression booleanExpression = qConfigInfo.dataId.eq(dataId);
        if (StringUtils.isBlank(tenant)) {
            booleanExpression = booleanExpression
                    .and(qConfigInfo.tenantId.eq(StringUtils.EMPTY).or(qConfigInfo.tenantId.isNull()));
        } else {
            booleanExpression = booleanExpression.and(qConfigInfo.tenantId.eq(tenant));
        }
        org.springframework.data.domain.Page<ConfigInfoEntity> sPage = configInfoRepository
                .findAll(booleanExpression, PageRequest.of(pageNo - 1, pageSize));
        Page<ConfigInfo> page = new Page<>();
        page.setPageNumber(pageNo);
        page.setPagesAvailable(sPage.getTotalPages());
        page.setPageItems(ConfigInfoMapStruct.INSTANCE.convertConfigInfoList2(sPage.getContent()));
        page.setTotalCount((int) sPage.getTotalElements());
        return page;
    }
    
    @Override
    public Page<ConfigInfo> findConfigInfoByDataIdAndApp(final int pageNo, final int pageSize, final String dataId,
            final String tenant, final String appName) {
        QConfigInfoEntity qConfigInfo = QConfigInfoEntity.configInfoEntity;
        BooleanExpression booleanExpression = qConfigInfo.dataId.eq(dataId).and(qConfigInfo.appName.eq(appName));
        if (StringUtils.isBlank(tenant)) {
            booleanExpression = booleanExpression
                    .and(qConfigInfo.tenantId.eq(StringUtils.EMPTY).or(qConfigInfo.tenantId.isNull()));
        } else {
            booleanExpression = booleanExpression.and(qConfigInfo.tenantId.eq(tenant));
        }
        org.springframework.data.domain.Page<ConfigInfoEntity> sPage = configInfoRepository
                .findAll(booleanExpression, PageRequest.of(pageNo - 1, pageSize));
        Page<ConfigInfo> page = new Page<>();
        page.setPageNumber(pageNo);
        page.setPagesAvailable(sPage.getTotalPages());
        page.setPageItems(ConfigInfoMapStruct.INSTANCE.convertConfigInfoList2(sPage.getContent()));
        page.setTotalCount((int) sPage.getTotalElements());
        return page;
    }
    
    @Override
    public Page<ConfigInfo> findConfigInfoByDataIdAndAdvance(final int pageNo, final int pageSize, final String dataId,
            final String tenant, final Map<String, Object> configAdvanceInfo) {
        QConfigInfoEntity qConfigInfo = QConfigInfoEntity.configInfoEntity;
        final String appName = configAdvanceInfo == null ? null : (String) configAdvanceInfo.get("appName");
        final String configTags = configAdvanceInfo == null ? null : (String) configAdvanceInfo.get("config_tags");
        BooleanBuilder booleanBuilder = new BooleanBuilder();
        if (StringUtils.isNotBlank(dataId)) {
            booleanBuilder.and(qConfigInfo.dataId.eq(dataId));
        }
        if (StringUtils.isBlank(tenant)) {
            booleanBuilder.and(qConfigInfo.tenantId.eq(StringUtils.EMPTY).or(qConfigInfo.tenantId.isNull()));
        } else {
            booleanBuilder.and(qConfigInfo.tenantId.eq(tenant));
        }
        if (StringUtils.isNotBlank(configTags)) {
            List<Long> configTagsRelationIds = new ArrayList<>();
            QConfigTagsRelationEntity qConfigTagsRelationEntity = QConfigTagsRelationEntity.configTagsRelationEntity;
            configTagsRelationRepository.findAll(qConfigTagsRelationEntity.tagName.in(configTags.split(",")))
                    .forEach(configTagsRelationEntity -> configTagsRelationIds.add(configTagsRelationEntity.getId()));
            booleanBuilder.and(qConfigInfo.id.in(configTagsRelationIds));
        }
        if (StringUtils.isNotBlank(appName)) {
            booleanBuilder.and(qConfigInfo.appName.eq(appName));
        }
        org.springframework.data.domain.Page<ConfigInfoEntity> sPage = configInfoRepository
                .findAll(booleanBuilder, PageRequest.of(pageNo - 1, pageSize));
        Page<ConfigInfo> page = new Page<>();
        page.setPageNumber(pageNo);
        page.setPagesAvailable(sPage.getTotalPages());
        page.setPageItems(ConfigInfoMapStruct.INSTANCE.convertConfigInfoList2(sPage.getContent()));
        page.setTotalCount((int) sPage.getTotalElements());
        return page;
    }
    
    @Override
    public Page<ConfigInfo> findConfigInfo4Page(final int pageNo, final int pageSize, final String dataId,
            final String group, final String tenant, final Map<String, Object> configAdvanceInfo) {
        final String appName = configAdvanceInfo == null ? null : (String) configAdvanceInfo.get("appName");
        final String configTags = configAdvanceInfo == null ? null : (String) configAdvanceInfo.get("config_tags");
        BooleanBuilder booleanBuilder = new BooleanBuilder();
        QConfigInfoEntity qConfigInfo = QConfigInfoEntity.configInfoEntity;
        buildConfigInfoCommonCondition(booleanBuilder, qConfigInfo, dataId, group, appName);
        if (StringUtils.isBlank(tenant)) {
            booleanBuilder.and(qConfigInfo.tenantId.eq(StringUtils.EMPTY).or(qConfigInfo.tenantId.isNull()));
        } else {
            booleanBuilder.and(qConfigInfo.tenantId.eq(tenant));
        }
        if (StringUtils.isNotBlank(configTags)) {
            List<Long> configTagsRelationIds = new ArrayList<>();
            QConfigTagsRelationEntity qConfigTagsRelationEntity = QConfigTagsRelationEntity.configTagsRelationEntity;
            configTagsRelationRepository.findAll(qConfigTagsRelationEntity.tagName.in(configTags.split(",")))
                    .forEach(configTagsRelationEntity -> configTagsRelationIds.add(configTagsRelationEntity.getId()));
            booleanBuilder.and(qConfigInfo.id.in(configTagsRelationIds));
        }
        org.springframework.data.domain.Page<ConfigInfoEntity> sPage = configInfoRepository
                .findAll(booleanBuilder, PageRequest.of(pageNo - 1, pageSize, Sort.by(Sort.Order.desc("gmtCreate"))));
        Page<ConfigInfo> page = new Page<>();
        page.setPageNumber(pageNo);
        page.setPagesAvailable(sPage.getTotalPages());
        page.setPageItems(ConfigInfoMapStruct.INSTANCE.convertConfigInfoList2(sPage.getContent()));
        page.setTotalCount((int) sPage.getTotalElements());
        return page;
    }
    
    private void buildConfigInfoCommonCondition(BooleanBuilder booleanBuilder, QConfigInfoEntity qConfigInfo,
            final String dataId, final String group, final String appName) {
        if (StringUtils.isNotBlank(dataId)) {
            booleanBuilder.and(qConfigInfo.dataId.like(generateLikeArgument(dataId)));
        }
        if (StringUtils.isNotBlank(group)) {
            booleanBuilder.and(qConfigInfo.groupId.like(generateLikeArgument(group)));
        }
        if (StringUtils.isNotBlank(appName)) {
            booleanBuilder.and(qConfigInfo.appName.like(generateLikeArgument(appName)));
        }
    }
    
    @Override
    public Page<ConfigInfoBase> findConfigInfoBaseByDataId(final int pageNo, final int pageSize, final String dataId) {
        QConfigInfoEntity qConfigInfo = QConfigInfoEntity.configInfoEntity;
        org.springframework.data.domain.Page<ConfigInfoEntity> sPage = configInfoRepository
                .findAll(qConfigInfo.dataId.eq(dataId).and(qConfigInfo.tenantId.eq(StringUtils.EMPTY)),
                        PageRequest.of(pageNo - 1, pageSize));
        
        Page<ConfigInfoBase> page = new Page<>();
        page.setPageNumber(pageNo);
        page.setPagesAvailable(sPage.getTotalPages());
        page.setPageItems(ConfigInfoEntityMapStruct.INSTANCE.convertConfigInfoBaseList(sPage.getContent()));
        page.setTotalCount((int) sPage.getTotalElements());
        return page;
    }
    
    @Override
    public Page<ConfigInfo> findConfigInfoByGroup(final int pageNo, final int pageSize, final String group,
            final String tenant) {
        QConfigInfoEntity qConfigInfo = QConfigInfoEntity.configInfoEntity;
        BooleanExpression booleanExpression = qConfigInfo.groupId.eq(group);
        if (StringUtils.isBlank(tenant)) {
            booleanExpression = booleanExpression
                    .and(qConfigInfo.tenantId.eq(StringUtils.EMPTY).or(qConfigInfo.tenantId.isNull()));
        } else {
            booleanExpression = booleanExpression.and(qConfigInfo.tenantId.eq(tenant));
        }
        org.springframework.data.domain.Page<ConfigInfoEntity> sPage = configInfoRepository
                .findAll(booleanExpression, PageRequest.of(pageNo - 1, pageSize));
        
        Page<ConfigInfo> page = new Page<>();
        page.setPageNumber(pageNo);
        page.setPagesAvailable(sPage.getTotalPages());
        page.setPageItems(ConfigInfoMapStruct.INSTANCE.convertConfigInfoList2(sPage.getContent()));
        page.setTotalCount((int) sPage.getTotalElements());
        return page;
    }
    
    @Override
    public Page<ConfigInfo> findConfigInfoByGroupAndApp(final int pageNo, final int pageSize, final String group,
            final String tenant, final String appName) {
        QConfigInfoEntity qConfigInfo = QConfigInfoEntity.configInfoEntity;
        BooleanExpression booleanExpression = qConfigInfo.groupId.eq(group).and(qConfigInfo.appName.eq(appName));
        if (StringUtils.isBlank(tenant)) {
            booleanExpression = booleanExpression
                    .and(qConfigInfo.tenantId.eq(StringUtils.EMPTY).or(qConfigInfo.tenantId.isNull()));
        } else {
            booleanExpression = booleanExpression.and(qConfigInfo.tenantId.eq(tenant));
        }
        org.springframework.data.domain.Page<ConfigInfoEntity> sPage = configInfoRepository
                .findAll(booleanExpression, PageRequest.of(pageNo - 1, pageSize));
        
        Page<ConfigInfo> page = new Page<>();
        page.setPageNumber(pageNo);
        page.setPagesAvailable(sPage.getTotalPages());
        page.setPageItems(ConfigInfoMapStruct.INSTANCE.convertConfigInfoList2(sPage.getContent()));
        page.setTotalCount((int) sPage.getTotalElements());
        return page;
    }
    
    @Override
    public Page<ConfigInfo> findConfigInfoByGroupAndAdvance(final int pageNo, final int pageSize, final String group,
            final String tenant, final Map<String, Object> configAdvanceInfo) {
        return null;
    }
    
    @Override
    public Page<ConfigInfo> findConfigInfoByApp(final int pageNo, final int pageSize, final String tenant,
            final String appName) {
        String tenantTmp = StringUtils.isBlank(tenant) ? StringUtils.EMPTY : tenant;
        QConfigInfoEntity qConfigInfo = QConfigInfoEntity.configInfoEntity;
        org.springframework.data.domain.Page<ConfigInfoEntity> sPage = configInfoRepository.findAll(
                qConfigInfo.tenantId.like(generateLikeArgument(tenantTmp)).and(qConfigInfo.appName.eq(appName)),
                PageRequest.of(pageNo - 1, pageSize));
        
        Page<ConfigInfo> page = new Page<>();
        page.setPageNumber(pageNo);
        page.setPagesAvailable(sPage.getTotalPages());
        page.setPageItems(ConfigInfoMapStruct.INSTANCE.convertConfigInfoList2(sPage.getContent()));
        page.setTotalCount((int) sPage.getTotalElements());
        return page;
    }
    
    @Override
    public Page<ConfigInfo> findConfigInfoByAdvance(final int pageNo, final int pageSize, final String tenant,
            final Map<String, Object> configAdvanceInfo) {
        QConfigInfoEntity qConfigInfo = QConfigInfoEntity.configInfoEntity;
        final String appName = configAdvanceInfo == null ? null : (String) configAdvanceInfo.get("appName");
        final String configTags = configAdvanceInfo == null ? null : (String) configAdvanceInfo.get("config_tags");
        BooleanBuilder booleanBuilder = new BooleanBuilder();
        if (StringUtils.isBlank(tenant)) {
            booleanBuilder.and(qConfigInfo.tenantId.eq(StringUtils.EMPTY).or(qConfigInfo.tenantId.isNull()));
        } else {
            booleanBuilder.and(qConfigInfo.tenantId.eq(tenant));
        }
        if (StringUtils.isNotBlank(configTags)) {
            List<Long> configTagsRelationIds = new ArrayList<>();
            QConfigTagsRelationEntity qConfigTagsRelationEntity = QConfigTagsRelationEntity.configTagsRelationEntity;
            configTagsRelationRepository.findAll(qConfigTagsRelationEntity.tagName.in(configTags.split(",")))
                    .forEach(configTagsRelationEntity -> configTagsRelationIds.add(configTagsRelationEntity.getId()));
            booleanBuilder.and(qConfigInfo.id.in(configTagsRelationIds));
        }
        if (StringUtils.isNotBlank(appName)) {
            booleanBuilder.and(qConfigInfo.appName.eq(appName));
        }
        org.springframework.data.domain.Page<ConfigInfoEntity> sPage = configInfoRepository
                .findAll(booleanBuilder, PageRequest.of(pageNo - 1, pageSize));
        Page<ConfigInfo> page = new Page<>();
        page.setPageNumber(pageNo);
        page.setPagesAvailable(sPage.getTotalPages());
        page.setPageItems(ConfigInfoMapStruct.INSTANCE.convertConfigInfoList2(sPage.getContent()));
        page.setTotalCount((int) sPage.getTotalElements());
        return page;
    }
    
    @Override
    
    public Page<ConfigInfoBase> findConfigInfoBaseByGroup(final int pageNo, final int pageSize, final String group) {
        QConfigInfoEntity qConfigInfo = QConfigInfoEntity.configInfoEntity;
        org.springframework.data.domain.Page<ConfigInfoEntity> sPage = configInfoRepository
                .findAll(qConfigInfo.groupId.eq(group).and(qConfigInfo.tenantId.eq(StringUtils.EMPTY)),
                        PageRequest.of(pageNo - 1, pageSize));
        
        Page<ConfigInfoBase> page = new Page<>();
        page.setPageNumber(pageNo);
        page.setPagesAvailable(sPage.getTotalPages());
        page.setPageItems(ConfigInfoEntityMapStruct.INSTANCE.convertConfigInfoBaseList(sPage.getContent()));
        page.setTotalCount((int) sPage.getTotalElements());
        return page;
    }
    
    @Override
    public int configInfoCount() {
        Long result = configInfoRepository.count();
        if (result == null) {
            throw new IllegalArgumentException("configInfoCount error");
        }
        return result.intValue();
    }
    
    @Override
    public int configInfoCount(String tenant) {
        QConfigInfoEntity qConfigInfo = QConfigInfoEntity.configInfoEntity;
        Long result = configInfoRepository.count(qConfigInfo.tenantId.like(tenant));
        if (result == null) {
            throw new IllegalArgumentException("configInfoCount error");
        }
        return result.intValue();
    }
    
    @Override
    public int configInfoBetaCount() {
        Long result = configInfoBetaRepository.count();
        if (result == null) {
            throw new IllegalArgumentException("configInfoBetaCount error");
        }
        return result.intValue();
    }
    
    @Override
    public int configInfoTagCount() {
        Long result = configInfoTagRepository.count();
        if (result == null) {
            throw new IllegalArgumentException("configInfoBetaCount error");
        }
        return result.intValue();
    }
    
    @Override
    public List<String> getTenantIdList(int pageNo, int pageSize) {
        Specification<ConfigInfoEntity> specification = new Specification<ConfigInfoEntity>() {
            @Override
            public Predicate toPredicate(Root<ConfigInfoEntity> root, CriteriaQuery<?> query,
                    CriteriaBuilder criteriaBuilder) {
                return query.groupBy(root.get("tenantId")).getRestriction();
            }
        };
        org.springframework.data.domain.Page<ConfigInfoEntity> page = configInfoRepository
                .findAll(specification, PageRequest.of(pageNo - 1, pageSize));
        return page.getContent().stream().map(ConfigInfoEntity::getGroupId).collect(Collectors.toList());
    }
    
    @Override
    public List<String> getGroupIdList(int pageNo, int pageSize) {
        Specification<ConfigInfoEntity> specification = new Specification<ConfigInfoEntity>() {
            @Override
            public Predicate toPredicate(Root<ConfigInfoEntity> root, CriteriaQuery<?> query,
                    CriteriaBuilder criteriaBuilder) {
                return query.groupBy(root.get("groupId")).getRestriction();
            }
        };
        org.springframework.data.domain.Page<ConfigInfoEntity> page = configInfoRepository
                .findAll(specification, PageRequest.of(pageNo - 1, pageSize));
        return page.getContent().stream().map(ConfigInfoEntity::getGroupId).collect(Collectors.toList());
    }
    
    @Override
    public int aggrConfigInfoCount(String dataId, String group, String tenant) {
        QConfigInfoEntity qConfigInfo = QConfigInfoEntity.configInfoEntity;
        BooleanExpression booleanExpression = qConfigInfo.dataId.eq(dataId).and(qConfigInfo.groupId.eq(group));
        if (StringUtils.isBlank(tenant)) {
            booleanExpression = booleanExpression
                    .and(qConfigInfo.tenantId.eq(StringUtils.EMPTY).or(qConfigInfo.tenantId.isNull()));
        } else {
            booleanExpression = booleanExpression.and(qConfigInfo.tenantId.eq(tenant));
        }
        Long result = configInfoRepository.count(booleanExpression);
        if (result == null) {
            throw new IllegalArgumentException("aggrConfigInfoCount error");
        }
        return result.intValue();
    }
    
    @Override
    public int aggrConfigInfoCount(String dataId, String group, String tenant, List<String> datumIds, boolean isIn) {
        if (datumIds == null || datumIds.isEmpty()) {
            return 0;
        }
        QConfigInfoAggrEntity qConfigInfo = QConfigInfoAggrEntity.configInfoAggrEntity;
        BooleanExpression predicate = qConfigInfo.dataId.eq(dataId).and(qConfigInfo.groupId.eq(group));
        if (StringUtils.isBlank(tenant)) {
            predicate = predicate.and(qConfigInfo.tenantId.eq(StringUtils.EMPTY).or(qConfigInfo.tenantId.isNull()));
        } else {
            predicate = predicate.and(qConfigInfo.tenantId.eq(tenant));
        }
        if (isIn) {
            predicate = predicate.and(qConfigInfo.datumId.in(datumIds));
        } else {
            predicate = predicate.and(qConfigInfo.datumId.notIn(datumIds));
        }
        return (int) configInfoAggrRepository.count(predicate);
    }
    
    @Override
    public int aggrConfigInfoCountIn(String dataId, String group, String tenant, List<String> datumIds) {
        return aggrConfigInfoCount(dataId, group, tenant, datumIds, true);
    }
    
    @Override
    public int aggrConfigInfoCountNotIn(String dataId, String group, String tenant, List<String> datumIds) {
        return aggrConfigInfoCount(dataId, group, tenant, datumIds, false);
    }
    
    @Override
    public Page<ConfigInfo> findAllConfigInfo(final int pageNo, final int pageSize, final String tenant) {
        QConfigInfoEntity qConfigInfo = QConfigInfoEntity.configInfoEntity;
        String tenantTmp = StringUtils.isBlank(tenant) ? StringUtils.EMPTY : tenant;
        org.springframework.data.domain.Page<ConfigInfoEntity> sPage = configInfoRepository
                .findAll(qConfigInfo.tenantId.like(generateLikeArgument(tenantTmp)),
                        PageRequest.of(pageNo - 1, pageSize, Sort.by(Sort.Order.by("id"))));
        
        Page<ConfigInfo> page = new Page<>();
        page.setPageNumber(pageNo);
        page.setPagesAvailable(sPage.getTotalPages());
        page.setPageItems(ConfigInfoMapStruct.INSTANCE.convertConfigInfoList2(sPage.getContent()));
        page.setTotalCount((int) sPage.getTotalElements());
        return page;
    }
    
    @Override
    public Page<ConfigKey> findAllConfigKey(final int pageNo, final int pageSize, final String tenant) {
        QConfigInfoEntity qConfigInfo = QConfigInfoEntity.configInfoEntity;
        final String tenantTmp = StringUtils.isBlank(tenant) ? StringUtils.EMPTY : tenant;
        org.springframework.data.domain.Page<ConfigInfoEntity> sPage = configInfoRepository
                .findAll(qConfigInfo.tenantId.like(generateLikeArgument(tenantTmp)),
                        PageRequest.of(pageNo - 1, pageSize, Sort.by(Sort.Order.by("id"))));
        
        Page<ConfigKey> page = new Page<>();
        page.setPageNumber(pageNo);
        page.setPagesAvailable(sPage.getTotalPages());
        page.setPageItems(ConfigKeyMapStruct.INSTANCE.convertConfigKeyList(sPage.getContent()));
        page.setTotalCount((int) sPage.getTotalElements());
        return page;
    }
    
    @Override
    @Deprecated
    public Page<ConfigInfoBase> findAllConfigInfoBase(final int pageNo, final int pageSize) {
        org.springframework.data.domain.Page<ConfigInfoEntity> sPage = configInfoRepository
                .findAll(PageRequest.of(pageNo - 1, pageSize, Sort.by(Sort.Order.by("id"))));
        Page<ConfigInfoBase> page = new Page<>();
        page.setPageNumber(pageNo);
        page.setPagesAvailable(sPage.getTotalPages());
        page.setPageItems(ConfigInfoEntityMapStruct.INSTANCE.convertConfigInfoBaseList(sPage.getContent()));
        page.setTotalCount((int) sPage.getTotalElements());
        return page;
    }
    
    @Override
    public Page<ConfigInfoWrapper> findAllConfigInfoForDumpAll(final int pageNo, final int pageSize) {
        org.springframework.data.domain.Page<ConfigInfoEntity> sPage = configInfoRepository
                .findAll(PageRequest.of(pageNo - 1, pageSize, Sort.by(Sort.Order.by("id"))));
        Page<ConfigInfoWrapper> page = new Page<>();
        page.setPageNumber(pageNo);
        page.setPagesAvailable(sPage.getTotalPages());
        page.setPageItems(ConfigInfoWrapperMapStruct.INSTANCE.convertConfigInfoWrapperList(sPage.getContent()));
        page.setTotalCount((int) sPage.getTotalElements());
        return page;
    }
    
    @Override
    public Page<ConfigInfoWrapper> findAllConfigInfoFragment(final long lastMaxId, final int pageSize) {
        QConfigInfoEntity qConfigInfo = QConfigInfoEntity.configInfoEntity;
        org.springframework.data.domain.Page<ConfigInfoEntity> sPage = configInfoRepository
                .findAll(qConfigInfo.id.gt(lastMaxId), PageRequest.of(0, pageSize, Sort.by(Sort.Order.asc("id"))));
        
        Page<ConfigInfoWrapper> page = new Page<>();
        page.setPageNumber(sPage.getNumber());
        page.setPagesAvailable(sPage.getTotalPages());
        page.setPageItems(ConfigInfoWrapperMapStruct.INSTANCE.convertConfigInfoWrapperList(sPage.getContent()));
        page.setTotalCount((int) sPage.getTotalElements());
        return page;
    }
    
    @Override
    public Page<ConfigInfoBetaWrapper> findAllConfigInfoBetaForDumpAll(final int pageNo, final int pageSize) {
        org.springframework.data.domain.Page<ConfigInfoBetaEntity> sPage = configInfoBetaRepository
                .findAll(null, PageRequest.of(pageNo - 1, pageSize));
        Page<ConfigInfoBetaWrapper> page = new Page<>();
        page.setPageNumber(pageNo);
        page.setPagesAvailable(sPage.getTotalPages());
        page.setPageItems(ConfigInfoBetaWrapperMapStruct.INSTANCE.convertConfigInfoBetaWrapperList(sPage.getContent()));
        page.setTotalCount((int) sPage.getTotalElements());
        return page;
    }
    
    @Override
    public Page<ConfigInfoTagWrapper> findAllConfigInfoTagForDumpAll(final int pageNo, final int pageSize) {
        org.springframework.data.domain.Page<ConfigInfoTagEntity> sPage = configInfoTagRepository
                .findAll(null, PageRequest.of(pageNo - 1, pageSize));
        Page<ConfigInfoTagWrapper> page = new Page<>();
        page.setPageNumber(pageNo);
        page.setPagesAvailable(sPage.getTotalPages());
        page.setPageItems(ConfigInfoTagWrapperMapStruct.INSTANCE.convertConfigInfoTagWrapperList(sPage.getContent()));
        page.setTotalCount((int) sPage.getTotalElements());
        return page;
    }
    
    @Override
    public List<ConfigInfo> findConfigInfoByBatch(final List<String> dataIds, final String group, final String tenant,
            int subQueryLimit) {
        // if dataids empty return empty list
        if (CollectionUtils.isEmpty(dataIds)) {
            return Collections.emptyList();
        }
        
        // Volume query upper limit
        // The number of in is within 100. The shorter the SQL statement, the better
        if (subQueryLimit > QUERY_LIMIT_SIZE) {
            subQueryLimit = 50;
        }
        List<ConfigInfo> result = new ArrayList<ConfigInfo>(dataIds.size());
        
        QConfigInfoEntity qConfigInfoEntity = QConfigInfoEntity.configInfoEntity;
        for (int i = 0; i < dataIds.size(); i += subQueryLimit) {
            // dataids
            List<String> params = new ArrayList<String>(
                    dataIds.subList(i, Math.min(i + subQueryLimit, dataIds.size())));
            BooleanExpression expression = qConfigInfoEntity.groupId.eq(group)
                    .and(qConfigInfoEntity.dataId.in(params));
            if (StringUtils.isBlank(tenant)) {
                expression = expression
                        .and(qConfigInfoEntity.tenantId.eq(StringUtils.EMPTY).or(qConfigInfoEntity.tenantId.isNull()));
            } else {
                expression = expression.and(qConfigInfoEntity.tenantId.eq(tenant));
            }
            Iterable<ConfigInfoEntity> iterable = configInfoRepository.findAll(expression);
            List<ConfigInfo> r = ConfigInfoMapStruct.INSTANCE.convertConfigInfoList2((List<ConfigInfoEntity>) iterable);
            
            // assert not null
            if (r != null && r.size() > 0) {
                result.addAll(r);
            }
        }
        return result;
    }
    
    @Override
    public Page<ConfigInfo> findConfigInfoLike(final int pageNo, final int pageSize, final String dataId,
            final String group, final String tenant, final String appName, final String content) {
        String tenantTmp = StringUtils.isBlank(tenant) ? StringUtils.EMPTY : tenant;
        if (StringUtils.isBlank(dataId) && StringUtils.isBlank(group)) {
            if (StringUtils.isBlank(appName)) {
                return this.findAllConfigInfo(pageNo, pageSize, tenantTmp);
            } else {
                return this.findConfigInfoByApp(pageNo, pageSize, tenantTmp, appName);
            }
        }
        QConfigInfoEntity qConfigInfoEntity = QConfigInfoEntity.configInfoEntity;
        BooleanBuilder booleanBuilder = new BooleanBuilder();
        
        if (!StringUtils.isBlank(dataId)) {
            booleanBuilder.and(qConfigInfoEntity.dataId.like(generateLikeArgument(dataId)));
        }
        if (!StringUtils.isBlank(group)) {
            booleanBuilder.and(qConfigInfoEntity.groupId.like(generateLikeArgument(group)));
        }
        booleanBuilder.and(qConfigInfoEntity.tenantId.like(generateLikeArgument(tenantTmp)));
        if (!StringUtils.isBlank(appName)) {
            booleanBuilder.and(qConfigInfoEntity.appName.eq(appName));
        }
        if (!StringUtils.isBlank(content)) {
            booleanBuilder.and(qConfigInfoEntity.content.like(generateLikeArgument(content)));
        }
        org.springframework.data.domain.Page<ConfigInfoEntity> sPage = configInfoRepository
                .findAll(booleanBuilder, PageRequest.of(pageNo - 1, pageSize));
        Page<ConfigInfo> page = new Page<>();
        page.setPageNumber(pageNo);
        page.setPagesAvailable(sPage.getTotalPages());
        page.setPageItems(ConfigInfoMapStruct.INSTANCE.convertConfigInfoList2(sPage.getContent()));
        page.setTotalCount((int) sPage.getTotalElements());
        return page;
    }
    
    @Override
    public Page<ConfigInfo> findConfigInfoLike(final int pageNo, final int pageSize, final ConfigKey[] configKeys,
            final boolean blacklist) {
        return null;
    }
    
    @Override
    public Page<ConfigInfo> findConfigInfoLike4Page(final int pageNo, final int pageSize, final String dataId,
            final String group, final String tenant, final Map<String, Object> configAdvanceInfo) {
        String tenantTmp = StringUtils.isBlank(tenant) ? StringUtils.EMPTY : tenant;
        final String appName = configAdvanceInfo == null ? null : (String) configAdvanceInfo.get("appName");
        final String content = configAdvanceInfo == null ? null : (String) configAdvanceInfo.get("content");
        final String configTags = configAdvanceInfo == null ? null : (String) configAdvanceInfo.get("config_tags");
        BooleanBuilder booleanBuilder = new BooleanBuilder();
        QConfigInfoEntity qConfigInfo = QConfigInfoEntity.configInfoEntity;
        buildConfigInfoCommonCondition(booleanBuilder, qConfigInfo, dataId, group, appName);
        booleanBuilder.and(qConfigInfo.tenantId.like(generateLikeArgument(tenantTmp)));
        if (StringUtils.isNotBlank(content)) {
            booleanBuilder.and(qConfigInfo.content.like(generateLikeArgument(content)));
        }
        if (StringUtils.isNotBlank(configTags)) {
            List<Long> configTagsRelationIds = new ArrayList<>();
            QConfigTagsRelationEntity qConfigTagsRelationEntity = QConfigTagsRelationEntity.configTagsRelationEntity;
            configTagsRelationRepository.findAll(qConfigTagsRelationEntity.tagName.in(configTags.split(",")))
                    .forEach(configTagsRelationEntity -> configTagsRelationIds.add(configTagsRelationEntity.getId()));
            booleanBuilder.and(qConfigInfo.id.in(configTagsRelationIds));
        }
        org.springframework.data.domain.Page<ConfigInfoEntity> sPage = configInfoRepository
                .findAll(booleanBuilder, PageRequest.of(pageNo - 1, pageSize, Sort.by(Sort.Order.desc("gmtCreate"))));
        Page<ConfigInfo> page = new Page<>();
        page.setPageNumber(pageNo);
        page.setPagesAvailable(sPage.getTotalPages());
        page.setPageItems(ConfigInfoMapStruct.INSTANCE.convertConfigInfoList2(sPage.getContent()));
        page.setTotalCount((int) sPage.getTotalElements());
        return page;
    }
    
    @Override
    public Page<ConfigInfoBase> findConfigInfoBaseLike(final int pageNo, final int pageSize, final String dataId,
            final String group, final String content) throws IOException {
        if (StringUtils.isBlank(dataId) && StringUtils.isBlank(group)) {
            throw new IOException("invalid param");
        }
        
        BooleanBuilder booleanBuilder = new BooleanBuilder();
        QConfigInfoEntity qConfigInfoEntity = QConfigInfoEntity.configInfoEntity;
        booleanBuilder.and(qConfigInfoEntity.tenantId.eq(Strings.EMPTY));
        if (!StringUtils.isBlank(dataId)) {
            booleanBuilder.and(qConfigInfoEntity.dataId.like(generateLikeArgument(dataId)));
        }
        if (!StringUtils.isBlank(group)) {
            booleanBuilder.and(qConfigInfoEntity.groupId.like(generateLikeArgument(group)));
        }
        if (!StringUtils.isBlank(content)) {
            booleanBuilder.and(qConfigInfoEntity.content.like(generateLikeArgument(content)));
        }
        
        org.springframework.data.domain.Page<ConfigInfoEntity> sPage = configInfoRepository.findAll(booleanBuilder,
                PageRequest.of(pageNo - 1, pageSize, Sort.by(Sort.Order.desc("gmtCreate"))));
        Page<ConfigInfoBase> page = new Page<>();
        page.setPageNumber(pageNo);
        page.setPagesAvailable(sPage.getTotalPages());
        page.setPageItems(ConfigInfoEntityMapStruct.INSTANCE.convertConfigInfoBaseList(sPage.getContent()));
        page.setTotalCount((int) sPage.getTotalElements());
        return page;
    }
    
    @Override
    public ConfigInfoAggr findSingleConfigInfoAggr(String dataId, String group, String tenant, String datumId) {
        String tenantTmp = StringUtils.isBlank(tenant) ? StringUtils.EMPTY : tenant;
        QConfigInfoAggrEntity qConfigInfoAggr = QConfigInfoAggrEntity.configInfoAggrEntity;
        BooleanBuilder booleanBuilder = new BooleanBuilder();
        if (StringUtils.isNotBlank(dataId)) {
            booleanBuilder.and(qConfigInfoAggr.dataId.eq(dataId));
        }
        if (StringUtils.isNotBlank(group)) {
            booleanBuilder.and(qConfigInfoAggr.groupId.eq(group));
        }
        if (StringUtils.isBlank(tenant)) {
            booleanBuilder.and(qConfigInfoAggr.tenantId.eq(StringUtils.EMPTY).or(qConfigInfoAggr.tenantId.isNull()));
        } else {
            booleanBuilder.and(qConfigInfoAggr.tenantId.eq(tenant));
        }
        if (StringUtils.isNotBlank(datumId)) {
            booleanBuilder.and(qConfigInfoAggr.datumId.eq(datumId));
        }
        
        ConfigInfoAggrEntity configInfoAggrEntity = configInfoAggrRepository.findOne(booleanBuilder).orElse(null);
        return ConfigInfoAggrMapStruct.INSTANCE.convertConfigInfoAggr(configInfoAggrEntity);
    }
    
    @Override
    public List<ConfigInfoAggr> findConfigInfoAggr(String dataId, String group, String tenant) {
        return null;
    }
    
    @Override
    public Page<ConfigInfoAggr> findConfigInfoAggrByPage(String dataId, String group, String tenant, final int pageNo,
            final int pageSize) {
        QConfigInfoAggrEntity qConfigInfoAggr = QConfigInfoAggrEntity.configInfoAggrEntity;
        BooleanExpression booleanExpression = qConfigInfoAggr.dataId.eq(dataId).and(qConfigInfoAggr.groupId.eq(group));
        if (StringUtils.isBlank(tenant)) {
            booleanExpression = booleanExpression
                    .and(qConfigInfoAggr.tenantId.eq(StringUtils.EMPTY).or(qConfigInfoAggr.tenantId.isNull()));
        } else {
            booleanExpression = booleanExpression.and(qConfigInfoAggr.tenantId.eq(tenant));
        }
        org.springframework.data.domain.Page<ConfigInfoAggrEntity> sPage = configInfoAggrRepository
                .findAll(booleanExpression, PageRequest.of(pageNo - 1, pageSize, Sort.by(Sort.Order.by("datumId"))));
        Page<ConfigInfoAggr> page = new Page<>();
        page.setPageNumber(pageNo);
        page.setPagesAvailable(sPage.getTotalPages());
        page.setPageItems(ConfigInfoAggrMapStruct.INSTANCE.convertConfigInfoAggrList(sPage.getContent()));
        page.setTotalCount((int) sPage.getTotalElements());
        return page;
    }
    
    @Override
    public Page<ConfigInfoAggr> findConfigInfoAggrLike(final int pageNo, final int pageSize, ConfigKey[] configKeys,
            boolean blacklist) {
        
        return null;
    }
    
    @Override
    public List<ConfigInfoChanged> findAllAggrGroup() {
        List<ConfigInfoAggrEntity> list = configInfoAggrRepository.findAllAggrGroup();
        return ConfigInfoChangedMapStruct.INSTANCE.convertConfigInfoChangedList(list);
    }
    
    @Override
    public List<String> findDatumIdByContent(String dataId, String groupId, String content) {
        return null;
    }
    
    @Override
    public List<ConfigInfoWrapper> findChangeConfig(final Timestamp startTime, final Timestamp endTime) {
        QConfigInfoEntity qConfigInfo = QConfigInfoEntity.configInfoEntity;
        Iterable<ConfigInfoEntity> iterable = configInfoRepository
                .findAll(qConfigInfo.gmtModified.goe(startTime).and(qConfigInfo.gmtModified.loe(endTime)));
        return ConfigInfoWrapperMapStruct.INSTANCE.convertConfigInfoWrapperList((List<ConfigInfoEntity>) iterable);
    }
    
    @Override
    public Page<ConfigInfoWrapper> findChangeConfig(final String dataId, final String group, final String tenant,
            final String appName, final Timestamp startTime, final Timestamp endTime, final int pageNo,
            final int pageSize, final long lastMaxId) {
        return null;
    }
    
    @Override
    public List<ConfigInfo> findDeletedConfig(final Timestamp startTime, final Timestamp endTime) {
        QHisConfigInfoEntity qHisConfigInfo = QHisConfigInfoEntity.hisConfigInfoEntity;
        Iterable<HisConfigInfoEntity> iterable = hisConfigInfoRepository.findAll(
                qHisConfigInfo.opType.eq("D").and(qHisConfigInfo.gmtModified.goe(startTime))
                        .and(qHisConfigInfo.gmtModified.loe(endTime)));
        return ConfigInfoMapStruct.INSTANCE.convertConfigInfoList((List<HisConfigInfoEntity>) iterable);
    }
    
    @Override
    public long addConfigInfoAtomic(final long configId, final String srcIp, final String srcUser,
            final ConfigInfo configInfo, final Timestamp time, Map<String, Object> configAdvanceInfo) {
        final String desc = configAdvanceInfo == null ? null : (String) configAdvanceInfo.get("desc");
        final String use = configAdvanceInfo == null ? null : (String) configAdvanceInfo.get("use");
        final String effect = configAdvanceInfo == null ? null : (String) configAdvanceInfo.get("effect");
        final String type = configAdvanceInfo == null ? null : (String) configAdvanceInfo.get("type");
        final String schema = configAdvanceInfo == null ? null : (String) configAdvanceInfo.get("schema");
        final String md5Tmp = MD5Utils.md5Hex(configInfo.getContent(), com.alibaba.nacos.api.common.Constants.ENCODE);
        ConfigInfoEntity configInfoEntity = ConfigInfoEntityMapStruct.INSTANCE.convertConfigInfoEntity(configInfo);
        configInfoEntity.setCDesc(desc);
        configInfoEntity.setCUse(use);
        configInfoEntity.setEffect(effect);
        configInfoEntity.setType(type);
        configInfoEntity.setCSchema(schema);
        configInfoEntity.setMd5(md5Tmp);
        configInfoEntity.setGmtCreate(time);
        configInfoEntity.setGmtModified(time);
        
        try {
            return configInfoRepository.save(configInfoEntity).getId();
        } catch (Exception e) {
            throw e;
        }
    }
    
    @Override
    public void addConfigTagsRelation(long configId, String configTags, String dataId, String group, String tenant) {
        if (StringUtils.isNotBlank(configTags)) {
            String[] tagArr = configTags.split(",");
            for (int i = 0; i < tagArr.length; i++) {
                addConfigTagRelationAtomic(configId, tagArr[i], dataId, group, tenant);
            }
        }
    }
    
    @Override
    public void addConfigTagRelationAtomic(long configId, String tagName, String dataId, String group, String tenant) {
        ConfigTagsRelationEntity configTagsRelation = new ConfigTagsRelationEntity();
        configTagsRelation.setId(configId);
        configTagsRelation.setTagName(tagName);
        configTagsRelation.setDataId(dataId);
        configTagsRelation.setGroupId(group);
        configTagsRelation.setTenantId(tenant);
        configTagsRelationRepository.save(configTagsRelation);
    }
    
    @Override
    public void removeTagByIdAtomic(long id) {
        configTagsRelationRepository.findById(id).ifPresent(s -> configTagsRelationRepository.delete(s));
    }
    
    @Override
    public List<String> getConfigTagsByTenant(String tenant) {
        return null;
    }
    
    @Override
    public List<String> selectTagByConfig(String dataId, String group, String tenant) {
        BooleanBuilder booleanBuilder = new BooleanBuilder();
        QConfigTagsRelationEntity qConfigTagsRelation = QConfigTagsRelationEntity.configTagsRelationEntity;
        if (StringUtils.isNotBlank(dataId)) {
            booleanBuilder.and(qConfigTagsRelation.dataId.eq(dataId));
        }
        if (StringUtils.isNotBlank(group)) {
            booleanBuilder.and(qConfigTagsRelation.groupId.eq(group));
        }
        if (StringUtils.isNotBlank(tenant)) {
            booleanBuilder.and(qConfigTagsRelation.tenantId.eq(tenant));
        }
        Iterable<ConfigTagsRelationEntity> iterable = configTagsRelationRepository.findAll(booleanBuilder);
        List<String> result = new ArrayList<>();
        iterable.forEach(s -> result.add(s.getTagName()));
        return result;
    }
    
    @Override
    public void removeConfigInfoAtomic(final String dataId, final String group, final String tenant, final String srcIp,
            final String srcUser) {
        BooleanBuilder booleanBuilder = new BooleanBuilder();
        QConfigInfoEntity qConfigInfo = QConfigInfoEntity.configInfoEntity;
        booleanBuilder.and(qConfigInfo.dataId.eq(dataId));
        booleanBuilder.and(qConfigInfo.groupId.eq(group));
        if (StringUtils.isBlank(tenant)) {
            booleanBuilder.and(qConfigInfo.tenantId.eq(StringUtils.EMPTY).or(qConfigInfo.tenantId.isNull()));
        } else {
            booleanBuilder.and(qConfigInfo.tenantId.eq(tenant));
        }
        Iterable<ConfigInfoEntity> configInfos = configInfoRepository.findAll(booleanBuilder);
        configInfos.forEach(s -> configInfoRepository.delete(s));
    }
    
    @Override
    public void removeConfigInfoByIdsAtomic(final String ids) {
        if (StringUtils.isBlank(ids)) {
            return;
        }
        if (StringUtils.isBlank(ids)) {
            return;
        }
        List<Long> paramList = new ArrayList<>();
        String[] tagArr = ids.split(",");
        for (int i = 0; i < tagArr.length; i++) {
            paramList.add(Long.parseLong(tagArr[i]));
        }
        tjt.execute(new TransactionCallback<Boolean>() {
            @Override
            public Boolean doInTransaction(TransactionStatus transactionStatus) {
                try {
                    for (Long id : paramList) {
                        configInfoRepository.deleteById(id);
                    }
                } catch (Exception e) {
                    transactionStatus.setRollbackOnly();
                    throw e;
                }
                return Boolean.TRUE;
            }
        });
    }
    
    @Override
    public void removeConfigInfoTag(final String dataId, final String group, final String tenant, final String tag,
            final String srcIp, final String srcUser) {
        final String tagTmp = StringUtils.isBlank(tag) ? StringUtils.EMPTY : tag;
        BooleanBuilder booleanBuilder = new BooleanBuilder();
        QConfigInfoTagEntity qConfigInfoTag = QConfigInfoTagEntity.configInfoTagEntity;
        if (StringUtils.isNotBlank(dataId)) {
            booleanBuilder.and(qConfigInfoTag.dataId.eq(dataId));
        }
        if (StringUtils.isNotBlank(group)) {
            booleanBuilder.and(qConfigInfoTag.groupId.eq(group));
        }
        if (StringUtils.isBlank(tenant)) {
            booleanBuilder.and(qConfigInfoTag.tenantId.eq(StringUtils.EMPTY).or(qConfigInfoTag.tenantId.isNull()));
        } else {
            booleanBuilder.and(qConfigInfoTag.tenantId.eq(tenant));
        }
        booleanBuilder.and(qConfigInfoTag.tagId.eq(tagTmp));
        tjt.execute(new TransactionCallback<Object>() {
            @Override
            public Boolean doInTransaction(TransactionStatus transactionStatus) {
                try {
                    Iterable<ConfigInfoTagEntity> configInfoTags = configInfoTagRepository.findAll(booleanBuilder);
                    configInfoTags.forEach(s -> configInfoTagRepository.delete(s));
                } catch (Exception e) {
                    transactionStatus.setRollbackOnly();
                    throw e;
                }
                return Boolean.TRUE;
            }
        });
    }
    
    @Override
    public void updateConfigInfoAtomic(final ConfigInfo configInfo, final String srcIp, final String srcUser,
            final Timestamp time, Map<String, Object> configAdvanceInfo) {
        ConfigInfoEntity configInfoEntity = ConfigInfoEntityMapStruct.INSTANCE.convertConfigInfoEntity(configInfo);
        final String md5Tmp = MD5Utils.md5Hex(configInfo.getContent(), com.alibaba.nacos.api.common.Constants.ENCODE);
        String desc = configAdvanceInfo == null ? null : (String) configAdvanceInfo.get("desc");
        String use = configAdvanceInfo == null ? null : (String) configAdvanceInfo.get("use");
        String effect = configAdvanceInfo == null ? null : (String) configAdvanceInfo.get("effect");
        String type = configAdvanceInfo == null ? null : (String) configAdvanceInfo.get("type");
        String schema = configAdvanceInfo == null ? null : (String) configAdvanceInfo.get("schema");
        
        configInfoEntity.setMd5(md5Tmp);
        configInfoEntity.setCDesc(desc);
        configInfoEntity.setCUse(use);
        configInfoEntity.setEffect(effect);
        configInfoEntity.setType(type);
        configInfoEntity.setCSchema(schema);
        configInfoEntity.setGmtModified(time);
        configInfoRepository.save(configInfoEntity);
    }
    
    @Override
    public List<ConfigInfo> findConfigInfosByIds(final String ids) {
        if (StringUtils.isBlank(ids)) {
            return null;
        }
        List<Long> paramList = new ArrayList<>();
        String[] tagArr = ids.split(",");
        for (int i = 0; i < tagArr.length; i++) {
            paramList.add(Long.parseLong(tagArr[i]));
        }
        QConfigInfoEntity qConfigInfo = QConfigInfoEntity.configInfoEntity;
        List<ConfigInfoEntity> list = (List<ConfigInfoEntity>) configInfoRepository
                .findAll(qConfigInfo.id.in(paramList));
        return ConfigInfoMapStruct.INSTANCE.convertConfigInfoList2(list);
    }
    
    @Override
    public ConfigAdvanceInfo findConfigAdvanceInfo(final String dataId, final String group, final String tenant) {
        BooleanBuilder booleanBuilder = new BooleanBuilder();
        QConfigInfoEntity qConfigInfo = QConfigInfoEntity.configInfoEntity;
        if (StringUtils.isNotBlank(dataId)) {
            booleanBuilder.and(qConfigInfo.dataId.eq(dataId));
        }
        if (StringUtils.isNotBlank(group)) {
            booleanBuilder.and(qConfigInfo.groupId.eq(group));
        }
        final String tenantTmp = StringUtils.isBlank(tenant) ? StringUtils.EMPTY : tenant;
        booleanBuilder.and(qConfigInfo.groupId.eq(tenantTmp));
        ConfigInfoEntity configInfo = configInfoRepository.findOne(booleanBuilder).orElse(null);
        ConfigAdvanceInfo configAdvance = ConfigAdvanceInfoMapStruct.INSTANCE.convertConfigAdvanceInfo(configInfo);
        List<String> configTagList = this.selectTagByConfig(dataId, group, tenant);
        if (configTagList != null && !configTagList.isEmpty()) {
            StringBuilder configTagsTmp = new StringBuilder();
            for (String configTag : configTagList) {
                if (configTagsTmp.length() == 0) {
                    configTagsTmp.append(configTag);
                } else {
                    configTagsTmp.append(",").append(configTag);
                }
            }
            configAdvance.setConfigTags(configTagsTmp.toString());
        }
        return configAdvance;
    }
    
    @Override
    public ConfigAllInfo findConfigAllInfo(final String dataId, final String group, final String tenant) {
        BooleanBuilder booleanBuilder = new BooleanBuilder();
        QConfigInfoEntity qConfigInfo = QConfigInfoEntity.configInfoEntity;
        if (StringUtils.isNotBlank(dataId)) {
            booleanBuilder.and(qConfigInfo.dataId.eq(dataId));
        }
        if (StringUtils.isNotBlank(group)) {
            booleanBuilder.and(qConfigInfo.groupId.eq(group));
        }
        if (StringUtils.isBlank(tenant)) {
            booleanBuilder.and(qConfigInfo.tenantId.eq(StringUtils.EMPTY).or(qConfigInfo.tenantId.isNull()));
        } else {
            booleanBuilder.and(qConfigInfo.tenantId.eq(tenant));
        }
        ConfigInfoEntity configInfo = configInfoRepository.findOne(booleanBuilder).orElse(null);
        ConfigAllInfo configAdvance = ConfigAllInfoMapStruct.INSTANCE.convertConfigAllInfo(configInfo);
        List<String> configTagList = selectTagByConfig(dataId, group, tenant);
        if (configTagList != null && !configTagList.isEmpty()) {
            StringBuilder configTagsTmp = new StringBuilder();
            for (String configTag : configTagList) {
                if (configTagsTmp.length() == 0) {
                    configTagsTmp.append(configTag);
                } else {
                    configTagsTmp.append(",").append(configTag);
                }
            }
            configAdvance.setConfigTags(configTagsTmp.toString());
        }
        return configAdvance;
    }
    
    @Override
    public void insertConfigHistoryAtomic(long id, ConfigInfo configInfo, String srcIp, String srcUser,
            final Timestamp time, String ops) {
        String appNameTmp = StringUtils.isBlank(configInfo.getAppName()) ? StringUtils.EMPTY : configInfo.getAppName();
        String tenantTmp = StringUtils.isBlank(configInfo.getTenant()) ? StringUtils.EMPTY : configInfo.getTenant();
        final String md5Tmp = MD5Utils.md5Hex(configInfo.getContent(), Constants.ENCODE);
        HisConfigInfoEntity hisConfigInfo = new HisConfigInfoEntity();
        hisConfigInfo.setId(id);
        hisConfigInfo.setDataId(configInfo.getDataId());
        hisConfigInfo.setGroupId(configInfo.getGroup());
        hisConfigInfo.setAppName(appNameTmp);
        hisConfigInfo.setContent(configInfo.getContent());
        hisConfigInfo.setMd5(md5Tmp);
        hisConfigInfo.setGmtModified(time);
        hisConfigInfo.setSrcUser(srcUser);
        hisConfigInfo.setSrcIp(srcIp);
        hisConfigInfo.setOpType(ops);
        hisConfigInfo.setTenantId(tenantTmp);
        hisConfigInfoRepository.save(hisConfigInfo);
    }
    
    @Override
    public Page<ConfigHistoryInfo> findConfigHistory(String dataId, String group, String tenant, int pageNo,
            int pageSize) {
        QHisConfigInfoEntity qHisConfigInfo = QHisConfigInfoEntity.hisConfigInfoEntity;
        BooleanExpression booleanExpression = qHisConfigInfo.dataId.eq(dataId).and(qHisConfigInfo.groupId.eq(group));
        if (StringUtils.isBlank(tenant)) {
            booleanExpression = booleanExpression
                    .and(qHisConfigInfo.tenantId.eq(StringUtils.EMPTY).or(qHisConfigInfo.tenantId.isNull()));
        } else {
            booleanExpression = booleanExpression.and(qHisConfigInfo.tenantId.eq(tenant));
        }
        org.springframework.data.domain.Page<HisConfigInfoEntity> sPage = hisConfigInfoRepository
                .findAll(booleanExpression, PageRequest.of(pageNo - 1, pageSize, Sort.by(Sort.Order.desc("nid"))));
        
        Page<ConfigHistoryInfo> page = new Page<>();
        page.setPageNumber(pageNo);
        page.setPagesAvailable(sPage.getTotalPages());
        page.setPageItems(ConfigHistoryInfoMapStruct.INSTANCE.convertConfigHistoryInfoList(sPage.getContent()));
        page.setTotalCount((int) sPage.getTotalElements());
        return page;
    }
    
    @Override
    public void addConfigSubAtomic(final String dataId, final String group, final String appName,
            final Timestamp date) {
    }
    
    @Override
    public void updateConfigSubAtomic(final String dataId, final String group, final String appName,
            final Timestamp time) {
        
    }
    
    @Override
    public ConfigHistoryInfo detailConfigHistory(Long nid) {
        HisConfigInfoEntity hisConfigInfoEntity = hisConfigInfoRepository.findById(nid).orElse(null);
        return ConfigHistoryInfoMapStruct.INSTANCE.convertConfigHistoryInfo(hisConfigInfoEntity);
    }
    
    @Override
    public ConfigHistoryInfo detailPreviousConfigHistory(Long id) {
        HisConfigInfoEntity topHisConfigInfoEntity = hisConfigInfoRepository.findTopByIdOrderByNidDesc(id);
        if (topHisConfigInfoEntity != null) {
            HisConfigInfoEntity hisConfigInfoEntity = hisConfigInfoRepository.findById(topHisConfigInfoEntity.getNid())
                    .orElse(null);
            return ConfigHistoryInfoMapStruct.INSTANCE.convertConfigHistoryInfo(hisConfigInfoEntity);
        }
        return null;
    }
    
    @Override
    public void insertTenantInfoAtomic(String kp, String tenantId, String tenantName, String tenantDesc,
            String createResoure, final long time) {
        TenantInfoEntity tenantInfo = new TenantInfoEntity();
        tenantInfo.setKp(kp);
        tenantInfo.setTenantId(tenantId);
        tenantInfo.setTenantName(tenantName);
        tenantInfo.setTenantDesc(tenantDesc);
        tenantInfo.setCreateSource(createResoure);
        tenantInfo.setGmtCreate(time);
        tenantInfo.setGmtModified(time);
        tenantInfoRepository.save(tenantInfo);
    }
    
    @Override
    public void updateTenantNameAtomic(String kp, String tenantId, String tenantName, String tenantDesc) {
        QTenantInfoEntity qTenantInfo = QTenantInfoEntity.tenantInfoEntity;
        tenantInfoRepository.findOne(qTenantInfo.kp.eq(kp).and(qTenantInfo.tenantId.eq(tenantId))).ifPresent(s -> {
            s.setTenantName(tenantName);
            s.setTenantDesc(tenantDesc);
            tenantInfoRepository.save(s);
        });
    }
    
    @Override
    public List<TenantInfo> findTenantByKp(String kp) {
        List<TenantInfoEntity> list = tenantInfoRepository.findByKp(kp);
        return TenantInfoMapStruct.INSTANCE.convertTenantInfoList(list);
    }
    
    @Override
    public TenantInfo findTenantByKp(String kp, String tenantId) {
        TenantInfoEntity tenantInfoEntity = tenantInfoRepository.findByKpAndTenantId(kp, tenantId);
        return TenantInfoMapStruct.INSTANCE.convertTenantInfo(tenantInfoEntity);
    }
    
    @Override
    public void removeTenantInfoAtomic(final String kp, final String tenantId) {
        tenantInfoRepository.findOne(QTenantInfoEntity.tenantInfoEntity.tenantId.eq(tenantId)
                .and(QTenantInfoEntity.tenantInfoEntity.kp.eq(kp))).ifPresent(s -> tenantInfoRepository.delete(s));
    }
    
    @Override
    public List<ConfigInfo> convertDeletedConfig(List<Map<String, Object>> list) {
        List<ConfigInfo> configs = new ArrayList<ConfigInfo>();
        for (Map<String, Object> map : list) {
            String dataId = (String) map.get("data_id");
            String group = (String) map.get("group_id");
            String tenant = (String) map.get("tenant_id");
            ConfigInfo config = new ConfigInfo();
            config.setDataId(dataId);
            config.setGroup(group);
            config.setTenant(tenant);
            configs.add(config);
        }
        return configs;
    }
    
    @Override
    public List<ConfigInfoWrapper> convertChangeConfig(List<Map<String, Object>> list) {
        List<ConfigInfoWrapper> configs = new ArrayList<ConfigInfoWrapper>();
        for (Map<String, Object> map : list) {
            String dataId = (String) map.get("data_id");
            String group = (String) map.get("group_id");
            String tenant = (String) map.get("tenant_id");
            String content = (String) map.get("content");
            long mTime = ((Timestamp) map.get("gmt_modified")).getTime();
            ConfigInfoWrapper config = new ConfigInfoWrapper();
            config.setDataId(dataId);
            config.setGroup(group);
            config.setTenant(tenant);
            config.setContent(content);
            config.setLastModified(mTime);
            configs.add(config);
        }
        return configs;
    }
    
    @Override
    public List<ConfigInfoWrapper> listAllGroupKeyMd5() {
        final int pageSize = 10000;
        int totalCount = configInfoCount();
        int pageCount = (int) Math.ceil(totalCount * 1.0 / pageSize);
        List<ConfigInfoWrapper> allConfigInfo = new ArrayList<ConfigInfoWrapper>();
        for (int pageNo = 0; pageNo < pageCount; pageNo++) {
            List<ConfigInfoWrapper> configInfoList = listGroupKeyMd5ByPage(pageNo, pageSize);
            allConfigInfo.addAll(configInfoList);
        }
        return allConfigInfo;
    }
    
    @Override
    public List<ConfigInfoWrapper> listGroupKeyMd5ByPage(int pageNo, int pageSize) {
        org.springframework.data.domain.Page<ConfigInfoEntity> sPage = configInfoRepository
                .findAll(PageRequest.of(pageNo - 1, pageSize, Sort.by(Sort.Order.by("id"))));
        return ConfigInfoWrapperMapStruct.INSTANCE.convertConfigInfoWrapperList(sPage.getContent());
    }
    
    @Override
    public String generateLikeArgument(String s) {
        String fuzzySearchSign = "\\*";
        String sqlLikePercentSign = "%";
        if (s.contains(PATTERN_STR)) {
            return s.replaceAll(fuzzySearchSign, sqlLikePercentSign);
        } else {
            return s;
        }
    }
    
    @Override
    public ConfigInfoWrapper queryConfigInfo(final String dataId, final String group, final String tenant) {
        QConfigInfoEntity qConfigInfo = QConfigInfoEntity.configInfoEntity;
        BooleanExpression booleanExpression = qConfigInfo.dataId.eq(dataId).and(qConfigInfo.groupId.eq(group));
        if (StringUtils.isBlank(tenant)) {
            booleanExpression = booleanExpression
                    .and(qConfigInfo.tenantId.eq(StringUtils.EMPTY).or(qConfigInfo.tenantId.isNull()));
        } else {
            booleanExpression = booleanExpression.and(qConfigInfo.tenantId.eq(tenant));
        }
        ConfigInfoEntity result = configInfoRepository.findOne(booleanExpression).orElse(null);
        return ConfigInfoWrapperMapStruct.INSTANCE.convertConfigInfoWrapper(result);
    }
    
    @Override
    public boolean isExistTable(String tableName) {
        return true;
    }
    
    @Override
    public Boolean completeMd5() {
        LogUtil.DEFAULT_LOG.info("[start completeMd5]");
        int perPageSize = 1000;
        int rowCount = configInfoCount();
        int pageCount = (int) Math.ceil(rowCount * 1.0 / perPageSize);
        int actualRowCount = 0;
        for (int pageNo = 1; pageNo <= pageCount; pageNo++) {
            Page<ConfigInfoWrapper> page = findAllConfigInfoForDumpAll(pageNo, perPageSize);
            if (page != null) {
                for (ConfigInfoWrapper cf : page.getPageItems()) {
                    String md5InDb = cf.getMd5();
                    final String content = cf.getContent();
                    final String tenant = cf.getTenant();
                    final String md5 = MD5Utils.md5Hex(content, Constants.ENCODE);
                    if (StringUtils.isBlank(md5InDb)) {
                        try {
                            updateMd5(cf.getDataId(), cf.getGroup(), tenant, md5, new Timestamp(cf.getLastModified()));
                        } catch (Exception e) {
                            LogUtil.DEFAULT_LOG.error("[completeMd5-error] datId:{} group:{} lastModified:{}",
                                    new Object[] {cf.getDataId(), cf.getGroup(), new Timestamp(cf.getLastModified())});
                        }
                    } else {
                        if (!md5InDb.equals(md5)) {
                            try {
                                updateMd5(cf.getDataId(), cf.getGroup(), tenant, md5,
                                        new Timestamp(cf.getLastModified()));
                            } catch (Exception e) {
                                LogUtil.DEFAULT_LOG.error("[completeMd5-error] datId:{} group:{} lastModified:{}",
                                        new Object[] {cf.getDataId(), cf.getGroup(),
                                                new Timestamp(cf.getLastModified())});
                            }
                        }
                    }
                }
                
                actualRowCount += page.getPageItems().size();
                LogUtil.DEFAULT_LOG.info("[completeMd5] {} / {}", actualRowCount, rowCount);
            }
        }
        return true;
    }
    
    @Override
    public List<ConfigAllInfo> findAllConfigInfo4Export(final String dataId, final String group, final String tenant,
            final String appName, final List<Long> ids) {
        BooleanBuilder booleanBuilder = new BooleanBuilder();
        QConfigInfoEntity qConfigInfo = QConfigInfoEntity.configInfoEntity;
        if (!org.springframework.util.CollectionUtils.isEmpty(ids)) {
            booleanBuilder.and(qConfigInfo.id.in(ids));
        } else {
            if (StringUtils.isBlank(tenant)) {
                booleanBuilder.and(qConfigInfo.tenantId.eq(StringUtils.EMPTY).or(qConfigInfo.tenantId.isNull()));
            } else {
                booleanBuilder.and(qConfigInfo.tenantId.eq(tenant));
            }
            if (StringUtils.isNotBlank(dataId)) {
                booleanBuilder.and(qConfigInfo.dataId.like(dataId));
            }
            if (StringUtils.isNotBlank(group)) {
                booleanBuilder.and(qConfigInfo.groupId.eq(group));
            }
            if (StringUtils.isNotBlank(appName)) {
                booleanBuilder.and(qConfigInfo.appName.eq(appName));
            }
        }
        Iterable<ConfigInfoEntity> configInfos = configInfoRepository.findAll(booleanBuilder);
        List<ConfigAllInfo> resultList = new ArrayList<>();
        configInfos.forEach(s -> {
            ConfigAllInfo configAllInfo = ConfigAllInfoMapStruct.INSTANCE.convertConfigAllInfo(s);
            resultList.add(configAllInfo);
        });
        return resultList;
    }
    
    @Override
    public Map<String, Object> batchInsertOrUpdate(List<ConfigAllInfo> configInfoList, String srcUser, String srcIp,
            Map<String, Object> configAdvanceInfo, Timestamp time, boolean notify, SameConfigPolicy policy)
            throws NacosException {
        int succCount = 0;
        int skipCount = 0;
        List<Map<String, String>> failData = null;
        List<Map<String, String>> skipData = null;
        
        for (int i = 0; i < configInfoList.size(); i++) {
            ConfigAllInfo configInfo = configInfoList.get(i);
            try {
                ParamUtils
                        .checkParam(configInfo.getDataId(), configInfo.getGroup(), "datumId", configInfo.getContent());
            } catch (NacosException e) {
                LogUtil.DEFAULT_LOG.error("data verification failed", e);
                throw e;
            }
            ConfigInfo configInfo2Save = new ConfigInfo(configInfo.getDataId(), configInfo.getGroup(),
                    configInfo.getTenant(), configInfo.getAppName(), configInfo.getContent());
            
            String type = configInfo.getType();
            if (StringUtils.isBlank(type)) {
                // simple judgment of file type based on suffix
                if (configInfo.getDataId().contains(SPOT)) {
                    String extName = configInfo.getDataId().substring(configInfo.getDataId().lastIndexOf(SPOT) + 1);
                    FileTypeEnum fileTypeEnum = FileTypeEnum.getFileTypeEnumByFileExtensionOrFileType(extName);
                    type = fileTypeEnum.getFileType();
                }
            }
            if (configAdvanceInfo == null) {
                configAdvanceInfo = new HashMap<>(16);
            }
            configAdvanceInfo.put("type", type);
            configAdvanceInfo.put("desc", configInfo.getDesc());
            try {
                addConfigInfo(srcIp, srcUser, configInfo2Save, time, configAdvanceInfo, notify);
                succCount++;
            } catch (DataIntegrityViolationException ive) {
                // uniqueness constraint conflict
                if (SameConfigPolicy.ABORT.equals(policy)) {
                    failData = new ArrayList<>();
                    skipData = new ArrayList<>();
                    Map<String, String> faileditem = new HashMap<>(2);
                    faileditem.put("dataId", configInfo2Save.getDataId());
                    faileditem.put("group", configInfo2Save.getGroup());
                    failData.add(faileditem);
                    for (int j = (i + 1); j < configInfoList.size(); j++) {
                        ConfigInfo skipConfigInfo = configInfoList.get(j);
                        Map<String, String> skipitem = new HashMap<>(2);
                        skipitem.put("dataId", skipConfigInfo.getDataId());
                        skipitem.put("group", skipConfigInfo.getGroup());
                        skipData.add(skipitem);
                    }
                    break;
                } else if (SameConfigPolicy.SKIP.equals(policy)) {
                    skipCount++;
                    if (skipData == null) {
                        skipData = new ArrayList<>();
                    }
                    Map<String, String> skipitem = new HashMap<>(2);
                    skipitem.put("dataId", configInfo2Save.getDataId());
                    skipitem.put("group", configInfo2Save.getGroup());
                    skipData.add(skipitem);
                } else if (SameConfigPolicy.OVERWRITE.equals(policy)) {
                    succCount++;
                    updateConfigInfo(configInfo2Save, srcIp, srcUser, time, configAdvanceInfo, notify);
                }
            }
        }
        Map<String, Object> result = new HashMap<>(4);
        result.put("succCount", succCount);
        result.put("skipCount", skipCount);
        if (failData != null && !failData.isEmpty()) {
            result.put("failData", failData);
        }
        if (skipData != null && !skipData.isEmpty()) {
            result.put("skipData", skipData);
        }
        return result;
    }
    
    @Override
    public int tenantInfoCountByTenantId(String tenantId) {
        Assert.hasText(tenantId, "tenantId can not be null");
        QTenantInfoEntity qTenantInfo = QTenantInfoEntity.tenantInfoEntity;
        BooleanBuilder booleanBuilder = new BooleanBuilder();
        if (StringUtils.isBlank(tenantId)) {
            booleanBuilder.and(qTenantInfo.tenantId.eq(StringUtils.EMPTY).or(qTenantInfo.tenantId.isNull()));
        } else {
            booleanBuilder.and(qTenantInfo.tenantId.eq(tenantId));
        }
        return (int) tenantInfoRepository.count(booleanBuilder);
    }
    
}
