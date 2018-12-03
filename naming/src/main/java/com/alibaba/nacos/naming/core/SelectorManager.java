package com.alibaba.nacos.naming.core;

import com.alibaba.nacos.api.cmdb.pojo.PreservedEntityTypes;
import com.alibaba.nacos.cmdb.service.CmdbReader;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author <a href="mailto:zpf.073@gmail.com">nkorange</a>
 */
@Component
public class SelectorManager {

    private Map<String, Selector> selectorMap = new ConcurrentHashMap<>();

    @Autowired
    private CmdbReader cmdbReader;

    public void addSelector(String selectorName, Set<String> labels) {
        Selector selector = new Selector();
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

        List<IpAddress> ipAddressList = new ArrayList<>();
        for (IpAddress ipAddress : providers) {

            boolean matched = true;
            for (String labelName : selector.getLabels()) {
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
