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

package com.alibaba.nacos.config.server.service.repository;

import com.alibaba.nacos.config.server.model.ConfigAdvanceInfo;
import com.alibaba.nacos.config.server.model.ConfigAllInfo;
import com.alibaba.nacos.config.server.model.ConfigHistoryInfo;
import com.alibaba.nacos.config.server.model.ConfigInfo;
import com.alibaba.nacos.config.server.model.ConfigInfoBase;
import com.alibaba.nacos.config.server.model.ConfigInfoChanged;
import com.alibaba.nacos.config.server.model.ConfigInfoGrayWrapper;
import com.alibaba.nacos.config.server.model.ConfigInfoStateWrapper;
import com.alibaba.nacos.config.server.model.ConfigInfoWrapper;
import com.alibaba.nacos.config.server.model.ConfigKey;
import com.alibaba.nacos.config.server.service.repository.ConfigRowMapperInjector.ConfigHistoryDetailRowMapper;
import com.alibaba.nacos.config.server.service.repository.ConfigRowMapperInjector.ConfigInfoChangedRowMapper;
import com.alibaba.nacos.config.server.service.repository.ConfigRowMapperInjector.ConfigInfoStateWrapperRowMapper;
import com.alibaba.nacos.persistence.repository.RowMapperManager;
import com.mysql.cj.jdbc.result.ResultSetImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.sql.SQLException;
import java.sql.Timestamp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.eq;

@ExtendWith(SpringExtension.class)
class ConfigRowMapperInjectorTest {
    
    @Test
    void testInit() {
        ConfigRowMapperInjector configRowMapperInjector = new ConfigRowMapperInjector();
        assertEquals(ConfigRowMapperInjector.CONFIG_INFO_WRAPPER_ROW_MAPPER,
            RowMapperManager.getRowMapper(
                ConfigRowMapperInjector.CONFIG_INFO_WRAPPER_ROW_MAPPER.getClass()
                    .getCanonicalName()));
    }
    
    @Test
    void testConfigAdvanceInfoRowMapper() throws SQLException {
        ConfigAdvanceInfo preConfig = new ConfigAdvanceInfo();
        preConfig.setModifyTime(System.currentTimeMillis());
        preConfig.setCreateTime(System.currentTimeMillis());
        preConfig.setCreateUser("user12345");
        preConfig.setCreateIp("1267890");
        preConfig.setDesc("desc23");
        preConfig.setUse("us345t");
        preConfig.setEffect("effect233");
        preConfig.setType("type132435");
        preConfig.setSchema("scheme344");
        ResultSetImpl resultSet = Mockito.mock(ResultSetImpl.class);
        Mockito.when(resultSet.getString(eq("src_ip"))).thenReturn(preConfig.getCreateIp());
        Mockito.when(resultSet.getString(eq("type"))).thenReturn(preConfig.getType());
        Mockito.when(resultSet.getString(eq("c_desc"))).thenReturn(preConfig.getDesc());
        Mockito.when(resultSet.getString(eq("effect"))).thenReturn(preConfig.getEffect());
        Mockito.when(resultSet.getString(eq("src_user"))).thenReturn(preConfig.getCreateUser());
        Mockito.when(resultSet.getTimestamp(eq("gmt_modified")))
            .thenReturn(new Timestamp(preConfig.getModifyTime()));
        Mockito.when(resultSet.getTimestamp(eq("gmt_create")))
            .thenReturn(new Timestamp(preConfig.getCreateTime()));
        Mockito.when(resultSet.getString(eq("c_use"))).thenReturn(preConfig.getUse());
        Mockito.when(resultSet.getString(eq("c_schema"))).thenReturn(preConfig.getSchema());
        ConfigRowMapperInjector.ConfigAdvanceInfoRowMapper configInfoWrapperRowMapper =
            new ConfigRowMapperInjector.ConfigAdvanceInfoRowMapper();
        ConfigAdvanceInfo configInfoWrapper = configInfoWrapperRowMapper.mapRow(resultSet, 10);
        assertEquals(preConfig, configInfoWrapper);
    }
    
