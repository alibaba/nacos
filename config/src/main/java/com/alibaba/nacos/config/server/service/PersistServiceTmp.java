package com.alibaba.nacos.config.server.service;

import java.util.Date;

import java.util.*;

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.config.server.enums.FileTypeEnum;
import com.alibaba.nacos.config.server.model.ConfigAdvanceInfo;
import com.alibaba.nacos.config.server.model.ConfigAllInfo;
import com.alibaba.nacos.config.server.model.ConfigInfoBase;
import com.alibaba.nacos.config.server.model.SameConfigPolicy;
import com.alibaba.nacos.config.server.modules.entity.*;
import com.alibaba.nacos.config.server.modules.repository.*;
import com.alibaba.nacos.config.server.utils.MD5;
import com.alibaba.nacos.config.server.utils.ParamUtils;
import com.alibaba.nacos.config.server.utils.event.EventDispatcher;
import com.google.common.base.Joiner;
import com.querydsl.core.BooleanBuilder;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.CannotGetJdbcConnectionException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;

import java.sql.Timestamp;

import static com.alibaba.nacos.config.server.service.PersistService.SPOT;
import static com.alibaba.nacos.config.server.utils.LogUtil.defaultLog;
import static com.alibaba.nacos.config.server.utils.LogUtil.fatalLog;


@Service
public class PersistServiceTmp {

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
    private TransactionTemplate tjt;


    /**
     * 根据dataId和group查询配置信息
     */
    public ConfigInfoBase findConfigInfoBase(final String dataId, final String group) {
        QConfigInfo qConfigInfo = QConfigInfo.configInfo;
        return configInfoRepository.findOne(qConfigInfo.dataId.eq(dataId)
            .and(qConfigInfo.groupId.eq(group)))
            .map(s -> {
                ConfigInfoBase configInfoBase = new ConfigInfoBase();
                BeanUtils.copyProperties(s, configInfoBase);
                return configInfoBase;
            }).orElse(null);
    }

    /**
     * @return List<ConfigInfo> deleted configInfos
     * @author klw
     * @Description: delete config info by ids
     * @Date 2019/7/5 16:45
     * @Param [ids, srcIp, srcUser]
     */
    public List<ConfigInfo> removeConfigInfoByIds(final List<Long> ids, final String srcIp, final String srcUser) {
        if (CollectionUtils.isEmpty(ids)) {
            return null;
        }
        ids.removeAll(Collections.singleton(null));
        return tjt.execute(new TransactionCallback<List<ConfigInfo>>() {
            final Timestamp time = new Timestamp(System.currentTimeMillis());

            @Override
            public List<ConfigInfo> doInTransaction(TransactionStatus status) {
                try {
                    List<ConfigInfo> configInfoList = findConfigInfosByIds(ids);
                    if (!CollectionUtils.isEmpty(configInfoList)) {
                        removeConfigInfoByIdsAtomic(configInfoList);
                        for (ConfigInfo configInfo : configInfoList) {
                            removeTagByIdAtomic(configInfo.getId());
                            insertConfigHistoryAtomic(configInfo.getId(), configInfo, srcIp, srcUser, time, "D");
                        }
                    }
                    return configInfoList;
                } catch (CannotGetJdbcConnectionException e) {
                    fatalLog.error("[db-error] " + e.toString(), e);
                    throw e;
                }
            }
        });
    }


    /**
     * 写入主表，插入或更新
     */
    public void insertOrUpdate(String srcIp, String srcUser, ConfigInfo configInfo, Timestamp time,
                               Map<String, Object> configAdvanceInfo, boolean notify) {
        ConfigInfo oldConfigInfo = findConfigInfo(configInfo.getDataId(), configInfo.getGroupId(),
            configInfo.getTenantId());
        if (oldConfigInfo == null) {
            addConfigInfo(srcIp, srcUser, configInfo, time, configAdvanceInfo, notify);
        } else {
            updateConfigInfo(configInfo, oldConfigInfo, srcIp, srcUser, time, configAdvanceInfo, notify);
        }
    }

