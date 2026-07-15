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
import com.alibaba.nacos.persistence.repository.embedded.EmbeddedStorageContextHolder;
import com.alibaba.nacos.persistence.repository.embedded.operate.DatabaseOperate;
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
import org.springframework.jdbc.core.RowMapper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link EmbeddedAiResourceVersionPersistServiceImpl}.
 */
@ExtendWith(MockitoExtension.class)
class EmbeddedAiResourceVersionPersistServiceImplTest {
    
    private static final String DATA_SOURCE_TYPE = "unit-test-embedded-ai-resource-version";
    
    private static final ConfigurableEnvironment CACHED_ENVIRONMENT = EnvUtil.getEnvironment();
    
    @Mock
    private DatabaseOperate databaseOperate;
    
    @Mock
    private DynamicDataSource dynamicDataSource;
    
    @Mock
    private DataSourceService dataSourceService;
    
    private MockedStatic<DynamicDataSource> dynamicDataSourceMockedStatic;
    
    private EmbeddedAiResourceVersionPersistServiceImpl service;
    
    @BeforeEach
    void setUp() {
        EnvUtil.setEnvironment(new StandardEnvironment());
        MapperManager.join(new UnitTestAiResourceVersionMapper());
        dynamicDataSourceMockedStatic = Mockito.mockStatic(DynamicDataSource.class);
        dynamicDataSourceMockedStatic.when(DynamicDataSource::getInstance)
            .thenReturn(dynamicDataSource);
        when(dynamicDataSource.getDataSource()).thenReturn(dataSourceService);
        when(dataSourceService.getDataSourceType()).thenReturn(DATA_SOURCE_TYPE);
        service = new EmbeddedAiResourceVersionPersistServiceImpl(databaseOperate);
    }
    
    @AfterEach
    void tearDown() {
        dynamicDataSourceMockedStatic.close();
        EmbeddedStorageContextHolder.cleanAllContext();
        EnvUtil.setEnvironment(CACHED_ENVIRONMENT);
    }
    
    @Test
    void insertShouldReturnInsertedId() {
        AiResourceVersion inserted = newVersion();
        inserted.setId(8L);
        when(databaseOperate.blockUpdate()).thenReturn(true);
        when(databaseOperate.queryOne(anyString(), any(Object[].class),
            eq(AiResourceRowMappers.AI_RESOURCE_VERSION_ROW_MAPPER))).thenReturn(inserted);
        
        assertEquals(8L, service.insert(newVersion()));
    }
    
    @Test
    void findAndListShouldReadFromDatabaseOperate() {
        AiResourceVersion version = newVersion();
        when(databaseOperate.queryOne(anyString(), any(Object[].class),
            eq(AiResourceRowMappers.AI_RESOURCE_VERSION_ROW_MAPPER))).thenReturn(version);
        when(databaseOperate.queryOne(anyString(), any(Object[].class), eq(Integer.class)))
            .thenReturn(1);
        when(databaseOperate.queryMany(anyString(), any(Object[].class),
            Mockito.<RowMapper<AiResourceVersion>>any())).thenReturn(List.of(version));
        
        assertEquals("1.0.0", service.find(null, "skill-a", "skill", "1.0.0").getVersion());
        Page<AiResourceVersion> page = service.list(null, "skill-a", "skill", "published", 1,
            10);
        
        assertEquals(1, page.getTotalCount());
        assertEquals("1.0.0", page.getPageItems().get(0).getVersion());
    }
    
    @Test
    void deleteShouldCheckExistingVersion() {
        when(databaseOperate.queryOne(anyString(), any(Object[].class),
            eq(AiResourceRowMappers.AI_RESOURCE_VERSION_ROW_MAPPER))).thenReturn(null,
                newVersion());
        when(databaseOperate.blockUpdate()).thenReturn(true);
        
        assertEquals(0, service.delete("public", "skill-a", "skill", "1.0.0"));
        assertEquals(1, service.delete("public", "skill-a", "skill", "1.0.0"));
    }
    
    @Test
    void deleteByNameMethodsShouldReflectBlockUpdateResult() {
        when(databaseOperate.blockUpdate()).thenReturn(true, false);
        
        assertEquals(1, service.deleteByName("public", "skill-a"));
        assertEquals(0, service.deleteByNameAndType("public", "skill-a", "skill"));
    }
    
    @Test
    void updateMethodsShouldReturnZeroWhenVersionMissing() {
        when(databaseOperate.queryOne(anyString(), any(Object[].class),
            eq(AiResourceRowMappers.AI_RESOURCE_VERSION_ROW_MAPPER))).thenReturn(null);
        
        assertEquals(0, service.updateStatus("public", "skill-a", "skill", "1.0.0",
            "published"));
        assertEquals(0, service.updateStorage("public", "skill-a", "skill", "1.0.0", "{}"));
        assertEquals(0, service.updateStorageAndDesc("public", "skill-a", "skill", "1.0.0",
            "{}", "desc"));
        assertEquals(0, service.updatePublishPipelineInfo("public", "skill-a", "skill",
            "1.0.0", "{}"));
        assertEquals(0, service.incrementDownloadCount("public", "skill-a", "skill", "1.0.0",
            1L));
        assertEquals(0, service.updateStorageMd5("public", "skill-a", "skill", "1.0.0",
            "md5"));
    }
    
    @Test
    void updateMethodsShouldReflectBlockUpdateResult() {
        when(databaseOperate.queryOne(anyString(), any(Object[].class),
            eq(AiResourceRowMappers.AI_RESOURCE_VERSION_ROW_MAPPER))).thenReturn(newVersion());
        when(databaseOperate.blockUpdate()).thenReturn(true, true, true, true, true, true, true);
        
        assertEquals(1, service.updateStatus("public", "skill-a", "skill", "1.0.0",
            "published"));
        assertEquals(1, service.updateStorage("public", "skill-a", "skill", "1.0.0", "{}"));
        assertEquals(1, service.updateStorageAndDesc("public", "skill-a", "skill", "1.0.0",
            "{}", "desc"));
        assertEquals(1, service.updatePublishPipelineInfo("public", "skill-a", "skill",
            "1.0.0", "{}"));
        assertEquals(1, service.incrementDownloadCount("public", "skill-a", "skill", "1.0.0",
            1L));
        assertEquals(1, service.updateStorageMd5("public", "skill-a", "skill", "1.0.0",
            "md5"));
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