    @Test
    void testConfigAllInfoRowMapper() throws SQLException {
        ConfigAllInfo preConfig = new ConfigAllInfo();
        preConfig.setDataId("testDataId");
        preConfig.setGroup("group_id11");
        preConfig.setTenant("tenant_id11111");
        preConfig.setModifyTime(System.currentTimeMillis());
        preConfig.setCreateTime(System.currentTimeMillis());
        preConfig.setId(1243567898L);
        preConfig.setAppName("app_name11111");
        preConfig.setType("type55555");
        preConfig.setContent("content1123434t");
        preConfig.setMd5("md54567");
        preConfig.setEncryptedDataKey("encrypted_data_key1324");
        ResultSetImpl resultSet = Mockito.mock(ResultSetImpl.class);
        
        Mockito.when(resultSet.getString(eq("data_id"))).thenReturn(preConfig.getDataId());
        Mockito.when(resultSet.getString(eq("group_id"))).thenReturn(preConfig.getGroup());
        Mockito.when(resultSet.getString(eq("tenant_id"))).thenReturn(preConfig.getTenant());
        Mockito.when(resultSet.getString(eq("app_name"))).thenReturn(preConfig.getAppName());
        Mockito.when(resultSet.getString(eq("type"))).thenReturn(preConfig.getType());
        Mockito.when(resultSet.getString(eq("content"))).thenReturn(preConfig.getContent());
        Mockito.when(resultSet.getTimestamp(eq("gmt_modified")))
            .thenReturn(new Timestamp(preConfig.getModifyTime()));
        Mockito.when(resultSet.getTimestamp(eq("gmt_create")))
            .thenReturn(new Timestamp(preConfig.getCreateTime()));
        
        Mockito.when(resultSet.getLong(eq("id"))).thenReturn(preConfig.getId());
        Mockito.when(resultSet.getString(eq("md5"))).thenReturn(preConfig.getMd5());
        Mockito.when(resultSet.getString(eq("encrypted_data_key")))
            .thenReturn(preConfig.getEncryptedDataKey());
        ConfigRowMapperInjector.ConfigAllInfoRowMapper configInfoWrapperRowMapper =
            new ConfigRowMapperInjector.ConfigAllInfoRowMapper();
        
        ConfigAllInfo configInfoWrapper = configInfoWrapperRowMapper.mapRow(resultSet, 10);
        assertEquals(preConfig, configInfoWrapper);
    }
    
    @Test
    void testConfigInfoRowMapper() throws SQLException {
        
        ConfigInfo preConfig = new ConfigInfo();
        preConfig.setDataId("testDataId");
        preConfig.setGroup("group_id11");
        preConfig.setTenant("tenant_id11111");
        preConfig.setId(1243567898L);
        preConfig.setAppName("app_name11111");
        preConfig.setType("type55555");
        preConfig.setContent("content1123434t");
        preConfig.setMd5("md54567");
        preConfig.setEncryptedDataKey("encrypted_data_key1324");
        ResultSetImpl resultSet = Mockito.mock(ResultSetImpl.class);
        Mockito.when(resultSet.getString(eq("data_id"))).thenReturn(preConfig.getDataId());
        Mockito.when(resultSet.getString(eq("group_id"))).thenReturn(preConfig.getGroup());
        Mockito.when(resultSet.getString(eq("tenant_id"))).thenReturn(preConfig.getTenant());
        Mockito.when(resultSet.getString(eq("app_name"))).thenReturn(preConfig.getAppName());
        Mockito.when(resultSet.getString(eq("type"))).thenReturn(preConfig.getType());
        Mockito.when(resultSet.getString(eq("content"))).thenReturn(preConfig.getContent());
        Mockito.when(resultSet.getLong(eq("id"))).thenReturn(preConfig.getId());
        Mockito.when(resultSet.getString(eq("md5"))).thenReturn(preConfig.getMd5());
        Mockito.when(resultSet.getString(eq("encrypted_data_key")))
            .thenReturn(preConfig.getEncryptedDataKey());
        ConfigRowMapperInjector.ConfigInfoRowMapper configInfoWrapperRowMapper =
            new ConfigRowMapperInjector.ConfigInfoRowMapper();
        ConfigInfo configInfoWrapper = configInfoWrapperRowMapper.mapRow(resultSet, 10);
        assertEquals(preConfig, configInfoWrapper);
    }
    
    @Test
    void testConfigInfoRowMapperIgnoresMissingOptionalFields() throws SQLException {
        ResultSetImpl resultSet = Mockito.mock(ResultSetImpl.class);
        Mockito.when(resultSet.getString(eq("data_id"))).thenReturn("dataId");
        Mockito.when(resultSet.getString(eq("group_id"))).thenReturn("group");
        Mockito.when(resultSet.getString(eq("tenant_id"))).thenReturn("tenant");
        Mockito.when(resultSet.getString(eq("app_name"))).thenThrow(new SQLException("missing"));
        Mockito.when(resultSet.getString(eq("content"))).thenThrow(new SQLException("missing"));
        Mockito.when(resultSet.getString(eq("md5"))).thenThrow(new SQLException("missing"));
        Mockito.when(resultSet.getLong(eq("id"))).thenThrow(new SQLException("missing"));
        Mockito.when(resultSet.getString(eq("type"))).thenThrow(new SQLException("missing"));
        Mockito.when(resultSet.getString(eq("encrypted_data_key")))
            .thenThrow(new SQLException("missing"));
        Mockito.when(resultSet.getString(eq("c_desc"))).thenThrow(new SQLException("missing"));
        Mockito.when(resultSet.getString(eq("config_tags")))
            .thenThrow(new SQLException("missing"));
        Mockito.when(resultSet.getTimestamp(eq("gmt_modified")))
            .thenThrow(new SQLException("missing"));
        
        ConfigInfo configInfo =
            new ConfigRowMapperInjector.ConfigInfoRowMapper().mapRow(resultSet, 10);
        
        assertEquals("dataId", configInfo.getDataId());
        assertEquals("group", configInfo.getGroup());
        assertEquals("tenant", configInfo.getTenant());
        assertNull(configInfo.getAppName());
        assertNull(configInfo.getContent());
        assertNull(configInfo.getMd5());
        assertEquals(0L, configInfo.getId());
        assertNull(configInfo.getType());
        assertNull(configInfo.getEncryptedDataKey());
        assertNull(configInfo.getDesc());
        assertNull(configInfo.getConfigTags());
        assertNull(configInfo.getGmtModified());
    }
    
