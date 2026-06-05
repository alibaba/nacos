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

package com.alibaba.nacos.test.maintainer.core;

import com.alibaba.nacos.maintainer.client.config.ConfigMaintainerService;
import com.alibaba.nacos.maintainer.client.core.CoreMaintainerService;
import com.alibaba.nacos.test.maintainer.MaintainerSdkBaseITCase;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration tests for {@link CoreMaintainerService}.
 *
 * <p>Scenario coverage:
 * <ul>
 *     <li>Expected capability: maintainer SDK factory can create a client and
 *     query liveness/server-state through a running standalone Nacos server.</li>
 *     <li>Boundary/validation: profile wiring resolves the default
 *     {@code nacos.host}/{@code nacos.port} server address.</li>
 *     <li>Error handling: detailed readiness and unavailable-server mappings
 *     are documented as pending scenarios for later batches.</li>
 * </ul>
 *
 * @author xiweng.yy
 */
class CoreMaintainerServiceMaintainerSdkITCase extends MaintainerSdkBaseITCase {
    
    @Test
    void shouldQueryStandaloneServerState() throws Exception {
        ConfigMaintainerService maintainerService = createConfigMaintainerService();
        
        assertTrue(maintainerService.liveness());
        Map<String, String> serverState = maintainerService.getServerState();
        assertNotNull(serverState);
    }
}
