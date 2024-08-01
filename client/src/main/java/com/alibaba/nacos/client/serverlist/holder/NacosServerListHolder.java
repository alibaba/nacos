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

package com.alibaba.nacos.client.serverlist.holder;

import com.alibaba.nacos.client.env.NacosClientProperties;

import java.util.List;

/**
 * server list holder.
 *
 * @author xz
 * @since 2024/7/24 15:07
 */
public interface NacosServerListHolder {
    /**
     * get server list.
     *
     * @return init server list or new list
     */
    List<String> getServerList();

    /**
     * is server list holder.
     *
     * @param properties nacos client properties
     * @param moduleName nacos use module name,current have naming or config.
     * @return true or false
     */
    boolean canApply(NacosClientProperties properties, String moduleName);

    /**
     * get holder name.
     *
     * @return holder name
     */
    String getName();

    /**
     * get order number.
     *
     * @return order number
     */
    int getOrder();
}