    @Test
    void testConfigInfoWrapperRowMapper() throws SQLException {
        
        ConfigInfoWrapper preConfig = new ConfigInfoWrapper();
        preConfig.setDataId("testDataId");
        preConfig.setGroup("group_id11");
        preConfig.setTenant("tenant_id11111");
        preConfig.setLastModified(System.currentTimeMillis());
        preConfig.setId(1243567898L);
        preConfig.setAppName("app_name11111");
        preConfig.setType("type55555");
        preConfig.setContent("content1123434t");
        preConfig.setMd5("md54567");
        preConfig.setEncryptedDataKey("encrypted_data_key1324");
        ResultSetImpl resultSet = Mockito.mock(ResultSetImpl.class);
        Mockito.when(resultSet.getString(eq("data_id"))).thenReturn(preConfig.getDataId());
        Mockito.when(resultSet.getString(eq("group_id"))).thenReturn(preConfig.getGroup());
        Mockito.when(resultSet.getString(eq("tenant_id"))).thenReturn(preConfig.getTenant());
        Mockito.when(resultSet.getString(eq("app_name"))).thenReturn(preConfig.getAppName());
        Mockito.when(resultSet.getString(eq("type"))).thenReturn(preConfig.getType());
        Mockito.when(resultSet.getString(eq("content"))).thenReturn(preConfig.getContent());
        Mockito.when(resultSet.getLong(eq("id"))).thenReturn(preConfig.getId());
        Mockito.when(resultSet.getTimestamp(eq("gmt_modified")))
            .thenReturn(new Timestamp(preConfig.getLastModified()));
        Mockito.when(resultSet.getString(eq("md5"))).thenReturn(preConfig.getMd5());
        Mockito.when(resultSet.getString(eq("encrypted_data_key")))
            .thenReturn(preConfig.getEncryptedDataKey());
        ConfigRowMapperInjector.ConfigInfoWrapperRowMapper configInfoWrapperRowMapper =
            new ConfigRowMapperInjector.ConfigInfoWrapperRowMapper();
        ConfigInfoWrapper configInfoWrapper = configInfoWrapperRowMapper.mapRow(resultSet, 10);
        assertEquals(preConfig, configInfoWrapper);
        
    }
    
    @Test
    void testConfigInfoBaseRowMapper() throws SQLException {
        
        ConfigInfoBase preConfig = new ConfigInfoBase();
        preConfig.setDataId("testDataId");
        preConfig.setGroup("group_id11");
        preConfig.setId(1243567898L);
        preConfig.setContent("content1123434t");
        ResultSetImpl resultSet = Mockito.mock(ResultSetImpl.class);
        Mockito.when(resultSet.getString(eq("data_id"))).thenReturn(preConfig.getDataId());
        Mockito.when(resultSet.getString(eq("group_id"))).thenReturn(preConfig.getGroup());
        Mockito.when(resultSet.getString(eq("content"))).thenReturn(preConfig.getContent());
        Mockito.when(resultSet.getLong(eq("id"))).thenReturn(preConfig.getId());
        ConfigRowMapperInjector.ConfigInfoBaseRowMapper configInfoWrapperRowMapper =
            new ConfigRowMapperInjector.ConfigInfoBaseRowMapper();
        ConfigInfoBase configInfoWrapper = configInfoWrapperRowMapper.mapRow(resultSet, 10);
        assertEquals(preConfig, configInfoWrapper);
        
    }
    
    @Test
    void testConfigInfoGrayRowMapper() throws SQLException {
        
        ConfigInfoGrayWrapper preConfig = new ConfigInfoGrayWrapper();
        preConfig.setDataId("testDataId");
        preConfig.setGroup("group_id11");
        preConfig.setContent("content1123434t");
        preConfig.setGrayName("grayName");
        preConfig.setGrayRule("rule12345");
        preConfig.setTenant("tenang34567890");
        preConfig.setAppName("app3456789");
        preConfig.setEncryptedDataKey("key12345");
        Timestamp timestamp = Timestamp.valueOf("2024-12-12 12:34:34");
        ResultSetImpl resultSet = Mockito.mock(ResultSetImpl.class);
        Mockito.when(resultSet.getString(eq("data_id"))).thenReturn(preConfig.getDataId());
        Mockito.when(resultSet.getString(eq("group_id"))).thenReturn(preConfig.getGroup());
        Mockito.when(resultSet.getString(eq("tenant_id"))).thenReturn(preConfig.getTenant());
        Mockito.when(resultSet.getString(eq("gray_name"))).thenReturn(preConfig.getGrayName());
        Mockito.when(resultSet.getString(eq("app_name"))).thenReturn(preConfig.getAppName());
        
        Mockito.when(resultSet.getString(eq("gray_rule"))).thenReturn(preConfig.getGrayRule());
        Mockito.when(resultSet.getTimestamp(eq("gmt_modified"))).thenReturn(timestamp);
        
        Mockito.when(resultSet.getString(eq("content"))).thenReturn(preConfig.getContent());
        Mockito.when(resultSet.getString(eq("app"))).thenReturn(preConfig.getAppName());
        Mockito.when(resultSet.getString(eq("encrypted_data_key")))
            .thenReturn(preConfig.getEncryptedDataKey());
        
        ConfigRowMapperInjector.ConfigInfoGrayWrapperRowMapper configInfoWrapperRowMapper =
            new ConfigRowMapperInjector.ConfigInfoGrayWrapperRowMapper();
        
        ConfigInfoGrayWrapper configInfoWrapper = configInfoWrapperRowMapper.mapRow(resultSet, 10);
        assertEquals(preConfig, configInfoWrapper);
        assertEquals(timestamp.getTime(), configInfoWrapper.getLastModified());
        
    }
    
