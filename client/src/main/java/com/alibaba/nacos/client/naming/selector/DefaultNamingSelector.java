/*
 * Copyright 1999-2023 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.client.naming.selector;

import com.alibaba.nacos.api.naming.listener.NamingEvent;
import com.alibaba.nacos.api.naming.pojo.Instance;
import com.alibaba.nacos.api.naming.selector.NamingContext;
import com.alibaba.nacos.api.naming.selector.NamingSelector;
import com.alibaba.nacos.client.naming.event.InstancesDiff;
import com.alibaba.nacos.client.naming.listener.NamingChangeEvent;

import java.util.Collections;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Default naming selector.
 *
 * @author lideyou
 */
public class DefaultNamingSelector implements NamingSelector {
    private final Predicate<Instance> filter;

    public DefaultNamingSelector(Predicate<Instance> filter) {
        this.filter = filter;
    }

    @Override
    public NamingEvent select(NamingContext context) {
        List<Instance> currentIns = doFilter(context.getCurrentInstances());

        InstancesDiff instancesDiff = new InstancesDiff(
                doFilter(context.getAddedInstances()),
                doFilter(context.getRemovedInstances()),
                doFilter(context.getModifiedInstances()));

        return new NamingChangeEvent(context.getServiceName(), context.getGroupName(),
                context.getClusters(), currentIns, instancesDiff);
    }

    private List<Instance> doFilter(List<Instance> instances) {
        return instances == null ? Collections.emptyList() :
                instances
                        .stream()
                        .filter(filter)
                        .collect(Collectors.toList());
    }
}
