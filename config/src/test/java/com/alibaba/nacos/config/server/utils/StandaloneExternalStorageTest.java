/*
 * Copyright 1999-2022 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.config.server.utils;

import com.alibaba.nacos.config.server.constant.PropertiesConstant;
import com.alibaba.nacos.config.server.service.datasource.DynamicDataSource;
import com.alibaba.nacos.config.server.service.datasource.ExternalDataSourceServiceImpl;
import com.alibaba.nacos.config.server.service.datasource.LocalDataSourceServiceImpl;
import com.alibaba.nacos.sys.env.Constants;
import com.alibaba.nacos.sys.env.EnvUtil;
import org.junit.Assert;
import org.junit.Before;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.MethodSorters;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * StandaloneExternalStorage unit test.
 *
 * @author Long Yu
 * @since 2.2.0
 */
@RunWith(MockitoJUnitRunner.class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class StandaloneExternalStorageTest {
    
    @InjectMocks
    private DynamicDataSource dataSource;
    
    private MockEnvironment environment;
    
    @Mock
    private LocalDataSourceServiceImpl localDataSourceService;
    
    @Mock
    private ExternalDataSourceServiceImpl basicDataSourceService;
    
    PropertyUtil propertyUtil = new PropertyUtil();
    
    @Before
    public void setUp() throws Exception {
        environment = new MockEnvironment();
        EnvUtil.setEnvironment(environment);
        dataSource = DynamicDataSource.getInstance();
        ReflectionTestUtils.setField(dataSource, "localDataSourceService", localDataSourceService);
        ReflectionTestUtils.setField(dataSource, "basicDataSourceService", basicDataSourceService);
    }
    
    @Test
    public void test001WithStandaloneAndNullDatabase() {
        // 模拟设置环境01：指定单例，未指定数据库，UseExternalDB是false
        System.setProperty(Constants.STANDALONE_MODE_PROPERTY_NAME, "true");
        environment.setProperty(PropertiesConstant.DATASOURCE_PLATFORM_PROPERTY_OLD, "");
        EnvUtil.setIsStandalone(Boolean.getBoolean(Constants.STANDALONE_MODE_PROPERTY_NAME));
        
        // 模拟初始化
        propertyUtil.initialize(null);
        
        Assert.assertTrue(EnvUtil.getStandaloneMode());
        Assert.assertTrue(dataSource.getDataSource() instanceof LocalDataSourceServiceImpl);
        Assert.assertFalse(PropertyUtil.isUseExternalDB());
    }
    
    @Test
    public void test002WithStandaloneAndDerbyDatabase() {
        // 模拟设置环境02：指定单例，指定数据库derby，UseExternalDB是false
        System.setProperty(Constants.STANDALONE_MODE_PROPERTY_NAME, "true");
        environment.setProperty(PropertiesConstant.DATASOURCE_PLATFORM_PROPERTY_OLD, "derby");
        EnvUtil.setIsStandalone(Boolean.getBoolean(Constants.STANDALONE_MODE_PROPERTY_NAME));
        // 模拟初始化
        
        propertyUtil.initialize(null);
        
        Assert.assertTrue(EnvUtil.getStandaloneMode());
        Assert.assertTrue(dataSource.getDataSource() instanceof LocalDataSourceServiceImpl);
        Assert.assertFalse(PropertyUtil.isUseExternalDB());
    }
    
    @Test
    public void test003WithStandaloneAndMysqlDatabase() {
        // 模拟设置环境03：指定单例，指定数据库为mysql， UseExternalDB是true
        System.setProperty(Constants.STANDALONE_MODE_PROPERTY_NAME, "true");
        environment.setProperty(PropertiesConstant.DATASOURCE_PLATFORM_PROPERTY_OLD, "mysql");
        EnvUtil.setIsStandalone(Boolean.getBoolean(Constants.STANDALONE_MODE_PROPERTY_NAME));
        // 模拟初始化
        
        propertyUtil.initialize(null);
        
        Assert.assertTrue(EnvUtil.getStandaloneMode());
        Assert.assertTrue(dataSource.getDataSource() instanceof ExternalDataSourceServiceImpl);
        Assert.assertTrue(PropertyUtil.isUseExternalDB());
    }
    
    @Test
    public void test004WithStandaloneAndOtherDatabase() {
        // 模拟设置环境04：指定单例，指定数据库为其他， UseExternalDB是true
        System.setProperty(Constants.STANDALONE_MODE_PROPERTY_NAME, "true");
        environment.setProperty(PropertiesConstant.DATASOURCE_PLATFORM_PROPERTY_OLD, "postgresql");
        EnvUtil.setIsStandalone(Boolean.getBoolean(Constants.STANDALONE_MODE_PROPERTY_NAME));
        // 模拟初始化
        
        propertyUtil.initialize(null);
        
        Assert.assertTrue(EnvUtil.getStandaloneMode());
        Assert.assertTrue(dataSource.getDataSource() instanceof ExternalDataSourceServiceImpl);
        Assert.assertTrue(PropertyUtil.isUseExternalDB());
    }
    
}
