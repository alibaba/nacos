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

package com.alibaba.nacos.naming.selector.interpreter;

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.common.utils.StringUtils;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Expression interpreter for label selector.
 *
 * <p>For now it supports very limited set of syntax rules.
 *
 * @author nokrange
 */
public class ExpressionInterpreter {
    
    private static final Set<String> SUPPORTED_INNER_CONNCETORS = new HashSet<>();
    
    private static final Set<String> SUPPORTED_OUTER_CONNCETORS = new HashSet<>();
    
    private static final String CONSUMER_PREFIX = "CONSUMER.label.";
    
    private static final String PROVIDER_PREFIX = "PROVIDER.label.";
    
    private static final char CEQUAL = '=';
    
    private static final char CAND = '&';
    
    static {
        SUPPORTED_INNER_CONNCETORS.add(String.valueOf(CEQUAL));
        SUPPORTED_OUTER_CONNCETORS.add(String.valueOf(CAND));
    }
    
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