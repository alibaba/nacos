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

package com.alibaba.nacos.config.server.service.datasource;

import com.zaxxer.hikari.HikariDataSource;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.mock.web.MockServletContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.List;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(classes = MockServletContext.class)
public class ExternalDataSourceServiceImplTest {
    
    @InjectMocks
    private ExternalDataSourceServiceImpl service;
    
    @Mock
    private JdbcTemplate jt;
    
    @Mock
    private DataSourceTransactionManager tm;
    
    @Mock
    private TransactionTemplate tjt;
    
    @Mock
    private JdbcTemplate testMasterJT;
    
    @Mock
    private JdbcTemplate testMasterWritableJT;
    
    @Before
    public void setUp() {
        service = new ExternalDataSourceServiceImpl();
        ReflectionTestUtils.setField(service, "jt", jt);
        ReflectionTestUtils.setField(service, "tm", tm);
        ReflectionTestUtils.setField(service, "tjt", tjt);
        ReflectionTestUtils.setField(service, "testMasterJT", testMasterJT);
        ReflectionTestUtils.setField(service, "testMasterWritableJT", testMasterWritableJT);
    }
    
    @Test
    public void testCheckMasterWritable() {
        
        when(testMasterWritableJT.queryForObject(eq(" SELECT @@read_only "), eq(Integer.class))).thenReturn(0);
        Assert.assertTrue(service.checkMasterWritable());
    }
    
    @Test
    public void testGetCurrentDbUrl() {
        
        HikariDataSource bds = new HikariDataSource();
        bds.setJdbcUrl("test.jdbc.url");
        when(jt.getDataSource()).thenReturn(bds);
        
        Assert.assertEquals("test.jdbc.url", service.getCurrentDbUrl());
    }
    
    @Test
    public void testGetHealth() {
    
        List<Boolean> isHealthList = new ArrayList<>();
        ReflectionTestUtils.setField(service, "isHealthList", isHealthList);
        Assert.assertEquals("UP", service.getHealth());
    }
    
    @Test
    public void testCheckDbHealthTaskRun() {
        
        List<JdbcTemplate> testJtList = new ArrayList<>();
        testJtList.add(jt);
        ReflectionTestUtils.setField(service, "testJtList", testJtList);
    
        List<Boolean> isHealthList = new ArrayList<>();
        isHealthList.add(Boolean.FALSE);
        ReflectionTestUtils.setField(service, "isHealthList", isHealthList);
        
        service.new CheckDbHealthTask().run();
        Assert.assertEquals(1, isHealthList.size());
        Assert.assertTrue(isHealthList.get(0));
    }
    
}
