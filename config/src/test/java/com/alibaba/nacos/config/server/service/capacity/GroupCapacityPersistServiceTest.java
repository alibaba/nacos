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
import com.alibaba.nacos.config.server.utils.TimeUtils;
import com.alibaba.nacos.persistence.datasource.DataSourceService;
import com.alibaba.nacos.persistence.datasource.DynamicDataSource;
import com.alibaba.nacos.plugin.datasource.MapperManager;
import com.alibaba.nacos.plugin.datasource.constants.CommonConstant;
import com.alibaba.nacos.plugin.datasource.constants.TableConstant;
import com.alibaba.nacos.plugin.datasource.impl.mysql.ConfigInfoMapperByMySql;
import com.alibaba.nacos.plugin.datasource.impl.mysql.GroupCapacityMapperByMysql;
import com.alibaba.nacos.sys.env.EnvUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.stubbing.Answer;
import org.springframework.jdbc.CannotGetJdbcConnectionException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.jdbc.core.PreparedStatementCreator;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.test.util.ReflectionTestUtils;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class GroupCapacityPersistServiceTest {
    
    MockedStatic<TimeUtils> timeUtilsMockedStatic;
    
    @InjectMocks
    private GroupCapacityPersistService service;
    
    @Mock
    private JdbcTemplate jdbcTemplate;
    
    @Mock
    private DataSourceService dataSourceService;
    
    @Mock
    private MapperManager mapperManager;
    
    @AfterEach
    void after() {
        timeUtilsMockedStatic.close();
    }
    
    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "jdbcTemplate", jdbcTemplate);
        ReflectionTestUtils.setField(service, "dataSourceService", dataSourceService);
        ReflectionTestUtils.setField(service, "mapperManager", mapperManager);
        when(dataSourceService.getJdbcTemplate()).thenReturn(jdbcTemplate);
        doReturn(new GroupCapacityMapperByMysql()).when(mapperManager).findMapper(any(),
            eq(TableConstant.GROUP_CAPACITY));
        timeUtilsMockedStatic = Mockito.mockStatic(TimeUtils.class);
        
    }
    
    @Test
    void testGetGroupCapacity() {
        
        List<GroupCapacity> list = new ArrayList<>();
        GroupCapacity groupCapacity = new GroupCapacity();
        groupCapacity.setGroupName("test");
        list.add(groupCapacity);
        
        String groupId = "testId";
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), eq(new Object[] {groupId})))
            .thenReturn(list);
        GroupCapacity ret = service.getGroupCapacity(groupId);
        
        assertEquals(groupCapacity.getGroupName(), ret.getGroupName());
    }
    
    @Test
    void testInit() {
        DynamicDataSource dynamicDataSource = Mockito.mock(DynamicDataSource.class);
        try (MockedStatic<DynamicDataSource> dynamicDataSourceMockedStatic =
            Mockito.mockStatic(DynamicDataSource.class);
            MockedStatic<EnvUtil> envUtilMockedStatic = Mockito.mockStatic(EnvUtil.class);
            MockedStatic<MapperManager> mapperManagerMockedStatic =
                Mockito.mockStatic(MapperManager.class)) {
            dynamicDataSourceMockedStatic.when(DynamicDataSource::getInstance)
                .thenReturn(dynamicDataSource);
            when(dynamicDataSource.getDataSource()).thenReturn(dataSourceService);
            when(dataSourceService.getJdbcTemplate()).thenReturn(jdbcTemplate);
            envUtilMockedStatic.when(() -> EnvUtil.getProperty(
                CommonConstant.NACOS_PLUGIN_DATASOURCE_LOG, Boolean.class, false))
                .thenReturn(true);
            mapperManagerMockedStatic.when(() -> MapperManager.instance(true))
                .thenReturn(mapperManager);
            
            service.init();
        }
        
        assertEquals(jdbcTemplate, ReflectionTestUtils.getField(service, "jdbcTemplate"));
        assertEquals(dataSourceService,
            ReflectionTestUtils.getField(service, "dataSourceService"));
        assertEquals(mapperManager, ReflectionTestUtils.getField(service, "mapperManager"));
    }
    
    @Test
    void testGetClusterCapacity() {
        
        List<GroupCapacity> list = new ArrayList<>();
        GroupCapacity groupCapacity = new GroupCapacity();
        groupCapacity.setId(1L);
        list.add(groupCapacity);
        
        String groupId = GroupCapacityPersistService.CLUSTER;
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), eq(new Object[] {groupId})))
            .thenReturn(list);
        Capacity ret = service.getClusterCapacity();
        
        assertEquals(groupCapacity.getId(), ret.getId());
    }
    
    @Test
    void testInsertGroupCapacity() {
        
        doReturn(1).when(jdbcTemplate).update(anyString(), eq(""), eq(null), eq(null), eq(null),
            eq(null), eq(null), eq(null));
        // when(jdbcTemplate.update(anyString(), eq(timestamp), eq("test3"))).thenReturn(1);
        
        GroupCapacity capacity = new GroupCapacity();
        capacity.setGroupName(GroupCapacityPersistService.CLUSTER);
        assertTrue(service.insertGroupCapacity(capacity));
        
        capacity.setGroupName("test");
        doReturn(1).when(jdbcTemplate)
            .update(anyString(), eq("test"), eq(null), eq(null), eq(null), eq(null), eq(null),
                eq(null), eq("test"));
        
        assertTrue(service.insertGroupCapacity(capacity));
    }
    
    @Test
    void testInsertGroupCapacityReturnsFalseWhenNoRowUpdated() {
        GroupCapacity capacity = new GroupCapacity();
        capacity.setGroupName(GroupCapacityPersistService.CLUSTER);
        doReturn(0).when(jdbcTemplate).update(anyString(), eq(""), eq(null), eq(null), eq(null),
            eq(null), eq(null), eq(null));
        
        assertFalse(service.insertGroupCapacity(capacity));
    }
    
    @Test
    void testGetClusterUsage() {
        doReturn(new ConfigInfoMapperByMySql()).when(mapperManager).findMapper(any(),
            eq(TableConstant.CONFIG_INFO));
        
        List<GroupCapacity> list = new ArrayList<>();
        GroupCapacity groupCapacity = new GroupCapacity();
        groupCapacity.setId(1L);
        groupCapacity.setUsage(10);
        list.add(groupCapacity);
        
        String groupId = GroupCapacityPersistService.CLUSTER;
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), eq(new Object[] {groupId})))
            .thenReturn(list);
        assertEquals(groupCapacity.getUsage().intValue(), service.getClusterUsage());
        
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), eq(new Object[] {groupId})))
            .thenReturn(new ArrayList<>());
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class))).thenReturn(20);
        assertEquals(20, service.getClusterUsage());
    }
    
    @Test
    void testIncrementUsageWithDefaultQuotaLimit() {
        GroupCapacity groupCapacity = new GroupCapacity();
        Timestamp timestamp = new Timestamp(System.currentTimeMillis());
        groupCapacity.setGmtModified(timestamp);
        groupCapacity.setGroupName("test");
        groupCapacity.setQuota(1);
        when(jdbcTemplate.update(anyString(), eq(timestamp), eq("test"), eq(1))).thenReturn(1);
        
        assertTrue(service.incrementUsageWithDefaultQuotaLimit(groupCapacity));
        
        //mock get connection fail
        when(jdbcTemplate.update(anyString(), eq(timestamp), eq("test"), eq(1))).thenThrow(
            new CannotGetJdbcConnectionException("conn fail"));
        try {
            service.incrementUsageWithDefaultQuotaLimit(groupCapacity);
            assertTrue(false);
        } catch (Exception e) {
            assertEquals("conn fail", e.getMessage());
        }
    }
    
    @Test
    void testIncrementUsageMethodsReturnFalseWhenNoRowUpdated() {
        GroupCapacity groupCapacity = new GroupCapacity();
        Timestamp timestamp = new Timestamp(System.currentTimeMillis());
        groupCapacity.setGmtModified(timestamp);
        groupCapacity.setGroupName("test");
        groupCapacity.setQuota(1);
        
        when(jdbcTemplate.update(anyString(), eq(timestamp), eq("test"), eq(1))).thenReturn(0);
        assertFalse(service.incrementUsageWithDefaultQuotaLimit(groupCapacity));
        
        when(jdbcTemplate.update(anyString(), eq(timestamp), eq("test"))).thenReturn(0);
        assertFalse(service.incrementUsageWithQuotaLimit(groupCapacity));
        assertFalse(service.incrementUsage(groupCapacity));
        assertFalse(service.decrementUsage(groupCapacity));
    }
    
    @Test
    void testIncrementUsageWithQuotaLimit() {
        GroupCapacity groupCapacity = new GroupCapacity();
        Timestamp timestamp = new Timestamp(System.currentTimeMillis());
        groupCapacity.setGmtModified(timestamp);
        groupCapacity.setGroupName("test2");
        when(jdbcTemplate.update(anyString(), eq(timestamp), eq("test2"))).thenReturn(1);
        
        assertTrue(service.incrementUsageWithQuotaLimit(groupCapacity));
        
        //mock get connection fail
        when(jdbcTemplate.update(anyString(), eq(timestamp), eq("test2")))
            .thenThrow(new CannotGetJdbcConnectionException("conn fail"));
        try {
            service.incrementUsageWithQuotaLimit(groupCapacity);
            assertTrue(false);
        } catch (Exception e) {
            assertEquals("conn fail", e.getMessage());
        }
    }
    
    @Test
    void testIncrementUsage() {
        
        GroupCapacity groupCapacity = new GroupCapacity();
        Timestamp timestamp = new Timestamp(System.currentTimeMillis());
        groupCapacity.setGmtModified(timestamp);
        groupCapacity.setGroupName("test3");
        when(jdbcTemplate.update(anyString(), eq(timestamp), eq("test3"))).thenReturn(1);
        
        assertTrue(service.incrementUsage(groupCapacity));
        
        //mock get connection fail
        when(jdbcTemplate.update(anyString(), eq(timestamp), eq("test3")))
            .thenThrow(new CannotGetJdbcConnectionException("conn fail"));
        try {
            service.incrementUsage(groupCapacity);
            assertTrue(false);
        } catch (Exception e) {
            assertEquals("conn fail", e.getMessage());
        }
    }
    
    @Test
    void testDecrementUsage() {
        GroupCapacity groupCapacity = new GroupCapacity();
        Timestamp timestamp = new Timestamp(System.currentTimeMillis());
        groupCapacity.setGmtModified(timestamp);
        groupCapacity.setGroupName("test4");
        when(jdbcTemplate.update(anyString(), eq(timestamp), eq("test4"))).thenReturn(1);
        
        assertTrue(service.decrementUsage(groupCapacity));
        
        //mock get connection fail
        when(jdbcTemplate.update(anyString(), eq(timestamp), eq("test4")))
            .thenThrow(new CannotGetJdbcConnectionException("conn fail"));
        try {
            service.decrementUsage(groupCapacity);
            assertTrue(false);
        } catch (Exception e) {
            assertEquals("conn fail", e.getMessage());
        }
    }
    
    @Test
    void testUpdateGroupCapacity() {
        
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
        when(TimeUtils.getCurrentTime()).thenReturn(timestamp);
        argList.add(timestamp);
        
        String group = "test";
        argList.add(group);
        
        when(jdbcTemplate.update(anyString(), any(Object.class)))
            .thenAnswer((Answer<Integer>) invocationOnMock -> {
                if (invocationOnMock.getArgument(1).equals(quota)
                    && invocationOnMock.getArgument(2).equals(maxSize)
                    && invocationOnMock.getArgument(3).equals(maxAggrCount)
                    && invocationOnMock.getArgument(4).equals(maxAggrSize)
                    && invocationOnMock.getArgument(5).equals(timestamp)
                    && invocationOnMock.getArgument(6).equals(group)) {
                    return 1;
                }
                return 0;
            });
        assertTrue(service.updateGroupCapacity(group, quota, maxSize, maxAggrCount, maxAggrSize));
        
        //mock get connection fail
        when(jdbcTemplate.update(anyString(), any(Object.class)))
            .thenThrow(new CannotGetJdbcConnectionException("conn fail"));
        try {
            service.updateGroupCapacity(group, quota, maxSize, maxAggrCount, maxAggrSize);
            assertTrue(false);
        } catch (Exception e) {
            assertEquals("conn fail", e.getMessage());
        }
    }
    
    @Test
    void testGroupCapacityRowMapper() throws SQLException {
        GroupCapacityPersistService.GroupCapacityRowMapper groupCapacityRowMapper =
            new GroupCapacityPersistService.GroupCapacityRowMapper();
        ResultSet rs = Mockito.mock(ResultSet.class);
        int quota = 12345;
        Mockito.when(rs.getInt(eq("quota"))).thenReturn(quota);
        int usage = 1244;
        Mockito.when(rs.getInt(eq("usage"))).thenReturn(usage);
        int maxSize = 123;
        Mockito.when(rs.getInt(eq("max_size"))).thenReturn(maxSize);
        int maxAggrCount = 123;
        Mockito.when(rs.getInt(eq("max_aggr_count"))).thenReturn(maxAggrCount);
        int maxAggrSize = 123;
        Mockito.when(rs.getInt(eq("max_aggr_size"))).thenReturn(maxAggrSize);
        String group = "testG";
        Mockito.when(rs.getString(eq("group_id"))).thenReturn(group);
        
        GroupCapacity groupCapacity = groupCapacityRowMapper.mapRow(rs, 1);
        assertEquals(quota, groupCapacity.getQuota().intValue());
        assertEquals(usage, groupCapacity.getUsage().intValue());
        assertEquals(maxSize, groupCapacity.getMaxSize().intValue());
        assertEquals(maxAggrCount, groupCapacity.getMaxAggrCount().intValue());
        assertEquals(maxAggrSize, groupCapacity.getMaxAggrSize().intValue());
        assertEquals(group, groupCapacity.getGroupName());
    }
    
    @Test
    void testUpdateQuota() {
        Timestamp timestamp = new Timestamp(System.currentTimeMillis());
        when(TimeUtils.getCurrentTime()).thenReturn(timestamp);
        List<Object> argList = CollectionUtils.list();
        
        Integer quota = 2;
        argList.add(quota);
        
        String group = "test2";
        argList.add(group);
        
        when(jdbcTemplate.update(anyString(), eq(2), eq(timestamp), eq(group))).thenReturn(1);
        
        assertTrue(service.updateQuota(group, quota));
    }
    
    @Test
    void testUpdateMaxSize() {
        
        List<Object> argList = CollectionUtils.list();
        Timestamp timestamp = new Timestamp(System.currentTimeMillis());
        when(TimeUtils.getCurrentTime()).thenReturn(timestamp);
        Integer maxSize = 3;
        argList.add(maxSize);
        
        String group = "test3";
        argList.add(group);
        when(jdbcTemplate.update(anyString(), eq(3), eq(timestamp), eq(group))).thenReturn(1);
        
        assertTrue(service.updateMaxSize(group, maxSize));
    }
    
    @Test
    void testUpdateQuotaReturnsFalseWhenNoRowUpdated() {
        Timestamp timestamp = new Timestamp(System.currentTimeMillis());
        when(TimeUtils.getCurrentTime()).thenReturn(timestamp);
        when(jdbcTemplate.update(anyString(), eq(1), eq(timestamp), eq("test")))
            .thenReturn(0);
        
        assertFalse(service.updateQuota("test", 1));
    }
    
    @Test
    void testCorrectUsage() {
        
        String group = GroupCapacityPersistService.CLUSTER;
        Timestamp timestamp = new Timestamp(System.currentTimeMillis());
        
        when(jdbcTemplate.update(anyString(), eq(timestamp), eq(group))).thenReturn(1);
        assertTrue(service.correctUsage(group, timestamp));
        
        group = "test";
        when(jdbcTemplate.update(anyString(), eq(group), eq(timestamp), eq(group))).thenReturn(1);
        assertTrue(service.correctUsage(group, timestamp));
        
        //mock get connection fail
        when(jdbcTemplate.update(anyString(), eq(group), eq(timestamp), eq(group))).thenThrow(
            new CannotGetJdbcConnectionException("conn fail"));
        try {
            service.correctUsage(group, timestamp);
            assertTrue(false);
        } catch (Exception e) {
            assertEquals("conn fail", e.getMessage());
        }
    }
    
    @Test
    void testCorrectUsageClusterConnectionFailure() {
        Timestamp timestamp = new Timestamp(System.currentTimeMillis());
        String cluster = GroupCapacityPersistService.CLUSTER;
        when(jdbcTemplate.update(anyString(), eq(timestamp), eq(cluster)))
            .thenThrow(new CannotGetJdbcConnectionException("conn fail"));
        
        CannotGetJdbcConnectionException actual = assertThrows(
            CannotGetJdbcConnectionException.class,
            () -> service.correctUsage(cluster, timestamp));
        
        assertEquals("conn fail", actual.getMessage());
    }
    
    @Test
    void testCorrectUsageReturnsFalseWhenNoRowUpdated() {
        Timestamp timestamp = new Timestamp(System.currentTimeMillis());
        String cluster = GroupCapacityPersistService.CLUSTER;
        when(jdbcTemplate.update(anyString(), eq(timestamp), eq(cluster)))
            .thenReturn(0);
        assertFalse(service.correctUsage(cluster, timestamp));
        
        when(jdbcTemplate.update(anyString(), eq("test"), eq(timestamp), eq("test")))
            .thenReturn(0);
        assertFalse(service.correctUsage("test", timestamp));
    }
    
    @Test
    void testGetCapacityList4CorrectUsage() {
        
        List<GroupCapacity> list = new ArrayList<>();
        GroupCapacity groupCapacity = new GroupCapacity();
        groupCapacity.setGroupName("test");
        list.add(groupCapacity);
        long lastId = 1;
        int pageSize = 1;
        
        when(jdbcTemplate.query(anyString(), eq(new Object[] {lastId, pageSize}),
            any(RowMapper.class))).thenReturn(list);
        List<GroupCapacity> ret = service.getCapacityList4CorrectUsage(lastId, pageSize);
        
        assertEquals(list.size(), ret.size());
        assertEquals(groupCapacity.getGroupName(), ret.get(0).getGroupName());
        
        //mock get connection fail
        when(jdbcTemplate.query(anyString(), eq(new Object[] {lastId, pageSize}),
            any(RowMapper.class))).thenThrow(
                new CannotGetJdbcConnectionException("conn fail"));
        try {
            service.getCapacityList4CorrectUsage(lastId, pageSize);
            assertTrue(false);
        } catch (Exception e) {
            assertEquals("conn fail", e.getMessage());
        }
    }
    
    @Test
    void testGetClusterUsageNullResult() {
        doReturn(new ConfigInfoMapperByMySql()).when(mapperManager).findMapper(any(),
            eq(TableConstant.CONFIG_INFO));
        String groupId = GroupCapacityPersistService.CLUSTER;
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), eq(new Object[] {groupId})))
            .thenReturn(new ArrayList<>());
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class))).thenReturn(null);
        try {
            service.getClusterUsage();
            assertTrue(false);
        } catch (IllegalArgumentException e) {
            assertEquals("configInfoCount error", e.getMessage());
        }
    }
    
    @Test
    void testDeleteGroupCapacity() {
        
        when(jdbcTemplate.update(any(PreparedStatementCreator.class))).thenReturn(1);
        assertTrue(service.deleteGroupCapacity("test"));
        
        //mock get connection fail
        when(jdbcTemplate.update(any(PreparedStatementCreator.class)))
            .thenThrow(new CannotGetJdbcConnectionException("conn fail"));
        try {
            service.deleteGroupCapacity("test");
            assertTrue(false);
        } catch (Exception e) {
            assertEquals("conn fail", e.getMessage());
        }
    }
    
    @Test
    void testDeleteGroupCapacityReturnsFalseWhenNoRowUpdated() {
        when(jdbcTemplate.update(any(PreparedStatementCreator.class))).thenReturn(0);
        
        assertFalse(service.deleteGroupCapacity("test"));
    }
    
    @Test
    void testGetCapacityList4CorrectUsageRowMapper() {
        long lastId = 1;
        int pageSize = 1;
        
        when(jdbcTemplate.query(anyString(), any(Object[].class), any(RowMapper.class)))
            .thenAnswer((Answer<List<GroupCapacity>>) invocation -> {
                RowMapper<GroupCapacity> rowMapper = invocation.getArgument(2);
                ResultSet rs = Mockito.mock(ResultSet.class);
                Mockito.when(rs.getLong("id")).thenReturn(100L);
                Mockito.when(rs.getString("group_id")).thenReturn("testGroup");
                List<GroupCapacity> result = new ArrayList<>();
                result.add(rowMapper.mapRow(rs, 1));
                return result;
            });
        
        List<GroupCapacity> ret = service.getCapacityList4CorrectUsage(lastId, pageSize);
        assertEquals(1, ret.size());
        assertEquals(100L, ret.get(0).getId().longValue());
        assertEquals("testGroup", ret.get(0).getGroupName());
    }
    
    @Test
    void testDeleteGroupCapacityPreparedStatementCreator() throws Exception {
        Connection connection = Mockito.mock(Connection.class);
        PreparedStatement ps = Mockito.mock(PreparedStatement.class);
        Mockito.when(connection.prepareStatement(anyString())).thenReturn(ps);
        
        when(jdbcTemplate.update(any(PreparedStatementCreator.class)))
            .thenAnswer((Answer<Integer>) invocation -> {
                PreparedStatementCreator creator = invocation.getArgument(0);
                creator.createPreparedStatement(connection);
                return 1;
            });
        
        assertTrue(service.deleteGroupCapacity("testGroup"));
        Mockito.verify(ps).setString(1, "testGroup");
    }
}
