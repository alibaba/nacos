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

package com.alibaba.nacos.plugin.ai.importer.defaultimpl.http;

import org.junit.jupiter.api.Test;

import java.net.http.HttpHeaders;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ImportHttpResponseTest {
    
    @Test
    void testSuccessResponseGetter() {
        byte[] body = "ok".getBytes(StandardCharsets.UTF_8);
        ImportHttpResponse response =
            new ImportHttpResponse("https://example.com", 200, headers("application/json"), body);
        
        assertTrue(response.isSuccess());
        assertEquals("https://example.com", response.getUrl());
        assertEquals(200, response.getStatusCode());
        assertEquals("ok", new String(response.getBody(), StandardCharsets.UTF_8));
        assertEquals("application/json", response.getContentType());
    }
    
    @Test
    void testNonSuccessAndNullBody() {
        ImportHttpResponse response =
            new ImportHttpResponse("https://example.com", 500, headers(null), null);
        
        assertFalse(response.isSuccess());
        assertEquals(0, response.getBody().length);
        assertEquals("", response.getContentType());
    }
    
    private HttpHeaders headers(String contentType) {
        if (contentType == null) {
            return HttpHeaders.of(Collections.emptyMap(), (key, value) -> true);
        }
        Map<String, java.util.List<String>> headers = new HashMap<>(1);
        headers.put("Content-Type", Collections.singletonList(contentType));
        return HttpHeaders.of(headers, (key, value) -> true);
    }
}
