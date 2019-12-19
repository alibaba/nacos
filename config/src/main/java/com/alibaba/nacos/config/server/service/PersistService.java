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

import com.alibaba.nacos.config.server.enums.FileTypeEnum;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.config.server.model.*;
import com.alibaba.nacos.config.server.utils.LogUtil;
import com.alibaba.nacos.config.server.utils.MD5;
import com.alibaba.nacos.config.server.utils.PaginationHelper;
import com.alibaba.nacos.config.server.utils.ParamUtils;
import com.alibaba.nacos.config.server.utils.event.EventDispatcher;
import com.google.common.base.Joiner;
import com.google.common.collect.Lists;
import org.apache.commons.lang.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.dao.IncorrectResultSizeDataAccessException;
import org.springframework.jdbc.CannotGetJdbcConnectionException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementCreator;
import org.springframework.jdbc.core.PreparedStatementSetter;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.TransactionException;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.TransactionSystemException;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.sql.*;
import java.util.*;
import java.util.Map.Entry;

import static com.alibaba.nacos.config.server.utils.LogUtil.defaultLog;
import static com.alibaba.nacos.config.server.utils.LogUtil.fatalLog;

/**
 * 数据库服务，提供ConfigInfo在数据库的存取<br> 3.0开始增加数据版本号, 并将物理删除改为逻辑删除<br> 3.0增加数据库切换功能
 *
 * @author boyan
 * @author leiwen.zh
 * @since 1.0
 */

@Repository
public class PersistService {

    @Autowired
    private DynamicDataSource dynamicDataSource;

    private DataSourceService dataSourceService;

    private static final String SQL_FIND_ALL_CONFIG_INFO = "select id,data_id,group_id,tenant_id,app_name,content,type,md5,gmt_create,gmt_modified,src_user,src_ip,c_desc,c_use,effect,c_schema from config_info";

    private static final String SQL_TENANT_INFO_COUNT_BY_TENANT_ID = "select count(1) from tenant_info where tenant_id = ?";

    private static final String SQL_FIND_CONFIG_INFO_BY_IDS = "SELECT ID,data_id,group_id,tenant_id,app_name,content,md5 FROM config_info WHERE ";

    private static final String SQL_DELETE_CONFIG_INFO_BY_IDS = "DELETE FROM config_info WHERE ";

    /**
     * @author klw
     * @Description: constant variables
     */
    public static final String SPOT = ".";

    @PostConstruct
    public void init() {
        dataSourceService = dynamicDataSource.getDataSource();

        jt = getJdbcTemplate();
        tjt = getTransactionTemplate();
    }

    public boolean checkMasterWritable() {
        return dataSourceService.checkMasterWritable();
    }

    public void setBasicDataSourceService(DataSourceService dataSourceService) {
        this.dataSourceService = dataSourceService;
    }

    static final class ConfigInfoWrapperRowMapper implements
        RowMapper<ConfigInfoWrapper> {
        @Override
        public ConfigInfoWrapper mapRow(ResultSet rs, int rowNum)
            throws SQLException {
            ConfigInfoWrapper info = new ConfigInfoWrapper();

            info.setDataId(rs.getString("data_id"));
            info.setGroup(rs.getString("group_id"));
            info.setTenant(rs.getString("tenant_id"));
            info.setAppName(rs.getString("app_name"));

            try {
                info.setContent(rs.getString("content"));
            } catch (SQLException e) {
                // ignore
            }
            try {
                info.setId(rs.getLong("ID"));
            } catch (SQLException e) {
                // ignore
            }
            try {
                info.setLastModified(rs.getTimestamp("gmt_modified").getTime());
            } catch (SQLException e) {
                // ignore
            }
            try {
                info.setMd5(rs.getString("md5"));
            } catch (SQLException e) {
            }
            return info;
        }
    }

    static final class ConfigInfoBetaWrapperRowMapper implements
        RowMapper<ConfigInfoBetaWrapper> {
        @Override
        public ConfigInfoBetaWrapper mapRow(ResultSet rs, int rowNum)
            throws SQLException {
            ConfigInfoBetaWrapper info = new ConfigInfoBetaWrapper();

            info.setDataId(rs.getString("data_id"));
            info.setGroup(rs.getString("group_id"));
            info.setTenant(rs.getString("tenant_id"));
            info.setAppName(rs.getString("app_name"));
            info.setBetaIps(rs.getString("beta_ips"));

            try {
                info.setContent(rs.getString("content"));
            } catch (SQLException e) {
                // ignore
            }
            try {
                info.setId(rs.getLong("ID"));
            } catch (SQLException e) {
                // ignore
            }
            try {
                info.setLastModified(rs.getTimestamp("gmt_modified").getTime());
            } catch (SQLException e) {
                // ignore
            }
            try {
                info.setMd5(rs.getString("md5"));
            } catch (SQLException e) {
            }
            return info;
        }
    }

    static final class ConfigInfoTagWrapperRowMapper implements
        RowMapper<ConfigInfoTagWrapper> {
        @Override
        public ConfigInfoTagWrapper mapRow(ResultSet rs, int rowNum)
            throws SQLException {
            ConfigInfoTagWrapper info = new ConfigInfoTagWrapper();

            info.setDataId(rs.getString("data_id"));
            info.setGroup(rs.getString("group_id"));
            info.setTenant(rs.getString("tenant_id"));
            info.setTag(rs.getString("tag_id"));
            info.setAppName(rs.getString("app_name"));

            try {
                info.setContent(rs.getString("content"));
            } catch (SQLException e) {
                // ignore
            }
            try {
                info.setId(rs.getLong("ID"));
            } catch (SQLException e) {
                // ignore
            }
            try {
                info.setLastModified(rs.getTimestamp("gmt_modified").getTime());
            } catch (SQLException e) {
                // ignore
            }
            try {
                info.setMd5(rs.getString("md5"));
            } catch (SQLException e) {
            }
            return info;
        }
    }

    static final class ConfigInfoRowMapper implements
        RowMapper<ConfigInfo> {
        @Override
        public ConfigInfo mapRow(ResultSet rs, int rowNum) throws SQLException {
            ConfigInfo info = new ConfigInfo();

            info.setDataId(rs.getString("data_id"));
            info.setGroup(rs.getString("group_id"));
            info.setTenant(rs.getString("tenant_id"));
            info.setAppName(rs.getString("app_name"));

            try {
                info.setContent(rs.getString("content"));
            } catch (SQLException e) {
                // ignore
            }
            try {
                info.setMd5(rs.getString("md5"));
            } catch (SQLException e) {
                // ignore
            }
            try {
                info.setId(rs.getLong("ID"));
            } catch (SQLException e) {
                // ignore
            }
            return info;
        }
    }

    static final class ConfigKeyRowMapper implements
        RowMapper<ConfigKey> {
        @Override
        public ConfigKey mapRow(ResultSet rs, int rowNum) throws SQLException {
            ConfigKey info = new ConfigKey();

            info.setDataId(rs.getString("data_id"));
            info.setGroup(rs.getString("group_id"));
            info.setAppName(rs.getString("app_name"));

            return info;
        }
    }

    static final class ConfigAdvanceInfoRowMapper implements RowMapper<ConfigAdvanceInfo> {
        @Override
        public ConfigAdvanceInfo mapRow(ResultSet rs, int rowNum) throws SQLException {
            ConfigAdvanceInfo info = new ConfigAdvanceInfo();
            info.setCreateTime(rs.getTimestamp("gmt_modified").getTime());
            info.setModifyTime(rs.getTimestamp("gmt_modified").getTime());
            info.setCreateUser(rs.getString("src_user"));
            info.setCreateIp(rs.getString("src_ip"));
            info.setDesc(rs.getString("c_desc"));
            info.setUse(rs.getString("c_use"));
            info.setEffect(rs.getString("effect"));
            info.setType(rs.getString("type"));
            info.setSchema(rs.getString("c_schema"));
            return info;
        }
    }

    static final class ConfigAllInfoRowMapper implements RowMapper<ConfigAllInfo> {
        @Override
        public ConfigAllInfo mapRow(ResultSet rs, int rowNum) throws SQLException {
            ConfigAllInfo info = new ConfigAllInfo();
            info.setDataId(rs.getString("data_id"));
            info.setGroup(rs.getString("group_id"));
            info.setTenant(rs.getString("tenant_id"));
            info.setAppName(rs.getString("app_name"));
            try {
                info.setContent(rs.getString("content"));
            } catch (SQLException e) {
                // ignore
            }
            try {
                info.setMd5(rs.getString("md5"));
            } catch (SQLException e) {
                // ignore
            }
            try {
                info.setId(rs.getLong("ID"));
            } catch (SQLException e) {
                // ignore
            }
            info.setCreateTime(rs.getTimestamp("gmt_modified").getTime());
            info.setModifyTime(rs.getTimestamp("gmt_modified").getTime());
            info.setCreateUser(rs.getString("src_user"));
            info.setCreateIp(rs.getString("src_ip"));
            info.setDesc(rs.getString("c_desc"));
            info.setUse(rs.getString("c_use"));
            info.setEffect(rs.getString("effect"));
            info.setType(rs.getString("type"));
            info.setSchema(rs.getString("c_schema"));
            return info;
        }
    }