    @Test
    void testConfigInfoChangedRowMapper() throws SQLException {
        
        ConfigInfoChanged preConfig = new ConfigInfoChanged();
        preConfig.setDataId("testDataId");
        preConfig.setGroup("group_id11");
        preConfig.setTenant("tenang34567890");
        ResultSetImpl resultSet = Mockito.mock(ResultSetImpl.class);
        Mockito.when(resultSet.getString(eq("data_id"))).thenReturn(preConfig.getDataId());
        Mockito.when(resultSet.getString(eq("group_id"))).thenReturn(preConfig.getGroup());
        Mockito.when(resultSet.getString(eq("tenant_id"))).thenReturn(preConfig.getTenant());
        ConfigInfoChangedRowMapper configInfoWrapperRowMapper = new ConfigInfoChangedRowMapper();
        ConfigInfoChanged configInfoWrapper = configInfoWrapperRowMapper.mapRow(resultSet, 10);
        assertEquals(preConfig, configInfoWrapper);
        
    }
    
    @Test
    void testConfigHistoryRowMapper() throws SQLException {
        
        ConfigHistoryInfo preConfig = new ConfigHistoryInfo();
        preConfig.setDataId("testDataId");
        preConfig.setGroup("group_id11");
        preConfig.setTenant("tenant_id11111");
        preConfig.setId(1243567898L);
        preConfig.setAppName("app_name11111");
        preConfig.setSrcIp("srciprtyui");
        preConfig.setSrcUser("234567890user");
        preConfig.setOpType("D2345678");
        preConfig.setCreatedTime(new Timestamp(System.currentTimeMillis()));
        preConfig.setLastModifiedTime(new Timestamp(System.currentTimeMillis()));
        ResultSetImpl resultSet = Mockito.mock(ResultSetImpl.class);
        Mockito.when(resultSet.getString(eq("data_id"))).thenReturn(preConfig.getDataId());
        Mockito.when(resultSet.getString(eq("group_id"))).thenReturn(preConfig.getGroup());
        Mockito.when(resultSet.getString(eq("tenant_id"))).thenReturn(preConfig.getTenant());
        Mockito.when(resultSet.getString(eq("app_name"))).thenReturn(preConfig.getAppName());
        Mockito.when(resultSet.getString(eq("op_type"))).thenReturn(preConfig.getOpType());
        Mockito.when(resultSet.getString(eq("src_user"))).thenReturn(preConfig.getSrcUser());
        Mockito.when(resultSet.getLong(eq("nid"))).thenReturn(preConfig.getId());
        Mockito.when(resultSet.getString(eq("src_ip"))).thenReturn(preConfig.getSrcIp());
        Mockito.when(resultSet.getTimestamp(eq("gmt_modified")))
            .thenReturn(preConfig.getLastModifiedTime());
        Mockito.when(resultSet.getTimestamp(eq("gmt_create")))
            .thenReturn(preConfig.getCreatedTime());
        ConfigRowMapperInjector.ConfigHistoryRowMapper configInfoWrapperRowMapper =
            new ConfigRowMapperInjector.ConfigHistoryRowMapper();
        
        ConfigHistoryInfo configInfoWrapper = configInfoWrapperRowMapper.mapRow(resultSet, 10);
        assertEquals(preConfig, configInfoWrapper);
        
    }
    
