/*
 * Copyright 1999-2022 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.plugin.config;

import com.alibaba.nacos.plugin.config.constants.ConfigChangeExecuteTypes;
import com.alibaba.nacos.plugin.config.constants.ConfigChangePointCutTypes;
import com.alibaba.nacos.plugin.config.model.ConfigChangeRequest;
import com.alibaba.nacos.plugin.config.model.ConfigChangeResponse;
import com.alibaba.nacos.plugin.config.spi.ConfigChangePluginService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ConfigChangePluginManagerTests.
 *
 * @author liyunfei
 **/
class ConfigChangePluginManagerTests {
    
    @Test
    void testInstance() {
        ConfigChangePluginManager instance = ConfigChangePluginManager.getInstance();
        assertNotNull(instance);
    }
    
    @BeforeEach
    void initPluginServices() {
        ConfigChangePluginManager.join(new ConfigChangePluginService() {
            @Override
            public void execute(ConfigChangeRequest configChangeRequest, ConfigChangeResponse configChangeResponse) {
                // ignore
            }
            
            @Override
            public ConfigChangeExecuteTypes executeType() {
                return ConfigChangeExecuteTypes.EXECUTE_BEFORE_TYPE;
            }
            
            @Override
            public String getServiceType() {
                return "test1";
            }
            
            @Override
            public int getOrder() {
                return 0;
            }
            
            @Override
            public ConfigChangePointCutTypes[] pointcutMethodNames() {
                return new ConfigChangePointCutTypes[] {ConfigChangePointCutTypes.PUBLISH_BY_HTTP,
                        ConfigChangePointCutTypes.PUBLISH_BY_RPC};
            }
        });
        ConfigChangePluginManager.join(new ConfigChangePluginService() {
            @Override
            public void execute(ConfigChangeRequest configChangeRequest, ConfigChangeResponse configChangeResponse) {
                // ignore
            }
            
            @Override
            public ConfigChangeExecuteTypes executeType() {
                return ConfigChangeExecuteTypes.EXECUTE_BEFORE_TYPE;
            }
            
            @Override
            public String getServiceType() {
                return "test2";
            }
            
            @Override
            public int getOrder() {
                return 200;
            }
            
            @Override
            public ConfigChangePointCutTypes[] pointcutMethodNames() {
                return new ConfigChangePointCutTypes[] {ConfigChangePointCutTypes.IMPORT_BY_HTTP, ConfigChangePointCutTypes.PUBLISH_BY_RPC};
            }
        });
        ConfigChangePluginManager.join(new ConfigChangePluginService() {
            @Override
            public void execute(ConfigChangeRequest configChangeRequest, ConfigChangeResponse configChangeResponse) {
                // ignore
            }
            
            @Override
            public ConfigChangeExecuteTypes executeType() {
                return ConfigChangeExecuteTypes.EXECUTE_AFTER_TYPE;
            }
            
            @Override
            public String getServiceType() {
                return "test3";
            }
            
            @Override
            public int getOrder() {
                return 400;
            }
            
            @Override
            public ConfigChangePointCutTypes[] pointcutMethodNames() {
                return new ConfigChangePointCutTypes[] {ConfigChangePointCutTypes.IMPORT_BY_HTTP, ConfigChangePointCutTypes.PUBLISH_BY_RPC,
                        ConfigChangePointCutTypes.REMOVE_BATCH_HTTP, ConfigChangePointCutTypes.REMOVE_BY_RPC,
                        ConfigChangePointCutTypes.REMOVE_BY_HTTP};
            }
        });
        
        ConfigChangePluginManager.join(new ConfigChangePluginService() {
            @Override
            public void execute(ConfigChangeRequest configChangeRequest, ConfigChangeResponse configChangeResponse) {
                // ignore
            }
            
            @Override
            public ConfigChangeExecuteTypes executeType() {
                return ConfigChangeExecuteTypes.EXECUTE_AFTER_TYPE;
            }
            
            @Override
            public String getServiceType() {
                return "test4";
            }
            
            @Override
            public int getOrder() {
                return 600;
            }
            
            @Override
            public ConfigChangePointCutTypes[] pointcutMethodNames() {
                return new ConfigChangePointCutTypes[] {ConfigChangePointCutTypes.PUBLISH_BY_HTTP,
                        ConfigChangePointCutTypes.REMOVE_BATCH_HTTP, ConfigChangePointCutTypes.REMOVE_BY_RPC,
                        ConfigChangePointCutTypes.REMOVE_BY_HTTP};
            }
        });
        
    }
    
    @Test
    void testFindPluginServiceQueueByPointcut() {
        List<ConfigChangePluginService> configChangePluginServices = ConfigChangePluginManager.findPluginServicesByPointcut(
                ConfigChangePointCutTypes.PUBLISH_BY_HTTP);
        assertEquals(2, configChangePluginServices.size());
        assertTrue(isSorted(configChangePluginServices));
        configChangePluginServices = ConfigChangePluginManager.findPluginServicesByPointcut(ConfigChangePointCutTypes.PUBLISH_BY_RPC);
        assertEquals(3, configChangePluginServices.size());
        assertTrue(isSorted(configChangePluginServices));
        configChangePluginServices = ConfigChangePluginManager.findPluginServicesByPointcut(ConfigChangePointCutTypes.IMPORT_BY_HTTP);
        assertEquals(2, configChangePluginServices.size());
        assertTrue(isSorted(configChangePluginServices));
        configChangePluginServices = ConfigChangePluginManager.findPluginServicesByPointcut(ConfigChangePointCutTypes.REMOVE_BATCH_HTTP);
        assertEquals(2, configChangePluginServices.size());
        assertTrue(isSorted(configChangePluginServices));
        configChangePluginServices = ConfigChangePluginManager.findPluginServicesByPointcut(ConfigChangePointCutTypes.REMOVE_BY_RPC);
        assertEquals(2, configChangePluginServices.size());
        assertTrue(isSorted(configChangePluginServices));
        configChangePluginServices = ConfigChangePluginManager.findPluginServicesByPointcut(ConfigChangePointCutTypes.REMOVE_BY_HTTP);
        assertEquals(2, configChangePluginServices.size());
        assertTrue(isSorted(configChangePluginServices));
    }
    
    @Test
    void testFindPluginServiceByServiceType() {
        Optional<ConfigChangePluginService> configChangePluginServiceOptional = ConfigChangePluginManager.getInstance()
                .findPluginServiceImpl("test1");
        assertTrue(configChangePluginServiceOptional.isPresent());
        configChangePluginServiceOptional = ConfigChangePluginManager.getInstance().findPluginServiceImpl("test2");
        assertTrue(configChangePluginServiceOptional.isPresent());
        configChangePluginServiceOptional = ConfigChangePluginManager.getInstance().findPluginServiceImpl("test3");
        assertTrue(configChangePluginServiceOptional.isPresent());
        configChangePluginServiceOptional = ConfigChangePluginManager.getInstance().findPluginServiceImpl("test4");
        assertTrue(configChangePluginServiceOptional.isPresent());
        configChangePluginServiceOptional = ConfigChangePluginManager.getInstance().findPluginServiceImpl("test5");
        assertFalse(configChangePluginServiceOptional.isPresent());
    }
    
    private boolean isSorted(List<ConfigChangePluginService> list) {
        return IntStream.range(0, list.size() - 1).allMatch(i -> list.get(i).getOrder() <= list.get(i + 1).getOrder());
    }
}
