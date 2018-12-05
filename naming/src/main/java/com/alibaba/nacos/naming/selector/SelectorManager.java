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
import com.alibaba.nacos.naming.core.IpAddress;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Selector manager which stores and manages all existing selectors.
 *
 * @author <a href="mailto:zpf.073@gmail.com">nkorange</a>
 * @see Selector
 */
@Component
public class SelectorManager {

    private Map<String, Selector> selectorMap = new ConcurrentHashMap<>();

    @Autowired
    private CmdbReader cmdbReader;

    public void addLabelSelector(String selectorName, Set<String> labels) {
        LabelSelector selector = new LabelSelector();
        selector.setName(selectorName);
        selector.setLabels(labels);
        addSelector(selector);
    }

    public void addSelector(Selector selector) {
        selectorMap.put(selector.getName(), selector);
    }

    public void removeSelector(String selectorName) {
        selectorMap.remove(selectorName);
    }

    public Selector getSelector(String selectorName) {
        return selectorMap.get(selectorName);
    }

    public Set<String> getSelectorNames() {
        return selectorMap.keySet();
    }

    public List<IpAddress> filter(String selectorName, String consumer, List<IpAddress> providers) {

        if (!selectorMap.containsKey(selectorName)) {
            return providers;
        }

        Selector selector = selectorMap.get(selectorName);

        return selector.select(consumer, providers);
    }

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
    public class LabelSelector extends Selector {

        /**
         * The labels included in the select strategy.
         *
         * @see com.alibaba.nacos.api.cmdb.pojo.Label
         */
        private Set<String> labels;

        public Set<String> getLabels() {
            return labels;
        }

        public void setLabels(Set<String> labels) {
            this.labels = labels;
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
    }

}
