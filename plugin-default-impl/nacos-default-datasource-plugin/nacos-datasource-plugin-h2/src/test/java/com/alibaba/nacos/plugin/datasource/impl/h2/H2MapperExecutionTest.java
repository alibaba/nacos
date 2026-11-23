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

package com.alibaba.nacos.plugin.datasource.impl.h2;

import com.alibaba.nacos.plugin.datasource.constants.FieldConstant;
import com.alibaba.nacos.plugin.datasource.impl.dialect.H2DatabaseDialect;
import com.alibaba.nacos.plugin.datasource.model.MapperContext;
import com.alibaba.nacos.plugin.datasource.model.MapperResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Runs the SQL produced by the H2 mappers against a real H2 database created from the bundled {@code h2-schema.sql}.
 *
 * <p>The other H2 tests only compare generated SQL strings. This class proves that H2 accepts the statements, binds
 * their parameters in order, and returns the rows the repositories expect.
 */
class H2MapperExecutionTest {
    
    private Connection connection;
    
    @BeforeEach
    void setUp() throws Exception {
        connection =
            DriverManager.getConnection("jdbc:h2:mem:" + UUID.randomUUID(), "nacos", "nacos");
        loadSchema(connection);
    }
    
    @AfterEach
    void tearDown() throws Exception {
        connection.close();
    }
    
    @Test
    void testSchemaCreatesEveryNacosTable() throws Exception {
        List<String> tables = new ArrayList<>();
        try (Statement statement = connection.createStatement();
            ResultSet resultSet = statement.executeQuery(
                "SELECT TABLE_NAME FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA = 'PUBLIC'")) {
            while (resultSet.next()) {
                tables.add(resultSet.getString(1));
            }
        }
        assertTrue(tables.containsAll(Arrays.asList("CONFIG_INFO", "CONFIG_INFO_GRAY",
            "CONFIG_TAGS_RELATION",
            "HIS_CONFIG_INFO", "TENANT_INFO", "TENANT_CAPACITY", "GROUP_CAPACITY", "USERS", "ROLES",
            "PERMISSIONS",
            "AI_RESOURCE", "AI_RESOURCE_VERSION", "AI_RESOURCE_SEARCH_DOCUMENT",
            "AI_RESOURCE_SEARCH_CHUNK",
            "AI_RESOURCE_TASK", "PIPELINE_EXECUTION", "APP_LIST", "APP_CONFIGDATA_RELATION_SUBS",
            "APP_CONFIGDATA_RELATION_PUBS")), tables.toString());
    }
    
    @Test
    void testPagedConfigQueryBindsOffsetAndFetchParameters() throws Exception {
        insertConfig("d1", "c1");
        insertConfig("d2", "c2");
        insertConfig("d3", "c3");
        MapperContext context = new MapperContext(1, 2);
        context.putWhereParameter(FieldConstant.TENANT_ID, "public");
        
        List<String> dataIds = queryStrings(
            new ConfigInfoMapperByH2().findAllConfigInfoFetchRows(context), "data_id");
        
        assertEquals(2, dataIds.size(), dataIds.toString());
        assertTrue(dataIds.containsAll(Arrays.asList("d2", "d3")), dataIds.toString());
    }
    
    @Test
    void testLikeEscapeMatchesLiteralUnderscoreAndKeepsWildcards() throws Exception {
        insertConfig("a_b", "c1");
        insertConfig("axb", "c2");
        MapperContext literal = new MapperContext(0, 10);
        literal.putWhereParameter(FieldConstant.TENANT_ID, "public");
        // Callers escape the backslash first, then the underscore, before the value reaches the mapper.
        literal.putWhereParameter(FieldConstant.DATA_ID, "a\\_b");
        assertEquals(1L,
            count(new ConfigInfoMapperByH2().findConfigInfoLike4PageCountRows(literal)));
        
        MapperContext wildcard = new MapperContext(0, 10);
        wildcard.putWhereParameter(FieldConstant.TENANT_ID, "public");
        wildcard.putWhereParameter(FieldConstant.DATA_ID, "a%");
        assertEquals(2L,
            count(new ConfigInfoMapperByH2().findConfigInfoLike4PageCountRows(wildcard)));
    }
    
