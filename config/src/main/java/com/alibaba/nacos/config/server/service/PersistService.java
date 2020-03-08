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
package com.alibaba.nacos.config.server.service;

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.config.server.enums.FileTypeEnum;
import com.alibaba.nacos.config.server.exception.NacosConfigException;
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
import com.alibaba.nacos.config.server.service.transaction.DatabaseOperate;
import com.alibaba.nacos.config.server.service.transaction.SqlContextUtils;
import com.alibaba.nacos.config.server.utils.LogUtil;
import com.alibaba.nacos.config.server.utils.MD5;
import com.alibaba.nacos.config.server.utils.PaginationHelper;
import com.alibaba.nacos.config.server.utils.ParamUtils;
import com.alibaba.nacos.config.server.utils.PropertyUtil;
import com.alibaba.nacos.config.server.utils.event.EventDispatcher;
import com.alibaba.nacos.core.distributed.id.IdGeneratorManager;
import com.google.common.base.Joiner;
import com.google.common.collect.Lists;
import java.io.IOException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Optional;
import javax.annotation.PostConstruct;
import org.apache.commons.lang.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;

import static com.alibaba.nacos.config.server.service.RowMapperManager.CONFIG_ADVANCE_INFO_ROW_MAPPER;
import static com.alibaba.nacos.config.server.service.RowMapperManager.CONFIG_ALL_INFO_ROW_MAPPER;
import static com.alibaba.nacos.config.server.service.RowMapperManager.CONFIG_INFO4BETA_ROW_MAPPER;
import static com.alibaba.nacos.config.server.service.RowMapperManager.CONFIG_INFO4TAG_ROW_MAPPER;
import static com.alibaba.nacos.config.server.service.RowMapperManager.CONFIG_INFO_AGGR_ROW_MAPPER;
import static com.alibaba.nacos.config.server.service.RowMapperManager.CONFIG_INFO_BASE_ROW_MAPPER;
import static com.alibaba.nacos.config.server.service.RowMapperManager.CONFIG_INFO_BETA_WRAPPER_ROW_MAPPER;
import static com.alibaba.nacos.config.server.service.RowMapperManager.CONFIG_INFO_CHANGED_ROW_MAPPER;
import static com.alibaba.nacos.config.server.service.RowMapperManager.CONFIG_INFO_ROW_MAPPER;
import static com.alibaba.nacos.config.server.service.RowMapperManager.CONFIG_INFO_TAG_WRAPPER_ROW_MAPPER;
import static com.alibaba.nacos.config.server.service.RowMapperManager.CONFIG_INFO_WRAPPER_ROW_MAPPER;
import static com.alibaba.nacos.config.server.service.RowMapperManager.CONFIG_KEY_ROW_MAPPER;
import static com.alibaba.nacos.config.server.service.RowMapperManager.HISTORY_DETAIL_ROW_MAPPER;
import static com.alibaba.nacos.config.server.service.RowMapperManager.HISTORY_LIST_ROW_MAPPER;
import static com.alibaba.nacos.config.server.service.RowMapperManager.TENANT_INFO_ROW_MAPPER;
import static com.alibaba.nacos.config.server.utils.LogUtil.defaultLog;

/**
 * 数据库服务，提供ConfigInfo在数据库的存取<br> 3.0开始增加数据版本号, 并将物理删除改为逻辑删除<br> 3.0增加数据库切换功能
 *
 * @author boyan
 * @author leiwen.zh
 * @since 1.0
 */
@SuppressWarnings("PMD.MethodReturnWrapperTypeRule")
@Repository
public class PersistService {

    /**
     * @author klw
     * @Description: constant variables
     */
    public static final String SPOT = ".";
    private static final Object[] EMPTY_ARRAY = new Object[]{};
    private static final String SQL_FIND_ALL_CONFIG_INFO = "select id,data_id,group_id,tenant_id,app_name,content,type,md5,gmt_create,gmt_modified,src_user,src_ip,c_desc,c_use,effect,c_schema from config_info";
    private static final String SQL_TENANT_INFO_COUNT_BY_TENANT_ID = "select count(1) from tenant_info where tenant_id = ?";
    private static final String SQL_FIND_CONFIG_INFO_BY_IDS = "SELECT ID,data_id,group_id,tenant_id,app_name,content,md5 FROM config_info WHERE ";
    private static final String SQL_DELETE_CONFIG_INFO_BY_IDS = "DELETE FROM config_info WHERE ";
    private static final String CONFIG_INFO_ID = "config-info-id";
    private static final String CONFIG_HISTORY_ID = "config-history-id";
    private static final String CONFIG_TAG_RELATION_ID = "config-tag-relation-id";
    private static final String CONFIG_BETA_ID = "config-beta-id";
    private static final String NAMESPACE_ID = "namespace-id";
    private static final String USER_ID = "user-id";
    private static final String ROLE_ID = "role-id";
    private static final String PERMISSION_ID = "permissions_id";
    private final static int QUERY_LIMIT_SIZE = 50;
    private static String PATTERN_STR = "*";
    protected JdbcTemplate jt;
    protected TransactionTemplate tjt;
    @Autowired
    private DatabaseOperate databaseOperate;
    @Autowired
    private DynamicDataSource dynamicDataSource;
    @Autowired
    private IdGeneratorManager idGeneratorManager;
    private DataSourceService dataSourceService;

    @PostConstruct
    public void init() {
        dataSourceService = dynamicDataSource.getDataSource();

        jt = getJdbcTemplate();
        tjt = getTransactionTemplate();

        if (PropertyUtil.isEnableDistributedID()) {
            idGeneratorManager.register(
                    CONFIG_INFO_ID,
                    CONFIG_HISTORY_ID,
                    CONFIG_TAG_RELATION_ID,
                    CONFIG_BETA_ID,
                    NAMESPACE_ID,
                    USER_ID,
                    ROLE_ID,
                    PERMISSION_ID
            );
        }
    }

    public boolean checkMasterWritable() {
        return dataSourceService.checkMasterWritable();
    }

    public void setBasicDataSourceService(DataSourceService dataSourceService) {
        this.dataSourceService = dataSourceService;
    }

    public synchronized void reload() throws IOException {
        this.dataSourceService.reload();
    }

    // ----------------------- config_info 表 insert update delete

    /**
     * 单元测试用
     */
    public JdbcTemplate getJdbcTemplate() {
        return this.dataSourceService.getJdbcTemplate();
    }

    public TransactionTemplate getTransactionTemplate() {
        return this.dataSourceService.getTransactionTemplate();
    }

    public String getCurrentDBUrl() {
        return this.dataSourceService.getCurrentDBUrl();
    }

    public DatabaseOperate getDatabaseOperate() {
        return databaseOperate;
    }

    /**
     * //TODO 大事务提交
     * <p>
     * 添加普通配置信息，发布数据变更事件
     */
    public void addConfigInfo(final String srcIp, final String srcUser, final ConfigInfo configInfo,
                              final Timestamp time, final Map<String, Object> configAdvanceInfo, final boolean notify) {

        try {
            Long configId = null;
            Long configHistoryId = null;

            boolean enableDistributedID = PropertyUtil.isEnableDistributedID();

            if (enableDistributedID) {
                configId = idGeneratorManager.nextId(CONFIG_INFO_ID);
                configHistoryId = idGeneratorManager.nextId(CONFIG_HISTORY_ID);
            }

            addConfigInfoAtomic(configId, srcIp, srcUser, configInfo, time, configAdvanceInfo);
            String configTags = configAdvanceInfo == null ? null : (String) configAdvanceInfo.get("config_tags");

            addConfigTagsRelation(configId, configTags, configInfo.getDataId(), configInfo.getGroup(),
                    configInfo.getTenant());
            insertConfigHistoryAtomic(configHistoryId, configInfo, srcIp, srcUser, time, "I");

            boolean result = databaseOperate.update(SqlContextUtils.getCurrentSqlContext());

            if (!result) {
                throw new NacosConfigException("配置发布失败");
            }

            if (notify) {
                EventDispatcher.fireEvent(
                        new ConfigDataChangeEvent(false, configInfo.getDataId(), configInfo.getGroup(),
                                configInfo.getTenant(), time.getTime()));
            }
        } finally {
            SqlContextUtils.cleanCurrentSqlContext();
        }
    }

    /**
     * 添加普通配置信息，发布数据变更事件
     */
    public void addConfigInfo4Beta(ConfigInfo configInfo, String betaIps,
                                   String srcIp, String srcUser, Timestamp time, boolean notify) {
        String appNameTmp = StringUtils.isBlank(configInfo.getAppName()) ? StringUtils.EMPTY : configInfo.getAppName();
        String tenantTmp = StringUtils.isBlank(configInfo.getTenant()) ? StringUtils.EMPTY : configInfo.getTenant();
        try {
            String md5 = MD5.getInstance().getMD5String(configInfo.getContent());

            String sql = "INSERT INTO config_info_beta(data_id,group_id,tenant_id,app_name,content,md5,beta_ips,src_ip," +
                    "src_user,gmt_create,gmt_modified) VALUES(?,?,?,?,?,?,?,?,?,?,?)";

            Object[] args = new Object[]{
                    configInfo.getDataId(), configInfo.getGroup(), tenantTmp, appNameTmp, configInfo.getContent(), md5,
                    betaIps, srcIp, srcUser, time, time
            };

            SqlContextUtils.addSqlContext(sql, args);

            boolean result = databaseOperate.update(SqlContextUtils.getCurrentSqlContext());

            if (!result) {
                throw new NacosConfigException("【灰度】配置发布失败");
            }

            if (notify) {
                EventDispatcher.fireEvent(new ConfigDataChangeEvent(true, configInfo.getDataId(), configInfo.getGroup(),
                        tenantTmp, time.getTime()));
            }
        } finally {
            SqlContextUtils.cleanCurrentSqlContext();
        }
    }

    /**
     * 添加普通配置信息，发布数据变更事件
     */
    public void addConfigInfo4Tag(ConfigInfo configInfo, String tag, String srcIp, String srcUser, Timestamp time,
                                  boolean notify) {
        String appNameTmp = StringUtils.isBlank(configInfo.getAppName()) ? StringUtils.EMPTY : configInfo.getAppName();
        String tenantTmp = StringUtils.isBlank(configInfo.getTenant()) ? StringUtils.EMPTY : configInfo.getTenant();
        String tagTmp = StringUtils.isBlank(tag) ? StringUtils.EMPTY : tag.trim();
        try {
            String md5 = MD5.getInstance().getMD5String(configInfo.getContent());

            String sql = "INSERT INTO config_info_tag(data_id,group_id,tenant_id,tag_id,app_name,content,md5,src_ip,src_user,"
                    + "gmt_create,gmt_modified) VALUES(?,?,?,?,?,?,?,?,?,?,?)";

            Object[] args = new Object[]{
                    configInfo.getDataId(), configInfo.getGroup(), tenantTmp, tagTmp, appNameTmp, configInfo.getContent(), md5,
                    srcIp, srcUser, time, time};

            SqlContextUtils.addSqlContext(sql, args);

            boolean result = databaseOperate.update(SqlContextUtils.getCurrentSqlContext());

            if (!result) {
                throw new NacosConfigException("【标签】配置添加失败");
            }

            if (notify) {
                EventDispatcher.fireEvent(new ConfigDataChangeEvent(false, configInfo.getDataId(),
                        configInfo.getGroup(), tenantTmp, tagTmp, time.getTime()));
            }
        } finally {
            SqlContextUtils.cleanCurrentSqlContext();
        }
    }

    /**
     * 更新配置信息
     */
    public void updateConfigInfo(final ConfigInfo configInfo, final String srcIp, final String srcUser,
                                 final Timestamp time, final Map<String, Object> configAdvanceInfo,
                                 final boolean notify) {
        try {
            ConfigInfo oldConfigInfo = findConfigInfo(configInfo.getDataId(), configInfo.getGroup(),
                    configInfo.getTenant());
            String appNameTmp = oldConfigInfo.getAppName();
            // 用户传过来的appName不为空，则用持久化用户的appName，否则用db的;清空appName的时候需要传空串
            if (configInfo.getAppName() == null) {
                configInfo.setAppName(appNameTmp);
            }
            updateConfigInfoAtomic(configInfo, srcIp, srcUser, time, configAdvanceInfo);
            String configTags = configAdvanceInfo == null ? null : (String) configAdvanceInfo.get("config_tags");
            if (configTags != null) {
                // 删除所有tag，然后再重新创建
                removeTagByIdAtomic(oldConfigInfo.getId());
                addConfigTagsRelation(oldConfigInfo.getId(), configTags, configInfo.getDataId(),
                        configInfo.getGroup(), configInfo.getTenant());
            }
            insertConfigHistoryAtomic(oldConfigInfo.getId(), oldConfigInfo, srcIp, srcUser, time, "U");

            boolean result = databaseOperate.update(SqlContextUtils.getCurrentSqlContext());

            if (!result) {
                throw new NacosConfigException("配置修改失败");
            }

            if (notify) {
                EventDispatcher.fireEvent(new ConfigDataChangeEvent(false, configInfo.getDataId(),
                        configInfo.getGroup(), configInfo.getTenant(), time.getTime()));
            }
        } finally {
            SqlContextUtils.cleanCurrentSqlContext();
        }
    }

