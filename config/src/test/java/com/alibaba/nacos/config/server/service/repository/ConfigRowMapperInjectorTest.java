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
import com.alibaba.nacos.config.server.service.repository.ConfigRowMapperInjector.ConfigHistoryDetailRowMapper;
import com.alibaba.nacos.config.server.service.repository.ConfigRowMapperInjector.ConfigInfo4BetaRowMapper;
import com.alibaba.nacos.config.server.service.repository.ConfigRowMapperInjector.ConfigInfoBetaWrapperRowMapper;
import com.alibaba.nacos.config.server.service.repository.ConfigRowMapperInjector.ConfigInfoChangedRowMapper;
import com.alibaba.nacos.config.server.service.repository.ConfigRowMapperInjector.ConfigInfoStateWrapperRowMapper;
import com.alibaba.nacos.config.server.service.repository.ConfigRowMapperInjector.ConfigInfoTagWrapperRowMapper;
import com.alibaba.nacos.persistence.repository.RowMapperManager;
import com.mysql.cj.jdbc.result.ResultSetImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.sql.SQLException;
import java.sql.Timestamp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.eq;

@ExtendWith(SpringExtension.class)
class ConfigRowMapperInjectorTest {
    
    @Test
    void testInit() {
        ConfigRowMapperInjector configRowMapperInjector = new ConfigRowMapperInjector();
        assertEquals(ConfigRowMapperInjector.CONFIG_INFO_WRAPPER_ROW_MAPPER, RowMapperManager.getRowMapper(
                ConfigRowMapperInjector.CONFIG_INFO_WRAPPER_ROW_MAPPER.getClass().getCanonicalName()));
    }
    
    @Test
    void testConfigInfoTagWrapperRowMapper() throws SQLException {
        ConfigInfoTagWrapper preConfig = new ConfigInfoTagWrapper();
        preConfig.setDataId("testDataId");
        preConfig.setGroup("group_id11");
        preConfig.setTenant("tenant_id11111");
        preConfig.setId(1243567898L);
        preConfig.setTag("tag345678");
        preConfig.setLastModified(System.currentTimeMillis());
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
        Mockito.when(resultSet.getTimestamp(eq("gmt_modified"))).thenReturn(new Timestamp(preConfig.getLastModified()));
        Mockito.when(resultSet.getString(eq("content"))).thenReturn(preConfig.getContent());
        Mockito.when(resultSet.getLong(eq("id"))).thenReturn(preConfig.getId());
        Mockito.when(resultSet.getString(eq("md5"))).thenReturn(preConfig.getMd5());
        Mockito.when(resultSet.getString(eq("encrypted_data_key"))).thenReturn(preConfig.getEncryptedDataKey());
        Mockito.when(resultSet.getString(eq("tag_id"))).thenReturn(preConfig.getTag());
        ConfigInfoTagWrapperRowMapper configInfoWrapperRowMapper = new ConfigInfoTagWrapperRowMapper();
        ConfigInfoTagWrapper configInfoWrapper = configInfoWrapperRowMapper.mapRow(resultSet, 10);
        assertEquals(preConfig, configInfoWrapper);
        assertEquals(preConfig.getTag(), configInfoWrapper.getTag());
    }
    
    @Test
    void testConfigInfo4BetaRowMapper() throws SQLException {
        ConfigInfo4Beta preConfig = new ConfigInfo4Beta();
        preConfig.setDataId("testDataId");
        preConfig.setGroup("group_id11");
        preConfig.setTenant("tenant_id11111");
        preConfig.setId(1243567898L);
        preConfig.setAppName("app_name11111");
        preConfig.setType("type55555");
        preConfig.setContent("content1123434t");
        preConfig.setMd5("md54567");
        preConfig.setEncryptedDataKey("encrypted_data_key1324");
        preConfig.setBetaIps("127.0.0.1,127.0.0.2");
        ResultSetImpl resultSet = Mockito.mock(ResultSetImpl.class);
        Mockito.when(resultSet.getString(eq("data_id"))).thenReturn(preConfig.getDataId());
        Mockito.when(resultSet.getString(eq("group_id"))).thenReturn(preConfig.getGroup());
        Mockito.when(resultSet.getString(eq("tenant_id"))).thenReturn(preConfig.getTenant());
        Mockito.when(resultSet.getString(eq("app_name"))).thenReturn(preConfig.getAppName());
        Mockito.when(resultSet.getString(eq("type"))).thenReturn(preConfig.getType());
        Mockito.when(resultSet.getString(eq("content"))).thenReturn(preConfig.getContent());
        Mockito.when(resultSet.getString(eq("beta_ips"))).thenReturn(preConfig.getBetaIps());
        Mockito.when(resultSet.getLong(eq("id"))).thenReturn(preConfig.getId());
        Mockito.when(resultSet.getString(eq("md5"))).thenReturn(preConfig.getMd5());
        Mockito.when(resultSet.getString(eq("encrypted_data_key"))).thenReturn(preConfig.getEncryptedDataKey());
        ConfigInfo4BetaRowMapper configInfoWrapperRowMapper = new ConfigInfo4BetaRowMapper();
        ConfigInfo4Beta configInfoWrapper = configInfoWrapperRowMapper.mapRow(resultSet, 10);
        assertEquals(preConfig, configInfoWrapper);
        assertEquals(preConfig.getBetaIps(), configInfoWrapper.getBetaIps());
    }
    