    public void insertOrUpdateTag(final ConfigInfo configInfo, final String tag, final String srcIp,
                                  final String srcUser, final Timestamp time, final boolean notify) {
        String tenantTmp = StringUtils.isBlank(configInfo.getTenantId()) ? StringUtils.EMPTY : configInfo.getTenantId();
        String tagTmp = StringUtils.isBlank(tag) ? StringUtils.EMPTY : tag.trim();
        QConfigInfoTag qConfigInfoTag = QConfigInfoTag.configInfoTag;
        ConfigInfoTag configInfoTag = configInfoTagRepository.findOne(qConfigInfoTag.dataId.eq(configInfo.getDataId())
            .and(qConfigInfoTag.groupId.eq(configInfo.getGroupId()))
            .and(qConfigInfoTag.tenantId.eq(tenantTmp))
            .and(qConfigInfoTag.tagId.eq(tagTmp)))
            .orElse(null);
        if (configInfoTag == null) {
            addConfigInfo4Tag(configInfo, tag, srcIp, null, time, notify);
        } else {
            updateConfigInfo4Tag(configInfo, configInfoTag, tag, srcIp, null, time, notify);
        }
    }

    public void insertOrUpdateBeta(final ConfigInfo configInfo, final String betaIps, final String srcIp,
                                   final String srcUser, final Timestamp time, final boolean notify) {
        String appNameTmp = StringUtils.isBlank(configInfo.getAppName()) ? StringUtils.EMPTY : configInfo.getAppName();
        String tenantTmp = StringUtils.isBlank(configInfo.getTenantId()) ? StringUtils.EMPTY : configInfo.getTenantId();
        BooleanBuilder booleanBuilder = new BooleanBuilder();
        QConfigInfoBeta qConfigInfoBeta = QConfigInfoBeta.configInfoBeta;
        if (StringUtils.isNotBlank(configInfo.getDataId())){
            booleanBuilder.and(qConfigInfoBeta.dataId.eq(configInfo.getDataId()));
        }
        if (StringUtils.isNotBlank(configInfo.getGroupId())){
            booleanBuilder.and(qConfigInfoBeta.groupId.eq(configInfo.getGroupId()));
        }
        if (StringUtils.isNotBlank(tenantTmp)){
            booleanBuilder.and(qConfigInfoBeta.tenantId.eq(tenantTmp));
        }
        ConfigInfoBeta configInfoBeta = configInfoBetaRepository.findOne(booleanBuilder)
            .orElse(null);
        if (configInfoBeta == null) {
            addConfigInfo4Beta(configInfo, betaIps, srcIp, null, time, notify);
        } else {
            updateConfigInfo4Beta(configInfo, configInfoBeta, srcIp, null, time, notify);
        }
    }


    /**
     * 更新配置信息
     */
    public void updateConfigInfo4Beta(ConfigInfo configInfo, ConfigInfoBeta configInfoBeta, String srcIp, String srcUser, Timestamp time,
                                      boolean notify) {
        String appNameTmp = StringUtils.isBlank(configInfo.getAppName()) ? StringUtils.EMPTY : configInfo.getAppName();
        String tenantTmp = StringUtils.isBlank(configInfo.getTenantId()) ? StringUtils.EMPTY : configInfo.getTenantId();
        try {
            String md5 = MD5.getInstance().getMD5String(configInfo.getContent());
            configInfoBeta.setAppName(appNameTmp);
            configInfoBeta.setContent(configInfo.getContent());
            configInfoBeta.setMd5(md5);
            configInfoBeta.setSrcIp(srcIp);
            configInfoBeta.setSrcUser(srcUser);
            configInfoBetaRepository.save(configInfoBeta);
            if (notify) {
                EventDispatcher.fireEvent(new ConfigDataChangeEvent(true, configInfo.getDataId(), configInfo.getGroupId(),
                    tenantTmp, time.getTime()));
            }

        } catch (CannotGetJdbcConnectionException e) {
            fatalLog.error("[db-error] " + e.toString(), e);
            throw e;
        }
    }

