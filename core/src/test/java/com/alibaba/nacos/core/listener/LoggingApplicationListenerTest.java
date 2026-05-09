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

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.StandardEnvironment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * {@link LoggingApplicationListener} unit test.
 */
class LoggingApplicationListenerTest {
    
    private static final String CONFIG_PROPERTY =
        org.springframework.boot.context.logging.LoggingApplicationListener.CONFIG_PROPERTY;
    private static final String DEFAULT_NACOS_LOGBACK_LOCATION =
        "classpath:META-INF/logback/nacos.xml";
    
    @AfterEach
    void tearDown() {
        System.clearProperty(CONFIG_PROPERTY);
    }
    
    @Test
    void environmentPreparedWhenPropertyAbsentSetsSystemProperty() {
        StandardEnvironment environment = new StandardEnvironment();
        LoggingApplicationListener listener = new LoggingApplicationListener();
        assertNull(System.getProperty(CONFIG_PROPERTY));
        listener.environmentPrepared(environment);
        assertEquals(DEFAULT_NACOS_LOGBACK_LOCATION, System.getProperty(CONFIG_PROPERTY));
    }
    
    @Test
    void environmentPreparedWhenPropertyPresentDoesNotOverride() {
        String customLocation = "classpath:custom-logback.xml";
        StandardEnvironment environment = new StandardEnvironment();
        MutablePropertySources sources = environment.getPropertySources();
        sources.addFirst(new MapPropertySource("test",
            java.util.Collections.singletonMap(CONFIG_PROPERTY, customLocation)));
        LoggingApplicationListener listener = new LoggingApplicationListener();
        listener.environmentPrepared(environment);
        assertEquals(customLocation, environment.getProperty(CONFIG_PROPERTY));
    }
}
