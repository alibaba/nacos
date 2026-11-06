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

package com.alibaba.nacos.test.nativeimage.plugin;

import com.alibaba.nacos.plugin.config.constants.ConfigChangeExecuteTypes;
import com.alibaba.nacos.plugin.config.constants.ConfigChangePointCutTypes;
import com.alibaba.nacos.plugin.config.model.ConfigChangeRequest;
import com.alibaba.nacos.plugin.config.model.ConfigChangeResponse;
import com.alibaba.nacos.plugin.config.spi.ConfigChangePluginService;

/**
 * Refuses to publish a configuration whose content contains {@link #MARKER}. A test publishes one
 * with and one without the marker, and the answer of the Server tells whether this plugin ran.
 *
 * @author Nacos
 */
public class RejectingConfigChangePlugin implements ConfigChangePluginService {

    /**
     * The text that makes the plugin refuse a publication.
     */
    public static final String MARKER = "rejected-by-plugin";

    /**
     * The service type, which the plugin list of the Server shows.
     */
    public static final String SERVICE_TYPE = "native-test-rejecting";

    @Override
    public void execute(ConfigChangeRequest configChangeRequest,
            ConfigChangeResponse configChangeResponse) {
        Object content = configChangeRequest.getArg("content");
        if (content instanceof String && ((String) content).contains(MARKER)) {
            configChangeResponse.setSuccess(false);
            configChangeResponse.setMsg("The content contains " + MARKER);
        }
    }

    @Override
    public ConfigChangeExecuteTypes executeType() {
        return ConfigChangeExecuteTypes.EXECUTE_BEFORE_TYPE;
    }

    @Override
    public String getServiceType() {
        return SERVICE_TYPE;
    }

    @Override
    public int getOrder() {
        return 1;
    }

    @Override
    public ConfigChangePointCutTypes[] pointcutMethodNames() {
        return new ConfigChangePointCutTypes[] {ConfigChangePointCutTypes.PUBLISH_BY_HTTP};
    }
}
