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

import com.alibaba.nacos.ai.model.AiResource;
import com.alibaba.nacos.api.model.Page;
import com.alibaba.nacos.persistence.datasource.DataSourceService;
import com.alibaba.nacos.persistence.datasource.DynamicDataSource;
import com.alibaba.nacos.persistence.repository.embedded.EmbeddedStorageContextHolder;
import com.alibaba.nacos.persistence.repository.embedded.operate.DatabaseOperate;
import com.alibaba.nacos.persistence.repository.embedded.sql.ModifyRequest;
import com.alibaba.nacos.plugin.datasource.MapperManager;
import com.alibaba.nacos.plugin.datasource.constants.TableConstant;
import com.alibaba.nacos.plugin.datasource.mapper.AiResourceMapper;
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
import org.springframework.jdbc.core.RowMapper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link EmbeddedAiResourcePersistServiceImpl}.
 */
@ExtendWith(MockitoExtension.class)
class EmbeddedAiResourcePersistServiceImplTest {
    
    private static final String DATA_SOURCE_TYPE = "unit-test-embedded-ai-resource";
    
    private static final ConfigurableEnvironment CACHED_ENVIRONMENT = EnvUtil.getEnvironment();
    
    @Mock
    private DatabaseOperate databaseOperate;
    
    @Mock
    private DynamicDataSource dynamicDataSource;
    
    @Mock
    private DataSourceService dataSourceService;
    
    private MockedStatic<DynamicDataSource> dynamicDataSourceMockedStatic;
    
    private EmbeddedAiResourcePersistServiceImpl service;
    
    @BeforeEach
    void setUp() {
        EnvUtil.setEnvironment(new StandardEnvironment());
        MapperManager.join(new UnitTestAiResourceMapper());
        dynamicDataSourceMockedStatic = Mockito.mockStatic(DynamicDataSource.class);
        dynamicDataSourceMockedStatic.when(DynamicDataSource::getInstance)
            .thenReturn(dynamicDataSource);
        when(dynamicDataSource.getDataSource()).thenReturn(dataSourceService);
        when(dataSourceService.getDataSourceType()).thenReturn(DATA_SOURCE_TYPE);
        service = new EmbeddedAiResourcePersistServiceImpl(databaseOperate);
    }
    
    @AfterEach
    void tearDown() {
        dynamicDataSourceMockedStatic.close();
        EmbeddedStorageContextHolder.cleanAllContext();
        EnvUtil.setEnvironment(CACHED_ENVIRONMENT);
    }
    
    @Test
    void insertShouldReturnInsertedId() {
        AiResource inserted = newResource();
        inserted.setId(7L);
        when(databaseOperate.blockUpdate()).thenReturn(true);
        when(databaseOperate.queryOne(anyString(), any(Object[].class),
            eq(AiResourceRowMappers.AI_RESOURCE_ROW_MAPPER))).thenReturn(inserted);
        
        assertEquals(7L, service.insert(newResource()));
    }
    
    @Test
    void findAndListShouldReadFromDatabaseOperate() {
        AiResource resource = newResource();
        when(databaseOperate.queryOne(anyString(), any(Object[].class),
            eq(AiResourceRowMappers.AI_RESOURCE_ROW_MAPPER))).thenReturn(resource);
        when(databaseOperate.queryOne(anyString(), any(Object[].class), eq(Integer.class)))
            .thenReturn(1);
        when(databaseOperate.queryMany(anyString(), any(Object[].class),
            Mockito.<RowMapper<AiResource>>any())).thenReturn(List.of(resource));
        
        assertEquals("skill-a", service.find(null, "skill-a", "skill").getName());
        QueryCondition condition = new QueryCondition();
        condition.setType("skill");
        condition.setNameLike("skill");
        condition.putOrGroup("name", List.of("skill-a"));
        Page<AiResource> page = service.list(condition, 1, 10);
        
        assertEquals(1, page.getTotalCount());
        assertEquals("skill-a", page.getPageItems().get(0).getName());
    }
    
