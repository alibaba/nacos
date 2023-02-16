/*
 *  Copyright 1999-2021 Alibaba Group Holding Ltd.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */

package com.alibaba.nacos.naming.selector;

import com.alibaba.nacos.api.cmdb.pojo.Entity;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.naming.pojo.Instance;
import com.alibaba.nacos.api.selector.AbstractCmdbSelector;
import com.alibaba.nacos.api.selector.context.CmdbContext;
import com.alibaba.nacos.common.utils.CollectionUtils;
import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.naming.selector.interpreter.ExpressionInterpreter;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The {@link LabelSelector} will return the instances labels in {@link #labels} and providers' label value is same with consumer.
 * If none matched, then will return all providers instead of.
 *
 * @author chenglu
 * @date 2021-07-16 16:26
 */
public class LabelSelector<T extends Instance> extends AbstractCmdbSelector<T> {
    
    private static final String TYPE = "label";
    
    /**
     * {@link Entity} labels key.
     */
    private Set<String> labels;
    
    public Set<String> getLabels() {
        return labels;
    }
    
    public void setLabels(Set<String> labels) {
        this.labels = labels;
    }
    
    @Override
    protected List<T> doSelect(CmdbContext<T> context) {
        if (CollectionUtils.isEmpty(labels)) {
            return context.getProviders()
                    .stream()
                    .map(CmdbContext.CmdbInstance::getInstance)
                    .collect(Collectors.toList());
        }
        CmdbContext.CmdbInstance<T> consumer = context.getConsumer();
        Map<String, String> consumerLabels = Optional.ofNullable(consumer.getEntity())
                .map(Entity::getLabels)
                .orElse(Collections.emptyMap());
        
        // filter the instance if consumer and providers' label values equals.
        List<T> result = context.getProviders()
                .stream()
                .filter(ci -> {
                    Entity providerEntity = ci.getEntity();
                    if (Objects.isNull(providerEntity)) {
                        return false;
                    }
                    Map<String, String> providerLabels = Optional.ofNullable(ci.getEntity().getLabels())
                            .orElse(Collections.emptyMap());
                    return labels.stream()
                            .allMatch(label -> {
                                String consumerLabelValue = consumerLabels.get(label);
                                if (StringUtils.isBlank(consumerLabelValue)) {
                                    return false;
                                }
                                return Objects.equals(consumerLabelValue, providerLabels.get(label));
                            });
                })
                .map(CmdbContext.CmdbInstance::getInstance)
                .collect(Collectors.toList());
        
        // if none match, then return all providers.
        if (CollectionUtils.isEmpty(result)) {
            return context.getProviders()
                    .stream()
                    .map(CmdbContext.CmdbInstance::getInstance)
                    .collect(Collectors.toList());
        }
        return result;
    }
    
    @Override
    protected void doParse(String expression) throws NacosException {
        this.labels = ExpressionInterpreter.parseExpression(expression);
    }
    
    @Override
    public String getType() {
        return TYPE;
    }
}
