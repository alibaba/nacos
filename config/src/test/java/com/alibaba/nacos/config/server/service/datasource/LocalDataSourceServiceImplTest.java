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
import org.springframework.mock.web.MockServletContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionTemplate;

import static org.mockito.Mockito.when;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(classes = MockServletContext.class)
public class LocalDataSourceServiceImplTest {
    
    @InjectMocks
    private LocalDataSourceServiceImpl service;
    
    @Mock
    private JdbcTemplate jt;
    
    @Mock
    private TransactionTemplate tjt;
    
    @Before
    public void setUp() {
        service = new LocalDataSourceServiceImpl();
        ReflectionTestUtils.setField(service, "jt", jt);
        ReflectionTestUtils.setField(service, "tjt", tjt);
    }
    
    @Test
    public void testGetDataSource() {
        
        HikariDataSource dataSource = new HikariDataSource();
        dataSource.setJdbcUrl("test.jdbc.url");
        when(jt.getDataSource()).thenReturn(dataSource);
        Assert.assertEquals(dataSource.getJdbcUrl(), ((HikariDataSource) service.getDatasource()).getJdbcUrl());
    }
    
    @Test
    public void testCheckMasterWritable() {
        
        Assert.assertTrue(service.checkMasterWritable());
    }
    
    @Test
    public void testSetAndGetHealth() {
        
        service.setHealthStatus("DOWN");
        Assert.assertEquals("DOWN", service.getHealth());
        
        service.setHealthStatus("UP");
        Assert.assertEquals("UP", service.getHealth());
    }
}
