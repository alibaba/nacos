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
    void testConfigInfoBetaAndTagCasUpdates() {
        ConfigInfoBetaMapper betaMapper = new TestConfigInfoBetaMapper();
        ConfigInfoTagMapper tagMapper = new TestConfigInfoTagMapper();
        MapperContext context = createUpdateContext();
        context.putUpdateParameter(FieldConstant.BETA_IPS, "1.1.1.1");
        context.putWhereParameter(FieldConstant.TAG_ID, "tag");
        
        MapperResult beta = betaMapper.updateConfigInfo4BetaCas(context);
        MapperResult tag = tagMapper.updateConfigInfo4TagCas(context);
        
        assertTrue(beta.getSql().contains("UPDATE config_info_beta SET content"));
        assertTrue(beta.getSql().contains("gmt_modified = NOW()"));
        assertEquals("config_info_beta", betaMapper.getTableName());
        assertEquals(Arrays.asList("content", "md5-new", "1.1.1.1", "127.0.0.1", "nacos",
            "app", "data", "group", "tenant", "md5-old"), beta.getParamList());
        assertTrue(tag.getSql().contains("UPDATE config_info_tag SET content"));
        assertEquals("config_info_tag", tagMapper.getTableName());
        assertEquals(Arrays.asList("content", "md5-new", "127.0.0.1", "nacos", "now", "app",
            "data", "group", "tenant", "tag", "md5-old"), tag.getParamList());
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
        assertNull(mapper.castToMap("not-map"));
    }
    
    private MapperResult buildEmptyOrConditionResult(AiResourceMapper mapper) {
        WhereBuilder where = new WhereBuilder("SELECT * FROM ai_resource");
        Map<String, Object> emptyOr = new LinkedHashMap<>();
        emptyOr.put("", "blank");
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
    
    private static class TestConfigInfoBetaMapper extends TestAbstractMapper
        implements ConfigInfoBetaMapper {
        
        @Override
        public String getTableName() {
            return ConfigInfoBetaMapper.super.getTableName();
        }
        
        @Override
        public MapperResult findAllConfigInfoBetaForDumpAllFetchRows(MapperContext context) {
            return null;
        }
    }
    
    private static class TestConfigInfoTagMapper extends TestAbstractMapper
        implements ConfigInfoTagMapper {
        
        @Override
        public String getTableName() {
            return ConfigInfoTagMapper.super.getTableName();
        }
        
        @Override
        public MapperResult findAllConfigInfoTagForDumpAllFetchRows(MapperContext context) {
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
