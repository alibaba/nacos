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

import com.alibaba.nacos.api.exception.runtime.NacosLoadException;
import com.alibaba.nacos.client.env.NacosClientProperties;
import com.alibaba.nacos.client.serverlist.holder.impl.EndpointNacosServerListHolder;
import com.alibaba.nacos.client.serverlist.holder.impl.FixedConfigNacosServerListHolder;
import com.alibaba.nacos.common.spi.NacosServiceLoader;
import com.alibaba.nacos.common.utils.CollectionUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * composite nacos server discovery.
 *
 * @author xz
 * @since 2024/7/24 17:17
 */
public class NacosServerListHolders {

    private NacosClientProperties nacosClientProperties;

    private final List<NacosServerListHolder> delegates;

    private NacosServerListHolder owner;

    private static final String NAME = "composite";

    private static final int ORDER = 0;

    public NacosServerListHolders(NacosClientProperties clientProperties) {
        this.nacosClientProperties = clientProperties;

        delegates = new ArrayList<>();
        delegates.add(new FixedConfigNacosServerListHolder());
        delegates.add(new EndpointNacosServerListHolder());
        delegates.addAll(NacosServiceLoader.load(NacosServerListHolder.class));

        delegates.sort(Comparator.comparing(NacosServerListHolder::getOrder));
    }

    public List<String> getServerList() {
        return Objects.isNull(owner) ? new ArrayList<>() : owner.getServerList();
    }

    /**
     * load server list.
     *
     * @return
     */
    public List<String> loadServerList() {
        for (NacosServerListHolder delegate : delegates) {
            if (delegate.canApply(nacosClientProperties)) {
                List<String> serverList = delegate.getServerList();
                if (CollectionUtils.isNotEmpty(serverList)) {
                    owner = delegate;
                    return serverList;
                }
                throw new NacosLoadException("serverList is empty,please check configuration");
            }
        }

        throw new NacosLoadException("can not found serverList,please check configuration");
    }

    public String getName() {
        return Objects.isNull(owner) ? NAME : owner.getName();
    }
}
