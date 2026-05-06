/*
 * Copyright 1999-2024 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.plugin.encryption;

import com.alibaba.nacos.api.plugin.PluginType;
import com.alibaba.nacos.plugin.encryption.spi.EncryptionPluginService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * {@link EncryptionPluginProvider} unit test.
 *
 * @author WangzJi
 */
class EncryptionPluginProviderTest {

    private EncryptionPluginProvider provider;

    @BeforeEach
    void setUp() {
        provider = new EncryptionPluginProvider();
    }

    @Test
    void getPluginTypeTest() {
        PluginType pluginType = provider.getPluginType();

        assertNotNull(pluginType);
        assertEquals(PluginType.ENCRYPTION, pluginType);
        assertEquals("encryption", pluginType.getType());
    }

    @Test
    void getAllPluginsTest() {
        Map<String, EncryptionPluginService> plugins = provider.getAllPlugins();

        assertNotNull(plugins);
    }

    @Test
    void getOrderTest() {
        int order = provider.getOrder();

        assertEquals(0, order);
    }
}