    @Test
    void testConfigHistoryDetailRowMapper() throws SQLException {
        
        ConfigHistoryInfo preConfig = new ConfigHistoryInfo();
        preConfig.setDataId("testDataId");
        preConfig.setGroup("group_id11");
        preConfig.setTenant("tenant_id11111");
        preConfig.setId(1243567898L);
        preConfig.setAppName("app_name11111");
        preConfig.setContent("content2345678");
        preConfig.setMd5("md5234567890");
        preConfig.setSrcIp("srciprtyui");
        preConfig.setSrcUser("234567890user");
        preConfig.setOpType("D2345678");
        preConfig.setCreatedTime(new Timestamp(System.currentTimeMillis()));
        preConfig.setLastModifiedTime(new Timestamp(System.currentTimeMillis()));
        preConfig.setContent("content1123434t");
        preConfig.setMd5("md54567");
        preConfig.setEncryptedDataKey("key3456789");
        ResultSetImpl resultSet = Mockito.mock(ResultSetImpl.class);
        Mockito.when(resultSet.getString(eq("data_id"))).thenReturn(preConfig.getDataId());
        Mockito.when(resultSet.getString(eq("group_id"))).thenReturn(preConfig.getGroup());
        Mockito.when(resultSet.getString(eq("tenant_id"))).thenReturn(preConfig.getTenant());
        Mockito.when(resultSet.getString(eq("app_name"))).thenReturn(preConfig.getAppName());
        Mockito.when(resultSet.getString(eq("op_type"))).thenReturn(preConfig.getOpType());
        Mockito.when(resultSet.getString(eq("src_user"))).thenReturn(preConfig.getSrcUser());
        Mockito.when(resultSet.getString(eq("content"))).thenReturn(preConfig.getContent());
        Mockito.when(resultSet.getString(eq("md5"))).thenReturn(preConfig.getMd5());
        Mockito.when(resultSet.getString(eq("encrypted_data_key")))
            .thenReturn(preConfig.getEncryptedDataKey());
        Mockito.when(resultSet.getLong(eq("nid"))).thenReturn(preConfig.getId());
        Mockito.when(resultSet.getString(eq("src_ip"))).thenReturn(preConfig.getSrcIp());
        Mockito.when(resultSet.getTimestamp(eq("gmt_modified")))
            .thenReturn(preConfig.getLastModifiedTime());
        Mockito.when(resultSet.getTimestamp(eq("gmt_create")))
            .thenReturn(preConfig.getCreatedTime());
        
        ConfigHistoryDetailRowMapper configInfoWrapperRowMapper =
            new ConfigHistoryDetailRowMapper();
        ConfigHistoryInfo configInfoWrapper = configInfoWrapperRowMapper.mapRow(resultSet, 10);
        assertEquals(preConfig, configInfoWrapper);
        
    }
    
    @Test
    void testConfigInfoStateWrapperRowMapper() throws SQLException {
        
        ConfigInfoStateWrapper preConfig = new ConfigInfoStateWrapper();
        preConfig.setDataId("testDataId");
        preConfig.setGroup("group_id11");
        preConfig.setTenant("tenant_id11111");
        preConfig.setId(1243567898L);
        ResultSetImpl resultSet = Mockito.mock(ResultSetImpl.class);
        Mockito.when(resultSet.getString(eq("data_id"))).thenReturn(preConfig.getDataId());
        Mockito.when(resultSet.getString(eq("group_id"))).thenReturn(preConfig.getGroup());
        Mockito.when(resultSet.getString(eq("tenant_id"))).thenReturn(preConfig.getTenant());
        Mockito.when(resultSet.getTimestamp(eq("gmt_modified")))
            .thenReturn(new Timestamp(preConfig.getLastModified()));
        
        Mockito.when(resultSet.getLong(eq("id"))).thenReturn(preConfig.getId());
        ConfigInfoStateWrapperRowMapper configInfoWrapperRowMapper =
            new ConfigInfoStateWrapperRowMapper();
        ConfigInfoStateWrapper configInfoWrapper = configInfoWrapperRowMapper.mapRow(resultSet, 10);
        assertEquals(preConfig, configInfoWrapper);
        
    }
    
    @Test
    void testConfigKeyRowMapper() throws SQLException {
        ConfigKey preConfig = new ConfigKey();
        preConfig.setDataId("testDataId");
        preConfig.setGroup("group_id11");
        preConfig.setAppName("appertyui4567");
        ResultSetImpl resultSet = Mockito.mock(ResultSetImpl.class);
        Mockito.when(resultSet.getString(eq("data_id"))).thenReturn(preConfig.getDataId());
        Mockito.when(resultSet.getString(eq("group_id"))).thenReturn(preConfig.getGroup());
        Mockito.when(resultSet.getString(eq("app_name"))).thenReturn(preConfig.getAppName());
        ConfigRowMapperInjector.ConfigKeyRowMapper configInfoWrapperRowMapper =
            new ConfigRowMapperInjector.ConfigKeyRowMapper();
        
        ConfigKey configInfoWrapper = configInfoWrapperRowMapper.mapRow(resultSet, 10);
        assertEquals(preConfig, configInfoWrapper);
        
    }
    
