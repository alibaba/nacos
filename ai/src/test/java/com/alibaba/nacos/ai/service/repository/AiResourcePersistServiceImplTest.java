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
import com.alibaba.nacos.ai.service.visibility.DefaultVisibilityAdvisorConverter;
import com.alibaba.nacos.api.model.Page;
import com.alibaba.nacos.persistence.datasource.DataSourceService;
import com.alibaba.nacos.persistence.datasource.DynamicDataSource;
import com.alibaba.nacos.plugin.datasource.MapperManager;
import com.alibaba.nacos.plugin.datasource.constants.FieldConstant;
import com.alibaba.nacos.plugin.datasource.constants.TableConstant;
import com.alibaba.nacos.plugin.datasource.mapper.AiResourceMapper;
import com.alibaba.nacos.plugin.datasource.mapper.ext.WhereBuilder;
import com.alibaba.nacos.plugin.datasource.model.MapperContext;
import com.alibaba.nacos.plugin.datasource.model.MapperResult;
import com.alibaba.nacos.plugin.visibility.model.BaseVisibilityPredicate;
import com.alibaba.nacos.plugin.visibility.model.VisibilityQueryContext;
import com.alibaba.nacos.plugin.visibility.spi.QueryAdvisor;
import com.alibaba.nacos.sys.env.EnvUtil;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link AiResourcePersistServiceImpl}.
 */
@ExtendWith(MockitoExtension.class)
class AiResourcePersistServiceImplTest {
    
    private static final String DATA_SOURCE_TYPE = "unit-test-ai-resource";
    
    private static final ConfigurableEnvironment CACHED_ENVIRONMENT = EnvUtil.getEnvironment();
    
    @Mock
    private DynamicDataSource dynamicDataSource;
    
    @Mock
    private DataSourceService dataSourceService;
    
    @Mock
    private JdbcTemplate jdbcTemplate;
    
    private MockedStatic<DynamicDataSource> dynamicDataSourceMockedStatic;
    
    private AiResourcePersistServiceImpl service;
    
    private JdbcTemplate realJdbcTemplate;
    
    @BeforeEach
    void setUp() {
        EnvUtil.setEnvironment(new StandardEnvironment());
        MapperManager.join(new UnitTestAiResourceMapper());
        dynamicDataSourceMockedStatic = Mockito.mockStatic(DynamicDataSource.class);
        dynamicDataSourceMockedStatic.when(DynamicDataSource::getInstance)
            .thenReturn(dynamicDataSource);
        when(dynamicDataSource.getDataSource()).thenReturn(dataSourceService);
        when(dataSourceService.getJdbcTemplate()).thenReturn(jdbcTemplate);
        when(dataSourceService.getDataSourceType()).thenReturn(DATA_SOURCE_TYPE);
        service = new AiResourcePersistServiceImpl();
    }
    
    @AfterEach
    void tearDown() {
        if (realJdbcTemplate != null) {
            realJdbcTemplate.execute("SHUTDOWN");
        }
        dynamicDataSourceMockedStatic.close();
        EnvUtil.setEnvironment(CACHED_ENVIRONMENT);
    }
    
    @Test
    void insertShouldReturnGeneratedIdAndApplyDefaults() throws Exception {
        stubGeneratedKey(7L);
        AiResource resource = newResource();
        resource.setNamespaceId(null);
        resource.setFrom(null);
        resource.setMetaVersion(null);
        resource.setScope(null);
        resource.setOwner(null);
        
        long id = service.insert(resource);
        
        assertEquals(7L, id);
    }
    
    @Test
    void findShouldReturnNullWhenRowDoesNotExist() {
        when(jdbcTemplate.queryForObject(anyString(), any(Object[].class),
            eq(AiResourceRowMappers.AI_RESOURCE_ROW_MAPPER)))
            .thenThrow(new EmptyResultDataAccessException(1));
        
        assertNull(service.find(null, "skill-a", "skill"));
    }
    
    @Test
    void listShouldUsePaginationHelper() {
        AiResource resource = newResource();
        when(jdbcTemplate.queryForObject(anyString(), any(Object[].class), eq(Integer.class)))
            .thenReturn(1);
        when(jdbcTemplate.query(anyString(), any(Object[].class),
            Mockito.<RowMapper<AiResource>>any())).thenReturn(List.of(resource));
        QueryCondition condition = new QueryCondition();
        condition.setType("skill");
        condition.setNameLike("skill");
        condition.setBizTagsLike("tag");
        condition.setScope("PUBLIC");
        condition.setOwner("alice");
        condition.setOrderBy("gmt_modified");
        condition.putOrGroup("name", List.of("skill-a"));
        
        Page<AiResource> page = service.list(condition, 1, 10);
        
        assertEquals(1, page.getTotalCount());
        assertEquals("skill-a", page.getPageItems().get(0).getName());
    }
    