    @Test
    void testConfigInfoBetaWrapperRowMapper() throws SQLException {
        ConfigInfoBetaWrapper preConfig = new ConfigInfoBetaWrapper();
        preConfig.setDataId("testDataId");
        preConfig.setGroup("group_id11");
        preConfig.setTenant("tenant_id11111");
        preConfig.setId(1243567898L);
        preConfig.setLastModified(System.currentTimeMillis());
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
        Mockito.when(resultSet.getTimestamp(eq("gmt_modified"))).thenReturn(new Timestamp(preConfig.getLastModified()));
        Mockito.when(resultSet.getString(eq("content"))).thenReturn(preConfig.getContent());
        Mockito.when(resultSet.getLong(eq("id"))).thenReturn(preConfig.getId());
        Mockito.when(resultSet.getString(eq("md5"))).thenReturn(preConfig.getMd5());
        Mockito.when(resultSet.getString(eq("encrypted_data_key"))).thenReturn(preConfig.getEncryptedDataKey());
        ConfigInfoBetaWrapperRowMapper configInfoWrapperRowMapper = new ConfigInfoBetaWrapperRowMapper();
        ConfigInfoBetaWrapper configInfoWrapper = configInfoWrapperRowMapper.mapRow(resultSet, 10);
        assertEquals(preConfig, configInfoWrapper);
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
        Mockito.when(resultSet.getTimestamp(eq("gmt_modified"))).thenReturn(new Timestamp(preConfig.getModifyTime()));
        Mockito.when(resultSet.getTimestamp(eq("gmt_create"))).thenReturn(new Timestamp(preConfig.getCreateTime()));
        Mockito.when(resultSet.getString(eq("c_use"))).thenReturn(preConfig.getUse());
        Mockito.when(resultSet.getString(eq("c_schema"))).thenReturn(preConfig.getSchema());
        ConfigRowMapperInjector.ConfigAdvanceInfoRowMapper configInfoWrapperRowMapper = new ConfigRowMapperInjector.ConfigAdvanceInfoRowMapper();
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
        Mockito.when(resultSet.getTimestamp(eq("gmt_modified"))).thenReturn(new Timestamp(preConfig.getModifyTime()));
        
        Mockito.when(resultSet.getLong(eq("id"))).thenReturn(preConfig.getId());
        Mockito.when(resultSet.getString(eq("md5"))).thenReturn(preConfig.getMd5());
        Mockito.when(resultSet.getString(eq("encrypted_data_key"))).thenReturn(preConfig.getEncryptedDataKey());
        ConfigRowMapperInjector.ConfigAllInfoRowMapper configInfoWrapperRowMapper = new ConfigRowMapperInjector.ConfigAllInfoRowMapper();
        
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
        Mockito.when(resultSet.getString(eq("encrypted_data_key"))).thenReturn(preConfig.getEncryptedDataKey());
        ConfigRowMapperInjector.ConfigInfoRowMapper configInfoWrapperRowMapper = new ConfigRowMapperInjector.ConfigInfoRowMapper();
        ConfigInfo configInfoWrapper = configInfoWrapperRowMapper.mapRow(resultSet, 10);
        assertEquals(preConfig, configInfoWrapper);
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
        Mockito.when(resultSet.getTimestamp(eq("gmt_modified"))).thenReturn(new Timestamp(preConfig.getLastModified()));
        Mockito.when(resultSet.getString(eq("md5"))).thenReturn(preConfig.getMd5());
        Mockito.when(resultSet.getString(eq("encrypted_data_key"))).thenReturn(preConfig.getEncryptedDataKey());
        ConfigRowMapperInjector.ConfigInfoWrapperRowMapper configInfoWrapperRowMapper = new ConfigRowMapperInjector.ConfigInfoWrapperRowMapper();
        ConfigInfoWrapper configInfoWrapper = configInfoWrapperRowMapper.mapRow(resultSet, 10);
        assertEquals(preConfig, configInfoWrapper);
        
    }
    