    @Test
    void updateCasShouldRequireSuccessfulUpdateAndNewMetaVersion() {
        AiResource updated = newResource();
        updated.setMetaVersion(2L);
        when(databaseOperate.blockUpdate()).thenReturn(true, false);
        when(databaseOperate.queryOne(anyString(), any(Object[].class),
            eq(AiResourceRowMappers.AI_RESOURCE_ROW_MAPPER))).thenReturn(updated);
        
        assertTrue(service.updateMetaCas("public", "skill-a", "skill", 1L, newResource()));
        ModifyRequest metaUpdate = EmbeddedStorageContextHolder.getCurrentSqlContext().get(0);
        assertFalse(metaUpdate.getSql().contains("owner=?"));
        assertFalse(metaUpdate.getSql().contains("scope=?"));
        assertFalse(metaUpdate.getSql().contains("COALESCE"));
        assertEquals(9, metaUpdate.getArgs().length);
        assertEquals("public", metaUpdate.getArgs()[5]);
        assertEquals("skill-a", metaUpdate.getArgs()[6]);
        assertEquals("skill", metaUpdate.getArgs()[7]);
        assertEquals(1L, metaUpdate.getArgs()[8]);
        assertFalse(service.updateSourceCas("public", "skill-a", "skill", 1L, "builtin"));
    }
    
    @Test
    void deleteShouldReturnZeroWhenMissingAndOneWhenDeleted() {
        when(databaseOperate.queryOne(anyString(), any(Object[].class),
            eq(AiResourceRowMappers.AI_RESOURCE_ROW_MAPPER))).thenReturn(null, newResource());
        when(databaseOperate.blockUpdate()).thenReturn(true);
        
        assertEquals(0, service.delete("public", "skill-a", "skill"));
        assertEquals(1, service.delete("public", "skill-a", "skill"));
    }
    
    @Test
    void simpleUpdatesShouldReflectBlockUpdateResult() {
        when(databaseOperate.blockUpdate()).thenReturn(true, false);
        
        assertTrue(service.updateScope("public", "skill-a", "skill", "PUBLIC"));
        assertFalse(service.incrementDownloadCount("public", "skill-a", "skill", 1L));
    }
    
    private static AiResource newResource() {
        AiResource resource = new AiResource();
        resource.setId(1L);
        resource.setName("skill-a");
        resource.setType("skill");
        resource.setDesc("desc");
        resource.setStatus("enabled");
        resource.setNamespaceId("public");
        resource.setBizTags("[\"tag\"]");
        resource.setExt("{}");
        resource.setFrom("local");
        resource.setVersionInfo("{}");
        resource.setMetaVersion(1L);
        resource.setScope("PRIVATE");
        resource.setOwner("alice");
        resource.setDownloadCount(0L);
        return resource;
    }
    
    private static class UnitTestAiResourceMapper implements AiResourceMapper {
        
        @Override
        public String select(List<String> columns, List<String> where) {
            return "SELECT * FROM ai_resource";
        }
        
        @Override
        public String insert(List<String> columns) {
            return "INSERT INTO ai_resource";
        }
        
        @Override
        public String update(List<String> columns, List<String> where) {
            return "UPDATE ai_resource";
        }
        
        @Override
        public String delete(List<String> params) {
            return "DELETE FROM ai_resource";
        }
        
        @Override
        public String count(List<String> where) {
            return "SELECT count(*) FROM ai_resource";
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
        public MapperResult findAiResourceCountRows(MapperContext context) {
            return new MapperResult(count(List.of("namespace_id")), new ArrayList<>());
        }
        
        @Override
        public MapperResult findAiResourceFetchRows(MapperContext context) {
            return new MapperResult(select(Collections.emptyList(), Collections.emptyList()),
                new ArrayList<>());
        }
        
        @Override
        public String getTableName() {
            return TableConstant.AI_RESOURCE;
        }
    }
}