    @ParameterizedTest
    @MethodSource("visibilityQueries")
    void visibilitySqlRestrictsCountsAndPages(BaseVisibilityPredicate base, List<String> scopes,
        String identity, String scopeFilter, String ownerFilter, boolean granted,
        Set<String> expectedNames) throws Exception {
        prepareVisibilityDatabase();
        QueryCondition condition = new QueryCondition();
        condition.setNamespaceId("ns1");
        condition.setType("skill");
        condition.setNameLike("visible-%");
        condition.setBizTagsLike("%selected%");
        condition.setScope(scopeFilter);
        condition.setOwner(ownerFilter);
        QueryAdvisor advisor = new QueryAdvisor();
        advisor.setBasePredicate(base);
        advisor.setPublicScopes(scopes);
        if (granted) {
            advisor.getAuthorizedPredicate().setResources(List.of("visible-granted"));
        }
        new DefaultVisibilityAdvisorConverter().convert(condition, identity, advisor,
            new VisibilityQueryContext());
        
        List<String> actualNames = new ArrayList<>();
        // Visit one page beyond the expected result to also detect leaked trailing rows.
        for (int pageNo = 1; pageNo <= (expectedNames.size() + 1) / 2 + 1; pageNo++) {
            Page<AiResource> page = service.list(condition, pageNo, 2);
            assertEquals(expectedNames.size(), page.getTotalCount());
            for (AiResource row : page.getPageItems()) {
                assertEquals("ns1", row.getNamespaceId());
                assertEquals("skill", row.getType());
                actualNames.add(row.getName());
            }
        }
        assertEquals(expectedNames.size(), actualNames.size());
        assertEquals(expectedNames, actualNames.stream().collect(Collectors.toSet()));
    }
    
    static Stream<Arguments> visibilityQueries() {
        List<String> publicScopes = List.of("PUBLIC", "TEAM_EDIT");
        Set<String> publicNames = Set.of("visible-public", "visible-team");
        return Stream.of(
            Arguments.of(BaseVisibilityPredicate.PUBLIC, List.of("PUBLIC"), null, null, null,
                false, Set.of("visible-public")),
            Arguments.of(BaseVisibilityPredicate.PUBLIC_AND_OWNER, publicScopes, null, null,
                null, false, publicNames),
            Arguments.of(BaseVisibilityPredicate.PUBLIC_AND_OWNER, publicScopes, "", null,
                null, false, publicNames),
            Arguments.of(BaseVisibilityPredicate.PUBLIC_AND_OWNER, publicScopes, " ", null,
                null, false, publicNames),
            Arguments.of(BaseVisibilityPredicate.PUBLIC, List.of(), null, null, null,
                false, Set.of()),
            Arguments.of(BaseVisibilityPredicate.OWNER, publicScopes, " ", null, null,
                false, Set.of()),
            Arguments.of(BaseVisibilityPredicate.PUBLIC_AND_OWNER, List.of(), "me", null, null,
                false, Set.of("visible-owned")),
            Arguments.of(BaseVisibilityPredicate.PUBLIC_AND_OWNER, publicScopes, "me", null,
                null, true,
                Set.of("visible-public", "visible-team", "visible-owned", "visible-granted")),
            Arguments.of(BaseVisibilityPredicate.PUBLIC_AND_OWNER, publicScopes, "me", "PRIVATE",
                "other", true, Set.of("visible-granted")),
            Arguments.of(BaseVisibilityPredicate.PUBLIC_AND_OWNER, publicScopes, "me", "PUBLIC",
                "other", true, Set.of("visible-public")),
            Arguments.of(BaseVisibilityPredicate.PUBLIC, publicScopes, null, "PRIVATE",
                "other", true, Set.of("visible-granted")),
            Arguments.of(BaseVisibilityPredicate.ALL, publicScopes, "me", "PRIVATE", "other",
                false, Set.of("visible-private", "visible-granted")),
            Arguments.of(BaseVisibilityPredicate.PUBLIC_AND_OWNER, publicScopes, "me", "UNKNOWN",
                "other", true, Set.of()),
            Arguments.of(BaseVisibilityPredicate.PUBLIC_AND_OWNER, List.of("TEAM_EDIT"), "me",
                null, null, false, Set.of("visible-team", "visible-owned")));
    }
    
