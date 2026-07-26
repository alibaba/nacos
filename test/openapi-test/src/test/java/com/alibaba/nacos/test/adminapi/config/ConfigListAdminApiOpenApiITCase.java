/*
 * Copyright 1999-2026 Alibaba Group Holding Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.alibaba.nacos.test.adminapi.config;

import com.alibaba.nacos.api.config.ConfigType;
import com.alibaba.nacos.api.model.v2.ErrorCode;
import com.alibaba.nacos.common.http.param.Query;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Integration tests for config admin list OpenAPI {@code GET /nacos/v3/admin/cs/config/list}.
 *
 * <p>Scenario coverage:
 * <ul>
 *     <li>Expected capability: list returns published configs in the admin page model, supports {@code *}-based fuzzy
 *     dataId search, advanced filters such as type and tags, and reports pagination fields correctly.</li>
 *     <li>Boundary/validation: omitted namespaceId uses public; blank dataId is accepted for group-scoped listing;
 *     no-match searches return a successful empty page; pageNo and pageSize must be positive.</li>
 *     <li>Exception/error handling: pagination validation failures return HTTP 400 with the v3 {@code Result} error
 *     envelope instead of HTTP 500.</li>
 * </ul>
 *
 * @author xiweng.yy
 */
public class ConfigListAdminApiOpenApiITCase extends ConfigAdminApiBaseITCase {
    
    @Test
    public void testListConfigsByFuzzyDataIdAdvancedFiltersAndPagination() throws Exception {
        String prefix = randomDataId("list");
        String groupName = randomGroupName("list");
        String firstDataId = prefix + "_a";
        String secondDataId = prefix + "_b";
        String firstContent = "list-content-a";
        String secondContent = "list-content-b";
        String firstTags = "list-tag-a";
        publishConfig(firstDataId, groupName, "", firstContent, ConfigType.JSON.getType(), "first", firstTags);
        addCleanup(() -> deleteConfigQuietly(firstDataId, groupName, ""));
        publishConfig(secondDataId, groupName, "", secondContent, ConfigType.YAML.getType(), "second",
                "list-tag-b");
        addCleanup(() -> deleteConfigQuietly(secondDataId, groupName, ""));
        
        String dataIdPattern = prefix + "*";
        JsonNode allPage = list(dataIdPattern, groupName, 1, 10).get("data");
        assertEquals(1, allPage.get("pageNumber").asInt(), allPage.toString());
        assertEquals(2, allPage.get("totalCount").asInt(), allPage.toString());
        assertPageContainsConfig(allPage, firstDataId, groupName, md5(firstContent));
        assertPageContainsConfig(allPage, secondDataId, groupName, md5(secondContent));
        
        JsonNode paged = list(dataIdPattern, groupName, 1, 1).get("data");
        assertEquals(1, paged.get("pageNumber").asInt(), paged.toString());
        assertEquals(2, paged.get("totalCount").asInt(), paged.toString());
        assertEquals(2, paged.get("pagesAvailable").asInt(), paged.toString());
        assertEquals(1, paged.get("pageItems").size(), paged.toString());
        
        Query typeFilter = listQuery(dataIdPattern, groupName, "", 1, 10)
                .addParam("type", ConfigType.JSON.getType());
        JsonNode typePage = getJsonOk(ADMIN_CONFIG_LIST_PATH, typeFilter).get("data");
        assertEquals(1, typePage.get("totalCount").asInt(), typePage.toString());
        JsonNode first = findConfig(typePage, firstDataId, groupName);
        assertEquals(ConfigType.JSON.getType(), first.get("type").asText(), first.toString());
        assertPageNotContainsConfig(typePage, secondDataId, groupName);
        
        Query tagsFilter = listQuery(dataIdPattern, groupName, "", 1, 10).addParam("configTags", firstTags);
        JsonNode tagsPage = getJsonOk(ADMIN_CONFIG_LIST_PATH, tagsFilter).get("data");
        assertEquals(1, tagsPage.get("totalCount").asInt(), tagsPage.toString());
        assertPageContainsConfig(tagsPage, firstDataId, groupName, md5(firstContent));
        assertPageNotContainsConfig(tagsPage, secondDataId, groupName);
    }
    
    @Test
    public void testListConfigAllowsBlankDataIdAndNoMatchReturnsEmptyPage() throws Exception {
        String dataId = randomDataId("blank-data");
        String groupName = randomGroupName("blank-data");
        String content = "blank-data-list-content";
        publishConfig(dataId, groupName, "", content, ConfigType.TEXT.getType(), "blank data id", "");
        addCleanup(() -> deleteConfigQuietly(dataId, groupName, ""));
        
        JsonNode groupScoped = getJsonOk(ADMIN_CONFIG_LIST_PATH, listQuery("", groupName, null, 1, 10)).get("data");
        assertEquals(1, groupScoped.get("pageNumber").asInt(), groupScoped.toString());
        assertPageContainsConfig(groupScoped, dataId, groupName, md5(content));
        
        JsonNode noMatch = list("no-match-" + randomDataId("list") + "*", groupName, 1, 10).get("data");
        assertEquals(1, noMatch.get("pageNumber").asInt(), noMatch.toString());
        assertEquals(0, noMatch.get("totalCount").asInt(), noMatch.toString());
        assertEquals(0, noMatch.get("pagesAvailable").asInt(), noMatch.toString());
        assertEquals(0, noMatch.get("pageItems").size(), noMatch.toString());
    }
    
    @Test
    public void testListConfigInvalidPaginationReturnsBadRequest() throws Exception {
        assertError(getRaw(ADMIN_CONFIG_LIST_PATH + "?search=blur&pageNo=0"), 400,
                ErrorCode.PARAMETER_VALIDATE_ERROR, "pageNo");
        assertError(getRaw(ADMIN_CONFIG_LIST_PATH + "?search=blur&pageSize=0"), 400,
                ErrorCode.PARAMETER_VALIDATE_ERROR, "pageSize");
    }
    
    private JsonNode list(String dataId, String groupName, int pageNo, int pageSize) throws Exception {
        return getJsonOk(ADMIN_CONFIG_LIST_PATH, listQuery(dataId, groupName, "", pageNo, pageSize));
    }
}
