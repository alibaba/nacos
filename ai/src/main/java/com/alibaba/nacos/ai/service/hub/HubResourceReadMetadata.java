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

package com.alibaba.nacos.ai.service.hub;

import com.alibaba.nacos.ai.model.AiResource;
import com.alibaba.nacos.ai.service.resource.ResourceVersionInfo;
import com.alibaba.nacos.common.utils.JacksonUtils;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Display source labels for the pinned version without exposing other source versions. */
final class HubResourceReadMetadata {
    
    private HubResourceReadMetadata() {
    }
    
    static Map<String, String> labels(AiResource source, String version) {
        if (source.getVersionInfo() == null || source.getVersionInfo().isBlank()) {
            return Collections.singletonMap("latest", version);
        }
        ResourceVersionInfo info =
            JacksonUtils.toObj(source.getVersionInfo(), ResourceVersionInfo.class);
        return labels(info.getLabels(), version);
    }
    
    static Map<String, String> labels(Map<String, String> source, String version) {
        Map<String, String> result = new LinkedHashMap<>();
        if (source != null) {
            source.forEach((name, value) -> {
                if (version.equals(value)) {
                    result.put(name, value);
                }
            });
        }
        return result;
    }
}
