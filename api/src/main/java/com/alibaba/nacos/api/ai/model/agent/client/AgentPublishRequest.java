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

package com.alibaba.nacos.api.ai.model.agent.client;

import com.alibaba.nacos.api.ai.model.agent.base.AbstractAgentDraftRequest;

/**
 * Client definition publication; a new first Version is submitted automatically.
 * Other drafts follow autoSubmit (default false); existing non-drafts remain unchanged.
 *
 * @author Nacos
 * @since 3.3.0
 */
public class AgentPublishRequest extends AbstractAgentDraftRequest {
    
    private static final long serialVersionUID = 1L;
    
    private boolean autoSubmit;
    
    /**
     * Whether to submit a draft through the ordinary pipeline; defaults to false.
     * A newly created first Version is submitted regardless of this flag. With no Pipeline,
     * submission publishes and selects latest; a configured Pipeline controls completion.
     * Existing non-draft Versions remain unchanged.
     *
     * @return whether to submit an editable draft, except for the automatic first submission
     */
    public boolean isAutoSubmit() {
        return autoSubmit;
    }
    
    /**
     * Set the draft submission choice described by {@link #isAutoSubmit()}.
     *
     * @param autoSubmit whether to submit; false does not suppress first-Version submission
     */
    public void setAutoSubmit(boolean autoSubmit) {
        this.autoSubmit = autoSubmit;
    }
    
}
