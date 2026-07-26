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

package com.alibaba.nacos.copilot.constant;

/**
 * Nacos Copilot Constants.
 *
 * @author nacos
 */
public class CopilotConstants {
    
    public static final String COPILOT_PATH = "/copilot";
    
    public static final String COPILOT_CONSOLE_PATH = "/v3/console" + COPILOT_PATH;
    
    public static final String SKILL_OPTIMIZE_PATH = "/skill/optimize";
    
    public static final String SKILL_GENERATE_PATH = "/skill/generate";
    
    public static final String PROMPT_OPTIMIZE_PATH = "/prompt/optimize";
    
    public static final String PROMPT_DEBUG_PATH = "/prompt/debug";
    
    public static final String CHAT_PATH = "/chat";
    
    public static final String CHAT_HISTORY_PATH = "/chat/history";
    
    private CopilotConstants() {
        // Private constructor to prevent instantiation
    }
}
