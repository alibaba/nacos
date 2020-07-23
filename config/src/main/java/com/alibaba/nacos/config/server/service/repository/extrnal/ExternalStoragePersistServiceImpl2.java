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

import java.util.Date;

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.common.utils.MD5Utils;
import com.alibaba.nacos.config.server.configuration.ConditionOnExternalStorage;
import com.alibaba.nacos.config.server.constant.Constants;
import com.alibaba.nacos.config.server.enums.FileTypeEnum;
import com.alibaba.nacos.config.server.model.*;
import com.alibaba.nacos.config.server.modules.entity.*;
import com.alibaba.nacos.config.server.modules.mapstruct.*;
import com.alibaba.nacos.config.server.modules.repository.*;
import com.alibaba.nacos.config.server.service.datasource.DataSourceService;
import com.alibaba.nacos.config.server.service.datasource.DynamicDataSource;
import com.alibaba.nacos.config.server.service.repository.PaginationHelper;
import com.alibaba.nacos.config.server.service.repository.PersistService;
import com.alibaba.nacos.config.server.utils.LogUtil;
import com.alibaba.nacos.config.server.utils.ParamUtils;
import com.google.common.base.Joiner;
import com.google.common.collect.Lists;
import com.querydsl.core.BooleanBuilder;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Conditional;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.dao.IncorrectResultSizeDataAccessException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.jdbc.CannotGetJdbcConnectionException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementCreator;
import org.springframework.jdbc.core.PreparedStatementSetter;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.TransactionException;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.TransactionSystemException;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.Assert;

import javax.annotation.PostConstruct;
import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Predicate;
import javax.persistence.criteria.Root;
import java.io.IOException;
import java.sql.*;
import java.util.*;
import java.util.stream.Collectors;

