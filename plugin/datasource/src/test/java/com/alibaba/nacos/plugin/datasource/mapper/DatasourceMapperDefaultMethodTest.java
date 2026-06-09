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

package com.alibaba.nacos.plugin.datasource.mapper;

import com.alibaba.nacos.plugin.datasource.constants.DataSourceConstant;
import com.alibaba.nacos.plugin.datasource.constants.FieldConstant;
import com.alibaba.nacos.plugin.datasource.mapper.ext.WhereBuilder;
import com.alibaba.nacos.plugin.datasource.model.MapperContext;
import com.alibaba.nacos.plugin.datasource.model.MapperResult;
import org.junit.jupiter.api.Test;

import java.sql.Timestamp;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DatasourceMapperDefaultMethodTest {
    
    @Test
    void testConfigTagsRelationCountRows() {
        ConfigTagsRelationMapper mapper = new TestConfigTagsRelationMapper();
        MapperContext context = new MapperContext();
        context.putWhereParameter(FieldConstant.TENANT_ID, "tenant");
        context.putWhereParameter(FieldConstant.DATA_ID, "data");
        context.putWhereParameter(FieldConstant.GROUP_ID, "group");
        context.putWhereParameter(FieldConstant.APP_NAME, "app");
        context.putWhereParameter(FieldConstant.CONTENT, "%content%");
        context.putWhereParameter(FieldConstant.TAG_ARR, new String[] {"tagA", "tagB"});
        
        MapperResult result = mapper.findConfigInfo4PageCountRows(context);
        
        assertTrue(result.getSql().contains("LEFT JOIN config_tags_relation"));
        assertTrue(result.getSql().contains("b.tag_name IN (?, ?)"));
        assertEquals(Arrays.asList("tenant", "data", "group", "app", "%content%", "tagA",
            "tagB"), result.getParamList());
        assertEquals("config_tags_relation", mapper.getTableName());
    }
    
    @Test
    void testConfigTagsRelationLikeCountRowsWithOptionalFilters() {
        ConfigTagsRelationMapper mapper = new TestConfigTagsRelationMapper();
        MapperContext context = new MapperContext();
        context.putWhereParameter(FieldConstant.TENANT_ID, "tenant");
        context.putWhereParameter(FieldConstant.DATA_ID, "data");
        context.putWhereParameter(FieldConstant.GROUP_ID, "group");
        context.putWhereParameter(FieldConstant.APP_NAME, "app");
        context.putWhereParameter(FieldConstant.CONTENT, "content");
        context.putWhereParameter(FieldConstant.TAG_ARR, new String[] {"tagA", "tagB"});
        context.putWhereParameter(FieldConstant.TYPE, new String[] {"yaml", "json"});
        
        MapperResult result = mapper.findConfigInfoLike4PageCountRows(context);
        
        assertTrue(result.getSql().contains("a.tenant_id LIKE ?"));
        assertTrue(result.getSql().contains("b.tag_name LIKE ?"));
        assertTrue(result.getSql().contains("a.type IN (?"));
        assertEquals(Arrays.asList("tenant", "data", "group", "app", "content", "tagA", "tagB",
            "yaml", "json"), result.getParamList());
    }
    
    @Test
    void testConfigInfoGrayDefaults() {
        ConfigInfoGrayMapper mapper = new TestConfigInfoGrayMapper();
        MapperContext context = createUpdateContext();
        context.putWhereParameter(FieldConstant.GRAY_NAME, "gray");
        context.putWhereParameter(FieldConstant.GRAY_RULE, "rule");
        context.putWhereParameter(FieldConstant.START_TIME, 100L);
        context.putWhereParameter(FieldConstant.LAST_MAX_ID, 10L);
        context.putWhereParameter(FieldConstant.PAGE_SIZE, 200);
        
        MapperResult updateResult = mapper.updateConfigInfo4GrayCas(context);
        MapperResult changeResult = mapper.findChangeConfig(context);
        
        assertTrue(updateResult.getSql().contains("UPDATE config_info_gray SET content"));
        assertEquals("config_info_gray", mapper.getTableName());
        assertEquals(Arrays.asList("content", "md5-new", "127.0.0.1", "nacos", "app", "rule",
            "data", "group", "tenant", "gray", "md5-old"), updateResult.getParamList());
        assertTrue(changeResult.getSql().contains("FROM config_info_gray WHERE"));
        assertEquals(Arrays.asList(100L, 10L, 200), changeResult.getParamList());
    }
    
    @Test
    void testAiResourceVersionCountRows() {
        AiResourceVersionMapper mapper = new TestAiResourceVersionMapper();
        MapperContext context = new MapperContext();
        context.putWhereParameter(FieldConstant.NAMESPACE_ID, "namespace");
        context.putWhereParameter(FieldConstant.NAME, "resource");
        context.putWhereParameter(FieldConstant.TYPE, "skill");
        context.putWhereParameter(FieldConstant.STATUS, "released");
        context.putWhereParameter(FieldConstant.VERSION, "v1");
        
        MapperResult result = mapper.findAiResourceVersionCountRows(context);
        
        assertEquals("ai_resource_version", mapper.getTableName());
        assertTrue(result.getSql().contains("namespace_id = ?"));
        assertTrue(result.getSql().contains("name = ?"));
        assertTrue(result.getSql().contains("type = ?"));
        assertEquals(Arrays.asList("namespace", "resource", "skill", "released", "v1"),
            result.getParamList());
    }
    
    @Test
    void testAiResourceConditions() {
        AiResourceMapper mapper = new TestAiResourceMapper();
        MapperContext context = new MapperContext();
        context.putWhereParameter(FieldConstant.NAMESPACE_ID, "namespace");
        context.putWhereParameter(FieldConstant.NAME, "resource");
        context.putWhereParameter(FieldConstant.BIZ_TAGS, "tag");
        context.putWhereParameter(FieldConstant.TYPE, Arrays.asList("skill", "prompt"));
        context.putWhereParameter(FieldConstant.ORDER_BY, FieldConstant.ORDER_BY_DOWNLOAD_COUNT);
        
        MapperResult result = mapper.findAiResourceCountRows(context);
        
        assertEquals("ai_resource", mapper.getTableName());
        assertTrue(result.getSql().contains("name LIKE ?"));
        assertTrue(result.getSql().contains("type IN (?"));
        assertEquals(Arrays.asList("namespace", "resource", "tag", "skill", "prompt"),
            result.getParamList());
        assertEquals(" ORDER BY download_count DESC", mapper.resolveOrderByClause(context));
        context.putWhereParameter(FieldConstant.ORDER_BY, "unknown");
        assertEquals(" ORDER BY gmt_modified DESC", mapper.resolveOrderByClause(context));
    }
    
    @Test
    void testAiResourceExtraConditionsBranches() {
        AiResourceMapper mapper = new TestAiResourceMapper();
        MapperContext context = new MapperContext();
        context.putWhereParameter(FieldConstant.NAMESPACE_ID, "namespace");
        context.putWhereParameter(AiResourceMapper.QUERY_CONDITION_ALWAYS_EMPTY, true);
        
        MapperResult alwaysEmpty = mapper.findAiResourceCountRows(context);
        assertTrue(alwaysEmpty.getSql().contains("1 = ?"));
        assertEquals(Arrays.asList("namespace", 0), alwaysEmpty.getParamList());
        
        MapperContext singleOrContext = new MapperContext();
        singleOrContext.putWhereParameter(FieldConstant.NAMESPACE_ID, "namespace");
        Map<Object, Object> singleOr = new LinkedHashMap<>();
        singleOr.put("owner", "nacos");
        singleOrContext.putWhereParameter(AiResourceMapper.QUERY_CONDITION_OR_GROUP, singleOr);
        MapperResult singleOrResult = mapper.findAiResourceCountRows(singleOrContext);
        assertTrue(singleOrResult.getSql().contains("owner = ?"));
        assertEquals(Arrays.asList("namespace", "nacos"), singleOrResult.getParamList());
        
        MapperContext multiOrContext = new MapperContext();
        multiOrContext.putWhereParameter(FieldConstant.NAMESPACE_ID, "namespace");
        Map<Object, Object> multiOr = new LinkedHashMap<>();
        multiOr.put("type", Arrays.asList("skill", "prompt"));
        multiOr.put("owner", "nacos");
        multiOrContext.putWhereParameter(AiResourceMapper.QUERY_CONDITION_OR_GROUP, multiOr);
        MapperResult multiOrResult = mapper.findAiResourceCountRows(multiOrContext);
        assertTrue(multiOrResult.getSql().contains("type IN (?"));
        assertTrue(multiOrResult.getSql().contains("owner = ?"));
        assertEquals(Arrays.asList("namespace", "skill", "prompt", "nacos"),
            multiOrResult.getParamList());
        
        MapperResult emptyOrResult = buildEmptyOrConditionResult(mapper);
        assertTrue(emptyOrResult.getSql().contains("1 = ?"));
        assertEquals(Collections.singletonList(0), emptyOrResult.getParamList());
        
        WhereBuilder emptySingleCondition = new WhereBuilder("SELECT * FROM ai_resource");
        mapper.appendSingleAndCondition(emptySingleCondition, "type", Collections.emptyList(),
            false);
        assertTrue(emptySingleCondition.build().getParamList().isEmpty());
        
        Map<Object, Object> rawMap = new LinkedHashMap<>();
        rawMap.put(null, "empty");
        assertTrue(mapper.castToMap(rawMap).containsKey(null));
        assertNull(mapper.castToMap("not-map"));
    }
    
    @Test
    void testWhereBuilderFluentBranches() {
        MapperResult result = new WhereBuilder("SELECT * FROM config_info")
            .startParentheses()
            .eq("data_id", "data")
            .or()
            .likeWithEscape("content", "%value\\_%")
            .endParentheses()
            .and()
            .exists("SELECT 1 FROM config_tags_relation b WHERE ",
                sub -> sub.eqColumn("b.id", "a.id").and().in("b.tag_name",
                    new String[] {"tagA", "tagB"}))
            .groupBy("data_id")
            .orderBy("id DESC")
            .limit(0, 10)
            .offset(10, 20)
            .build();
        
        assertTrue(result.getSql().contains("content LIKE ? ESCAPE '\\'"));
        assertTrue(result.getSql().contains("EXISTS ( SELECT 1 FROM config_tags_relation"));
        assertTrue(result.getSql().contains("b.id = a.id"));
        assertTrue(result.getSql().contains("GROUP BY data_id"));
        assertTrue(result.getSql().contains("LIMIT 0,10"));
        assertTrue(result.getSql().contains("OFFSET 10 ROWS FETCH NEXT 20 ROWS ONLY"));
        assertEquals(Arrays.asList("data", "%value\\_%", "tagA", "tagB"),
            result.getParamList());
    }
    
    @Test
    void testConfigInfoSimpleDefaultSql() {
        ConfigInfoMapper mapper = new TestConfigInfoMapper();
        MapperContext context = new MapperContext();
        context.putWhereParameter(FieldConstant.TENANT_ID, "tenant");
        context.putWhereParameter(FieldConstant.APP_NAME, "app");
        context.putWhereParameter(FieldConstant.START_TIME, 100L);
        context.putWhereParameter(FieldConstant.LAST_MAX_ID, 10L);
        context.putWhereParameter(FieldConstant.PAGE_SIZE, 50);
        
        assertEquals("SELECT MAX(id) FROM config_info", mapper.findConfigMaxId(context).getSql());
        assertEquals("SELECT DISTINCT data_id, group_id FROM config_info",
            mapper.findAllDataIdAndGroup(context).getSql());
        assertEquals(Arrays.asList("tenant", "app"),
            mapper.findConfigInfoByAppCountRows(context).getParamList());
        assertEquals(Collections.singletonList("tenant"),
            mapper.configInfoLikeTenantCount(context).getParamList());
        
        MapperResult changeConfig = mapper.findChangeConfig(context);
        assertTrue(changeConfig.getSql().contains("gmt_modified >= ?"));
        assertEquals(Arrays.asList(100L, 10L, 50), changeConfig.getParamList());
        assertEquals("config_info", mapper.getTableName());
    }
    
    @Test
    void testConfigInfoChangeCountAndExportBranches() {
        ConfigInfoMapper mapper = new TestConfigInfoMapper();
        MapperContext context = createQueryContext();
        Timestamp startTime = Timestamp.valueOf("2026-05-22 00:00:00");
        Timestamp endTime = Timestamp.valueOf("2026-05-22 01:00:00");
        context.putWhereParameter(FieldConstant.TENANT, "tenant");
        context.putWhereParameter(FieldConstant.START_TIME, startTime);
        context.putWhereParameter(FieldConstant.END_TIME, endTime);
        
        MapperResult changeCount = mapper.findChangeConfigCountRows(context);
        assertTrue(changeCount.getSql().contains("data_id LIKE ?"));
        assertTrue(changeCount.getSql().contains("gmt_modified <=?"));
        assertEquals(Arrays.asList("data", "group", "tenant", "app", startTime, endTime),
            changeCount.getParamList());
        
        MapperContext idsContext = new MapperContext();
        idsContext.putWhereParameter(FieldConstant.IDS, Arrays.asList(1L, 2L));
        MapperResult idsExport = mapper.findAllConfigInfo4Export(idsContext);
        assertTrue(idsExport.getSql().contains("id IN (?, ?)"));
        assertEquals(Arrays.asList(1L, 2L), idsExport.getParamList());
        
        MapperResult filteredExport = mapper.findAllConfigInfo4Export(context);
        assertTrue(filteredExport.getSql().contains("tenant_id = ?"));
        assertTrue(filteredExport.getSql().contains("app_name= ?"));
        assertEquals(Arrays.asList("tenantId", "data", "group", "app"),
            filteredExport.getParamList());
    }
    
    @Test
    void testConfigInfoCountAndIdListDefaults() {
        ConfigInfoMapper mapper = new TestConfigInfoMapper();
        MapperContext context = createQueryContext();
        context.putWhereParameter(FieldConstant.CONTENT, "content");
        context.putWhereParameter(FieldConstant.TYPE, new String[] {"yaml", "json"});
        context.putWhereParameter(FieldConstant.IDS, Arrays.asList(1L, 2L, 3L));
        
        MapperResult baseLike = mapper.findConfigInfoBaseLikeCountRows(context);
        assertTrue(baseLike.getSql().contains("content LIKE ?"));
        assertEquals(Arrays.asList("data", "group", "content"), baseLike.getParamList());
        
        MapperResult pageCount = mapper.findConfigInfo4PageCountRows(context);
        assertTrue(pageCount.getSql().contains("tenant_id=?"));
        assertEquals(Arrays.asList("tenantId", "data", "group", "app", "content"),
            pageCount.getParamList());
        
        MapperResult likeCount = mapper.findConfigInfoLike4PageCountRows(context);
        assertTrue(likeCount.getSql().contains("type IN (?"));
        assertEquals(Arrays.asList("tenantId", "data", "group", "app", "content", "yaml",
            "json"), likeCount.getParamList());
        
        assertEquals(Arrays.asList(1L, 2L, 3L),
            mapper.findConfigInfosByIds(context).getParamList());
        assertTrue(mapper.findConfigInfosByIds(context).getSql().contains("id IN (?, ?, ?)"));
        assertEquals(Arrays.asList(1L, 2L, 3L),
            mapper.removeConfigInfoByIdsAtomic(context).getParamList());
    }
    
    @Test
    void testConfigInfoAtomicCasWithOptionalDescription() {
        ConfigInfoMapper mapper = new TestConfigInfoMapper();
        MapperContext context = createUpdateContext();
        context.putUpdateParameter(FieldConstant.C_DESC, "description");
        context.putUpdateParameter(FieldConstant.C_USE, "use");
        context.putUpdateParameter(FieldConstant.EFFECT, "effect");
        context.putUpdateParameter(FieldConstant.TYPE, "type");
        context.putUpdateParameter(FieldConstant.C_SCHEMA, "schema");
        context.putUpdateParameter(FieldConstant.ENCRYPTED_DATA_KEY, "key");
        
        MapperResult result = mapper.updateConfigInfoAtomicCas(context);
        
        assertTrue(result.getSql().contains("gmt_modified=NOW()"));
        assertTrue(result.getSql().contains("c_desc=?"));
        assertEquals(Arrays.asList("content", "md5-new", "127.0.0.1", "nacos", "app",
            "description", "use", "effect", "type", "schema", "key", "data", "group",
            "tenant", "md5-old"), result.getParamList());
        
        context.putUpdateParameter(FieldConstant.C_DESC, null);
        assertTrue(!mapper.updateConfigInfoAtomicCas(context).getSql().contains("c_desc=?"));
    }
    
    @Test
    void testHistoryConfigInfoDefaults() {
        HistoryConfigInfoMapper mapper = new TestHistoryConfigInfoMapper();
        MapperContext context = createHistoryContext();
        
        assertEquals(Collections.singletonList(100L),
            mapper.findConfigHistoryCountByTime(context).getParamList());
        assertEquals(Arrays.asList("formal", 100L, 10L, 50),
            mapper.findDeletedConfig(context).getParamList());
        assertEquals(Arrays.asList("data", "group", "tenant"),
            mapper.findConfigHistoryFetchRows(context).getParamList());
        assertEquals(Collections.singletonList(1L),
            mapper.detailPreviousConfigHistory(context).getParamList());
        assertEquals("his_config_info", mapper.getTableName());
        
        MapperResult blankGray = mapper.getNextHistoryInfo(context);
        assertTrue(!blankGray.getSql().contains("gray_name = ?"));
        assertEquals(Arrays.asList("data", "group", "tenant", "formal", 5L),
            blankGray.getParamList());
        
        context.putWhereParameter(FieldConstant.GRAY_NAME, "gray");
        context.putContextParameter(FieldConstant.GRAY_NAME, "gray");
        MapperResult withGray = mapper.getNextHistoryInfo(context);
        assertTrue(withGray.getSql().contains("gray_name = ?"));
        assertEquals(Arrays.asList("data", "group", "tenant", "formal", "gray", 5L),
            withGray.getParamList());
    }
    
    private MapperResult buildEmptyOrConditionResult(AiResourceMapper mapper) {
        WhereBuilder where = new WhereBuilder("SELECT * FROM ai_resource");
        Map<String, Object> emptyOr = new LinkedHashMap<>();
        emptyOr.put("", "blank");
        emptyOr.put("owner", Collections.emptyList());
        emptyOr.put("type", null);
        mapper.appendOrConditions(where, emptyOr);
        return where.build();
    }
    
    private MapperContext createUpdateContext() {
        MapperContext context = new MapperContext();
        context.putUpdateParameter(FieldConstant.CONTENT, "content");
        context.putUpdateParameter(FieldConstant.MD5, "md5-new");
        context.putUpdateParameter(FieldConstant.SRC_IP, "127.0.0.1");
        context.putUpdateParameter(FieldConstant.SRC_USER, "nacos");
        context.putUpdateParameter(FieldConstant.GMT_MODIFIED, "now");
        context.putUpdateParameter(FieldConstant.APP_NAME, "app");
        context.putWhereParameter(FieldConstant.DATA_ID, "data");
        context.putWhereParameter(FieldConstant.GROUP_ID, "group");
        context.putWhereParameter(FieldConstant.TENANT_ID, "tenant");
        context.putWhereParameter(FieldConstant.MD5, "md5-old");
        return context;
    }
    
    private MapperContext createQueryContext() {
        MapperContext context = new MapperContext();
        context.putWhereParameter(FieldConstant.TENANT_ID, "tenantId");
        context.putWhereParameter(FieldConstant.DATA_ID, "data");
        context.putWhereParameter(FieldConstant.GROUP_ID, "group");
        context.putWhereParameter(FieldConstant.APP_NAME, "app");
        return context;
    }
    
    private MapperContext createHistoryContext() {
        MapperContext context = new MapperContext();
        context.putWhereParameter(FieldConstant.START_TIME, 100L);
        context.putWhereParameter(FieldConstant.LAST_MAX_ID, 10L);
        context.putWhereParameter(FieldConstant.PAGE_SIZE, 50);
        context.putWhereParameter(FieldConstant.PUBLISH_TYPE, "formal");
        context.putWhereParameter(FieldConstant.DATA_ID, "data");
        context.putWhereParameter(FieldConstant.GROUP_ID, "group");
        context.putWhereParameter(FieldConstant.TENANT_ID, "tenant");
        context.putWhereParameter(FieldConstant.ID, 1L);
        context.putWhereParameter(FieldConstant.NID, 5L);
        return context;
    }
    
    private static class TestConfigTagsRelationMapper extends TestAbstractMapper
        implements ConfigTagsRelationMapper {
        
        @Override
        public String getTableName() {
            return ConfigTagsRelationMapper.super.getTableName();
        }
        
        @Override
        public MapperResult findConfigInfo4PageFetchRows(MapperContext context) {
            return null;
        }
        
        @Override
        public MapperResult findConfigInfoLike4PageFetchRows(MapperContext context) {
            return null;
        }
    }
    
    private static class TestConfigInfoGrayMapper extends TestAbstractMapper
        implements ConfigInfoGrayMapper {
        
        @Override
        public String getTableName() {
            return ConfigInfoGrayMapper.super.getTableName();
        }
        
        @Override
        public MapperResult findAllConfigInfoGrayForDumpAllFetchRows(MapperContext context) {
            return null;
        }
    }
    
    private static class TestAiResourceVersionMapper extends TestAbstractMapper
        implements AiResourceVersionMapper {
        
        @Override
        public String getTableName() {
            return AiResourceVersionMapper.super.getTableName();
        }
        
        @Override
        public MapperResult findAiResourceVersionFetchRows(MapperContext context) {
            return null;
        }
    }
    
    private static class TestAiResourceMapper extends TestAbstractMapper
        implements AiResourceMapper {
        
        @Override
        public String getTableName() {
            return AiResourceMapper.super.getTableName();
        }
        
        @Override
        public MapperResult findAiResourceFetchRows(MapperContext context) {
            return null;
        }
    }
    
    private static class TestConfigInfoMapper extends TestAbstractMapper
        implements ConfigInfoMapper {
        
        @Override
        public String getTableName() {
            return ConfigInfoMapper.super.getTableName();
        }
        
        @Override
        public MapperResult findConfigInfoByAppFetchRows(MapperContext context) {
            return null;
        }
        
        @Override
        public MapperResult getTenantIdList(MapperContext context) {
            return null;
        }
        
        @Override
        public MapperResult getGroupIdList(MapperContext context) {
            return null;
        }
        
        @Override
        public MapperResult findAllConfigKey(MapperContext context) {
            return null;
        }
        
        @Override
        public MapperResult findAllConfigInfoBaseFetchRows(MapperContext context) {
            return null;
        }
        
        @Override
        public MapperResult findAllConfigInfoFragment(MapperContext context) {
            return null;
        }
        
        @Override
        public MapperResult findChangeConfigFetchRows(MapperContext context) {
            return null;
        }
        
        @Override
        public MapperResult listGroupKeyMd5ByPageFetchRows(MapperContext context) {
            return null;
        }
        
        @Override
        public MapperResult findConfigInfoBaseLikeFetchRows(MapperContext context) {
            return null;
        }
        
        @Override
        public MapperResult findConfigInfo4PageFetchRows(MapperContext context) {
            return null;
        }
        
        @Override
        public MapperResult findConfigInfoBaseByGroupFetchRows(MapperContext context) {
            return null;
        }
        
        @Override
        public MapperResult findConfigInfoLike4PageFetchRows(MapperContext context) {
            return null;
        }
        
        @Override
        public MapperResult findAllConfigInfoFetchRows(MapperContext context) {
            return null;
        }
    }
    
    private static class TestHistoryConfigInfoMapper extends TestAbstractMapper
        implements HistoryConfigInfoMapper {
        
        @Override
        public String getTableName() {
            return HistoryConfigInfoMapper.super.getTableName();
        }
        
        @Override
        public MapperResult removeConfigHistory(MapperContext context) {
            return null;
        }
        
        @Override
        public MapperResult pageFindConfigHistoryFetchRows(MapperContext context) {
            return null;
        }
    }
    
    private abstract static class TestAbstractMapper extends AbstractMapper {
        
        @Override
        public String getDataSource() {
            return DataSourceConstant.MYSQL;
        }
        
        @Override
        public String getFunction(String functionName) {
            return functionName;
        }
    }
}
