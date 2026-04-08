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

package com.alibaba.nacos.ai.form.prompt;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

class PromptBizTagsUpdateFormTest {
    
    @Test
    void validateShouldPassWhenBizTagsProvided() {
        PromptBizTagsUpdateForm form = new PromptBizTagsUpdateForm();
        form.setPromptKey("p1");
        form.setBizTags("[\"tag1\",\"tag2\"]");
        assertDoesNotThrow(form::validate);
    }
    
    @Test
    void validateShouldPassWhenBizTagsMissing() {
        PromptBizTagsUpdateForm form = new PromptBizTagsUpdateForm();
        form.setPromptKey("p1");
        assertDoesNotThrow(form::validate);
    }
}
