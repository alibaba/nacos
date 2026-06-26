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

package com.alibaba.nacos.common.json;

import com.alibaba.nacos.api.utils.json.JsonUtils;
import com.alibaba.nacos.api.utils.json.NacosJsonAdapterNames;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Logs selected JSON adapter for client diagnostics.
 *
 * @author nacos
 */
public final class JsonAdapterLogUtils {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(JsonAdapterLogUtils.class);
    
    private static final AtomicBoolean LOGGED = new AtomicBoolean(false);
    
    private JsonAdapterLogUtils() {
    }
    
    /**
     * Log selected JSON adapter once for current JVM.
     */
    public static void logSelectedAdapter() {
        if (LOGGED.compareAndSet(false, true)) {
            LOGGER.info("[json-adapter] {}",
                formatLogMessage(JsonUtils.selectedAdapterName(), configuredAdapterName()));
        }
    }
    
    private static String formatLogMessage(String effectiveAdapter, String configuredAdapter) {
        return "effective Jackson adapter: " + formatEffectiveAdapter(effectiveAdapter)
            + ", configured "
            + JsonUtils.ADAPTER_PROPERTY_NAME + ": " + configuredAdapter;
    }
    
    private static String formatEffectiveAdapter(String effectiveAdapter) {
        if (NacosJsonAdapterNames.JACKSON2.equals(effectiveAdapter)) {
            return "Jackson 2 (adapter: " + effectiveAdapter + ")";
        }
        if (NacosJsonAdapterNames.JACKSON3.equals(effectiveAdapter)) {
            return "Jackson 3 (adapter: " + effectiveAdapter + ")";
        }
        return effectiveAdapter;
    }
    
    private static String configuredAdapterName() {
        String configured = System.getProperty(JsonUtils.ADAPTER_PROPERTY_NAME);
        if (configured == null || configured.trim().isEmpty()) {
            return NacosJsonAdapterNames.AUTO;
        }
        return configured.trim();
    }
}
