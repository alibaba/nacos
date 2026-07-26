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

package com.alibaba.nacos.test.consoleapi.config;

import com.alibaba.nacos.api.config.ConfigType;
import com.alibaba.nacos.api.model.v2.ErrorCode;
import com.alibaba.nacos.common.http.param.Query;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Integration tests for console config list/search OpenAPIs.
 *
 * <p>Scenario coverage:
 * <ul>
 *     <li>Expected capability: list returns published configs in the console page model, supports fuzzy dataId search,
 *     advanced filters such as type/tags, exact content search via {@code searchDetail}, and pagination fields.</li>
 *     <li>Boundary/validation: omitted namespaceId uses public, blank dataId is accepted for group-scoped listing,
 *     no-match searches return a successful empty page, and pageNo/pageSize must be positive.</li>
 *     <li>Exception/error handling: pagination validation failures return HTTP 400 with the v3 {@code Result} envelope
 *     instead of HTTP 500.</li>
 * </ul>
 *
 * @author xiweng.yy
 */
public class ConfigListConsoleApiOpenApiITCase extends ConfigConsoleApiBaseITCase {

    @Test
    public void testListConfigsByFuzzyDataIdAdvancedFiltersAndContentSearch() throws Exception {
        String prefix = randomDataId("list");
        String groupName = randomGroupName("list");
        String firstDataId = prefix + "_a";
        String secondDataId = prefix + "_b";
        String firstContent = "console-list-content-alpha";
        String secondContent = "console-list-content-beta";
        String firstTags = "console-list-tag-a";
        publishConfig(firstDataId, groupName, "", firstContent, ConfigType.JSON.getType(), "first", firstTags);
        addCleanup(() -> deleteConfigQuietly(firstDataId, groupName, ""));
        publishConfig(secondDataId, groupName, "", secondContent, ConfigType.YAML.getType(), "second",
                "console-list-tag-b");
        addCleanup(() -> deleteConfigQuietly(secondDataId, groupName, ""));

        String dataIdPattern = prefix + "*";
        JsonNode allPage = getJsonOk(CONSOLE_CONFIG_LIST_PATH, listQuery(dataIdPattern, groupName, "", 1, 10))
                .get("data");
        assertEquals(1, allPage.get("pageNumber").asInt(), allPage.toString());
        assertEquals(2, allPage.get("totalCount").asInt(), allPage.toString());
        assertPageContainsConfig(allPage, firstDataId, groupName, md5(firstContent));
        assertPageContainsConfig(allPage, secondDataId, groupName, md5(secondContent));

        JsonNode paged = getJsonOk(CONSOLE_CONFIG_LIST_PATH, listQuery(dataIdPattern, groupName, "", 1, 1))
                .get("data");
        assertEquals(1, paged.get("pageNumber").asInt(), paged.toString());
        assertEquals(2, paged.get("totalCount").asInt(), paged.toString());
        assertEquals(2, paged.get("pagesAvailable").asInt(), paged.toString());
        assertEquals(1, paged.get("pageItems").size(), paged.toString());

        JsonNode typePage = getJsonOk(CONSOLE_CONFIG_LIST_PATH,
                listQuery(dataIdPattern, groupName, "", 1, 10).addParam("type", ConfigType.JSON.getType()))
                .get("data");
        assertEquals(1, typePage.get("totalCount").asInt(), typePage.toString());
        assertEquals(ConfigType.JSON.getType(), findConfig(typePage, firstDataId, groupName).get("type").asText(),
                typePage.toString());
        assertPageNotContainsConfig(typePage, secondDataId, groupName);

        JsonNode tagsPage = getJsonOk(CONSOLE_CONFIG_LIST_PATH,
                listQuery(dataIdPattern, groupName, "", 1, 10).addParam("configTags", firstTags)).get("data");
        assertEquals(1, tagsPage.get("totalCount").asInt(), tagsPage.toString());
        assertPageContainsConfig(tagsPage, firstDataId, groupName, md5(firstContent));

        JsonNode contentPage = getJsonOk(CONSOLE_CONFIG_SEARCH_DETAIL_PATH,
                listQuery(firstDataId, groupName, "", 1, 10).addParam("configDetail", firstContent)
                        .addParam("search", "accurate")).get("data");
        assertEquals(1, contentPage.get("totalCount").asInt(), contentPage.toString());
        assertPageContainsConfig(contentPage, firstDataId, groupName, md5(firstContent));
        assertPageNotContainsConfig(contentPage, secondDataId, groupName);

        JsonNode partialContent = getJsonOk(CONSOLE_CONFIG_SEARCH_DETAIL_PATH,
                listQuery(dataIdPattern, groupName, "", 1, 10).addParam("configDetail", "alpha")
                        .addParam("search", "blur")).get("data");
        assertEquals(0, partialContent.get("totalCount").asInt(), partialContent.toString());
    }

    @Test
    public void testListAllowsBlankDataIdAndNoMatchReturnsEmptyPage() throws Exception {
        String dataId = randomDataId("blank-data");
        String groupName = randomGroupName("blank-data");
        String content = "blank-data-list-content";
        publishConfig(dataId, groupName, "", content, ConfigType.TEXT.getType(), "blank data id", "");
        addCleanup(() -> deleteConfigQuietly(dataId, groupName, ""));

        JsonNode groupScoped = getJsonOk(CONSOLE_CONFIG_LIST_PATH, listQuery("", groupName, null, 1, 10))
                .get("data");
        assertEquals(1, groupScoped.get("pageNumber").asInt(), groupScoped.toString());
        assertPageContainsConfig(groupScoped, dataId, groupName, md5(content));

        JsonNode noMatch = getJsonOk(CONSOLE_CONFIG_LIST_PATH,
                listQuery("no-match-" + randomDataId("list") + "*", groupName, "", 1, 10)).get("data");
        assertEquals(1, noMatch.get("pageNumber").asInt(), noMatch.toString());
        assertEquals(0, noMatch.get("totalCount").asInt(), noMatch.toString());
        assertEquals(0, noMatch.get("pagesAvailable").asInt(), noMatch.toString());
        assertEquals(0, noMatch.get("pageItems").size(), noMatch.toString());
    }

    @Test
    public void testListAndSearchInvalidPaginationReturnBadRequest() throws Exception {
        assertError(getRaw(CONSOLE_CONFIG_LIST_PATH + "?search=blur&pageNo=0"), 400,
                ErrorCode.PARAMETER_VALIDATE_ERROR, "pageNo");
        assertError(getRaw(CONSOLE_CONFIG_LIST_PATH + "?search=blur&pageSize=0"), 400,
                ErrorCode.PARAMETER_VALIDATE_ERROR, "pageSize");
        assertError(getRaw(CONSOLE_CONFIG_SEARCH_DETAIL_PATH + "?search=blur&pageNo=0"), 400,
                ErrorCode.PARAMETER_VALIDATE_ERROR, "pageNo");
    }
}
