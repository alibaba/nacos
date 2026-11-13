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

package com.alibaba.nacos.ai.hub;

import com.alibaba.nacos.ai.model.AiResource;
import com.alibaba.nacos.ai.service.repository.EmbeddedHubEntryStorage;
import com.alibaba.nacos.ai.service.repository.ExternalHubEntryStorage;
import com.alibaba.nacos.ai.service.repository.HubEntryStorage;
import com.alibaba.nacos.api.ai.constant.AiConstants;
import com.alibaba.nacos.persistence.datasource.DataSourceService;
import com.alibaba.nacos.persistence.datasource.DynamicDataSource;
import com.alibaba.nacos.persistence.repository.embedded.operate.DatabaseOperate;
import com.alibaba.nacos.plugin.datasource.MapperManager;
import com.alibaba.nacos.plugin.datasource.constants.CommonConstant;
import com.alibaba.nacos.plugin.datasource.constants.TableConstant;
import com.alibaba.nacos.plugin.datasource.mapper.AiResourceMapper;
import com.alibaba.nacos.sys.env.EnvUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedStatic;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

/** Verifies the actual portable Hub ID scan SQL in both storage adapters on Derby. */
class HubDirectoryStorageDerbyTest {
    
    private static final String HUB = AiConstants.Hub.NAMESPACE_ID;
    
    private HubDatabaseTestSupport database;
    
    private JdbcTemplate jdbc;
    
    @BeforeEach
    void open() throws Exception {
        database = HubDatabaseTestSupport.open();
        jdbc = new JdbcTemplate(new SingleConnectionDataSource(database.connection(), true));
    }
    
    @AfterEach
    void close() throws Exception {
        if (database != null) {
            database.close();
        }
    }
    
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void emptyHubHasNoScanAnchors(boolean embedded) {
        HubEntryStorage storage = createStorage(embedded);
        assertEquals(0L, storage.maxEntryId());
        assertNull(storage.nextEntryId(0L, 100L));
        assertTrue(storage.entriesInIdRange(0L, 100L).isEmpty());
    }
    
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void idScanIsolatesHubSkillAndNeverReturnsOtherOrigins(boolean embedded) throws Exception {
        long local = insert(HUB, "ordinary-skill", "skill", "local");
        long wrongType = insert(HUB, "another-type", "prompt", "hub");
        long wrongNamespace = insert("team-a", "outside-hub", "skill", "hub");
        long first = insert(HUB, "find-skills-101", "skill", "hub");
        long second = insert(HUB, "find-skills-201", "skill", "hub");
        HubEntryStorage storage = createStorage(embedded);
        assertEquals(second, storage.maxEntryId());
        assertEquals(Long.valueOf(first), storage.nextEntryId(0L, second));
        assertEquals(Long.valueOf(second), storage.nextEntryId(first, second));
        assertNull(storage.nextEntryId(second, second));
        assertNull(storage.nextEntryId(0L, first - 1L));
        List<AiResource> result = storage.entriesInIdRange(0L, second);
        assertEquals(2, result.size());
        assertEquals(Long.valueOf(first), result.get(0).getId());
        assertEquals(Long.valueOf(second), result.get(1).getId());
        assertTrue(result.stream().noneMatch(entry -> entry.getId() == local
            || entry.getId() == wrongType || entry.getId() == wrongNamespace));
    }
    
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void capturedUpperBoundExcludesNewHubRows(boolean embedded) throws Exception {
        long first = insert(HUB, "first-11", "skill", "hub");
        HubEntryStorage storage = createStorage(embedded);
        long upper = storage.maxEntryId();
        long later = insert(HUB, "later-12", "skill", "hub");
        assertEquals(first, upper);
        assertTrue(later > upper);
        assertNull(storage.nextEntryId(first, upper));
        assertEquals(1, storage.entriesInIdRange(1L, upper).size());
    }
    
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void disappearingAnchorProducesEmptyRangeButScanCanAdvance(boolean embedded)
        throws Exception {
        long id = insert(HUB, "find-skills-101", "skill", "hub");
        HubEntryStorage storage = createStorage(embedded);
        assertEquals(Long.valueOf(id), storage.nextEntryId(0L, id));
        database.update("DELETE FROM ai_resource WHERE id=?", id);
        assertTrue(storage.entriesInIdRange(id, id).isEmpty());
    }
    
    private HubEntryStorage createStorage(boolean embedded) {
        DynamicDataSource dynamic = mock(DynamicDataSource.class);
        DataSourceService source = mock(DataSourceService.class);
        MapperManager mapperManager = mock(MapperManager.class);
        AiResourceMapper mapper = mock(AiResourceMapper.class);
        when(mapper.getFunction("NOW()")).thenReturn("CURRENT_TIMESTAMP");
        when(dynamic.getDataSource()).thenReturn(source);
        when(source.getDataSourceType()).thenReturn("derby");
        when(source.getJdbcTemplate()).thenReturn(jdbc);
        when(mapperManager.findMapper("derby", TableConstant.AI_RESOURCE)).thenReturn(mapper);
        try (MockedStatic<DynamicDataSource> dataSources = mockStatic(DynamicDataSource.class);
            MockedStatic<MapperManager> managers = mockStatic(MapperManager.class);
            MockedStatic<EnvUtil> environment = mockStatic(EnvUtil.class)) {
            dataSources.when(DynamicDataSource::getInstance).thenReturn(dynamic);
            managers.when(() -> MapperManager.instance(false)).thenReturn(mapperManager);
            environment.when(() -> EnvUtil.getProperty(CommonConstant.NACOS_PLUGIN_DATASOURCE_LOG,
                Boolean.class, false)).thenReturn(false);
            return embedded ? new EmbeddedHubEntryStorage(databaseOperate())
                : new ExternalHubEntryStorage();
        }
    }
    
    private DatabaseOperate databaseOperate() {
        DatabaseOperate operate = mock(DatabaseOperate.class);
        when(operate.queryOne(anyString(), any(Object[].class), eq(Long.class)))
            .thenAnswer(invocation -> jdbc.queryForObject(invocation.getArgument(0),
                Long.class, invocation.<Object[]>getArgument(1)));
        when(operate.queryMany(anyString(), any(Object[].class),
            org.mockito.ArgumentMatchers.<RowMapper<AiResource>>any()))
            .thenAnswer(invocation -> jdbc.query(invocation.getArgument(0),
                invocation.<Object[]>getArgument(1),
                invocation.<RowMapper<AiResource>>getArgument(2)));
        return operate;
    }
    
    private long insert(String namespace, String name, String type, String origin)
        throws Exception {
        database.update("INSERT INTO ai_resource "
            + "(namespace_id,name,type,c_from,status,scope,owner,meta_version,ext,version_info) "
            + "VALUES (?,?,?,?,'enable','PUBLIC','nacos-ai-hub',1,'{}','{}')",
            namespace, name, type, origin);
        return database.queryLong("SELECT id FROM ai_resource WHERE namespace_id=? "
            + "AND name=? AND type=? AND c_from=?", namespace, name, type, origin);
    }
}
