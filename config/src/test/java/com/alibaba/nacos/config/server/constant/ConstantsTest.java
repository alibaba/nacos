/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
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
import org.springframework.mock.env.MockEnvironment;

import static com.alibaba.nacos.config.server.constant.Constants.*;

/**
 * {@link Constants} Test
 *
 * @author <a href="mailto:mercyblitz@gmail.com">Mercy</a>
 * @since 0.2.2
 */
public class ConstantsTest {

    @Test
    public void testControllerPathsDefaultValues() {

        MockEnvironment environment = new MockEnvironment();

        Assert.assertEquals(DEFAULT_CAPACITY_CONTROLLER_PATH, environment.resolvePlaceholders(CAPACITY_CONTROLLER_PATH));
        Assert.assertEquals(DEFAULT_COMMUNICATION_CONTROLLER_PATH, environment.resolvePlaceholders(COMMUNICATION_CONTROLLER_PATH));
        Assert.assertEquals(DEFAULT_CONFIG_CONTROLLER_PATH, environment.resolvePlaceholders(CONFIG_CONTROLLER_PATH));
        Assert.assertEquals(DEFAULT_HEALTH_CONTROLLER_PATH, environment.resolvePlaceholders(HEALTH_CONTROLLER_PATH));
        Assert.assertEquals(DEFAULT_HISTORY_CONTROLLER_PATH, environment.resolvePlaceholders(HISTORY_CONTROLLER_PATH));
        Assert.assertEquals(DEFAULT_LISTENER_CONTROLLER_PATH, environment.resolvePlaceholders(LISTENER_CONTROLLER_PATH));
        Assert.assertEquals(DEFAULT_NAMESPACE_CONTROLLER_PATH, environment.resolvePlaceholders(NAMESPACE_CONTROLLER_PATH));


        Assert.assertEquals("/nacos/v1/cs/capacity", DEFAULT_CAPACITY_CONTROLLER_PATH);
        Assert.assertEquals("/nacos/v1/cs/communication", DEFAULT_COMMUNICATION_CONTROLLER_PATH);
        Assert.assertEquals("/nacos/v1/cs/configs", DEFAULT_CONFIG_CONTROLLER_PATH);
        Assert.assertEquals("/nacos/v1/cs/health", DEFAULT_HEALTH_CONTROLLER_PATH);
        Assert.assertEquals("/nacos/v1/cs/history", DEFAULT_HISTORY_CONTROLLER_PATH);
        Assert.assertEquals("/nacos/v1/cs/listener", DEFAULT_LISTENER_CONTROLLER_PATH);
        Assert.assertEquals("/nacos/v1/cs/namespaces", DEFAULT_NAMESPACE_CONTROLLER_PATH);
    }
}
