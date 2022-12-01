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

package com.alibaba.nacos.config.server.service.capacity;

import com.alibaba.nacos.common.utils.CollectionUtils;
import com.alibaba.nacos.config.server.model.capacity.Capacity;
import com.alibaba.nacos.config.server.model.capacity.GroupCapacity;
import com.alibaba.nacos.config.server.service.datasource.DataSourceService;
import com.alibaba.nacos.config.server.utils.TimeUtils;
import com.alibaba.nacos.plugin.datasource.MapperManager;
import com.alibaba.nacos.plugin.datasource.constants.TableConstant;
import com.alibaba.nacos.plugin.datasource.impl.mysql.ConfigInfoMapperByMySql;
import com.alibaba.nacos.plugin.datasource.impl.mysql.GroupCapacityMapperByMysql;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentMatcher;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.stubbing.Answer;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementCreator;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.mock.web.MockServletContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.test.util.ReflectionTestUtils;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(classes = MockServletContext.class)
public class GroupCapacityPersistServiceTest {
    
    @InjectMocks
    private GroupCapacityPersistService service;
    
    @Mock
    private JdbcTemplate jdbcTemplate;
    
    @Mock
    private DataSourceService dataSourceService;
    
    @Mock
    private MapperManager mapperManager;
    
    @Before
    public void setUp() {
        ReflectionTestUtils.setField(service, "jdbcTemplate", jdbcTemplate);
        ReflectionTestUtils.setField(service, "dataSourceService", dataSourceService);
        ReflectionTestUtils.setField(service, "mapperManager", mapperManager);
        when(dataSourceService.getJdbcTemplate()).thenReturn(jdbcTemplate);
        doReturn(new GroupCapacityMapperByMysql()).when(mapperManager)
                .findMapper(any(), eq(TableConstant.GROUP_CAPACITY));
    }
    
    @Test
    public void testGetGroupCapacity() {
        
        List<GroupCapacity> list = new ArrayList<>();
        GroupCapacity groupCapacity = new GroupCapacity();
        groupCapacity.setGroup("test");
        list.add(groupCapacity);
        
        String groupId = "testId";
        when(jdbcTemplate.query(anyString(), eq(new Object[] {groupId}), any(RowMapper.class))).thenReturn(list);
        GroupCapacity ret = service.getGroupCapacity(groupId);
        
        Assert.assertEquals(groupCapacity.getGroup(), ret.getGroup());
    }
    
    @Test
    public void testGetClusterCapacity() {
        
        List<GroupCapacity> list = new ArrayList<>();
        GroupCapacity groupCapacity = new GroupCapacity();
        groupCapacity.setId(1L);
        list.add(groupCapacity);
        
        String groupId = GroupCapacityPersistService.CLUSTER;
        when(jdbcTemplate.query(anyString(), eq(new Object[] {groupId}), any(RowMapper.class))).thenReturn(list);
        Capacity ret = service.getClusterCapacity();
        
        Assert.assertEquals(groupCapacity.getId(), ret.getId());
    }
    
    @Test
    public void testInsertGroupCapacity() {
        Mockito.when(jdbcTemplate.update(any(PreparedStatementCreator.class),
                argThat((ArgumentMatcher<GeneratedKeyHolder>) keyHolder -> {
                    List<Map<String, Object>> keyList = new ArrayList<>();
                    Map<String, Object> keyMap = new HashMap<>();
                    Number number = 1;
                    keyMap.put("test", number);
                    keyList.add(keyMap);
                    List<Map<String, Object>> expect = keyHolder.getKeyList();
                    expect.addAll(keyList);
                    return false;
                }))).thenReturn(1);
        
        GroupCapacity capacity = new GroupCapacity();
        capacity.setGroup(GroupCapacityPersistService.CLUSTER);
        Assert.assertTrue(service.insertGroupCapacity(capacity));
        
        capacity.setGroup("test");
        Assert.assertTrue(service.insertGroupCapacity(capacity));
    }
    
