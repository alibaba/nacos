package com.alibaba.nacos.core.remote;

/**
 * Enum representing different types of communication.
 *
 * <p>CommunicationType includes:</p>
 * <ul>
 *     <li>SDK: Communication between SDK and servers.</li>
 *     <li>CLUSTER: Communication between servers in a cluster.</li>
 * </ul>
 *
 * @author stone-98
 * @date 2023/12/23
 */
public enum CommunicationType {
    /**
     * Communication between SDK and servers
     */
    SDK("sdk"),
    /**
     * Communication between servers in a cluster
     */
    CLUSTER("cluster");
    
    private final String type;
    
    CommunicationType(String type) {
        this.type = type;
    }
    
    public String getType() {
        return type;
    }
}

