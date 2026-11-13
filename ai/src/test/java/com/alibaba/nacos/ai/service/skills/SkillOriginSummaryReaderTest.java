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

package com.alibaba.nacos.ai.service.skills;

import com.alibaba.nacos.api.ai.model.skills.SkillOriginSummary;
import org.junit.jupiter.api.Test;
import com.alibaba.nacos.common.utils.JacksonUtils;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class SkillOriginSummaryReaderTest {
    
    @Test
    void extractsHubOriginWithoutLeakingInternalSourceKeys() {
        SkillOriginSummary result = SkillOriginSummaryReader.fromExt("{\"origin\":{"
            + "\"sourceNamespaceId\":\"secret-ns\",\"sourceResourceId\":11,"
            + "\"sourceVersionId\":22,\"sourceName\":\"find-skills\","
            + "\"sourceVersion\":\"1.0.0\",\"hubEntryId\":901}} ");
        assertEquals("find-skills", result.getSourceName());
        assertEquals("1.0.0", result.getSourceVersion());
        assertEquals(901L, result.getHubEntryId());
        assertEquals("HUB_IMPORT", result.getMode());
    }
    
    @Test
    void directCopyDoesNotGetAHubLink() {
        SkillOriginSummary result = SkillOriginSummaryReader.fromExt("{\"origin\":{"
            + "\"sourceName\":\"skill\",\"sourceVersion\":\"2.0.0\","
            + "\"hubEntryId\":null}} ");
        assertEquals("DIRECT_COPY", result.getMode());
        assertNull(result.getHubEntryId());
    }
    
    @Test
    void ignoresMalformedOrMissingOrigin() {
        assertNull(SkillOriginSummaryReader.fromExt(null));
        assertNull(SkillOriginSummaryReader.fromExt("garbage"));
        assertNull(SkillOriginSummaryReader.fromExt("{}"));
        assertNull(SkillOriginSummaryReader.fromExt("{\"origin\":{\"sourceName\":\"bad\"}}"));
    }
    
    @Test
    void safeProjectionSerializesLargeIdsAsStringsWithoutPrivateFields() {
        SkillOriginSummary result = SkillOriginSummaryReader.fromExt("{\"origin\":{"
            + "\"sourceNamespaceId\":\"secret-ns\",\"sourceVersionId\":22,"
            + "\"sourceName\":\"find-skills\",\"sourceVersion\":\"1.0.0\","
            + "\"hubEntryId\":9223372036854775807}} ");
        String json = JacksonUtils.toJson(result);
        assertTrue(json.contains("\"hubEntryId\":\"9223372036854775807\""));
        assertFalse(json.contains("secret-ns"));
        assertFalse(json.contains("sourceVersionId"));
    }
    
    @Test
    void nestedValuesCannotLeakDescriptorsThroughStringCoercion() {
        assertNull(SkillOriginSummaryReader.fromExt("{\"origin\":{"
            + "\"sourceName\":{\"secret\":\"descriptor\"},\"sourceVersion\":\"1.0.0\"}}"));
        assertNull(SkillOriginSummaryReader.fromExt("{\"origin\":{"
            + "\"sourceName\":\"skill\",\"sourceVersion\":[\"descriptor\"]}}"));
        SkillOriginSummary result = SkillOriginSummaryReader.fromExt("{\"origin\":{"
            + "\"sourceName\":\"skill\",\"sourceVersion\":\"1.0.0\","
            + "\"importedAt\":{\"secret\":\"descriptor\"}}}");
        assertNull(result.getImportedAt());
    }
    
    @Test
    void malformedHubIdsDoNotBecomeMisleadingDirectCopies() {
        for (String id : new String[] {"0", "-1", "1.5", "9223372036854775808", "{}", "[]"}) {
            assertNull(SkillOriginSummaryReader.fromExt("{\"origin\":{"
                + "\"sourceName\":\"skill\",\"sourceVersion\":\"1.0.0\",\"hubEntryId\":" + id
                + "}}"));
        }
    }
    
}