    @Test
    void testGeneratedConfigIdIsReturnedThroughDialectKeyColumns() throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(
            "INSERT INTO config_info (data_id, group_id, tenant_id, content, md5) VALUES (?, ?, ?, ?, ?)",
            new H2DatabaseDialect().getReturnPrimaryKeys())) {
            statement.setString(1, "generated");
            statement.setString(2, "DEFAULT_GROUP");
            statement.setString(3, "public");
            statement.setString(4, "content");
            statement.setString(5, "md5");
            assertEquals(1, statement.executeUpdate());
            try (ResultSet keys = statement.getGeneratedKeys()) {
                assertTrue(keys.next());
                assertTrue(keys.getLong(1) > 0L);
            }
        }
    }
    
    @Test
    void testHistoryPurgeDeletesOnlyRowsBeforeStartTime() throws Exception {
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate(
                "INSERT INTO his_config_info (id, data_id, group_id, tenant_id, content, "
                    + "gmt_create, gmt_modified) VALUES (1, 'old', 'DEFAULT_GROUP', 'public', 'c', "
                    + "TIMESTAMP '2010-05-05 00:00:00', TIMESTAMP '2010-05-05 00:00:00')");
            statement.executeUpdate(
                "INSERT INTO his_config_info (id, data_id, group_id, tenant_id, content, "
                    + "gmt_create, gmt_modified) VALUES (2, 'new', 'DEFAULT_GROUP', 'public', 'c', "
                    + "TIMESTAMP '2030-01-01 00:00:00', TIMESTAMP '2030-01-01 00:00:00')");
        }
        MapperContext context = new MapperContext();
        context.putWhereParameter(FieldConstant.START_TIME,
            Timestamp.valueOf("2020-01-01 00:00:00"));
        context.putWhereParameter(FieldConstant.LIMIT_SIZE, 10);
        
        assertEquals(1, update(new HistoryConfigInfoMapperByH2().removeConfigHistory(context)));
        assertEquals(1L, scalar("SELECT count(*) FROM his_config_info"));
    }
    
    @Test
    void testTagSearchReturnsEachConfigurationOnceAndCountMatchesRows() throws Exception {
        insertTaggedConfig("a", "json", "blue", "green");
        insertTaggedConfig("b", "yaml", "blue");
        insertTaggedConfig("c", "json", "red");
        ConfigInfoTagsRelationMapperByH2 mapper = new ConfigInfoTagsRelationMapperByH2();
        
        // Exact tags: "a" matches both requested tags and is still listed once.
        MapperContext exact = tagContext(0, 10, "blue", "green");
        assertEquals(Arrays.asList("a", "b"),
            queryStrings(mapper.findConfigInfo4PageFetchRows(exact), "data_id"));
        assertEquals(2L, count(mapper.findConfigInfo4PageCountRows(exact)));
        
        // Fuzzy tags with a type filter: the page and the count apply the same filter.
        MapperContext json = tagContext(0, 10, "blue", "green");
        json.putWhereParameter(FieldConstant.TYPE, new String[] {"json"});
        assertEquals(Collections.singletonList("a"),
            queryStrings(mapper.findConfigInfoLike4PageFetchRows(json), "data_id"));
        assertEquals(1L, count(mapper.findConfigInfoLike4PageCountRows(json)));
        
        // Fuzzy tags without a type filter: "a" is still listed once.
        MapperContext anyType = tagContext(0, 10, "blue", "green");
        assertEquals(Arrays.asList("a", "b"),
            queryStrings(mapper.findConfigInfoLike4PageFetchRows(anyType), "data_id"));
        assertEquals(2L, count(mapper.findConfigInfoLike4PageCountRows(anyType)));
        
        // Pages hold distinct configurations: the second page of size one is "b", not a repeat of "a".
        MapperContext secondPage = tagContext(1, 1, "blue", "green");
        assertEquals(Collections.singletonList("b"),
            queryStrings(mapper.findConfigInfoLike4PageFetchRows(secondPage), "data_id"));
    }
    
    @Test
    void testSchemaCanRunAgainstAnExistingDatabase() throws Exception {
        insertConfig("kept", "content");
        loadSchema(connection);
        assertEquals(1L, scalar("SELECT count(*) FROM config_info"));
    }
    
    @Test
    void testGroupCapacityInsertSelectCountsConfigRows() throws Exception {
        insertConfig("g1", "c1");
        insertConfig("g2", "c2");
        MapperContext context = new MapperContext();
        Timestamp now = new Timestamp(System.currentTimeMillis());
        context.putUpdateParameter(FieldConstant.GROUP_ID, "grp-h2");
        context.putUpdateParameter(FieldConstant.QUOTA, 200);
        context.putUpdateParameter(FieldConstant.MAX_SIZE, 10240);
        context.putUpdateParameter(FieldConstant.MAX_AGGR_COUNT, 10000);
        context.putUpdateParameter(FieldConstant.MAX_AGGR_SIZE, 1024);
        context.putUpdateParameter(FieldConstant.GMT_CREATE, now);
        context.putUpdateParameter(FieldConstant.GMT_MODIFIED, now);
        
        assertEquals(1, update(new GroupCapacityMapperByH2().insertIntoSelect(context)));
        assertEquals(2L, scalar("SELECT usage FROM group_capacity WHERE group_id = 'grp-h2'"));
    }
    
    private static void loadSchema(Connection connection) throws Exception {
        String schema;
        try (InputStream inputStream =
            H2MapperExecutionTest.class.getClassLoader()
                .getResourceAsStream("META-INF/h2-schema.sql")) {
            assertNotNull(inputStream);
            schema = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        }
        try (Statement statement = connection.createStatement()) {
            // Same rule as LocalDataSourceServiceImpl#loadSql: split on ';', then strip line comments.
            for (String part : schema.split(";")) {
                String sql = part.replaceAll("--.*", "").trim();
                if (!sql.isEmpty()) {
                    statement.execute(sql);
                }
            }
        }
    }
    
    private void insertConfig(String dataId, String content) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(
            "INSERT INTO config_info (data_id, group_id, tenant_id, content, md5) VALUES (?, ?, ?, ?, ?)")) {
            statement.setString(1, dataId);
            statement.setString(2, "DEFAULT_GROUP");
            statement.setString(3, "public");
            statement.setString(4, content);
            statement.setString(5, "md5-" + dataId);
            statement.executeUpdate();
        }
    }
    
    private void insertTaggedConfig(String dataId, String type, String... tags) throws Exception {
        long id;
        try (PreparedStatement statement = connection.prepareStatement(
            "INSERT INTO config_info (data_id, group_id, tenant_id, content, md5, type) "
                + "VALUES (?, ?, ?, ?, ?, ?)",
            Statement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, dataId);
            statement.setString(2, "DEFAULT_GROUP");
            statement.setString(3, "public");
            statement.setString(4, "content-" + dataId);
            statement.setString(5, "md5-" + dataId);
            statement.setString(6, type);
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                assertTrue(keys.next());
                id = keys.getLong(1);
            }
        }
        for (String tag : tags) {
            try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO config_tags_relation (id, tag_name, data_id, group_id, tenant_id) "
                    + "VALUES (?, ?, ?, ?, ?)")) {
                statement.setLong(1, id);
                statement.setString(2, tag);
                statement.setString(3, dataId);
                statement.setString(4, "DEFAULT_GROUP");
                statement.setString(5, "public");
                statement.executeUpdate();
            }
        }
    }
    
    private static MapperContext tagContext(int startRow, int pageSize, String... tags) {
        MapperContext context = new MapperContext(startRow, pageSize);
        context.putWhereParameter(FieldConstant.TENANT_ID, "public");
        context.putWhereParameter(FieldConstant.TAG_ARR, tags);
        return context;
    }
    
    private PreparedStatement prepare(MapperResult mapperResult) throws Exception {
        PreparedStatement statement = connection.prepareStatement(mapperResult.getSql());
        List<Object> params = mapperResult.getParamList();
        for (int i = 0; i < params.size(); i++) {
            statement.setObject(i + 1, params.get(i));
        }
        return statement;
    }
    
    private List<String> queryStrings(MapperResult mapperResult, String column) throws Exception {
        List<String> values = new ArrayList<>();
        try (PreparedStatement statement = prepare(mapperResult);
            ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                values.add(resultSet.getString(column));
            }
        }
        return values;
    }
    
    private long count(MapperResult mapperResult) throws Exception {
        try (PreparedStatement statement = prepare(mapperResult);
            ResultSet resultSet = statement.executeQuery()) {
            assertTrue(resultSet.next());
            return resultSet.getLong(1);
        }
    }
    
    private int update(MapperResult mapperResult) throws Exception {
        try (PreparedStatement statement = prepare(mapperResult)) {
            return statement.executeUpdate();
        }
    }
    
    private long scalar(String sql) throws Exception {
        try (Statement statement = connection.createStatement();
            ResultSet resultSet = statement.executeQuery(sql)) {
            assertTrue(resultSet.next());
            return resultSet.getLong(1);
        }
    }
}
