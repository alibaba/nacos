package com.alibaba.nacos.naming.cluster.servers;

import java.util.List;

/**
 * Nacos cluster member change event listener
 *
 * @author nkorange
 * @since 1.0.0
 */
public interface ServerChangeListener {

    /**
     * If member list changed, this method is invoked.
     *
     * @param servers servers after change
     */
    void onChangeServerList(List<Server> servers);

    /**
     * If reachable member list changed, this method is invoked.
     *
     * @param healthyServer reachable servers after change
     */
    void onChangeHealthyServerList(List<Server> healthyServer);
}
