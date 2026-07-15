/*
 * Copyright 1999-2026 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.ai.service.repository;

import com.alibaba.nacos.ai.model.AiResourceVersion;
import com.alibaba.nacos.api.model.Page;
import com.alibaba.nacos.persistence.datasource.DataSourceService;
import com.alibaba.nacos.persistence.datasource.DynamicDataSource;
import com.alibaba.nacos.plugin.datasource.MapperManager;
import com.alibaba.nacos.plugin.datasource.constants.TableConstant;
import com.alibaba.nacos.plugin.datasource.mapper.AiResourceVersionMapper;
import com.alibaba.nacos.plugin.datasource.model.MapperContext;
import com.alibaba.nacos.plugin.datasource.model.MapperResult;
import com.alibaba.nacos.sys.env.EnvUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementCreator;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.KeyHolder;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link AiResourceVersionPersistServiceImpl}.
 */
@ExtendWith(MockitoExtension.class)
class AiResourceVersionPersistServiceImplTest {
    
    private static final String DATA_SOURCE_TYPE = "unit-test-ai-resource-version";
    
    private static final ConfigurableEnvironment CACHED_ENVIRONMENT = EnvUtil.getEnvironment();
    
    @Mock
    private DynamicDataSource dynamicDataSource;
    
    @Mock
    private DataSourceService dataSourceService;
    
    @Mock
    private JdbcTemplate jdbcTemplate;
    
    private MockedStatic<DynamicDataSource> dynamicDataSourceMockedStatic;
    
    private AiResourceVersionPersistServiceImpl service;
    
    @BeforeEach
    void setUp() {
        EnvUtil.setEnvironment(new StandardEnvironment());
        MapperManager.join(new UnitTestAiResourceVersionMapper());
        dynamicDataSourceMockedStatic = Mockito.mockStatic(DynamicDataSource.class);
        dynamicDataSourceMockedStatic.when(DynamicDataSource::getInstance)
            .thenReturn(dynamicDataSource);
        when(dynamicDataSource.getDataSource()).thenReturn(dataSourceService);
        when(dataSourceService.getJdbcTemplate()).thenReturn(jdbcTemplate);
        when(dataSourceService.getDataSourceType()).thenReturn(DATA_SOURCE_TYPE);
        service = new AiResourceVersionPersistServiceImpl();
    }
    
    @AfterEach
    void tearDown() {
        dynamicDataSourceMockedStatic.close();
        EnvUtil.setEnvironment(CACHED_ENVIRONMENT);
    }
    
    @Test
    void insertShouldReturnGeneratedId() throws Exception {
        stubGeneratedKey(8L);
        
        assertEquals(8L, service.insert(newVersion()));
    }
    
    @Test
    void findShouldReturnNullWhenRowDoesNotExist() {
        when(jdbcTemplate.queryForObject(anyString(), any(Object[].class),
            eq(AiResourceRowMappers.AI_RESOURCE_VERSION_ROW_MAPPER)))
            .thenThrow(new EmptyResultDataAccessException(1));
        
        assertNull(service.find(null, "skill-a", "skill", "1.0.0"));
    }
    
    @Test
    void listShouldUsePaginationHelper() {
        when(jdbcTemplate.queryForObject(anyString(), any(Object[].class), eq(Integer.class)))
            .thenReturn(1);
        when(jdbcTemplate.query(anyString(), any(Object[].class),
            Mockito.<RowMapper<AiResourceVersion>>any())).thenReturn(List.of(newVersion()));
        
        Page<AiResourceVersion> page = service.list(null, "skill-a", "skill", "published", 1,
            10);
        
        assertEquals(1, page.getTotalCount());
        assertEquals("1.0.0", page.getPageItems().get(0).getVersion());
    }
    
