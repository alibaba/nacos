/*
 * Copyright 1999-2025 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.api.ai.model.skills;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BatchUploadResultTest {
    
    @Test
    void testAddResultGeneratesLegacyFields() {
        BatchUploadResult result = new BatchUploadResult();
        result.addResult(BatchUploadItemResult.success("successful-skill"));
        result.addResult(BatchUploadItemResult.failure("failed-skill", "INVALID_SKILL",
            "Invalid SKILL.md", "owner"));
        
        assertEquals(2, result.getResults().size());
        assertEquals("successful-skill", result.getSucceeded().get(0));
        assertEquals(1, result.getFailed().size());
        assertEquals("failed-skill", result.getFailed().get(0).getName());
        assertEquals("Invalid SKILL.md", result.getFailed().get(0).getReason());
        assertEquals("owner", result.getFailed().get(0).getOwner());
    }
    
    @Test
    void testLegacyFieldsGenerateResults() {
        BatchUploadResult result = new BatchUploadResult();
        result.addSucceeded("successful-skill");
        result.addFailed("failed-skill", "owner", "Upload failed");
        
        assertEquals(2, result.getResults().size());
        BatchUploadItemResult successfulItem = result.getResults().get(0);
        assertEquals("successful-skill", successfulItem.getName());
        assertTrue(successfulItem.isSuccess());
        assertEquals(BatchUploadItemResult.ERROR_CODE_SUCCESS,
            successfulItem.getErrorCode());
        BatchUploadItemResult failedItem = result.getResults().get(1);
        assertEquals("failed-skill", failedItem.getName());
        assertFalse(failedItem.isSuccess());
        assertEquals(BatchUploadItemResult.ERROR_CODE_UPLOAD_FAILED,
            failedItem.getErrorCode());
        assertEquals("Upload failed", failedItem.getErrorMessage());
        assertEquals("owner", failedItem.getOwner());
    }
}
