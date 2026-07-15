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

package com.alibaba.nacos.ai.form.pipeline;

import com.alibaba.nacos.api.exception.api.NacosApiException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Tests for PipelineListForm validation.
 *
 * @author kiro
 * @since 3.2.0
 */
class PipelineListFormValidationTest {
    
    private static String[] blankStrings() {
        return new String[] {"", " ", "  ", "\t", "\n", " \t\n "};
    }
    
    private static String[] nonBlankStrings() {
        return new String[] {"a", "skill", "RESOURCE_TYPE", "abc123def"};
    }
    
    /**
     * Blank resourceType should be rejected with 400.
     *
     * <p><b>Validates: Requirement 3.1</b></p>
     */
    @Test
    void blankResourceTypeShouldThrow400() {
        for (String blankResourceType : blankStrings()) {
            PipelineListForm form = new PipelineListForm();
            form.setResourceType(blankResourceType);
            NacosApiException ex = assertThrows(NacosApiException.class, form::validate);
            assertEquals(400, ex.getErrCode());
        }
    }
    
    /**
     * Non-blank resourceType with missing optional params should not throw.
     *
     * <p><b>Validates: Requirement 3.2</b></p>
     */
    @Test
    void nonBlankResourceTypeWithMissingOptionalsShouldPass() {
        for (String resourceType : nonBlankStrings()) {
            PipelineListForm form = new PipelineListForm();
            form.setResourceType(resourceType);
            assertDoesNotThrow(form::validate);
        }
    }
}
