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
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Property-based tests for PipelineListForm validation.
 *
 * @author kiro
 * @since 3.2.0
 */
class PipelineListFormPropertyTest {
    
    /**
     * Property 7a: Blank resourceType should be rejected with 400.
     *
     * <p><b>Validates: Requirement 3.1</b></p>
     */
    @Property(tries = 30)
    void blankResourceTypeShouldThrow400(@ForAll("blankStrings") String blankResourceType) {
        PipelineListForm form = new PipelineListForm();
        form.setResourceType(blankResourceType);
        NacosApiException ex = assertThrows(NacosApiException.class, form::validate);
        assertEquals(400, ex.getErrCode());
    }
    
    /**
     * Property 7b: Non-blank resourceType with missing optional params should not throw.
     *
     * <p><b>Validates: Requirement 3.2</b></p>
     */
    @Property(tries = 30)
    void nonBlankResourceTypeWithMissingOptionalsShouldPass(
            @ForAll("nonBlankStrings") String resourceType) {
        PipelineListForm form = new PipelineListForm();
        form.setResourceType(resourceType);
        // Leave resourceName, namespaceId, version as null
        assertDoesNotThrow(form::validate);
    }
    
    @Provide
    Arbitrary<String> blankStrings() {
        return Arbitraries.of("", " ", "  ", "\t", "\n", " \t\n ");
    }
    
    @Provide
    Arbitrary<String> nonBlankStrings() {
        return Arbitraries.strings().alpha().ofMinLength(1).ofMaxLength(30);
    }
}
