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

package com.alibaba.nacos.api.utils.json;

import java.util.Collections;

/**
 * Test helper for configuring {@link JsonUtils}.
 *
 * @author nacos
 */
public final class JsonUtilsTestHelper {
    
    private JsonUtilsTestHelper() {
    }
    
    /**
     * Use a single JSON adapter for the current test.
     *
     * @param adapter JSON adapter
     */
    public static void useAdapter(NacosJsonAdapter adapter) {
        JsonUtils.setAdapterSelectorForTest(
            new JsonAdapterSelector(Collections.<NacosJsonAdapter>singletonList(adapter)));
    }
    
    /**
     * Reset JSON utils test state.
     */
    public static void reset() {
        System.clearProperty(JsonUtils.ADAPTER_PROPERTY_NAME);
        JsonUtils.resetForTest();
    }
}
