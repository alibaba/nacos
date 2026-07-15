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

package com.alibaba.nacos.ai.constant;

/**
 * Shared constants for AI resource operations (Skill, AgentSpec, etc.).
 *
 * <p>Extracts duplicated constants that were previously defined independently
 * in {@code SkillOperationServiceImpl} and {@code AgentSpecOperationServiceImpl}.</p>
 *
 * @author nacos
 */
public final class AiResourceConstants {
    
    private AiResourceConstants() {
    }
    
    /**
     * Meta status: resource is enabled.
     */
    public static final String META_STATUS_ENABLE = "enable";
    
    /**
     * Meta status: resource is disabled.
     */
    public static final String META_STATUS_DISABLE = "disable";
    
    /**
     * Version status: version is online (published and active).
     */
    public static final String VERSION_STATUS_ONLINE = "online";
    
    /**
     * Version status: version is in draft (not yet submitted).
     */
    public static final String VERSION_STATUS_DRAFT = "draft";
    
    /**
     * Version status: version is under review (pipeline running).
     */
    public static final String VERSION_STATUS_REVIEWING = "reviewing";
    
    /**
     * Version status: version has been reviewed (pipeline approved, awaiting publish).
     */
    public static final String VERSION_STATUS_REVIEWED = "reviewed";
    
    /**
     * Version status: version has been taken offline.
     */
    public static final String VERSION_STATUS_OFFLINE = "offline";
    
    /**
     * Maximum retry count for CAS-based meta update operations.
     */
    public static final int MAX_WORKING_VERSION_RETRY = 3;
    
    /**
     * Label key that points to the latest published version.
     */
    public static final String LABEL_LATEST = "latest";
}
