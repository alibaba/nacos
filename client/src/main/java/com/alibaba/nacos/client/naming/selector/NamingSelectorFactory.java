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

import com.alibaba.nacos.api.naming.pojo.Instance;
import com.alibaba.nacos.api.naming.selector.NamingSelector;
import com.alibaba.nacos.common.utils.CollectionUtils;
import com.alibaba.nacos.common.utils.StringUtils;

import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Predicate;
import java.util.regex.Pattern;

/**
 * Selectors factory.
 *
 * @author lideyou
 */
public final class NamingSelectorFactory {
    
    public static final NamingSelector EMPTY_SELECTOR = context -> context::getInstances;
    
    public static final NamingSelector HEALTHY_SELECTOR = new DefaultNamingSelector(Instance::isHealthy);
    
    /**
     * Cluster selector.
     */
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
    
    private NamingSelectorFactory() {
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
    
    /**
     * Create a IP selector.
     *
     * @param regex regular expression of IP
     * @return IP selector
     */
    public static NamingSelector newIpSelector(String regex) {
        if (regex == null) {
            throw new IllegalArgumentException("The parameter 'regex' cannot be null.");
        }
        return new DefaultNamingSelector(instance -> Pattern.matches(regex, instance.getIp()));
    }
    
    /**
     * Create a metadata selector.
     *
     * @param metadata metadata that needs to be matched
     * @return metadata selector
     */
    public static NamingSelector newMetadataSelector(Map<String, String> metadata) {
        return newMetadataSelector(metadata, false);
    }
    
    /**
     * Create a metadata selector.
     *
     * @param metadata target metadata
     * @param isAny    true if any of the metadata needs to be matched, false if all the metadata need to be matched.
     * @return metadata selector
     */
    public static NamingSelector newMetadataSelector(Map<String, String> metadata, boolean isAny) {
        if (metadata == null) {
            throw new IllegalArgumentException("The parameter 'metadata' cannot be null.");
        }
        
        Predicate<Instance> filter = instance -> instance.getMetadata().size() >= metadata.size();
        
        for (Map.Entry<String, String> entry : metadata.entrySet()) {
            Predicate<Instance> nextFilter = instance -> {
                Map<String, String> map = instance.getMetadata();
                return Objects.equals(map.get(entry.getKey()), entry.getValue());
            };
            if (isAny) {
                filter = filter.or(nextFilter);
            } else {
                filter = filter.and(nextFilter);
            }
        }
        return new DefaultNamingSelector(filter);
    }
    
    public static String getUniqueClusterString(Collection<String> cluster) {
        TreeSet<String> treeSet = new TreeSet<>(cluster);
        return StringUtils.join(treeSet, ",");
    }
    
}
