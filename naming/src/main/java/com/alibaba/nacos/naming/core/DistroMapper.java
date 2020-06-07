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
package com.alibaba.nacos.naming.core;

import com.alibaba.nacos.core.cluster.MemberChangeListener;
import com.alibaba.nacos.core.cluster.MemberChangeEvent;
import com.alibaba.nacos.core.cluster.MemberUtils;
import com.alibaba.nacos.core.cluster.ServerMemberManager;
import com.alibaba.nacos.core.notify.NotifyCenter;
import com.alibaba.nacos.core.utils.ApplicationUtils;
import com.alibaba.nacos.naming.misc.Loggers;
import com.alibaba.nacos.naming.misc.SwitchDomain;
import org.apache.commons.collections.CollectionUtils;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * @author nkorange
 */
@Component("distroMapper")
public class DistroMapper implements MemberChangeListener {

    /**
     * List of service nodes, you must ensure that the order of healthyList is the same for all nodes
     */
    private volatile List<String> healthyList = new ArrayList<>();

    private final SwitchDomain switchDomain;

    private final ServerMemberManager memberManager;

    public DistroMapper(ServerMemberManager memberManager, SwitchDomain switchDomain) {
        this.memberManager = memberManager;
        this.switchDomain = switchDomain;
    }

    public List<String> getHealthyList() {
        return healthyList;
    }

    /**
     * init server list
     */
    @PostConstruct
    public void init() {
        NotifyCenter.registerSubscribe(this);
        this.healthyList = MemberUtils.simpleMembers(memberManager.allMembers());
    }

    public boolean responsible(Cluster cluster, Instance instance) {
        return switchDomain.isHealthCheckEnabled(cluster.getServiceName())
            && !cluster.getHealthCheckTask().isCancelled()
            && responsible(cluster.getServiceName())
            && cluster.contains(instance);
    }

    public boolean responsible(String serviceName) {
        final List<String> servers = healthyList;

        if (!switchDomain.isDistroEnabled() || ApplicationUtils.getStandaloneMode()) {
            return true;
        }

        if (CollectionUtils.isEmpty(servers)) {
            // means distro config is not ready yet
            return false;
        }

        int index = servers.indexOf(ApplicationUtils.getLocalAddress());
        int lastIndex = servers.lastIndexOf(ApplicationUtils.getLocalAddress());
        if (lastIndex < 0 || index < 0) {
            return true;
        }

        int target = distroHash(serviceName) % servers.size();
        return target >= index && target <= lastIndex;
    }

    public String mapSrv(String serviceName) {
        final List<String> servers = healthyList;

        if (CollectionUtils.isEmpty(servers) || !switchDomain.isDistroEnabled()) {
            return ApplicationUtils.getLocalAddress();
        }

        try {
            int index = distroHash(serviceName) % servers.size();
            return servers.get(index);
        } catch (Throwable e) {
            Loggers.SRV_LOG.warn("[NACOS-DISTRO] distro mapper failed, return localhost: " + ApplicationUtils.getLocalAddress(), e);
            return ApplicationUtils.getLocalAddress();
        }
    }

    public int distroHash(String serviceName) {
        return Math.abs(serviceName.hashCode() % Integer.MAX_VALUE);
    }

    @Override
    public void onEvent(MemberChangeEvent event) {
        // Here, the node list must be sorted to ensure that all nacos-server's
        // node list is in the same order
        List<String> list = MemberUtils.simpleMembers(event.getMembers());
        Collections.sort(list);
        Collection<String> old = healthyList;
        healthyList = Collections.unmodifiableList(list);
        Loggers.SRV_LOG.info("[NACOS-DISTRO] healthy server list changed, old: {}, new: {}", old, healthyList);
    }

    @Override
    public boolean ignoreExpireEvent() {
        return true;
    }
}
