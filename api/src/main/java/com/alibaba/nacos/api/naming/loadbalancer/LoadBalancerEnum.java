/*
 * Copyright 1999-2018 Alibaba Group Holding Ltd.
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
package com.alibaba.nacos.api.naming.loadbalancer;


/**
 *
 * Load-Balancer enum
 * Load-Balancer that has been implemented by Nacos
 * @author XCXCXCXCX
 */
public enum LoadBalancerEnum {

    /**
     * Random-Without-Weight
     */
    RANDOM("随机"),
    /**
     * Random-With-Weight
     */
    RANDOM_BY_WEIGHT("加权随机"),
    /**
     * Poll-Without-Weight
     */
    POLL("轮询"),
    /**
     * Poll-With-Weight
     * Implemented based on niginx weighted polling
     */
    POLL_BY_WEIGHT("加权轮询"),
    /**
     * Ip-Hash
     * A client ip selects an instance of a fixed index
     */
    IP_HASH("IP哈希");

    private String description;

    LoadBalancerEnum(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
