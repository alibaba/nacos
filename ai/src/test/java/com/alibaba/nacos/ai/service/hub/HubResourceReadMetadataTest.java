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
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

class HubResourceReadMetadataTest {
    
    @Test
    void keepsActualPinnedLabelsWithoutFabricatingLatestOrExposingOtherVersions() {
        AiResource source = new AiResource();
        source.setVersionInfo(
            "{\"labels\":{\"stable\":\"1.0.0\",\"latest\":\"2.0.0\",\"canary\":\"2.0.0\"}}");
        assertEquals(Map.of("stable", "1.0.0"), HubResourceReadMetadata.labels(source, "1.0.0"));
    }
}
