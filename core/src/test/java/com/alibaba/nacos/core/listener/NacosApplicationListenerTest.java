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

package com.alibaba.nacos.core.listener;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.mock.env.MockEnvironment;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

/**
 * {@link NacosApplicationListener} default methods unit test.
 */
@ExtendWith(MockitoExtension.class)
class NacosApplicationListenerTest {
    
    @Test
    void defaultMethodsDoNotThrow() {
        NacosApplicationListener listener = new NacosApplicationListener() {
        };
        assertDoesNotThrow(listener::starting);
        assertDoesNotThrow(() -> listener.environmentPrepared(new MockEnvironment()));
        ConfigurableApplicationContext ctx =
            org.mockito.Mockito.mock(ConfigurableApplicationContext.class);
        assertDoesNotThrow(() -> listener.contextPrepared(ctx));
        assertDoesNotThrow(() -> listener.contextLoaded(ctx));
        assertDoesNotThrow(() -> listener.started(ctx));
        assertDoesNotThrow(() -> listener.ready(ctx));
        assertDoesNotThrow(() -> listener.failed(ctx, new RuntimeException("test")));
    }
}
