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
