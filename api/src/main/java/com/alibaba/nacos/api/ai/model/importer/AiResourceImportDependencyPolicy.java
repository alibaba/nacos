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

package com.alibaba.nacos.api.ai.model.importer;

/**
 * Dependency handling policy for AI resource import.
 *
 * @author xiweng.yy
 * @since 3.2.1
 */
public enum AiResourceImportDependencyPolicy {

    /**
     * Keep dependency metadata only.
     */
    IGNORE,

    /**
     * Validate whether matching resources exist.
     */
    VALIDATE_ONLY,

    /**
     * Link existing matching resources when supported.
     */
    LINK_EXISTING,

    /**
     * Import only dependencies explicitly selected by the user.
     */
    IMPORT_SELECTED
}
