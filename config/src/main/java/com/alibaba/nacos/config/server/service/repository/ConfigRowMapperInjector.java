/*
 * Copyright 1999-2023 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.config.server.service.repository;

import com.alibaba.nacos.config.server.model.ConfigAdvanceInfo;
import com.alibaba.nacos.config.server.model.ConfigAllInfo;
import com.alibaba.nacos.config.server.model.ConfigHistoryInfo;
import com.alibaba.nacos.config.server.model.ConfigInfo;
import com.alibaba.nacos.config.server.model.ConfigInfo4Beta;
import com.alibaba.nacos.config.server.model.ConfigInfo4Tag;
import com.alibaba.nacos.config.server.model.ConfigInfoBase;
import com.alibaba.nacos.config.server.model.ConfigInfoBetaWrapper;
import com.alibaba.nacos.config.server.model.ConfigInfoChanged;
import com.alibaba.nacos.config.server.model.ConfigInfoGrayWrapper;
import com.alibaba.nacos.config.server.model.ConfigInfoStateWrapper;
import com.alibaba.nacos.config.server.model.ConfigInfoTagWrapper;
import com.alibaba.nacos.config.server.model.ConfigInfoWrapper;
import com.alibaba.nacos.config.server.model.ConfigKey;
import com.alibaba.nacos.persistence.repository.RowMapperManager;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;

import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Config row mapper injector.
 *
 * @author xiweng.yy
 */
@Component
public class ConfigRowMapperInjector {
    
    public static final RowMapper<ConfigInfoWrapper> CONFIG_INFO_WRAPPER_ROW_MAPPER = new ConfigInfoWrapperRowMapper();
    
    public static final ConfigInfoStateWrapperRowMapper CONFIG_INFO_STATE_WRAPPER_ROW_MAPPER = new ConfigInfoStateWrapperRowMapper();
    
    public static final RowMapper<ConfigKey> CONFIG_KEY_ROW_MAPPER = new ConfigKeyRowMapper();
    
    public static final ConfigInfoBetaWrapperRowMapper CONFIG_INFO_BETA_WRAPPER_ROW_MAPPER = new ConfigInfoBetaWrapperRowMapper();
    
    public static final ConfigInfoTagWrapperRowMapper CONFIG_INFO_TAG_WRAPPER_ROW_MAPPER = new ConfigInfoTagWrapperRowMapper();
    
    public static final ConfigInfoGrayWrapperRowMapper CONFIG_INFO_GRAY_WRAPPER_ROW_MAPPER = new ConfigInfoGrayWrapperRowMapper();
    
    public static final ConfigInfoRowMapper CONFIG_INFO_ROW_MAPPER = new ConfigInfoRowMapper();
    
    public static final ConfigAdvanceInfoRowMapper CONFIG_ADVANCE_INFO_ROW_MAPPER = new ConfigAdvanceInfoRowMapper();
    
    public static final ConfigAllInfoRowMapper CONFIG_ALL_INFO_ROW_MAPPER = new ConfigAllInfoRowMapper();
    
    public static final ConfigInfo4BetaRowMapper CONFIG_INFO4BETA_ROW_MAPPER = new ConfigInfo4BetaRowMapper();
    
    public static final ConfigInfo4TagRowMapper CONFIG_INFO4TAG_ROW_MAPPER = new ConfigInfo4TagRowMapper();
    
    public static final ConfigInfoBaseRowMapper CONFIG_INFO_BASE_ROW_MAPPER = new ConfigInfoBaseRowMapper();
    
    public static final ConfigInfoChangedRowMapper CONFIG_INFO_CHANGED_ROW_MAPPER = new ConfigInfoChangedRowMapper();
    
    public static final ConfigHistoryRowMapper HISTORY_LIST_ROW_MAPPER = new ConfigHistoryRowMapper();
    
    public static final ConfigHistoryDetailRowMapper HISTORY_DETAIL_ROW_MAPPER = new ConfigHistoryDetailRowMapper();
    
    static {
        injectConfigRowMapper();
    }
    
    public ConfigRowMapperInjector() {
    }
    