    @Test
    public void testGetClusterUsage() {
        doReturn(new ConfigInfoMapperByMySql()).when(mapperManager).findMapper(any(), eq(TableConstant.CONFIG_INFO));
        
        List<GroupCapacity> list = new ArrayList<>();
        GroupCapacity groupCapacity = new GroupCapacity();
        groupCapacity.setId(1L);
        groupCapacity.setUsage(10);
        list.add(groupCapacity);
        
        String groupId = GroupCapacityPersistService.CLUSTER;
        when(jdbcTemplate.query(anyString(), eq(new Object[] {groupId}), any(RowMapper.class))).thenReturn(list);
        Assert.assertEquals(groupCapacity.getUsage().intValue(), service.getClusterUsage());
        
        when(jdbcTemplate.query(anyString(), eq(new Object[] {groupId}), any(RowMapper.class)))
                .thenReturn(new ArrayList<>());
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class))).thenReturn(20);
        Assert.assertEquals(20, service.getClusterUsage());
    }
    
    @Test
    public void testIncrementUsageWithDefaultQuotaLimit() {
        GroupCapacity groupCapacity = new GroupCapacity();
        Timestamp timestamp = new Timestamp(System.currentTimeMillis());
        groupCapacity.setGmtModified(timestamp);
        groupCapacity.setGroup("test");
        groupCapacity.setQuota(1);
        when(jdbcTemplate.update(anyString(), eq(timestamp), eq("test"), eq(1))).thenReturn(1);
        
        Assert.assertTrue(service.incrementUsageWithDefaultQuotaLimit(groupCapacity));
    }
    
    @Test
    public void testIncrementUsageWithQuotaLimit() {
        GroupCapacity groupCapacity = new GroupCapacity();
        Timestamp timestamp = new Timestamp(System.currentTimeMillis());
        groupCapacity.setGmtModified(timestamp);
        groupCapacity.setGroup("test2");
        when(jdbcTemplate.update(anyString(), eq(timestamp), eq("test2"))).thenReturn(1);
        
        Assert.assertTrue(service.incrementUsageWithQuotaLimit(groupCapacity));
    }
    
    @Test
    public void testIncrementUsage() {
        
        GroupCapacity groupCapacity = new GroupCapacity();
        Timestamp timestamp = new Timestamp(System.currentTimeMillis());
        groupCapacity.setGmtModified(timestamp);
        groupCapacity.setGroup("test3");
        when(jdbcTemplate.update(anyString(), eq(timestamp), eq("test3"))).thenReturn(1);
        
        Assert.assertTrue(service.incrementUsage(groupCapacity));
    }
    
    @Test
    public void testDecrementUsage() {
        GroupCapacity groupCapacity = new GroupCapacity();
        Timestamp timestamp = new Timestamp(System.currentTimeMillis());
        groupCapacity.setGmtModified(timestamp);
        groupCapacity.setGroup("test4");
        when(jdbcTemplate.update(anyString(), eq(timestamp), eq("test4"))).thenReturn(1);
        
        Assert.assertTrue(service.decrementUsage(groupCapacity));
    }
    
    @Test
    public void testUpdateGroupCapacity() {
        final MockedStatic<TimeUtils> timeUtilsMockedStatic = Mockito.mockStatic(TimeUtils.class);
        
        List<Object> argList = CollectionUtils.list();
        
        Integer quota = 1;
        argList.add(quota);
        
        Integer maxSize = 2;
        argList.add(maxSize);
        
        Integer maxAggrCount = 3;
        argList.add(maxAggrCount);
        
        Integer maxAggrSize = 4;
        argList.add(maxAggrSize);
        
        Timestamp timestamp = new Timestamp(System.currentTimeMillis());
        
        timeUtilsMockedStatic.when(TimeUtils::getCurrentTime).thenReturn(timestamp);
        argList.add(timestamp);
        
        String group = "test";
        argList.add(group);
        
        when(jdbcTemplate.update(anyString(), any(Object.class))).thenAnswer((Answer<Integer>) invocationOnMock -> {
            if (invocationOnMock.getArgument(1).equals(quota) && invocationOnMock.getArgument(2).equals(maxSize)
                    && invocationOnMock.getArgument(3).equals(maxAggrCount) && invocationOnMock.getArgument(4)
                    .equals(maxAggrSize) && invocationOnMock.getArgument(5).equals(timestamp) && invocationOnMock
                    .getArgument(6).equals(group)) {
                return 1;
            }
            return 0;
        });
        Assert.assertTrue(service.updateGroupCapacity(group, quota, maxSize, maxAggrCount, maxAggrSize));
        timeUtilsMockedStatic.close();
    }
    
    @Test
    public void testUpdateQuota() {
        
        List<Object> argList = CollectionUtils.list();
        
        Integer quota = 2;
        argList.add(quota);
        
        String group = "test2";
        argList.add(group);
        
        when(jdbcTemplate.update(anyString(), any(Object.class))).thenAnswer((Answer<Integer>) invocationOnMock -> {
            if (invocationOnMock.getArgument(1).equals(quota) && invocationOnMock.getArgument(3).equals(group)) {
                return 1;
            }
            return 0;
        });
        Assert.assertTrue(service.updateQuota(group, quota));
    }
    
    @Test
    public void testUpdateMaxSize() {
        
        List<Object> argList = CollectionUtils.list();
        
        Integer maxSize = 3;
        argList.add(maxSize);
        
        String group = "test3";
        argList.add(group);
        
        when(jdbcTemplate.update(anyString(), any(Object.class))).thenAnswer((Answer<Integer>) invocationOnMock -> {
            if (invocationOnMock.getArgument(1).equals(maxSize) && invocationOnMock.getArgument(3).equals(group)) {
                return 1;
            }
            return 0;
        });
        Assert.assertTrue(service.updateMaxSize(group, maxSize));
    }
    
    @Test
    public void testCorrectUsage() {
        
        String group = GroupCapacityPersistService.CLUSTER;
        Timestamp timestamp = new Timestamp(System.currentTimeMillis());
        
        when(jdbcTemplate.update(anyString(), eq(timestamp), eq(group))).thenReturn(1);
        Assert.assertTrue(service.correctUsage(group, timestamp));
        
        group = "test";
        when(jdbcTemplate.update(anyString(), eq(group), eq(timestamp), eq(group))).thenReturn(1);
        Assert.assertTrue(service.correctUsage(group, timestamp));
    }
    
    @Test
    public void testGetCapacityList4CorrectUsage() {
        
        List<GroupCapacity> list = new ArrayList<>();
        GroupCapacity groupCapacity = new GroupCapacity();
        groupCapacity.setGroup("test");
        list.add(groupCapacity);
        long lastId = 1;
        int pageSize = 1;
        
        when(jdbcTemplate.query(anyString(), eq(new Object[] {lastId, pageSize}), any(RowMapper.class)))
                .thenReturn(list);
        List<GroupCapacity> ret = service.getCapacityList4CorrectUsage(lastId, pageSize);
        
        Assert.assertEquals(list.size(), ret.size());
        Assert.assertEquals(groupCapacity.getGroup(), ret.get(0).getGroup());
    }
    
    @Test
    public void testDeleteGroupCapacity() {
        
        when(jdbcTemplate.update(any(PreparedStatementCreator.class))).thenReturn(1);
        Assert.assertTrue(service.deleteGroupCapacity("test"));
    }
}
