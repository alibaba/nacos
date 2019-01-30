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
     * @param latestMembers servers after chang
     */
    void onChangeServerList(List<Server> latestMembers);

    /**
     * If reachable member list changed, this method is invoked.
     *
     * @param latestReachableMembers reachable servers after change
     */
    void onChangeHealthServerList(List<Server> latestReachableMembers);
}