    private static void injectConfigRowMapper() {
        // CONFIG_INFO_WRAPPER_ROW_MAPPER
        
        RowMapperManager.registerRowMapper(
                ConfigRowMapperInjector.CONFIG_INFO_WRAPPER_ROW_MAPPER.getClass().getCanonicalName(),
                ConfigRowMapperInjector.CONFIG_INFO_WRAPPER_ROW_MAPPER);
        
        // CONFIG_INFO_STATE_WRAPPER_ROW_MAPPER
        RowMapperManager.registerRowMapper(
                ConfigRowMapperInjector.CONFIG_INFO_STATE_WRAPPER_ROW_MAPPER.getClass().getCanonicalName(),
                ConfigRowMapperInjector.CONFIG_INFO_STATE_WRAPPER_ROW_MAPPER);
        
        // CONFIG_KEY_ROW_MAPPER
        
        RowMapperManager.registerRowMapper(ConfigRowMapperInjector.CONFIG_KEY_ROW_MAPPER.getClass().getCanonicalName(),
                ConfigRowMapperInjector.CONFIG_KEY_ROW_MAPPER);
        
        // CONFIG_INFO_BETA_WRAPPER_ROW_MAPPER
        
        RowMapperManager.registerRowMapper(
                ConfigRowMapperInjector.CONFIG_INFO_BETA_WRAPPER_ROW_MAPPER.getClass().getCanonicalName(),
                ConfigRowMapperInjector.CONFIG_INFO_BETA_WRAPPER_ROW_MAPPER);
        
        // CONFIG_INFO_TAG_WRAPPER_ROW_MAPPER
        
        RowMapperManager.registerRowMapper(
                ConfigRowMapperInjector.CONFIG_INFO_TAG_WRAPPER_ROW_MAPPER.getClass().getCanonicalName(),
                ConfigRowMapperInjector.CONFIG_INFO_TAG_WRAPPER_ROW_MAPPER);
        
        // CONFIG_INFO_ROW_MAPPER
        
        RowMapperManager.registerRowMapper(ConfigRowMapperInjector.CONFIG_INFO_ROW_MAPPER.getClass().getCanonicalName(),
                ConfigRowMapperInjector.CONFIG_INFO_ROW_MAPPER);
        
        // CONFIG_ADVANCE_INFO_ROW_MAPPER
        
        RowMapperManager.registerRowMapper(
                ConfigRowMapperInjector.CONFIG_ADVANCE_INFO_ROW_MAPPER.getClass().getCanonicalName(),
                ConfigRowMapperInjector.CONFIG_ADVANCE_INFO_ROW_MAPPER);
        
        // CONFIG_ALL_INFO_ROW_MAPPER
        
        RowMapperManager.registerRowMapper(
                ConfigRowMapperInjector.CONFIG_ALL_INFO_ROW_MAPPER.getClass().getCanonicalName(),
                ConfigRowMapperInjector.CONFIG_ALL_INFO_ROW_MAPPER);
        
        // CONFIG_INFO4BETA_ROW_MAPPER
        
        RowMapperManager.registerRowMapper(
                ConfigRowMapperInjector.CONFIG_INFO4BETA_ROW_MAPPER.getClass().getCanonicalName(),
                ConfigRowMapperInjector.CONFIG_INFO4BETA_ROW_MAPPER);
        
        // CONFIG_INFO4TAG_ROW_MAPPER
        
        RowMapperManager.registerRowMapper(
                ConfigRowMapperInjector.CONFIG_INFO4TAG_ROW_MAPPER.getClass().getCanonicalName(),
                ConfigRowMapperInjector.CONFIG_INFO4TAG_ROW_MAPPER);
        
        // CONFIG_INFO_BASE_ROW_MAPPER
        
        RowMapperManager.registerRowMapper(
                ConfigRowMapperInjector.CONFIG_INFO_BASE_ROW_MAPPER.getClass().getCanonicalName(),
                ConfigRowMapperInjector.CONFIG_INFO_BASE_ROW_MAPPER);
        
        // CONFIG_INFO_CHANGED_ROW_MAPPER
        
        RowMapperManager.registerRowMapper(
                ConfigRowMapperInjector.CONFIG_INFO_CHANGED_ROW_MAPPER.getClass().getCanonicalName(),
                ConfigRowMapperInjector.CONFIG_INFO_CHANGED_ROW_MAPPER);
        
        // HISTORY_LIST_ROW_MAPPER
        
        RowMapperManager.registerRowMapper(
                ConfigRowMapperInjector.HISTORY_LIST_ROW_MAPPER.getClass().getCanonicalName(),
                ConfigRowMapperInjector.HISTORY_LIST_ROW_MAPPER);
        
        // HISTORY_DETAIL_ROW_MAPPER
        
        RowMapperManager.registerRowMapper(
                ConfigRowMapperInjector.HISTORY_DETAIL_ROW_MAPPER.getClass().getCanonicalName(),
                ConfigRowMapperInjector.HISTORY_DETAIL_ROW_MAPPER);
    }
    
