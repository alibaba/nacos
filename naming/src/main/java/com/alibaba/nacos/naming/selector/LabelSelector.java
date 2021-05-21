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

package com.alibaba.nacos.naming.selector;

import com.alibaba.nacos.api.cmdb.pojo.PreservedEntityTypes;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.naming.pojo.Instance;
import com.alibaba.nacos.api.selector.ExpressionSelector;
import com.alibaba.nacos.api.selector.SelectorType;
import com.alibaba.nacos.cmdb.service.CmdbReader;
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.sys.utils.ApplicationUtils;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeInfo.Id;

import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * A selector to implement a so called same-label-prior rule for service discovery.
 * <h2>Backgroup</h2>
 * Consider service providers are deployed in two sites i.e. site A and site B, and consumers of this service provider
 * are also deployed in site A and site B. So the consumers may want to visit the service provider in current site, thus
 * consumers in site A visit service providers in site A and consumers in site B visit service providers in site B. This
 * is quite useful to reduce the transfer delay of RPC. This is called same-site-prior strategy.
 * <h2>Same Label Prior</h2>
 * The same-site-prior strategy covers many circumstances in large companies and we can abstract it to a higher level
 * strategy: same-label-prior.
 *
 * <p>So the idea is that presumed we have built a self-defined or integrated a third-party idc CMDB which stores all
 * the labels of all IPs. Then we can filter provider IPs by the consumer IP and we only return the providers who have
 * the same label values with consumer. We can define the labels we want to include in the comparison.
 *
 * <p>If no provider has the same label value with the consumer, we fall back to give all providers to the consumer.
 * Note that this fallback strategy may also be abstracted in future to introduce more kinds of behaviors.
 *
 * @author nkorange
 * @see CmdbReader
 * @since 0.7.0
 */
@JsonTypeInfo(use = Id.NAME, property = "type")
public class LabelSelector extends ExpressionSelector implements Selector {
    
    private static final long serialVersionUID = -7381912003505096093L;
    
    /**
     * The labels relevant to this the selector.
     *
     * @see com.alibaba.nacos.api.cmdb.pojo.Label
     */
    private Set<String> labels;
    
    private static final Set<String> SUPPORTED_INNER_CONNCETORS = new HashSet<>();
    
    private static final Set<String> SUPPORTED_OUTER_CONNCETORS = new HashSet<>();
    
    private static final String CONSUMER_PREFIX = "CONSUMER.label.";
    
    private static final String PROVIDER_PREFIX = "PROVIDER.label.";
    
    private static final char CEQUAL = '=';
    
    private static final char CAND = '&';
    
    static {
        SUPPORTED_INNER_CONNCETORS.add(String.valueOf(CEQUAL));
        SUPPORTED_OUTER_CONNCETORS.add(String.valueOf(CAND));
        JacksonUtils.registerSubtype(LabelSelector.class, SelectorType.label.name());
    }
    
    public Set<String> getLabels() {
        return labels;
    }
    
    public void setLabels(Set<String> labels) {
        this.labels = labels;
    }
    
    public LabelSelector() {
        super();
    }
    
    private CmdbReader getCmdbReader() {
        return ApplicationUtils.getBean(CmdbReader.class);
    }
    
    public static Set<String> parseExpression(String expression) throws NacosException {
        return ExpressionInterpreter.parseExpression(expression);
    }
    
    @Override
    public <T extends Instance> List<T> select(String consumer, List<T> providers) {
        if (labels.isEmpty()) {
            return providers;
        }
    
        List<T> instanceList = new ArrayList<>();
        for (T instance : providers) {
        
            boolean matched = true;
            for (String labelName : getLabels()) {
            
                String consumerLabelValue = getCmdbReader()
                        .queryLabel(consumer, PreservedEntityTypes.ip.name(), labelName);
            
                if (StringUtils.isNotBlank(consumerLabelValue) && !StringUtils.equals(consumerLabelValue,
                        getCmdbReader().queryLabel(instance.getIp(), PreservedEntityTypes.ip.name(), labelName))) {
                    matched = false;
                    break;
                }
            }
            if (matched) {
                instanceList.add(instance);
            }
        }
    
        if (instanceList.isEmpty()) {
            return providers;
        }
    
        return instanceList;
    }
    
