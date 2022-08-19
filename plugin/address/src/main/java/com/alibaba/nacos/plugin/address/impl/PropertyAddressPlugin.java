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

package com.alibaba.nacos.plugin.address.impl;

import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.plugin.address.common.AddressProperties;
import com.alibaba.nacos.plugin.address.exception.AddressException;
import com.alibaba.nacos.plugin.address.spi.AbstractAddressPlugin;

import java.util.Arrays;

/**
 * Get nacos server list by addressProperties
 * Date 2022/7/30.
 *
 * @author GuoJiangFu
 */
public class PropertyAddressPlugin extends AbstractAddressPlugin {
    
    private static final String SERVER_ADDRESS_STR = "serverAddressStr";
    
    private static final String PLUGIN_NAME = "property-address-plugin";
    
    @Override
    public void start() throws AddressException {
        String serverAddressStr = AddressProperties.getProperty(SERVER_ADDRESS_STR);
        if (StringUtils.isEmpty(serverAddressStr)) {
            throw new AddressException("Param addressStr is empty");
        }
        
        this.serverList = Arrays.asList(serverAddressStr.split(","));
    }
    
    @Override
    public String getPluginName() {
        return PLUGIN_NAME;
    }
    
}
