package com.alibaba.nacos.client.naming.loadbalancer;


import com.alibaba.nacos.api.naming.listener.Event;
import com.alibaba.nacos.api.naming.listener.NamingEvent;
import com.alibaba.nacos.api.naming.pojo.Instance;
import com.alibaba.nacos.api.naming.pojo.ServiceInfo;
import com.alibaba.nacos.client.naming.utils.Chooser;
import com.alibaba.nacos.client.naming.utils.LogUtils;
import com.alibaba.nacos.client.naming.utils.Pair;

import java.util.ArrayList;
import java.util.List;


/**
 * Poll-Without-Weight Load-Balancer Implementation
 * @author XCXCXCXCX
 */
public class PollLoadBalancer extends BaseLoadBalancer{

    @Override
    public Instance choose(ServiceInfo serviceInfo) {
        return Balancer.getHostByPoll(serviceInfo);
    }

    /**
     * callback event
     * update cache when instances changed
     *
     * @param event
     */
    @Override
    public void onEvent(Event event) {
        if(event instanceof NamingEvent){
            String serviceName = ((NamingEvent) event).getServiceName();
            List<Instance> hosts = ((NamingEvent) event).getInstances();
            Chooser<String, Instance> vipChooser = Balancer.pollCacheChooser.get(serviceName);
            Chooser<String, Instance> tmpChooser = new Chooser<String, Instance>("load_balance_poll");
            LogUtils.LOG.debug("new Chooser");

            List<Pair<Instance>> hostsWithoutWeight = new ArrayList<Pair<Instance>>();
            for (Instance host : hosts) {
                if (host.isHealthy()) {
                    hostsWithoutWeight.add(new Pair<Instance>(host, host.getWeight()));
                }
            }
            LogUtils.LOG.debug("for (Host host : hosts)");
            tmpChooser.refresh(hostsWithoutWeight);
            LogUtils.LOG.debug("vipChooser.refresh");
            if (vipChooser == null || !tmpChooser.getRef().equals(vipChooser.getRef())) {
                Balancer.pollCacheChooser.put(serviceName, tmpChooser);
            }
        }
    }
}
