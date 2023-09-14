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
import com.alibaba.nacos.api.naming.selector.NamingContext;
import com.alibaba.nacos.api.naming.selector.NamingResult;
import com.alibaba.nacos.api.naming.selector.NamingSelector;
import com.alibaba.nacos.common.utils.CollectionUtils;
import com.alibaba.nacos.common.utils.StringUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
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
    public static NamingSelector newIPSelector(String regex) {
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
    
    /**
     * Create a custom selector.
     *
     * @param filter Filter condition
     * @return custom selector
     */
    public static NamingSelector newCustomSelector(Predicate<Instance> filter) {
        if (filter == null) {
            throw new IllegalArgumentException("The parameter 'filter' cannot be null.");
        }
        return new DefaultNamingSelector(filter);
    }
    
    /**
     * Combine multiple selectors and take the intersection of the selected results.
     *
     * @param selectors Combined selectors
     * @return intersection selector
     */
    public static NamingSelector newInterSelector(NamingSelector... selectors) {
        if (selectors == null) {
            throw new IllegalArgumentException("The parameter 'selectors' cannot be null.");
        }
        return new NamingSelector() {
            @Override
            public NamingResult select(NamingContext context) {
                Set<Instance> set = new HashSet<>(context.getInstances());
                for (NamingSelector selector : selectors) {
                    NamingResult result = selector.select(context);
                    set.retainAll(result.getResult());
                }
                List<Instance> list = new ArrayList<>(set);
                return () -> list;
            }
        };
    }
    
    /**
     * Combine multiple selectors and take the union of the selected results.
     *
     * @param selectors Combined selectors
     * @return union selector
     */
    public static NamingSelector newUnionSelector(NamingSelector... selectors) {
        if (selectors == null) {
            throw new IllegalArgumentException("The parameter 'selectors' cannot be null.");
        }
        return new NamingSelector() {
            @Override
            public NamingResult select(NamingContext context) {
                Set<Instance> set = new HashSet<>();
                for (NamingSelector selector : selectors) {
                    NamingResult result = selector.select(context);
                    set.addAll(result.getResult());
                }
                List<Instance> list = new ArrayList<>(set);
                return () -> list;
            }
        };
    }
    
    /**
     * Combine multiple selectors and take the difference set of selected results.
     *
     * @param original original selector
     * @param exclude  excluded selector
     * @return difference selector
     */
    public static NamingSelector newDiffSelector(NamingSelector original, NamingSelector... exclude) {
        if (original == null) {
            throw new IllegalArgumentException("The parameter 'original' cannot be null.");
        }
        if (exclude == null) {
            throw new IllegalArgumentException("The parameter 'exclude' cannot be null.");
        }
        return new NamingSelector() {
            @Override
            public NamingResult select(NamingContext context) {
                Set<Instance> set = new HashSet<>(original.select(context).getResult());
                for (NamingSelector selector : exclude) {
                    NamingResult result = selector.select(context);
                    result.getResult().forEach(set::remove);
                }
                List<Instance> list = new ArrayList<>(set);
                return () -> list;
            }
        };
    }
    
    
    public static String getUniqueClusterString(Collection<String> cluster) {
        TreeSet<String> treeSet = new TreeSet<>(cluster);
        return StringUtils.join(treeSet, ",");
    }
    
}
