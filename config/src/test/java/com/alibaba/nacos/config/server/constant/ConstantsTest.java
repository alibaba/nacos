/*
 * Copyright 1999-2018 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.config.server.constant;

import org.junit.Assert;
import org.junit.Test;

import static com.alibaba.nacos.config.server.constant.Constants.CAPACITY_CONTROLLER_PATH;
import static com.alibaba.nacos.config.server.constant.Constants.COMMUNICATION_CONTROLLER_PATH;
import static com.alibaba.nacos.config.server.constant.Constants.CONFIG_CONTROLLER_PATH;
import static com.alibaba.nacos.config.server.constant.Constants.HEALTH_CONTROLLER_PATH;
import static com.alibaba.nacos.config.server.constant.Constants.HISTORY_CONTROLLER_PATH;
import static com.alibaba.nacos.config.server.constant.Constants.LISTENER_CONTROLLER_PATH;
import static com.alibaba.nacos.config.server.constant.Constants.NAMESPACE_CONTROLLER_PATH;

public class ConstantsTest {
    
    @Test
    public void testControllerPathsDefaultValues() {
        
        Assert.assertEquals("/v1/cs/capacity", CAPACITY_CONTROLLER_PATH);
        Assert.assertEquals("/v1/cs/communication", COMMUNICATION_CONTROLLER_PATH);
        Assert.assertEquals("/v1/cs/configs", CONFIG_CONTROLLER_PATH);
        Assert.assertEquals("/v1/cs/health", HEALTH_CONTROLLER_PATH);
        Assert.assertEquals("/v1/cs/history", HISTORY_CONTROLLER_PATH);
        Assert.assertEquals("/v1/cs/listener", LISTENER_CONTROLLER_PATH);
        Assert.assertEquals("/v1/cs/namespaces", NAMESPACE_CONTROLLER_PATH);
    }
}
