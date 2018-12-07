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
import com.alibaba.nacos.cmdb.service.CmdbReader;
import com.alibaba.nacos.naming.boot.SpringContext;
import com.alibaba.nacos.naming.core.IpAddress;
import org.apache.commons.lang3.StringUtils;
import org.springframework.context.ApplicationContext;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * A selector to implement a so called same-label-prior rule for service discovery.
 * <h2>Backgroup</h2>
 * Consider service providers are deployed in two sites i.e. site A and site B, and consumers
 * of this service provider are also deployed in site A and site B. So the consumers may want to
 * visit the service provider in current site, thus consumers in site A visit service providers
 * in site A and consumers in site B visit service providers in site B. This is quite useful to
 * reduce the transfer delay of RPC. This is called same-site-prior strategy.
 * <h2>Same Label Prior</h2>
 * The same-site-prior strategy covers many circumstances in large companies and we can abstract
 * it to a higher level strategy: same-label-prior.
 * <p>
 * So the idea is that presumed we have built a self-defined or integrated a third-party idc CMDB
 * which stores all the labels of all IPs. Then we can filter provider IPs by the consumer IP and
 * we only return the providers who have the same label values with consumer. We can define the
 * labels we want to include in the comparison.
 * <p>
 * If no provider has the same label value with the consumer, we fall back to give all providers
 * to the consumer. Note that this fallback strategy may also be abstracted in future to introduce
 * more kinds of behaviors.
 *
 * @author <a href="mailto:zpf.073@gmail.com">nkorange</a>
 * @see CmdbReader
 */
public class LabelSelector extends AbstractSelector {

    private CmdbReader cmdbReader;

    /**
     * The labels relevant to this the selector.
     *
     * @see com.alibaba.nacos.api.cmdb.pojo.Label
     */
    private Set<String> labels;

    /**
     * Label expression of this selector.
     * <p>
     * Currently we only support this very single type of expression:
     * <pre>
     *     consumer.labelA = provider.labelA & consumer.labelB = provider.labelB
     * </pre>
     * TODO what this expression means?
     */
    private String expression;

    private static final Set<String> SUPPORTED_INNER_CONNCETORS = new HashSet<>();

    private static final Set<String> SUPPORTED_OUTER_CONNCETORS = new HashSet<>();

    private static final String CONSUMER_PREFIX = "consumer.";

    private static final String PROVIDER_PREFIX = "provider.";

    static {
        SUPPORTED_INNER_CONNCETORS.add("=");
        SUPPORTED_OUTER_CONNCETORS.add("&");
    }

    public Set<String> getLabels() {
        return labels;
    }

    public void setLabels(Set<String> labels) {
        this.labels = labels;
    }

    public String getExpression() {
        return expression;
    }

    public void setExpression(String expression) {
        this.expression = expression;
    }

    public LabelSelector() {
        ApplicationContext context = SpringContext.getAppContext();
        cmdbReader = context.getBean(CmdbReader.class);
    }


    public static Set<String> parseExpression(String expression) {
        return ExpressionInterpreter.parseExpression(expression);
    }


    @Override
    public List<IpAddress> select(String consumer, List<IpAddress> providers) {
        List<IpAddress> ipAddressList = new ArrayList<>();
        for (IpAddress ipAddress : providers) {

            boolean matched = true;
            for (String labelName : getLabels()) {
                if (!StringUtils.equals(cmdbReader.queryLabel(consumer, PreservedEntityTypes.ip.name(), labelName),
                        cmdbReader.queryLabel(ipAddress.getIp(), PreservedEntityTypes.ip.name(), labelName))) {
                    matched = false;
                    break;
                }
            }
            if (matched) {
                ipAddressList.add(ipAddress);
            }
        }

        if (ipAddressList.isEmpty()) {
            return providers;
        }

        return ipAddressList;
    }

    /**
     * Expression interpreter for label selector.
     * <p>
     * For now it supports very limited set of syntax rules.
     */
    public static class ExpressionInterpreter {

        /**
         * Parse the label expression.
         * <p>
         * Currently we support the very single type of expression:
         * <pre>
         *     consumer.labelA = provider.labelA & consumer.labelB = provider.labelB
         * </pre>
         * Later we will implement a interpreter to parse this expression in a standard LL parser way.
         *
         * @param expression the label expression to parse
         * @return collection of labels
         */
        public static Set<String> parseExpression(String expression) {

            String[] elements = expression.split(" ");
            Set<String> gotLabels = new HashSet<>();
            int index = 0;

            index = checkInnerSyntax(elements, index);

            if (index == -1) {
                return new HashSet<>();
            }

            gotLabels.add(elements[index++].split(PROVIDER_PREFIX)[1]);

            while (index < elements.length) {

                index = checkOuterSyntax(elements, index);

                if (index >= elements.length) {
                    return gotLabels;
                }

                if (index == -1) {
                    return new HashSet<>();
                }

                gotLabels.add(elements[index++].split(PROVIDER_PREFIX)[1]);
            }

            return gotLabels;
        }

        private static int skipEmpty(String[] elements, int start) {
            while (start < elements.length && StringUtils.isBlank(elements[start])) {
                start++;
            }
            return start;
        }

        private static int checkOuterSyntax(String[] elements, int start) {

            int index = start;

            index = skipEmpty(elements, index);
            if (index >= elements.length) {
                return index;
            }

            if (!SUPPORTED_OUTER_CONNCETORS.contains(elements[index++])) {
                return -1;
            }

            return checkInnerSyntax(elements, index);
        }

        private static int checkInnerSyntax(String[] elements, int start) {

            int index = start;

            index = skipEmpty(elements, index);
            if (index >= elements.length) {
                return -1;
            }

            if (!elements[index].startsWith(CONSUMER_PREFIX)) {
                return -1;
            }

            String labelConsumer = elements[index++].split(CONSUMER_PREFIX)[1];

            index = skipEmpty(elements, index);
            if (index >= elements.length) {
                return -1;
            }

            if (!SUPPORTED_INNER_CONNCETORS.contains(elements[index++])) {
                return -1;
            }

            index = skipEmpty(elements, index);
            if (index >= elements.length) {
                return -1;
            }

            if (!elements[index].startsWith(PROVIDER_PREFIX)) {
                return -1;
            }

            String labelProvider = elements[index].split(PROVIDER_PREFIX)[1];

            if (!labelConsumer.equals(labelProvider)) {
                return -1;
            }

            return index;
        }
    }

    public static void main(String[] args) {

        String expression = "consumer.A = provider.A & consumer.B = provider.B & consumer.B = provider.B";
        System.out.println(parseExpression(expression));
    }
}