    /**
     * 添加普通配置信息，发布数据变更事件
     */
    public void addConfigInfo4Beta(ConfigInfo configInfo, String betaIps,
                                   String srcIp, String srcUser, Timestamp time, boolean notify) {
        String appNameTmp = StringUtils.isBlank(configInfo.getAppName()) ? StringUtils.EMPTY : configInfo.getAppName();
        String tenantTmp = StringUtils.isBlank(configInfo.getTenantId()) ? StringUtils.EMPTY : configInfo.getTenantId();
        try {
            String md5 = MD5.getInstance().getMD5String(configInfo.getContent());
            ConfigInfoBeta configInfoBeta = new ConfigInfoBeta();
            configInfoBeta.setDataId(configInfo.getDataId());
            configInfoBeta.setGroupId(configInfo.getGroupId());
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
            if (notify) {
                EventDispatcher.fireEvent(new ConfigDataChangeEvent(true, configInfo.getDataId(), configInfo.getGroupId(),
                    tenantTmp, time.getTime()));
            }
        } catch (CannotGetJdbcConnectionException e) {
            fatalLog.error("[db-error] " + e.toString(), e);
            throw e;
        }
    }

    /**
     * 添加普通配置信息，发布数据变更事件
     */
    public void addConfigInfo4Tag(ConfigInfo configInfo, String tag, String srcIp, String srcUser, Timestamp time,
                                  boolean notify) {
        String appNameTmp = StringUtils.isBlank(configInfo.getAppName()) ? StringUtils.EMPTY : configInfo.getAppName();
        String tenantTmp = StringUtils.isBlank(configInfo.getTenantId()) ? StringUtils.EMPTY : configInfo.getTenantId();
        String tagTmp = StringUtils.isBlank(tag) ? StringUtils.EMPTY : tag.trim();
        try {
            String md5 = MD5.getInstance().getMD5String(configInfo.getContent());
            ConfigInfoTag configInfoTag = new ConfigInfoTag();
            configInfoTag.setDataId(configInfo.getDataId());
            configInfoTag.setGroupId(configInfo.getGroupId());
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
            if (notify) {
                EventDispatcher.fireEvent(new ConfigDataChangeEvent(false, configInfo.getDataId(),
                    configInfo.getGroupId(), tenantTmp, tagTmp, time.getTime()));
            }
        } catch (CannotGetJdbcConnectionException e) {
            fatalLog.error("[db-error] " + e.toString(), e);
            throw e;
        }
    }

    /**
     * 更新配置信息
     */
    public void updateConfigInfo4Tag(ConfigInfo configInfo, ConfigInfoTag configInfoTag, String tag,
                                     String srcIp, String srcUser, Timestamp time,
                                     boolean notify) {
        String appNameTmp = StringUtils.isBlank(configInfo.getAppName()) ? StringUtils.EMPTY : configInfo.getAppName();
        String tenantTmp = StringUtils.isBlank(configInfo.getTenantId()) ? StringUtils.EMPTY : configInfo.getTenantId();
        String tagTmp = StringUtils.isBlank(tag) ? StringUtils.EMPTY : tag.trim();
        try {
            String md5 = MD5.getInstance().getMD5String(configInfo.getContent());
            configInfoTag.setDataId(configInfo.getDataId());
            configInfoTag.setGroupId(configInfo.getGroupId());
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
            if (notify) {
                EventDispatcher.fireEvent(new ConfigDataChangeEvent(true, configInfo.getDataId(), configInfo.getGroupId(),
                    tenantTmp, tagTmp, time.getTime()));
            }

        } catch (CannotGetJdbcConnectionException e) {
            fatalLog.error("[db-error] " + e.toString(), e);
            throw e;
        }
    }

    public Page<ConfigInfo> findConfigInfo4Page(final int pageNo, final int pageSize, final String dataId,
                                                final String group,
                                                final String tenant, final String appName) {
        BooleanBuilder booleanBuilder = new BooleanBuilder();
        QConfigInfo qConfigInfo = QConfigInfo.configInfo;
        buildConfigInfoCommonCondition(booleanBuilder, qConfigInfo, dataId, group, appName);
        if (StringUtils.isNotBlank(tenant)) {
            booleanBuilder.and(qConfigInfo.tenantId.eq(tenant));
        }
        return configInfoRepository.findAll(booleanBuilder, PageRequest.of(pageNo, pageSize, Sort.by(Sort.Order.desc("gmtCreate"))));
    }

