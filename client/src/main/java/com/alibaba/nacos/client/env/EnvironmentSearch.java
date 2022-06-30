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

package com.alibaba.nacos.client.env;

import com.alibaba.nacos.common.utils.StringUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

class EnvironmentSearch {
    
    private static final int PATTERN_TOTAL = 2;
    
    private final List<NacosEnvironment> environmentList;
    
    private EnvironmentSearch(List<NacosEnvironment> environmentList) {
        this.environmentList = environmentList;
    }
    
    <T> T search(Function<NacosEnvironment, T> function) {
        return search(function, null);
    }
    
    <T> T search(Function<NacosEnvironment, T> function, T defaultValue) {
        for (NacosEnvironment nacosEnvironment : environmentList) {
            final T ret = function.apply(nacosEnvironment);
            if (ret != null) {
                return ret;
            }
        }
        return defaultValue;
    }
    
    private enum BuilderOpt {
        
        /**
         * call the Builder's build method.
         */
        BUILD,
        
        /**
         * call the Builder's first method.
         */
        FIRST,
        
        /**
         * call the Builder's last method.
         */
        LAST
        
    }
    
    static class Formatter {
        
        private static final String FINISH_OPT_DELIMITER = "@";
        
        private static final String ENV_ORDER_DELIMITER = ",";
        
        private static final Map<BuilderOpt, Function<Builder, EnvironmentSearch>> BUILDER_OPT_FUNCTIONS = new HashMap<>(
                4);
        
        static {
            
            BUILDER_OPT_FUNCTIONS.put(BuilderOpt.FIRST, Builder::first);
            BUILDER_OPT_FUNCTIONS.put(BuilderOpt.LAST, Builder::last);
            BUILDER_OPT_FUNCTIONS.put(BuilderOpt.BUILD, Builder::build);
        }
        
        private final String pattern;
        
        private final NacosEnvironment[] environments;
        
        private Formatter(String pattern, NacosEnvironment[] environments) {
            this.pattern = pattern;
            this.environments = environments;
        }
        
        static Formatter of(String pattern, NacosEnvironment... environments) {
            if (environments == null || environments.length == 0) {
                throw new IllegalArgumentException("NacosEnvironments must be at least one");
            }
            return new Formatter(pattern, environments);
        }
        
        EnvironmentSearch parse() {
            
            if (StringUtils.isBlank(pattern)) {
                return Builder.envs(environments).build();
            }
            
            BuilderOpt opt = BuilderOpt.BUILD;
            
            final String[] splitByFinishOpt = pattern.split(FINISH_OPT_DELIMITER);
            String orderStr = splitByFinishOpt[0];
            if (splitByFinishOpt.length == PATTERN_TOTAL) {
                try {
                    opt = BuilderOpt.valueOf(splitByFinishOpt[1].toUpperCase());
                } catch (Exception ignore) {
                    // ignore
                }
                
            }
            
            try {
                final EnvType[] envTypes = Arrays.stream(orderStr.split(ENV_ORDER_DELIMITER))
                        .map(order -> EnvType.valueOf(order.toUpperCase())).toArray(EnvType[]::new);
    
                return BUILDER_OPT_FUNCTIONS.getOrDefault(opt, Builder::build)
                        .apply(Builder.envs(environments).order(envTypes));
            } catch (Exception e) {
                throw new IllegalArgumentException("Nacos environment parse argument error," + pattern);
            }
          
        }
    }
    
    static class Builder {
        
        private final Map<EnvType, NacosEnvironment> environmentMap;
        
        private final List<EnvType> defaultOrder = Arrays.asList(EnvType.USER_CUSTOMIZABLE, EnvType.JVM_ARGS,
                EnvType.SYSTEM_ENV);
        
        private List<EnvType> order;
        
        private Builder(List<NacosEnvironment> environmentList) {
            this.environmentMap = environmentList.stream().collect(
                    Collectors.toMap(NacosEnvironment::getType, nacosEnvironment -> nacosEnvironment, (t, t2) -> t));
        }
        
        static Builder envs(NacosEnvironment... envs) {
            return new Builder(Arrays.asList(envs));
        }
        
        Builder order(EnvType... types) {
            this.order = Arrays.asList(types);
            return this;
        }
        
        EnvironmentSearch build() {
            
            List<EnvType> temp = order;
            if (temp == null) {
                temp = defaultOrder;
            }
            
            List<NacosEnvironment> environments = temp.stream().map(environmentMap::get).collect(Collectors.toList());
            return new EnvironmentSearch(environments);
        }
        
        EnvironmentSearch first() {
            if (order == null) {
                return build();
            }
            List<NacosEnvironment> environments = defaultOrder.stream().filter(envType -> !order.contains(envType))
                    .collect(() -> new ArrayList<>(order), List::add, List::addAll).stream().map(environmentMap::get)
                    .collect(Collectors.toList());
            
            return new EnvironmentSearch(environments);
        }
        
        EnvironmentSearch last() {
            if (order == null) {
                return build();
            }
            
            List<NacosEnvironment> environments = defaultOrder.stream().filter(envType -> !order.contains(envType))
                    .collect(() -> new ArrayList<>(order), (envTypes, envType) -> envTypes.add(0, envType),
                            List::addAll).stream().map(environmentMap::get).collect(Collectors.toList());
            
            return new EnvironmentSearch(environments);
        }
    }
}