    /**
     * Expression interpreter for label selector.
     *
     * <p>For now it supports very limited set of syntax rules.
     */
    public static class ExpressionInterpreter {
        
        /**
         * Parse the label expression.
         *
         * <p>Currently we support the very single type of expression:
         * <pre>
         *     consumer.labelA = provider.labelA & consumer.labelB = provider.labelB
         * </pre>
         * Later we will implement a interpreter to parse this expression in a standard LL parser way.
         *
         * @param expression the label expression to parse
         * @return collection of labels
         */
        public static Set<String> parseExpression(String expression) throws NacosException {
            
            if (StringUtils.isBlank(expression)) {
                return new HashSet<>();
            }
            
            expression = StringUtils.deleteWhitespace(expression);
            
            List<String> elements = getTerms(expression);
            Set<String> gotLabels = new HashSet<>();
            int index = 0;
            
            index = checkInnerSyntax(elements, index);
            
            if (index == -1) {
                throw new NacosException(NacosException.INVALID_PARAM, "parse expression failed!");
            }
            
            gotLabels.add(elements.get(index++).split(PROVIDER_PREFIX)[1]);
            
            while (index < elements.size()) {
                
                index = checkOuterSyntax(elements, index);
                
                if (index >= elements.size()) {
                    return gotLabels;
                }
                
                if (index == -1) {
                    throw new NacosException(NacosException.INVALID_PARAM, "parse expression failed!");
                }
                
                gotLabels.add(elements.get(index++).split(PROVIDER_PREFIX)[1]);
            }
            
            return gotLabels;
        }
        
        public static List<String> getTerms(String expression) {
            
            List<String> terms = new ArrayList<>();
            
            Set<Character> characters = new HashSet<>();
            characters.add(CEQUAL);
            characters.add(CAND);
            
            char[] chars = expression.toCharArray();
            
            int lastIndex = 0;
            for (int index = 0; index < chars.length; index++) {
                char ch = chars[index];
                if (characters.contains(ch)) {
                    terms.add(expression.substring(lastIndex, index));
                    terms.add(expression.substring(index, index + 1));
                    index++;
                    lastIndex = index;
                }
            }
            
            terms.add(expression.substring(lastIndex, chars.length));
            
            return terms;
        }
        
        private static int skipEmpty(List<String> elements, int start) {
            while (start < elements.size() && StringUtils.isBlank(elements.get(start))) {
                start++;
            }
            return start;
        }
        
        private static int checkOuterSyntax(List<String> elements, int start) {
            
            int index = start;
            
            index = skipEmpty(elements, index);
            if (index >= elements.size()) {
                return index;
            }
            
            if (!SUPPORTED_OUTER_CONNCETORS.contains(elements.get(index++))) {
                return -1;
            }
            
            return checkInnerSyntax(elements, index);
        }
        
        private static int checkInnerSyntax(List<String> elements, int start) {
            
            int index = start;
            
            index = skipEmpty(elements, index);
            if (index >= elements.size()) {
                return -1;
            }
            
            if (!elements.get(index).startsWith(CONSUMER_PREFIX)) {
                return -1;
            }
            
            final String labelConsumer = elements.get(index++).split(CONSUMER_PREFIX)[1];
            
            index = skipEmpty(elements, index);
            if (index >= elements.size()) {
                return -1;
            }
            
            if (!SUPPORTED_INNER_CONNCETORS.contains(elements.get(index++))) {
                return -1;
            }
            
            index = skipEmpty(elements, index);
            if (index >= elements.size()) {
                return -1;
            }
            
            if (!elements.get(index).startsWith(PROVIDER_PREFIX)) {
                return -1;
            }
            
            final String labelProvider = elements.get(index).split(PROVIDER_PREFIX)[1];
            
            if (!labelConsumer.equals(labelProvider)) {
                return -1;
            }
            
            return index;
        }
    }
}
