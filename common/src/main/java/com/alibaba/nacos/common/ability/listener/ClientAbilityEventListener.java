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

package com.alibaba.nacos.common.ability.listener;

import com.alibaba.nacos.common.ability.discover.NacosAbilityManagerHolder;
import com.alibaba.nacos.common.remote.client.Connection;
import com.alibaba.nacos.common.remote.client.ConnectionEventListener;

/**.
 * @author Daydreamer
 * @description This listener is used for remove ability table if disconnected.
 * @date 2022/8/30 22:00
 **/
public class ClientAbilityEventListener implements ConnectionEventListener {
    
    @Override
    public void onConnected(Connection connection) {
        // nothing to do
    }
    
    @Override
    public void onDisConnect(Connection connection) {
        NacosAbilityManagerHolder.getInstance().removeTable(connection.getConnectionId());
    }
}