    static final class ConfigInfo4BetaRowMapper implements
        RowMapper<ConfigInfo4Beta> {
        @Override
        public ConfigInfo4Beta mapRow(ResultSet rs, int rowNum) throws SQLException {
            ConfigInfo4Beta info = new ConfigInfo4Beta();

            info.setDataId(rs.getString("data_id"));
            info.setGroup(rs.getString("group_id"));
            info.setTenant(rs.getString("tenant_id"));
            info.setAppName(rs.getString("app_name"));
            info.setBetaIps(rs.getString("beta_ips"));

            try {
                info.setContent(rs.getString("content"));
            } catch (SQLException e) {
                // ignore
            }
            try {
                info.setId(rs.getLong("ID"));
            } catch (SQLException e) {
                // ignore
            }
            try {
                info.setMd5(rs.getString("md5"));
            } catch (SQLException e) {
            }
            return info;
        }
    }

    static final class ConfigInfo4TagRowMapper implements
        RowMapper<ConfigInfo4Tag> {
        @Override
        public ConfigInfo4Tag mapRow(ResultSet rs, int rowNum) throws SQLException {
            ConfigInfo4Tag info = new ConfigInfo4Tag();

            info.setDataId(rs.getString("data_id"));
            info.setGroup(rs.getString("group_id"));
            info.setTenant(rs.getString("tenant_id"));
            info.setTag(rs.getString("tag_id"));
            info.setAppName(rs.getString("app_name"));

            try {
                info.setContent(rs.getString("content"));
            } catch (SQLException e) {
                // ignore
            }
            try {
                info.setId(rs.getLong("ID"));
            } catch (SQLException e) {
                // ignore
            }
            try {
                info.setMd5(rs.getString("md5"));
            } catch (SQLException e) {
            }
            return info;
        }
    }

    static final class ConfigInfoBaseRowMapper implements
        RowMapper<ConfigInfoBase> {
        @Override
        public ConfigInfoBase mapRow(ResultSet rs, int rowNum) throws SQLException {
            ConfigInfoBase info = new ConfigInfoBase();

            info.setDataId(rs.getString("data_id"));
            info.setGroup(rs.getString("group_id"));

            try {
                info.setContent(rs.getString("content"));
            } catch (SQLException e) {
                // ignore
            }
            try {
                info.setId(rs.getLong("ID"));
            } catch (SQLException e) {
                // ignore
            }
            return info;
        }
    }

    static final class ConfigInfoAggrRowMapper implements
        RowMapper<ConfigInfoAggr> {
        @Override
        public ConfigInfoAggr mapRow(ResultSet rs, int rowNum)
            throws SQLException {
            ConfigInfoAggr info = new ConfigInfoAggr();
            info.setDataId(rs.getString("data_id"));
            info.setGroup(rs.getString("group_id"));
            info.setDatumId(rs.getString("datum_id"));
            info.setTenant(rs.getString("tenant_id"));
            info.setAppName(rs.getString("app_name"));
            info.setContent(rs.getString("content"));
            return info;
        }
    }

    static final class ConfigInfoChangedRowMapper implements RowMapper<ConfigInfoChanged> {
        @Override
        public ConfigInfoChanged mapRow(ResultSet rs, int rowNum) throws SQLException {
            ConfigInfoChanged info = new ConfigInfoChanged();
            info.setDataId(rs.getString("data_id"));
            info.setGroup(rs.getString("group_id"));
            info.setTenant(rs.getString("tenant_id"));
            return info;
        }
    }

    static final class ConfigHistoryRowMapper implements RowMapper<ConfigHistoryInfo> {
        @Override
        public ConfigHistoryInfo mapRow(ResultSet rs, int rowNum) throws SQLException {
            ConfigHistoryInfo configHistoryInfo = new ConfigHistoryInfo();
            configHistoryInfo.setId(rs.getLong("nid"));
            configHistoryInfo.setDataId(rs.getString("data_id"));
            configHistoryInfo.setGroup(rs.getString("group_id"));
            configHistoryInfo.setTenant(rs.getString("tenant_id"));
            configHistoryInfo.setAppName(rs.getString("app_name"));
            configHistoryInfo.setSrcIp(rs.getString("src_ip"));
            configHistoryInfo.setOpType(rs.getString("op_type"));
            configHistoryInfo.setCreatedTime(rs.getTimestamp("gmt_create"));
            configHistoryInfo.setLastModifiedTime(rs.getTimestamp("gmt_modified"));
            return configHistoryInfo;
        }
    }

    static final class ConfigHistoryDetailRowMapper implements RowMapper<ConfigHistoryInfo> {
        @Override
        public ConfigHistoryInfo mapRow(ResultSet rs, int rowNum) throws SQLException {
            ConfigHistoryInfo configHistoryInfo = new ConfigHistoryInfo();
            configHistoryInfo.setId(rs.getLong("nid"));
            configHistoryInfo.setDataId(rs.getString("data_id"));
            configHistoryInfo.setGroup(rs.getString("group_id"));
            configHistoryInfo.setTenant(rs.getString("tenant_id"));
            configHistoryInfo.setAppName(rs.getString("app_name"));
            configHistoryInfo.setMd5(rs.getString("md5"));
            configHistoryInfo.setContent(rs.getString("content"));
            configHistoryInfo.setSrcUser(rs.getString("src_user"));
            configHistoryInfo.setSrcIp(rs.getString("src_ip"));
            configHistoryInfo.setOpType(rs.getString("op_type"));
            configHistoryInfo.setCreatedTime(rs.getTimestamp("gmt_create"));
            configHistoryInfo.setLastModifiedTime(rs.getTimestamp("gmt_modified"));
            return configHistoryInfo;
        }
    }

    static final class TenantInfoRowMapper implements RowMapper<TenantInfo> {
        @Override
        public TenantInfo mapRow(ResultSet rs, int rowNum) throws SQLException {
            TenantInfo info = new TenantInfo();
            info.setTenantId(rs.getString("tenant_id"));
            info.setTenantName(rs.getString("tenant_name"));
            info.setTenantDesc(rs.getString("tenant_desc"));
            return info;
        }
    }

    static final class UserRowMapper implements RowMapper<User> {
        @Override
        public User mapRow(ResultSet rs, int rowNum) throws SQLException {
            User user = new User();
            user.setUsername(rs.getString("username"));
            user.setPassword(rs.getString("password"));
            return user;
        }
    }

    public synchronized void reload() throws IOException {
        this.dataSourceService.reload();
    }

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