    public static final class ConfigInfoWrapperRowMapper implements RowMapper<ConfigInfoWrapper> {
        
        @Override
        public ConfigInfoWrapper mapRow(ResultSet rs, int rowNum) throws SQLException {
            ConfigInfoWrapper info = new ConfigInfoWrapper();
            
            info.setDataId(rs.getString("data_id"));
            info.setGroup(rs.getString("group_id"));
            info.setTenant(rs.getString("tenant_id"));
            info.setAppName(rs.getString("app_name"));
            
            try {
                info.setType(rs.getString("type"));
            } catch (SQLException ignore) {
            }
            
            try {
                info.setContent(rs.getString("content"));
            } catch (SQLException ignore) {
            }
            try {
                info.setId(rs.getLong("id"));
            } catch (SQLException ignore) {
            }
            try {
                info.setLastModified(rs.getTimestamp("gmt_modified").getTime());
            } catch (SQLException ignore) {
            }
            try {
                info.setMd5(rs.getString("md5"));
            } catch (SQLException ignore) {
            }
            try {
                info.setEncryptedDataKey(rs.getString("encrypted_data_key"));
            } catch (SQLException ignore) {
            
            }
            return info;
        }
    }
    
    public static final class ConfigInfoStateWrapperRowMapper implements RowMapper<ConfigInfoStateWrapper> {
        
        @Override
        public ConfigInfoStateWrapper mapRow(ResultSet rs, int rowNum) throws SQLException {
            ConfigInfoStateWrapper info = new ConfigInfoStateWrapper();
            
            info.setDataId(rs.getString("data_id"));
            info.setGroup(rs.getString("group_id"));
            info.setTenant(rs.getString("tenant_id"));
            info.setLastModified(rs.getTimestamp("gmt_modified").getTime());
            try {
                info.setMd5(rs.getString("md5"));
            } catch (SQLException e) {
                // ignore
            }
            try {
                info.setId(rs.getLong("id"));
            } catch (SQLException e) {
                // ignore
            }
            
            return info;
        }
    }
    
    public static final class ConfigInfoBetaWrapperRowMapper implements RowMapper<ConfigInfoBetaWrapper> {
        
        @Override
        public ConfigInfoBetaWrapper mapRow(ResultSet rs, int rowNum) throws SQLException {
            ConfigInfoBetaWrapper info = new ConfigInfoBetaWrapper();
            
            info.setDataId(rs.getString("data_id"));
            info.setGroup(rs.getString("group_id"));
            info.setTenant(rs.getString("tenant_id"));
            info.setAppName(rs.getString("app_name"));
            info.setBetaIps(rs.getString("beta_ips"));
            
            try {
                info.setContent(rs.getString("content"));
            } catch (SQLException ignore) {
            }
            try {
                info.setId(rs.getLong("id"));
            } catch (SQLException ignore) {
            }
            try {
                info.setLastModified(rs.getTimestamp("gmt_modified").getTime());
            } catch (SQLException ignore) {
            }
            try {
                info.setMd5(rs.getString("md5"));
            } catch (SQLException ignore) {
            }
            try {
                info.setEncryptedDataKey(rs.getString("encrypted_data_key"));
            } catch (SQLException ignore) {
            }
            return info;
        }
    }
    
    public static final class ConfigInfoTagWrapperRowMapper implements RowMapper<ConfigInfoTagWrapper> {
        
