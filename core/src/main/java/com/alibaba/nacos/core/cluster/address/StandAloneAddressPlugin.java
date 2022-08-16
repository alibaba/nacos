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

package com.alibaba.nacos.core.cluster.address;

import com.alibaba.nacos.plugin.address.spi.AbstractAddressPlugin;
import com.alibaba.nacos.sys.env.EnvUtil;

import java.util.ArrayList;

/**
 * Get nacos server list standAlone
 * Date 2022/7/30.
 *
 * @author GuoJiangFu
 */
public class StandAloneAddressPlugin extends AbstractAddressPlugin {
    
    private static final String PLUGIN_NAME = "standalone";
    
    @Override
    public void start() {
        String url = EnvUtil.getLocalAddress();
        serverList = new ArrayList<>();
        serverList.add(url);
    }
    
    @Override
    public String getPluginName() {
        return PLUGIN_NAME;
    }
    
}
