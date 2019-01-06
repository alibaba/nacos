package com.alibaba.nacos.api.naming.loadbalancer;


/**
 *
 * Load-Balancer enum
 * Load-Balancer that has been implemented by Nacos
 * @author XCXCXCXCX
 */
public enum LoadBalancerEnum {

    RANDOM("随机"),
    RANDOM_BY_WEIGHT("加权随机"),
    POLL("轮询"),
    POLL_BY_WEIGHT("加权轮询"),
    IP_HASH("IP哈希");

    private String description;

    LoadBalancerEnum(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