    // ----------------------- config_info 表 insert update delete

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
                    addConfiTagsRelationAtomic(configId, configTags, configInfo.getDataId(), configInfo.getGroup(),
                        configInfo.getTenant());
                    insertConfigHistoryAtomic(0, configInfo, srcIp, srcUser, time, "I");
                    if (notify) {
                        EventDispatcher.fireEvent(
                            new ConfigDataChangeEvent(false, configInfo.getDataId(), configInfo.getGroup(),
                                configInfo.getTenant(), time.getTime()));
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
     * 添加普通配置信息，发布数据变更事件
     */
    public void addConfigInfo4Beta(ConfigInfo configInfo, String betaIps,
                                   String srcIp, String srcUser, Timestamp time, boolean notify) {
        String appNameTmp = StringUtils.isBlank(configInfo.getAppName()) ? StringUtils.EMPTY : configInfo.getAppName();
        String tenantTmp = StringUtils.isBlank(configInfo.getTenant()) ? StringUtils.EMPTY : configInfo.getTenant();
        try {
            String md5 = MD5.getInstance().getMD5String(configInfo.getContent());
            jt.update(
                "INSERT INTO config_info_beta(data_id,group_id,tenant_id,app_name,content,md5,beta_ips,src_ip,"
                    + "src_user,gmt_create,gmt_modified) VALUES(?,?,?,?,?,?,?,?,?,?,?)",
                configInfo.getDataId(), configInfo.getGroup(), tenantTmp, appNameTmp, configInfo.getContent(), md5,
                betaIps, srcIp, srcUser, time, time);
            if (notify) {
                EventDispatcher.fireEvent(new ConfigDataChangeEvent(true, configInfo.getDataId(), configInfo.getGroup(),
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
        String tenantTmp = StringUtils.isBlank(configInfo.getTenant()) ? StringUtils.EMPTY : configInfo.getTenant();
        String tagTmp = StringUtils.isBlank(tag) ? StringUtils.EMPTY : tag.trim();
        try {
            String md5 = MD5.getInstance().getMD5String(configInfo.getContent());
            jt.update(
                "INSERT INTO config_info_tag(data_id,group_id,tenant_id,tag_id,app_name,content,md5,src_ip,src_user,"
                    + "gmt_create,gmt_modified) VALUES(?,?,?,?,?,?,?,?,?,?,?)",
                configInfo.getDataId(), configInfo.getGroup(), tenantTmp, tagTmp, appNameTmp, configInfo.getContent(),
                md5,
                srcIp, srcUser, time, time);
            if (notify) {
                EventDispatcher.fireEvent(new ConfigDataChangeEvent(false, configInfo.getDataId(),
                    configInfo.getGroup(), tenantTmp, tagTmp, time.getTime()));
            }
        } catch (CannotGetJdbcConnectionException e) {
            fatalLog.error("[db-error] " + e.toString(), e);
            throw e;
        }
    }

    /**
     * 更新配置信息
     */
    public void updateConfigInfo(final ConfigInfo configInfo, final String srcIp, final String srcUser,
                                 final Timestamp time, final Map<String, Object> configAdvanceInfo,
                                 final boolean notify) {
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
                    updateConfigInfoAtomic(configInfo, srcIp, srcUser, time, configAdvanceInfo);
                    String configTags = configAdvanceInfo == null ? null : (String) configAdvanceInfo.get("config_tags");
                    if (configTags != null) {
                        // 删除所有tag，然后再重新创建
                        removeTagByIdAtomic(oldConfigInfo.getId());
                        addConfiTagsRelationAtomic(oldConfigInfo.getId(), configTags, configInfo.getDataId(),
                            configInfo.getGroup(), configInfo.getTenant());
                    }
                    insertConfigHistoryAtomic(oldConfigInfo.getId(), oldConfigInfo, srcIp, srcUser, time, "U");
                    if (notify) {
                        EventDispatcher.fireEvent(new ConfigDataChangeEvent(false, configInfo.getDataId(),
                            configInfo.getGroup(), configInfo.getTenant(), time.getTime()));
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
     * 更新配置信息
     */
    public void updateConfigInfo4Beta(ConfigInfo configInfo, String srcIp, String srcUser, Timestamp time,
                                      boolean notify) {
        String appNameTmp = StringUtils.isBlank(configInfo.getAppName()) ? StringUtils.EMPTY : configInfo.getAppName();
        String tenantTmp = StringUtils.isBlank(configInfo.getTenant()) ? StringUtils.EMPTY : configInfo.getTenant();
        try {
            String md5 = MD5.getInstance().getMD5String(configInfo.getContent());
            jt.update(
                "UPDATE config_info_beta SET content=?, md5 = ?, src_ip=?,src_user=?,gmt_modified=?,app_name=? WHERE "
                    + "data_id=? AND group_id=? AND tenant_id=?",
                configInfo.getContent(), md5, srcIp, srcUser, time, appNameTmp, configInfo.getDataId(),
                configInfo.getGroup(), tenantTmp);
            if (notify) {
                EventDispatcher.fireEvent(new ConfigDataChangeEvent(true, configInfo.getDataId(), configInfo.getGroup(),
                    tenantTmp, time.getTime()));
            }

        } catch (CannotGetJdbcConnectionException e) {
            fatalLog.error("[db-error] " + e.toString(), e);
            throw e;
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
            jt.update(
                "UPDATE config_info_tag SET content=?, md5 = ?, src_ip=?,src_user=?,gmt_modified=?,app_name=? WHERE "
                    + "data_id=? AND group_id=? AND tenant_id=? AND tag_id=?",
                configInfo.getContent(), md5, srcIp, srcUser, time, appNameTmp, configInfo.getDataId(),
                configInfo.getGroup(), tenantTmp, tagTmp);
            if (notify) {
                EventDispatcher.fireEvent(new ConfigDataChangeEvent(true, configInfo.getDataId(), configInfo.getGroup(),
                    tenantTmp, tagTmp, time.getTime()));
            }

        } catch (CannotGetJdbcConnectionException e) {
            fatalLog.error("[db-error] " + e.toString(), e);
            throw e;
        }
    }

    public void insertOrUpdateBeta(final ConfigInfo configInfo, final String betaIps, final String srcIp,
                                   final String srcUser, final Timestamp time, final boolean notify) {
        try {
            addConfigInfo4Beta(configInfo, betaIps, srcIp, null, time, notify);
        } catch (DataIntegrityViolationException ive) { // 唯一性约束冲突
            updateConfigInfo4Beta(configInfo, srcIp, null, time, notify);
        }
    }

    public void insertOrUpdateTag(final ConfigInfo configInfo, final String tag, final String srcIp,
                                  final String srcUser, final Timestamp time, final boolean notify) {
        try {
            addConfigInfo4Tag(configInfo, tag, srcIp, null, time, notify);
        } catch (DataIntegrityViolationException ive) { // 唯一性约束冲突
            updateConfigInfo4Tag(configInfo, tag, srcIp, null, time, notify);
        }
    }

    /**
     * 更新md5
     */
    public void updateMd5(String dataId, String group, String tenant, String md5, Timestamp lastTime) {
        String tenantTmp = StringUtils.isBlank(tenant) ? StringUtils.EMPTY : tenant;
        try {
            jt.update(
                "UPDATE config_info SET md5 = ? WHERE data_id=? AND group_id=? AND tenant_id=? AND gmt_modified=?",
                md5, dataId, group, tenantTmp, lastTime);
        } catch (CannotGetJdbcConnectionException e) {
            fatalLog.error("[db-error] " + e.toString(), e);
            throw e;
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
        try {
            addConfigInfo(srcIp, srcUser, configInfo, time, configAdvanceInfo, notify);
        } catch (DataIntegrityViolationException ive) { // 唯一性约束冲突
            updateConfigInfo(configInfo, srcIp, srcUser, time, configAdvanceInfo, notify);
        }
    }

    /**
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
     * @author klw
     * @Description: delete config info by ids
     * @Date 2019/7/5 16:45
     * @Param [ids, srcIp, srcUser]
     * @return List<ConfigInfo> deleted configInfos
     */
    public List<ConfigInfo> removeConfigInfoByIds(final List<Long> ids, final String srcIp, final String srcUser) {
        if(CollectionUtils.isEmpty(ids)){
            return null;
        }
        ids.removeAll(Collections.singleton(null));
        return tjt.execute(new TransactionCallback<List<ConfigInfo>>() {
            final Timestamp time = new Timestamp(System.currentTimeMillis());

            @Override
            public List<ConfigInfo> doInTransaction(TransactionStatus status) {
                try {
                    String idsStr = Joiner.on(",").join(ids);
                    List<ConfigInfo> configInfoList = findConfigInfosByIds(idsStr);
                    if (!CollectionUtils.isEmpty(configInfoList)) {
                        removeConfigInfoByIdsAtomic(idsStr);
                        for(ConfigInfo configInfo : configInfoList){
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
     * 删除beta配置信息, 物理删除
     */
    public void removeConfigInfo4Beta(final String dataId, final String group, final String tenant) {
        final String tenantTmp = StringUtils.isBlank(tenant) ? StringUtils.EMPTY : tenant;
        tjt.execute(new TransactionCallback<Boolean>() {
            @Override
            public Boolean doInTransaction(TransactionStatus status) {
                try {
                    ConfigInfo configInfo = findConfigInfo4Beta(dataId, group, tenant);
                    if (configInfo != null) {
                        jt.update("DELETE FROM config_info_beta WHERE data_id=? AND group_id=? AND tenant_id=?", dataId,
                            group, tenantTmp);
                    }
                } catch (CannotGetJdbcConnectionException e) {
                    fatalLog.error("[db-error] " + e.toString(), e);
                    throw e;
                }
                return Boolean.TRUE;
            }
        });
    }

    // ----------------------- config_aggr_info 表 insert update delete

    /**
     * 增加聚合前数据到数据库, select -> update or insert
     */
    public boolean addAggrConfigInfo(final String dataId, final String group, String tenant, final String datumId,
                                     String appName, final String content) {
        String appNameTmp = StringUtils.isBlank(appName) ? StringUtils.EMPTY : appName;
        String tenantTmp = StringUtils.isBlank(tenant) ? StringUtils.EMPTY : tenant;
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

        try {
            try {
                String dbContent = jt.queryForObject(select, new Object[]{dataId, group, tenantTmp, datumId},
                    String.class);

                if (dbContent != null && dbContent.equals(content)) {
                    return true;
                } else {
                    return jt.update(update, content, now, dataId, group, tenantTmp, datumId) > 0;
                }
            } catch (EmptyResultDataAccessException ex) { // no data, insert
                return jt.update(insert, dataId, group, tenantTmp, datumId, appNameTmp, content, now) > 0;
            }
        } catch (DataAccessException e) {
            fatalLog.error("[db-error] " + e.toString(), e);
            throw e;
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
            this.jt.update(sql, new PreparedStatementSetter() {
                @Override
                public void setValues(PreparedStatement ps) throws SQLException {
                    int index = 1;
                    ps.setString(index++, dataId);
                    ps.setString(index++, group);
                    ps.setString(index++, tenantTmp);
                    ps.setString(index, datumId);
                }
            });
        } catch (CannotGetJdbcConnectionException e) {
            fatalLog.error("[db-error] " + e.toString(), e);
            throw e;
        }
    }

    /**
     * 删除一个dataId下面所有的聚合前数据
     */
    public void removeAggrConfigInfo(final String dataId, final String group, final String tenant) {
        final String tenantTmp = StringUtils.isBlank(tenant) ? StringUtils.EMPTY : tenant;
        String sql = "DELETE FROM config_info_aggr WHERE data_id=? AND group_id=? AND tenant_id=?";

        try {
            this.jt.update(sql, new PreparedStatementSetter() {
                @Override
                public void setValues(PreparedStatement ps) throws SQLException {
                    int index = 1;
                    ps.setString(index++, dataId);
                    ps.setString(index++, group);
                    ps.setString(index, tenantTmp);
                }
            });
        } catch (CannotGetJdbcConnectionException e) {
            fatalLog.error("[db-error] " + e.toString(), e);
            throw e;
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
            jt.update(sql, dataId, group, tenantTmp);
        } catch (CannotGetJdbcConnectionException e) {
            fatalLog.error("[db-error] " + e.toString(), e);
            return false;
        }
        return true;
    }

    /**
     * 删除startTime前的数据
     */
    public void removeConfigHistory(final Timestamp startTime, final int limitSize) {
        String sql = "delete from his_config_info where gmt_modified < ? limit ?";
        PaginationHelper<ConfigInfo> helper = new PaginationHelper<ConfigInfo>();
        try {
            helper.updateLimit(jt, sql, new Object[]{startTime, limitSize});
        } catch (CannotGetJdbcConnectionException e) {
            fatalLog.error("[db-error] " + e.toString(), e);
            throw e;
        }
    }

    /**
     * 获取指定时间前配置条数
     */
    public int findConfigHistoryCountByTime(final Timestamp startTime) {
        String sql = "SELECT COUNT(*) FROM his_config_info WHERE gmt_modified < ?";
        Integer result = jt.queryForObject(sql, Integer.class, new Object[]{startTime});
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
        try {
            return jt.queryForObject(sql, Integer.class);
        } catch (NullPointerException e) {
            return 0;
        }
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
            Boolean isPublishOk = tjt.execute(new TransactionCallback<Boolean>() {
                @Override
                public Boolean doInTransaction(TransactionStatus status) {
                    for (Entry<String, String> entry : datumMap.entrySet()) {
                        try {
                            if (!addAggrConfigInfo(dataId, group, tenant, entry.getKey(), appName, entry.getValue())) {
                                throw new TransactionSystemException(
                                    "error in addAggrConfigInfo");
                            }
                        } catch (Throwable e) {
                            throw new TransactionSystemException(
                                "error in addAggrConfigInfo");
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
            fatalLog.error("[db-error] " + e.toString(), e);
            return false;
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
        try {
            Boolean isReplaceOk = tjt.execute(new TransactionCallback<Boolean>() {
                @Override
                public Boolean doInTransaction(TransactionStatus status) {
                    try {
                        String appNameTmp = appName == null ? "" : appName;
                        removeAggrConfigInfo(dataId, group, tenant);
                        String tenantTmp = StringUtils.isBlank(tenant) ? StringUtils.EMPTY : tenant;
                        String sql
                            = "INSERT INTO config_info_aggr(data_id, group_id, tenant_id, datum_id, app_name, "
                            + "content, gmt_modified) VALUES(?,?,?,?,?,?,?) ";
                        for (Entry<String, String> datumEntry : datumMap.entrySet()) {
                            jt.update(sql, dataId, group, tenantTmp, datumEntry.getKey(), appNameTmp,
                                datumEntry.getValue(), new Timestamp(System.currentTimeMillis()));
                        }
                    } catch (Throwable e) {
                        throw new TransactionSystemException(
                            "error in addAggrConfigInfo");
                    }
                    return Boolean.TRUE;
                }
            });
            if (isReplaceOk == null) {
                return false;
            }
            return isReplaceOk;
        } catch (TransactionException e) {
            fatalLog.error("[db-error] " + e.toString(), e);
            return false;
        }

    }

    /**
     * 查找所有的dataId和group。保证不返回NULL。
     */
    @Deprecated
    public List<ConfigInfo> findAllDataIdAndGroup() {
        String sql = "SELECT DISTINCT data_id, group_id FROM config_info";

        try {
            return jt.query(sql, new Object[]{}, CONFIG_INFO_ROW_MAPPER);
        } catch (EmptyResultDataAccessException e) {
            return Collections.emptyList();
        } catch (CannotGetJdbcConnectionException e) {
            fatalLog.error("[db-error] " + e.toString(), e);
            throw e;
        } catch (Exception e) {
            fatalLog.error("[db-other-error]" + e.getMessage(), e);
            throw new RuntimeException(e);
        }
    }

    /**
     * 根据dataId和group查询配置信息
     */
    public ConfigInfo4Beta findConfigInfo4Beta(final String dataId, final String group, final String tenant) {
        String tenantTmp = StringUtils.isBlank(tenant) ? StringUtils.EMPTY : tenant;
        try {
            return this.jt.queryForObject(
                "SELECT ID,data_id,group_id,tenant_id,app_name,content,beta_ips FROM config_info_beta WHERE data_id=?"
                    + " AND group_id=? AND tenant_id=?",
                new Object[]{dataId, group, tenantTmp}, CONFIG_INFO4BETA_ROW_MAPPER);
        } catch (EmptyResultDataAccessException e) { // 表明数据不存在, 返回null
            return null;
        } catch (CannotGetJdbcConnectionException e) {
            fatalLog.error("[db-error] " + e.toString(), e);
            throw e;
        }
    }

    /**
     * 根据dataId和group查询配置信息
     */
    public ConfigInfo4Tag findConfigInfo4Tag(final String dataId, final String group, final String tenant,
                                             final String tag) {
        String tenantTmp = StringUtils.isBlank(tenant) ? StringUtils.EMPTY : tenant;
        String tagTmp = StringUtils.isBlank(tag) ? StringUtils.EMPTY : tag.trim();
        try {
            return this.jt.queryForObject(
                "SELECT ID,data_id,group_id,tenant_id,tag_id,app_name,content FROM config_info_tag WHERE data_id=? "
                    + "AND group_id=? AND tenant_id=? AND tag_id=?",
                new Object[]{dataId, group, tenantTmp, tagTmp}, CONFIG_INFO4TAG_ROW_MAPPER);
        } catch (EmptyResultDataAccessException e) { // 表明数据不存在, 返回null
            return null;
        } catch (CannotGetJdbcConnectionException e) {
            fatalLog.error("[db-error] " + e.toString(), e);
            throw e;
        }
    }

    /**
     * 根据dataId和group查询配置信息
     */
    public ConfigInfo findConfigInfoApp(final String dataId, final String group, final String tenant,
                                        final String appName) {
        String tenantTmp = StringUtils.isBlank(tenant) ? StringUtils.EMPTY : tenant;
        try {
            return this.jt.queryForObject(
                "SELECT ID,data_id,group_id,tenant_id,app_name,content FROM config_info WHERE data_id=? AND "
                    + "group_id=? AND tenant_id=? AND app_name=?",
                new Object[]{dataId, group, tenantTmp, appName}, CONFIG_INFO_ROW_MAPPER);
        } catch (EmptyResultDataAccessException e) { // 表明数据不存在, 返回null
            return null;
        } catch (CannotGetJdbcConnectionException e) {
            fatalLog.error("[db-error] " + e.toString(), e);
            throw e;
        }
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

        try {
            return this.jt.queryForObject(sql.toString(), paramList.toArray(), CONFIG_INFO_ROW_MAPPER);
        } catch (EmptyResultDataAccessException e) { // 表明数据不存在, 返回null
            return null;
        } catch (CannotGetJdbcConnectionException e) {
            fatalLog.error("[db-error] " + e.toString(), e);
            throw e;
        }

    }

    /**
     * 根据dataId和group查询配置信息
     */
    public ConfigInfoBase findConfigInfoBase(final String dataId, final String group) {
        try {
            return this.jt
                .queryForObject(
                    "SELECT ID,data_id,group_id,content FROM config_info WHERE data_id=? AND group_id=? AND "
                        + "tenant_id=?",
                    new Object[]{dataId, group, StringUtils.EMPTY},
                    CONFIG_INFO_BASE_ROW_MAPPER);
        } catch (EmptyResultDataAccessException e) { // 表明数据不存在, 返回null
            return null;
        } catch (CannotGetJdbcConnectionException e) {
            fatalLog.error("[db-error] " + e.toString(), e);
            throw e;
        }
    }

    /**
     * 根据数据库主键ID查询配置信息
     *
     * @param id
     * @return
     */
    public ConfigInfo findConfigInfo(long id) {
        try {
            return this.jt
                .queryForObject(
                    "SELECT ID,data_id,group_id,tenant_id,app_name,content FROM config_info WHERE ID=?",
                    new Object[]{id}, CONFIG_INFO_ROW_MAPPER);
        } catch (EmptyResultDataAccessException e) { // 表明数据不存在
            return null;
        } catch (CannotGetJdbcConnectionException e) {
            fatalLog.error("[db-error] " + e.toString(), e);
            throw e;
        }
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
        try {
            return helper.fetchPage(this.jt, "select count(*) from config_info where data_id=? and tenant_id=?",
                "select ID,data_id,group_id,tenant_id,app_name,content from config_info where data_id=? and "
                    + "tenant_id=?",
                new Object[]{dataId, tenantTmp}, pageNo, pageSize, CONFIG_INFO_ROW_MAPPER);
        } catch (CannotGetJdbcConnectionException e) {
            fatalLog.error("[db-error] " + e.toString(), e);
            throw e;
        }
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
        try {
            return helper.fetchPage(this.jt,
                "select count(*) from config_info where data_id=? and tenant_id=? and app_name=?",
                "select ID,data_id,group_id,tenant_id,app_name,content from config_info where data_id=? and "
                    + "tenant_id=? and app_name=?",
                new Object[]{dataId, tenantTmp, appName}, pageNo, pageSize, CONFIG_INFO_ROW_MAPPER);
        } catch (CannotGetJdbcConnectionException e) {
            fatalLog.error("[db-error] " + e.toString(), e);
            throw e;
        }
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
        try {
            return helper.fetchPage(this.jt, sqlCount.toString(), sql.toString(), paramList.toArray(), pageNo, pageSize,
                CONFIG_INFO_ROW_MAPPER);
        } catch (CannotGetJdbcConnectionException e) {
            fatalLog.error("[db-error] " + e.toString(), e);
            throw e;
        }
    }

    public Page<ConfigInfo> findConfigInfo4Page(final int pageNo, final int pageSize, final String dataId,
                                                final String group,
                                                final String tenant, final Map<String, Object> configAdvanceInfo) {
        String tenantTmp = StringUtils.isBlank(tenant) ? StringUtils.EMPTY : tenant;
        PaginationHelper<ConfigInfo> helper = new PaginationHelper<ConfigInfo>();
        final String appName = configAdvanceInfo == null ? null : (String) configAdvanceInfo.get("appName");
        final String configTags = configAdvanceInfo == null ? null : (String) configAdvanceInfo.get("config_tags");
        String sqlCount = "select count(*) from config_info";
        String sql = "select ID,data_id,group_id,tenant_id,app_name,content from config_info";
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
        try {
            return helper.fetchPage(this.jt, sqlCount + where, sql + where, paramList.toArray(), pageNo, pageSize,
                CONFIG_INFO_ROW_MAPPER);
        } catch (CannotGetJdbcConnectionException e) {
            fatalLog.error("[db-error] " + e.toString(), e);
            throw e;
        }
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
        try {
            return helper
                .fetchPage(
                    this.jt,
                    "select count(*) from config_info where data_id=? and tenant_id=?",
                    "select ID,data_id,group_id,content from config_info where data_id=? and tenant_id=?",
                    new Object[]{dataId, StringUtils.EMPTY}, pageNo, pageSize,
                    CONFIG_INFO_BASE_ROW_MAPPER);
        } catch (CannotGetJdbcConnectionException e) {
            fatalLog.error("[db-error] " + e.toString(), e);
            throw e;
        }
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
        try {
            return helper.fetchPage(this.jt, "select count(*) from config_info where group_id=? and tenant_id=?",
                "select ID,data_id,group_id,tenant_id,app_name,content from config_info where group_id=? and "
                    + "tenant_id=?",
                new Object[]{group, tenantTmp}, pageNo, pageSize, CONFIG_INFO_ROW_MAPPER);
        } catch (CannotGetJdbcConnectionException e) {
            fatalLog.error("[db-error] " + e.toString(), e);
            throw e;
        }
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
        try {
            return helper.fetchPage(this.jt,
                "select count(*) from config_info where group_id=? and tenant_id=? and app_name =?",
                "select ID,data_id,group_id,tenant_id,app_name,content from config_info where group_id=? and "
                    + "tenant_id=? and app_name =?",
                new Object[]{group, tenantTmp, appName}, pageNo, pageSize, CONFIG_INFO_ROW_MAPPER);
        } catch (CannotGetJdbcConnectionException e) {
            fatalLog.error("[db-error] " + e.toString(), e);
            throw e;
        }
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

        try {
            return helper.fetchPage(this.jt, sqlCount.toString(), sql.toString(), paramList.toArray(), pageNo, pageSize,
                CONFIG_INFO_ROW_MAPPER);
        } catch (CannotGetJdbcConnectionException e) {
            fatalLog.error("[db-error] " + e.toString(), e);
            throw e;
        }
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
        try {
            return helper.fetchPage(this.jt, "select count(*) from config_info where tenant_id like ? and app_name=?",
                "select ID,data_id,group_id,tenant_id,app_name,content from config_info where tenant_id like ? and "
                    + "app_name=?",
                new Object[]{generateLikeArgument(tenantTmp), appName}, pageNo, pageSize,
                CONFIG_INFO_ROW_MAPPER);
        } catch (CannotGetJdbcConnectionException e) {
            fatalLog.error("[db-error] " + e.toString(), e);
            throw e;
        }
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

        try {
            return helper.fetchPage(this.jt, sqlCount.toString(), sql.toString(), paramList.toArray(), pageNo, pageSize,
                CONFIG_INFO_ROW_MAPPER);
        } catch (CannotGetJdbcConnectionException e) {
            fatalLog.error("[db-error] " + e.toString(), e);
            throw e;
        }
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
        try {
            return helper
                .fetchPage(
                    this.jt,
                    "select count(*) from config_info where group_id=? and tenant_id=?",
                    "select ID,data_id,group_id,content from config_info where group_id=? and tenant_id=?",
                    new Object[]{group, StringUtils.EMPTY}, pageNo, pageSize,
                    CONFIG_INFO_BASE_ROW_MAPPER);
        } catch (CannotGetJdbcConnectionException e) {
            fatalLog.error("[db-error] " + e.toString(), e);
            throw e;
        }
    }

    /**
     * 返回配置项个数
     */
    public int configInfoCount() {
        String sql = " SELECT COUNT(ID) FROM config_info ";
        Integer result = jt.queryForObject(sql, Integer.class);
        if (result == null) {
            throw new IllegalArgumentException("configInfoCount error");
        }
        return result.intValue();
    }

    /**
     * 返回配置项个数
     */
    public int configInfoCount(String tenant) {
        String sql = " SELECT COUNT(ID) FROM config_info where tenant_id like '" + tenant + "'";
        Integer result = jt.queryForObject(sql, Integer.class);
        if (result == null) {
            throw new IllegalArgumentException("configInfoCount error");
        }
        return result.intValue();
    }

    /**
     * 返回beta配置项个数
     */
    public int configInfoBetaCount() {
        String sql = " SELECT COUNT(ID) FROM config_info_beta ";
        Integer result = jt.queryForObject(sql, Integer.class);
        if (result == null) {
            throw new IllegalArgumentException("configInfoBetaCount error");
        }
        return result.intValue();
    }

    /**
     * 返回beta配置项个数
     */
    public int configInfoTagCount() {
        String sql = " SELECT COUNT(ID) FROM config_info_tag ";
        Integer result = jt.queryForObject(sql, Integer.class);
        if (result == null) {
            throw new IllegalArgumentException("configInfoBetaCount error");
        }
        return result.intValue();
    }

    public List<String> getTenantIdList(int page, int pageSize) {
        String sql = "SELECT tenant_id FROM config_info WHERE tenant_id != '' GROUP BY tenant_id LIMIT ?, ?";
        int from = (page - 1) * pageSize;
        return jt.queryForList(sql, String.class, from, pageSize);
    }

    public List<String> getGroupIdList(int page, int pageSize) {
        String sql = "SELECT group_id FROM config_info WHERE tenant_id ='' GROUP BY group_id LIMIT ?, ?";
        int from = (page - 1) * pageSize;
        return jt.queryForList(sql, String.class, from, pageSize);
    }

    public int aggrConfigInfoCount(String dataId, String group, String tenant) {
        String tenantTmp = StringUtils.isBlank(tenant) ? StringUtils.EMPTY : tenant;
        String sql = " SELECT COUNT(ID) FROM config_info_aggr WHERE data_id = ? AND group_id = ? AND tenant_id = ?";
        Integer result = jt.queryForObject(sql, Integer.class, new Object[]{dataId, group, tenantTmp});
        if (result == null) {
            throw new IllegalArgumentException("aggrConfigInfoCount error");
        }
        return result.intValue();
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
        Integer result = jt.queryForObject(sql.toString(), Integer.class, objectList.toArray());
        if (result == null) {
            throw new IllegalArgumentException("aggrConfigInfoCount error");
        }
        return result.intValue();
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
        try {
            return helper.fetchPageLimit(jt, sqlCountRows, sqlFetchRows,
                new Object[]{generateLikeArgument(tenantTmp), (pageNo - 1) * pageSize, pageSize},
                pageNo, pageSize, CONFIG_INFO_ROW_MAPPER);
        } catch (CannotGetJdbcConnectionException e) {
            fatalLog.error("[db-error] " + e.toString(), e);
            throw e;
        }
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

        try {
            List<ConfigKey> result = jt.query(select,
                new Object[]{generateLikeArgument(tenantTmp), (pageNo - 1) * pageSize, pageSize},
                // new Object[0],
                CONFIG_KEY_ROW_MAPPER);

            for (ConfigKey item : result) {
                page.getPageItems().add(item);
            }
            return page;
        } catch (EmptyResultDataAccessException e) {
            return page;
        } catch (CannotGetJdbcConnectionException e) {
            fatalLog.error("[db-error] " + e.toString(), e);
            throw e;
        }
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
        try {
            return helper.fetchPageLimit(jt, sqlCountRows, sqlFetchRows, new Object[]{
                (pageNo - 1) * pageSize, pageSize}, pageNo, pageSize, CONFIG_INFO_BASE_ROW_MAPPER);
        } catch (CannotGetJdbcConnectionException e) {
            fatalLog.error("[db-error] " + e.toString(), e);
            throw e;
        }
    }

    public static class ConfigInfoWrapper extends ConfigInfo {
        private static final long serialVersionUID = 4511997359365712505L;

        private long lastModified;

        public ConfigInfoWrapper() {
        }

        public long getLastModified() {
            return lastModified;
        }

        public void setLastModified(long lastModified) {
            this.lastModified = lastModified;
        }

        @Override
        public int hashCode() {
            return super.hashCode();
        }

        @Override
        public boolean equals(Object obj) {
            return super.equals(obj);
        }
    }

    public static class ConfigInfoBetaWrapper extends ConfigInfo4Beta {
        private static final long serialVersionUID = 4511997359365712505L;

        private long lastModified;

        public ConfigInfoBetaWrapper() {
        }

        public long getLastModified() {
            return lastModified;
        }

        public void setLastModified(long lastModified) {
            this.lastModified = lastModified;
        }

        @Override
        public int hashCode() {
            return super.hashCode();
        }

        @Override
        public boolean equals(Object obj) {
            return super.equals(obj);
        }
    }

    public static class ConfigInfoTagWrapper extends ConfigInfo4Tag {
        private static final long serialVersionUID = 4511997359365712505L;

        private long lastModified;

        public ConfigInfoTagWrapper() {
        }

        public long getLastModified() {
            return lastModified;
        }

        public void setLastModified(long lastModified) {
            this.lastModified = lastModified;
        }

        @Override
        public int hashCode() {
            return super.hashCode();
        }

        @Override
        public boolean equals(Object obj) {
            return super.equals(obj);
        }
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

        List<String> params = new ArrayList<String>();

        try {
            return helper.fetchPageLimit(jt, sqlCountRows, sqlFetchRows, params.toArray(), pageNo, pageSize,
                CONFIG_INFO_WRAPPER_ROW_MAPPER);
        } catch (CannotGetJdbcConnectionException e) {
            fatalLog.error("[db-error] " + e.toString(), e);
            throw e;
        }
    }

    public Page<ConfigInfoWrapper> findAllConfigInfoFragment(final long lastMaxId, final int pageSize) {
        String select
            = "SELECT id,data_id,group_id,tenant_id,app_name,content,md5,gmt_modified from config_info where id > ? "
            + "order by id asc limit ?,?";
        PaginationHelper<ConfigInfoWrapper> helper = new PaginationHelper<ConfigInfoWrapper>();
        try {
            return helper.fetchPageLimit(jt, select, new Object[]{lastMaxId, 0, pageSize}, 1, pageSize,
                CONFIG_INFO_WRAPPER_ROW_MAPPER);
        } catch (CannotGetJdbcConnectionException e) {
            fatalLog.error("[db-error] " + e.toString(), e);
            throw e;
        }
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
        try {
            return helper.fetchPageLimit(jt, sqlCountRows, sqlFetchRows, new Object[]{
                (pageNo - 1) * pageSize, pageSize}, pageNo, pageSize, CONFIG_INFO_BETA_WRAPPER_ROW_MAPPER);

        } catch (CannotGetJdbcConnectionException e) {
            fatalLog.error("[db-error] " + e.toString(), e);
            throw e;
        }
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
        try {
            return helper.fetchPageLimit(jt, sqlCountRows, sqlFetchRows, new Object[]{
                (pageNo - 1) * pageSize, pageSize}, pageNo, pageSize, CONFIG_INFO_TAG_WRAPPER_ROW_MAPPER);

        } catch (CannotGetJdbcConnectionException e) {
            fatalLog.error("[db-error] " + e.toString(), e);
            throw e;
        }
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
            List<String> params = new ArrayList<String>(dataIds.subList(i, i
                + subQueryLimit < dataIds.size() ? i + subQueryLimit
                : dataIds.size()));

            for (int j = 0; j < params.size(); j++) {
                subQuerySql.append("?");
                if (j != params.size() - 1) {
                    subQuerySql.append(",");
                }
            }

            // group
            params.add(0, group);
            params.add(1, tenantTmp);

            List<ConfigInfo> r = this.jt.query(
                sqlStart + subQuerySql.toString() + sqlEnd,
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

        try {
            return helper.fetchPage(jt, sqlCountRows + where, sqlFetchRows
                    + where, params.toArray(), pageNo, pageSize,
                CONFIG_INFO_ROW_MAPPER);
        } catch (CannotGetJdbcConnectionException e) {
            fatalLog.error("[db-error] " + e.toString(), e);
            throw e;
        }
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

        try {
            return helper.fetchPage(jt, sqlCountRows + where, sqlFetchRows
                    + where, params.toArray(), pageNo, pageSize,
                CONFIG_INFO_ROW_MAPPER);
        } catch (CannotGetJdbcConnectionException e) {
            fatalLog.error("[db-error] " + e.toString(), e);
            throw e;
        }
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
        if (configKeys.length == 0 && blacklist == false) {
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

        try {
            return helper.fetchPage(jt, sqlCountRows + where, sqlFetchRows
                    + where, params.toArray(), pageNo, pageSize,
                CONFIG_INFO_ROW_MAPPER);
        } catch (CannotGetJdbcConnectionException e) {
            fatalLog.error("[db-error] " + e.toString(), e);
            throw e;
        }
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

        try {
            return helper.fetchPage(jt, sqlCountRows + where, sqlFetchRows
                    + where, params.toArray(), pageNo, pageSize,
                CONFIG_INFO_BASE_ROW_MAPPER);
        } catch (CannotGetJdbcConnectionException e) {
            fatalLog.error("[db-error] " + e.toString(), e);
            throw e;
        }
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

        try {
            return this.jt.queryForObject(sql, new Object[]{dataId, group, tenantTmp, datumId},
                CONFIG_INFO_AGGR_ROW_MAPPER);
        } catch (EmptyResultDataAccessException e) {
            // 是EmptyResultDataAccessException, 表明数据不存在, 返回null
            return null;
        } catch (CannotGetJdbcConnectionException e) {
            fatalLog.error("[db-error] " + e.toString(), e);
            throw e;
        } catch (Exception e) {
            fatalLog.error("[db-other-error]" + e.getMessage(), e);
            throw new RuntimeException(e);
        }
    }

    /**
     * 查找一个dataId下面的所有聚合前的数据. 保证不返回NULL.
     */
    public List<ConfigInfoAggr> findConfigInfoAggr(String dataId, String group, String tenant) {
        String tenantTmp = StringUtils.isBlank(tenant) ? StringUtils.EMPTY : tenant;
        String sql
            = "SELECT data_id,group_id,tenant_id,datum_id,app_name,content FROM config_info_aggr WHERE data_id=? AND "
            + "group_id=? AND tenant_id=? ORDER BY datum_id";

        try {
            return this.jt.query(sql, new Object[]{dataId, group, tenantTmp},
                CONFIG_INFO_AGGR_ROW_MAPPER);
        } catch (CannotGetJdbcConnectionException e) {
            fatalLog.error("[db-error] " + e.toString(), e);
            throw e;
        } catch (EmptyResultDataAccessException e) {
            return Collections.emptyList();
        } catch (Exception e) {
            fatalLog.error("[db-other-error]" + e.getMessage(), e);
            throw new RuntimeException(e);
        }
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
        try {
            return helper.fetchPageLimit(jt, sqlCountRows, new Object[]{dataId, group, tenantTmp}, sqlFetchRows,
                new Object[]{dataId, group, tenantTmp, (pageNo - 1) * pageSize, pageSize},
                pageNo, pageSize, CONFIG_INFO_AGGR_ROW_MAPPER);

        } catch (CannotGetJdbcConnectionException e) {
            fatalLog.error("[db-error] " + e.toString(), e);
            throw e;
        }
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

        try {
            Page<ConfigInfoAggr> result = helper.fetchPage(jt, sqlCountRows
                    + where, sqlFetchRows + where, params.toArray(), pageNo,
                pageSize, CONFIG_INFO_AGGR_ROW_MAPPER);
            return result;
        } catch (CannotGetJdbcConnectionException e) {
            fatalLog.error("[db-error] " + e.toString(), e);
            throw e;
        }
    }

    /**
     * 找到所有聚合数据组。
     */
    public List<ConfigInfoChanged> findAllAggrGroup() {
        String sql = "SELECT DISTINCT data_id, group_id, tenant_id FROM config_info_aggr";

        try {
            return this.jt.query(sql, new Object[]{},
                CONFIG_INFO_CHANGED_ROW_MAPPER);
        } catch (CannotGetJdbcConnectionException e) {
            fatalLog.error("[db-error] " + e.toString(), e);
            throw e;
        } catch (EmptyResultDataAccessException e) {
            return null;
        } catch (Exception e) {
            fatalLog.error("[db-other-error]" + e.getMessage(), e);
            throw new RuntimeException(e);
        }
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

        try {
            return this.jt.queryForList(sql, new Object[]{dataId, groupId,
                content}, String.class);
        } catch (EmptyResultDataAccessException e) {
            return null;
        } catch (IncorrectResultSizeDataAccessException e) {
            return null;
        } catch (CannotGetJdbcConnectionException e) {
            fatalLog.error("[db-error] " + e.toString(), e);
            throw e;
        }
    }

    public List<ConfigInfoWrapper> findChangeConfig(final Timestamp startTime,
                                                    final Timestamp endTime) {
        try {
            List<Map<String, Object>> list = jt
                .queryForList(
                    "SELECT data_id, group_id, tenant_id, app_name, content, gmt_modified FROM config_info WHERE "
                        + "gmt_modified >=? AND gmt_modified <= ?",
                    new Object[]{startTime, endTime});
            return convertChangeConfig(list);
        } catch (DataAccessException e) {
            fatalLog.error("[db-error] " + e.toString(), e);
            throw e;
        }
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
        try {
            return helper.fetchPage(jt, sqlCountRows + where, sqlFetchRows + where, params.toArray(), pageNo, pageSize,
                lastMaxId, CONFIG_INFO_WRAPPER_ROW_MAPPER);
        } catch (CannotGetJdbcConnectionException e) {
            fatalLog.error("[db-error] " + e.toString(), e);
            throw e;
        }
    }

    public List<ConfigInfo> findDeletedConfig(final Timestamp startTime,
                                              final Timestamp endTime) {
        try {
            List<Map<String, Object>> list = jt
                .queryForList(
                    "SELECT DISTINCT data_id, group_id, tenant_id FROM his_config_info WHERE op_type = 'D' AND "
                        + "gmt_modified >=? AND gmt_modified <= ?",
                    new Object[]{startTime, endTime});
            return convertDeletedConfig(list);
        } catch (DataAccessException e) {
            fatalLog.error("[db-error] " + e.toString(), e);
            throw e;
        }
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

        KeyHolder keyHolder = new GeneratedKeyHolder();

        final String sql
            = "INSERT INTO config_info(data_id,group_id,tenant_id,app_name,content,md5,src_ip,src_user,gmt_create,"
            + "gmt_modified,c_desc,c_use,effect,type,c_schema) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)";

        try {
            jt.update(new PreparedStatementCreator() {
                @Override
                public PreparedStatement createPreparedStatement(Connection connection) throws SQLException {
                    PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
                    ps.setString(1, configInfo.getDataId());
                    ps.setString(2, configInfo.getGroup());
                    ps.setString(3, tenantTmp);
                    ps.setString(4, appNameTmp);
                    ps.setString(5, configInfo.getContent());
                    ps.setString(6, md5Tmp);
                    ps.setString(7, srcIp);
                    ps.setString(8, srcUser);
                    ps.setTimestamp(9, time);
                    ps.setTimestamp(10, time);
                    ps.setString(11, desc);
                    ps.setString(12, use);
                    ps.setString(13, effect);
                    ps.setString(14, type);
                    ps.setString(15, schema);
                    return ps;
                }
            }, keyHolder);
            Number nu = keyHolder.getKey();
            if (nu == null) {
                throw new IllegalArgumentException("insert config_info fail");
            }
            return nu.longValue();
        } catch (CannotGetJdbcConnectionException e) {
            fatalLog.error("[db-error] " + e.toString(), e);
            throw e;
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
        try {
            jt.update(
                "INSERT INTO config_tags_relation(id,tag_name,tag_type,data_id,group_id,tenant_id) VALUES(?,?,?,?,?,?)",
                configId, tagName, null, dataId, group, tenant);
        } catch (CannotGetJdbcConnectionException e) {
            fatalLog.error("[db-error] " + e.toString(), e);
            throw e;
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

    public void removeTagByIdAtomic(long id) {
        try {
            jt.update("DELETE FROM config_tags_relation WHERE id=?", id);
        } catch (CannotGetJdbcConnectionException e) {
            fatalLog.error("[db-error] " + e.toString(), e);
            throw e;
        }
    }

    public List<String> getConfigTagsByTenant(String tenant) {
        String sql = "SELECT tag_name FROM config_tags_relation WHERE tenant_id = ? ";
        try {
            return jt.queryForList(sql, new Object[]{tenant}, String.class);
        } catch (EmptyResultDataAccessException e) {
            return null;
        } catch (IncorrectResultSizeDataAccessException e) {
            return null;
        } catch (CannotGetJdbcConnectionException e) {
            fatalLog.error("[db-error] " + e.toString(), e);
            throw e;
        }
    }

    public List<String> selectTagByConfig(String dataId, String group, String tenant) {
        String sql = "SELECT tag_name FROM config_tags_relation WHERE data_id=? AND group_id=? AND tenant_id = ? ";
        try {
            return jt.queryForList(sql, new Object[]{dataId, group, tenant}, String.class);
        } catch (EmptyResultDataAccessException e) {
            return null;
        } catch (IncorrectResultSizeDataAccessException e) {
            return null;
        } catch (CannotGetJdbcConnectionException e) {
            fatalLog.error("[db-error] " + e.toString(), e);
            throw e;
        }
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
        try {
            jt.update("DELETE FROM config_info WHERE data_id=? AND group_id=? AND tenant_id=?", dataId, group,
                tenantTmp);
        } catch (CannotGetJdbcConnectionException e) {
            fatalLog.error("[db-error] " + e.toString(), e);
            throw e;
        }
    }

    /**
     * @author klw
     * @Description: Delete configuration; database atomic operation, minimum SQL action, no business encapsulation
     * @Date 2019/7/5 16:39
     * @Param [id]
     * @return void
     */
    private void removeConfigInfoByIdsAtomic(final String ids) {
        if(StringUtils.isBlank(ids)){
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
        try {
            jt.update(sql.toString(), paramList.toArray());
        } catch (CannotGetJdbcConnectionException e) {
            fatalLog.error("[db-error] " + e.toString(), e);
            throw e;
        }
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
            jt.update("DELETE FROM config_info_tag WHERE data_id=? AND group_id=? AND tenant_id=? AND tag_id=?", dataId,
                group,
                tenantTmp, tagTmp);
        } catch (CannotGetJdbcConnectionException e) {
            fatalLog.error("[db-error] " + e.toString(), e);
            throw e;
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
        String appNameTmp = StringUtils.isBlank(configInfo.getAppName()) ? StringUtils.EMPTY : configInfo.getAppName();
        String tenantTmp = StringUtils.isBlank(configInfo.getTenant()) ? StringUtils.EMPTY : configInfo.getTenant();
        final String md5Tmp = MD5.getInstance().getMD5String(configInfo.getContent());
        String desc = configAdvanceInfo == null ? null : (String) configAdvanceInfo.get("desc");
        String use = configAdvanceInfo == null ? null : (String) configAdvanceInfo.get("use");
        String effect = configAdvanceInfo == null ? null : (String) configAdvanceInfo.get("effect");
        String type = configAdvanceInfo == null ? null : (String) configAdvanceInfo.get("type");
        String schema = configAdvanceInfo == null ? null : (String) configAdvanceInfo.get("schema");

        try {
            jt.update(
                "UPDATE config_info SET content=?, md5 = ?, src_ip=?,src_user=?,gmt_modified=?,app_name=?,c_desc=?,c_use=?,effect=?,type=?,c_schema=? WHERE data_id=? AND group_id=? AND tenant_id=?",
                configInfo.getContent(), md5Tmp, srcIp, srcUser, time, appNameTmp, desc, use, effect, type, schema,
                configInfo.getDataId(), configInfo.getGroup(), tenantTmp);
        } catch (CannotGetJdbcConnectionException e) {
            fatalLog.error("[db-error] " + e.toString(), e);
            throw e;
        }
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
        try {
            return this.jt.queryForObject(
                "SELECT ID,data_id,group_id,tenant_id,app_name,content,md5 FROM config_info WHERE data_id=? AND group_id=? AND tenant_id=?",
                new Object[]{dataId, group, tenantTmp}, CONFIG_INFO_ROW_MAPPER);
        } catch (EmptyResultDataAccessException e) { // 表明数据不存在, 返回null
            return null;
        } catch (CannotGetJdbcConnectionException e) {
            fatalLog.error("[db-error] " + e.toString(), e);
            throw e;
        }
    }

    /**
     * @author klw
     * @Description: find ConfigInfo by ids
     * @Date 2019/7/5 16:37
     * @Param [ids]
     * @return java.util.List<com.alibaba.nacos.config.server.model.ConfigInfo>
     */
    public List<ConfigInfo> findConfigInfosByIds(final String ids) {
        if(StringUtils.isBlank(ids)){
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
        try {
            return this.jt.query(sql.toString(), paramList.toArray(), CONFIG_INFO_ROW_MAPPER);
        } catch (EmptyResultDataAccessException e) { // 表明数据不存在, 返回null
            return null;
        } catch (CannotGetJdbcConnectionException e) {
            fatalLog.error("[db-error] " + e.toString(), e);
            throw e;
        }
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
        try {
            List<String> configTagList = this.selectTagByConfig(dataId, group, tenant);
            ConfigAdvanceInfo configAdvance = this.jt.queryForObject(
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
        } catch (EmptyResultDataAccessException e) { // 表明数据不存在, 返回null
            return null;
        } catch (CannotGetJdbcConnectionException e) {
            fatalLog.error("[db-error] " + e.toString(), e);
            throw e;
        }
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
        try {
            List<String> configTagList = this.selectTagByConfig(dataId, group, tenant);
            ConfigAllInfo configAdvance = this.jt.queryForObject(
                "SELECT ID,data_id,group_id,tenant_id,app_name,content,md5,gmt_create,gmt_modified,src_user,src_ip,c_desc,c_use,effect,type,c_schema FROM config_info WHERE data_id=? AND group_id=? AND tenant_id=?",
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
        } catch (EmptyResultDataAccessException e) { // 表明数据不存在, 返回null
            return null;
        } catch (CannotGetJdbcConnectionException e) {
            fatalLog.error("[db-error] " + e.toString(), e);
            throw e;
        }
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
        String appNameTmp = StringUtils.isBlank(configInfo.getAppName()) ? StringUtils.EMPTY : configInfo.getAppName();
        String tenantTmp = StringUtils.isBlank(configInfo.getTenant()) ? StringUtils.EMPTY : configInfo.getTenant();
        final String md5Tmp = MD5.getInstance().getMD5String(configInfo.getContent());
        try {
            jt.update(
                "INSERT INTO his_config_info (id,data_id,group_id,tenant_id,app_name,content,md5,src_ip,src_user,gmt_modified,op_type) VALUES(?,?,?,?,?,?,?,?,?,?,?)",
                id, configInfo.getDataId(), configInfo.getGroup(), tenantTmp, appNameTmp, configInfo.getContent(),
                md5Tmp, srcIp, srcUser, time, ops);
        } catch (DataAccessException e) {
            fatalLog.error("[db-error] " + e.toString(), e);
            throw e;
        }
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
        try {
            page = helper.fetchPage(this.jt, sqlCountRows, sqlFetchRows, new Object[]{dataId, group, tenantTmp},
                pageNo,
                pageSize, HISTORY_LIST_ROW_MAPPER);
        } catch (DataAccessException e) {
            fatalLog.error("[list-config-history] error, dataId:{}, group:{}", new Object[]{dataId, group}, e);
            throw e;
        }
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
            jt.update(
                "INSERT INTO app_configdata_relation_subs(data_id,group_id,app_name,gmt_modified) VALUES(?,?,?,?)",
                dataId, group, appNameTmp, date);
        } catch (CannotGetJdbcConnectionException e) {
            fatalLog.error("[db-error] " + e.toString(), e);
            throw e;
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
            jt.update(
                "UPDATE app_configdata_relation_subs SET gmt_modified=? WHERE data_id=? AND group_id=? AND app_name=?",
                time, dataId, group, appNameTmp);
        } catch (CannotGetJdbcConnectionException e) {
            fatalLog.error("[db-error] " + e.toString(), e);
            throw e;
        }
    }

    public ConfigHistoryInfo detailConfigHistory(Long nid) {
        String sqlFetchRows
            = "SELECT nid,data_id,group_id,tenant_id,app_name,content,md5,src_user,src_ip,op_type,gmt_create,gmt_modified FROM his_config_info WHERE nid = ?";
        try {
            ConfigHistoryInfo historyInfo = jt.queryForObject(sqlFetchRows, new Object[]{nid},
                HISTORY_DETAIL_ROW_MAPPER);
            return historyInfo;
        } catch (DataAccessException e) {
            fatalLog.error("[list-config-history] error, nid:{}", new Object[]{nid}, e);
            throw e;
        }
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
            jt.update(
                "INSERT INTO tenant_info(kp,tenant_id,tenant_name,tenant_desc,create_source,gmt_create,gmt_modified) VALUES(?,?,?,?,?,?,?)",
                kp, tenantId, tenantName, tenantDesc, createResoure, time, time);
        } catch (DataAccessException e) {
            fatalLog.error("[db-error] " + e.toString(), e);
            throw e;
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
            jt.update(
                "UPDATE tenant_info SET tenant_name = ?, tenant_desc = ?, gmt_modified= ? WHERE kp=? AND tenant_id=?",
                tenantName, tenantDesc, System.currentTimeMillis(), kp, tenantId);
        } catch (DataAccessException e) {
            fatalLog.error("[db-error] " + e.toString(), e);
            throw e;
        }
    }

    public List<TenantInfo> findTenantByKp(String kp) {
        String sql = "SELECT tenant_id,tenant_name,tenant_desc FROM tenant_info WHERE kp=?";
        try {
            return this.jt.query(sql, new Object[]{kp}, TENANT_INFO_ROW_MAPPER);
        } catch (CannotGetJdbcConnectionException e) {
            fatalLog.error("[db-error] " + e.toString(), e);
            throw e;
        } catch (EmptyResultDataAccessException e) {
            return Collections.emptyList();
        } catch (Exception e) {
            fatalLog.error("[db-other-error]" + e.getMessage(), e);
            throw new RuntimeException(e);
        }
    }

    public TenantInfo findTenantByKp(String kp, String tenantId) {
        String sql = "SELECT tenant_id,tenant_name,tenant_desc FROM tenant_info WHERE kp=? AND tenant_id=?";
        try {
            return this.jt.queryForObject(sql, new Object[]{kp, tenantId}, TENANT_INFO_ROW_MAPPER);
        } catch (CannotGetJdbcConnectionException e) {
            fatalLog.error("[db-error] " + e.toString(), e);
            throw e;
        } catch (EmptyResultDataAccessException e) {
            return null;
        } catch (Exception e) {
            fatalLog.error("[db-other-error]" + e.getMessage(), e);
            throw new RuntimeException(e);
        }
    }

    public void removeTenantInfoAtomic(final String kp, final String tenantId) {
        try {
            jt.update("DELETE FROM tenant_info WHERE kp=? AND tenant_id=?", kp, tenantId);
        } catch (CannotGetJdbcConnectionException e) {
            fatalLog.error("[db-error] " + e.toString(), e);
            throw e;
        }
    }

    public User findUserByUsername(String username) {
        String sql = "SELECT username,password FROM users WHERE username=? ";
        try {
            return this.jt.queryForObject(sql, new Object[]{username}, USER_ROW_MAPPER);
        } catch (CannotGetJdbcConnectionException e) {
            fatalLog.error("[db-error] " + e.toString(), e);
            throw e;
        } catch (EmptyResultDataAccessException e) {
            return null;
        } catch (Exception e) {
            fatalLog.error("[db-other-error]" + e.getMessage(), e);
            throw new RuntimeException(e);
        }
    }

    /**
     * 更新用户密码
     */
    public void updateUserPassword(String username, String password) {
        try {
            jt.update(
                "UPDATE users SET password = ? WHERE username=?",
                password, username);
        } catch (CannotGetJdbcConnectionException e) {
            fatalLog.error("[db-error] " + e.toString(), e);
            throw e;
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
        try {
            Page<ConfigInfoWrapper> page = helper.fetchPageLimit(jt, sqlCountRows, sqlFetchRows, new Object[]{
                (pageNo - 1) * pageSize, pageSize}, pageNo, pageSize, CONFIG_INFO_WRAPPER_ROW_MAPPER);

            return page.getPageItems();
        } catch (CannotGetJdbcConnectionException e) {
            fatalLog.error("[db-error] " + e.toString(), e);
            throw e;
        }
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
        try {
            return this.jt
                .queryForObject(
                    "SELECT ID,data_id,group_id,tenant_id,app_name,content,gmt_modified,md5 FROM config_info WHERE data_id=? AND group_id=? AND tenant_id=?",
                    new Object[]{dataId, group, tenantTmp}, CONFIG_INFO_WRAPPER_ROW_MAPPER);
        } catch (EmptyResultDataAccessException e) {
            return null;
        } catch (CannotGetJdbcConnectionException e) {
            fatalLog.error("[db-error] " + e.toString(), e);
            throw e;
        }
    }

    public boolean isExistTable(String tableName) {
        String sql = "SELECT COUNT(*) FROM " + tableName;
        try {
            jt.queryForObject(sql, Integer.class);
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
            Page<PersistService.ConfigInfoWrapper> page = findAllConfigInfoForDumpAll(
                pageNo, perPageSize);
            if (page != null) {
                for (PersistService.ConfigInfoWrapper cf : page.getPageItems()) {
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
                                    new Object[]{
                                        cf.getDataId(),
                                        cf.getGroup(),
                                        new Timestamp(cf
                                            .getLastModified())});
                        }
                    } else {
                        if (!md5InDb.equals(md5)) {
                            try {
                                updateMd5(cf.getDataId(), cf.getGroup(), tenant, md5,
                                    new Timestamp(cf.getLastModified()));
                            } catch (Exception e) {
                                LogUtil.defaultLog.error("[completeMd5-error] datId:{} group:{} lastModified:{}",
                                    new Object[]{cf.getDataId(), cf.getGroup(),
                                        new Timestamp(cf.getLastModified())});
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
        try {
            return this.jt.query(SQL_FIND_ALL_CONFIG_INFO + where, paramList.toArray(), CONFIG_ALL_INFO_ROW_MAPPER);
        } catch (CannotGetJdbcConnectionException e) {
            fatalLog.error("[db-error] " + e.toString(), e);
            throw e;
        }
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
        Integer result = this.jt.queryForObject(SQL_TENANT_INFO_COUNT_BY_TENANT_ID, new String[]{tenantId}, Integer.class);
        if (result == null) {
            return 0;
        }
        return result.intValue();
    }


    static final TenantInfoRowMapper TENANT_INFO_ROW_MAPPER = new TenantInfoRowMapper();

    static final UserRowMapper USER_ROW_MAPPER = new UserRowMapper();

    static final ConfigInfoWrapperRowMapper CONFIG_INFO_WRAPPER_ROW_MAPPER = new ConfigInfoWrapperRowMapper();

    static final ConfigKeyRowMapper CONFIG_KEY_ROW_MAPPER = new ConfigKeyRowMapper();

    static final ConfigInfoBetaWrapperRowMapper CONFIG_INFO_BETA_WRAPPER_ROW_MAPPER
        = new ConfigInfoBetaWrapperRowMapper();

    static final ConfigInfoTagWrapperRowMapper CONFIG_INFO_TAG_WRAPPER_ROW_MAPPER = new ConfigInfoTagWrapperRowMapper();

    static final ConfigInfoRowMapper CONFIG_INFO_ROW_MAPPER = new ConfigInfoRowMapper();

    static final ConfigAdvanceInfoRowMapper CONFIG_ADVANCE_INFO_ROW_MAPPER = new ConfigAdvanceInfoRowMapper();

    static final ConfigAllInfoRowMapper CONFIG_ALL_INFO_ROW_MAPPER = new ConfigAllInfoRowMapper();

    static final ConfigInfo4BetaRowMapper CONFIG_INFO4BETA_ROW_MAPPER = new ConfigInfo4BetaRowMapper();

    static final ConfigInfo4TagRowMapper CONFIG_INFO4TAG_ROW_MAPPER = new ConfigInfo4TagRowMapper();

    static final ConfigInfoBaseRowMapper CONFIG_INFO_BASE_ROW_MAPPER = new ConfigInfoBaseRowMapper();

    static final ConfigInfoAggrRowMapper CONFIG_INFO_AGGR_ROW_MAPPER = new ConfigInfoAggrRowMapper();

    static final ConfigInfoChangedRowMapper CONFIG_INFO_CHANGED_ROW_MAPPER = new ConfigInfoChangedRowMapper();

    static final ConfigHistoryRowMapper HISTORY_LIST_ROW_MAPPER = new ConfigHistoryRowMapper();

    static final ConfigHistoryDetailRowMapper HISTORY_DETAIL_ROW_MAPPER = new ConfigHistoryDetailRowMapper();

    private static String PATTERN_STR = "*";
    private final static int QUERY_LIMIT_SIZE = 50;
    private JdbcTemplate jt;
    private TransactionTemplate tjt;

}
