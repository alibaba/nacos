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

import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;

class PreInitUtilsTest {
    
    @Test
    void testAsyncPreLoadCostComponent() throws InterruptedException {
        // There is no things need to be assert.
        // The method will called when nacos-client init to async to load some components to reduce the sync load time.
        PreInitUtils.asyncPreLoadCostComponent();
        // No exception is ok.
        // Let async thread run completed
        TimeUnit.SECONDS.sleep(2);
    }
}