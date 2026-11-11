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

package com.alibaba.nacos.airegistry.service.ard;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.alibaba.nacos.airegistry.contract.ArdContractSchemas;
import com.alibaba.nacos.airegistry.model.ard.ArdEntry;
import com.alibaba.nacos.airegistry.model.ard.ArdSearchResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Publication quality and legacy compatibility without changing search eligibility.
 *
 * @author nacos
 */
class ArdEntryRendererTest {
    
    @ParameterizedTest
    @ValueSource(ints = {-1, 0, 1, 2, 5, 6})
    void shouldRetainCurrentExamplesAndWarnWithoutChangingSource(int count) {
        ArdSearchResult source = source();
        List<String> queries = count < 0 ? null : new ArrayList<>();
        for (int i = 0; i < count; i++) {
            queries.add("Find capability " + i);
        }
        source.setRepresentativeQueries(queries);
        Logger logger = (Logger) LoggerFactory.getLogger(ArdEntryRenderer.class);
        ListAppender<ILoggingEvent> events = new ListAppender<>();
        events.start();
        logger.addAppender(events);
        try {
            ArdEntry result = ArdEntryRenderer.manifestEntry(source);
            assertEquals(queries, result.getRepresentativeQueries());
            assertEquals(count < 2 || count > 5 ? 1 : 0, events.list.size());
            if (!events.list.isEmpty()) {
                assertTrue(events.list.get(0).getFormattedMessage()
                    .contains("recommended count is 2 to 5"));
                assertEquals(ch.qos.logback.classic.Level.WARN, events.list.get(0).getLevel());
            }
            var json = new ObjectMapper().valueToTree(result);
            assertTrue(new ArdContractSchemas().validateEntry("ArdEntry", json).isEmpty());
            assertFalse(json.has("score"));
            assertFalse(json.has("source"));
            assertEquals(count < 2 ? null : queries.subList(0, Math.min(5, count)),
                ArdEntryRenderer.legacyQueries(queries));
            if (queries != null) {
                queries.clear();
                assertEquals(count, result.getRepresentativeQueries().size());
            }
        } finally {
            logger.detachAppender(events);
            events.stop();
        }
    }
    
    @Test
    void shouldPreserveSuppliedStringsWithoutInventingExamples() {
        List<String> values =
            new ArrayList<>(List.of("Find a tool", "Find a tool", "", "   ", "five", "six"));
        ArdSearchResult source = source();
        source.setRepresentativeQueries(values);
        assertEquals(values, ArdEntryRenderer.manifestEntry(source).getRepresentativeQueries());
        assertEquals(values.subList(0, 5), ArdEntryRenderer.legacyQueries(values));
        assertEquals(6, source.getRepresentativeQueries().size());
    }
    
    private ArdSearchResult source() {
        ArdSearchResult source = new ArdSearchResult();
        source.setIdentifier("urn:air:example.com:skill:demo");
        source.setDisplayName("Demo");
        source.setType("application/agent-skills+zip");
        source.setUrl("https://example.com/demo.zip");
        source.setScore(80);
        source.setSource("https://example.com/registry");
        return source;
    }
}