    public Page<ConfigInfo> findConfigInfoLike4Page(final int pageNo, final int pageSize, final String dataId,
                                                    final String group, final String tenant,
                                                    final String appName) {
        BooleanBuilder booleanBuilder = new BooleanBuilder();
        QConfigInfo qConfigInfo = QConfigInfo.configInfo;
        buildConfigInfoCommonCondition(booleanBuilder, qConfigInfo, dataId, group, appName);
        if (StringUtils.isNotBlank(tenant)) {
            booleanBuilder.and(qConfigInfo.tenantId.like(tenant));
        }
//        if (StringUtils.isNotBlank(content)) {
//            booleanBuilder.and(qConfigInfo.content.like(content));
//        }
        return configInfoRepository.findAll(booleanBuilder, PageRequest.of(pageNo, pageSize, Sort.by(Sort.Order.desc("gmtCreate"))));
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

    public ConfigInfoBeta findConfigInfo4Beta(final String dataId, final String group, final String tenant) {
        BooleanBuilder booleanBuilder = new BooleanBuilder();
        QConfigInfoBeta qConfigInfoBeta = QConfigInfoBeta.configInfoBeta;
        if (StringUtils.isNotBlank(dataId)) {
            qConfigInfoBeta.dataId.eq(dataId);
        }
        if (StringUtils.isNotBlank(group)) {
            qConfigInfoBeta.groupId.eq(group);
        }
        if (StringUtils.isNotBlank(tenant)) {
            qConfigInfoBeta.tenantId.eq(tenant);
        }
        return configInfoBetaRepository.findOne(booleanBuilder)
            .orElseThrow(() -> new RuntimeException("find configInfoBeta data null"));
    }


    /**
     * 根据dataId和group查询配置信息
     */
    public ConfigInfoTag findConfigInfo4Tag(final String dataId, final String group, final String tenant,
                                            final String tag) {
        BooleanBuilder booleanBuilder = new BooleanBuilder();
        QConfigInfoTag qConfigInfoTag = QConfigInfoTag.configInfoTag;
        if (StringUtils.isNotBlank(dataId)) {
            qConfigInfoTag.dataId.eq(dataId);
        }
        if (StringUtils.isNotBlank(group)) {
            qConfigInfoTag.groupId.eq(group);
        }
        if (StringUtils.isNotBlank(tenant)) {
            qConfigInfoTag.tenantId.eq(tenant);
        }
        if (StringUtils.isNotBlank(tag)) {
            qConfigInfoTag.tagId.eq(tag);
        }
        return configInfoTagRepository.findOne(booleanBuilder)
            .orElseThrow(() -> new RuntimeException("find configInfoTag data null"));
    }


    /**
     * 查询配置信息；数据库原子操作，最小sql动作，无业务封装
     *
     * @param dataId dataId
     * @param group  group
     * @param tenant tenant
     * @return config info
     */
    public ConfigInfo findConfigInfo(final String dataId, final String group, final String tenant) {
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
        return configInfoRepository.findOne(booleanBuilder)
            .orElseThrow(() -> new RuntimeException("find configInfo data null"));
    }

    /**
     * 查询配置信息；数据库原子操作，最小sql动作，无业务封装
     *
     * @param dataId dataId
     * @param group  group
     * @param tenant tenant
     * @return advance info
     */
    public ConfigAllInfo findConfigAllInfo(final String dataId, final String group, final String tenant) {
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
        ConfigInfo configInfo = configInfoRepository.findOne(booleanBuilder)
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


    public List<String> selectTagByConfig(String dataId, String group, String tenant) {
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
        Iterable<ConfigTagsRelation> iterable = configTagsRelationRepository.findAll(booleanBuilder);
        List<String> result = new ArrayList<>();
        iterable.forEach(s -> result.add(s.getTagName()));
        return result;
    }


    public void removeTagByIdAtomic(Long id) {
        configTagsRelationRepository.findById(id)
            .ifPresent(s -> configTagsRelationRepository.delete(s));
    }

    private void removeConfigInfoAtomic(final String dataId, final String group, final String tenant,
                                        final String srcIp,
                                        final String srcUser) {
        BooleanBuilder booleanBuilder = new BooleanBuilder();
        QConfigInfo qConfigInfo = QConfigInfo.configInfo;
        booleanBuilder.and(qConfigInfo.dataId.eq(dataId));
        booleanBuilder.and(qConfigInfo.groupId.eq(group));
        if (StringUtils.isNotBlank(tenant)) {
            booleanBuilder.and(qConfigInfo.tenantId.eq(tenant));
        }
        Iterable<ConfigInfo> configInfos = configInfoRepository.findAll(booleanBuilder);
        configInfos.forEach(s -> configInfoRepository.delete(s));
    }

    /**
     * 更新变更记录；数据库原子操作，最小sql动作，无业务封装
     *
     * @param id         id
     * @param configInfo config info
     * @param srcIp      ip
     * @param srcUser    user
     * @param time       time
     * @param ops        ops type
     */
    private void insertConfigHistoryAtomic(long id, ConfigInfo configInfo, String srcIp, String srcUser,
                                           final Timestamp time, String ops) {

        final String md5Tmp = MD5.getInstance().getMD5String(configInfo.getContent());
        HisConfigInfo hisConfigInfo = new HisConfigInfo();
        hisConfigInfo.setId(id);
        hisConfigInfo.setDataId(configInfo.getDataId());
        hisConfigInfo.setGroupId(configInfo.getGroupId());
        hisConfigInfo.setAppName(configInfo.getAppName());
        hisConfigInfo.setContent(configInfo.getContent());
        hisConfigInfo.setMd5(md5Tmp);
        hisConfigInfo.setGmtModified(time);
        hisConfigInfo.setSrcUser(srcUser);
        hisConfigInfo.setSrcIp(srcIp);
        hisConfigInfo.setOpType(ops);
        hisConfigInfo.setTenantId(configInfo.getTenantId());
        hisConfigInfo.setGmtCreate(time);
        hisConfigInfoRepository.save(hisConfigInfo);
    }


    /**
     * 删除配置信息, 物理删除
     */
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
                    fatalLog.error("[db-error] " + e.toString(), e);
                    throw e;
                }
                return Boolean.TRUE;
            }
        });
    }


    /**
     * 删除配置；数据库原子操作，最小sql动作，无业务封装
     *
     * @param dataId  dataId
     * @param group   group
     * @param tenant  tenant
     * @param tag     tag
     * @param srcIp   ip
     * @param srcUser user
     */
    public void removeConfigInfoTag(final String dataId, final String group, final String tenant, final String tag,
                                    final String srcIp,
                                    final String srcUser) {
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
                    Iterable<ConfigInfoTag> configInfoTags = configInfoTagRepository.findAll(booleanBuilder);
                    configInfoTags.forEach(s -> configInfoTagRepository.delete(s));
                } catch (Exception e) {
                    throw e;
                }
                return Boolean.TRUE;
            }
        });

    }


    /**
     * @return java.util.List<com.alibaba.nacos.config.server.model.ConfigInfo>
     * @author klw
     * @Description: find ConfigInfo by ids
     * @Date 2019/7/5 16:37
     * @Param [ids]
     */
    public List<ConfigInfo> findConfigInfosByIds(final List<Long> ids) {
        if (CollectionUtils.isEmpty(ids)) {
            return null;
        }
        QConfigInfo qConfigInfo = QConfigInfo.configInfo;
        return (List<ConfigInfo>) configInfoRepository.findAll(qConfigInfo.id.in(ids));
    }

    private void removeConfigInfoByIdsAtomic(final List<ConfigInfo> list) {
        configInfoRepository.deleteAll(list);
    }


    /**
     * 查询配置信息；数据库原子操作，最小sql动作，无业务封装
     *
     * @param dataId dataId
     * @param group  group
     * @param tenant tenant
     * @return advance info
     */
    public ConfigAdvanceInfo findConfigAdvanceInfo(final String dataId, final String group, final String tenant) {
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
        ConfigInfo configInfo = configInfoRepository.findOne(booleanBuilder)
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

    /**
     * 删除beta配置信息, 物理删除
     */
    public void removeConfigInfo4Beta(final String dataId, final String group, final String tenant) {
        tjt.execute(new TransactionCallback<Boolean>() {
            @Override
            public Boolean doInTransaction(TransactionStatus status) {
                try {
                    ConfigInfoBeta configInfoBeta = findConfigInfo4Beta(dataId, group, tenant);
                    if (configInfoBeta != null) {
                        configInfoBetaRepository.deleteById(configInfoBeta.getId());
                    }
                } catch (CannotGetJdbcConnectionException e) {
                    fatalLog.error("[db-error] " + e.toString(), e);
                    throw e;
                }
                return Boolean.TRUE;
            }
        });
    }

    /**
     * query all configuration information according to group, appName, tenant (for export)
     *
     * @param group
     * @return Collection of ConfigInfo objects
     */
    public List<ConfigAllInfo> findAllConfigInfo4Export(final String dataId, final String group, final String tenant,
                                                        final String appName, final List<Long> ids) {
        BooleanBuilder booleanBuilder = new BooleanBuilder();
        QConfigInfo qConfigInfo = QConfigInfo.configInfo;
        if (!CollectionUtils.isEmpty(ids)) {
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
        Iterable<ConfigInfo> configInfos = configInfoRepository.findAll(booleanBuilder);
        List<ConfigAllInfo> resultList = new ArrayList<>();
        configInfos.forEach(s -> {
            ConfigAllInfo configAllInfo = new ConfigAllInfo();
            BeanUtils.copyProperties(s, configAllInfo);
            configAllInfo.setGroup(s.getGroupId());
            resultList.add(configAllInfo);
        });
        return resultList;
    }


    /**
     * query tenantInfo (namespace) existence based by tenantId
     *
     * @param tenantId
     * @return count by tenantId
     */
    public int tenantInfoCountByTenantId(String tenantId) {
        Assert.hasText(tenantId, "tenantId can not be null");
        QTenantInfo qTenantInfo = QTenantInfo.tenantInfo;
        Long result = tenantInfoRepository.count(qTenantInfo.tenantId.eq(tenantId));
        return result.intValue();
    }

    /**
     * 添加普通配置信息，发布数据变更事件
     */
    public void addConfigInfo(final String srcIp, final String srcUser, final ConfigInfo configInfo,
                              final Timestamp time, final Map<String, Object> configAdvanceInfo, final boolean notify) {
        tjt.execute(new TransactionCallback<Boolean>() {
            @Override
            public Boolean doInTransaction(TransactionStatus status) {
                try {
                    long configId = addConfigInfoAtomic(srcIp, srcUser, configInfo, time, configAdvanceInfo);
                    String configTags = configAdvanceInfo == null ? null : (String) configAdvanceInfo.get("config_tags");
                    addConfiTagsRelationAtomic(configId, configTags, configInfo.getDataId(), configInfo.getGroupId(),
                        configInfo.getTenantId());
                    insertConfigHistoryAtomic(0, configInfo, srcIp, srcUser, time, "I");
                    if (notify) {
                        EventDispatcher.fireEvent(
                            new ConfigDataChangeEvent(false, configInfo.getDataId(), configInfo.getGroupId(),
                                configInfo.getTenantId(), time.getTime()));
                    }
                } catch (CannotGetJdbcConnectionException e) {
                    fatalLog.error("[db-error] " + e.toString(), e);
                    throw e;
                }
                return Boolean.TRUE;
            }
        });
    }

    /**
     * 增加配置；数据库原子操作，最小sql动作，无业务封装
     *
     * @param srcIp             ip
     * @param srcUser           user
     * @param configInfo        info
     * @param time              time
     * @param configAdvanceInfo advance info
     * @return excute sql result
     */
    private long addConfigInfoAtomic(final String srcIp, final String srcUser, final ConfigInfo configInfo,
                                     final Timestamp time,
                                     Map<String, Object> configAdvanceInfo) {

        final String desc = configAdvanceInfo == null ? null : (String) configAdvanceInfo.get("desc");
        final String use = configAdvanceInfo == null ? null : (String) configAdvanceInfo.get("use");
        final String effect = configAdvanceInfo == null ? null : (String) configAdvanceInfo.get("effect");
        final String type = configAdvanceInfo == null ? null : (String) configAdvanceInfo.get("type");
        final String schema = configAdvanceInfo == null ? null : (String) configAdvanceInfo.get("schema");
        final String md5Tmp = MD5.getInstance().getMD5String(configInfo.getContent());

        configInfo.setCDesc(desc);
        configInfo.setCUse(use);
        configInfo.setEffect(effect);
        configInfo.setType(type);
        configInfo.setCSchema(schema);
        configInfo.setMd5(md5Tmp);
        configInfo.setGmtCreate(time);
        configInfo.setGmtModified(time);
        try {
            return configInfoRepository.save(configInfo).getId();
        } catch (Exception e) {
            e.printStackTrace();
            return 0;
        }

    }

    /**
     * 增加配置；数据库原子操作，最小sql动作，无业务封装
     *
     * @param configId   config id
     * @param configTags tags
     * @param dataId     dataId
     * @param group      group
     * @param tenant     tenant
     */
    public void addConfiTagsRelationAtomic(long configId, String configTags, String dataId, String group,
                                           String tenant) {
        if (StringUtils.isNotBlank(configTags)) {
            String[] tagArr = configTags.split(",");
            for (String tag : tagArr) {
                addConfiTagRelationAtomic(configId, tag, dataId, group, tenant);
            }
        }
    }

    /**
     * 增加配置；数据库原子操作，最小sql动作，无业务封装
     *
     * @param configId id
     * @param tagName  tag
     * @param dataId   data id
     * @param group    group
     * @param tenant   tenant
     */
    public void addConfiTagRelationAtomic(long configId, String tagName, String dataId, String group, String tenant) {
        ConfigTagsRelation configTagsRelation = new ConfigTagsRelation();
        configTagsRelation.setId(configId);
        configTagsRelation.setTagName(tagName);
        configTagsRelation.setDataId(dataId);
        configTagsRelation.setGroupId(group);
        configTagsRelation.setTenantId(tenant);
        configTagsRelationRepository.save(configTagsRelation);
    }

    /**
     * 更新配置信息
     */
    public void updateConfigInfo(final ConfigInfo configInfo, final ConfigInfo oldConfigInfo,
                                 final String srcIp, final String srcUser,
                                 final Timestamp time, final Map<String, Object> configAdvanceInfo,
                                 final boolean notify) {
        tjt.execute(new TransactionCallback<Boolean>() {
            @Override
            public Boolean doInTransaction(TransactionStatus status) {
                try {
                    String appNameTmp = oldConfigInfo.getAppName();
                    // 用户传过来的appName不为空，则用持久化用户的appName，否则用db的;清空appName的时候需要传空串
                    if (configInfo.getAppName() == null) {
                        configInfo.setAppName(appNameTmp);
                    }
                    configInfo.setId(oldConfigInfo.getId());
                    configInfo.setGmtCreate(oldConfigInfo.getGmtCreate());
                    updateConfigInfoAtomic(configInfo, srcIp, srcUser, time, configAdvanceInfo);
                    String configTags = configAdvanceInfo == null ? null : (String) configAdvanceInfo.get("config_tags");
                    if (configTags != null) {
                        // 删除所有tag，然后再重新创建
                        removeTagByIdAtomic(oldConfigInfo.getId());
                        addConfiTagsRelationAtomic(oldConfigInfo.getId(), configTags, configInfo.getDataId(),
                            configInfo.getGroupId(), configInfo.getTenantId());
                    }
                    insertConfigHistoryAtomic(oldConfigInfo.getId(), oldConfigInfo, srcIp, srcUser, time, "U");
                    if (notify) {
                        EventDispatcher.fireEvent(new ConfigDataChangeEvent(false, configInfo.getDataId(),
                            configInfo.getGroupId(), configInfo.getTenantId(), time.getTime()));
                    }
                } catch (CannotGetJdbcConnectionException e) {
                    fatalLog.error("[db-error] " + e.toString(), e);
                    throw e;
                }
                return Boolean.TRUE;
            }
        });
    }


    /**
     * 更新配置;数据库原子操作，最小sql动作，无业务封装
     *
     * @param configInfo        config info
     * @param srcIp             ip
     * @param srcUser           user
     * @param time              time
     * @param configAdvanceInfo advance info
     */
    private void updateConfigInfoAtomic(final ConfigInfo configInfo, final String srcIp, final String srcUser,
                                        final Timestamp time, Map<String, Object> configAdvanceInfo) {
        final String md5Tmp = MD5.getInstance().getMD5String(configInfo.getContent());
        String desc = configAdvanceInfo == null ? null : (String) configAdvanceInfo.get("desc");
        String use = configAdvanceInfo == null ? null : (String) configAdvanceInfo.get("use");
        String effect = configAdvanceInfo == null ? null : (String) configAdvanceInfo.get("effect");
        String type = configAdvanceInfo == null ? null : (String) configAdvanceInfo.get("type");
        String schema = configAdvanceInfo == null ? null : (String) configAdvanceInfo.get("schema");

        configInfo.setMd5(md5Tmp);
        configInfo.setCDesc(desc);
        configInfo.setCUse(use);
        configInfo.setEffect(effect);
        configInfo.setType(type);
        configInfo.setCSchema(schema);
        configInfo.setGmtModified(time);
        configInfoRepository.save(configInfo);
    }


    /**
     * batch operation,insert or update
     * the format of the returned:
     * succCount: number of successful imports
     * skipCount: number of import skips (only with skip for the same configs)
     * failData: import failed data (only with abort for the same configs)
     * skipData: data skipped at import  (only with skip for the same configs)
     */
    public Map<String, Object> batchInsertOrUpdate(List<ConfigAllInfo> configInfoList, String srcUser, String srcIp,
                                                   Map<String, Object> configAdvanceInfo, Timestamp time, boolean notify, SameConfigPolicy policy) throws NacosException {
        int succCount = 0;
        int skipCount = 0;
        List<Map<String, String>> failData = null;
        List<Map<String, String>> skipData = null;

        for (int i = 0; i < configInfoList.size(); i++) {
            ConfigAllInfo configInfo = configInfoList.get(i);
            try {
                ParamUtils.checkParam(configInfo.getDataId(), configInfo.getGroup(), "datumId", configInfo.getContent());
            } catch (NacosException e) {
                defaultLog.error("data verification failed", e);
                throw e;
            }
            ConfigInfo configInfo2Save = new ConfigInfo(configInfo.getDataId(), configInfo.getGroup(),
                configInfo.getContent(), configInfo.getAppName(), configInfo.getTenant());

            String type = configInfo.getType();
            if (StringUtils.isBlank(type)) {
                // simple judgment of file type based on suffix
                if (configInfo.getDataId().contains(SPOT)) {
                    String extName = configInfo.getDataId().substring(configInfo.getDataId().lastIndexOf(SPOT) + 1).toUpperCase();
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
                    faileditem.put("group", configInfo2Save.getGroupId());
                    failData.add(faileditem);
                    for (int j = (i + 1); j < configInfoList.size(); j++) {
                        ConfigInfo skipConfigInfo = new ConfigInfo();
                        BeanUtils.copyProperties(skipConfigInfo, configInfoList.get(j));
                        Map<String, String> skipitem = new HashMap<>(2);
                        skipitem.put("dataId", skipConfigInfo.getDataId());
                        skipitem.put("group", skipConfigInfo.getGroupId());
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
                    skipitem.put("group", configInfo2Save.getGroupId());
                    skipData.add(skipitem);
                } else if (SameConfigPolicy.OVERWRITE.equals(policy)) {
                    succCount++;
                    ConfigInfo oldConfigInfo = findConfigInfo(configInfo.getDataId(), configInfo2Save.getGroupId(),
                        configInfo2Save.getTenantId());
                    updateConfigInfo(configInfo2Save, oldConfigInfo, srcIp, srcUser, time, configAdvanceInfo, notify);
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

    /**
     * list配置的历史变更记录
     *
     * @param dataId   data Id
     * @param group    group
     * @param tenant   tenant
     * @param pageNo   no
     * @param pageSize size
     * @return history info
     */
    public Page<HisConfigInfo> findConfigHistory(String dataId, String group, String tenant, int pageNo,
                                                 int pageSize) {
        QHisConfigInfo qHisConfigInfo = QHisConfigInfo.hisConfigInfo;
        return hisConfigInfoRepository.findAll(qHisConfigInfo.dataId.eq(dataId)
            .and(qHisConfigInfo.groupId.eq(group))
            .and(qHisConfigInfo.tenantId.eq(tenant)), PageRequest.of(pageNo, pageSize, Sort.by(Sort.Order.desc("nid"))));
    }


    public HisConfigInfo detailConfigHistory(Long nid) {
        return hisConfigInfoRepository.findById(nid)
            .orElseThrow(() -> new RuntimeException("findById hisConfigInfo data null nid=" + nid));
    }


    public List<String> getTenantIdList(int page, int pageSize) {
//        String sql = "SELECT tenant_id FROM config_info WHERE tenant_id != '' GROUP BY tenant_id LIMIT ?, ?";
//        int from = (page - 1) * pageSize;
//        return jt.queryForList(sql, String.class, from, pageSize);
        return null;
    }

}
