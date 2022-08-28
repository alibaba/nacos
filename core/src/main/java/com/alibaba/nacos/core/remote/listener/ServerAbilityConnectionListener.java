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

package com.alibaba.nacos.core.remote.listener;

import com.alibaba.nacos.api.ability.entity.AbilityTable;
import com.alibaba.nacos.common.ability.discover.NacosAbilityManagerHolder;
import com.alibaba.nacos.core.remote.ClientConnectionEventListener;
import com.alibaba.nacos.core.remote.Connection;
import org.springframework.stereotype.Component;

/**.
 * @author Daydreamer
 * @description This listener is used to register or remove ability table.
 * @date 2022/7/17 19:18
 **/
@Component
public class ServerAbilityConnectionListener extends ClientConnectionEventListener {

    @Override
    public void clientConnected(Connection connect) {
        // it will be thought from client all
        AbilityTable abilityTable = new AbilityTable(connect.getMetaInfo().getConnectionId(), connect.getAbilityTable(),
                false, connect.getMetaInfo().getVersion());
        NacosAbilityManagerHolder.getInstance().addNewTable(abilityTable);
    }

    @Override
    public void clientDisConnected(Connection connect) {
        NacosAbilityManagerHolder.getInstance().removeTable(connect.getMetaInfo().getConnectionId());
    }
}
