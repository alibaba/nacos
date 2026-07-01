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

package com.alibaba.nacos.ai.service.ard;

import com.alibaba.nacos.ai.model.AiResourceVersion;
import com.alibaba.nacos.ai.model.ard.ArdEntry;

import java.util.Collections;
import java.util.List;

/**
 * Optional loader for source content used by ARD index enhancement.
 *
 * @author nacos
 */
public interface ArdIndexContentLoader {
    
    /**
     * No-op loader used by tests and unsupported resource types.
     */
    ArdIndexContentLoader NOOP = (entry, version) -> Collections.emptyList();
    
    /**
     * Load source content snippets for one ARD entry.
     */
    List<ArdIndexEnhancementContent> load(ArdEntry entry, AiResourceVersion version)
        throws Exception;
}
