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

package com.alibaba.nacos.ai.utils;

import com.alibaba.nacos.api.ai.model.prompt.Prompt;
import com.alibaba.nacos.api.ai.model.prompt.PromptVariable;
import com.alibaba.nacos.api.ai.model.prompt.PromptVersionInfo;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

class PromptConvertUtilsTest {
    
    @Test
    void toClientPromptShouldCopyAllFields() {
        PromptVersionInfo versionInfo = new PromptVersionInfo();
        versionInfo.setPromptKey("demo");
        versionInfo.setVersion("1.0.0");
        versionInfo.setTemplate("Hello {{name}}");
        versionInfo.setMd5("abc123");
        List<PromptVariable> variables = Collections.singletonList(
            new PromptVariable("name", "world", "user name"));
        versionInfo.setVariables(variables);
        
        Prompt prompt = PromptConvertUtils.toClientPrompt(versionInfo);
        
        assertNotNull(prompt);
        assertEquals("demo", prompt.getPromptKey());
        assertEquals("1.0.0", prompt.getVersion());
        assertEquals("Hello {{name}}", prompt.getTemplate());
        assertEquals("abc123", prompt.getMd5());
        assertSame(variables, prompt.getVariables());
        assertEquals(1, prompt.getVariables().size());
        assertEquals("name", prompt.getVariables().get(0).getName());
    }
    
    @Test
    void toClientPromptShouldKeepNullVariablesWhenAbsent() {
        PromptVersionInfo versionInfo = new PromptVersionInfo();
        versionInfo.setPromptKey("demo");
        versionInfo.setVersion("1.0.0");
        versionInfo.setTemplate("Hello");
        versionInfo.setMd5("md5");
        
        Prompt prompt = PromptConvertUtils.toClientPrompt(versionInfo);
        
        assertNotNull(prompt);
        assertNull(prompt.getVariables());
    }
    
    @Test
    void toClientPromptShouldPropagateNullFields() {
        PromptVersionInfo versionInfo = new PromptVersionInfo();
        
        Prompt prompt = PromptConvertUtils.toClientPrompt(versionInfo);
        
        assertNotNull(prompt);
        assertNull(prompt.getPromptKey());
        assertNull(prompt.getVersion());
        assertNull(prompt.getTemplate());
        assertNull(prompt.getMd5());
        assertNull(prompt.getVariables());
    }
}
