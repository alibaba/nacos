/*
 *  Copyright 1999-2021 Alibaba Group Holding Ltd.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */

package com.alibaba.nacos.core.remote;

import com.alibaba.nacos.api.remote.response.HealthCheckResponse;
import org.junit.Assert;
import org.junit.Test;

/**
 * {@link HealthCheckRequestHandler} unit test.
 *
 * @author chenglu
 * @date 2021-07-02 19:17
 */
public class HealthCheckRequestHandlerTest {
    
    @Test
    public void testHandle() {
        HealthCheckRequestHandler handler = new HealthCheckRequestHandler();
        HealthCheckResponse response = handler.handle(null, null);
        Assert.assertNotNull(response);
    }
}
