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

package com.alibaba.nacos.client.selector;

import com.alibaba.nacos.api.naming.listener.NamingEvent;
import com.alibaba.nacos.api.naming.pojo.Instance;
import com.alibaba.nacos.api.naming.selector.NamingContext;
import com.alibaba.nacos.api.naming.selector.NamingSelector;
import com.alibaba.nacos.client.naming.event.InstancesDiff;
import com.alibaba.nacos.client.naming.listener.NamingChangeEvent;
import com.alibaba.nacos.client.naming.selector.DefaultNamingSelector;
import com.alibaba.nacos.common.utils.CollectionUtils;
import com.alibaba.nacos.common.utils.StringUtils;

import java.util.Collection;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Predicate;

/**
 * Selectors factory.
 *
 * @author lideyou
 */
public final class SelectorFactory {
    private static final NamingSelector EMPTY_SELECTOR = SelectorFactory::transferToNamingEvent;

    private SelectorFactory() {
    }

    private static class ClusterSelector extends DefaultNamingSelector {
        private final String clusterString;

        public ClusterSelector(Predicate<Instance> filter, String clusterString) {
            super(filter);
            this.clusterString = clusterString;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            ClusterSelector that = (ClusterSelector) o;
            return Objects.equals(this.clusterString, that.clusterString);
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(this.clusterString);
        }
    }

    /**
     * Create a cluster selector.
     *
     * @param clusters target cluster
     * @return cluster selector
     */
    public static NamingSelector newClusterSelector(Collection<String> clusters) {
        if (CollectionUtils.isNotEmpty(clusters)) {
            final Set<String> set = new HashSet<>(clusters);
            Predicate<Instance> filter = instance -> set.contains(instance.getClusterName());
            String clusterString = getUniqueClusterString(clusters);
            return new ClusterSelector(filter, clusterString);
        } else {
            return EMPTY_SELECTOR;
        }
    }

    private static String getUniqueClusterString(Collection<String> cluster) {
        TreeSet<String> treeSet = new TreeSet<>(cluster);
        return StringUtils.join(treeSet, ",");
    }

    private static NamingEvent transferToNamingEvent(NamingContext context) {
        InstancesDiff instancesDiff = new InstancesDiff(
                context.getAddedInstances(),
                context.getRemovedInstances(),
                context.getModifiedInstances()
        );
        return new NamingChangeEvent(context.getServiceName(), context.getGroupName(),
                context.getClusters(), context.getCurrentInstances(), instancesDiff);
    }
}
