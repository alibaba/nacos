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

package com.alibaba.nacos.api.ai.model.prompt;

import java.io.Serializable;

/**
 * Prompt history item for history list response.
 *
 * @author nacos
 */
public class PromptHistoryItem implements Serializable {
    
    private static final long serialVersionUID = 1L;
    
    /**
     * History record ID.
     */
    private Long id;
    
    /**
     * Prompt key.
     */
    private String promptKey;
    
    /**
     * Version of this history record.
     */
    private String version;
    
    /**
     * Commit message for this version.
     */
    private String commitMsg;
    
    /**
     * Operator who made this change.
     */
    private String srcUser;
    
    /**
     * Operation type: I (Insert), U (Update), D (Delete).
     */
    private String opType;
    
    /**
     * Publish time of this version.
     */
    private Long publishTime;
    
    public Long getId() {
        return id;
    }
    
    public void setId(Long id) {
        this.id = id;
    }
    
    public String getPromptKey() {
        return promptKey;
    }
    
    public void setPromptKey(String promptKey) {
        this.promptKey = promptKey;
    }
    
    public String getVersion() {
        return version;
    }
    
    public void setVersion(String version) {
        this.version = version;
    }
    
    public String getCommitMsg() {
        return commitMsg;
    }
    
    public void setCommitMsg(String commitMsg) {
        this.commitMsg = commitMsg;
    }
    
    public String getSrcUser() {
        return srcUser;
    }
    
    public void setSrcUser(String srcUser) {
        this.srcUser = srcUser;
    }
    
    public String getOpType() {
        return opType;
    }
    
    public void setOpType(String opType) {
        this.opType = opType;
    }
    
    public Long getPublishTime() {
        return publishTime;
    }
    
    public void setPublishTime(Long publishTime) {
        this.publishTime = publishTime;
    }
}
