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

package com.alibaba.nacos.test.consoleapi.core;

import com.alibaba.nacos.api.model.v2.ErrorCode;
import com.alibaba.nacos.common.http.param.Query;
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration tests for console server OpenAPIs under {@code /nacos/v3/console/server}.
 *
 * <p>Scenario coverage:
 * <ul>
 *     <li>Expected capability: state returns the console server state map, guide returns the configured guide text,
 *     and announcement returns the language-specific announcement in the v3 {@code Result} envelope.</li>
 *     <li>Boundary/validation: omitted announcement language defaults to zh-CN, en-US is accepted, and unexpected
 *     query parameters on the state endpoint are ignored without changing the map response contract.</li>
 *     <li>Exception/error handling: unsupported announcement language returns a controlled failure result instead of
 *     HTTP 500; state intentionally does not use the {@code Result} wrapper because the controller returns a raw map.</li>
 * </ul>
 *
 * @author xiweng.yy
 */
public class ServerStateConsoleApiOpenApiITCase extends CoreConsoleApiBaseITCase {

    @Test
    public void testServerStateGuideAndAnnouncement() throws Exception {
        HttpResponse stateResponse = getRaw(CONSOLE_SERVER_PATH + "/state?unexpected=ignored");
        assertEquals(200, stateResponse.code(), stateResponse.body());
        JsonNode state = JacksonUtils.toObj(stateResponse.body());
        assertTrue(state.isObject(), state.toString());
        assertTrue(state.size() > 0, state.toString());

        JsonNode guide = getJsonOk(CONSOLE_SERVER_PATH + "/guide", Query.newInstance());
        assertTrue(guide.get("data").isTextual(), guide.toString());

        JsonNode defaultAnnouncement = getJsonOk(CONSOLE_SERVER_PATH + "/announcement",
                Query.newInstance());
        assertTrue(defaultAnnouncement.get("data").isTextual(), defaultAnnouncement.toString());

        JsonNode enAnnouncement = getJsonOk(CONSOLE_SERVER_PATH + "/announcement",
                Query.newInstance().addParam("language", "en-US"));
        assertTrue(enAnnouncement.get("data").isTextual(), enAnnouncement.toString());
    }

    @Test
    public void testUnsupportedAnnouncementLanguageReturnsFailureResult() throws Exception {
        HttpResponse response = getRaw(CONSOLE_SERVER_PATH + "/announcement?language=fr-FR");
        assertEquals(200, response.code(), response.body());
        JsonNode root = JacksonUtils.toObj(response.body());
        assertEquals(ErrorCode.SERVER_ERROR.getCode(), root.get("code").asInt(), response.body());
        assertTrue(root.get("message").asText().contains("Unsupported language"), response.body());
    }
}