    @Test
    void testConfigInfo4TagRowMapper() throws SQLException {
        
        ConfigInfo4Tag preConfig = new ConfigInfo4Tag();
        preConfig.setDataId("testDataId");
        preConfig.setGroup("group_id11");
        preConfig.setTenant("tenant_id11111");
        preConfig.setId(1243567898L);
        preConfig.setAppName("app_name11111");
        preConfig.setType("type55555");
        preConfig.setContent("content1123434t");
        preConfig.setMd5("md54567");
        preConfig.setEncryptedDataKey("encrypted_data_key1324");
        preConfig.setTag("tag567890");
        ResultSetImpl resultSet = Mockito.mock(ResultSetImpl.class);
        Mockito.when(resultSet.getString(eq("data_id"))).thenReturn(preConfig.getDataId());
        Mockito.when(resultSet.getString(eq("group_id"))).thenReturn(preConfig.getGroup());
        Mockito.when(resultSet.getString(eq("tenant_id"))).thenReturn(preConfig.getTenant());
        Mockito.when(resultSet.getString(eq("app_name"))).thenReturn(preConfig.getAppName());
        Mockito.when(resultSet.getString(eq("type"))).thenReturn(preConfig.getType());
        Mockito.when(resultSet.getString(eq("content"))).thenReturn(preConfig.getContent());
        Mockito.when(resultSet.getLong(eq("id"))).thenReturn(preConfig.getId());
        Mockito.when(resultSet.getString(eq("tag_id"))).thenReturn(preConfig.getTag());
        Mockito.when(resultSet.getString(eq("md5"))).thenReturn(preConfig.getMd5());
        Mockito.when(resultSet.getString(eq("encrypted_data_key"))).thenReturn(preConfig.getEncryptedDataKey());
        ConfigRowMapperInjector.ConfigInfo4TagRowMapper configInfoWrapperRowMapper = new ConfigRowMapperInjector.ConfigInfo4TagRowMapper();
        ConfigInfo4Tag configInfoWrapper = configInfoWrapperRowMapper.mapRow(resultSet, 10);
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
        ConfigRowMapperInjector.ConfigInfoBaseRowMapper configInfoWrapperRowMapper = new ConfigRowMapperInjector.ConfigInfoBaseRowMapper();
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
        Mockito.when(resultSet.getString(eq("encrypted_data_key"))).thenReturn(preConfig.getEncryptedDataKey());
        
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
        Mockito.when(resultSet.getTimestamp(eq("gmt_modified"))).thenReturn(preConfig.getLastModifiedTime());
        Mockito.when(resultSet.getTimestamp(eq("gmt_create"))).thenReturn(preConfig.getCreatedTime());
        ConfigRowMapperInjector.ConfigHistoryRowMapper configInfoWrapperRowMapper = new ConfigRowMapperInjector.ConfigHistoryRowMapper();
        
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
        Mockito.when(resultSet.getString(eq("encrypted_data_key"))).thenReturn(preConfig.getEncryptedDataKey());
        Mockito.when(resultSet.getLong(eq("nid"))).thenReturn(preConfig.getId());
        Mockito.when(resultSet.getString(eq("src_ip"))).thenReturn(preConfig.getSrcIp());
        Mockito.when(resultSet.getTimestamp(eq("gmt_modified"))).thenReturn(preConfig.getLastModifiedTime());
        Mockito.when(resultSet.getTimestamp(eq("gmt_create"))).thenReturn(preConfig.getCreatedTime());
        
        ConfigHistoryDetailRowMapper configInfoWrapperRowMapper = new ConfigHistoryDetailRowMapper();
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
        Mockito.when(resultSet.getTimestamp(eq("gmt_modified"))).thenReturn(new Timestamp(preConfig.getLastModified()));
        
        Mockito.when(resultSet.getLong(eq("id"))).thenReturn(preConfig.getId());
        ConfigInfoStateWrapperRowMapper configInfoWrapperRowMapper = new ConfigInfoStateWrapperRowMapper();
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
        ConfigRowMapperInjector.ConfigKeyRowMapper configInfoWrapperRowMapper = new ConfigRowMapperInjector.ConfigKeyRowMapper();
        
        ConfigKey configInfoWrapper = configInfoWrapperRowMapper.mapRow(resultSet, 10);
        assertEquals(preConfig, configInfoWrapper);
        
    }
    
}
