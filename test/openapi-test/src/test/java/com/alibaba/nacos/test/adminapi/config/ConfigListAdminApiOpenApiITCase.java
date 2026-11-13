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
    public void testBlurListKeepsPageBoundariesAndFields() throws Exception {
        String prefix = randomDataId("sample-routing");
        String group = randomGroupName("page-fields");
        String content = "sample-routing=true\nlast-line=true";
        for (int index = 0; index < 3; index++) {
            String dataId = prefix + "-" + index;
            addCleanup(() -> deleteConfigQuietly(dataId, group, ""));
            publishConfig(dataId, group, "", index == 2 ? content : "sample=" + index,
                    ConfigType.TEXT.getType(), "sample description", index == 2 ? "sample-tag" : "");
        }
        for (int pageNo = 1; pageNo <= 3; pageNo++) {
            JsonNode page = list(prefix + "*", group, pageNo, 2).get("data");
            assertEquals(3, page.get("totalCount").asInt());
            assertEquals(2, page.get("pagesAvailable").asInt());
            assertEquals(pageNo, page.get("pageNumber").asInt());
            int expectedSize = pageNo == 1 ? 2 : pageNo == 2 ? 1 : 0;
            assertEquals(expectedSize, page.get("pageItems").size());
            for (int index = 0; index < expectedSize; index++) {
                int ordinal = (pageNo - 1) * 2 + index;
                JsonNode item = page.get("pageItems").get(index);
                assertEquals(prefix + "-" + ordinal, item.get("dataId").asText());
                assertEquals(group, item.get("groupName").asText());
                assertEquals(md5(ordinal == 2 ? content : "sample=" + ordinal), item.get("md5").asText());
                assertEquals("public", item.path("namespaceId").asText());
                assertEquals(ConfigType.TEXT.getType(), item.path("type").asText());
                assertEquals("sample description", item.path("desc").asText());
                assertEquals(ordinal == 2 ? "sample-tag" : "", item.path("configTags").asText(""));
            }
        }
        assertEquals(content, queryConfig(prefix + "-2", group, "").path("data").path("content").asText());
        JsonNode contentPage = getJsonOk(ADMIN_CONFIG_LIST_PATH,
                listQuery(prefix + "*", group, "", 1, 100).addParam("configDetail", "*last-line=true"))
                .get("data");
        assertEquals(1, contentPage.get("totalCount").asInt());
        assertEquals(prefix + "-2", contentPage.get("pageItems").get(0).get("dataId").asText());
        JsonNode noMatch = getJsonOk(ADMIN_CONFIG_LIST_PATH,
                listQuery(prefix + "*", group, "", 1, 10).addParam("configDetail", "missing' OR 1=1 --"))
                .get("data");
        assertEquals(0, noMatch.path("totalCount").asInt());
        assertEquals(0, noMatch.path("pageItems").size());
    }
    
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