    private void prepareVisibilityDatabase() throws Exception {
        JdbcDataSource source = new JdbcDataSource();
        source.setURL("jdbc:h2:mem:visibility_" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1");
        realJdbcTemplate = new JdbcTemplate(source);
        String schema =
            Files.readString(Path.of("../plugin-default-impl/nacos-default-datasource-plugin/"
                + "nacos-datasource-plugin-derby/src/main/resources/META-INF/derby-schema.sql"));
        int start = schema.indexOf("CREATE TABLE ai_resource (");
        realJdbcTemplate.execute(schema.substring(start, schema.indexOf(';', start)));
        when(dataSourceService.getJdbcTemplate()).thenReturn(realJdbcTemplate);
        service = new AiResourcePersistServiceImpl();
        for (String namespace : List.of("ns1", "ns2")) {
            for (String type : List.of("skill", "agentspec")) {
                insertVisibilityRow(namespace, type, "visible-public", "PUBLIC", "other",
                    "selected");
                insertVisibilityRow(namespace, type, "visible-team", "TEAM_EDIT", "other",
                    "selected");
                insertVisibilityRow(namespace, type, "visible-private", "PRIVATE", "other",
                    "selected");
                insertVisibilityRow(namespace, type, "visible-owned", "PRIVATE", "me", "selected");
                insertVisibilityRow(namespace, type, "visible-granted", "PRIVATE", "other",
                    "selected");
                insertVisibilityRow(namespace, type, "visible-unknown", "UNKNOWN", "other",
                    "selected");
                insertVisibilityRow(namespace, type, "visible-ownerless", "PRIVATE", "",
                    "selected");
                insertVisibilityRow(namespace, type, "excluded-name", "TEAM_EDIT", "me",
                    "selected");
                insertVisibilityRow(namespace, type, "visible-excluded-tag", "TEAM_EDIT", "me",
                    "excluded");
            }
        }
    }
    
    private void insertVisibilityRow(String namespace, String type, String name, String scope,
        String owner, String tag) {
        realJdbcTemplate
            .update("INSERT INTO ai_resource (namespace_id,type,name,scope,owner,biz_tags)"
                + " VALUES (?,?,?,?,?,?)", namespace, type, name, scope, owner, tag);
    }
    
    @Test
    void updatesAndDeletesShouldReturnAffectedState() {
        doReturn(1).when(jdbcTemplate).update(anyString(), any(Object[].class));
        doReturn(1).when(jdbcTemplate).update(anyString(), any(), any(), any());
        doReturn(1).when(jdbcTemplate).update(anyString(), any(), any(), any(), any());
        doReturn(1).when(jdbcTemplate).update(anyString(), any(), any(), any(), any(), any());
        AiResource newValue = newResource();
        
        assertTrue(service.updateMetaCas("public", "skill-a", "skill", 1L, newValue));
        assertTrue(service.updateSourceCas("public", "skill-a", "skill", 2L, "import"));
        assertEquals(1, service.delete("public", "skill-a", "skill"));
        assertTrue(service.updateScope("public", "skill-a", "skill", "PUBLIC"));
        assertTrue(service.incrementDownloadCount("public", "skill-a", "skill", 3L));
    }
    
    @Test
    void updateMetaCasShouldNotReplaceGovernanceFields() {
        doReturn(1).when(jdbcTemplate).update(anyString(), any(Object[].class));
        AiResource newValue = newResource();
        
        assertTrue(service.updateMetaCas("public", "skill-a", "skill", 1L, newValue));
        
        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object[]> argsCaptor = ArgumentCaptor.forClass(Object[].class);
        Mockito.verify(jdbcTemplate).update(sqlCaptor.capture(), argsCaptor.capture());
        assertFalse(sqlCaptor.getValue().contains("owner=?"));
        assertFalse(sqlCaptor.getValue().contains("scope=?"));
        assertFalse(sqlCaptor.getValue().contains("COALESCE"));
        assertEquals(9, argsCaptor.getValue().length);
        assertEquals("public", argsCaptor.getValue()[5]);
        assertEquals("skill-a", argsCaptor.getValue()[6]);
        assertEquals("skill", argsCaptor.getValue()[7]);
        assertEquals(1L, argsCaptor.getValue()[8]);
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
        public MapperResult findAiResourceFetchRows(MapperContext context) {
            WhereBuilder where = new WhereBuilder("SELECT * FROM ai_resource");
            where.eq("namespace_id", context.getWhereParameter(FieldConstant.NAMESPACE_ID));
            appendExtraQueryCondition(where, context);
            return where.orderBy("id").offset(context.getStartRow(), context.getPageSize()).build();
        }
        
        @Override
        public String getTableName() {
            return TableConstant.AI_RESOURCE;
        }
    }
}