    /**
     * 更新配置信息
     */
    public void updateConfigInfo4Beta(ConfigInfo configInfo, String srcIp, String srcUser, Timestamp time,
                                      boolean notify) {
        String appNameTmp = StringUtils.isBlank(configInfo.getAppName()) ? StringUtils.EMPTY : configInfo.getAppName();
        String tenantTmp = StringUtils.isBlank(configInfo.getTenant()) ? StringUtils.EMPTY : configInfo.getTenant();
        try {
            String md5 = MD5.getInstance().getMD5String(configInfo.getContent());
            final String sql = "UPDATE config_info_beta SET content=?, md5 = ?, src_ip=?,src_user=?,gmt_modified=?,app_name=? WHERE "
                    + "data_id=? AND group_id=? AND tenant_id=?";
            final Object[] args = new Object[]{
                    configInfo.getContent(), md5, srcIp, srcUser, time, appNameTmp, configInfo.getDataId(),
                    configInfo.getGroup(), tenantTmp
            };

            SqlContextUtils.addSqlContext(sql, args);

            boolean result = databaseOperate.update(SqlContextUtils.getCurrentSqlContext());

            if (!result) {
                throw new NacosConfigException("【灰度】配置修改失败");
            }

            if (notify) {
                EventDispatcher.fireEvent(new ConfigDataChangeEvent(true, configInfo.getDataId(), configInfo.getGroup(),
                        tenantTmp, time.getTime()));
            }
        } finally {
            SqlContextUtils.cleanCurrentSqlContext();
        }
    }

    /**
     * 更新配置信息
     */
    public void updateConfigInfo4Tag(ConfigInfo configInfo, String tag, String srcIp, String srcUser, Timestamp time,
                                     boolean notify) {
        String appNameTmp = StringUtils.isBlank(configInfo.getAppName()) ? StringUtils.EMPTY : configInfo.getAppName();
        String tenantTmp = StringUtils.isBlank(configInfo.getTenant()) ? StringUtils.EMPTY : configInfo.getTenant();
        String tagTmp = StringUtils.isBlank(tag) ? StringUtils.EMPTY : tag.trim();
        try {
            String md5 = MD5.getInstance().getMD5String(configInfo.getContent());
            final String sql = "UPDATE config_info_tag SET content=?, md5 = ?, src_ip=?,src_user=?,gmt_modified=?,app_name=? WHERE "
                    + "data_id=? AND group_id=? AND tenant_id=? AND tag_id=?";
            final Object[] args = new Object[]{
                    configInfo.getContent(), md5, srcIp, srcUser, time, appNameTmp, configInfo.getDataId(),
                    configInfo.getGroup(), tenantTmp, tagTmp
            };

            SqlContextUtils.addSqlContext(sql, args);

            boolean result = databaseOperate.update(SqlContextUtils.getCurrentSqlContext());

            if (!result) {
                throw new NacosConfigException("【标签】配置修改失败");
            }

            if (notify) {
                EventDispatcher.fireEvent(new ConfigDataChangeEvent(true, configInfo.getDataId(), configInfo.getGroup(),
                        tenantTmp, tagTmp, time.getTime()));
            }
        } finally {
            SqlContextUtils.cleanCurrentSqlContext();
        }
    }

    public void insertOrUpdateBeta(final ConfigInfo configInfo, final String betaIps, final String srcIp,
                                   final String srcUser, final Timestamp time, final boolean notify) {
        if (findConfigInfo4Beta(configInfo.getDataId(), configInfo.getGroup(), configInfo.getTenant()) == null) {
            addConfigInfo4Beta(configInfo, betaIps, srcIp, null, time, notify);
        } else {
            updateConfigInfo4Beta(configInfo, srcIp, null, time, notify);
        }
    }

    public void insertOrUpdateTag(final ConfigInfo configInfo, final String tag, final String srcIp,
                                  final String srcUser, final Timestamp time, final boolean notify) {
        if (findConfigInfo4Tag(configInfo.getDataId(), configInfo.getGroup(), configInfo.getTenant(), tag) == null) {
            addConfigInfo4Tag(configInfo, tag, srcIp, null, time, notify);
        } else {
            updateConfigInfo4Tag(configInfo, tag, srcIp, null, time, notify);
        }
    }

    /**
     * 更新md5
     */
    public void updateMd5(String dataId, String group, String tenant, String md5, Timestamp lastTime) {
        String tenantTmp = StringUtils.isBlank(tenant) ? StringUtils.EMPTY : tenant;
        try {

            final String sql = "UPDATE config_info SET md5 = ? WHERE data_id=? AND group_id=? AND tenant_id=? AND gmt_modified=?";
            final Object[] args = new Object[]{
                    md5, dataId, group, tenantTmp, lastTime
            };

            SqlContextUtils.addSqlContext(sql, args);

            boolean result = databaseOperate.update(SqlContextUtils.getCurrentSqlContext());
            if (!result) {
                throw new NacosConfigException("配置 MD5 修改失败");
            }
        } finally {
            SqlContextUtils.cleanCurrentSqlContext();
        }
    }

    public void insertOrUpdate(String srcIp, String srcUser, ConfigInfo configInfo, Timestamp time,
                               Map<String, Object> configAdvanceInfo) {
        insertOrUpdate(srcIp, srcUser, configInfo, time, configAdvanceInfo, true);
    }

    /**
     * 写入主表，插入或更新
     */
    public void insertOrUpdate(String srcIp, String srcUser, ConfigInfo configInfo, Timestamp time,
                               Map<String, Object> configAdvanceInfo, boolean notify) {
        if (findConfigInfo(configInfo.getDataId(), configInfo.getGroup(), configInfo.getTenant()) == null) {
            addConfigInfo(srcIp, srcUser, configInfo, time, configAdvanceInfo, notify);
        } else {
            updateConfigInfo(configInfo, srcIp, srcUser, time, configAdvanceInfo, notify);
        }
    }

    // ----------------------- config_aggr_info 表 insert update delete

    /**
     * // TODO 暂时未对外开放使用
     * <p>
     * 写入主表，插入或更新
     */
    public void insertOrUpdateSub(SubInfo subInfo) {
        try {
            addConfigSubAtomic(subInfo.getDataId(), subInfo.getGroup(), subInfo.getAppName(), subInfo.getDate());
        } catch (DataIntegrityViolationException ive) { // 唯一性约束冲突
            updateConfigSubAtomic(subInfo.getDataId(), subInfo.getGroup(), subInfo.getAppName(), subInfo.getDate());
        }
    }

