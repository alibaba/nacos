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
import com.alibaba.nacos.ai.utils.HubResourceNameUtils;
import com.alibaba.nacos.api.ai.constant.AiConstants;
import com.alibaba.nacos.persistence.datasource.DataSourceService;
import com.alibaba.nacos.persistence.datasource.DynamicDataSource;
import com.alibaba.nacos.persistence.repository.embedded.EmbeddedStorageContextHolder;
import com.alibaba.nacos.persistence.repository.embedded.operate.BaseDatabaseOperate;
import com.alibaba.nacos.persistence.repository.embedded.operate.DatabaseOperate;
import com.alibaba.nacos.persistence.repository.embedded.sql.ModifyRequest;
import com.alibaba.nacos.plugin.datasource.MapperManager;
import com.alibaba.nacos.plugin.datasource.constants.CommonConstant;
import com.alibaba.nacos.plugin.datasource.constants.TableConstant;
import com.alibaba.nacos.plugin.datasource.mapper.AiResourceMapper;
import com.alibaba.nacos.sys.env.EnvUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedStatic;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.function.BiConsumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

/**
 * Executes the actual Hub storage adapters against the checkout's Derby schema.
 * Embedded writes use the real BaseDatabaseOperate transaction implementation.
 * A Mapper mock supplies Derby timestamp syntax without depending on the dialect plugin.
 */
class HubEntryStorageDerbyTest {
    
    private static final String HUB = AiConstants.Hub.NAMESPACE_ID;
    
    private static final String NAME = "find-skills-101";
    
    private HubDatabaseTestSupport database;
    
    private JdbcTemplate jdbcTemplate;
    
    private TransactionTemplate transactionTemplate;
    
    @BeforeEach
    void setUp() throws Exception {
        database = HubDatabaseTestSupport.open();
        SingleConnectionDataSource dataSource =
            new SingleConnectionDataSource(database.connection(), true);
        jdbcTemplate = new JdbcTemplate(dataSource);
        transactionTemplate = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
    }
    
    @AfterEach
    void tearDown() throws Exception {
        EmbeddedStorageContextHolder.cleanAllContext();
        if (database != null) {
            database.close();
        }
    }
    
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void readsMustIsolateHubNamespaceTypeAndOrigin(boolean embedded) throws Exception {
        long localId = insert(HUB, NAME, "skill", "local");
        long sourceId = insert("source-ns", NAME, "skill", "hub");
        long otherTypeId = insert(HUB, NAME, "prompt", "hub");
        HubEntryStorage storage = storage(embedded);
        assertNull(storage.findByInternalName(NAME));
        assertNull(storage.findByEntryId(localId));
        assertNull(storage.findByEntryId(sourceId));
        assertNull(storage.findByEntryId(otherTypeId));
        assertNull(storage.findByEntryId(-1));
        
        long hubId = insert(HUB, NAME, "skill", "hub");
        assertEquals(hubId, storage.findByInternalName(NAME).getId());
        assertEquals(NAME, storage.findByEntryId(hubId).getName());
    }
    
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void casMustPublishAllMutableFieldsWithoutChangingIdentity(boolean embedded) throws Exception {
        long id = insert(HUB, NAME, "skill", "hub");
        HubEntryStorage storage = storage(embedded);
        AiResource existing = storage.findByEntryId(id);
        AiResource next = desired("enable", "reference-v1");
        next.setDesc("shared description");
        next.setBizTags("[\"search\"]");
        next.setVersionInfo("{\"labels\":{\"latest\":\"1.0.0\"}}");
        assertTrue(storage.updateMetaCas(existing, 1, next));
        
        AiResource actual = storage.findByEntryId(id);
        assertEquals(id, actual.getId());
        assertEquals(HUB, actual.getNamespaceId());
        assertEquals(NAME, actual.getName());
        assertEquals("skill", actual.getType());
        assertEquals("hub", actual.getFrom());
        assertEquals("nacos-ai-hub", actual.getOwner());
        assertEquals("PUBLIC", actual.getScope());
        assertEquals(2L, actual.getMetaVersion());
        assertEquals(next.getStatus(), actual.getStatus());
        assertEquals(next.getDesc(), actual.getDesc());
        assertEquals(next.getBizTags(), actual.getBizTags());
        assertEquals(next.getExt(), actual.getExt());
        assertEquals(next.getVersionInfo(), actual.getVersionInfo());
    }
    
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void staleCasMustFailEvenWhenItRequestsTheWinnersExactValues(boolean embedded)
        throws Exception {
        long id = insert(HUB, NAME, "skill", "hub");
        HubEntryStorage storage = storage(embedded);
        AiResource existing = storage.findByEntryId(id);
        AiResource next = desired("enable", "reference-v1");
        assertTrue(storage.updateMetaCas(existing, 1, next));
        assertFalse(storage.updateMetaCas(existing, 1, next));
        assertFalse(storage.updateMetaCas(existing, 1, desired("disable", "reference-v2")));
        assertEquals(2L, storage.findByEntryId(id).getMetaVersion());
        assertEquals("reference-v1", storage.findByEntryId(id).getExt());
    }
    
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void casMustRejectWrongIdNameOrNonHubOrigin(boolean embedded) throws Exception {
        long id = insert(HUB, NAME, "skill", "hub");
        long localId = insert(HUB, NAME, "skill", "local");
        HubEntryStorage storage = storage(embedded);
        AiResource existing = storage.findByEntryId(id);
        existing.setId(localId);
        assertFalse(storage.updateMetaCas(existing, 1, desired("enable", "v1")));
        existing.setId(id + 1000);
        assertFalse(storage.updateMetaCas(existing, 1, desired("enable", "v1")));
        existing.setId(id);
        existing.setName("another-name-101");
        assertFalse(storage.updateMetaCas(existing, 1, desired("enable", "v1")));
        assertEquals(1L, storage.findByEntryId(id).getMetaVersion());
        assertEquals("disable",
            database.queryString("SELECT status FROM ai_resource WHERE id=?", localId));
    }
    
