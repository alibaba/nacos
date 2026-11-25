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

import com.alibaba.nacos.plugin.datasource.constants.DataSourceConstant;
import com.alibaba.nacos.plugin.datasource.constants.FieldConstant;
import com.alibaba.nacos.plugin.datasource.constants.TableConstant;
import com.alibaba.nacos.plugin.datasource.model.MapperContext;
import com.alibaba.nacos.plugin.datasource.model.MapperResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class ConfigInfoTagsRelationMapperByH2Test {
    
    private static final String EXACT_PAGE_SELECT =
        "SELECT a.id,a.data_id,a.group_id,a.tenant_id,a.app_name,"
            + "a.content,a.md5,a.type,a.encrypted_data_key,a.c_desc FROM config_info a";
    
    private static final String LIKE_PAGE_SELECT =
        "SELECT a.ID,a.data_id,a.group_id,a.tenant_id,a.app_name,"
            + "a.content,a.md5,a.encrypted_data_key,a.type,a.c_desc FROM config_info a";
    
    private static final String EXACT_TAGS = "EXISTS ( SELECT 1 FROM config_tags_relation b "
        + "WHERE b.id = a.id AND b.tag_name IN (?, ?, ?, ?, ?) )";
    
    private static final String LIKE_TAGS = "EXISTS ( SELECT 1 FROM config_tags_relation b "
        + "WHERE b.id = a.id AND ( b.tag_name LIKE ? ESCAPE '\\' OR b.tag_name LIKE ? ESCAPE '\\' "
        + "OR b.tag_name LIKE ? ESCAPE '\\' OR b.tag_name LIKE ? ESCAPE '\\' "
        + "OR b.tag_name LIKE ? ESCAPE '\\' ) )";
    
    private static final String PAGE_TAIL = " ORDER BY a.id OFFSET 0 ROWS FETCH NEXT 5 ROWS ONLY";
    
    int startRow = 0;
    
    int pageSize = 5;
    
    String tenantId = "tenantId";
    
    String[] tagArr = new String[] {"tag1", "tag3", "tag2", "tag4", "tag5"};
    
    MapperContext context;
    
    private ConfigInfoTagsRelationMapperByH2 configInfoTagsRelationMapperByH2;
    
    @BeforeEach
    void setUp() throws Exception {
        this.configInfoTagsRelationMapperByH2 = new ConfigInfoTagsRelationMapperByH2();
        context = new MapperContext(startRow, pageSize);
        context.putWhereParameter(FieldConstant.TENANT_ID, tenantId);
        context.putWhereParameter(FieldConstant.TAG_ARR, tagArr);
    }
    
    @Test
    void testFindConfigInfo4PageCountRowsUsesTheSameConditionsAsThePage() {
        MapperResult mapperResult =
            configInfoTagsRelationMapperByH2.findConfigInfo4PageCountRows(context);
        assertEquals("SELECT count(*) FROM config_info a WHERE a.tenant_id = ? AND " + EXACT_TAGS,
            normalize(mapperResult.getSql()));
        assertArrayEquals(params(tenantId, tagArr), mapperResult.getParamList().toArray());
    }
    
    @Test
    void testFindConfigInfo4PageFetchRows() {
        MapperResult mapperResult =
            configInfoTagsRelationMapperByH2.findConfigInfo4PageFetchRows(context);
        assertEquals(EXACT_PAGE_SELECT + " WHERE a.tenant_id = ? AND " + EXACT_TAGS + PAGE_TAIL,
            normalize(mapperResult.getSql()));
        assertArrayEquals(params(tenantId, tagArr), mapperResult.getParamList().toArray());
    }
    
    @Test
    void testFindConfigInfo4PageFetchRowsWithContent() {
        context.putWhereParameter(FieldConstant.CONTENT, "test_content");
        MapperResult mapperResult =
            configInfoTagsRelationMapperByH2.findConfigInfo4PageFetchRows(context);
        assertEquals(
            EXACT_PAGE_SELECT + " WHERE a.tenant_id = ? AND a.content LIKE ? ESCAPE '\\' AND "
                + EXACT_TAGS + PAGE_TAIL,
            normalize(mapperResult.getSql()));
        assertArrayEquals(params(tenantId, "test_content", tagArr),
            mapperResult.getParamList().toArray());
    }
    
    @Test
    void testFindConfigInfoLike4PageCountRows() {
        MapperResult mapperResult =
            configInfoTagsRelationMapperByH2.findConfigInfoLike4PageCountRows(context);
        assertEquals(
            "SELECT count(*) FROM config_info a WHERE a.tenant_id LIKE ? ESCAPE '\\' AND "
                + LIKE_TAGS,
            normalize(mapperResult.getSql()));
        assertArrayEquals(params(tenantId, tagArr), mapperResult.getParamList().toArray());
    }
    
    @Test
    void testFindConfigInfoLike4PageCountRowsAppliesTheTypeFilterLikeThePage() {
        context.putWhereParameter(FieldConstant.TYPE, new String[] {"json", "yaml"});
        MapperResult count =
            configInfoTagsRelationMapperByH2.findConfigInfoLike4PageCountRows(context);
        MapperResult page =
            configInfoTagsRelationMapperByH2.findConfigInfoLike4PageFetchRows(context);
        
        assertEquals(
            "SELECT count(*) FROM config_info a WHERE a.tenant_id LIKE ? ESCAPE '\\' AND "
                + LIKE_TAGS
                + " AND a.type IN (?, ?)",
            normalize(count.getSql()));
        assertEquals(LIKE_PAGE_SELECT + " WHERE a.tenant_id LIKE ? ESCAPE '\\' AND " + LIKE_TAGS
            + " AND a.type IN (?, ?)" + PAGE_TAIL,
            normalize(page.getSql()));
        assertArrayEquals(params(tenantId, tagArr, "json", "yaml"), count.getParamList().toArray());
        assertArrayEquals(params(tenantId, tagArr, "json", "yaml"), page.getParamList().toArray());
    }
    
    @Test
    void testFindConfigInfoLike4PageFetchRows() {
        MapperResult mapperResult =
            configInfoTagsRelationMapperByH2.findConfigInfoLike4PageFetchRows(context);
        assertEquals(
            LIKE_PAGE_SELECT + " WHERE a.tenant_id LIKE ? ESCAPE '\\' AND " + LIKE_TAGS + PAGE_TAIL,
            normalize(mapperResult.getSql()));
        assertArrayEquals(params(tenantId, tagArr), mapperResult.getParamList().toArray());
    }
    
    @Test
    void testFindConfigInfoLike4PageFetchRowsWithAllFilters() {
        context.putWhereParameter(FieldConstant.DATA_ID, "test_data");
        context.putWhereParameter(FieldConstant.GROUP_ID, "test_group");
        context.putWhereParameter(FieldConstant.APP_NAME, "test_app");
        context.putWhereParameter(FieldConstant.CONTENT, "test_content");
        MapperResult mapperResult =
            configInfoTagsRelationMapperByH2.findConfigInfoLike4PageFetchRows(context);
        assertEquals(LIKE_PAGE_SELECT + " WHERE a.tenant_id LIKE ? ESCAPE '\\' "
            + "AND a.data_id LIKE ? ESCAPE '\\' AND a.group_id LIKE ? ESCAPE '\\' "
            + "AND a.app_name = ? AND a.content LIKE ? ESCAPE '\\' AND " + LIKE_TAGS + PAGE_TAIL,
            normalize(mapperResult.getSql()));
        assertArrayEquals(
            params(tenantId, "test_data", "test_group", "test_app", "test_content", tagArr),
            mapperResult.getParamList().toArray());
    }
    
    @Test
    void testGetTableName() {
        String tableName = configInfoTagsRelationMapperByH2.getTableName();
        assertEquals(TableConstant.CONFIG_TAGS_RELATION, tableName);
    }
    
    @Test
    void testGetDataSource() {
        String dataSource = configInfoTagsRelationMapperByH2.getDataSource();
        assertEquals(DataSourceConstant.H2, dataSource);
    }
    
    private static String normalize(String sql) {
        return sql.replaceAll("\\s+", " ").trim();
    }
    
    /**
     * Build the expected parameter list. Array arguments contribute their elements, in order.
     */
    private static Object[] params(Object... parts) {
        List<Object> result = new ArrayList<>();
        for (Object part : parts) {
            if (part instanceof Object[]) {
                result.addAll(Arrays.asList((Object[]) part));
            } else {
                result.add(part);
            }
        }
        return result.toArray();
    }
}
