/*
 * Copyright 1999-2023 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.client.utils;

import com.alibaba.nacos.client.auth.ram.utils.SpasAdapter;
import com.alibaba.nacos.common.utils.JacksonUtils;

/**
 * Async do pre init to load some cost component.
 *
 * <ul>
 *     <li>JacksonUtil</li>
 *     <li>SpasAdapter</li>
 * </ul>
 *
 * @author xiweng.yy
 */
public class PreInitUtils {
    
    /**
     * Async pre load cost component.
     */
    @SuppressWarnings("PMD.AvoidManuallyCreateThreadRule")
    public static void asyncPreLoadCostComponent() {
        Thread preLoadThread = new Thread(() -> {
            // Jackson util will init static {@code ObjectMapper}, which will cost hundreds milliseconds.
            JacksonUtils.createEmptyJsonNode();
            // Ram auth plugin will try to get credential from env and system when leak input identity by properties.
            SpasAdapter.getAk();
        });
        preLoadThread.start();
    }
}
