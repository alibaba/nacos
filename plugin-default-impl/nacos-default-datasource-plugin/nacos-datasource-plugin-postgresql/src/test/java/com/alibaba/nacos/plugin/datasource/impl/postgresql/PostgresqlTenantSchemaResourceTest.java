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

package com.alibaba.nacos.plugin.datasource.impl.postgresql;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class PostgresqlTenantSchemaResourceTest {
    
    @Test
    void testTenantColumnsAreHardenedInSchemaResource() throws IOException {
        try (InputStream inputStream =
            getClass().getClassLoader().getResourceAsStream("META-INF/pg-schema.sql")) {
            assertNotNull(inputStream);
            String schema = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
            Matcher matcher = Pattern.compile("\"tenant_id\" varchar\\(128\\) NOT NULL DEFAULT ''")
                .matcher(schema);
            int count = 0;
            while (matcher.find()) {
                count++;
            }
            assertEquals(4, count);
        }
    }
}
