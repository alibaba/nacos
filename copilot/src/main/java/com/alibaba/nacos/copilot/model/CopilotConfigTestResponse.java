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

package com.alibaba.nacos.copilot.model;

public class CopilotConfigTestResponse {

    /**
     * Whether the overall connection test succeeds.
     * 整体连接检测是否成功。
     */
    private boolean success;

    /**
     * Whether the submitted configuration passes basic validation,
     * such as required apiKey/model checks.
     * 提交的配置是否通过基础校验，例如 apiKey、model 等必要字段检查。
     */
    private boolean configValid;

    /**
     * Whether the LLM service is reachable with the submitted configuration.
     * 基于当前提交的配置，LLM 服务是否可达。
     */
    private boolean llmReachable;

    /**
     * Source of the effective API key.
     * Expected values: ENV, CONFIG, NONE.
     * 当前生效的 API Key 来源。
     * 可选值为：ENV、CONFIG、NONE。
     */
    private String apiKeySource;

    /**
     * Whether Studio-related configuration is provided.
     * This field only reflects configuration presence and does not indicate connectivity.
     * 是否配置了 Studio 相关信息。
     * 该字段仅表示是否已配置，不代表 Studio 连通性。
     */
    private boolean studioConfigured;

    /**
     * Human-readable result message for frontend display.
     * 提供给前端展示的人类可读结果信息。
     */
    private String message;

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public boolean isConfigValid() {
        return configValid;
    }

    public void setConfigValid(boolean configValid) {
        this.configValid = configValid;
    }

    public boolean isLlmReachable() {
        return llmReachable;
    }

    public void setLlmReachable(boolean llmReachable) {
        this.llmReachable = llmReachable;
    }

    public String getApiKeySource() {
        return apiKeySource;
    }

    public void setApiKeySource(String apiKeySource) {
        this.apiKeySource = apiKeySource;
    }

    public boolean isStudioConfigured() {
        return studioConfigured;
    }

    public void setStudioConfigured(boolean studioConfigured) {
        this.studioConfigured = studioConfigured;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}