    @Test
    void testConfigInfoRowMapperWithDescAndTags() throws SQLException {
        ConfigRowMapperInjector.ConfigInfoRowMapper mapper =
            new ConfigRowMapperInjector.ConfigInfoRowMapper();
        ResultSetImpl resultSet = Mockito.mock(ResultSetImpl.class);
        
        Mockito.when(resultSet.getLong(eq("id"))).thenReturn(1L);
        Mockito.when(resultSet.getString(eq("data_id"))).thenReturn("test.properties");
        Mockito.when(resultSet.getString(eq("group_id"))).thenReturn("DEFAULT_GROUP");
        Mockito.when(resultSet.getString(eq("tenant_id"))).thenReturn("public");
        Mockito.when(resultSet.getString(eq("app_name"))).thenReturn("testApp");
        Mockito.when(resultSet.getString(eq("content"))).thenReturn("key=value");
        Mockito.when(resultSet.getString(eq("md5"))).thenReturn("abc123");
        Mockito.when(resultSet.getString(eq("type"))).thenReturn("properties");
        Mockito.when(resultSet.getString(eq("encrypted_data_key"))).thenReturn("encKey");
        Mockito.when(resultSet.getString(eq("c_desc"))).thenReturn("测试配置描述");
        Mockito.when(resultSet.getString(eq("config_tags"))).thenReturn("tag1,tag2,tag3");
        Timestamp gmtModified = new Timestamp(1700000000000L);
        Mockito.when(resultSet.getTimestamp(eq("gmt_modified"))).thenReturn(gmtModified);
        
        ConfigInfo configInfo = mapper.mapRow(resultSet, 1);
        
        assertEquals(1L, configInfo.getId());
        assertEquals("test.properties", configInfo.getDataId());
        assertEquals("DEFAULT_GROUP", configInfo.getGroup());
        assertEquals("public", configInfo.getTenant());
        assertEquals("testApp", configInfo.getAppName());
        assertEquals("key=value", configInfo.getContent());
        assertEquals("abc123", configInfo.getMd5());
        assertEquals("properties", configInfo.getType());
        assertEquals("encKey", configInfo.getEncryptedDataKey());
        assertEquals("测试配置描述", configInfo.getDesc());
        assertEquals("tag1,tag2,tag3", configInfo.getConfigTags());
        assertEquals(1700000000000L, configInfo.getGmtModified());
    }
    
    @Test
    void testConfigInfoRowMapperWithNullDescAndTags() throws SQLException {
        ConfigRowMapperInjector.ConfigInfoRowMapper mapper =
            new ConfigRowMapperInjector.ConfigInfoRowMapper();
        ResultSetImpl resultSet = Mockito.mock(ResultSetImpl.class);
        
        Mockito.when(resultSet.getLong(eq("id"))).thenReturn(1L);
        Mockito.when(resultSet.getString(eq("data_id"))).thenReturn("test.properties");
        Mockito.when(resultSet.getString(eq("group_id"))).thenReturn("DEFAULT_GROUP");
        Mockito.when(resultSet.getString(eq("tenant_id"))).thenReturn("public");
        Mockito.when(resultSet.getString(eq("app_name"))).thenReturn("testApp");
        Mockito.when(resultSet.getString(eq("content"))).thenReturn("key=value");
        Mockito.when(resultSet.getString(eq("md5"))).thenReturn("abc123");
        Mockito.when(resultSet.getString(eq("type"))).thenReturn("properties");
        Mockito.when(resultSet.getString(eq("encrypted_data_key"))).thenReturn("encKey");
        Mockito.when(resultSet.getString(eq("c_desc"))).thenReturn(null);
        Mockito.when(resultSet.getString(eq("config_tags"))).thenReturn("  ");
        Mockito.when(resultSet.getTimestamp(eq("gmt_modified"))).thenReturn(null);
        
        ConfigInfo configInfo = mapper.mapRow(resultSet, 1);
        
        assertEquals(1L, configInfo.getId());
        assertEquals("test.properties", configInfo.getDataId());
        assertEquals("DEFAULT_GROUP", configInfo.getGroup());
        assertEquals("public", configInfo.getTenant());
        assertEquals("testApp", configInfo.getAppName());
        assertEquals("key=value", configInfo.getContent());
        assertEquals("abc123", configInfo.getMd5());
        assertEquals("properties", configInfo.getType());
        assertEquals("encKey", configInfo.getEncryptedDataKey());
        assertEquals(null, configInfo.getDesc());
        assertEquals(null, configInfo.getConfigTags());
    }
    
    @Test
    void testConfigInfoRowMapperBackwardCompatibility() throws SQLException {
        ConfigRowMapperInjector.ConfigInfoRowMapper mapper =
            new ConfigRowMapperInjector.ConfigInfoRowMapper();
        ResultSetImpl resultSet = Mockito.mock(ResultSetImpl.class);
        
        // 模拟旧版本数据库，没有 c_desc 和 config_tags 字段
        Mockito.when(resultSet.getLong(eq("id"))).thenReturn(1L);
        Mockito.when(resultSet.getString(eq("data_id"))).thenReturn("test.properties");
        Mockito.when(resultSet.getString(eq("group_id"))).thenReturn("DEFAULT_GROUP");
        Mockito.when(resultSet.getString(eq("tenant_id"))).thenReturn("public");
        Mockito.when(resultSet.getString(eq("app_name"))).thenReturn("testApp");
        Mockito.when(resultSet.getString(eq("content"))).thenReturn("key=value");
        Mockito.when(resultSet.getString(eq("md5"))).thenReturn("abc123");
        Mockito.when(resultSet.getString(eq("type"))).thenReturn("properties");
        Mockito.when(resultSet.getString(eq("encrypted_data_key"))).thenReturn("encKey");
        
        // 模拟字段不存在的情况
        Mockito.when(resultSet.getString(eq("c_desc")))
            .thenThrow(new SQLException("Column 'c_desc' not found"));
        Mockito.when(resultSet.getString(eq("config_tags")))
            .thenThrow(new SQLException("Column 'config_tags' not found"));
        
        ConfigInfo configInfo = mapper.mapRow(resultSet, 1);
        
        assertEquals(1L, configInfo.getId());
        assertEquals("test.properties", configInfo.getDataId());
        assertEquals("DEFAULT_GROUP", configInfo.getGroup());
        assertEquals("public", configInfo.getTenant());
        assertEquals("testApp", configInfo.getAppName());
        assertEquals("key=value", configInfo.getContent());
        assertEquals("abc123", configInfo.getMd5());
        assertEquals("properties", configInfo.getType());
        assertEquals("encKey", configInfo.getEncryptedDataKey());
        // 新字段应该为 null，保证向后兼容
        assertEquals(null, configInfo.getDesc());
        assertEquals(null, configInfo.getConfigTags());
    }
    
