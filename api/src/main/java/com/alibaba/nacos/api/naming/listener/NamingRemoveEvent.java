package com.alibaba.nacos.api.naming.listener;

import com.alibaba.nacos.api.naming.pojo.Instance;

import java.util.List;

/**
 * Naming remove event
 *
 * @author zhixiang.yuan
 */
public class NamingRemoveEvent extends NamingBaseEvent {
    public NamingRemoveEvent(String serviceName, List<Instance> instances) {
        super(serviceName, instances);
    }

    public NamingRemoveEvent(String serviceName, String groupName, String clusters, List<Instance> instances) {
        super(serviceName, groupName, clusters, instances);
    }
}
