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

package com.alibaba.nacos.plugin.address.spi;

import com.alibaba.nacos.api.utils.StringUtils;
import com.alibaba.nacos.common.spi.NacosServiceLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Date 2022/7/30.
 *
 * @author GuoJiangFu
 */
public class AddressPluginManager {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(AddressPlugin.class);
    
    private static final AddressPluginManager INSTANCE = new AddressPluginManager();
    
    private final Map<String, AddressPlugin> addressPluginMap = new HashMap<>();
    
    private AddressPluginManager() {
        initAddressPlugins();
    }
    
    private void initAddressPlugins() {
        Collection<AddressPlugin> addressPlugins = NacosServiceLoader.load(AddressPlugin.class);
        for (AddressPlugin each : addressPlugins) {
            if (StringUtils.isEmpty(each.getPluginName())) {
                LOGGER.warn(
                        "[AddressPluginManager] Load AddressPlugin({}) PluginName(null/empty) fail. Please Add PluginName to resolve.",
                        each.getClass());
                continue;
            }
            addressPluginMap.put(each.getPluginName(), each);
            LOGGER.info("[AddressPluginManager] Load AddressPlugin({}) PluginName({}) successfully.",
                    each.getClass(), each.getPluginName());
        }
    }
    
    public static AddressPluginManager getInstance() {
        return INSTANCE;
    }
    
    /**
    * Get address plugin by plugin name
    *@Param: plugin name
    *@return: address plugin
    */
    public Optional<AddressPlugin> findAuthServiceSpiImpl(String pluginName) {
        return Optional.ofNullable(addressPluginMap.get(pluginName));
    }
    
}