    @Test
    void testConfigInfoGrayWrapperRowMapperMissingColumns() throws SQLException {
        ResultSetImpl resultSet = Mockito.mock(ResultSetImpl.class);
        Mockito.when(resultSet.getString(eq("data_id"))).thenReturn("d");
        Mockito.when(resultSet.getString(eq("group_id"))).thenReturn("g");
        Mockito.when(resultSet.getString(eq("tenant_id"))).thenReturn("t");
        Mockito.when(resultSet.getString(eq("gray_name"))).thenReturn("gn");
        Mockito.when(resultSet.getString(eq("gray_rule")))
            .thenThrow(new SQLException("not found"));
        Mockito.when(resultSet.getString(eq("app_name")))
            .thenThrow(new SQLException("not found"));
        Mockito.when(resultSet.getString(eq("content")))
            .thenThrow(new SQLException("not found"));
        Mockito.when(resultSet.getLong(eq("id")))
            .thenThrow(new SQLException("not found"));
        Mockito.when(resultSet.getTimestamp(eq("gmt_modified")))
            .thenThrow(new SQLException("not found"));
        Mockito.when(resultSet.getString(eq("md5")))
            .thenThrow(new SQLException("not found"));
        Mockito.when(resultSet.getString(eq("encrypted_data_key")))
            .thenThrow(new SQLException("not found"));
        Mockito.when(resultSet.getString(eq("src_user")))
            .thenThrow(new SQLException("not found"));
        
        ConfigRowMapperInjector.ConfigInfoGrayWrapperRowMapper mapper =
            new ConfigRowMapperInjector.ConfigInfoGrayWrapperRowMapper();
        ConfigInfoGrayWrapper result = mapper.mapRow(resultSet, 1);
        assertEquals("d", result.getDataId());
        assertEquals("g", result.getGroup());
        assertEquals("gn", result.getGrayName());
    }
    
    @Test
    void testConfigInfoWrapperRowMapperMissingColumns() throws SQLException {
        ResultSetImpl resultSet = Mockito.mock(ResultSetImpl.class);
        Mockito.when(resultSet.getString(eq("data_id"))).thenReturn("d");
        Mockito.when(resultSet.getString(eq("group_id"))).thenReturn("g");
        Mockito.when(resultSet.getString(eq("tenant_id"))).thenReturn("t");
        Mockito.when(resultSet.getString(eq("app_name"))).thenReturn("app");
        Mockito.when(resultSet.getString(eq("type")))
            .thenThrow(new SQLException("not found"));
        Mockito.when(resultSet.getString(eq("content")))
            .thenThrow(new SQLException("not found"));
        Mockito.when(resultSet.getLong(eq("id")))
            .thenThrow(new SQLException("not found"));
        Mockito.when(resultSet.getTimestamp(eq("gmt_modified")))
            .thenThrow(new SQLException("not found"));
        Mockito.when(resultSet.getString(eq("md5")))
            .thenThrow(new SQLException("not found"));
        Mockito.when(resultSet.getString(eq("encrypted_data_key")))
            .thenThrow(new SQLException("not found"));
        
        ConfigRowMapperInjector.ConfigInfoWrapperRowMapper mapper =
            new ConfigRowMapperInjector.ConfigInfoWrapperRowMapper();
        ConfigInfoWrapper result = mapper.mapRow(resultSet, 1);
        assertEquals("d", result.getDataId());
        assertEquals("g", result.getGroup());
        assertEquals("app", result.getAppName());
    }
    
    @Test
    void testConfigInfoStateWrapperRowMapperMissingOptionalColumns() throws SQLException {
        ResultSetImpl resultSet = mockResultSetWithConfigKey();
        Mockito.when(resultSet.getTimestamp(eq("gmt_modified"))).thenReturn(new Timestamp(123L));
        mockMissingString(resultSet, "md5");
        mockMissingLong(resultSet, "id");
        
        ConfigInfoStateWrapper result = new ConfigInfoStateWrapperRowMapper().mapRow(resultSet, 1);
        
        assertEquals("d", result.getDataId());
        assertEquals("g", result.getGroup());
        assertEquals(123L, result.getLastModified());
    }
    
