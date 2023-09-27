package com.alibaba.nacos.api.ability.constant;

/**
 * Ability mode.
 *
 * @author Daydreamer
 * @date 2023/9/25 12:32
 **/
public enum AbilityMode {

    /**
     * for server ability.
     */
    SERVER,

    /**
     * for sdk client.
     */
    SDK_CLIENT,

    /**
     * for cluster client.
     */
    CLUSTER_CLIENT;
}