    @Test
    void supplementaryCharacterNameMustFitBothDatabaseColumns() throws Exception {
        String name = HubResourceNameUtils.buildInternalName("😀".repeat(300), 10L);
        long id = insert(HUB, name, "skill", "hub");
        database.update(
            "INSERT INTO ai_resource_version (namespace_id,name,type,version,status,storage) "
                + "VALUES (?,?,'skill','1.0.0','online','{}')",
            HUB, name);
        assertEquals(name, storage(true).findByEntryId(id).getName());
        assertEquals(name, database.queryString("SELECT name FROM ai_resource_version"));
        assertTrue(name.length() <= 256);
    }
    
    private HubEntryStorage storage(boolean embedded) {
        DynamicDataSource dynamic = mock(DynamicDataSource.class);
        DataSourceService source = mock(DataSourceService.class);
        MapperManager manager = mock(MapperManager.class);
        AiResourceMapper mapper = mock(AiResourceMapper.class);
        when(mapper.getFunction("NOW()")).thenReturn("CURRENT_TIMESTAMP");
        when(dynamic.getDataSource()).thenReturn(source);
        when(source.getDataSourceType()).thenReturn("derby");
        when(source.getJdbcTemplate()).thenReturn(jdbcTemplate);
        when(manager.findMapper("derby", TableConstant.AI_RESOURCE)).thenReturn(mapper);
        try (MockedStatic<DynamicDataSource> dataSources = mockStatic(DynamicDataSource.class);
            MockedStatic<MapperManager> managers = mockStatic(MapperManager.class);
            MockedStatic<EnvUtil> environment = mockStatic(EnvUtil.class)) {
            dataSources.when(DynamicDataSource::getInstance).thenReturn(dynamic);
            managers.when(() -> MapperManager.instance(false)).thenReturn(manager);
            environment.when(() -> EnvUtil.getProperty(CommonConstant.NACOS_PLUGIN_DATASOURCE_LOG,
                Boolean.class, false)).thenReturn(false);
            return embedded ? new EmbeddedHubEntryStorage(databaseOperate())
                : new ExternalHubEntryStorage();
        }
    }
    
    private DatabaseOperate databaseOperate() {
        DatabaseOperate operate = mock(DatabaseOperate.class, CALLS_REAL_METHODS);
        BaseDatabaseOperate base = mock(BaseDatabaseOperate.class, CALLS_REAL_METHODS);
        doAnswer(invocation -> base.update(transactionTemplate, jdbcTemplate,
            invocation.<List<ModifyRequest>>getArgument(0),
            invocation.<BiConsumer<Boolean, Throwable>>getArgument(1)))
            .when(operate).update(anyList(), any());
        doAnswer(invocation -> base.queryOne(jdbcTemplate, invocation.getArgument(0),
            invocation.<Object[]>getArgument(1), invocation.<RowMapper<AiResource>>getArgument(2)))
            .when(operate).queryOne(anyString(), any(Object[].class),
                org.mockito.Mockito.<RowMapper<AiResource>>any());
        return operate;
    }
    
    private long insert(String namespace, String name, String type, String origin)
        throws Exception {
        database.update("INSERT INTO ai_resource "
            + "(namespace_id,name,type,c_from,status,scope,owner,meta_version,ext,version_info) "
            + "VALUES (?,?,?,?,'disable','PUBLIC','nacos-ai-hub',1,'{}','{}')", namespace, name,
            type, origin);
        return database.queryLong(
            "SELECT id FROM ai_resource WHERE namespace_id=? AND name=? AND type=? AND c_from=?",
            namespace, name, type, origin);
    }
    
    private AiResource desired(String status, String reference) {
        AiResource next = new AiResource();
        next.setStatus(status);
        next.setExt(reference);
        next.setVersionInfo("{}");
        return next;
    }
}