    @Test
    void testConfigAllInfoRowMapperMissingOptionalColumns() throws SQLException {
        ResultSetImpl resultSet = mockResultSetWithConfigKey();
        Mockito.when(resultSet.getString(eq("app_name"))).thenReturn("app");
        Mockito.when(resultSet.getTimestamp(eq("gmt_create"))).thenReturn(new Timestamp(123L));
        Mockito.when(resultSet.getTimestamp(eq("gmt_modified"))).thenReturn(new Timestamp(456L));
        Mockito.when(resultSet.getString(eq("src_user"))).thenReturn("user");
        Mockito.when(resultSet.getString(eq("src_ip"))).thenReturn("ip");
        Mockito.when(resultSet.getString(eq("c_desc"))).thenReturn("desc");
        Mockito.when(resultSet.getString(eq("c_use"))).thenReturn("use");
        Mockito.when(resultSet.getString(eq("effect"))).thenReturn("effect");
        Mockito.when(resultSet.getString(eq("type"))).thenReturn("type");
        Mockito.when(resultSet.getString(eq("c_schema"))).thenReturn("schema");
        mockMissingString(resultSet, "content");
        mockMissingString(resultSet, "md5");
        mockMissingLong(resultSet, "id");
        mockMissingString(resultSet, "encrypted_data_key");
        
        ConfigAllInfo result = new ConfigRowMapperInjector.ConfigAllInfoRowMapper()
            .mapRow(resultSet, 1);
        
        assertEquals("d", result.getDataId());
        assertEquals("g", result.getGroup());
        assertEquals("app", result.getAppName());
    }
    
    @Test
    void testConfigInfoBaseRowMapperMissingOptionalColumns() throws SQLException {
        ResultSetImpl resultSet = Mockito.mock(ResultSetImpl.class);
        Mockito.when(resultSet.getString(eq("data_id"))).thenReturn("d");
        Mockito.when(resultSet.getString(eq("group_id"))).thenReturn("g");
        mockMissingString(resultSet, "content");
        mockMissingLong(resultSet, "id");
        
        ConfigInfoBase result =
            new ConfigRowMapperInjector.ConfigInfoBaseRowMapper().mapRow(resultSet, 1);
        
        assertEquals("d", result.getDataId());
        assertEquals("g", result.getGroup());
    }
    
    @Test
    void testConfigHistoryDetailRowMapperMissingEncryptedDataKey() throws SQLException {
        ResultSetImpl resultSet = Mockito.mock(ResultSetImpl.class);
        Mockito.when(resultSet.getLong(eq("nid"))).thenReturn(1L);
        Mockito.when(resultSet.getString(eq("data_id"))).thenReturn("d");
        Mockito.when(resultSet.getString(eq("group_id"))).thenReturn("g");
        Mockito.when(resultSet.getString(eq("tenant_id"))).thenReturn("t");
        Mockito.when(resultSet.getString(eq("app_name"))).thenReturn("app");
        Mockito.when(resultSet.getString(eq("md5"))).thenReturn("md5");
        Mockito.when(resultSet.getString(eq("content"))).thenReturn("content");
        Mockito.when(resultSet.getString(eq("src_user"))).thenReturn("user");
        Mockito.when(resultSet.getString(eq("src_ip"))).thenReturn("ip");
        Mockito.when(resultSet.getString(eq("op_type"))).thenReturn("op");
        Mockito.when(resultSet.getString(eq("publish_type"))).thenReturn("formal");
        Mockito.when(resultSet.getString(eq("gray_name"))).thenReturn("gray");
        Mockito.when(resultSet.getString(eq("ext_info"))).thenReturn("{}");
        Mockito.when(resultSet.getTimestamp(eq("gmt_create"))).thenReturn(new Timestamp(123L));
        Mockito.when(resultSet.getTimestamp(eq("gmt_modified"))).thenReturn(new Timestamp(456L));
        mockMissingString(resultSet, "encrypted_data_key");
        
        ConfigHistoryInfo result = new ConfigHistoryDetailRowMapper().mapRow(resultSet, 1);
        
        assertEquals("d", result.getDataId());
        assertEquals("g", result.getGroup());
        assertEquals("content", result.getContent());
    }
    
    private ResultSetImpl mockResultSetWithConfigKey() throws SQLException {
        ResultSetImpl resultSet = Mockito.mock(ResultSetImpl.class);
        Mockito.when(resultSet.getString(eq("data_id"))).thenReturn("d");
        Mockito.when(resultSet.getString(eq("group_id"))).thenReturn("g");
        Mockito.when(resultSet.getString(eq("tenant_id"))).thenReturn("t");
        return resultSet;
    }
    
    private void mockMissingString(ResultSetImpl resultSet, String column) throws SQLException {
        Mockito.when(resultSet.getString(eq(column))).thenThrow(new SQLException("not found"));
    }
    
    private void mockMissingLong(ResultSetImpl resultSet, String column) throws SQLException {
        Mockito.when(resultSet.getLong(eq(column))).thenThrow(new SQLException("not found"));
    }
    
    private void mockMissingTimestamp(ResultSetImpl resultSet, String column) throws SQLException {
        Mockito.when(resultSet.getTimestamp(eq(column))).thenThrow(new SQLException("not found"));
    }
    
}