        @Override
        public ConfigInfoTagWrapper mapRow(ResultSet rs, int rowNum) throws SQLException {
            ConfigInfoTagWrapper info = new ConfigInfoTagWrapper();
            
            info.setDataId(rs.getString("data_id"));
            info.setGroup(rs.getString("group_id"));
            info.setTenant(rs.getString("tenant_id"));
            info.setTag(rs.getString("tag_id"));
            info.setAppName(rs.getString("app_name"));
            
            try {
                info.setContent(rs.getString("content"));
            } catch (SQLException ignore) {
            }
            try {
                info.setId(rs.getLong("id"));
            } catch (SQLException ignore) {
            }
            try {
                info.setLastModified(rs.getTimestamp("gmt_modified").getTime());
            } catch (SQLException ignore) {
            }
            try {
                info.setMd5(rs.getString("md5"));
            } catch (SQLException ignore) {
            }
            return info;
        }
    }
    
    public static final class ConfigInfoGrayWrapperRowMapper implements RowMapper<ConfigInfoGrayWrapper> {
        
        @Override
        public ConfigInfoGrayWrapper mapRow(ResultSet rs, int rowNum) throws SQLException {
            ConfigInfoGrayWrapper info = new ConfigInfoGrayWrapper();
            
            info.setDataId(rs.getString("data_id"));
            info.setGroup(rs.getString("group_id"));
            info.setTenant(rs.getString("tenant_id"));
            info.setGrayName(rs.getString("gray_name"));
            info.setGrayRule(rs.getString("gray_rule"));
            info.setAppName(rs.getString("app_name"));
            
            try {
                info.setContent(rs.getString("content"));
            } catch (SQLException ignore) {
            }
            try {
                info.setId(rs.getLong("id"));
            } catch (SQLException ignore) {
            }
            try {
                info.setLastModified(rs.getTimestamp("gmt_modified").getTime());
            } catch (SQLException ignore) {
            }
            try {
                info.setMd5(rs.getString("md5"));
            } catch (SQLException ignore) {
            }
            try {
                info.setEncryptedDataKey(rs.getString("encrypted_data_key"));
            } catch (SQLException ignore) {
            }
    
            try {
                info.setSrcUser(rs.getString("src_user"));
            } catch (SQLException ignore) {
            }
            return info;
        }
    }
    
    public static final class ConfigInfoRowMapper implements RowMapper<ConfigInfo> {
        
        @Override
        public ConfigInfo mapRow(ResultSet rs, int rowNum) throws SQLException {
            ConfigInfo info = new ConfigInfo();
            
            info.setDataId(rs.getString("data_id"));
            info.setGroup(rs.getString("group_id"));
            info.setTenant(rs.getString("tenant_id"));
            info.setAppName(rs.getString("app_name"));
            
            try {
                info.setContent(rs.getString("content"));
            } catch (SQLException ignore) {
            }
            try {
                info.setMd5(rs.getString("md5"));
            } catch (SQLException ignore) {
            }
            try {
                info.setId(rs.getLong("id"));
            } catch (SQLException ignore) {
            }
            try {
                info.setType(rs.getString("type"));
            } catch (SQLException ignore) {
            }
            try {
                info.setEncryptedDataKey(rs.getString("encrypted_data_key"));
            } catch (SQLException ignore) {
            }
            return info;
        }
    }
    
    public static final class ConfigKeyRowMapper implements RowMapper<ConfigKey> {
        
        @Override
        public ConfigKey mapRow(ResultSet rs, int rowNum) throws SQLException {
            ConfigKey info = new ConfigKey();
            
            info.setDataId(rs.getString("data_id"));
            info.setGroup(rs.getString("group_id"));
            info.setAppName(rs.getString("app_name"));
            
            return info;
        }
    }
    
    public static final class ConfigAdvanceInfoRowMapper implements RowMapper<ConfigAdvanceInfo> {
        
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
    
    public static final class ConfigAllInfoRowMapper implements RowMapper<ConfigAllInfo> {
        