    /**
     * 删除配置信息, 物理删除
     */
    public void removeConfigInfo(final String dataId, final String group, final String tenant, final String srcIp,
                                 final String srcUser) {
        final Timestamp time = new Timestamp(System.currentTimeMillis());
        ConfigInfo configInfo = findConfigInfo(dataId, group, tenant);
        if (configInfo != null) {
            try {
                removeConfigInfoAtomic(dataId, group, tenant, srcIp, srcUser);
                removeTagByIdAtomic(configInfo.getId());
                insertConfigHistoryAtomic(configInfo.getId(), configInfo, srcIp, srcUser, time, "D");

                boolean result = databaseOperate.update(SqlContextUtils.getCurrentSqlContext());
                if (!result) {
                    throw new NacosConfigException("配置删除失败");
                }

            } finally {
                SqlContextUtils.cleanCurrentSqlContext();
            }
        }
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
        final Timestamp time = new Timestamp(System.currentTimeMillis());
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

            boolean result = databaseOperate.update(SqlContextUtils.getCurrentSqlContext());
            if (!result) {
                throw new NacosConfigException("配置批量删除失败");
            }

            return configInfoList;
        } finally {
            SqlContextUtils.cleanCurrentSqlContext();
        }
    }

    /**
     * 删除beta配置信息, 物理删除
     */
    public void removeConfigInfo4Beta(final String dataId, final String group, final String tenant) {
        final String tenantTmp = StringUtils.isBlank(tenant) ? StringUtils.EMPTY : tenant;
        ConfigInfo configInfo = findConfigInfo4Beta(dataId, group, tenant);
        if (configInfo != null) {
            try {
                final String sql = "DELETE FROM config_info_beta WHERE data_id=? AND group_id=? AND tenant_id=?";
                final Object[] args = new Object[]{
                        dataId, group, tenantTmp
                };
                SqlContextUtils.addSqlContext(sql, args);

                boolean result = databaseOperate.update(SqlContextUtils.getCurrentSqlContext());
                if (!result) {
                    throw new NacosConfigException("【标签】配置删除失败");
                }
            } finally {
                SqlContextUtils.cleanCurrentSqlContext();
            }

        }
    }

    /**
     * 增加聚合前数据到数据库, select -> update or insert
     */
    public void addAggrConfigInfo(final String dataId, final String group, String tenant, final String datumId,
                                  String appName, final String content) {
        String appNameTmp = StringUtils.isBlank(appName) ? StringUtils.EMPTY : appName;
        String tenantTmp = StringUtils.isBlank(tenant) ? StringUtils.EMPTY : tenant;
        String contentTmp = StringUtils.isBlank(content) ? StringUtils.EMPTY : content;
        final Timestamp now = new Timestamp(System.currentTimeMillis());
        String select
                = "SELECT content FROM config_info_aggr WHERE data_id = ? AND group_id = ? AND tenant_id = ?  AND "
                + "datum_id = ?";
        String insert
                = "INSERT INTO config_info_aggr(data_id, group_id, tenant_id, datum_id, app_name, content, gmt_modified) "
                + "VALUES(?,?,?,?,?,?,?) ";
        String update
                = "UPDATE config_info_aggr SET content = ? , gmt_modified = ? WHERE data_id = ? AND group_id = ? AND "
                + "tenant_id = ? AND datum_id = ?";

        String dbContent = databaseOperate.queryOne(select, new Object[]{dataId, group, tenantTmp, datumId},
                String.class);

        if (dbContent == null) {
            final Object[] args = new Object[]{
                    dataId, group, tenantTmp, datumId, appNameTmp, contentTmp, now
            };
            SqlContextUtils.addSqlContext(insert, args);
        } else if (!dbContent.equals(content)) {
            final Object[] args = new Object[]{
                    contentTmp, now, dataId, group, tenantTmp, datumId
            };
            SqlContextUtils.addSqlContext(update, args);
        }

        try {
            boolean result = databaseOperate.update(SqlContextUtils.getCurrentSqlContext());
            if (!result) {
                throw new NacosConfigException("【聚合】配置发布失败");
            }
        } finally {
            SqlContextUtils.cleanCurrentSqlContext();
        }
    }

    /**
     * 删除单条聚合前数据
     */
    public void removeSingleAggrConfigInfo(final String dataId,
                                           final String group, final String tenant, final String datumId) {
        final String tenantTmp = StringUtils.isBlank(tenant) ? StringUtils.EMPTY : tenant;
        String sql = "DELETE FROM config_info_aggr WHERE data_id=? AND group_id=? AND tenant_id=? AND datum_id=?";

        try {

            final Object[] args = new Object[]{
                    dataId, group, tenantTmp, datumId
            };

            SqlContextUtils.addSqlContext(sql, args);

            boolean result = databaseOperate.update(SqlContextUtils.getCurrentSqlContext());
            if (!result) {
                throw new NacosConfigException("【聚合单】配置删除失败");
            }
        } finally {
            SqlContextUtils.cleanCurrentSqlContext();
        }
    }

    /**
     * 删除一个dataId下面所有的聚合前数据
     */
    public void removeAggrConfigInfo(final String dataId, final String group, final String tenant) {
        final String tenantTmp = StringUtils.isBlank(tenant) ? StringUtils.EMPTY : tenant;
        String sql = "DELETE FROM config_info_aggr WHERE data_id=? AND group_id=? AND tenant_id=?";

        try {
            final Object[] args = new Object[]{
                    dataId, group, tenantTmp
            };
            SqlContextUtils.addSqlContext(sql, args);
            boolean result = databaseOperate.update(SqlContextUtils.getCurrentSqlContext());
            if (!result) {
                throw new NacosConfigException("【聚合全】配置删除失败");
            }
        } finally {
            SqlContextUtils.cleanCurrentSqlContext();
        }
    }

    /**
     * 批量删除聚合数据，需要指定datum的列表
     *
     * @param dataId
     * @param group
     * @param datumList
     */
    public boolean batchRemoveAggr(final String dataId, final String group, final String tenant,
                                   final List<String> datumList) {
        final String tenantTmp = StringUtils.isBlank(tenant) ? StringUtils.EMPTY : tenant;
        final StringBuilder datumString = new StringBuilder();
        for (String datum : datumList) {
            datumString.append("'").append(datum).append("',");
        }
        datumString.deleteCharAt(datumString.length() - 1);
        final String sql =
                "delete from config_info_aggr where data_id=? and group_id=? and tenant_id=? and datum_id in ("
                        + datumString.toString() + ")";
        try {

            final Object[] args = new Object[]{
                    dataId, group, tenantTmp
            };
            SqlContextUtils.addSqlContext(sql, args);
            boolean result = databaseOperate.update(SqlContextUtils.getCurrentSqlContext());
            if (!result) {
                throw new NacosConfigException("【聚合】配置批量删除失败");
            }
            return true;
        } finally {
            SqlContextUtils.cleanCurrentSqlContext();
        }
    }

    /**
     * 删除startTime前的数据
     */
    public void removeConfigHistory(final Timestamp startTime, final int limitSize) {
        String sql = "delete from his_config_info where gmt_modified < ? limit ?";
        PaginationHelper<ConfigInfo> helper = new PaginationHelper<ConfigInfo>();
        helper.updateLimit(databaseOperate, sql, new Object[]{startTime, limitSize});
    }

    /**
     * 获取指定时间前配置条数
     */
    public int findConfigHistoryCountByTime(final Timestamp startTime) {
        String sql = "SELECT COUNT(*) FROM his_config_info WHERE gmt_modified < ?";
        Integer result = databaseOperate.queryOne(sql, new Object[]{startTime}, Integer.class);
        if (result == null) {
            throw new IllegalArgumentException("configInfoBetaCount error");
        }
        return result.intValue();
    }

    /**
     * 获取最大maxId
     */
    public long findConfigMaxId() {
        String sql = "SELECT max(id) FROM config_info";
        return Optional.ofNullable(databaseOperate.queryOne(sql, Long.class)).orElse(0L);
    }

    /**
     * 批量添加或者更新数据.事务过程中出现任何异常都会强制抛出TransactionSystemException
     *
     * @param dataId
     * @param group
     * @param datumMap
     * @return
     */
    public boolean batchPublishAggr(final String dataId, final String group, final String tenant,
                                    final Map<String, String> datumMap, final String appName) {
        try {
            Boolean isPublishOk = false;
            for (Entry<String, String> entry : datumMap.entrySet()) {
                addAggrConfigInfo(dataId, group, tenant, entry.getKey(), appName, entry.getValue());
            }

            isPublishOk = databaseOperate.update(SqlContextUtils.getCurrentSqlContext());

            if (isPublishOk == null) {
                return false;
            }
            return isPublishOk;
        } finally {
            SqlContextUtils.cleanCurrentSqlContext();
        }
    }

    /**
     * 批量替换，先全部删除聚合表中指定DataID+Group的数据，再插入数据. 事务过程中出现任何异常都会强制抛出TransactionSystemException
     *
     * @param dataId
     * @param group
     * @param datumMap
     * @return
     */
    public boolean replaceAggr(final String dataId, final String group, final String tenant,
                               final Map<String, String> datumMap, final String appName) {
        Boolean isReplaceOk = false;
        String appNameTmp = appName == null ? "" : appName;
        removeAggrConfigInfo(dataId, group, tenant);
        String tenantTmp = StringUtils.isBlank(tenant) ? StringUtils.EMPTY : tenant;
        String sql
                = "INSERT INTO config_info_aggr(data_id, group_id, tenant_id, datum_id, app_name, "
                + "content, gmt_modified) VALUES(?,?,?,?,?,?,?) ";
        for (Entry<String, String> datumEntry : datumMap.entrySet()) {
            final Object[] args = new Object[]{
                    dataId, group, tenantTmp, datumEntry.getKey(), appNameTmp,
                    datumEntry.getValue(), new Timestamp(System.currentTimeMillis())
            };
            SqlContextUtils.addSqlContext(sql, args);
        }
        try {
            isReplaceOk = databaseOperate.update(SqlContextUtils.getCurrentSqlContext());

            if (isReplaceOk == null) {
                return false;
            }
            return isReplaceOk;
        } finally {
            SqlContextUtils.cleanCurrentSqlContext();
        }

    }

    /**
     * 查找所有的dataId和group。保证不返回NULL。
     */
    @Deprecated
    public List<ConfigInfo> findAllDataIdAndGroup() {
        String sql = "SELECT DISTINCT data_id, group_id FROM config_info";
        return databaseOperate.queryMany(sql, EMPTY_ARRAY, CONFIG_INFO_ROW_MAPPER);
    }

    /**
     * 根据dataId和group查询配置信息
     */
    public ConfigInfo4Beta findConfigInfo4Beta(final String dataId, final String group, final String tenant) {
        String tenantTmp = StringUtils.isBlank(tenant) ? StringUtils.EMPTY : tenant;
        final String sql = "SELECT ID,data_id,group_id,tenant_id,app_name,content,beta_ips FROM config_info_beta WHERE data_id=?"
                + " AND group_id=? AND tenant_id=?";

        return databaseOperate.queryOne(
                sql,
                new Object[]{dataId, group, tenantTmp}, CONFIG_INFO4BETA_ROW_MAPPER);

    }

    /**
     * 根据dataId和group查询配置信息
     */
    public ConfigInfo4Tag findConfigInfo4Tag(final String dataId, final String group, final String tenant,
                                             final String tag) {
        String tenantTmp = StringUtils.isBlank(tenant) ? StringUtils.EMPTY : tenant;
        String tagTmp = StringUtils.isBlank(tag) ? StringUtils.EMPTY : tag.trim();
        final String sql = "SELECT ID,data_id,group_id,tenant_id,tag_id,app_name,content FROM config_info_tag WHERE data_id=? "
                + "AND group_id=? AND tenant_id=? AND tag_id=?";

        return databaseOperate.queryOne(
                sql,
                new Object[]{dataId, group, tenantTmp, tagTmp}, CONFIG_INFO4TAG_ROW_MAPPER);
    }

    /**
     * 根据dataId和group查询配置信息
     */
    public ConfigInfo findConfigInfoApp(final String dataId, final String group, final String tenant,
                                        final String appName) {
        String tenantTmp = StringUtils.isBlank(tenant) ? StringUtils.EMPTY : tenant;
        final String sql = "SELECT ID,data_id,group_id,tenant_id,app_name,content FROM config_info WHERE data_id=? AND "
                + "group_id=? AND tenant_id=? AND app_name=?";

        return databaseOperate.queryOne(
                sql,
                new Object[]{dataId, group, tenantTmp, appName}, CONFIG_INFO_ROW_MAPPER);

    }

    /**
     * 根据dataId和group查询配置信息
     */
    public ConfigInfo findConfigInfoAdvanceInfo(final String dataId, final String group, final String tenant,
                                                final Map<String, Object> configAdvanceInfo) {
        String tenantTmp = StringUtils.isBlank(tenant) ? StringUtils.EMPTY : tenant;
        final String appName = configAdvanceInfo == null ? null : (String) configAdvanceInfo.get("appName");
        final String configTags = configAdvanceInfo == null ? null : (String) configAdvanceInfo.get("config_tags");
        List<String> paramList = new ArrayList<String>();
        paramList.add(dataId);
        paramList.add(group);
        paramList.add(tenantTmp);

        StringBuilder sql = new StringBuilder(
                "select ID,data_id,group_id,tenant_id,app_name,content from config_info where data_id=? and group_id=? "
                        + "and tenant_id=? ");
        if (StringUtils.isNotBlank(configTags)) {
            sql = new StringBuilder(
                    "select a.ID,a.data_id,a.group_id,a.tenant_id,a.app_name,a.content from config_info  a left join "
                            + "config_tags_relation b on a.id=b.id where a.data_id=? and a.group_id=? and a.tenant_id=? ");
            sql.append(" and b.tag_name in (");
            String[] tagArr = configTags.split(",");
            for (int i = 0; i < tagArr.length; i++) {
                if (i != 0) {
                    sql.append(", ");
                }
                sql.append("?");
                paramList.add(tagArr[i]);
            }
            sql.append(") ");

            if (StringUtils.isNotBlank(appName)) {
                sql.append(" and a.app_name=? ");
                paramList.add(appName);
            }
        } else {
            if (StringUtils.isNotBlank(appName)) {
                sql.append(" and app_name=? ");
                paramList.add(appName);
            }
        }

        return databaseOperate.queryOne(sql.toString(), paramList.toArray(), CONFIG_INFO_ROW_MAPPER);
    }

    /**
     * 根据dataId和group查询配置信息
     */
    public ConfigInfoBase findConfigInfoBase(final String dataId, final String group) {
        final String sql = "SELECT ID,data_id,group_id,content FROM config_info WHERE data_id=? AND group_id=? AND "
                + "tenant_id=?";

        return databaseOperate
                .queryOne(
                        sql,
                        new Object[]{dataId, group, StringUtils.EMPTY},
                        CONFIG_INFO_BASE_ROW_MAPPER);
    }

    /**
     * 根据数据库主键ID查询配置信息
     *
     * @param id
     * @return
     */
    public ConfigInfo findConfigInfo(long id) {
        final String sql = "SELECT ID,data_id,group_id,tenant_id,app_name,content FROM config_info WHERE ID=?";

        return databaseOperate
                .queryOne(
                        sql,
                        new Object[]{id}, CONFIG_INFO_ROW_MAPPER);
    }

    /**
     * 根据dataId查询配置信息
     *
     * @param pageNo   页码(必须大于0)
     * @param pageSize 每页大小(必须大于0)
     * @param dataId
     * @return ConfigInfo对象的集合
     */
    public Page<ConfigInfo> findConfigInfoByDataId(final int pageNo, final int pageSize, final String dataId,
                                                   final String tenant) {
        String tenantTmp = StringUtils.isBlank(tenant) ? StringUtils.EMPTY : tenant;
        PaginationHelper<ConfigInfo> helper = new PaginationHelper<ConfigInfo>();
        return helper.fetchPage(databaseOperate, "select count(*) from config_info where data_id=? and tenant_id=?",
                "select ID,data_id,group_id,tenant_id,app_name,content from config_info where data_id=? and "
                        + "tenant_id=?",
                new Object[]{dataId, tenantTmp}, pageNo, pageSize, CONFIG_INFO_ROW_MAPPER);
    }

    /**
     * 根据dataId查询配置信息
     *
     * @param pageNo   页码(必须大于0)
     * @param pageSize 每页大小(必须大于0)
     * @param dataId
     * @return ConfigInfo对象的集合
     */
    public Page<ConfigInfo> findConfigInfoByDataIdAndApp(final int pageNo, final int pageSize, final String dataId,
                                                         final String tenant, final String appName) {
        String tenantTmp = StringUtils.isBlank(tenant) ? StringUtils.EMPTY : tenant;
        PaginationHelper<ConfigInfo> helper = new PaginationHelper<ConfigInfo>();
        return helper.fetchPage(databaseOperate,
                "select count(*) from config_info where data_id=? and tenant_id=? and app_name=?",
                "select ID,data_id,group_id,tenant_id,app_name,content from config_info where data_id=? and "
                        + "tenant_id=? and app_name=?",
                new Object[]{dataId, tenantTmp, appName}, pageNo, pageSize, CONFIG_INFO_ROW_MAPPER);
    }

    public Page<ConfigInfo> findConfigInfoByDataIdAndAdvance(final int pageNo, final int pageSize, final String dataId,
                                                             final String tenant,
                                                             final Map<String, Object> configAdvanceInfo) {
        String tenantTmp = StringUtils.isBlank(tenant) ? StringUtils.EMPTY : tenant;
        PaginationHelper<ConfigInfo> helper = new PaginationHelper<ConfigInfo>();
        final String appName = configAdvanceInfo == null ? null : (String) configAdvanceInfo.get("appName");
        final String configTags = configAdvanceInfo == null ? null : (String) configAdvanceInfo.get("config_tags");
        StringBuilder sqlCount = new StringBuilder("select count(*) from config_info where data_id=? and tenant_id=? ");
        StringBuilder sql = new StringBuilder(
                "select ID,data_id,group_id,tenant_id,app_name,content from config_info where data_id=? and tenant_id=? ");
        List<String> paramList = new ArrayList<String>();
        paramList.add(dataId);
        paramList.add(tenantTmp);
        if (StringUtils.isNotBlank(configTags)) {
            sqlCount = new StringBuilder(
                    "select count(*) from config_info  a left join config_tags_relation b on a.id=b.id where a.data_id=? "
                            + "and a.tenant_id=? ");

            sql = new StringBuilder(
                    "select a.ID,a.data_id,a.group_id,a.tenant_id,a.app_name,a.content from config_info  a left join "
                            + "config_tags_relation b on a.id=b.id where a.data_id=? and a.tenant_id=? ");

            sqlCount.append(" and b.tag_name in (");
            sql.append(" and b.tag_name in (");
            String[] tagArr = configTags.split(",");
            for (int i = 0; i < tagArr.length; i++) {
                if (i != 0) {
                    sqlCount.append(", ");
                    sql.append(", ");
                }
                sqlCount.append("?");
                sql.append("?");
                paramList.add(tagArr[i]);
            }
            sqlCount.append(") ");
            sql.append(") ");

            if (StringUtils.isNotBlank(appName)) {
                sqlCount.append(" and a.app_name=? ");
                sql.append(" and a.app_name=? ");
                paramList.add(appName);
            }
        } else {
            if (StringUtils.isNotBlank(appName)) {
                sqlCount.append(" and app_name=? ");
                sql.append(" and app_name=? ");
                paramList.add(appName);
            }
        }
        return helper.fetchPage(databaseOperate, sqlCount.toString(), sql.toString(), paramList.toArray(), pageNo, pageSize,
                CONFIG_INFO_ROW_MAPPER);
    }

    public Page<ConfigInfo> findConfigInfo4Page(final int pageNo, final int pageSize, final String dataId,
                                                final String group,
                                                final String tenant, final Map<String, Object> configAdvanceInfo) {
        String tenantTmp = StringUtils.isBlank(tenant) ? StringUtils.EMPTY : tenant;
        PaginationHelper<ConfigInfo> helper = new PaginationHelper<ConfigInfo>();
        final String appName = configAdvanceInfo == null ? null : (String) configAdvanceInfo.get("appName");
        final String configTags = configAdvanceInfo == null ? null : (String) configAdvanceInfo.get("config_tags");
        String sqlCount = "select count(*) from config_info";
        String sql = "select ID,data_id,group_id,tenant_id,app_name,content,type from config_info";
        StringBuilder where = new StringBuilder(" where ");
        List<String> paramList = new ArrayList<String>();
        paramList.add(tenantTmp);
        if (StringUtils.isNotBlank(configTags)) {
            sqlCount = "select count(*) from config_info  a left join config_tags_relation b on a.id=b.id";
            sql
                    = "select a.ID,a.data_id,a.group_id,a.tenant_id,a.app_name,a.content from config_info  a left join "
                    + "config_tags_relation b on a.id=b.id";

            where.append(" a.tenant_id=? ");

            if (StringUtils.isNotBlank(dataId)) {
                where.append(" and a.data_id=? ");
                paramList.add(dataId);
            }
            if (StringUtils.isNotBlank(group)) {
                where.append(" and a.group_id=? ");
                paramList.add(group);
            }
            if (StringUtils.isNotBlank(appName)) {
                where.append(" and a.app_name=? ");
                paramList.add(appName);
            }

            where.append(" and b.tag_name in (");
            String[] tagArr = configTags.split(",");
            for (int i = 0; i < tagArr.length; i++) {
                if (i != 0) {
                    where.append(", ");
                }
                where.append("?");
                paramList.add(tagArr[i]);
            }
            where.append(") ");
        } else {
            where.append(" tenant_id=? ");
            if (StringUtils.isNotBlank(dataId)) {
                where.append(" and data_id=? ");
                paramList.add(dataId);
            }
            if (StringUtils.isNotBlank(group)) {
                where.append(" and group_id=? ");
                paramList.add(group);
            }
            if (StringUtils.isNotBlank(appName)) {
                where.append(" and app_name=? ");
                paramList.add(appName);
            }
        }
        return helper.fetchPage(databaseOperate, sqlCount + where, sql + where, paramList.toArray(), pageNo, pageSize,
                CONFIG_INFO_ROW_MAPPER);
    }

    /**
     * 根据dataId查询配置信息
     *
     * @param pageNo   页码(必须大于0)
     * @param pageSize 每页大小(必须大于0)
     * @param dataId
     * @return ConfigInfo对象的集合
     */
    public Page<ConfigInfoBase> findConfigInfoBaseByDataId(final int pageNo,
                                                           final int pageSize, final String dataId) {
        PaginationHelper<ConfigInfoBase> helper = new PaginationHelper<ConfigInfoBase>();
        return helper
                .fetchPage(
                        databaseOperate,
                        "select count(*) from config_info where data_id=? and tenant_id=?",
                        "select ID,data_id,group_id,content from config_info where data_id=? and tenant_id=?",
                        new Object[]{dataId, StringUtils.EMPTY}, pageNo, pageSize,
                        CONFIG_INFO_BASE_ROW_MAPPER);

    }

    /**
     * 根据group查询配置信息
     *
     * @param pageNo   页码(必须大于0)
     * @param pageSize 每页大小(必须大于0)
     * @param group
     * @return ConfigInfo对象的集合
     */
    public Page<ConfigInfo> findConfigInfoByGroup(final int pageNo, final int pageSize, final String group,
                                                  final String tenant) {
        String tenantTmp = StringUtils.isBlank(tenant) ? StringUtils.EMPTY : tenant;
        PaginationHelper<ConfigInfo> helper = new PaginationHelper<ConfigInfo>();
        return helper.fetchPage(databaseOperate, "select count(*) from config_info where group_id=? and tenant_id=?",
                "select ID,data_id,group_id,tenant_id,app_name,content from config_info where group_id=? and "
                        + "tenant_id=?",
                new Object[]{group, tenantTmp}, pageNo, pageSize, CONFIG_INFO_ROW_MAPPER);

    }

    /**
     * 根据group查询配置信息
     *
     * @param pageNo   页码(必须大于0)
     * @param pageSize 每页大小(必须大于0)
     * @param group
     * @return ConfigInfo对象的集合
     */
    public Page<ConfigInfo> findConfigInfoByGroupAndApp(final int pageNo,
                                                        final int pageSize, final String group, final String tenant,
                                                        final String appName) {
        String tenantTmp = StringUtils.isBlank(tenant) ? StringUtils.EMPTY : tenant;
        PaginationHelper<ConfigInfo> helper = new PaginationHelper<ConfigInfo>();
        return helper.fetchPage(databaseOperate,
                "select count(*) from config_info where group_id=? and tenant_id=? and app_name =?",
                "select ID,data_id,group_id,tenant_id,app_name,content from config_info where group_id=? and "
                        + "tenant_id=? and app_name =?",
                new Object[]{group, tenantTmp, appName}, pageNo, pageSize, CONFIG_INFO_ROW_MAPPER);

    }

    public Page<ConfigInfo> findConfigInfoByGroupAndAdvance(final int pageNo,
                                                            final int pageSize, final String group, final String tenant,
                                                            final Map<String, Object> configAdvanceInfo) {
        String tenantTmp = StringUtils.isBlank(tenant) ? StringUtils.EMPTY : tenant;
        PaginationHelper<ConfigInfo> helper = new PaginationHelper<ConfigInfo>();

        final String appName = configAdvanceInfo == null ? null : (String) configAdvanceInfo.get("appName");
        final String configTags = configAdvanceInfo == null ? null : (String) configAdvanceInfo.get("config_tags");
        StringBuilder sqlCount = new StringBuilder(
                "select count(*) from config_info where group_id=? and tenant_id=? ");
        StringBuilder sql = new StringBuilder(
                "select ID,data_id,group_id,tenant_id,app_name,content from config_info where group_id=? and tenant_id=? ");
        List<String> paramList = new ArrayList<String>();
        paramList.add(group);
        paramList.add(tenantTmp);
        if (StringUtils.isNotBlank(configTags)) {
            sqlCount = new StringBuilder(
                    "select count(*) from config_info  a left join config_tags_relation b on a.id=b.id where a.group_id=?"
                            + " and a.tenant_id=? ");
            sql = new StringBuilder(
                    "select a.ID,a.data_id,a.group_id,a.tenant_id,a.app_name,a.content from config_info  a left join "
                            + "config_tags_relation b on a.id=b.id where a.group_id=? and a.tenant_id=? ");

            sqlCount.append(" and b.tag_name in (");
            sql.append(" and b.tag_name in (");
            String[] tagArr = configTags.split(",");
            for (int i = 0; i < tagArr.length; i++) {
                if (i != 0) {
                    sqlCount.append(", ");
                    sql.append(", ");
                }
                sqlCount.append("?");
                sql.append("?");
                paramList.add(tagArr[i]);
            }
            sqlCount.append(") ");
            sql.append(") ");

            if (StringUtils.isNotBlank(appName)) {
                sqlCount.append(" and a.app_name=? ");
                sql.append(" and a.app_name=? ");
                paramList.add(appName);
            }
        } else {
            if (StringUtils.isNotBlank(appName)) {
                sqlCount.append(" and app_name=? ");
                sql.append(" and app_name=? ");
                paramList.add(appName);
            }
        }

        return helper.fetchPage(databaseOperate, sqlCount.toString(), sql.toString(), paramList.toArray(), pageNo, pageSize,
                CONFIG_INFO_ROW_MAPPER);

    }

    /**
     * 根据group查询配置信息
     *
     * @param pageNo   页码(必须大于0)
     * @param pageSize 每页大小(必须大于0)
     * @return ConfigInfo对象的集合
     */
    public Page<ConfigInfo> findConfigInfoByApp(final int pageNo,
                                                final int pageSize, final String tenant, final String appName) {
        String tenantTmp = StringUtils.isBlank(tenant) ? StringUtils.EMPTY : tenant;
        PaginationHelper<ConfigInfo> helper = new PaginationHelper<ConfigInfo>();
        return helper.fetchPage(databaseOperate, "select count(*) from config_info where tenant_id like ? and app_name=?",
                "select ID,data_id,group_id,tenant_id,app_name,content from config_info where tenant_id like ? and "
                        + "app_name=?",
                new Object[]{generateLikeArgument(tenantTmp), appName}, pageNo, pageSize,
                CONFIG_INFO_ROW_MAPPER);

    }

    public Page<ConfigInfo> findConfigInfoByAdvance(final int pageNo,
                                                    final int pageSize, final String tenant,
                                                    final Map<String, Object> configAdvanceInfo) {
        String tenantTmp = StringUtils.isBlank(tenant) ? StringUtils.EMPTY : tenant;
        PaginationHelper<ConfigInfo> helper = new PaginationHelper<ConfigInfo>();
        final String appName = configAdvanceInfo == null ? null : (String) configAdvanceInfo.get("appName");
        final String configTags = configAdvanceInfo == null ? null : (String) configAdvanceInfo.get("config_tags");
        StringBuilder sqlCount = new StringBuilder("select count(*) from config_info where tenant_id like ? ");
        StringBuilder sql = new StringBuilder(
                "select ID,data_id,group_id,tenant_id,app_name,content from config_info where tenant_id like ? ");
        List<String> paramList = new ArrayList<String>();
        paramList.add(tenantTmp);
        if (StringUtils.isNotBlank(configTags)) {
            sqlCount = new StringBuilder(
                    "select count(*) from config_info a left join config_tags_relation b on a.id=b.id where a.tenant_id=?"
                            + " ");

            sql = new StringBuilder(
                    "select a.ID,a.data_id,a.group_id,a.tenant_id,a.app_name,a.content from config_info  a left join "
                            + "config_tags_relation b on a.id=b.id where a.tenant_id=? ");

            sqlCount.append(" and b.tag_name in (");
            sql.append(" and b.tag_name in (");
            String[] tagArr = configTags.split(",");
            for (int i = 0; i < tagArr.length; i++) {
                if (i != 0) {
                    sqlCount.append(", ");
                    sql.append(", ");
                }
                sqlCount.append("?");
                sql.append("?");
                paramList.add(tagArr[i]);
            }
            sqlCount.append(") ");
            sql.append(") ");

            if (StringUtils.isNotBlank(appName)) {
                sqlCount.append(" and a.app_name=? ");
                sql.append(" and a.app_name=? ");
                paramList.add(appName);
            }
        } else {
            if (StringUtils.isNotBlank(appName)) {
                sqlCount.append(" and app_name=? ");
                sql.append(" and app_name=? ");
                paramList.add(appName);
            }
        }

        return helper.fetchPage(databaseOperate, sqlCount.toString(), sql.toString(), paramList.toArray(), pageNo, pageSize,
                CONFIG_INFO_ROW_MAPPER);

    }

    /**
     * 根据group查询配置信息
     *
     * @param pageNo   页码(必须大于0)
     * @param pageSize 每页大小(必须大于0)
     * @param group
     * @return ConfigInfo对象的集合
     */
    public Page<ConfigInfoBase> findConfigInfoBaseByGroup(final int pageNo,
                                                          final int pageSize, final String group) {
        PaginationHelper<ConfigInfoBase> helper = new PaginationHelper<ConfigInfoBase>();
        return helper
                .fetchPage(
                        databaseOperate,
                        "select count(*) from config_info where group_id=? and tenant_id=?",
                        "select ID,data_id,group_id,content from config_info where group_id=? and tenant_id=?",
                        new Object[]{group, StringUtils.EMPTY}, pageNo, pageSize,
                        CONFIG_INFO_BASE_ROW_MAPPER);
    }

    /**
     * 返回配置项个数
     */
    public int configInfoCount() {
        String sql = " SELECT COUNT(ID) FROM config_info ";
        Integer result = databaseOperate.queryOne(sql, Integer.class);
        if (result == null) {
            throw new IllegalArgumentException("configInfoCount error");
        }
        return result;
    }

    /**
     * 返回配置项个数
     */
    public int configInfoCount(String tenant) {
        String sql = " SELECT COUNT(ID) FROM config_info where tenant_id like '" + tenant + "'";
        Integer result = databaseOperate.queryOne(sql, Integer.class);
        if (result == null) {
            throw new IllegalArgumentException("configInfoCount error");
        }
        return result;
    }

    /**
     * 返回beta配置项个数
     */
    public int configInfoBetaCount() {
        String sql = " SELECT COUNT(ID) FROM config_info_beta ";
        Integer result = databaseOperate.queryOne(sql, Integer.class);
        if (result == null) {
            throw new IllegalArgumentException("configInfoBetaCount error");
        }
        return result;
    }

    /**
     * 返回beta配置项个数
     */
    public int configInfoTagCount() {
        String sql = " SELECT COUNT(ID) FROM config_info_tag ";
        Integer result = databaseOperate.queryOne(sql, Integer.class);
        if (result == null) {
            throw new IllegalArgumentException("configInfoBetaCount error");
        }
        return result;
    }

    public List<String> getTenantIdList(int page, int pageSize) {
        String sql = "SELECT tenant_id FROM config_info WHERE tenant_id != '' GROUP BY tenant_id LIMIT ?, ?";
        int from = (page - 1) * pageSize;
        return databaseOperate.queryMany(sql, new Object[]{from, pageSize}, String.class);
    }

    public List<String> getGroupIdList(int page, int pageSize) {
        String sql = "SELECT group_id FROM config_info WHERE tenant_id ='' GROUP BY group_id LIMIT ?, ?";
        int from = (page - 1) * pageSize;
        return databaseOperate.queryMany(sql, new Object[]{from, pageSize}, String.class);
    }

    public int aggrConfigInfoCount(String dataId, String group, String tenant) {
        String tenantTmp = StringUtils.isBlank(tenant) ? StringUtils.EMPTY : tenant;
        String sql = " SELECT COUNT(ID) FROM config_info_aggr WHERE data_id = ? AND group_id = ? AND tenant_id = ?";
        Integer result = databaseOperate.queryOne(sql, new Object[]{dataId, group, tenantTmp}, Integer.class);
        if (result == null) {
            throw new IllegalArgumentException("aggrConfigInfoCount error");
        }
        return result;
    }

    public int aggrConfigInfoCountIn(String dataId, String group, String tenant, List<String> datumIds) {
        return aggrConfigInfoCount(dataId, group, tenant, datumIds, true);
    }

    public int aggrConfigInfoCountNotIn(String dataId, String group, String tenant, List<String> datumIds) {
        return aggrConfigInfoCount(dataId, group, tenant, datumIds, false);
    }

    private int aggrConfigInfoCount(String dataId, String group, String tenant, List<String> datumIds,
                                    boolean isIn) {
        if (datumIds == null || datumIds.isEmpty()) {
            return 0;
        }
        String tenantTmp = StringUtils.isBlank(tenant) ? StringUtils.EMPTY : tenant;
        StringBuilder sql = new StringBuilder(
                " SELECT COUNT(*) FROM config_info_aggr WHERE data_id = ? and group_id = ? and tenant_id = ? and "
                        + "datum_id");
        if (isIn) {
            sql.append(" in (");
        } else {
            sql.append(" not in (");
        }
        for (int i = 0, size = datumIds.size(); i < size; i++) {
            if (i > 0) {
                sql.append(", ");
            }
            sql.append("?");
        }
        sql.append(")");

        List<Object> objectList = Lists.<Object>newArrayList(dataId, group, tenantTmp);
        objectList.addAll(datumIds);
        Integer result = databaseOperate.queryOne(sql.toString(), objectList.toArray(), Integer.class);
        if (result == null) {
            throw new IllegalArgumentException("aggrConfigInfoCount error");
        }
        return result;
    }

    /**
     * 分页查询所有的配置信息
     *
     * @param pageNo   页码(从1开始)
     * @param pageSize 每页大小(必须大于0)
     * @return ConfigInfo对象的集合
     */
    public Page<ConfigInfo> findAllConfigInfo(final int pageNo, final int pageSize, final String tenant) {
        String tenantTmp = StringUtils.isBlank(tenant) ? StringUtils.EMPTY : tenant;
        String sqlCountRows = "SELECT COUNT(*) FROM config_info";
        String sqlFetchRows = " SELECT t.id,data_id,group_id,tenant_id,app_name,content,md5 "
                + " FROM (                               "
                + "   SELECT id FROM config_info         "
                + "   WHERE tenant_id like ?                  "
                + "   ORDER BY id LIMIT ?,?             "
                + " ) g, config_info t                   "
                + " WHERE g.id = t.id                    ";

        PaginationHelper<ConfigInfo> helper = new PaginationHelper<ConfigInfo>();
        return helper.fetchPageLimit(databaseOperate, sqlCountRows, sqlFetchRows,
                new Object[]{generateLikeArgument(tenantTmp), (pageNo - 1) * pageSize, pageSize},
                pageNo, pageSize, CONFIG_INFO_ROW_MAPPER);

    }

    /**
     * 分页查询所有的配置信息
     *
     * @param pageNo   页码(从1开始)
     * @param pageSize 每页大小(必须大于0)
     * @return ConfigInfo对象的集合
     */
    public Page<ConfigKey> findAllConfigKey(final int pageNo, final int pageSize, final String tenant) {
        String tenantTmp = StringUtils.isBlank(tenant) ? StringUtils.EMPTY : tenant;
        String select = " SELECT data_id,group_id,app_name "
                + " FROM (                               "
                + "   SELECT id FROM config_info         "
                + "   WHERE tenant_id LIKE ?                  "
                + "   ORDER BY id LIMIT ?, ?             "
                + " ) g, config_info t                   "
                + " WHERE g.id = t.id                    ";

        final int totalCount = configInfoCount(tenant);
        int pageCount = totalCount / pageSize;
        if (totalCount > pageSize * pageCount) {
            pageCount++;
        }

        if (pageNo > pageCount) {
            return null;
        }

        final Page<ConfigKey> page = new Page<ConfigKey>();
        page.setPageNumber(pageNo);
        page.setPagesAvailable(pageCount);
        page.setTotalCount(totalCount);

        List<ConfigKey> result = databaseOperate.queryMany(select,
                new Object[]{generateLikeArgument(tenantTmp), (pageNo - 1) * pageSize, pageSize},
                // new Object[0],
                CONFIG_KEY_ROW_MAPPER);

        for (ConfigKey item : result) {
            page.getPageItems().add(item);
        }
        return page;

    }

    /**
     * 分页查询所有的配置信息
     *
     * @param pageNo   页码(从1开始)
     * @param pageSize 每页大小(必须大于0)
     * @return ConfigInfo对象的集合
     */
    @Deprecated
    public Page<ConfigInfoBase> findAllConfigInfoBase(final int pageNo,
                                                      final int pageSize) {
        String sqlCountRows = "SELECT COUNT(*) FROM config_info";
        String sqlFetchRows = " SELECT t.id,data_id,group_id,content,md5 "
                + " FROM (                               "
                + "   SELECT id FROM config_info         "
                + "   ORDER BY id LIMIT ?,?             "
                + " ) g, config_info t                   "
                + " WHERE g.id = t.id                    ";

        PaginationHelper<ConfigInfoBase> helper = new PaginationHelper<ConfigInfoBase>();
        return helper.fetchPageLimit(databaseOperate, sqlCountRows, sqlFetchRows, new Object[]{
                (pageNo - 1) * pageSize, pageSize}, pageNo, pageSize, CONFIG_INFO_BASE_ROW_MAPPER);

    }

    public Page<ConfigInfoWrapper> findAllConfigInfoForDumpAll(
            final int pageNo, final int pageSize) {
        String sqlCountRows = "select count(*) from config_info";
        String sqlFetchRows = " SELECT t.id,data_id,group_id,tenant_id,app_name,content,md5,gmt_modified "
                + " FROM (                               "
                + "   SELECT id FROM config_info         "
                + "   ORDER BY id LIMIT ?,?             "
                + " ) g, config_info t                   "
                + " WHERE g.id = t.id                    ";
        PaginationHelper<ConfigInfoWrapper> helper = new PaginationHelper<ConfigInfoWrapper>();

        return helper.fetchPageLimit(databaseOperate, sqlCountRows, sqlFetchRows, EMPTY_ARRAY, pageNo, pageSize,
                CONFIG_INFO_WRAPPER_ROW_MAPPER);

    }

    public Page<ConfigInfoWrapper> findAllConfigInfoFragment(final long lastMaxId, final int pageSize) {
        String select
                = "SELECT id,data_id,group_id,tenant_id,app_name,content,md5,gmt_modified,type from config_info where id > ? "
                + "order by id asc limit ?,?";
        PaginationHelper<ConfigInfoWrapper> helper = new PaginationHelper<ConfigInfoWrapper>();
        return helper.fetchPageLimit(databaseOperate, select, new Object[]{lastMaxId, 0, pageSize}, 1, pageSize,
                CONFIG_INFO_WRAPPER_ROW_MAPPER);

    }

    public Page<ConfigInfoBetaWrapper> findAllConfigInfoBetaForDumpAll(
            final int pageNo, final int pageSize) {
        String sqlCountRows = "SELECT COUNT(*) FROM config_info_beta";
        String sqlFetchRows = " SELECT t.id,data_id,group_id,tenant_id,app_name,content,md5,gmt_modified,beta_ips "
                + " FROM (                               "
                + "   SELECT id FROM config_info_beta         "
                + "   ORDER BY id LIMIT ?,?             "
                + " ) g, config_info_beta t                   "
                + " WHERE g.id = t.id                    ";
        PaginationHelper<ConfigInfoBetaWrapper> helper = new PaginationHelper<ConfigInfoBetaWrapper>();
        return helper.fetchPageLimit(databaseOperate, sqlCountRows, sqlFetchRows, new Object[]{
                (pageNo - 1) * pageSize, pageSize}, pageNo, pageSize, CONFIG_INFO_BETA_WRAPPER_ROW_MAPPER);

    }

    public Page<ConfigInfoTagWrapper> findAllConfigInfoTagForDumpAll(
            final int pageNo, final int pageSize) {
        String sqlCountRows = "SELECT COUNT(*) FROM config_info_tag";
        String sqlFetchRows = " SELECT t.id,data_id,group_id,tenant_id,tag_id,app_name,content,md5,gmt_modified "
                + " FROM (                               "
                + "   SELECT id FROM config_info_tag         "
                + "   ORDER BY id LIMIT ?,?             "
                + " ) g, config_info_tag t                   "
                + " WHERE g.id = t.id                    ";
        PaginationHelper<ConfigInfoTagWrapper> helper = new PaginationHelper<ConfigInfoTagWrapper>();
        return helper.fetchPageLimit(databaseOperate, sqlCountRows, sqlFetchRows, new Object[]{
                (pageNo - 1) * pageSize, pageSize}, pageNo, pageSize, CONFIG_INFO_TAG_WRAPPER_ROW_MAPPER);

    }

    /**
     * 通过select in方式实现db记录的批量查询； subQueryLimit指定in中条件的个数，上限20
     */
    public List<ConfigInfo> findConfigInfoByBatch(final List<String> dataIds,
                                                  final String group, final String tenant, int subQueryLimit) {
        // assert dataids group not null
        String tenantTmp = StringUtils.isBlank(tenant) ? StringUtils.EMPTY : tenant;
        // if dataids empty return empty list
        if (CollectionUtils.isEmpty(dataIds)) {
            return Collections.emptyList();
        }

        // 批量查询上限
        // in 个数控制在100内, sql语句长度越短越好
        if (subQueryLimit > QUERY_LIMIT_SIZE) {
            subQueryLimit = 50;
        }
        List<ConfigInfo> result = new ArrayList<ConfigInfo>(dataIds.size());

        String sqlStart
                = "select data_id, group_id, tenant_id, app_name, content from config_info where group_id = ? and "
                + "tenant_id = ? and data_id in (";
        String sqlEnd = ")";
        StringBuilder subQuerySql = new StringBuilder();

        for (int i = 0; i < dataIds.size(); i += subQueryLimit) {
            // dataids
            List<String> params = new ArrayList<String>(dataIds.subList(i, Math.min(i
                    + subQueryLimit, dataIds.size())));

            for (int j = 0; j < params.size(); j++) {
                subQuerySql.append("?");
                if (j != params.size() - 1) {
                    subQuerySql.append(",");
                }
            }

            // group
            params.add(0, group);
            params.add(1, tenantTmp);

            final String sql = sqlStart + subQuerySql.toString() + sqlEnd;

            List<ConfigInfo> r = databaseOperate.queryMany(sql,
                    params.toArray(), CONFIG_INFO_ROW_MAPPER);

            // assert not null
            if (r != null && r.size() > 0) {
                result.addAll(r);
            }
        }
        return result;
    }

    /**
     * 根据dataId和group模糊查询配置信息
     *
     * @param pageNo   页码(必须大于0)
     * @param pageSize 每页大小(必须大于0)
     * @param dataId   支持模糊查询
     * @param group    支持模糊查询
     * @param tenant   支持模糊查询
     * @return ConfigInfo对象的集合
     */
    public Page<ConfigInfo> findConfigInfoLike(final int pageNo, final int pageSize, final String dataId,
                                               final String group, final String tenant, final String appName,
                                               final String content) {
        String tenantTmp = StringUtils.isBlank(tenant) ? StringUtils.EMPTY : tenant;
        if (StringUtils.isBlank(dataId) && StringUtils.isBlank(group)) {
            if (StringUtils.isBlank(appName)) {
                return this.findAllConfigInfo(pageNo, pageSize, tenantTmp);
            } else {
                return this.findConfigInfoByApp(pageNo, pageSize, tenantTmp, appName);
            }
        }

        PaginationHelper<ConfigInfo> helper = new PaginationHelper<ConfigInfo>();

        String sqlCountRows = "select count(*) from config_info where ";
        String sqlFetchRows = "select ID,data_id,group_id,tenant_id,app_name,content from config_info where ";
        String where = " 1=1 ";
        List<String> params = new ArrayList<String>();

        if (!StringUtils.isBlank(dataId)) {
            where += " and data_id like ? ";
            params.add(generateLikeArgument(dataId));
        }
        if (!StringUtils.isBlank(group)) {
            where += " and group_id like ? ";
            params.add(generateLikeArgument(group));
        }

        where += " and tenant_id like ? ";
        params.add(generateLikeArgument(tenantTmp));

        if (!StringUtils.isBlank(appName)) {
            where += " and app_name = ? ";
            params.add(appName);
        }
        if (!StringUtils.isBlank(content)) {
            where += " and content like ? ";
            params.add(generateLikeArgument(content));
        }

        return helper.fetchPage(databaseOperate, sqlCountRows + where, sqlFetchRows
                        + where, params.toArray(), pageNo, pageSize,
                CONFIG_INFO_ROW_MAPPER);

    }

    public Page<ConfigInfo> findConfigInfoLike4Page(final int pageNo, final int pageSize, final String dataId,
                                                    final String group, final String tenant,
                                                    final Map<String, Object> configAdvanceInfo) {
        String tenantTmp = StringUtils.isBlank(tenant) ? StringUtils.EMPTY : tenant;
        final String appName = configAdvanceInfo == null ? null : (String) configAdvanceInfo.get("appName");
        final String content = configAdvanceInfo == null ? null : (String) configAdvanceInfo.get("content");
        final String configTags = configAdvanceInfo == null ? null : (String) configAdvanceInfo.get("config_tags");
        PaginationHelper<ConfigInfo> helper = new PaginationHelper<ConfigInfo>();
        String sqlCountRows = "select count(*) from config_info";
        String sqlFetchRows = "select ID,data_id,group_id,tenant_id,app_name,content from config_info";
        StringBuilder where = new StringBuilder(" where ");
        List<String> params = new ArrayList<String>();
        params.add(generateLikeArgument(tenantTmp));
        if (StringUtils.isNotBlank(configTags)) {
            sqlCountRows = "select count(*) from config_info  a left join config_tags_relation b on a.id=b.id ";
            sqlFetchRows
                    = "select a.ID,a.data_id,a.group_id,a.tenant_id,a.app_name,a.content from config_info  a left join "
                    + "config_tags_relation b on a.id=b.id ";

            where.append(" a.tenant_id like ? ");
            if (!StringUtils.isBlank(dataId)) {
                where.append(" and a.data_id like ? ");
                params.add(generateLikeArgument(dataId));
            }
            if (!StringUtils.isBlank(group)) {
                where.append(" and a.group_id like ? ");
                params.add(generateLikeArgument(group));
            }
            if (!StringUtils.isBlank(appName)) {
                where.append(" and a.app_name = ? ");
                params.add(appName);
            }
            if (!StringUtils.isBlank(content)) {
                where.append(" and a.content like ? ");
                params.add(generateLikeArgument(content));
            }

            where.append(" and b.tag_name in (");
            String[] tagArr = configTags.split(",");
            for (int i = 0; i < tagArr.length; i++) {
                if (i != 0) {
                    where.append(", ");
                }
                where.append("?");
                params.add(tagArr[i]);
            }
            where.append(") ");
        } else {
            where.append(" tenant_id like ? ");
            if (!StringUtils.isBlank(dataId)) {
                where.append(" and data_id like ? ");
                params.add(generateLikeArgument(dataId));
            }
            if (!StringUtils.isBlank(group)) {
                where.append(" and group_id like ? ");
                params.add(generateLikeArgument(group));
            }
            if (!StringUtils.isBlank(appName)) {
                where.append(" and app_name = ? ");
                params.add(appName);
            }
            if (!StringUtils.isBlank(content)) {
                where.append(" and content like ? ");
                params.add(generateLikeArgument(content));
            }
        }

        return helper.fetchPage(databaseOperate, sqlCountRows + where, sqlFetchRows
                        + where, params.toArray(), pageNo, pageSize,
                CONFIG_INFO_ROW_MAPPER);

    }

    /**
     * 根据dataId和group模糊查询配置信息
     *
     * @param pageNo     页码(必须大于0)
     * @param pageSize   每页大小(必须大于0)
     * @param configKeys 查询配置列表
     * @param blacklist  是否黑名单
     * @return ConfigInfo对象的集合
     */
    public Page<ConfigInfo> findConfigInfoLike(final int pageNo,
                                               final int pageSize, final ConfigKey[] configKeys,
                                               final boolean blacklist) {
        String sqlCountRows = "select count(*) from config_info where ";
        String sqlFetchRows = "select ID,data_id,group_id,tenant_id,app_name,content from config_info where ";
        String where = " 1=1 ";
        // 白名单，请同步条件为空，则没有符合条件的配置
        if (configKeys.length == 0 && !blacklist) {
            Page<ConfigInfo> page = new Page<ConfigInfo>();
            page.setTotalCount(0);
            return page;
        }
        PaginationHelper<ConfigInfo> helper = new PaginationHelper<ConfigInfo>();
        List<String> params = new ArrayList<String>();
        boolean isFirst = true;
        for (ConfigKey configInfo : configKeys) {
            String dataId = configInfo.getDataId();
            String group = configInfo.getGroup();
            String appName = configInfo.getAppName();

            if (StringUtils.isBlank(dataId)
                    && StringUtils.isBlank(group)
                    && StringUtils.isBlank(appName)) {
                break;
            }

            if (blacklist) {
                if (isFirst) {
                    isFirst = false;
                    where += " and ";
                } else {
                    where += " and ";
                }

                where += "(";
                boolean isFirstSub = true;
                if (!StringUtils.isBlank(dataId)) {
                    where += " data_id not like ? ";
                    params.add(generateLikeArgument(dataId));
                    isFirstSub = false;
                }
                if (!StringUtils.isBlank(group)) {
                    if (!isFirstSub) {
                        where += " or ";
                    }
                    where += " group_id not like ? ";
                    params.add(generateLikeArgument(group));
                    isFirstSub = false;
                }
                if (!StringUtils.isBlank(appName)) {
                    if (!isFirstSub) {
                        where += " or ";
                    }
                    where += " app_name != ? ";
                    params.add(appName);
                    isFirstSub = false;
                }
                where += ") ";
            } else {
                if (isFirst) {
                    isFirst = false;
                    where += " and ";
                } else {
                    where += " or ";
                }
                where += "(";
                boolean isFirstSub = true;
                if (!StringUtils.isBlank(dataId)) {
                    where += " data_id like ? ";
                    params.add(generateLikeArgument(dataId));
                    isFirstSub = false;
                }
                if (!StringUtils.isBlank(group)) {
                    if (!isFirstSub) {
                        where += " and ";
                    }
                    where += " group_id like ? ";
                    params.add(generateLikeArgument(group));
                    isFirstSub = false;
                }
                if (!StringUtils.isBlank(appName)) {
                    if (!isFirstSub) {
                        where += " and ";
                    }
                    where += " app_name = ? ";
                    params.add(appName);
                    isFirstSub = false;
                }
                where += ") ";
            }
        }

        return helper.fetchPage(databaseOperate, sqlCountRows + where, sqlFetchRows
                        + where, params.toArray(), pageNo, pageSize,
                CONFIG_INFO_ROW_MAPPER);

    }

    /**
     * 根据dataId和group模糊查询配置信息
     *
     * @param pageNo   页码(必须大于0)
     * @param pageSize 每页大小(必须大于0)
     * @param dataId
     * @param group
     * @return ConfigInfo对象的集合
     * @throws IOException
     */
    public Page<ConfigInfoBase> findConfigInfoBaseLike(final int pageNo,
                                                       final int pageSize, final String dataId, final String group,
                                                       final String content) throws IOException {
        if (StringUtils.isBlank(dataId) && StringUtils.isBlank(group)) {
            throw new IOException("invalid param");
        }

        PaginationHelper<ConfigInfoBase> helper = new PaginationHelper<ConfigInfoBase>();

        String sqlCountRows = "select count(*) from config_info where ";
        String sqlFetchRows = "select ID,data_id,group_id,tenant_id,content from config_info where ";
        String where = " 1=1 and tenant_id='' ";
        List<String> params = new ArrayList<String>();

        if (!StringUtils.isBlank(dataId)) {
            where += " and data_id like ? ";
            params.add(generateLikeArgument(dataId));
        }
        if (!StringUtils.isBlank(group)) {
            where += " and group_id like ? ";
            params.add(generateLikeArgument(group));
        }
        if (!StringUtils.isBlank(content)) {
            where += " and content like ? ";
            params.add(generateLikeArgument(content));
        }

        return helper.fetchPage(databaseOperate, sqlCountRows + where, sqlFetchRows
                        + where, params.toArray(), pageNo, pageSize,
                CONFIG_INFO_BASE_ROW_MAPPER);

    }

    /**
     * 查找聚合前的单条数据
     *
     * @param dataId
     * @param group
     * @param datumId
     * @return
     */
    public ConfigInfoAggr findSingleConfigInfoAggr(String dataId, String group, String tenant, String datumId) {
        String tenantTmp = StringUtils.isBlank(tenant) ? StringUtils.EMPTY : tenant;
        String sql
                = "SELECT id,data_id,group_id,tenant_id,datum_id,app_name,content FROM config_info_aggr WHERE data_id=? "
                + "AND group_id=? AND tenant_id=? AND datum_id=?";

        return databaseOperate.queryOne(sql, new Object[]{dataId, group, tenantTmp, datumId},
                CONFIG_INFO_AGGR_ROW_MAPPER);

    }

    /**
     * 查找一个dataId下面的所有聚合前的数据. 保证不返回NULL.
     */
    public List<ConfigInfoAggr> findConfigInfoAggr(String dataId, String group, String tenant) {
        String tenantTmp = StringUtils.isBlank(tenant) ? StringUtils.EMPTY : tenant;
        String sql
                = "SELECT data_id,group_id,tenant_id,datum_id,app_name,content FROM config_info_aggr WHERE data_id=? AND "
                + "group_id=? AND tenant_id=? ORDER BY datum_id";

        return databaseOperate.queryMany(sql, new Object[]{dataId, group, tenantTmp},
                CONFIG_INFO_AGGR_ROW_MAPPER);

    }

    public Page<ConfigInfoAggr> findConfigInfoAggrByPage(String dataId, String group, String tenant, final int pageNo,
                                                         final int pageSize) {
        String tenantTmp = StringUtils.isBlank(tenant) ? StringUtils.EMPTY : tenant;
        String sqlCountRows
                = "SELECT COUNT(*) FROM config_info_aggr WHERE data_id = ? and group_id = ? and tenant_id = ?";
        String sqlFetchRows
                = "select data_id,group_id,tenant_id,datum_id,app_name,content from config_info_aggr where data_id=? and "
                + "group_id=? and tenant_id=? order by datum_id limit ?,?";
        PaginationHelper<ConfigInfoAggr> helper = new PaginationHelper<ConfigInfoAggr>();
        return helper.fetchPageLimit(databaseOperate, sqlCountRows, new Object[]{dataId, group, tenantTmp}, sqlFetchRows,
                new Object[]{dataId, group, tenantTmp, (pageNo - 1) * pageSize, pageSize},
                pageNo, pageSize, CONFIG_INFO_AGGR_ROW_MAPPER);

    }

    /**
     * 查询符合条件的聚合数据
     *
     * @param pageNo     pageNo
     * @param pageSize   pageSize
     * @param configKeys 聚合数据条件
     * @param blacklist  黑名单
     * @return
     */
    public Page<ConfigInfoAggr> findConfigInfoAggrLike(final int pageNo, final int pageSize, ConfigKey[] configKeys,
                                                       boolean blacklist) {

        String sqlCountRows = "select count(*) from config_info_aggr where ";
        String sqlFetchRows
                = "select data_id,group_id,tenant_id,datum_id,app_name,content from config_info_aggr where ";
        String where = " 1=1 ";
        // 白名单，请同步条件为空，则没有符合条件的配置
        if (configKeys.length == 0 && blacklist == false) {
            Page<ConfigInfoAggr> page = new Page<ConfigInfoAggr>();
            page.setTotalCount(0);
            return page;
        }
        PaginationHelper<ConfigInfoAggr> helper = new PaginationHelper<ConfigInfoAggr>();
        List<String> params = new ArrayList<String>();
        boolean isFirst = true;

        for (ConfigKey configInfoAggr : configKeys) {
            String dataId = configInfoAggr.getDataId();
            String group = configInfoAggr.getGroup();
            String appName = configInfoAggr.getAppName();
            if (StringUtils.isBlank(dataId)
                    && StringUtils.isBlank(group)
                    && StringUtils.isBlank(appName)) {
                break;
            }
            if (blacklist) {
                if (isFirst) {
                    isFirst = false;
                    where += " and ";
                } else {
                    where += " and ";
                }

                where += "(";
                boolean isFirstSub = true;
                if (!StringUtils.isBlank(dataId)) {
                    where += " data_id not like ? ";
                    params.add(generateLikeArgument(dataId));
                    isFirstSub = false;
                }
                if (!StringUtils.isBlank(group)) {
                    if (!isFirstSub) {
                        where += " or ";
                    }
                    where += " group_id not like ? ";
                    params.add(generateLikeArgument(group));
                    isFirstSub = false;
                }
                if (!StringUtils.isBlank(appName)) {
                    if (!isFirstSub) {
                        where += " or ";
                    }
                    where += " app_name != ? ";
                    params.add(appName);
                    isFirstSub = false;
                }
                where += ") ";
            } else {
                if (isFirst) {
                    isFirst = false;
                    where += " and ";
                } else {
                    where += " or ";
                }
                where += "(";
                boolean isFirstSub = true;
                if (!StringUtils.isBlank(dataId)) {
                    where += " data_id like ? ";
                    params.add(generateLikeArgument(dataId));
                    isFirstSub = false;
                }
                if (!StringUtils.isBlank(group)) {
                    if (!isFirstSub) {
                        where += " and ";
                    }
                    where += " group_id like ? ";
                    params.add(generateLikeArgument(group));
                    isFirstSub = false;
                }
                if (!StringUtils.isBlank(appName)) {
                    if (!isFirstSub) {
                        where += " and ";
                    }
                    where += " app_name = ? ";
                    params.add(appName);
                    isFirstSub = false;
                }
                where += ") ";
            }
        }

        Page<ConfigInfoAggr> result = helper.fetchPage(databaseOperate, sqlCountRows
                        + where, sqlFetchRows + where, params.toArray(), pageNo,
                pageSize, CONFIG_INFO_AGGR_ROW_MAPPER);
        return result;

    }

    /**
     * 找到所有聚合数据组。
     */
    public List<ConfigInfoChanged> findAllAggrGroup() {
        String sql = "SELECT DISTINCT data_id, group_id, tenant_id FROM config_info_aggr";

        return databaseOperate.queryMany(sql, EMPTY_ARRAY,
                CONFIG_INFO_CHANGED_ROW_MAPPER);

    }

    /**
     * 由datum内容查找datumId
     *
     * @param dataId  data id
     * @param groupId group
     * @param content content
     * @return datum keys
     */
    public List<String> findDatumIdByContent(String dataId, String groupId,
                                             String content) {
        String sql = "SELECT datum_id FROM config_info_aggr WHERE data_id = ? AND group_id = ? AND content = ? ";

        return databaseOperate.queryMany(sql, new Object[]{dataId, groupId,
                content}, String.class);

    }

    public List<ConfigInfoWrapper> findChangeConfig(final Timestamp startTime,
                                                    final Timestamp endTime) {
        List<Map<String, Object>> list = databaseOperate
                .queryMany(
                        "SELECT data_id, group_id, tenant_id, app_name, content, gmt_modified FROM config_info WHERE "
                                + "gmt_modified >=? AND gmt_modified <= ?",
                        new Object[]{startTime, endTime});
        return convertChangeConfig(list);

    }

    /**
     * 根据时间段和配置条件查询符合条件的配置
     *
     * @param dataId    dataId 支持模糊
     * @param group     dataId 支持模糊
     * @param appName   产品名
     * @param startTime 起始时间
     * @param endTime   截止时间
     * @param pageNo    pageNo
     * @param pageSize  pageSize
     * @return
     */
    public Page<ConfigInfoWrapper> findChangeConfig(final String dataId, final String group, final String tenant,
                                                    final String appName, final Timestamp startTime,
                                                    final Timestamp endTime, final int pageNo,
                                                    final int pageSize, final long lastMaxId) {
        String tenantTmp = StringUtils.isBlank(tenant) ? StringUtils.EMPTY : tenant;
        String sqlCountRows = "select count(*) from config_info where ";
        String sqlFetchRows
                = "select id,data_id,group_id,tenant_id,app_name,content,md5,gmt_modified from config_info where ";
        String where = " 1=1 ";
        List<Object> params = new ArrayList<Object>();

        if (!StringUtils.isBlank(dataId)) {
            where += " and data_id like ? ";
            params.add(generateLikeArgument(dataId));
        }
        if (!StringUtils.isBlank(group)) {
            where += " and group_id like ? ";
            params.add(generateLikeArgument(group));
        }

        if (!StringUtils.isBlank(tenantTmp)) {
            where += " and tenant_id = ? ";
            params.add(tenantTmp);
        }

        if (!StringUtils.isBlank(appName)) {
            where += " and app_name = ? ";
            params.add(appName);
        }
        if (startTime != null) {
            where += " and gmt_modified >=? ";
            params.add(startTime);
        }
        if (endTime != null) {
            where += " and gmt_modified <=? ";
            params.add(endTime);
        }

        PaginationHelper<ConfigInfoWrapper> helper = new PaginationHelper<ConfigInfoWrapper>();
        return helper.fetchPage(databaseOperate, sqlCountRows + where, sqlFetchRows + where, params.toArray(), pageNo, pageSize,
                lastMaxId, CONFIG_INFO_WRAPPER_ROW_MAPPER);

    }

    public List<ConfigInfo> findDeletedConfig(final Timestamp startTime,
                                              final Timestamp endTime) {
        List<Map<String, Object>> list = databaseOperate
                .queryMany(
                        "SELECT DISTINCT data_id, group_id, tenant_id FROM his_config_info WHERE op_type = 'D' AND "
                                + "gmt_modified >=? AND gmt_modified <= ?",
                        new Object[]{startTime, endTime});
        return convertDeletedConfig(list);

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
    public long addConfigInfoAtomic(final Long id, final String srcIp, final String srcUser, final ConfigInfo configInfo,
                                    final Timestamp time,
                                    Map<String, Object> configAdvanceInfo) {
        final String appNameTmp = StringUtils.isBlank(configInfo.getAppName()) ? StringUtils.EMPTY
                : configInfo.getAppName();
        final String tenantTmp = StringUtils.isBlank(configInfo.getTenant()) ? StringUtils.EMPTY
                : configInfo.getTenant();

        final String desc = configAdvanceInfo == null ? null : (String) configAdvanceInfo.get("desc");
        final String use = configAdvanceInfo == null ? null : (String) configAdvanceInfo.get("use");
        final String effect = configAdvanceInfo == null ? null : (String) configAdvanceInfo.get("effect");
        final String type = configAdvanceInfo == null ? null : (String) configAdvanceInfo.get("type");
        final String schema = configAdvanceInfo == null ? null : (String) configAdvanceInfo.get("schema");
        final String md5Tmp = MD5.getInstance().getMD5String(configInfo.getContent());

        final String sql;

        final Object[] args;

        if (Objects.isNull(id)) {
            sql = "INSERT INTO config_info(data_id, group_id, tenant_id, app_name, content, md5, src_ip, src_user, gmt_create,"
                    + "gmt_modified, c_desc, c_use, effect, type, c_schema) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)";

            args = new Object[]{
                    configInfo.getDataId(), configInfo.getGroup(), tenantTmp, appNameTmp,
                    configInfo.getContent(), md5Tmp, srcIp, srcUser,
                    time, time, desc, use,
                    effect, type, schema,
            };

        } else {
            sql = "INSERT INTO config_info(id, data_id, group_id, tenant_id, app_name, content, md5, src_ip, src_user, gmt_create,"
                    + "gmt_modified, c_desc, c_use, effect, type, c_schema) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)";

            args = new Object[]{
                    id, configInfo.getDataId(), configInfo.getGroup(), tenantTmp,
                    appNameTmp, configInfo.getContent(), md5Tmp, srcIp,
                    srcUser, time, time, desc,
                    use, effect, type, schema,
            };
        }

        SqlContextUtils.addSqlContext(sql, args);
        return id;
    }

    /**
     * // TODO 是否可以改为 insert values
     * <p>
     * 增加配置；数据库原子操作，最小sql动作，无业务封装
     *
     * @param configId id
     * @param tagName  tag
     * @param dataId   data id
     * @param group    group
     * @param tenant   tenant
     */
    public void addConfigTagRelationAtomic(Long configId, String tagName, String dataId, String group, String tenant) {
        final String sql = "INSERT INTO config_tags_relation(id,tag_name,tag_type,data_id,group_id,tenant_id) " +
                "VALUES(((select id from config_info where data_id=?' and group_id =? and tenant_id=?),?,?,?,?,?),?,?,?,?,?)";
        final Object[] args = new Object[]{dataId, group, tenant, tagName, null, dataId, group, tenant};
        SqlContextUtils.addSqlContext(sql, args);
    }

    /**
     * 增加配置；数据库原子操作
     *
     * @param configId   config id
     * @param configTags tags
     * @param dataId     dataId
     * @param group      group
     * @param tenant     tenant
     */
    public void addConfigTagsRelation(Long configId, String configTags, String dataId, String group,
                                      String tenant) {
        if (StringUtils.isNotBlank(configTags)) {
            String[] tagArr = configTags.split(",");
            for (int i = 0; i < tagArr.length; i++) {
                addConfigTagRelationAtomic(configId, tagArr[i], dataId, group, tenant);
            }
        }
    }

    public void removeTagByIdAtomic(long id) {
        final String sql = "DELETE FROM config_tags_relation WHERE id=?";
        final Object[] args = new Object[]{id};
        SqlContextUtils.addSqlContext(sql, args);
    }

    public List<String> getConfigTagsByTenant(String tenant) {
        String sql = "SELECT tag_name FROM config_tags_relation WHERE tenant_id = ? ";
        return databaseOperate.queryMany(sql, new Object[]{tenant}, String.class);
    }

    public List<String> selectTagByConfig(String dataId, String group, String tenant) {
        String sql = "SELECT tag_name FROM config_tags_relation WHERE data_id=? AND group_id=? AND tenant_id = ? ";
        return databaseOperate.queryMany(sql, new Object[]{dataId, group, tenant}, String.class);
    }

    /**
     * 删除配置；数据库原子操作，最小sql动作，无业务封装
     *
     * @param dataId  dataId
     * @param group   group
     * @param tenant  tenant
     * @param srcIp   ip
     * @param srcUser user
     */
    private void removeConfigInfoAtomic(final String dataId, final String group, final String tenant,
                                        final String srcIp,
                                        final String srcUser) {
        String tenantTmp = StringUtils.isBlank(tenant) ? StringUtils.EMPTY : tenant;

        final String sql = "DELETE FROM config_info WHERE data_id=? AND group_id=? AND tenant_id=?";

        final Object[] args = new Object[]{
                dataId, group, tenantTmp
        };

        SqlContextUtils.addSqlContext(sql, args);
    }

    /**
     * @return void
     * @author klw
     * @Description: Delete configuration; database atomic operation, minimum SQL action, no business encapsulation
     * @Date 2019/7/5 16:39
     * @Param [id]
     */
    private void removeConfigInfoByIdsAtomic(final String ids) {
        if (StringUtils.isBlank(ids)) {
            return;
        }
        StringBuilder sql = new StringBuilder(SQL_DELETE_CONFIG_INFO_BY_IDS);
        sql.append("id in (");
        List<Long> paramList = new ArrayList<>();
        String[] tagArr = ids.split(",");
        for (int i = 0; i < tagArr.length; i++) {
            if (i != 0) {
                sql.append(", ");
            }
            sql.append("?");
            paramList.add(Long.parseLong(tagArr[i]));
        }
        sql.append(") ");
        SqlContextUtils.addSqlContext(sql.toString(), paramList.toArray());
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
        String tenantTmp = StringUtils.isBlank(tenant) ? StringUtils.EMPTY : tenant;
        String tagTmp = StringUtils.isBlank(tag) ? StringUtils.EMPTY : tag;
        try {

            final String sql = "DELETE FROM config_info_tag WHERE data_id=? AND group_id=? AND tenant_id=? AND tag_id=?";
            final Object[] args = new Object[]{
                    dataId, group, tenantTmp, tagTmp
            };

            SqlContextUtils.addSqlContext(sql, args);
            databaseOperate.update(SqlContextUtils.getCurrentSqlContext());
        } finally {
            SqlContextUtils.cleanCurrentSqlContext();
        }
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
        final String appNameTmp = StringUtils.isBlank(configInfo.getAppName()) ? StringUtils.EMPTY : configInfo.getAppName();
        final String tenantTmp = StringUtils.isBlank(configInfo.getTenant()) ? StringUtils.EMPTY : configInfo.getTenant();
        final String md5Tmp = MD5.getInstance().getMD5String(configInfo.getContent());
        final String desc = configAdvanceInfo == null ? null : (String) configAdvanceInfo.get("desc");
        final String use = configAdvanceInfo == null ? null : (String) configAdvanceInfo.get("use");
        final String effect = configAdvanceInfo == null ? null : (String) configAdvanceInfo.get("effect");
        final String type = configAdvanceInfo == null ? null : (String) configAdvanceInfo.get("type");
        final String schema = configAdvanceInfo == null ? null : (String) configAdvanceInfo.get("schema");

        final String sql = "UPDATE config_info SET content=?, md5 = ?, src_ip=?,src_user=?,gmt_modified=?,app_name=?," +
                "c_desc=?,c_use=?,effect=?,type=?,c_schema=? WHERE data_id=? AND group_id=? AND tenant_id=?";

        final Object[] args = new Object[]{
                configInfo.getContent(), md5Tmp, srcIp, srcUser, time, appNameTmp, desc, use, effect, type, schema,
                configInfo.getDataId(), configInfo.getGroup(), tenantTmp
        };

        SqlContextUtils.addSqlContext(sql, args);
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
        final String tenantTmp = StringUtils.isBlank(tenant) ? StringUtils.EMPTY : tenant;

        final String sql = "SELECT ID,data_id,group_id,tenant_id,app_name,content,md5,type FROM config_info WHERE data_id=? AND group_id=? AND tenant_id=?";
        final Object[] args = new Object[]{dataId, group, tenantTmp};
        return databaseOperate.queryOne(sql, args, CONFIG_INFO_ROW_MAPPER);

    }

    /**
     * @return java.util.List<com.alibaba.nacos.config.server.model.ConfigInfo>
     * @author klw
     * @Description: find ConfigInfo by ids
     * @Date 2019/7/5 16:37
     * @Param [ids]
     */
    public List<ConfigInfo> findConfigInfosByIds(final String ids) {
        if (StringUtils.isBlank(ids)) {
            return null;
        }
        StringBuilder sql = new StringBuilder(SQL_FIND_CONFIG_INFO_BY_IDS);
        sql.append("id in (");
        List<Long> paramList = new ArrayList<>();
        String[] tagArr = ids.split(",");
        for (int i = 0; i < tagArr.length; i++) {
            if (i != 0) {
                sql.append(", ");
            }
            sql.append("?");
            paramList.add(Long.parseLong(tagArr[i]));
        }
        sql.append(") ");
        return databaseOperate.queryMany(sql.toString(), paramList.toArray(), CONFIG_INFO_ROW_MAPPER);

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
        final String tenantTmp = StringUtils.isBlank(tenant) ? StringUtils.EMPTY : tenant;
        List<String> configTagList = this.selectTagByConfig(dataId, group, tenant);
        ConfigAdvanceInfo configAdvance = databaseOperate.queryOne(
                "SELECT gmt_create,gmt_modified,src_user,src_ip,c_desc,c_use,effect,type,c_schema FROM config_info WHERE data_id=? AND group_id=? AND tenant_id=?",
                new Object[]{dataId, group, tenantTmp}, CONFIG_ADVANCE_INFO_ROW_MAPPER);
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
     * 查询配置信息；数据库原子操作，最小sql动作，无业务封装
     *
     * @param dataId dataId
     * @param group  group
     * @param tenant tenant
     * @return advance info
     */
    public ConfigAllInfo findConfigAllInfo(final String dataId, final String group, final String tenant) {
        final String tenantTmp = StringUtils.isBlank(tenant) ? StringUtils.EMPTY : tenant;

        final String sql = "SELECT ID,data_id,group_id,tenant_id,app_name,content,md5,gmt_create," +
                "gmt_modified,src_user,src_ip,c_desc,c_use,effect,type,c_schema FROM config_info " +
                "WHERE data_id=? AND group_id=? AND tenant_id=?";

        List<String> configTagList = this.selectTagByConfig(dataId, group, tenant);
        ConfigAllInfo configAdvance = databaseOperate.queryOne(sql,
                new Object[]{dataId, group, tenantTmp}, CONFIG_ALL_INFO_ROW_MAPPER);
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
     * 更新变更记录；数据库原子操作，最小sql动作，无业务封装
     *
     * @param id         id
     * @param configInfo config info
     * @param srcIp      ip
     * @param srcUser    user
     * @param time       time
     * @param ops        ops type
     */
    public void insertConfigHistoryAtomic(Long id, ConfigInfo configInfo, String srcIp, String srcUser,
                                          final Timestamp time, String ops) {
        String appNameTmp = StringUtils.isBlank(configInfo.getAppName()) ? StringUtils.EMPTY : configInfo.getAppName();
        String tenantTmp = StringUtils.isBlank(configInfo.getTenant()) ? StringUtils.EMPTY : configInfo.getTenant();
        final String md5Tmp = MD5.getInstance().getMD5String(configInfo.getContent());

        final String sql;
        final Object[] args;

        if (id == null) {
            sql = "INSERT INTO his_config_info (data_id,group_id,tenant_id,app_name,content,md5," +
                    "src_ip,src_user,gmt_modified,op_type) VALUES(?,?,?,?,?,?,?,?,?,?)";
            args = new Object[]{
                    configInfo.getDataId(), configInfo.getGroup(), tenantTmp, appNameTmp, configInfo.getContent(),
                    md5Tmp, srcIp, srcUser, time, ops
            };
        } else {
            sql = "INSERT INTO his_config_info (id,data_id,group_id,tenant_id,app_name,content,md5," +
                    "src_ip,src_user,gmt_modified,op_type) VALUES(?,?,?,?,?,?,?,?,?,?,?)";
            args = new Object[]{
                    id, configInfo.getDataId(), configInfo.getGroup(), tenantTmp, appNameTmp, configInfo.getContent(),
                    md5Tmp, srcIp, srcUser, time, ops
            };
        }

        SqlContextUtils.addSqlContext(sql, args);
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
    public Page<ConfigHistoryInfo> findConfigHistory(String dataId, String group, String tenant, int pageNo,
                                                     int pageSize) {
        PaginationHelper<ConfigHistoryInfo> helper = new PaginationHelper<ConfigHistoryInfo>();
        String tenantTmp = StringUtils.isBlank(tenant) ? StringUtils.EMPTY : tenant;
        String sqlCountRows
                = "select count(*) from his_config_info where data_id = ? and group_id = ? and tenant_id = ?";
        String sqlFetchRows
                = "select nid,data_id,group_id,tenant_id,app_name,src_ip,op_type,gmt_create,gmt_modified from his_config_info where data_id = ? and group_id = ? and tenant_id = ? order by nid desc";

        Page<ConfigHistoryInfo> page = null;
        page = helper.fetchPage(databaseOperate, sqlCountRows, sqlFetchRows, new Object[]{dataId, group, tenantTmp},
                pageNo,
                pageSize, HISTORY_LIST_ROW_MAPPER);
        return page;
    }

    /**
     * 增加配置；数据库原子操作，最小sql动作，无业务封装
     *
     * @param dataId  dataId
     * @param group   group
     * @param appName appName
     * @param date    date
     */
    private void addConfigSubAtomic(final String dataId, final String group, final String appName,
                                    final Timestamp date) {
        final String appNameTmp = appName == null ? "" : appName;
        try {

            final String sql = "INSERT INTO app_configdata_relation_subs(data_id,group_id,app_name,gmt_modified) VALUES(?,?,?,?)";
            final Object[] args = new Object[]{dataId, group, appNameTmp, date};

            SqlContextUtils.addSqlContext(sql, args);

            databaseOperate.update(SqlContextUtils.getCurrentSqlContext());
        } finally {
            SqlContextUtils.cleanCurrentSqlContext();
        }
    }

    /**
     * 更新配置;数据库原子操作，最小sql动作，无业务封装
     *
     * @param dataId  data Id
     * @param group   group
     * @param appName app name
     * @param time    time
     */
    private void updateConfigSubAtomic(final String dataId, final String group, final String appName,
                                       final Timestamp time) {
        final String appNameTmp = appName == null ? "" : appName;
        try {

            final String sql = "UPDATE app_configdata_relation_subs SET gmt_modified=? WHERE data_id=? AND group_id=? AND app_name=?";

            SqlContextUtils.addSqlContext(sql, time, dataId, group, appNameTmp);

            databaseOperate.update(SqlContextUtils.getCurrentSqlContext());
        } finally {
            SqlContextUtils.cleanCurrentSqlContext();
        }
    }

    public ConfigHistoryInfo detailConfigHistory(Long nid) {
        String sqlFetchRows
                = "SELECT nid,data_id,group_id,tenant_id,app_name,content,md5,src_user,src_ip," +
                "op_type,gmt_create,gmt_modified FROM his_config_info WHERE nid = ?";
        ConfigHistoryInfo historyInfo = databaseOperate.queryOne(sqlFetchRows, new Object[]{nid},
                HISTORY_DETAIL_ROW_MAPPER);
        return historyInfo;
    }

    /**
     * insert tenant info
     *
     * @param kp         kp
     * @param tenantId   tenant Id
     * @param tenantName tenant name
     * @param tenantDesc tenant description
     * @param time       time
     */
    public void insertTenantInfoAtomic(String kp, String tenantId, String tenantName, String tenantDesc,
                                       String createResoure, final long time) {
        try {
            SqlContextUtils.addSqlContext(
                    "INSERT INTO tenant_info(kp,tenant_id,tenant_name,tenant_desc,create_source,gmt_create,gmt_modified) VALUES(?,?,?,?,?,?,?)",
                    kp, tenantId, tenantName, tenantDesc, createResoure, time, time);
            boolean result = databaseOperate.update(SqlContextUtils.getCurrentSqlContext());

            if (!result) {
                throw new NacosConfigException("命名空间创建失败");
            }

        } finally {
            SqlContextUtils.cleanCurrentSqlContext();
        }
    }

    /**
     * Update tenantInfo showname
     *
     * @param kp         kp
     * @param tenantId   tenant Id
     * @param tenantName tenant name
     * @param tenantDesc tenant description
     */
    public void updateTenantNameAtomic(String kp, String tenantId, String tenantName, String tenantDesc) {
        try {
            SqlContextUtils.addSqlContext(
                    "UPDATE tenant_info SET tenant_name = ?, tenant_desc = ?, gmt_modified= ? WHERE kp=? AND tenant_id=?",
                    tenantName, tenantDesc, System.currentTimeMillis(), kp, tenantId);
            boolean result = databaseOperate.update(SqlContextUtils.getCurrentSqlContext());
            if (!result) {
                throw new NacosConfigException("命名空间更新失败");
            }
        } finally {
            SqlContextUtils.cleanCurrentSqlContext();
        }
    }

    public List<TenantInfo> findTenantByKp(String kp) {
        String sql = "SELECT tenant_id,tenant_name,tenant_desc FROM tenant_info WHERE kp=?";
        return databaseOperate.queryMany(sql, new Object[]{kp}, TENANT_INFO_ROW_MAPPER);

    }

    public TenantInfo findTenantByKp(String kp, String tenantId) {
        String sql = "SELECT tenant_id,tenant_name,tenant_desc FROM tenant_info WHERE kp=? AND tenant_id=?";
        return databaseOperate.queryOne(sql, new Object[]{kp, tenantId}, TENANT_INFO_ROW_MAPPER);

    }

    public void removeTenantInfoAtomic(final String kp, final String tenantId) {
        try {
            SqlContextUtils.addSqlContext("DELETE FROM tenant_info WHERE kp=? AND tenant_id=?", kp, tenantId);
            databaseOperate.update(SqlContextUtils.getCurrentSqlContext());
        } finally {
            SqlContextUtils.cleanCurrentSqlContext();
        }
    }

    private List<ConfigInfo> convertDeletedConfig(List<Map<String, Object>> list) {
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

    private List<ConfigInfoWrapper> convertChangeConfig(
            List<Map<String, Object>> list) {
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

    /**
     * 获取所有的配置的Md5值，通过分页方式获取。
     *
     * @return
     */
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

    private List<ConfigInfoWrapper> listGroupKeyMd5ByPage(int pageNo, int pageSize) {
        String sqlCountRows = " SELECT COUNT(*) FROM config_info ";
        String sqlFetchRows
                = " SELECT t.id,data_id,group_id,tenant_id,app_name,md5,gmt_modified FROM ( SELECT id FROM config_info ORDER BY id LIMIT ?,?  ) g, config_info t WHERE g.id = t.id";
        PaginationHelper<ConfigInfoWrapper> helper = new PaginationHelper<ConfigInfoWrapper>();
        Page<ConfigInfoWrapper> page = helper.fetchPageLimit(databaseOperate, sqlCountRows, sqlFetchRows, new Object[]{
                (pageNo - 1) * pageSize, pageSize}, pageNo, pageSize, CONFIG_INFO_WRAPPER_ROW_MAPPER);

        return page.getPageItems();
    }

    private String generateLikeArgument(String s) {
        String fuzzySearchSign = "\\*";
        String sqlLikePercentSign = "%";
        if (s.contains(PATTERN_STR)) {
            return s.replaceAll(fuzzySearchSign, sqlLikePercentSign);
        } else {
            return s;
        }
    }

    public ConfigInfoWrapper queryConfigInfo(final String dataId, final String group, final String tenant) {
        String tenantTmp = StringUtils.isBlank(tenant) ? StringUtils.EMPTY : tenant;
        final String sql = "SELECT ID,data_id,group_id,tenant_id,app_name,content,gmt_modified,md5 FROM " +
                "config_info WHERE data_id=? AND group_id=? AND tenant_id=?";

        return databaseOperate
                .queryOne(sql,
                        new Object[]{dataId, group, tenantTmp}, CONFIG_INFO_WRAPPER_ROW_MAPPER);
    }

    public boolean isExistTable(String tableName) {
        String sql = "SELECT COUNT(*) FROM " + tableName;
        try {
            databaseOperate.queryOne(sql, Integer.class);
            return true;
        } catch (Throwable e) {
            return false;
        }
    }

    public Boolean completeMd5() {
        defaultLog.info("[start completeMd5]");
        int perPageSize = 1000;
        int rowCount = configInfoCount();
        int pageCount = (int) Math.ceil(rowCount * 1.0 / perPageSize);
        int actualRowCount = 0;
        for (int pageNo = 1; pageNo <= pageCount; pageNo++) {
            Page<ConfigInfoWrapper> page = findAllConfigInfoForDumpAll(
                    pageNo, perPageSize);
            if (page != null) {
                for (ConfigInfoWrapper cf : page.getPageItems()) {
                    String md5InDb = cf.getMd5();
                    final String content = cf.getContent();
                    final String tenant = cf.getTenant();
                    final String md5 = MD5.getInstance().getMD5String(
                            content);
                    if (StringUtils.isBlank(md5InDb)) {
                        try {
                            updateMd5(cf.getDataId(), cf.getGroup(), tenant, md5, new Timestamp(cf.getLastModified()));
                        } catch (Exception e) {
                            LogUtil.defaultLog
                                    .error("[completeMd5-error] datId:{} group:{} lastModified:{}",
                                            cf.getDataId(),
                                            cf.getGroup(),
                                            new Timestamp(cf
                                                    .getLastModified()));
                        }
                    } else {
                        if (!md5InDb.equals(md5)) {
                            try {
                                updateMd5(cf.getDataId(), cf.getGroup(), tenant, md5,
                                        new Timestamp(cf.getLastModified()));
                            } catch (Exception e) {
                                LogUtil.defaultLog.error("[completeMd5-error] datId:{} group:{} lastModified:{}",
                                        cf.getDataId(), cf.getGroup(),
                                        new Timestamp(cf.getLastModified()));
                            }
                        }
                    }
                }

                actualRowCount += page.getPageItems().size();
                defaultLog.info("[completeMd5] {} / {}", actualRowCount, rowCount);
            }
        }
        return true;
    }

    /**
     * query all configuration information according to group, appName, tenant (for export)
     *
     * @param group
     * @return Collection of ConfigInfo objects
     */
    public List<ConfigAllInfo> findAllConfigInfo4Export(final String dataId, final String group, final String tenant,
                                                        final String appName, final List<Long> ids) {
        String tenantTmp = StringUtils.isBlank(tenant) ? StringUtils.EMPTY : tenant;
        StringBuilder where = new StringBuilder(" where ");
        List<Object> paramList = new ArrayList<>();
        if (!CollectionUtils.isEmpty(ids)) {
            where.append(" id in (");
            for (int i = 0; i < ids.size(); i++) {
                if (i != 0) {
                    where.append(", ");
                }
                where.append("?");
                paramList.add(ids.get(i));
            }
            where.append(") ");
        } else {
            where.append(" tenant_id=? ");
            paramList.add(tenantTmp);
            if (!StringUtils.isBlank(dataId)) {
                where.append(" and data_id like ? ");
                paramList.add(generateLikeArgument(dataId));
            }
            if (StringUtils.isNotBlank(group)) {
                where.append(" and group_id=? ");
                paramList.add(group);
            }
            if (StringUtils.isNotBlank(appName)) {
                where.append(" and app_name=? ");
                paramList.add(appName);
            }
        }
        return databaseOperate.queryMany(SQL_FIND_ALL_CONFIG_INFO + where, paramList.toArray(), CONFIG_ALL_INFO_ROW_MAPPER);
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
                    configInfo.getTenant(), configInfo.getAppName(), configInfo.getContent());

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

    /**
     * query tenantInfo (namespace) existence based by tenantId
     *
     * @param tenantId
     * @return count by tenantId
     */
    public int tenantInfoCountByTenantId(String tenantId) {
        Assert.hasText(tenantId, "tenantId can not be null");
        Integer result = databaseOperate.queryOne(SQL_TENANT_INFO_COUNT_BY_TENANT_ID, new String[]{tenantId}, Integer.class);
        if (result == null) {
            return 0;
        }
        return result;
    }

}
