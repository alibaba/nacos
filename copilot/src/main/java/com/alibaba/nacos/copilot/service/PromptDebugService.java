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

package com.alibaba.nacos.copilot.service;

import com.alibaba.nacos.copilot.adapter.StreamResponseCallback;
import com.alibaba.nacos.copilot.model.PromptDebugRequest;
import com.alibaba.nacos.copilot.model.PromptDebugResponse;

/**
 * Prompt debug service interface.
 *
 * @author nacos
 */
public interface PromptDebugService {
    
    /**
     * Debug prompt with stream response.
     * This will send the prompt as system prompt and user input to the LLM,
     * returning the model's response including thinking process.
     *
     * @param request  debug request containing prompt and user input
     * @param callback stream response callback
     */
    void debugPromptStream(PromptDebugRequest request,
        StreamResponseCallback<PromptDebugResponse> callback);
}