        @Override
        public ConfigAllInfo mapRow(ResultSet rs, int rowNum) throws SQLException {
            ConfigAllInfo info = new ConfigAllInfo();
            info.setDataId(rs.getString("data_id"));
            info.setGroup(rs.getString("group_id"));
            info.setTenant(rs.getString("tenant_id"));
            info.setAppName(rs.getString("app_name"));
            try {
                info.setContent(rs.getString("content"));
            } catch (SQLException ignore) {
            }
            try {
                info.setMd5(rs.getString("md5"));
            } catch (SQLException ignore) {
            }
            try {
                info.setId(rs.getLong("id"));
            } catch (SQLException ignore) {
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
            try {
                info.setEncryptedDataKey(rs.getString("encrypted_data_key"));
            } catch (SQLException ignore) {
            
            }
            return info;
        }
    }
    
    public static final class ConfigInfo4BetaRowMapper implements RowMapper<ConfigInfo4Beta> {
        
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
            } catch (SQLException ignore) {
            }
            try {
                info.setId(rs.getLong("id"));
            } catch (SQLException ignore) {
            }
            try {
                info.setMd5(rs.getString("md5"));
            } catch (SQLException ignore) {
            }
            return info;
        }
    }
    
    public static final class ConfigInfo4TagRowMapper implements RowMapper<ConfigInfo4Tag> {
        
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
            } catch (SQLException ignore) {
            }
            try {
                info.setId(rs.getLong("id"));
            } catch (SQLException ignore) {
            }
            try {
                info.setMd5(rs.getString("md5"));
            } catch (SQLException ignore) {
            }
            return info;
        }
    }
    
    public static final class ConfigInfoBaseRowMapper implements RowMapper<ConfigInfoBase> {
        
        @Override
        public ConfigInfoBase mapRow(ResultSet rs, int rowNum) throws SQLException {
            ConfigInfoBase info = new ConfigInfoBase();
            
            info.setDataId(rs.getString("data_id"));
            info.setGroup(rs.getString("group_id"));
            
            try {
                info.setContent(rs.getString("content"));
            } catch (SQLException ignore) {
            }
            try {
                info.setId(rs.getLong("id"));
            } catch (SQLException ignore) {
            }
            return info;
        }
    }
    
    public static final class ConfigInfoChangedRowMapper implements RowMapper<ConfigInfoChanged> {
        
        @Override
        public ConfigInfoChanged mapRow(ResultSet rs, int rowNum) throws SQLException {
            ConfigInfoChanged info = new ConfigInfoChanged();
            info.setDataId(rs.getString("data_id"));
            info.setGroup(rs.getString("group_id"));
            info.setTenant(rs.getString("tenant_id"));
            return info;
        }
    }
    
    public static final class ConfigHistoryRowMapper implements RowMapper<ConfigHistoryInfo> {
        
        @Override
        public ConfigHistoryInfo mapRow(ResultSet rs, int rowNum) throws SQLException {
            ConfigHistoryInfo configHistoryInfo = new ConfigHistoryInfo();
            configHistoryInfo.setId(rs.getLong("nid"));
            configHistoryInfo.setDataId(rs.getString("data_id"));
            configHistoryInfo.setGroup(rs.getString("group_id"));
            configHistoryInfo.setTenant(rs.getString("tenant_id"));
            configHistoryInfo.setAppName(rs.getString("app_name"));
            configHistoryInfo.setSrcIp(rs.getString("src_ip"));
            configHistoryInfo.setSrcUser(rs.getString("src_user"));
            configHistoryInfo.setOpType(rs.getString("op_type"));
            configHistoryInfo.setPublishType(rs.getString("publish_type"));
            configHistoryInfo.setExtInfo(rs.getString("ext_info"));
            configHistoryInfo.setCreatedTime(rs.getTimestamp("gmt_create"));
            configHistoryInfo.setLastModifiedTime(rs.getTimestamp("gmt_modified"));
            return configHistoryInfo;
        }
    }
    
    public static final class ConfigHistoryDetailRowMapper implements RowMapper<ConfigHistoryInfo> {
        
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
            configHistoryInfo.setPublishType(rs.getString("publish_type"));
            configHistoryInfo.setExtInfo(rs.getString("ext_info"));
            configHistoryInfo.setCreatedTime(rs.getTimestamp("gmt_create"));
            configHistoryInfo.setLastModifiedTime(rs.getTimestamp("gmt_modified"));
            try {
                configHistoryInfo.setEncryptedDataKey(rs.getString("encrypted_data_key"));
            } catch (SQLException ignore) {
            
            }
            return configHistoryInfo;
        }
    }
}
