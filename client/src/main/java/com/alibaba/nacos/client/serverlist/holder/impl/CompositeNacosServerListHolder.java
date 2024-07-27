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

package com.alibaba.nacos.client.serverlist.holder.impl;

import com.alibaba.nacos.client.env.NacosClientProperties;
import com.alibaba.nacos.client.serverlist.holder.NacosServerListHolder;
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
public class CompositeNacosServerListHolder implements NacosServerListHolder {
    private final List<NacosServerListHolder> delegates;

    private NacosServerListHolder owner;

    private static final String NAME = "composite";

    private static final int ORDER = 0;

    public CompositeNacosServerListHolder() {
        delegates = new ArrayList<>();
        delegates.add(new FixedConfigNacosServerListHolder());
        delegates.add(new EndpointNacosServerListHolder());
        delegates.addAll(NacosServiceLoader.load(NacosServerListHolder.class));

        delegates.sort(Comparator.comparing(NacosServerListHolder::getOrder));
    }

    @Override
    public List<String> getServerList() {
        return Objects.isNull(owner) ? new ArrayList<>() : owner.getServerList();
    }

    @Override
    public List<String> initServerList(NacosClientProperties properties) {
        for (NacosServerListHolder delegate : delegates) {
            if (delegate.getClass().equals(this.getClass())) {
                continue;
            }
            List<String> serverList = delegate.initServerList(properties);
            if (CollectionUtils.isNotEmpty(serverList)) {
                owner = delegate;
                return serverList;
            }
        }

        return new ArrayList<>();
    }

    public String getName() {
        return Objects.isNull(owner) ? NAME : owner.getName();
    }

    @Override
    public int getOrder() {
        return ORDER;
    }
}