import static com.alibaba.nacos.config.server.service.repository.RowMapperManager.*;

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
public class ExternalStoragePersistServiceImpl2 implements PersistService {

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
//        boolean result = tjt.execute(status -> {
//            try {
//                long configId = addConfigInfoAtomic(-1, srcIp, srcUser, configInfo, time, configAdvanceInfo);
//                String configTags = configAdvanceInfo == null ? null : (String) configAdvanceInfo.get("config_tags");
//                addConfigTagsRelation(configId, configTags, configInfo.getDataId(), configInfo.getGroup(),
//                    configInfo.getTenant());
//                insertConfigHistoryAtomic(0, configInfo, srcIp, srcUser, time, "I");
//            } catch (CannotGetJdbcConnectionException e) {
//                LogUtil.FATAL_LOG.error("[db-error] " + e.toString(), e);
//                throw e;
//            }
//            return Boolean.TRUE;
//        });
        ConfigInfoEntity configInfoEntity = ConfigInfoEntityMapStruct.INSTANCE
            .convertConfigInfoEntity(configInfo);
        tjt.execute(new TransactionCallback<Boolean>() {
            @Override
            public Boolean doInTransaction(TransactionStatus status) {
                try {
                    long configId = addConfigInfoAtomic(-1, srcIp, srcUser, configInfo, time, configAdvanceInfo);
                    String configTags = configAdvanceInfo == null ? null : (String) configAdvanceInfo.get("config_tags");
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
//        String appNameTmp = StringUtils.isBlank(configInfo.getAppName()) ? StringUtils.EMPTY : configInfo.getAppName();
//        String tenantTmp = StringUtils.isBlank(configInfo.getTenant()) ? StringUtils.EMPTY : configInfo.getTenant();
//        String md5 = MD5Utils.md5Hex(configInfo.getContent(), Constants.ENCODE);
//        try {
//            jt.update("INSERT INTO config_info_beta(data_id,group_id,tenant_id,app_name,content,md5,beta_ips,src_ip,"
//                    + "src_user,gmt_create,gmt_modified) VALUES(?,?,?,?,?,?,?,?,?,?,?)", configInfo.getDataId(),
//                configInfo.getGroup(), tenantTmp, appNameTmp, configInfo.getContent(), md5, betaIps, srcIp, srcUser,
//                time, time);
//        } catch (CannotGetJdbcConnectionException e) {
//            LogUtil.FATAL_LOG.error("[db-error] " + e.toString(), e);
//            throw e;
//        }
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
//        String appNameTmp = StringUtils.isBlank(configInfo.getAppName()) ? StringUtils.EMPTY : configInfo.getAppName();
//        String tenantTmp = StringUtils.isBlank(configInfo.getTenant()) ? StringUtils.EMPTY : configInfo.getTenant();
//        String tagTmp = StringUtils.isBlank(tag) ? StringUtils.EMPTY : tag.trim();
//        String md5 = MD5Utils.md5Hex(configInfo.getContent(), Constants.ENCODE);
//        try {
//            jt.update(
//                "INSERT INTO config_info_tag(data_id,group_id,tenant_id,tag_id,app_name,content,md5,src_ip,src_user,"
//                    + "gmt_create,gmt_modified) VALUES(?,?,?,?,?,?,?,?,?,?,?)", configInfo.getDataId(),
//                configInfo.getGroup(), tenantTmp, tagTmp, appNameTmp, configInfo.getContent(), md5, srcIp, srcUser,
//                time, time);
//        } catch (CannotGetJdbcConnectionException e) {
//            LogUtil.FATAL_LOG.error("[db-error] " + e.toString(), e);
//            throw e;
//        }
        String appNameTmp = StringUtils.isBlank(configInfo.getAppName()) ? StringUtils.EMPTY : configInfo.getAppName();
        String tenantTmp = StringUtils.isBlank(configInfo.getTenant()) ? StringUtils.EMPTY : configInfo.getTenant();
        String tagTmp = StringUtils.isBlank(tag) ? StringUtils.EMPTY : tag.trim();
        try {
            String md5 = MD5Utils.md5Hex(configInfo.getContent(), com.alibaba.nacos.api.common.Constants.ENCODE);
            ConfigInfoTagEntity configInfoTag = new ConfigInfoTagEntity();
            configInfoTag.setDataId(configInfo.getDataId());
            configInfoTag.setGroupId(configInfo.getGroup());
            configInfoTag.setTenantId(tenantTmp);
            configInfoTag.setTagId(tag);
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
//        boolean result = tjt.execute(status -> {
//            try {
//                ConfigInfo oldConfigInfo = findConfigInfo(configInfo.getDataId(), configInfo.getGroup(),
//                    configInfo.getTenant());
//                String appNameTmp = oldConfigInfo.getAppName();
//                /*
//                 If the appName passed by the user is not empty, use the persistent user's appName,
//                 otherwise use db; when emptying appName, you need to pass an empty string
//                 */
//                if (configInfo.getAppName() == null) {
//                    configInfo.setAppName(appNameTmp);
//                }
//                updateConfigInfoAtomic(configInfo, srcIp, srcUser, time, configAdvanceInfo);
//                String configTags = configAdvanceInfo == null ? null : (String) configAdvanceInfo.get("config_tags");
//                if (configTags != null) {
//                    // delete all tags and then recreate
//                    removeTagByIdAtomic(oldConfigInfo.getId());
//                    addConfigTagsRelation(oldConfigInfo.getId(), configTags, configInfo.getDataId(),
//                        configInfo.getGroup(), configInfo.getTenant());
//                }
//                insertConfigHistoryAtomic(oldConfigInfo.getId(), oldConfigInfo, srcIp, srcUser, time, "U");
//            } catch (CannotGetJdbcConnectionException e) {
//                LogUtil.FATAL_LOG.error("[db-error] " + e.toString(), e);
//                throw e;
//            }
//            return Boolean.TRUE;
//        });
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
                    String configTags = configAdvanceInfo == null ? null : (String) configAdvanceInfo.get("config_tags");
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
//        String appNameTmp = StringUtils.isBlank(configInfo.getAppName()) ? StringUtils.EMPTY : configInfo.getAppName();
//        String tenantTmp = StringUtils.isBlank(configInfo.getTenant()) ? StringUtils.EMPTY : configInfo.getTenant();
//        String md5 = MD5Utils.md5Hex(configInfo.getContent(), Constants.ENCODE);
//        try {
//            jt.update(
//                "UPDATE config_info_beta SET content=?, md5 = ?, src_ip=?,src_user=?,gmt_modified=?,app_name=? WHERE "
//                    + "data_id=? AND group_id=? AND tenant_id=?", configInfo.getContent(), md5, srcIp, srcUser,
//                time, appNameTmp, configInfo.getDataId(), configInfo.getGroup(), tenantTmp);
//        } catch (CannotGetJdbcConnectionException e) {
//            LogUtil.FATAL_LOG.error("[db-error] " + e.toString(), e);
//            throw e;
//        }
        String appNameTmp = StringUtils.isBlank(configInfo.getAppName()) ? StringUtils.EMPTY : configInfo.getAppName();
        String tenantTmp = StringUtils.isBlank(configInfo.getTenant()) ? StringUtils.EMPTY : configInfo.getTenant();
        BooleanBuilder booleanBuilder = new BooleanBuilder();
        QConfigInfoBeta qConfigInfoBeta = QConfigInfoBeta.configInfoBeta;
        if (StringUtils.isNotBlank(configInfo.getDataId())) {
            booleanBuilder.and(qConfigInfoBeta.dataId.eq(configInfo.getDataId()));
        }
        if (StringUtils.isNotBlank(configInfo.getGroup())) {
            booleanBuilder.and(qConfigInfoBeta.groupId.eq(configInfo.getGroup()));
        }
        if (StringUtils.isNotBlank(tenantTmp)) {
            booleanBuilder.and(qConfigInfoBeta.tenantId.eq(tenantTmp));
        }
        ConfigInfoBetaEntity configInfoBeta = configInfoBetaRepository.findOne(booleanBuilder)
            .orElse(null);
        try {
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
//        String appNameTmp = StringUtils.isBlank(configInfo.getAppName()) ? StringUtils.EMPTY : configInfo.getAppName();
//        String tenantTmp = StringUtils.isBlank(configInfo.getTenant()) ? StringUtils.EMPTY : configInfo.getTenant();
//        String tagTmp = StringUtils.isBlank(tag) ? StringUtils.EMPTY : tag.trim();
//        try {
//            String md5 = MD5Utils.md5Hex(configInfo.getContent(), Constants.ENCODE);
//            jt.update(
//                "UPDATE config_info_tag SET content=?, md5 = ?, src_ip=?,src_user=?,gmt_modified=?,app_name=? WHERE "
//                    + "data_id=? AND group_id=? AND tenant_id=? AND tag_id=?", configInfo.getContent(), md5,
//                srcIp, srcUser, time, appNameTmp, configInfo.getDataId(), configInfo.getGroup(), tenantTmp, tagTmp);
//        } catch (CannotGetJdbcConnectionException e) {
//            LogUtil.FATAL_LOG.error("[db-error] " + e.toString(), e);
//            throw e;
//        }

        String appNameTmp = StringUtils.isBlank(configInfo.getAppName()) ? StringUtils.EMPTY : configInfo.getAppName();
        String tenantTmp = StringUtils.isBlank(configInfo.getTenant()) ? StringUtils.EMPTY : configInfo.getTenant();
        String tagTmp = StringUtils.isBlank(tag) ? StringUtils.EMPTY : tag.trim();
        QConfigInfoTag qConfigInfoTag = QConfigInfoTag.configInfoTag;
        ConfigInfoTagEntity configInfoTag = configInfoTagRepository.findOne(qConfigInfoTag.dataId.eq(configInfo.getDataId())
            .and(qConfigInfoTag.groupId.eq(configInfo.getGroup()))
            .and(qConfigInfoTag.tenantId.eq(tenantTmp))
            .and(qConfigInfoTag.tagId.eq(tagTmp)))
            .orElse(null);
        try {
            String md5 = MD5Utils.md5Hex(configInfo.getContent(), com.alibaba.nacos.api.common.Constants.ENCODE);
            configInfoTag.setDataId(configInfo.getDataId());
            configInfoTag.setGroupId(configInfo.getGroup());
            configInfoTag.setTenantId(tenantTmp);
            configInfoTag.setTagId(tag);
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
//        String tenantTmp = StringUtils.isBlank(tenant) ? StringUtils.EMPTY : tenant;
//        try {
//            jt.update(
//                "UPDATE config_info SET md5 = ? WHERE data_id=? AND group_id=? AND tenant_id=? AND gmt_modified=?",
//                md5, dataId, group, tenantTmp, lastTime);
//        } catch (CannotGetJdbcConnectionException e) {
//            LogUtil.FATAL_LOG.error("[db-error] " + e.toString(), e);
//            throw e;
//        }
        String tenantTmp = StringUtils.isBlank(tenant) ? StringUtils.EMPTY : tenant;
        QConfigInfo qConfigInfo = QConfigInfo.configInfo;
        BooleanBuilder booleanBuilder = new BooleanBuilder();
        if (StringUtils.isNotBlank(dataId)) {
            booleanBuilder.and(qConfigInfo.dataId.eq(dataId));
        }
        if (StringUtils.isNotBlank(group)) {
            booleanBuilder.and(qConfigInfo.groupId.eq(group));
        }
        if (StringUtils.isNotBlank(tenant)) {
            booleanBuilder.and(qConfigInfo.tenantId.eq(tenant));
        }
        if (lastTime != null) {
            booleanBuilder.and(qConfigInfo.gmtModified.eq(lastTime));
        }
        configInfoRepository.findOne(booleanBuilder)
            .ifPresent(config -> {
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
//        final String tenantTmp = StringUtils.isBlank(tenant) ? StringUtils.EMPTY : tenant;
//        tjt.execute(status -> {
//            try {
//                ConfigInfo configInfo = findConfigInfo4Beta(dataId, group, tenant);
//                if (configInfo != null) {
//                    jt.update("DELETE FROM config_info_beta WHERE data_id=? AND group_id=? AND tenant_id=?", dataId,
//                        group, tenantTmp);
//                }
//            } catch (CannotGetJdbcConnectionException e) {
//                LogUtil.FATAL_LOG.error("[db-error] " + e.toString(), e);
//                throw e;
//            }
//            return Boolean.TRUE;
//        });
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
        String appNameTmp = StringUtils.isBlank(appName) ? StringUtils.EMPTY : appName;
        String tenantTmp = StringUtils.isBlank(tenant) ? StringUtils.EMPTY : tenant;
        final Timestamp now = new Timestamp(System.currentTimeMillis());
//        String select = "SELECT content FROM config_info_aggr WHERE data_id = ? AND group_id = ? AND tenant_id = ?  AND datum_id = ?";
//        String insert = "INSERT INTO config_info_aggr(data_id, group_id, tenant_id, datum_id, app_name, content, gmt_modified) VALUES(?,?,?,?,?,?,?) ";
//        String update = "UPDATE config_info_aggr SET content = ? , gmt_modified = ? WHERE data_id = ? AND group_id = ? AND tenant_id = ? AND datum_id = ?";
//
//        try {
//            try {
//                String dbContent = jt
//                    .queryForObject(select, new Object[]{dataId, group, tenantTmp, datumId}, String.class);
//
//                if (dbContent != null && dbContent.equals(content)) {
//                    return true;
//                } else {
//                    return jt.update(update, content, now, dataId, group, tenantTmp, datumId) > 0;
//                }
//            } catch (EmptyResultDataAccessException ex) { // no data, insert
//                return jt.update(insert, dataId, group, tenantTmp, datumId, appNameTmp, content, now) > 0;
//            }
//        } catch (DataAccessException e) {
//            LogUtil.FATAL_LOG.error("[db-error] " + e.toString(), e);
//            throw e;
//        }
        QConfigInfoAggr qConfigInfoAggr = QConfigInfoAggr.configInfoAggr;
        BooleanBuilder booleanBuilder = new BooleanBuilder();
        if (StringUtils.isNotBlank(dataId)) {
            booleanBuilder.and(qConfigInfoAggr.dataId.eq(dataId));
        }
        if (StringUtils.isNotBlank(group)) {
            booleanBuilder.and(qConfigInfoAggr.groupId.eq(group));
        }
        if (StringUtils.isNotBlank(tenant)) {
            booleanBuilder.and(qConfigInfoAggr.tenantId.eq(tenant));
        }
        if (StringUtils.isNotBlank(datumId)) {
            booleanBuilder.and(qConfigInfoAggr.datumId.eq(datumId));
        }
        try {
            try {
                ConfigInfoAggrEntity result = configInfoAggrRepository.findOne(booleanBuilder)
                    .orElse(null);

                if (result.getContent() != null && result.getContent().equals(content)) {
                    return true;
                } else {
                    result.setContent(content);
                    result.setGmtModified(now);
                    configInfoAggrRepository.save(result);
                    return true;
                }
            } catch (EmptyResultDataAccessException ex) { // no data, insert
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
//        final String tenantTmp = StringUtils.isBlank(tenant) ? StringUtils.EMPTY : tenant;
//        String sql = "DELETE FROM config_info_aggr WHERE data_id=? AND group_id=? AND tenant_id=? AND datum_id=?";
//
//        try {
//            this.jt.update(sql, new PreparedStatementSetter() {
//                @Override
//                public void setValues(PreparedStatement ps) throws SQLException {
//                    int index = 1;
//                    ps.setString(index++, dataId);
//                    ps.setString(index++, group);
//                    ps.setString(index++, tenantTmp);
//                    ps.setString(index, datumId);
//                }
//            });
//        } catch (CannotGetJdbcConnectionException e) {
//            LogUtil.FATAL_LOG.error("[db-error] " + e.toString(), e);
//            throw e;
//        }
    }

    @Override
    public void removeAggrConfigInfo(final String dataId, final String group, final String tenant) {
//        final String tenantTmp = StringUtils.isBlank(tenant) ? StringUtils.EMPTY : tenant;
//        String sql = "DELETE FROM config_info_aggr WHERE data_id=? AND group_id=? AND tenant_id=?";
//
//        try {
//            this.jt.update(sql, new PreparedStatementSetter() {
//                @Override
//                public void setValues(PreparedStatement ps) throws SQLException {
//                    int index = 1;
//                    ps.setString(index++, dataId);
//                    ps.setString(index++, group);
//                    ps.setString(index, tenantTmp);
//                }
//            });
//        } catch (CannotGetJdbcConnectionException e) {
//            LogUtil.FATAL_LOG.error("[db-error] " + e.toString(), e);
//            throw e;
//        }
        QConfigInfoAggr qConfigInfoAggr = QConfigInfoAggr.configInfoAggr;
        BooleanBuilder booleanBuilder = new BooleanBuilder();
        if (StringUtils.isNotBlank(dataId)) {
            booleanBuilder.and(qConfigInfoAggr.dataId.eq(dataId));
        }
        if (StringUtils.isNotBlank(group)) {
            booleanBuilder.and(qConfigInfoAggr.groupId.eq(group));
        }
        if (StringUtils.isNotBlank(tenant)) {
            booleanBuilder.and(qConfigInfoAggr.tenantId.eq(tenant));
        }
        configInfoAggrRepository.findOne(booleanBuilder)
            .ifPresent(aggr -> configInfoAggrRepository.delete(aggr));
    }

    @Override
    public boolean batchRemoveAggr(final String dataId, final String group, final String tenant,
                                   final List<String> datumList) {
//        final String tenantTmp = StringUtils.isBlank(tenant) ? StringUtils.EMPTY : tenant;
//        final StringBuilder datumString = new StringBuilder();
//        for (String datum : datumList) {
//            datumString.append("'").append(datum).append("',");
//        }
//        datumString.deleteCharAt(datumString.length() - 1);
//        final String sql =
//            "delete from config_info_aggr where data_id=? and group_id=? and tenant_id=? and datum_id in ("
//                + datumString.toString() + ")";
//        try {
//            jt.update(sql, dataId, group, tenantTmp);
//        } catch (CannotGetJdbcConnectionException e) {
//            LogUtil.FATAL_LOG.error("[db-error] " + e.toString(), e);
//            return false;
//        }
//        return true;
        return false;
    }

    @Override
    public void removeConfigHistory(final Timestamp startTime, final int limitSize) {
//        String sql = "delete from his_config_info where gmt_modified < ? limit ?";
//        PaginationHelper<ConfigInfo> helper = createPaginationHelper();
//        try {
//            helper.updateLimit(sql, new Object[]{startTime, limitSize});
//        } catch (CannotGetJdbcConnectionException e) {
//            LogUtil.FATAL_LOG.error("[db-error] " + e.toString(), e);
//            throw e;
//        }
        QHisConfigInfo qHisConfigInfo = QHisConfigInfo.hisConfigInfo;
        Iterable<HisConfigInfoEntity> iterable = hisConfigInfoRepository.findAll(qHisConfigInfo.gmtModified.lt(startTime),
            PageRequest.of(0, limitSize));
        hisConfigInfoRepository.deleteAll(iterable);
    }

    @Override
    public int findConfigHistoryCountByTime(final Timestamp startTime) {
//        String sql = "SELECT COUNT(*) FROM his_config_info WHERE gmt_modified < ?";
//        Integer result = jt.queryForObject(sql, Integer.class, new Object[]{startTime});
//        if (result == null) {
//            throw new IllegalArgumentException("configInfoBetaCount error");
//        }
//        return result.intValue();
        QHisConfigInfo qHisConfigInfo = QHisConfigInfo.hisConfigInfo;
        Long result = hisConfigInfoRepository.count(qHisConfigInfo.gmtModified.lt(startTime));
        if (result == null) {
            throw new IllegalArgumentException("configInfoBetaCount error");
        }
        return result.intValue();
    }

    @Override
    public long findConfigMaxId() {
//        String sql = "SELECT max(id) FROM config_info";
//        try {
//            return jt.queryForObject(sql, Long.class);
//        } catch (NullPointerException e) {
//            return 0;
//        }
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
//        try {
//            Boolean isReplaceOk = tjt.execute(new TransactionCallback<Boolean>() {
//                @Override
//                public Boolean doInTransaction(TransactionStatus status) {
//                    try {
//                        String appNameTmp = appName == null ? "" : appName;
//                        removeAggrConfigInfo(dataId, group, tenant);
//                        String tenantTmp = StringUtils.isBlank(tenant) ? StringUtils.EMPTY : tenant;
//                        String sql = "INSERT INTO config_info_aggr(data_id, group_id, tenant_id, datum_id, app_name, content, gmt_modified) VALUES(?,?,?,?,?,?,?) ";
//                        for (Map.Entry<String, String> datumEntry : datumMap.entrySet()) {
//                            jt.update(sql, dataId, group, tenantTmp, datumEntry.getKey(), appNameTmp,
//                                datumEntry.getValue(), new Timestamp(System.currentTimeMillis()));
//                        }
//                    } catch (Throwable e) {
//                        throw new TransactionSystemException("error in addAggrConfigInfo");
//                    }
//                    return Boolean.TRUE;
//                }
//            });
//            if (isReplaceOk == null) {
//                return false;
//            }
//            return isReplaceOk;
//        } catch (TransactionException e) {
//            LogUtil.FATAL_LOG.error("[db-error] " + e.toString(), e);
//            return false;
//        }
        return false;
    }

    @Deprecated
    @Override
    public List<ConfigInfo> findAllDataIdAndGroup() {
//        String sql = "SELECT DISTINCT data_id, group_id FROM config_info";
//
//        try {
//            return jt.query(sql, new Object[]{}, CONFIG_INFO_ROW_MAPPER);
//        } catch (EmptyResultDataAccessException e) {
//            return Collections.emptyList();
//        } catch (CannotGetJdbcConnectionException e) {
//            LogUtil.FATAL_LOG.error("[db-error] " + e.toString(), e);
//            throw e;
//        } catch (Exception e) {
//            LogUtil.FATAL_LOG.error("[db-other-error]" + e.getMessage(), e);
//            throw new RuntimeException(e);
//        }
        return null;
    }

    @Override
    public ConfigInfo4Beta findConfigInfo4Beta(final String dataId, final String group, final String tenant) {
//        String tenantTmp = StringUtils.isBlank(tenant) ? StringUtils.EMPTY : tenant;
//        try {
//            return this.jt.queryForObject(
//                "SELECT ID,data_id,group_id,tenant_id,app_name,content,beta_ips FROM config_info_beta WHERE data_id=? AND group_id=? AND tenant_id=?",
//                new Object[]{dataId, group, tenantTmp}, CONFIG_INFO4BETA_ROW_MAPPER);
//        } catch (EmptyResultDataAccessException e) { // Indicates that the data does not exist, returns null.
//            return null;
//        } catch (CannotGetJdbcConnectionException e) {
//            LogUtil.FATAL_LOG.error("[db-error] " + e.toString(), e);
//            throw e;
//        }

        BooleanBuilder booleanBuilder = new BooleanBuilder();
        QConfigInfoBeta qConfigInfoBeta = QConfigInfoBeta.configInfoBeta;
        if (StringUtils.isNotBlank(dataId)) {
            booleanBuilder.and(qConfigInfoBeta.dataId.eq(dataId));
        }
        if (StringUtils.isNotBlank(group)) {
            booleanBuilder.and(qConfigInfoBeta.groupId.eq(group));
        }
        if (StringUtils.isNotBlank(tenant)) {
            booleanBuilder.and(qConfigInfoBeta.tenantId.eq(tenant));
        }
        ConfigInfoBetaEntity configInfoBetaEntity = configInfoBetaRepository.findOne(booleanBuilder)
            .orElseThrow(() -> new RuntimeException("find configInfoBeta data null"));
        return ConfigInfo4BetaMapStruct.INSTANCE.convertConfigInfo4Beta(configInfoBetaEntity);
    }

    @Override
    public ConfigInfo4Tag findConfigInfo4Tag(final String dataId, final String group, final String tenant,
                                             final String tag) {
//        String tenantTmp = StringUtils.isBlank(tenant) ? StringUtils.EMPTY : tenant;
//        String tagTmp = StringUtils.isBlank(tag) ? StringUtils.EMPTY : tag.trim();
//        try {
//            return this.jt.queryForObject(
//                "SELECT ID,data_id,group_id,tenant_id,tag_id,app_name,content FROM config_info_tag WHERE data_id=? AND group_id=? AND tenant_id=? AND tag_id=?",
//                new Object[]{dataId, group, tenantTmp, tagTmp}, CONFIG_INFO4TAG_ROW_MAPPER);
//        } catch (EmptyResultDataAccessException e) { // Indicates that the data does not exist, returns null.
//            return null;
//        } catch (CannotGetJdbcConnectionException e) {
//            LogUtil.FATAL_LOG.error("[db-error] " + e.toString(), e);
//            throw e;
//        }
        String tenantTmp = StringUtils.isBlank(tenant) ? StringUtils.EMPTY : tenant;
        String tagTmp = StringUtils.isBlank(tag) ? StringUtils.EMPTY : tag.trim();
        BooleanBuilder booleanBuilder = new BooleanBuilder();
        QConfigInfoTag qConfigInfoTag = QConfigInfoTag.configInfoTag;
        if (StringUtils.isNotBlank(dataId)) {
            booleanBuilder.and(qConfigInfoTag.dataId.eq(dataId));
        }
        if (StringUtils.isNotBlank(group)) {
            booleanBuilder.and(qConfigInfoTag.groupId.eq(group));
        }
        if (StringUtils.isNotBlank(tenant)) {
            booleanBuilder.and(qConfigInfoTag.tenantId.eq(tenant));
        }
        if (StringUtils.isNotBlank(tag)) {
            booleanBuilder.and(qConfigInfoTag.tagId.eq(tag));
        }
        ConfigInfoTagEntity result = configInfoTagRepository.findOne(booleanBuilder)
            .orElseThrow(() -> new RuntimeException("find configInfoTag data null"));
        return ConfigInfo4TagMapStruct.INSTANCE.convertConfigInfo4Tag(result);
    }

    @Override
    public ConfigInfo findConfigInfoApp(final String dataId, final String group, final String tenant,
                                        final String appName) {
//        String tenantTmp = StringUtils.isBlank(tenant) ? StringUtils.EMPTY : tenant;
//        try {
//            return this.jt.queryForObject(
//                "SELECT ID,data_id,group_id,tenant_id,app_name,content FROM config_info WHERE data_id=? AND group_id=? AND tenant_id=? AND app_name=?",
//                new Object[]{dataId, group, tenantTmp, appName}, CONFIG_INFO_ROW_MAPPER);
//        } catch (EmptyResultDataAccessException e) { // Indicates that the data does not exist, returns null.
//            return null;
//        } catch (CannotGetJdbcConnectionException e) {
//            LogUtil.FATAL_LOG.error("[db-error] " + e.toString(), e);
//            throw e;
//        }
        return null;
    }

    @Override
    public ConfigInfo findConfigInfoAdvanceInfo(final String dataId, final String group, final String tenant,
                                                final Map<String, Object> configAdvanceInfo) {
//        String tenantTmp = StringUtils.isBlank(tenant) ? StringUtils.EMPTY : tenant;
//        final String appName = configAdvanceInfo == null ? null : (String) configAdvanceInfo.get("appName");
//        final String configTags = configAdvanceInfo == null ? null : (String) configAdvanceInfo.get("config_tags");
//        List<String> paramList = new ArrayList<String>();
//        paramList.add(dataId);
//        paramList.add(group);
//        paramList.add(tenantTmp);
//
//        StringBuilder sql = new StringBuilder(
//            "select ID,data_id,group_id,tenant_id,app_name,content from config_info where data_id=? and group_id=? and tenant_id=? ");
//        if (StringUtils.isNotBlank(configTags)) {
//            sql = new StringBuilder(
//                "select a.ID,a.data_id,a.group_id,a.tenant_id,a.app_name,a.content from config_info  a left join "
//                    + "config_tags_relation b on a.id=b.id where a.data_id=? and a.group_id=? and a.tenant_id=? ");
//            sql.append(" and b.tag_name in (");
//            String[] tagArr = configTags.split(",");
//            for (int i = 0; i < tagArr.length; i++) {
//                if (i != 0) {
//                    sql.append(", ");
//                }
//                sql.append("?");
//                paramList.add(tagArr[i]);
//            }
//            sql.append(") ");
//
//            if (StringUtils.isNotBlank(appName)) {
//                sql.append(" and a.app_name=? ");
//                paramList.add(appName);
//            }
//        } else {
//            if (StringUtils.isNotBlank(appName)) {
//                sql.append(" and app_name=? ");
//                paramList.add(appName);
//            }
//        }
//
//        try {
//            return this.jt.queryForObject(sql.toString(), paramList.toArray(), CONFIG_INFO_ROW_MAPPER);
//        } catch (EmptyResultDataAccessException e) { // Indicates that the data does not exist, returns null.
//            return null;
//        } catch (CannotGetJdbcConnectionException e) {
//            LogUtil.FATAL_LOG.error("[db-error] " + e.toString(), e);
//            throw e;
//        }
        return null;
    }

    @Override
    public ConfigInfoBase findConfigInfoBase(final String dataId, final String group) {
//        try {
//            return this.jt.queryForObject(
//                "SELECT ID,data_id,group_id,content FROM config_info WHERE data_id=? AND group_id=? AND tenant_id=?",
//                new Object[]{dataId, group, StringUtils.EMPTY}, CONFIG_INFO_BASE_ROW_MAPPER);
//        } catch (EmptyResultDataAccessException e) { // Indicates that the data does not exist, returns null.
//            return null;
//        } catch (CannotGetJdbcConnectionException e) {
//            LogUtil.FATAL_LOG.error("[db-error] " + e.toString(), e);
//            throw e;
//        }
        QConfigInfo qConfigInfo = QConfigInfo.configInfo;
        return configInfoRepository.findOne(qConfigInfo.dataId.eq(dataId)
            .and(qConfigInfo.groupId.eq(group)))
            .map(s -> {
                ConfigInfoBase configInfoBase = new ConfigInfoBase();
                BeanUtils.copyProperties(s, configInfoBase);
                configInfoBase.setGroup(s.getGroupId());
                return configInfoBase;
            }).orElse(null);
    }

    @Override
    public ConfigInfo findConfigInfo(long id) {
//        try {
//            return this.jt
//                .queryForObject("SELECT ID,data_id,group_id,tenant_id,app_name,content FROM config_info WHERE ID=?",
//                    new Object[]{id}, CONFIG_INFO_ROW_MAPPER);
//        } catch (EmptyResultDataAccessException e) { // Indicates that the data does not exist, returns null.
//            return null;
//        } catch (CannotGetJdbcConnectionException e) {
//            LogUtil.FATAL_LOG.error("[db-error] " + e.toString(), e);
//            throw e;
//        }
        return null;
    }

    @Override
    public ConfigInfo findConfigInfo(final String dataId, final String group, final String tenant) {
//        final String tenantTmp = StringUtils.isBlank(tenant) ? StringUtils.EMPTY : tenant;
//        try {
//            return this.jt.queryForObject(
//                "SELECT ID,data_id,group_id,tenant_id,app_name,content,md5,type FROM config_info WHERE data_id=? AND group_id=? AND tenant_id=?",
//                new Object[]{dataId, group, tenantTmp}, CONFIG_INFO_ROW_MAPPER);
//        } catch (EmptyResultDataAccessException e) { // Indicates that the data does not exist, returns null.
//            return null;
//        } catch (CannotGetJdbcConnectionException e) {
//            LogUtil.FATAL_LOG.error("[db-error] " + e.toString(), e);
//            throw e;
//        }
        BooleanBuilder booleanBuilder = new BooleanBuilder();
        QConfigInfo qConfigInfo = QConfigInfo.configInfo;
        if (StringUtils.isNotBlank(dataId)) {
            booleanBuilder.and(qConfigInfo.dataId.eq(dataId));
        }
        if (StringUtils.isNotBlank(group)) {
            booleanBuilder.and(qConfigInfo.groupId.eq(group));
        }
        if (StringUtils.isNotBlank(tenant)) {
            booleanBuilder.and(qConfigInfo.tenantId.eq(tenant));
        }
        ConfigInfoEntity result = configInfoRepository.findOne(booleanBuilder)
            .orElse(null);
        return ConfigInfoMapStruct.INSTANCE.convertConfigInfo(result);
    }

    @Override
    public Page<ConfigInfo> findConfigInfoByDataId(final int pageNo, final int pageSize, final String dataId,
                                                   final String tenant) {
//        String tenantTmp = StringUtils.isBlank(tenant) ? StringUtils.EMPTY : tenant;
//        PaginationHelper<ConfigInfo> helper = createPaginationHelper();
//        try {
//            return helper.fetchPage("select count(*) from config_info where data_id=? and tenant_id=?",
//                "select ID,data_id,group_id,tenant_id,app_name,content from config_info where data_id=? and tenant_id=?",
//                new Object[]{dataId, tenantTmp}, pageNo, pageSize, CONFIG_INFO_ROW_MAPPER);
//        } catch (CannotGetJdbcConnectionException e) {
//            LogUtil.FATAL_LOG.error("[db-error] " + e.toString(), e);
//            throw e;
//        }
        return null;
    }

    @Override
    public Page<ConfigInfo> findConfigInfoByDataIdAndApp(final int pageNo, final int pageSize, final String dataId,
                                                         final String tenant, final String appName) {
//        String tenantTmp = StringUtils.isBlank(tenant) ? StringUtils.EMPTY : tenant;
//        PaginationHelper<ConfigInfo> helper = createPaginationHelper();
//        try {
//            return helper.fetchPage("select count(*) from config_info where data_id=? and tenant_id=? and app_name=?",
//                "select ID,data_id,group_id,tenant_id,app_name,content from config_info where data_id=? and tenant_id=? and app_name=?",
//                new Object[]{dataId, tenantTmp, appName}, pageNo, pageSize, CONFIG_INFO_ROW_MAPPER);
//        } catch (CannotGetJdbcConnectionException e) {
//            LogUtil.FATAL_LOG.error("[db-error] " + e.toString(), e);
//            throw e;
//        }
        return null;
    }

    @Override
    public Page<ConfigInfo> findConfigInfoByDataIdAndAdvance(final int pageNo, final int pageSize, final String dataId,
                                                             final String tenant, final Map<String, Object> configAdvanceInfo) {
//        String tenantTmp = StringUtils.isBlank(tenant) ? StringUtils.EMPTY : tenant;
//        PaginationHelper<ConfigInfo> helper = createPaginationHelper();
//        final String appName = configAdvanceInfo == null ? null : (String) configAdvanceInfo.get("appName");
//        final String configTags = configAdvanceInfo == null ? null : (String) configAdvanceInfo.get("config_tags");
//        StringBuilder sqlCount = new StringBuilder("select count(*) from config_info where data_id=? and tenant_id=? ");
//        StringBuilder sql = new StringBuilder(
//            "select ID,data_id,group_id,tenant_id,app_name,content from config_info where data_id=? and tenant_id=? ");
//        List<String> paramList = new ArrayList<String>();
//        paramList.add(dataId);
//        paramList.add(tenantTmp);
//        if (StringUtils.isNotBlank(configTags)) {
//            sqlCount = new StringBuilder(
//                "select count(*) from config_info  a left join config_tags_relation b on a.id=b.id where a.data_id=? and a.tenant_id=? ");
//
//            sql = new StringBuilder(
//                "select a.ID,a.data_id,a.group_id,a.tenant_id,a.app_name,a.content from config_info  a left join "
//                    + "config_tags_relation b on a.id=b.id where a.data_id=? and a.tenant_id=? ");
//
//            sqlCount.append(" and b.tag_name in (");
//            sql.append(" and b.tag_name in (");
//            String[] tagArr = configTags.split(",");
//            for (int i = 0; i < tagArr.length; i++) {
//                if (i != 0) {
//                    sqlCount.append(", ");
//                    sql.append(", ");
//                }
//                sqlCount.append("?");
//                sql.append("?");
//                paramList.add(tagArr[i]);
//            }
//            sqlCount.append(") ");
//            sql.append(") ");
//
//            if (StringUtils.isNotBlank(appName)) {
//                sqlCount.append(" and a.app_name=? ");
//                sql.append(" and a.app_name=? ");
//                paramList.add(appName);
//            }
//        } else {
//            if (StringUtils.isNotBlank(appName)) {
//                sqlCount.append(" and app_name=? ");
//                sql.append(" and app_name=? ");
//                paramList.add(appName);
//            }
//        }
//        try {
//            return helper.fetchPage(sqlCount.toString(), sql.toString(), paramList.toArray(), pageNo, pageSize,
//                CONFIG_INFO_ROW_MAPPER);
//        } catch (CannotGetJdbcConnectionException e) {
//            LogUtil.FATAL_LOG.error("[db-error] " + e.toString(), e);
//            throw e;
//        }
        return null;
    }

    @Override
    public Page<ConfigInfo> findConfigInfo4Page(final int pageNo, final int pageSize, final String dataId,
                                                final String group, final String tenant, final Map<String, Object> configAdvanceInfo) {
//        String tenantTmp = StringUtils.isBlank(tenant) ? StringUtils.EMPTY : tenant;
//        PaginationHelper<ConfigInfo> helper = createPaginationHelper();
//        final String appName = configAdvanceInfo == null ? null : (String) configAdvanceInfo.get("appName");
//        final String configTags = configAdvanceInfo == null ? null : (String) configAdvanceInfo.get("config_tags");
//        String sqlCount = "select count(*) from config_info";
//        String sql = "select ID,data_id,group_id,tenant_id,app_name,content,type from config_info";
//        StringBuilder where = new StringBuilder(" where ");
//        List<String> paramList = new ArrayList<String>();
//        paramList.add(tenantTmp);
//        if (StringUtils.isNotBlank(configTags)) {
//            sqlCount = "select count(*) from config_info  a left join config_tags_relation b on a.id=b.id";
//            sql = "select a.ID,a.data_id,a.group_id,a.tenant_id,a.app_name,a.content from config_info  a left join "
//                + "config_tags_relation b on a.id=b.id";
//
//            where.append(" a.tenant_id=? ");
//
//            if (StringUtils.isNotBlank(dataId)) {
//                where.append(" and a.data_id=? ");
//                paramList.add(dataId);
//            }
//            if (StringUtils.isNotBlank(group)) {
//                where.append(" and a.group_id=? ");
//                paramList.add(group);
//            }
//            if (StringUtils.isNotBlank(appName)) {
//                where.append(" and a.app_name=? ");
//                paramList.add(appName);
//            }
//
//            where.append(" and b.tag_name in (");
//            String[] tagArr = configTags.split(",");
//            for (int i = 0; i < tagArr.length; i++) {
//                if (i != 0) {
//                    where.append(", ");
//                }
//                where.append("?");
//                paramList.add(tagArr[i]);
//            }
//            where.append(") ");
//        } else {
//            where.append(" tenant_id=? ");
//            if (StringUtils.isNotBlank(dataId)) {
//                where.append(" and data_id=? ");
//                paramList.add(dataId);
//            }
//            if (StringUtils.isNotBlank(group)) {
//                where.append(" and group_id=? ");
//                paramList.add(group);
//            }
//            if (StringUtils.isNotBlank(appName)) {
//                where.append(" and app_name=? ");
//                paramList.add(appName);
//            }
//        }
//        try {
//            return helper.fetchPage(sqlCount + where, sql + where, paramList.toArray(), pageNo, pageSize,
//                CONFIG_INFO_ROW_MAPPER);
//        } catch (CannotGetJdbcConnectionException e) {
//            LogUtil.FATAL_LOG.error("[db-error] " + e.toString(), e);
//            throw e;
//        }
        final String appName = configAdvanceInfo == null ? null : (String) configAdvanceInfo.get("appName");
        final String configTags = configAdvanceInfo == null ? null : (String) configAdvanceInfo.get("config_tags");
        BooleanBuilder booleanBuilder = new BooleanBuilder();
        QConfigInfo qConfigInfo = QConfigInfo.configInfo;
        buildConfigInfoCommonCondition(booleanBuilder, qConfigInfo, dataId, group, appName);
        if (StringUtils.isNotBlank(tenant)) {
            booleanBuilder.and(qConfigInfo.tenantId.eq(tenant));
        }
        org.springframework.data.domain.Page<ConfigInfoEntity> sPage = configInfoRepository
            .findAll(booleanBuilder, PageRequest.of(pageNo, pageSize, Sort.by(Sort.Order.desc("gmtCreate"))));
        Page<ConfigInfo> page = new Page<>();
        page.setPageNumber(sPage.getNumber());
        page.setPagesAvailable(sPage.getTotalPages());
        page.setPageItems(ConfigInfoMapStruct.INSTANCE.convertConfigInfoList2(sPage.getContent()));
        page.setTotalCount((int) sPage.getTotalElements());
        return page;
    }

    private void buildConfigInfoCommonCondition(BooleanBuilder booleanBuilder,
                                                QConfigInfo qConfigInfo,
                                                final String dataId,
                                                final String group,
                                                final String appName) {
        if (StringUtils.isNotBlank(dataId)) {
            booleanBuilder.and(qConfigInfo.dataId.eq(dataId));
        }
        if (StringUtils.isNotBlank(group)) {
            booleanBuilder.and(qConfigInfo.groupId.eq(group));
        }
        if (StringUtils.isNotBlank(appName)) {
            booleanBuilder.and(qConfigInfo.appName.eq(appName));
        }
    }

    @Override
    public Page<ConfigInfoBase> findConfigInfoBaseByDataId(final int pageNo, final int pageSize, final String dataId) {
//        PaginationHelper<ConfigInfoBase> helper = createPaginationHelper();
//        try {
//            return helper.fetchPage("select count(*) from config_info where data_id=? and tenant_id=?",
//                "select ID,data_id,group_id,content from config_info where data_id=? and tenant_id=?",
//                new Object[]{dataId, StringUtils.EMPTY}, pageNo, pageSize, CONFIG_INFO_BASE_ROW_MAPPER);
//        } catch (CannotGetJdbcConnectionException e) {
//            LogUtil.FATAL_LOG.error("[db-error] " + e.toString(), e);
//            throw e;
//        }
        return null;
    }

    @Override
    public Page<ConfigInfo> findConfigInfoByGroup(final int pageNo, final int pageSize, final String group,
                                                  final String tenant) {
//        String tenantTmp = StringUtils.isBlank(tenant) ? StringUtils.EMPTY : tenant;
//        PaginationHelper<ConfigInfo> helper = createPaginationHelper();
//        try {
//            return helper.fetchPage("select count(*) from config_info where group_id=? and tenant_id=?",
//                "select ID,data_id,group_id,tenant_id,app_name,content from config_info where group_id=? and tenant_id=?",
//                new Object[]{group, tenantTmp}, pageNo, pageSize, CONFIG_INFO_ROW_MAPPER);
//        } catch (CannotGetJdbcConnectionException e) {
//            LogUtil.FATAL_LOG.error("[db-error] " + e.toString(), e);
//            throw e;
//        }
        return null;
    }

    @Override
    public Page<ConfigInfo> findConfigInfoByGroupAndApp(final int pageNo, final int pageSize, final String group,
                                                        final String tenant, final String appName) {
//        String tenantTmp = StringUtils.isBlank(tenant) ? StringUtils.EMPTY : tenant;
//        PaginationHelper<ConfigInfo> helper = createPaginationHelper();
//        try {
//            return helper.fetchPage("select count(*) from config_info where group_id=? and tenant_id=? and app_name =?",
//                "select ID,data_id,group_id,tenant_id,app_name,content from config_info where group_id=? and tenant_id=? and app_name =?",
//                new Object[]{group, tenantTmp, appName}, pageNo, pageSize, CONFIG_INFO_ROW_MAPPER);
//        } catch (CannotGetJdbcConnectionException e) {
//            LogUtil.FATAL_LOG.error("[db-error] " + e.toString(), e);
//            throw e;
//        }
        return null;
    }

    @Override
    public Page<ConfigInfo> findConfigInfoByGroupAndAdvance(final int pageNo, final int pageSize, final String group,
                                                            final String tenant, final Map<String, Object> configAdvanceInfo) {
//        String tenantTmp = StringUtils.isBlank(tenant) ? StringUtils.EMPTY : tenant;
//        PaginationHelper<ConfigInfo> helper = createPaginationHelper();
//
//        final String appName = configAdvanceInfo == null ? null : (String) configAdvanceInfo.get("appName");
//        final String configTags = configAdvanceInfo == null ? null : (String) configAdvanceInfo.get("config_tags");
//        StringBuilder sqlCount = new StringBuilder(
//            "select count(*) from config_info where group_id=? and tenant_id=? ");
//        StringBuilder sql = new StringBuilder(
//            "select ID,data_id,group_id,tenant_id,app_name,content from config_info where group_id=? and tenant_id=? ");
//        List<String> paramList = new ArrayList<String>();
//        paramList.add(group);
//        paramList.add(tenantTmp);
//        if (StringUtils.isNotBlank(configTags)) {
//            sqlCount = new StringBuilder(
//                "select count(*) from config_info  a left join config_tags_relation b on a.id=b.id where a.group_id=? and a.tenant_id=? ");
//            sql = new StringBuilder(
//                "select a.ID,a.data_id,a.group_id,a.tenant_id,a.app_name,a.content from config_info  a left join "
//                    + "config_tags_relation b on a.id=b.id where a.group_id=? and a.tenant_id=? ");
//
//            sqlCount.append(" and b.tag_name in (");
//            sql.append(" and b.tag_name in (");
//            String[] tagArr = configTags.split(",");
//            for (int i = 0; i < tagArr.length; i++) {
//                if (i != 0) {
//                    sqlCount.append(", ");
//                    sql.append(", ");
//                }
//                sqlCount.append("?");
//                sql.append("?");
//                paramList.add(tagArr[i]);
//            }
//            sqlCount.append(") ");
//            sql.append(") ");
//
//            if (StringUtils.isNotBlank(appName)) {
//                sqlCount.append(" and a.app_name=? ");
//                sql.append(" and a.app_name=? ");
//                paramList.add(appName);
//            }
//        } else {
//            if (StringUtils.isNotBlank(appName)) {
//                sqlCount.append(" and app_name=? ");
//                sql.append(" and app_name=? ");
//                paramList.add(appName);
//            }
//        }
//
//        try {
//            return helper.fetchPage(sqlCount.toString(), sql.toString(), paramList.toArray(), pageNo, pageSize,
//                CONFIG_INFO_ROW_MAPPER);
//        } catch (CannotGetJdbcConnectionException e) {
//            LogUtil.FATAL_LOG.error("[db-error] " + e.toString(), e);
//            throw e;
//        }
        return null;
    }

    @Override
    public Page<ConfigInfo> findConfigInfoByApp(final int pageNo, final int pageSize, final String tenant,
                                                final String appName) {
//        String tenantTmp = StringUtils.isBlank(tenant) ? StringUtils.EMPTY : tenant;
//        PaginationHelper<ConfigInfo> helper = createPaginationHelper();
//        try {
//            return helper.fetchPage("select count(*) from config_info where tenant_id like ? and app_name=?",
//                "select ID,data_id,group_id,tenant_id,app_name,content from config_info where tenant_id like ? and app_name=?",
//                new Object[]{generateLikeArgument(tenantTmp), appName}, pageNo, pageSize, CONFIG_INFO_ROW_MAPPER);
//        } catch (CannotGetJdbcConnectionException e) {
//            LogUtil.FATAL_LOG.error("[db-error] " + e.toString(), e);
//            throw e;
//        }
        return null;
    }

    @Override
    public Page<ConfigInfo> findConfigInfoByAdvance(final int pageNo, final int pageSize, final String tenant,
                                                    final Map<String, Object> configAdvanceInfo) {
//        String tenantTmp = StringUtils.isBlank(tenant) ? StringUtils.EMPTY : tenant;
//        PaginationHelper<ConfigInfo> helper = createPaginationHelper();
//        final String appName = configAdvanceInfo == null ? null : (String) configAdvanceInfo.get("appName");
//        final String configTags = configAdvanceInfo == null ? null : (String) configAdvanceInfo.get("config_tags");
//        StringBuilder sqlCount = new StringBuilder("select count(*) from config_info where tenant_id like ? ");
//        StringBuilder sql = new StringBuilder(
//            "select ID,data_id,group_id,tenant_id,app_name,content from config_info where tenant_id like ? ");
//        List<String> paramList = new ArrayList<String>();
//        paramList.add(tenantTmp);
//        if (StringUtils.isNotBlank(configTags)) {
//            sqlCount = new StringBuilder(
//                "select count(*) from config_info a left join config_tags_relation b on a.id=b.id where a.tenant_id=? ");
//
//            sql = new StringBuilder(
//                "select a.ID,a.data_id,a.group_id,a.tenant_id,a.app_name,a.content from config_info  a left join "
//                    + "config_tags_relation b on a.id=b.id where a.tenant_id=? ");
//
//            sqlCount.append(" and b.tag_name in (");
//            sql.append(" and b.tag_name in (");
//            String[] tagArr = configTags.split(",");
//            for (int i = 0; i < tagArr.length; i++) {
//                if (i != 0) {
//                    sqlCount.append(", ");
//                    sql.append(", ");
//                }
//                sqlCount.append("?");
//                sql.append("?");
//                paramList.add(tagArr[i]);
//            }
//            sqlCount.append(") ");
//            sql.append(") ");
//
//            if (StringUtils.isNotBlank(appName)) {
//                sqlCount.append(" and a.app_name=? ");
//                sql.append(" and a.app_name=? ");
//                paramList.add(appName);
//            }
//        } else {
//            if (StringUtils.isNotBlank(appName)) {
//                sqlCount.append(" and app_name=? ");
//                sql.append(" and app_name=? ");
//                paramList.add(appName);
//            }
//        }
//
//        try {
//            return helper.fetchPage(sqlCount.toString(), sql.toString(), paramList.toArray(), pageNo, pageSize,
//                CONFIG_INFO_ROW_MAPPER);
//        } catch (CannotGetJdbcConnectionException e) {
//            LogUtil.FATAL_LOG.error("[db-error] " + e.toString(), e);
//            throw e;
//        }
        return null;
    }

    @Override
    public Page<ConfigInfoBase> findConfigInfoBaseByGroup(final int pageNo, final int pageSize, final String group) {
//        PaginationHelper<ConfigInfoBase> helper = createPaginationHelper();
//        try {
//            return helper.fetchPage("select count(*) from config_info where group_id=? and tenant_id=?",
//                "select ID,data_id,group_id,content from config_info where group_id=? and tenant_id=?",
//                new Object[]{group, StringUtils.EMPTY}, pageNo, pageSize, CONFIG_INFO_BASE_ROW_MAPPER);
//        } catch (CannotGetJdbcConnectionException e) {
//            LogUtil.FATAL_LOG.error("[db-error] " + e.toString(), e);
//            throw e;
//        }
        return null;
    }

    @Override
    public int configInfoCount() {
//        String sql = " SELECT COUNT(ID) FROM config_info ";
//        Integer result = jt.queryForObject(sql, Integer.class);
//        if (result == null) {
//            throw new IllegalArgumentException("configInfoCount error");
//        }
//        return result.intValue();
        Long result = configInfoRepository.count();
        if (result == null) {
            throw new IllegalArgumentException("configInfoCount error");
        }
        return result.intValue();
    }

    @Override
    public int configInfoCount(String tenant) {
//        String sql = " SELECT COUNT(ID) FROM config_info where tenant_id like '" + tenant + "'";
//        Integer result = jt.queryForObject(sql, Integer.class);
//        if (result == null) {
//            throw new IllegalArgumentException("configInfoCount error");
//        }
//        return result.intValue();
        QConfigInfo qConfigInfo = QConfigInfo.configInfo;
        Long result = configInfoRepository.count(qConfigInfo.tenantId.like(tenant));
        if (result == null) {
            throw new IllegalArgumentException("configInfoCount error");
        }
        return result.intValue();
    }

    @Override
    public int configInfoBetaCount() {
//        String sql = " SELECT COUNT(ID) FROM config_info_beta ";
//        Integer result = jt.queryForObject(sql, Integer.class);
//        if (result == null) {
//            throw new IllegalArgumentException("configInfoBetaCount error");
//        }
//        return result.intValue();
        Long result = configInfoBetaRepository.count();
        if (result == null) {
            throw new IllegalArgumentException("configInfoBetaCount error");
        }
        return result.intValue();
    }

    @Override
    public int configInfoTagCount() {
//        String sql = " SELECT COUNT(ID) FROM config_info_tag ";
//        Integer result = jt.queryForObject(sql, Integer.class);
//        if (result == null) {
//            throw new IllegalArgumentException("configInfoBetaCount error");
//        }
//        return result.intValue();
        Long result = configInfoTagRepository.count();
        if (result == null) {
            throw new IllegalArgumentException("configInfoBetaCount error");
        }
        return result.intValue();
    }

    @Override
    public List<String> getTenantIdList(int pageNo, int pageSize) {
//        String sql = "SELECT tenant_id FROM config_info WHERE tenant_id != '' GROUP BY tenant_id LIMIT ?, ?";
//        int from = (page - 1) * pageSize;
//        return jt.queryForList(sql, String.class, from, pageSize);
        Specification<ConfigInfoEntity> specification = new Specification<ConfigInfoEntity>() {
            @Override
            public Predicate toPredicate(Root<ConfigInfoEntity> root, CriteriaQuery<?> query, CriteriaBuilder criteriaBuilder) {
                return query.groupBy(root.get("tenantId")).getRestriction();
            }
        };
        org.springframework.data.domain.Page<ConfigInfoEntity> page = configInfoRepository.findAll(specification, PageRequest.of(pageNo, pageSize));
        return page.getContent().stream().map(config -> config.getGroupId()).collect(Collectors.toList());
    }

    @Override
    public List<String> getGroupIdList(int pageNo, int pageSize) {
//        String sql = "SELECT group_id FROM config_info WHERE tenant_id ='' GROUP BY group_id LIMIT ?, ?";
//        int from = (page - 1) * pageSize;
//        return jt.queryForList(sql, String.class, from, pageSize);
        Specification<ConfigInfoEntity> specification = new Specification<ConfigInfoEntity>() {
            @Override
            public Predicate toPredicate(Root<ConfigInfoEntity> root, CriteriaQuery<?> query, CriteriaBuilder criteriaBuilder) {
                return query.groupBy(root.get("groupId")).getRestriction();
            }
        };
        org.springframework.data.domain.Page<ConfigInfoEntity> page = configInfoRepository.findAll(specification, PageRequest.of(pageNo, pageSize));
        return page.getContent().stream().map(config -> config.getGroupId()).collect(Collectors.toList());
    }

    @Override
    public int aggrConfigInfoCount(String dataId, String group, String tenant) {
//        String tenantTmp = StringUtils.isBlank(tenant) ? StringUtils.EMPTY : tenant;
//        String sql = " SELECT COUNT(ID) FROM config_info_aggr WHERE data_id = ? AND group_id = ? AND tenant_id = ?";
//        Integer result = jt.queryForObject(sql, Integer.class, new Object[]{dataId, group, tenantTmp});
//        if (result == null) {
//            throw new IllegalArgumentException("aggrConfigInfoCount error");
//        }
//        return result.intValue();
        String tenantTmp = StringUtils.isBlank(tenant) ? StringUtils.EMPTY : tenant;
        QConfigInfo qConfigInfo = QConfigInfo.configInfo;
        Long result = configInfoRepository.count(qConfigInfo.dataId.eq(dataId)
            .and(qConfigInfo.groupId.eq(group))
            .and(qConfigInfo.tenantId.eq(tenantTmp)));
        if (result == null) {
            throw new IllegalArgumentException("aggrConfigInfoCount error");
        }
        return result.intValue();
    }

    @Override
    public int aggrConfigInfoCount(String dataId, String group, String tenant, List<String> datumIds, boolean isIn) {
//        if (datumIds == null || datumIds.isEmpty()) {
//            return 0;
//        }
//        final String tenantTmp = StringUtils.isBlank(tenant) ? StringUtils.EMPTY : tenant;
//        StringBuilder sql = new StringBuilder(
//            " SELECT COUNT(*) FROM config_info_aggr WHERE data_id = ? and group_id = ? and tenant_id = ? and datum_id");
//        if (isIn) {
//            sql.append(" in (");
//        } else {
//            sql.append(" not in (");
//        }
//        for (int i = 0, size = datumIds.size(); i < size; i++) {
//            if (i > 0) {
//                sql.append(", ");
//            }
//            sql.append("?");
//        }
//        sql.append(")");
//
//        List<Object> objectList = Lists.<Object>newArrayList(dataId, group, tenantTmp);
//        objectList.addAll(datumIds);
//        Integer result = jt.queryForObject(sql.toString(), Integer.class, objectList.toArray());
//        if (result == null) {
//            throw new IllegalArgumentException("aggrConfigInfoCount error");
//        }
//        return result.intValue();
        return 0;
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
//        String tenantTmp = StringUtils.isBlank(tenant) ? StringUtils.EMPTY : tenant;
//        String sqlCountRows = "SELECT COUNT(*) FROM config_info";
//        String sqlFetchRows = " SELECT t.id,data_id,group_id,tenant_id,app_name,content,md5 "
//            + " FROM (  SELECT id FROM config_info WHERE tenant_id like ? ORDER BY id LIMIT ?,? )"
//            + " g, config_info t  WHERE g.id = t.id ";
//
//        PaginationHelper<ConfigInfo> helper = createPaginationHelper();
//        try {
//            return helper.fetchPageLimit(sqlCountRows, sqlFetchRows,
//                new Object[]{generateLikeArgument(tenantTmp), (pageNo - 1) * pageSize, pageSize}, pageNo, pageSize,
//                CONFIG_INFO_ROW_MAPPER);
//        } catch (CannotGetJdbcConnectionException e) {
//            LogUtil.FATAL_LOG.error("[db-error] " + e.toString(), e);
//            throw e;
//        }
        return null;
    }

    @Override
    public Page<ConfigKey> findAllConfigKey(final int pageNo, final int pageSize, final String tenant) {
//        String tenantTmp = StringUtils.isBlank(tenant) ? StringUtils.EMPTY : tenant;
//        String select = " SELECT data_id,group_id,app_name  FROM ( "
//            + " SELECT id FROM config_info WHERE tenant_id LIKE ? ORDER BY id LIMIT ?, ?  )"
//            + " g, config_info t WHERE g.id = t.id  ";
//
//        final int totalCount = configInfoCount(tenant);
//        int pageCount = totalCount / pageSize;
//        if (totalCount > pageSize * pageCount) {
//            pageCount++;
//        }
//
//        if (pageNo > pageCount) {
//            return null;
//        }
//
//        final Page<ConfigKey> page = new Page<ConfigKey>();
//        page.setPageNumber(pageNo);
//        page.setPagesAvailable(pageCount);
//        page.setTotalCount(totalCount);
//
//        try {
//            List<ConfigKey> result = jt
//                .query(select, new Object[]{generateLikeArgument(tenantTmp), (pageNo - 1) * pageSize, pageSize},
//                    // new Object[0],
//                    CONFIG_KEY_ROW_MAPPER);
//
//            for (ConfigKey item : result) {
//                page.getPageItems().add(item);
//            }
//            return page;
//        } catch (EmptyResultDataAccessException e) {
//            return page;
//        } catch (CannotGetJdbcConnectionException e) {
//            LogUtil.FATAL_LOG.error("[db-error] " + e.toString(), e);
//            throw e;
//        }
        return null;
    }

    @Override
    @Deprecated
    public Page<ConfigInfoBase> findAllConfigInfoBase(final int pageNo, final int pageSize) {
//        String sqlCountRows = "SELECT COUNT(*) FROM config_info";
//        String sqlFetchRows = " SELECT t.id,data_id,group_id,content,md5"
//            + " FROM ( SELECT id FROM config_info ORDER BY id LIMIT ?,?  ) "
//            + " g, config_info t  WHERE g.id = t.id ";
//
//        PaginationHelper<ConfigInfoBase> helper = createPaginationHelper();
//        try {
//            return helper.fetchPageLimit(sqlCountRows, sqlFetchRows, new Object[]{(pageNo - 1) * pageSize, pageSize},
//                pageNo, pageSize, CONFIG_INFO_BASE_ROW_MAPPER);
//        } catch (CannotGetJdbcConnectionException e) {
//            LogUtil.FATAL_LOG.error("[db-error] " + e.toString(), e);
//            throw e;
//        }
        return null;
    }

    @Override
    public Page<ConfigInfoWrapper> findAllConfigInfoForDumpAll(final int pageNo, final int pageSize) {
//        String sqlCountRows = "select count(*) from config_info";
//        String sqlFetchRows = " SELECT t.id,type,data_id,group_id,tenant_id,app_name,content,md5,gmt_modified "
//            + " FROM ( SELECT id FROM config_info   ORDER BY id LIMIT ?,?  )"
//            + " g, config_info t WHERE g.id = t.id ";
//        PaginationHelper<ConfigInfoWrapper> helper = createPaginationHelper();
//
//        List<String> params = new ArrayList<String>();
//
//        try {
//            return helper.fetchPageLimit(sqlCountRows, sqlFetchRows, params.toArray(), pageNo, pageSize,
//                CONFIG_INFO_WRAPPER_ROW_MAPPER);
//        } catch (CannotGetJdbcConnectionException e) {
//            LogUtil.FATAL_LOG.error("[db-error] " + e.toString(), e);
//            throw e;
//        }
        return null;
    }

    @Override
    public Page<ConfigInfoWrapper> findAllConfigInfoFragment(final long lastMaxId, final int pageSize) {
//        String select = "SELECT id,data_id,group_id,tenant_id,app_name,content,md5,gmt_modified,type from config_info where id > ? order by id asc limit ?,?";
//        PaginationHelper<ConfigInfoWrapper> helper = createPaginationHelper();
//        try {
//            return helper.fetchPageLimit(select, new Object[]{lastMaxId, 0, pageSize}, 1, pageSize,
//                CONFIG_INFO_WRAPPER_ROW_MAPPER);
//        } catch (CannotGetJdbcConnectionException e) {
//            LogUtil.FATAL_LOG.error("[db-error] " + e.toString(), e);
//            throw e;
//        }
        QConfigInfo qConfigInfo = QConfigInfo.configInfo;
        org.springframework.data.domain.Page<ConfigInfoEntity> sPage = configInfoRepository.findAll(qConfigInfo.id.gt(lastMaxId),
            PageRequest.of(0, pageSize, Sort.by(Sort.Order.asc("id"))));

        Page<ConfigInfoWrapper> page = new Page<>();
        page.setPageNumber(sPage.getNumber());
        page.setPagesAvailable(sPage.getTotalPages());
        page.setPageItems(ConfigInfoWrapperMapStruct.INSTANCE.convertConfigInfoWrapperList(sPage.getContent()));
        page.setTotalCount((int) sPage.getTotalElements());
        return page;
    }

    @Override
    public Page<ConfigInfoBetaWrapper> findAllConfigInfoBetaForDumpAll(final int pageNo, final int pageSize) {
//        String sqlCountRows = "SELECT COUNT(*) FROM config_info_beta";
//        String sqlFetchRows = " SELECT t.id,data_id,group_id,tenant_id,app_name,content,md5,gmt_modified,beta_ips "
//            + " FROM ( SELECT id FROM config_info_beta  ORDER BY id LIMIT ?,?  )"
//            + "  g, config_info_beta t WHERE g.id = t.id ";
//        PaginationHelper<ConfigInfoBetaWrapper> helper = createPaginationHelper();
//        try {
//            return helper.fetchPageLimit(sqlCountRows, sqlFetchRows, new Object[]{(pageNo - 1) * pageSize, pageSize},
//                pageNo, pageSize, CONFIG_INFO_BETA_WRAPPER_ROW_MAPPER);
//
//        } catch (CannotGetJdbcConnectionException e) {
//            LogUtil.FATAL_LOG.error("[db-error] " + e.toString(), e);
//            throw e;
//        }
        org.springframework.data.domain.Page<ConfigInfoBetaEntity> sPage = configInfoBetaRepository
            .findAll(null, PageRequest.of(pageNo, pageSize));
        Page<ConfigInfoBetaWrapper> page = new Page<>();
        page.setPageNumber(sPage.getNumber());
        page.setPagesAvailable(sPage.getTotalPages());
        page.setPageItems(ConfigInfoBetaWrapperMapStruct.INSTANCE.convertConfigInfoBetaWrapperList(sPage.getContent()));
        page.setTotalCount((int) sPage.getTotalElements());
        return page;
    }

    @Override
    public Page<ConfigInfoTagWrapper> findAllConfigInfoTagForDumpAll(final int pageNo, final int pageSize) {
//        String sqlCountRows = "SELECT COUNT(*) FROM config_info_tag";
//        String sqlFetchRows = " SELECT t.id,data_id,group_id,tenant_id,tag_id,app_name,content,md5,gmt_modified "
//            + " FROM (  SELECT id FROM config_info_tag  ORDER BY id LIMIT ?,? ) "
//            + "g, config_info_tag t  WHERE g.id = t.id  ";
//        PaginationHelper<ConfigInfoTagWrapper> helper = createPaginationHelper();
//        try {
//            return helper.fetchPageLimit(sqlCountRows, sqlFetchRows, new Object[]{(pageNo - 1) * pageSize, pageSize},
//                pageNo, pageSize, CONFIG_INFO_TAG_WRAPPER_ROW_MAPPER);
//
//        } catch (CannotGetJdbcConnectionException e) {
//            LogUtil.FATAL_LOG.error("[db-error] " + e.toString(), e);
//            throw e;
//        }
        org.springframework.data.domain.Page<ConfigInfoTagEntity> sPage = configInfoTagRepository
            .findAll(null, PageRequest.of(pageNo, pageSize));
        Page<ConfigInfoTagWrapper> page = new Page<>();
        page.setPageNumber(sPage.getNumber());
        page.setPagesAvailable(sPage.getTotalPages());
        page.setPageItems(ConfigInfoTagWrapperMapStruct.INSTANCE.convertConfigInfoTagWrapperList(sPage.getContent()));
        page.setTotalCount((int) sPage.getTotalElements());
        return page;
    }

    @Override
    public List<ConfigInfo> findConfigInfoByBatch(final List<String> dataIds, final String group, final String tenant,
                                                  int subQueryLimit) {
//        // assert dataids group not null
//        String tenantTmp = StringUtils.isBlank(tenant) ? StringUtils.EMPTY : tenant;
//        // if dataids empty return empty list
//        if (CollectionUtils.isEmpty(dataIds)) {
//            return Collections.emptyList();
//        }
//
//        // Batch query limit
//        // The number of in is controlled within 100, the shorter the length of the SQL statement, the better
//        if (subQueryLimit > QUERY_LIMIT_SIZE) {
//            subQueryLimit = 50;
//        }
//        List<ConfigInfo> result = new ArrayList<ConfigInfo>(dataIds.size());
//
//        String sqlStart = "select data_id, group_id, tenant_id, app_name, content from config_info where group_id = ? and tenant_id = ? and data_id in (";
//        String sqlEnd = ")";
//        StringBuilder subQuerySql = new StringBuilder();
//
//        for (int i = 0; i < dataIds.size(); i += subQueryLimit) {
//            // dataids
//            List<String> params = new ArrayList<String>(
//                dataIds.subList(i, i + subQueryLimit < dataIds.size() ? i + subQueryLimit : dataIds.size()));
//
//            for (int j = 0; j < params.size(); j++) {
//                subQuerySql.append("?");
//                if (j != params.size() - 1) {
//                    subQuerySql.append(",");
//                }
//            }
//
//            // group
//            params.add(0, group);
//            params.add(1, tenantTmp);
//
//            List<ConfigInfo> r = this.jt
//                .query(sqlStart + subQuerySql.toString() + sqlEnd, params.toArray(), CONFIG_INFO_ROW_MAPPER);
//
//            // assert not null
//            if (r != null && r.size() > 0) {
//                result.addAll(r);
//            }
//        }
//        return result;
        return null;
    }

    @Override
    public Page<ConfigInfo> findConfigInfoLike(final int pageNo, final int pageSize, final String dataId,
                                               final String group, final String tenant, final String appName, final String content) {
//        String tenantTmp = StringUtils.isBlank(tenant) ? StringUtils.EMPTY : tenant;
//        if (StringUtils.isBlank(dataId) && StringUtils.isBlank(group)) {
//            if (StringUtils.isBlank(appName)) {
//                return this.findAllConfigInfo(pageNo, pageSize, tenantTmp);
//            } else {
//                return this.findConfigInfoByApp(pageNo, pageSize, tenantTmp, appName);
//            }
//        }
//
//        PaginationHelper<ConfigInfo> helper = createPaginationHelper();
//
//        String sqlCountRows = "select count(*) from config_info where ";
//        String sqlFetchRows = "select ID,data_id,group_id,tenant_id,app_name,content from config_info where ";
//        String where = " 1=1 ";
//        List<String> params = new ArrayList<String>();
//
//        if (!StringUtils.isBlank(dataId)) {
//            where += " and data_id like ? ";
//            params.add(generateLikeArgument(dataId));
//        }
//        if (!StringUtils.isBlank(group)) {
//            where += " and group_id like ? ";
//            params.add(generateLikeArgument(group));
//        }
//
//        where += " and tenant_id like ? ";
//        params.add(generateLikeArgument(tenantTmp));
//
//        if (!StringUtils.isBlank(appName)) {
//            where += " and app_name = ? ";
//            params.add(appName);
//        }
//        if (!StringUtils.isBlank(content)) {
//            where += " and content like ? ";
//            params.add(generateLikeArgument(content));
//        }
//
//        try {
//            return helper.fetchPage(sqlCountRows + where, sqlFetchRows + where, params.toArray(), pageNo, pageSize,
//                CONFIG_INFO_ROW_MAPPER);
//        } catch (CannotGetJdbcConnectionException e) {
//            LogUtil.FATAL_LOG.error("[db-error] " + e.toString(), e);
//            throw e;
//        }
        return null;
    }

    @Override
    public Page<ConfigInfo> findConfigInfoLike(final int pageNo, final int pageSize, final ConfigKey[] configKeys,
                                               final boolean blacklist) {
//        String sqlCountRows = "select count(*) from config_info where ";
//        String sqlFetchRows = "select ID,data_id,group_id,tenant_id,app_name,content from config_info where ";
//        StringBuilder where = new StringBuilder(" 1=1 ");
//        // Whitelist, please leave the synchronization condition empty, there is no configuration that meets the conditions
//        if (configKeys.length == 0 && blacklist == false) {
//            Page<ConfigInfo> page = new Page<ConfigInfo>();
//            page.setTotalCount(0);
//            return page;
//        }
//        PaginationHelper<ConfigInfo> helper = createPaginationHelper();
//        List<String> params = new ArrayList<String>();
//        boolean isFirst = true;
//        for (ConfigKey configInfo : configKeys) {
//            String dataId = configInfo.getDataId();
//            String group = configInfo.getGroup();
//            String appName = configInfo.getAppName();
//
//            if (StringUtils.isBlank(dataId) && StringUtils.isBlank(group) && StringUtils.isBlank(appName)) {
//                break;
//            }
//
//            if (blacklist) {
//                if (isFirst) {
//                    isFirst = false;
//                    where.append(" and ");
//                } else {
//                    where.append(" and ");
//                }
//
//                where.append("(");
//                boolean isFirstSub = true;
//                if (!StringUtils.isBlank(dataId)) {
//                    where.append(" data_id not like ? ");
//                    params.add(generateLikeArgument(dataId));
//                    isFirstSub = false;
//                }
//                if (!StringUtils.isBlank(group)) {
//                    if (!isFirstSub) {
//                        where.append(" or ");
//                    }
//                    where.append(" group_id not like ? ");
//                    params.add(generateLikeArgument(group));
//                    isFirstSub = false;
//                }
//                if (!StringUtils.isBlank(appName)) {
//                    if (!isFirstSub) {
//                        where.append(" or ");
//                    }
//                    where.append(" app_name != ? ");
//                    params.add(appName);
//                    isFirstSub = false;
//                }
//                where.append(") ");
//            } else {
//                if (isFirst) {
//                    isFirst = false;
//                    where.append(" and ");
//                } else {
//                    where.append(" or ");
//                }
//                where.append("(");
//                boolean isFirstSub = true;
//                if (!StringUtils.isBlank(dataId)) {
//                    where.append(" data_id like ? ");
//                    params.add(generateLikeArgument(dataId));
//                    isFirstSub = false;
//                }
//                if (!StringUtils.isBlank(group)) {
//                    if (!isFirstSub) {
//                        where.append(" and ");
//                    }
//                    where.append(" group_id like ? ");
//                    params.add(generateLikeArgument(group));
//                    isFirstSub = false;
//                }
//                if (!StringUtils.isBlank(appName)) {
//                    if (!isFirstSub) {
//                        where.append(" and ");
//                    }
//                    where.append(" app_name = ? ");
//                    params.add(appName);
//                    isFirstSub = false;
//                }
//                where.append(") ");
//            }
//        }
//
//        try {
//            return helper.fetchPage(sqlCountRows + where.toString(), sqlFetchRows + where.toString(), params.toArray(),
//                pageNo, pageSize, CONFIG_INFO_ROW_MAPPER);
//        } catch (CannotGetJdbcConnectionException e) {
//            LogUtil.FATAL_LOG.error("[db-error] " + e.toString(), e);
//            throw e;
//        }
        return null;
    }

    @Override
    public Page<ConfigInfo> findConfigInfoLike4Page(final int pageNo, final int pageSize, final String dataId,
                                                    final String group, final String tenant, final Map<String, Object> configAdvanceInfo) {
//        String tenantTmp = StringUtils.isBlank(tenant) ? StringUtils.EMPTY : tenant;
//        final String appName = configAdvanceInfo == null ? null : (String) configAdvanceInfo.get("appName");
//        final String content = configAdvanceInfo == null ? null : (String) configAdvanceInfo.get("content");
//        final String configTags = configAdvanceInfo == null ? null : (String) configAdvanceInfo.get("config_tags");
//        PaginationHelper<ConfigInfo> helper = createPaginationHelper();
//        String sqlCountRows = "select count(*) from config_info";
//        String sqlFetchRows = "select ID,data_id,group_id,tenant_id,app_name,content from config_info";
//        StringBuilder where = new StringBuilder(" where ");
//        List<String> params = new ArrayList<String>();
//        params.add(generateLikeArgument(tenantTmp));
//        if (StringUtils.isNotBlank(configTags)) {
//            sqlCountRows = "select count(*) from config_info  a left join config_tags_relation b on a.id=b.id ";
//            sqlFetchRows = "select a.ID,a.data_id,a.group_id,a.tenant_id,a.app_name,a.content from config_info a left join config_tags_relation b on a.id=b.id ";
//
//            where.append(" a.tenant_id like ? ");
//            if (!StringUtils.isBlank(dataId)) {
//                where.append(" and a.data_id like ? ");
//                params.add(generateLikeArgument(dataId));
//            }
//            if (!StringUtils.isBlank(group)) {
//                where.append(" and a.group_id like ? ");
//                params.add(generateLikeArgument(group));
//            }
//            if (!StringUtils.isBlank(appName)) {
//                where.append(" and a.app_name = ? ");
//                params.add(appName);
//            }
//            if (!StringUtils.isBlank(content)) {
//                where.append(" and a.content like ? ");
//                params.add(generateLikeArgument(content));
//            }
//
//            where.append(" and b.tag_name in (");
//            String[] tagArr = configTags.split(",");
//            for (int i = 0; i < tagArr.length; i++) {
//                if (i != 0) {
//                    where.append(", ");
//                }
//                where.append("?");
//                params.add(tagArr[i]);
//            }
//            where.append(") ");
//        } else {
//            where.append(" tenant_id like ? ");
//            if (!StringUtils.isBlank(dataId)) {
//                where.append(" and data_id like ? ");
//                params.add(generateLikeArgument(dataId));
//            }
//            if (!StringUtils.isBlank(group)) {
//                where.append(" and group_id like ? ");
//                params.add(generateLikeArgument(group));
//            }
//            if (!StringUtils.isBlank(appName)) {
//                where.append(" and app_name = ? ");
//                params.add(appName);
//            }
//            if (!StringUtils.isBlank(content)) {
//                where.append(" and content like ? ");
//                params.add(generateLikeArgument(content));
//            }
//        }
//
//        try {
//            return helper.fetchPage(sqlCountRows + where, sqlFetchRows + where, params.toArray(), pageNo, pageSize,
//                CONFIG_INFO_ROW_MAPPER);
//        } catch (CannotGetJdbcConnectionException e) {
//            LogUtil.FATAL_LOG.error("[db-error] " + e.toString(), e);
//            throw e;
//        }
        String tenantTmp = StringUtils.isBlank(tenant) ? StringUtils.EMPTY : tenant;
        final String appName = configAdvanceInfo == null ? null : (String) configAdvanceInfo.get("appName");
        final String content = configAdvanceInfo == null ? null : (String) configAdvanceInfo.get("content");
        final String configTags = configAdvanceInfo == null ? null : (String) configAdvanceInfo.get("config_tags");
        BooleanBuilder booleanBuilder = new BooleanBuilder();
        QConfigInfo qConfigInfo = QConfigInfo.configInfo;
        buildConfigInfoCommonCondition(booleanBuilder, qConfigInfo, dataId, group, appName);
        if (StringUtils.isNotBlank(tenant)) {
            booleanBuilder.and(qConfigInfo.tenantId.like(tenant));
        }
//        if (StringUtils.isNotBlank(content)) {
//            booleanBuilder.and(qConfigInfo.content.like(content));
//        }
        org.springframework.data.domain.Page<ConfigInfoEntity> sPage = configInfoRepository.findAll(booleanBuilder, PageRequest.of(pageNo, pageSize, Sort.by(Sort.Order.desc("gmtCreate"))));
        Page<ConfigInfo> page = new Page<>();
        page.setPageNumber(sPage.getNumber());
        page.setPagesAvailable(sPage.getTotalPages());
        page.setPageItems(ConfigInfoMapStruct.INSTANCE.convertConfigInfoList2(sPage.getContent()));
        page.setTotalCount((int) sPage.getTotalElements());
        return page;
    }

    @Override
    public Page<ConfigInfoBase> findConfigInfoBaseLike(final int pageNo, final int pageSize, final String dataId,
                                                       final String group, final String content) throws IOException {
//        if (StringUtils.isBlank(dataId) && StringUtils.isBlank(group)) {
//            throw new IOException("invalid param");
//        }
//
//        PaginationHelper<ConfigInfoBase> helper = createPaginationHelper();
//
//        String sqlCountRows = "select count(*) from config_info where ";
//        String sqlFetchRows = "select ID,data_id,group_id,tenant_id,content from config_info where ";
//        String where = " 1=1 and tenant_id='' ";
//        List<String> params = new ArrayList<String>();
//
//        if (!StringUtils.isBlank(dataId)) {
//            where += " and data_id like ? ";
//            params.add(generateLikeArgument(dataId));
//        }
//        if (!StringUtils.isBlank(group)) {
//            where += " and group_id like ? ";
//            params.add(generateLikeArgument(group));
//        }
//        if (!StringUtils.isBlank(content)) {
//            where += " and content like ? ";
//            params.add(generateLikeArgument(content));
//        }
//
//        try {
//            return helper.fetchPage(sqlCountRows + where, sqlFetchRows + where, params.toArray(), pageNo, pageSize,
//                CONFIG_INFO_BASE_ROW_MAPPER);
//        } catch (CannotGetJdbcConnectionException e) {
//            LogUtil.FATAL_LOG.error("[db-error] " + e.toString(), e);
//            throw e;
//        }
        return null;
    }

    @Override
    public ConfigInfoAggr findSingleConfigInfoAggr(String dataId, String group, String tenant, String datumId) {
//        String tenantTmp = StringUtils.isBlank(tenant) ? StringUtils.EMPTY : tenant;
//        String sql = "SELECT id,data_id,group_id,tenant_id,datum_id,app_name,content FROM config_info_aggr WHERE data_id=? AND group_id=? AND tenant_id=? AND datum_id=?";
//
//        try {
//            return this.jt
//                .queryForObject(sql, new Object[]{dataId, group, tenantTmp, datumId}, CONFIG_INFO_AGGR_ROW_MAPPER);
//        } catch (EmptyResultDataAccessException e) {
//            // EmptyResultDataAccessException, indicating that the data does not exist, returns null
//            return null;
//        } catch (CannotGetJdbcConnectionException e) {
//            LogUtil.FATAL_LOG.error("[db-error] " + e.toString(), e);
//            throw e;
//        } catch (Exception e) {
//            LogUtil.FATAL_LOG.error("[db-other-error]" + e.getMessage(), e);
//            throw new RuntimeException(e);
//        }
        String tenantTmp = StringUtils.isBlank(tenant) ? StringUtils.EMPTY : tenant;
        QConfigInfoAggr qConfigInfoAggr = QConfigInfoAggr.configInfoAggr;
        BooleanBuilder booleanBuilder = new BooleanBuilder();
        if (StringUtils.isNotBlank(dataId)) {
            booleanBuilder.and(qConfigInfoAggr.dataId.eq(dataId));
        }
        if (StringUtils.isNotBlank(group)) {
            booleanBuilder.and(qConfigInfoAggr.groupId.eq(group));
        }
        if (StringUtils.isNotBlank(tenantTmp)) {
            booleanBuilder.and(qConfigInfoAggr.tenantId.eq(tenantTmp));
        }
        if (StringUtils.isNotBlank(datumId)) {
            booleanBuilder.and(qConfigInfoAggr.datumId.eq(datumId));
        }

        ConfigInfoAggrEntity configInfoAggrEntity = configInfoAggrRepository.findOne(booleanBuilder)
            .orElse(null);
        return ConfigInfoAggrMapStruct.INSTANCE.convertConfigInfoAggr(configInfoAggrEntity);

    }

    @Override
    public List<ConfigInfoAggr> findConfigInfoAggr(String dataId, String group, String tenant) {
//        String tenantTmp = StringUtils.isBlank(tenant) ? StringUtils.EMPTY : tenant;
//        String sql = "SELECT data_id,group_id,tenant_id,datum_id,app_name,content FROM config_info_aggr WHERE data_id=? AND group_id=? AND tenant_id=? ORDER BY datum_id";
//
//        try {
//            return this.jt.query(sql, new Object[]{dataId, group, tenantTmp}, CONFIG_INFO_AGGR_ROW_MAPPER);
//        } catch (CannotGetJdbcConnectionException e) {
//            LogUtil.FATAL_LOG.error("[db-error] " + e.toString(), e);
//            throw e;
//        } catch (EmptyResultDataAccessException e) {
//            return Collections.emptyList();
//        } catch (Exception e) {
//            LogUtil.FATAL_LOG.error("[db-other-error]" + e.getMessage(), e);
//            throw new RuntimeException(e);
//        }
        return null;
    }

    @Override
    public Page<ConfigInfoAggr> findConfigInfoAggrByPage(String dataId, String group, String tenant, final int pageNo,
                                                         final int pageSize) {
//        String tenantTmp = StringUtils.isBlank(tenant) ? StringUtils.EMPTY : tenant;
//        String sqlCountRows = "SELECT COUNT(*) FROM config_info_aggr WHERE data_id = ? and group_id = ? and tenant_id = ?";
//        String sqlFetchRows =
//            "select data_id,group_id,tenant_id,datum_id,app_name,content from config_info_aggr where data_id=? and "
//                + "group_id=? and tenant_id=? order by datum_id limit ?,?";
//        PaginationHelper<ConfigInfoAggr> helper = createPaginationHelper();
//        try {
//            return helper.fetchPageLimit(sqlCountRows, new Object[]{dataId, group, tenantTmp}, sqlFetchRows,
//                new Object[]{dataId, group, tenantTmp, (pageNo - 1) * pageSize, pageSize}, pageNo, pageSize,
//                CONFIG_INFO_AGGR_ROW_MAPPER);
//
//        } catch (CannotGetJdbcConnectionException e) {
//            LogUtil.FATAL_LOG.error("[db-error] " + e.toString(), e);
//            throw e;
//        }
        String tenantTmp = StringUtils.isBlank(tenant) ? StringUtils.EMPTY : tenant;
        QConfigInfoAggr qConfigInfoAggr = QConfigInfoAggr.configInfoAggr;
        org.springframework.data.domain.Page<ConfigInfoAggrEntity> sPage = configInfoAggrRepository.findAll(qConfigInfoAggr.dataId.eq(dataId)
            .and(qConfigInfoAggr.groupId.eq(group))
            .and(qConfigInfoAggr.tenantId.eq(tenantTmp)), PageRequest.of(pageNo, pageSize, Sort.by(Sort.Order.by("datumId"))));
        Page<ConfigInfoAggr> page = new Page<>();
        page.setPageNumber(sPage.getNumber());
        page.setPagesAvailable(sPage.getTotalPages());
        page.setPageItems(ConfigInfoAggrMapStruct.INSTANCE.convertConfigInfoAggrList(sPage.getContent()));
        page.setTotalCount((int) sPage.getTotalElements());
        return page;
    }

    @Override
    public Page<ConfigInfoAggr> findConfigInfoAggrLike(final int pageNo, final int pageSize, ConfigKey[] configKeys,
                                                       boolean blacklist) {

//        String sqlCountRows = "select count(*) from config_info_aggr where ";
//        String sqlFetchRows = "select data_id,group_id,tenant_id,datum_id,app_name,content from config_info_aggr where ";
//        StringBuilder where = new StringBuilder(" 1=1 ");
//        // Whitelist, please leave the synchronization condition empty, there is no configuration that meets the conditions
//        if (configKeys.length == 0 && blacklist == false) {
//            Page<ConfigInfoAggr> page = new Page<ConfigInfoAggr>();
//            page.setTotalCount(0);
//            return page;
//        }
//        PaginationHelper<ConfigInfoAggr> helper = createPaginationHelper();
//        List<String> params = new ArrayList<String>();
//        boolean isFirst = true;
//
//        for (ConfigKey configInfoAggr : configKeys) {
//            String dataId = configInfoAggr.getDataId();
//            String group = configInfoAggr.getGroup();
//            String appName = configInfoAggr.getAppName();
//            if (StringUtils.isBlank(dataId) && StringUtils.isBlank(group) && StringUtils.isBlank(appName)) {
//                break;
//            }
//            if (blacklist) {
//                if (isFirst) {
//                    isFirst = false;
//                    where.append(" and ");
//                } else {
//                    where.append(" and ");
//                }
//
//                where.append("(");
//                boolean isFirstSub = true;
//                if (!StringUtils.isBlank(dataId)) {
//                    where.append(" data_id not like ? ");
//                    params.add(generateLikeArgument(dataId));
//                    isFirstSub = false;
//                }
//                if (!StringUtils.isBlank(group)) {
//                    if (!isFirstSub) {
//                        where.append(" or ");
//                    }
//                    where.append(" group_id not like ? ");
//                    params.add(generateLikeArgument(group));
//                    isFirstSub = false;
//                }
//                if (!StringUtils.isBlank(appName)) {
//                    if (!isFirstSub) {
//                        where.append(" or ");
//                    }
//                    where.append(" app_name != ? ");
//                    params.add(appName);
//                    isFirstSub = false;
//                }
//                where.append(") ");
//            } else {
//                if (isFirst) {
//                    isFirst = false;
//                    where.append(" and ");
//                } else {
//                    where.append(" or ");
//                }
//                where.append("(");
//                boolean isFirstSub = true;
//                if (!StringUtils.isBlank(dataId)) {
//                    where.append(" data_id like ? ");
//                    params.add(generateLikeArgument(dataId));
//                    isFirstSub = false;
//                }
//                if (!StringUtils.isBlank(group)) {
//                    if (!isFirstSub) {
//                        where.append(" and ");
//                    }
//                    where.append(" group_id like ? ");
//                    params.add(generateLikeArgument(group));
//                    isFirstSub = false;
//                }
//                if (!StringUtils.isBlank(appName)) {
//                    if (!isFirstSub) {
//                        where.append(" and ");
//                    }
//                    where.append(" app_name = ? ");
//                    params.add(appName);
//                    isFirstSub = false;
//                }
//                where.append(") ");
//            }
//        }
//
//        try {
//            Page<ConfigInfoAggr> result = helper
//                .fetchPage(sqlCountRows + where.toString(), sqlFetchRows + where.toString(), params.toArray(),
//                    pageNo, pageSize, CONFIG_INFO_AGGR_ROW_MAPPER);
//            return result;
//        } catch (CannotGetJdbcConnectionException e) {
//            LogUtil.FATAL_LOG.error("[db-error] " + e.toString(), e);
//            throw e;
//        }
        return null;
    }

    @Override
    public List<ConfigInfoChanged> findAllAggrGroup() {
//        String sql = "SELECT DISTINCT data_id, group_id, tenant_id FROM config_info_aggr";
//
//        try {
//            return jt.query(sql, new Object[]{}, CONFIG_INFO_CHANGED_ROW_MAPPER);
//        } catch (CannotGetJdbcConnectionException e) {
//            LogUtil.FATAL_LOG.error("[db-error] " + e.toString(), e);
//            throw e;
//        } catch (EmptyResultDataAccessException e) {
//            return null;
//        } catch (Exception e) {
//            LogUtil.FATAL_LOG.error("[db-other-error]" + e.getMessage(), e);
//            throw new RuntimeException(e);
//        }
        List<ConfigInfoAggrEntity> list = configInfoAggrRepository.findAllAggrGroup();
        return ConfigInfoChangedMapStruct.INSTANCE.convertConfigInfoChangedList(list);
    }

    @Override
    public List<String> findDatumIdByContent(String dataId, String groupId, String content) {
//        String sql = "SELECT datum_id FROM config_info_aggr WHERE data_id = ? AND group_id = ? AND content = ? ";
//
//        try {
//            return this.jt.queryForList(sql, new Object[]{dataId, groupId, content}, String.class);
//        } catch (EmptyResultDataAccessException e) {
//            return null;
//        } catch (IncorrectResultSizeDataAccessException e) {
//            return null;
//        } catch (CannotGetJdbcConnectionException e) {
//            LogUtil.FATAL_LOG.error("[db-error] " + e.toString(), e);
//            throw e;
//        }
        return null;
    }

    @Override
    public List<ConfigInfoWrapper> findChangeConfig(final Timestamp startTime, final Timestamp endTime) {
//        try {
//            List<Map<String, Object>> list = jt.queryForList(
//                "SELECT data_id, group_id, tenant_id, app_name, content, gmt_modified FROM config_info WHERE gmt_modified >=? AND gmt_modified <= ?",
//                new Object[]{startTime, endTime});
//            return convertChangeConfig(list);
//        } catch (DataAccessException e) {
//            LogUtil.FATAL_LOG.error("[db-error] " + e.toString(), e);
//            throw e;
//        }
        QConfigInfo qConfigInfo = QConfigInfo.configInfo;
        Iterable<ConfigInfoEntity> iterable = configInfoRepository.findAll(qConfigInfo.gmtModified.goe(startTime)
            .and(qConfigInfo.gmtModified.loe(endTime)));
        return ConfigInfoWrapperMapStruct.INSTANCE.convertConfigInfoWrapperList((List<ConfigInfoEntity>) iterable);
    }

    @Override
    public Page<ConfigInfoWrapper> findChangeConfig(final String dataId, final String group, final String tenant,
                                                    final String appName, final Timestamp startTime, final Timestamp endTime, final int pageNo,
                                                    final int pageSize, final long lastMaxId) {
//        String tenantTmp = StringUtils.isBlank(tenant) ? StringUtils.EMPTY : tenant;
//        String sqlCountRows = "select count(*) from config_info where ";
//        String sqlFetchRows = "select id,data_id,group_id,tenant_id,app_name,content,type,md5,gmt_modified from config_info where ";
//        String where = " 1=1 ";
//        List<Object> params = new ArrayList<Object>();
//
//        if (!StringUtils.isBlank(dataId)) {
//            where += " and data_id like ? ";
//            params.add(generateLikeArgument(dataId));
//        }
//        if (!StringUtils.isBlank(group)) {
//            where += " and group_id like ? ";
//            params.add(generateLikeArgument(group));
//        }
//
//        if (!StringUtils.isBlank(tenantTmp)) {
//            where += " and tenant_id = ? ";
//            params.add(tenantTmp);
//        }
//
//        if (!StringUtils.isBlank(appName)) {
//            where += " and app_name = ? ";
//            params.add(appName);
//        }
//        if (startTime != null) {
//            where += " and gmt_modified >=? ";
//            params.add(startTime);
//        }
//        if (endTime != null) {
//            where += " and gmt_modified <=? ";
//            params.add(endTime);
//        }
//
//        PaginationHelper<ConfigInfoWrapper> helper = createPaginationHelper();
//        try {
//            return helper.fetchPage(sqlCountRows + where, sqlFetchRows + where, params.toArray(), pageNo, pageSize,
//                lastMaxId, CONFIG_INFO_WRAPPER_ROW_MAPPER);
//        } catch (CannotGetJdbcConnectionException e) {
//            LogUtil.FATAL_LOG.error("[db-error] " + e.toString(), e);
//            throw e;
//        }
        return null;
    }

    @Override
    public List<ConfigInfo> findDeletedConfig(final Timestamp startTime, final Timestamp endTime) {
//        try {
//            List<Map<String, Object>> list = jt.queryForList(
//                "SELECT DISTINCT data_id, group_id, tenant_id FROM his_config_info WHERE op_type = 'D' AND gmt_modified >=? AND gmt_modified <= ?",
//                new Object[]{startTime, endTime});
//            return convertDeletedConfig(list);
//        } catch (DataAccessException e) {
//            LogUtil.FATAL_LOG.error("[db-error] " + e.toString(), e);
//            throw e;
//        }
        QHisConfigInfo qHisConfigInfo = QHisConfigInfo.hisConfigInfo;
        Iterable<HisConfigInfoEntity> iterable = hisConfigInfoRepository.findAll(qHisConfigInfo.opType.eq("D")
            .and(qHisConfigInfo.gmtModified.goe(startTime))
            .and(qHisConfigInfo.gmtModified.loe(endTime)));
        return ConfigInfoMapStruct.INSTANCE.convertConfigInfoList((List<HisConfigInfoEntity>) iterable);
    }

    @Override
    public long addConfigInfoAtomic(final long configId, final String srcIp, final String srcUser,
                                    final ConfigInfo configInfo, final Timestamp time, Map<String, Object> configAdvanceInfo) {
//        final String appNameTmp =
//            StringUtils.isBlank(configInfo.getAppName()) ? StringUtils.EMPTY : configInfo.getAppName();
//        final String tenantTmp =
//            StringUtils.isBlank(configInfo.getTenant()) ? StringUtils.EMPTY : configInfo.getTenant();
//
//        final String desc = configAdvanceInfo == null ? null : (String) configAdvanceInfo.get("desc");
//        final String use = configAdvanceInfo == null ? null : (String) configAdvanceInfo.get("use");
//        final String effect = configAdvanceInfo == null ? null : (String) configAdvanceInfo.get("effect");
//        final String type = configAdvanceInfo == null ? null : (String) configAdvanceInfo.get("type");
//        final String schema = configAdvanceInfo == null ? null : (String) configAdvanceInfo.get("schema");
//
//        final String md5Tmp = MD5Utils.md5Hex(configInfo.getContent(), Constants.ENCODE);
//
//        KeyHolder keyHolder = new GeneratedKeyHolder();
//
//        final String sql =
//            "INSERT INTO config_info(data_id,group_id,tenant_id,app_name,content,md5,src_ip,src_user,gmt_create,"
//                + "gmt_modified,c_desc,c_use,effect,type,c_schema) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)";
//
//        try {
//            jt.update(new PreparedStatementCreator() {
//                @Override
//                public PreparedStatement createPreparedStatement(Connection connection) throws SQLException {
//                    PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
//                    ps.setString(1, configInfo.getDataId());
//                    ps.setString(2, configInfo.getGroup());
//                    ps.setString(3, tenantTmp);
//                    ps.setString(4, appNameTmp);
//                    ps.setString(5, configInfo.getContent());
//                    ps.setString(6, md5Tmp);
//                    ps.setString(7, srcIp);
//                    ps.setString(8, srcUser);
//                    ps.setTimestamp(9, time);
//                    ps.setTimestamp(10, time);
//                    ps.setString(11, desc);
//                    ps.setString(12, use);
//                    ps.setString(13, effect);
//                    ps.setString(14, type);
//                    ps.setString(15, schema);
//                    return ps;
//                }
//            }, keyHolder);
//            Number nu = keyHolder.getKey();
//            if (nu == null) {
//                throw new IllegalArgumentException("insert config_info fail");
//            }
//            return nu.longValue();
//        } catch (CannotGetJdbcConnectionException e) {
//            LogUtil.FATAL_LOG.error("[db-error] " + e.toString(), e);
//            throw e;
//        }
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
//        try {
//            jt.update(
//                "INSERT INTO config_tags_relation(id,tag_name,tag_type,data_id,group_id,tenant_id) VALUES(?,?,?,?,?,?)",
//                configId, tagName, null, dataId, group, tenant);
//        } catch (CannotGetJdbcConnectionException e) {
//            LogUtil.FATAL_LOG.error("[db-error] " + e.toString(), e);
//            throw e;
//        }
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
//        try {
//            jt.update("DELETE FROM config_tags_relation WHERE id=?", id);
//        } catch (CannotGetJdbcConnectionException e) {
//            LogUtil.FATAL_LOG.error("[db-error] " + e.toString(), e);
//            throw e;
//        }
        configTagsRelationRepository.findById(id)
            .ifPresent(s -> configTagsRelationRepository.delete(s));
    }

    @Override
    public List<String> getConfigTagsByTenant(String tenant) {
//        String sql = "SELECT tag_name FROM config_tags_relation WHERE tenant_id = ? ";
//        try {
//            return jt.queryForList(sql, new Object[]{tenant}, String.class);
//        } catch (EmptyResultDataAccessException e) {
//            return null;
//        } catch (IncorrectResultSizeDataAccessException e) {
//            return null;
//        } catch (CannotGetJdbcConnectionException e) {
//            LogUtil.FATAL_LOG.error("[db-error] " + e.toString(), e);
//            throw e;
//        }
        return null;
    }

    @Override
    public List<String> selectTagByConfig(String dataId, String group, String tenant) {
//        String sql = "SELECT tag_name FROM config_tags_relation WHERE data_id=? AND group_id=? AND tenant_id = ? ";
//        try {
//            return jt.queryForList(sql, new Object[]{dataId, group, tenant}, String.class);
//        } catch (EmptyResultDataAccessException e) {
//            return null;
//        } catch (IncorrectResultSizeDataAccessException e) {
//            return null;
//        } catch (CannotGetJdbcConnectionException e) {
//            LogUtil.FATAL_LOG.error("[db-error] " + e.toString(), e);
//            throw e;
//        }
        BooleanBuilder booleanBuilder = new BooleanBuilder();
        QConfigTagsRelation qConfigTagsRelation = QConfigTagsRelation.configTagsRelation;
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
//        String tenantTmp = StringUtils.isBlank(tenant) ? StringUtils.EMPTY : tenant;
//        try {
//            jt.update("DELETE FROM config_info WHERE data_id=? AND group_id=? AND tenant_id=?", dataId, group,
//                tenantTmp);
//        } catch (CannotGetJdbcConnectionException e) {
//            LogUtil.FATAL_LOG.error("[db-error] " + e.toString(), e);
//            throw e;
//        }
        BooleanBuilder booleanBuilder = new BooleanBuilder();
        QConfigInfo qConfigInfo = QConfigInfo.configInfo;
        booleanBuilder.and(qConfigInfo.dataId.eq(dataId));
        booleanBuilder.and(qConfigInfo.groupId.eq(group));
        if (StringUtils.isNotBlank(tenant)) {
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
//        StringBuilder sql = new StringBuilder(SQL_DELETE_CONFIG_INFO_BY_IDS);
//        sql.append("id in (");
//        List<Long> paramList = new ArrayList<>();
//        String[] tagArr = ids.split(",");
//        for (int i = 0; i < tagArr.length; i++) {
//            if (i != 0) {
//                sql.append(", ");
//            }
//            sql.append("?");
//            paramList.add(Long.parseLong(tagArr[i]));
//        }
//        sql.append(") ");
//        try {
//            jt.update(sql.toString(), paramList.toArray());
//        } catch (CannotGetJdbcConnectionException e) {
//            LogUtil.FATAL_LOG.error("[db-error] " + e.toString(), e);
//            throw e;
//        }
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
//        String tenantTmp = StringUtils.isBlank(tenant) ? StringUtils.EMPTY : tenant;
//        String tagTmp = StringUtils.isBlank(tag) ? StringUtils.EMPTY : tag;
//        try {
//            jt.update("DELETE FROM config_info_tag WHERE data_id=? AND group_id=? AND tenant_id=? AND tag_id=?", dataId,
//                group, tenantTmp, tagTmp);
//        } catch (CannotGetJdbcConnectionException e) {
//            LogUtil.FATAL_LOG.error("[db-error] " + e.toString(), e);
//            throw e;
//        }
        BooleanBuilder booleanBuilder = new BooleanBuilder();
        QConfigInfoTag qConfigInfoTag = QConfigInfoTag.configInfoTag;
        if (StringUtils.isNotBlank(dataId)) {
            booleanBuilder.and(qConfigInfoTag.dataId.eq(dataId));
        }
        if (StringUtils.isNotBlank(group)) {
            booleanBuilder.and(qConfigInfoTag.groupId.eq(group));
        }
        if (StringUtils.isNotBlank(tenant)) {
            booleanBuilder.and(qConfigInfoTag.tenantId.eq(tenant));
        }
        if (StringUtils.isNotBlank(tag)) {
            booleanBuilder.and(qConfigInfoTag.tagId.eq(tag));
        }
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
//        String appNameTmp = StringUtils.isBlank(configInfo.getAppName()) ? StringUtils.EMPTY : configInfo.getAppName();
//        String tenantTmp = StringUtils.isBlank(configInfo.getTenant()) ? StringUtils.EMPTY : configInfo.getTenant();
//        final String md5Tmp = MD5Utils.md5Hex(configInfo.getContent(), Constants.ENCODE);
//        String desc = configAdvanceInfo == null ? null : (String) configAdvanceInfo.get("desc");
//        String use = configAdvanceInfo == null ? null : (String) configAdvanceInfo.get("use");
//        String effect = configAdvanceInfo == null ? null : (String) configAdvanceInfo.get("effect");
//        String type = configAdvanceInfo == null ? null : (String) configAdvanceInfo.get("type");
//        String schema = configAdvanceInfo == null ? null : (String) configAdvanceInfo.get("schema");
//
//        try {
//            jt.update("UPDATE config_info SET content=?, md5 = ?, src_ip=?,src_user=?,gmt_modified=?,"
//                    + "app_name=?,c_desc=?,c_use=?,effect=?,type=?,c_schema=? "
//                    + "WHERE data_id=? AND group_id=? AND tenant_id=?", configInfo.getContent(), md5Tmp, srcIp, srcUser,
//                time, appNameTmp, desc, use, effect, type, schema, configInfo.getDataId(), configInfo.getGroup(),
//                tenantTmp);
//        } catch (CannotGetJdbcConnectionException e) {
//            LogUtil.FATAL_LOG.error("[db-error] " + e.toString(), e);
//            throw e;
//        }
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
//        if (StringUtils.isBlank(ids)) {
//            return null;
//        }
//        StringBuilder sql = new StringBuilder(SQL_FIND_CONFIG_INFO_BY_IDS);
//        sql.append("id in (");
//        List<Long> paramList = new ArrayList<>();
//        String[] tagArr = ids.split(",");
//        for (int i = 0; i < tagArr.length; i++) {
//            if (i != 0) {
//                sql.append(", ");
//            }
//            sql.append("?");
//            paramList.add(Long.parseLong(tagArr[i]));
//        }
//        sql.append(") ");
//        try {
//            return this.jt.query(sql.toString(), paramList.toArray(), CONFIG_INFO_ROW_MAPPER);
//        } catch (EmptyResultDataAccessException e) { // Indicates that the data does not exist, returns null
//            return null;
//        } catch (CannotGetJdbcConnectionException e) {
//            LogUtil.FATAL_LOG.error("[db-error] " + e.toString(), e);
//            throw e;
//        }
        if (StringUtils.isBlank(ids)) {
            return null;
        }
        List<Long> paramList = new ArrayList<>();
        String[] tagArr = ids.split(",");
        for (int i = 0; i < tagArr.length; i++) {
            paramList.add(Long.parseLong(tagArr[i]));
        }
        QConfigInfo qConfigInfo = QConfigInfo.configInfo;
        List<ConfigInfoEntity> list = (List<ConfigInfoEntity>) configInfoRepository.findAll(qConfigInfo.id.in(paramList));
        return ConfigInfoMapStruct.INSTANCE.convertConfigInfoList2(list);
    }

    @Override
    public ConfigAdvanceInfo findConfigAdvanceInfo(final String dataId, final String group, final String tenant) {
//        final String tenantTmp = StringUtils.isBlank(tenant) ? StringUtils.EMPTY : tenant;
//        try {
//            List<String> configTagList = this.selectTagByConfig(dataId, group, tenant);
//            ConfigAdvanceInfo configAdvance = this.jt.queryForObject(
//                "SELECT gmt_create,gmt_modified,src_user,src_ip,c_desc,c_use,effect,type,c_schema FROM config_info WHERE data_id=? AND group_id=? AND tenant_id=?",
//                new Object[]{dataId, group, tenantTmp}, CONFIG_ADVANCE_INFO_ROW_MAPPER);
//            if (configTagList != null && !configTagList.isEmpty()) {
//                StringBuilder configTagsTmp = new StringBuilder();
//                for (String configTag : configTagList) {
//                    if (configTagsTmp.length() == 0) {
//                        configTagsTmp.append(configTag);
//                    } else {
//                        configTagsTmp.append(",").append(configTag);
//                    }
//                }
//                configAdvance.setConfigTags(configTagsTmp.toString());
//            }
//            return configAdvance;
//        } catch (EmptyResultDataAccessException e) { // Indicates that the data does not exist, returns null
//            return null;
//        } catch (CannotGetJdbcConnectionException e) {
//            LogUtil.FATAL_LOG.error("[db-error] " + e.toString(), e);
//            throw e;
//        }
        List<String> configTagList = this.selectTagByConfig(dataId, group, tenant);
        BooleanBuilder booleanBuilder = new BooleanBuilder();
        QConfigInfo qConfigInfo = QConfigInfo.configInfo;
        if (StringUtils.isNotBlank(dataId)) {
            booleanBuilder.and(qConfigInfo.dataId.eq(dataId));
        }
        if (StringUtils.isNotBlank(group)) {
            booleanBuilder.and(qConfigInfo.groupId.eq(group));
        }
        if (StringUtils.isNotBlank(tenant)) {
            booleanBuilder.and(qConfigInfo.groupId.eq(group));
        }
        ConfigInfoEntity configInfo = configInfoRepository.findOne(booleanBuilder)
            .orElseThrow(() -> new RuntimeException("find configInfo data null"));
        ConfigAdvanceInfo configAdvance = new ConfigAdvanceInfo();
        BeanUtils.copyProperties(configInfo, configAdvance);
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
//        final String tenantTmp = StringUtils.isBlank(tenant) ? StringUtils.EMPTY : tenant;
//        try {
//            List<String> configTagList = this.selectTagByConfig(dataId, group, tenant);
//            ConfigAllInfo configAdvance = this.jt.queryForObject(
//                "SELECT ID,data_id,group_id,tenant_id,app_name,content,md5,"
//                    + "gmt_create,gmt_modified,src_user,src_ip,c_desc,c_use,effect,type,c_schema FROM config_info "
//                    + "WHERE data_id=? AND group_id=? AND tenant_id=?", new Object[]{dataId, group, tenantTmp},
//                CONFIG_ALL_INFO_ROW_MAPPER);
//            if (configTagList != null && !configTagList.isEmpty()) {
//                StringBuilder configTagsTmp = new StringBuilder();
//                for (String configTag : configTagList) {
//                    if (configTagsTmp.length() == 0) {
//                        configTagsTmp.append(configTag);
//                    } else {
//                        configTagsTmp.append(",").append(configTag);
//                    }
//                }
//                configAdvance.setConfigTags(configTagsTmp.toString());
//            }
//            return configAdvance;
//        } catch (EmptyResultDataAccessException e) { // Indicates that the data does not exist, returns null
//            return null;
//        } catch (CannotGetJdbcConnectionException e) {
//            LogUtil.FATAL_LOG.error("[db-error] " + e.toString(), e);
//            throw e;
//        }
        List<String> configTagList = selectTagByConfig(dataId, group, tenant);
        BooleanBuilder booleanBuilder = new BooleanBuilder();
        QConfigInfo qConfigInfo = QConfigInfo.configInfo;
        if (StringUtils.isNotBlank(dataId)) {
            booleanBuilder.and(qConfigInfo.dataId.eq(dataId));
        }
        if (StringUtils.isNotBlank(group)) {
            booleanBuilder.and(qConfigInfo.groupId.eq(group));
        }
        if (StringUtils.isNotBlank(tenant)) {
            booleanBuilder.and(qConfigInfo.tenantId.eq(tenant));
        }
        ConfigInfoEntity configInfo = configInfoRepository.findOne(booleanBuilder)
            .orElseThrow(() -> new RuntimeException("find configInfo data null"));
        ConfigAllInfo configAdvance = new ConfigAllInfo();
        BeanUtils.copyProperties(configInfo, configAdvance);
        configAdvance.setGroup(configInfo.getGroupId());
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
//        String appNameTmp = StringUtils.isBlank(configInfo.getAppName()) ? StringUtils.EMPTY : configInfo.getAppName();
//        String tenantTmp = StringUtils.isBlank(configInfo.getTenant()) ? StringUtils.EMPTY : configInfo.getTenant();
//        final String md5Tmp = MD5Utils.md5Hex(configInfo.getContent(), Constants.ENCODE);
//        try {
//            jt.update(
//                "INSERT INTO his_config_info (id,data_id,group_id,tenant_id,app_name,content,md5,src_ip,src_user,gmt_modified,op_type) "
//                    + "VALUES(?,?,?,?,?,?,?,?,?,?,?)", id, configInfo.getDataId(), configInfo.getGroup(),
//                tenantTmp, appNameTmp, configInfo.getContent(), md5Tmp, srcIp, srcUser, time, ops);
//        } catch (DataAccessException e) {
//            LogUtil.FATAL_LOG.error("[db-error] " + e.toString(), e);
//            throw e;
//        }
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
        hisConfigInfo.setGmtCreate(time);
        hisConfigInfoRepository.save(hisConfigInfo);
    }

    @Override
    public Page<ConfigHistoryInfo> findConfigHistory(String dataId, String group, String tenant, int pageNo,
                                                     int pageSize) {
//        PaginationHelper<ConfigHistoryInfo> helper = createPaginationHelper();
//        String tenantTmp = StringUtils.isBlank(tenant) ? StringUtils.EMPTY : tenant;
//        String sqlCountRows = "select count(*) from his_config_info where data_id = ? and group_id = ? and tenant_id = ?";
//        String sqlFetchRows =
//            "select nid,data_id,group_id,tenant_id,app_name,src_ip,op_type,gmt_create,gmt_modified from his_config_info "
//                + "where data_id = ? and group_id = ? and tenant_id = ? order by nid desc";
//
//        Page<ConfigHistoryInfo> page = null;
//        try {
//            page = helper
//                .fetchPage(sqlCountRows, sqlFetchRows, new Object[]{dataId, group, tenantTmp}, pageNo, pageSize,
//                    HISTORY_LIST_ROW_MAPPER);
//        } catch (DataAccessException e) {
//            LogUtil.FATAL_LOG.error("[list-config-history] error, dataId:{}, group:{}", new Object[]{dataId, group}, e);
//            throw e;
//        }
//        return page;
        QHisConfigInfo qHisConfigInfo = QHisConfigInfo.hisConfigInfo;
        org.springframework.data.domain.Page<HisConfigInfoEntity> sPage = hisConfigInfoRepository.findAll(qHisConfigInfo.dataId.eq(dataId)
            .and(qHisConfigInfo.groupId.eq(group))
            .and(qHisConfigInfo.tenantId.eq(tenant)), PageRequest.of(pageNo, pageSize, Sort.by(Sort.Order.desc("nid"))));

        Page<ConfigHistoryInfo> page = new Page<>();
        page.setPageNumber(sPage.getNumber());
        page.setPagesAvailable(sPage.getTotalPages());
        page.setPageItems(ConfigHistoryInfoMapStruct.INSTANCE.convertConfigHistoryInfoList(sPage.getContent()));
        page.setTotalCount((int) sPage.getTotalElements());
        return page;
    }

    @Override
    public void addConfigSubAtomic(final String dataId, final String group, final String appName,
                                   final Timestamp date) {
//        final String appNameTmp = appName == null ? "" : appName;
//        try {
//            jt.update(
//                "INSERT INTO app_configdata_relation_subs(data_id,group_id,app_name,gmt_modified) VALUES(?,?,?,?)",
//                dataId, group, appNameTmp, date);
//        } catch (CannotGetJdbcConnectionException e) {
//            LogUtil.FATAL_LOG.error("[db-error] " + e.toString(), e);
//            throw e;
//        }
    }

    @Override
    public void updateConfigSubAtomic(final String dataId, final String group, final String appName,
                                      final Timestamp time) {
//        final String appNameTmp = appName == null ? "" : appName;
//        try {
//            jt.update(
//                "UPDATE app_configdata_relation_subs SET gmt_modified=? WHERE data_id=? AND group_id=? AND app_name=?",
//                time, dataId, group, appNameTmp);
//        } catch (CannotGetJdbcConnectionException e) {
//            LogUtil.FATAL_LOG.error("[db-error] " + e.toString(), e);
//            throw e;
//        }
    }

    @Override
    public ConfigHistoryInfo detailConfigHistory(Long nid) {
//        String sqlFetchRows = "SELECT nid,data_id,group_id,tenant_id,app_name,content,md5,src_user,src_ip,op_type,gmt_create,gmt_modified FROM his_config_info WHERE nid = ?";
//        try {
//            ConfigHistoryInfo historyInfo = jt
//                .queryForObject(sqlFetchRows, new Object[]{nid}, HISTORY_DETAIL_ROW_MAPPER);
//            return historyInfo;
//        } catch (DataAccessException e) {
//            LogUtil.FATAL_LOG.error("[list-config-history] error, nid:{}", new Object[]{nid}, e);
//            throw e;
//        }
        HisConfigInfoEntity hisConfigInfoEntity = hisConfigInfoRepository.findById(nid)
            .orElseThrow(() -> new RuntimeException("findById hisConfigInfo data null nid=" + nid));
        return ConfigHistoryInfoMapStruct.INSTANCE.convertConfigHistoryInfo(hisConfigInfoEntity);
    }

    @Override
    public void insertTenantInfoAtomic(String kp, String tenantId, String tenantName, String tenantDesc,
                                       String createResoure, final long time) {
//        try {
//            jt.update(
//                "INSERT INTO tenant_info(kp,tenant_id,tenant_name,tenant_desc,create_source,gmt_create,gmt_modified) VALUES(?,?,?,?,?,?,?)",
//                kp, tenantId, tenantName, tenantDesc, createResoure, time, time);
//        } catch (DataAccessException e) {
//            LogUtil.FATAL_LOG.error("[db-error] " + e.toString(), e);
//            throw e;
//        }
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
//        try {
//            jt.update(
//                "UPDATE tenant_info SET tenant_name = ?, tenant_desc = ?, gmt_modified= ? WHERE kp=? AND tenant_id=?",
//                tenantName, tenantDesc, System.currentTimeMillis(), kp, tenantId);
//        } catch (DataAccessException e) {
//            LogUtil.FATAL_LOG.error("[db-error] " + e.toString(), e);
//            throw e;
//        }
        QTenantInfo qTenantInfo = QTenantInfo.tenantInfo;
        tenantInfoRepository.findOne(qTenantInfo.kp.eq(kp)
            .and(qTenantInfo.tenantId.eq(tenantId)))
            .ifPresent(s -> {
                s.setTenantName(tenantName);
                s.setTenantDesc(tenantDesc);
                tenantInfoRepository.save(s);
            });
    }

    @Override
    public List<TenantInfo> findTenantByKp(String kp) {
//        String sql = "SELECT tenant_id,tenant_name,tenant_desc FROM tenant_info WHERE kp=?";
//        try {
//            return this.jt.query(sql, new Object[]{kp}, TENANT_INFO_ROW_MAPPER);
//        } catch (CannotGetJdbcConnectionException e) {
//            LogUtil.FATAL_LOG.error("[db-error] " + e.toString(), e);
//            throw e;
//        } catch (EmptyResultDataAccessException e) {
//            return Collections.emptyList();
//        } catch (Exception e) {
//            LogUtil.FATAL_LOG.error("[db-other-error]" + e.getMessage(), e);
//            throw new RuntimeException(e);
//        }
        List<TenantInfoEntity> list = tenantInfoRepository.findByKp(kp);
        return TenantInfoMapStruct.INSTANCE.convertTenantInfoList(list);
    }

    @Override
    public TenantInfo findTenantByKp(String kp, String tenantId) {
//        String sql = "SELECT tenant_id,tenant_name,tenant_desc FROM tenant_info WHERE kp=? AND tenant_id=?";
//        try {
//            return jt.queryForObject(sql, new Object[]{kp, tenantId}, TENANT_INFO_ROW_MAPPER);
//        } catch (CannotGetJdbcConnectionException e) {
//            LogUtil.FATAL_LOG.error("[db-error] " + e.toString(), e);
//            throw e;
//        } catch (EmptyResultDataAccessException e) {
//            return null;
//        } catch (Exception e) {
//            LogUtil.FATAL_LOG.error("[db-other-error]" + e.getMessage(), e);
//            throw new RuntimeException(e);
//        }
        TenantInfoEntity tenantInfoEntity = tenantInfoRepository.findByKpAndTenantId(kp, tenantId);
        return TenantInfoMapStruct.INSTANCE.convertTenantInfo(tenantInfoEntity);
    }

    @Override
    public void removeTenantInfoAtomic(final String kp, final String tenantId) {
//        try {
//            jt.update("DELETE FROM tenant_info WHERE kp=? AND tenant_id=?", kp, tenantId);
//        } catch (CannotGetJdbcConnectionException e) {
//            LogUtil.FATAL_LOG.error("[db-error] " + e.toString(), e);
//            throw e;
//        }
//        TenantInfo tenantInfo = findTenantByKp(kp, tenantId);
        tenantInfoRepository.findOne(QTenantInfo.tenantInfo.tenantId.eq(tenantId)
            .and(QTenantInfo.tenantInfo.kp.eq(kp)))
            .ifPresent(s -> tenantInfoRepository.delete(s));

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
        for (int pageNo = 1; pageNo <= pageCount; pageNo++) {
            List<ConfigInfoWrapper> configInfoList = listGroupKeyMd5ByPage(pageNo, pageSize);
            allConfigInfo.addAll(configInfoList);
        }
        return allConfigInfo;
    }

    @Override
    public List<ConfigInfoWrapper> listGroupKeyMd5ByPage(int pageNo, int pageSize) {
        String sqlCountRows = " SELECT COUNT(*) FROM config_info ";
        String sqlFetchRows = " SELECT t.id,data_id,group_id,tenant_id,app_name,md5,type,gmt_modified FROM "
            + "( SELECT id FROM config_info ORDER BY id LIMIT ?,?  ) g, config_info t WHERE g.id = t.id";
        PaginationHelper<ConfigInfoWrapper> helper = createPaginationHelper();
        try {
            Page<ConfigInfoWrapper> page = helper
                .fetchPageLimit(sqlCountRows, sqlFetchRows, new Object[]{(pageNo - 1) * pageSize, pageSize},
                    pageNo, pageSize, CONFIG_INFO_WRAPPER_ROW_MAPPER);

            return page.getPageItems();
        } catch (CannotGetJdbcConnectionException e) {
            LogUtil.FATAL_LOG.error("[db-error] " + e.toString(), e);
            throw e;
        }
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
//        String tenantTmp = StringUtils.isBlank(tenant) ? StringUtils.EMPTY : tenant;
//        try {
//            return this.jt.queryForObject(
//                "SELECT ID,data_id,group_id,tenant_id,app_name,content,type,gmt_modified,md5 FROM config_info "
//                    + "WHERE data_id=? AND group_id=? AND tenant_id=?", new Object[]{dataId, group, tenantTmp},
//                CONFIG_INFO_WRAPPER_ROW_MAPPER);
//        } catch (EmptyResultDataAccessException e) {
//            return null;
//        } catch (CannotGetJdbcConnectionException e) {
//            LogUtil.FATAL_LOG.error("[db-error] " + e.toString(), e);
//            throw e;
//        }
        String tenantTmp = StringUtils.isBlank(tenant) ? StringUtils.EMPTY : tenant;
        QConfigInfo qConfigInfo = QConfigInfo.configInfo;
        ConfigInfoEntity result = configInfoRepository.findOne(qConfigInfo.dataId.eq(dataId)
            .and(qConfigInfo.groupId.eq(group))
            .and(qConfigInfo.tenantId.eq(tenantTmp)))
            .orElse(null);
        return ConfigInfoWrapperMapStruct.INSTANCE.convertConfigInfoWrapper(result);
    }

    //TODO 后续改
    @Override
    public boolean isExistTable(String tableName) {
//        String sql = String.format("select 1 from %s limit 1", tableName);
//        try {
//            jt.queryForObject(sql, Integer.class);
//            return true;
//        } catch (Throwable e) {
//            return false;
//        }
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
                                new Object[]{cf.getDataId(), cf.getGroup(), new Timestamp(cf.getLastModified())});
                        }
                    } else {
                        if (!md5InDb.equals(md5)) {
                            try {
                                updateMd5(cf.getDataId(), cf.getGroup(), tenant, md5,
                                    new Timestamp(cf.getLastModified()));
                            } catch (Exception e) {
                                LogUtil.DEFAULT_LOG.error("[completeMd5-error] datId:{} group:{} lastModified:{}",
                                    new Object[]{cf.getDataId(), cf.getGroup(),
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
//        String tenantTmp = StringUtils.isBlank(tenant) ? StringUtils.EMPTY : tenant;
//        StringBuilder where = new StringBuilder(" where ");
//        List<Object> paramList = new ArrayList<>();
//        if (!CollectionUtils.isEmpty(ids)) {
//            where.append(" id in (");
//            for (int i = 0; i < ids.size(); i++) {
//                if (i != 0) {
//                    where.append(", ");
//                }
//                where.append("?");
//                paramList.add(ids.get(i));
//            }
//            where.append(") ");
//        } else {
//            where.append(" tenant_id=? ");
//            paramList.add(tenantTmp);
//            if (!StringUtils.isBlank(dataId)) {
//                where.append(" and data_id like ? ");
//                paramList.add(generateLikeArgument(dataId));
//            }
//            if (StringUtils.isNotBlank(group)) {
//                where.append(" and group_id=? ");
//                paramList.add(group);
//            }
//            if (StringUtils.isNotBlank(appName)) {
//                where.append(" and app_name=? ");
//                paramList.add(appName);
//            }
//        }
//        try {
//            return this.jt.query(SQL_FIND_ALL_CONFIG_INFO + where, paramList.toArray(), CONFIG_ALL_INFO_ROW_MAPPER);
//        } catch (CannotGetJdbcConnectionException e) {
//            LogUtil.FATAL_LOG.error("[db-error] " + e.toString(), e);
//            throw e;
//        }
        BooleanBuilder booleanBuilder = new BooleanBuilder();
        QConfigInfo qConfigInfo = QConfigInfo.configInfo;
        if (!org.springframework.util.CollectionUtils.isEmpty(ids)) {
            booleanBuilder.and(qConfigInfo.id.in(ids));
        } else {
            if (StringUtils.isNotBlank(tenant)) {
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
            ConfigAllInfo configAllInfo = new ConfigAllInfo();
            BeanUtils.copyProperties(s, configAllInfo);
            configAllInfo.setGroup(s.getGroupId());
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
                    String extName = configInfo.getDataId().substring(configInfo.getDataId().lastIndexOf(SPOT) + 1)
                        .toUpperCase();
                    try {
                        type = FileTypeEnum.valueOf(extName.toUpperCase()).getFileType();
                    } catch (Exception ex) {
                        type = FileTypeEnum.TEXT.getFileType();
                    }
                }
            }
            if (configAdvanceInfo == null) {
                configAdvanceInfo = new HashMap<>(16);
            }
            configAdvanceInfo.put("type", type);
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
//        Assert.hasText(tenantId, "tenantId can not be null");
//        Integer result = this.jt
//            .queryForObject(SQL_TENANT_INFO_COUNT_BY_TENANT_ID, new String[]{tenantId}, Integer.class);
//        if (result == null) {
//            return 0;
//        }
//        return result.intValue();
        Assert.hasText(tenantId, "tenantId can not be null");
        QTenantInfo qTenantInfo = QTenantInfo.tenantInfo;
        Long result = tenantInfoRepository.count(qTenantInfo.tenantId.eq(tenantId));
        return result.intValue();
    }

}