    @Test
    void updateAndDeleteMethodsShouldReturnAffectedRows() {
        doReturn(1).when(jdbcTemplate)
            .update(anyString(), any(Object.class), any(Object.class));
        doReturn(1).when(jdbcTemplate)
            .update(anyString(), any(Object.class), any(Object.class), any(Object.class));
        doReturn(1).when(jdbcTemplate)
            .update(anyString(), any(Object.class), any(Object.class), any(Object.class),
                any(Object.class));
        doReturn(1).when(jdbcTemplate)
            .update(anyString(), any(Object.class), any(Object.class), any(Object.class),
                any(Object.class), any(Object.class));
        doReturn(1).when(jdbcTemplate)
            .update(anyString(), any(Object.class), any(Object.class), any(Object.class),
                any(Object.class), any(Object.class), any(Object.class));
        when(jdbcTemplate.queryForObject(anyString(), any(Object[].class),
            eq(AiResourceRowMappers.AI_RESOURCE_VERSION_ROW_MAPPER))).thenReturn(newVersion());
        
        assertEquals(1, service.delete("public", "skill-a", "skill", "1.0.0"));
        assertEquals(1, service.deleteByName("public", "skill-a"));
        assertEquals(1, service.deleteByNameAndType("public", "skill-a", "skill"));
        assertEquals(1, service.updateStatus("public", "skill-a", "skill", "1.0.0",
            "published"));
        assertEquals(1, service.updateStorage("public", "skill-a", "skill", "1.0.0", "{}"));
        assertEquals(1, service.updateStorageAndDesc("public", "skill-a", "skill", "1.0.0",
            "{}", "desc"));
        assertEquals(1, service.updatePublishPipelineInfo("public", "skill-a", "skill",
            "1.0.0", "{}"));
        assertEquals(1, service.incrementDownloadCount("public", "skill-a", "skill", "1.0.0",
            2L));
        assertEquals(1, service.updateStorageMd5("public", "skill-a", "skill", "1.0.0",
            "md5"));
    }
    
    private void stubGeneratedKey(long id) throws Exception {
        doAnswer(invocation -> {
            PreparedStatementCreator creator = invocation.getArgument(0);
            KeyHolder keyHolder = invocation.getArgument(1);
            Connection connection = Mockito.mock(Connection.class);
            PreparedStatement statement = Mockito.mock(PreparedStatement.class);
            when(connection.prepareStatement(anyString(), any(String[].class)))
                .thenReturn(statement);
            creator.createPreparedStatement(connection);
            keyHolder.getKeyList().add(Collections.singletonMap("id", id));
            return 1;
        }).when(jdbcTemplate).update(any(PreparedStatementCreator.class), any(KeyHolder.class));
    }
    
    private static AiResourceVersion newVersion() {
        AiResourceVersion version = new AiResourceVersion();
        version.setId(1L);
        version.setType("skill");
        version.setAuthor("alice");
        version.setName("skill-a");
        version.setDesc("desc");
        version.setStatus("published");
        version.setVersion("1.0.0");
        version.setNamespaceId("public");
        version.setStorage("{}");
        version.setPublishPipelineInfo("{}");
        version.setDownloadCount(0L);
        return version;
    }
    
    private static class UnitTestAiResourceVersionMapper implements AiResourceVersionMapper {
        
        @Override
        public String select(List<String> columns, List<String> where) {
            return "SELECT * FROM ai_resource_version";
        }
        
        @Override
        public String insert(List<String> columns) {
            return "INSERT INTO ai_resource_version";
        }
        
        @Override
        public String update(List<String> columns, List<String> where) {
            return "UPDATE ai_resource_version";
        }
        
        @Override
        public String delete(List<String> params) {
            return "DELETE FROM ai_resource_version";
        }
        
        @Override
        public String count(List<String> where) {
            return "SELECT count(*) FROM ai_resource_version";
        }
        
        @Override
        public String getDataSource() {
            return DATA_SOURCE_TYPE;
        }
        
        @Override
        public String[] getPrimaryKeyGeneratedKeys() {
            return new String[] {"id"};
        }
        
        @Override
        public String getFunction(String functionName) {
            return "NOW()";
        }
        
        @Override
        public MapperResult findAiResourceVersionCountRows(MapperContext context) {
            return new MapperResult(count(List.of("namespace_id")), new ArrayList<>());
        }
        
        @Override
        public MapperResult findAiResourceVersionFetchRows(MapperContext context) {
            return new MapperResult(select(Collections.emptyList(), Collections.emptyList()),
                new ArrayList<>());
        }
        
        @Override
        public String getTableName() {
            return TableConstant.AI_RESOURCE_VERSION;
        }
    }
}
