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

import com.alibaba.nacos.common.notify.NotifyCenter;
import com.alibaba.nacos.core.cluster.MemberChangeListener;
import com.alibaba.nacos.core.cluster.MemberUtil;
import com.alibaba.nacos.core.cluster.MembersChangeEvent;
import com.alibaba.nacos.core.cluster.NodeState;
import com.alibaba.nacos.core.cluster.ServerMemberManager;
import com.alibaba.nacos.naming.misc.Loggers;
import com.alibaba.nacos.naming.misc.SwitchDomain;
import com.alibaba.nacos.sys.env.EnvUtil;
import org.apache.commons.collections.CollectionUtils;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * Distro mapper, judge which server response input service.
 *
 * @author nkorange
 */
@Component("distroMapper")
public class DistroMapper extends MemberChangeListener {
    
    /**
     * List of service nodes, you must ensure that the order of healthyList is the same for all nodes.
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
     * init server list.
     */
    @PostConstruct
    public void init() {
        NotifyCenter.registerSubscriber(this);
        this.healthyList = MemberUtil.simpleMembers(memberManager.allMembers());
    }
    
    public boolean responsible(Cluster cluster, Instance instance) {
        return switchDomain.isHealthCheckEnabled(cluster.getServiceName()) && !cluster.getHealthCheckTask()
                .isCancelled() && responsible(cluster.getServiceName()) && cluster.contains(instance);
    }
    
    /**
     * Judge whether current server is responsible for input tag.
     *
     * @param responsibleTag responsible tag, serviceName for v1 and ip:port for v2
     * @return true if input service is response, otherwise false
     */
    public boolean responsible(String responsibleTag) {
        final List<String> servers = healthyList;
        
        if (!switchDomain.isDistroEnabled() || EnvUtil.getStandaloneMode()) {
            return true;
        }
        
        if (CollectionUtils.isEmpty(servers)) {
            // means distro config is not ready yet
            return false;
        }
        
        int index = servers.indexOf(EnvUtil.getLocalAddress());
        int lastIndex = servers.lastIndexOf(EnvUtil.getLocalAddress());
        if (lastIndex < 0 || index < 0) {
            return true;
        }
        
        int target = distroHash(responsibleTag) % servers.size();
        return target >= index && target <= lastIndex;
    }
    
    /**
     * Calculate which other server response input tag.
     *
     * @param responsibleTag responsible tag, serviceName for v1 and ip:port for v2
     * @return server which response input service
     */
    public String mapSrv(String responsibleTag) {
        final List<String> servers = healthyList;
        
        if (CollectionUtils.isEmpty(servers) || !switchDomain.isDistroEnabled()) {
            return EnvUtil.getLocalAddress();
        }
        
        try {
            int index = distroHash(responsibleTag) % servers.size();
            return servers.get(index);
        } catch (Throwable e) {
            Loggers.SRV_LOG
                    .warn("[NACOS-DISTRO] distro mapper failed, return localhost: " + EnvUtil.getLocalAddress(), e);
            return EnvUtil.getLocalAddress();
        }
    }
    
    private int distroHash(String responsibleTag) {
        return Math.abs(responsibleTag.hashCode() % Integer.MAX_VALUE);
    }
    
    @Override
    public void onEvent(MembersChangeEvent event) {
        // Here, the node list must be sorted to ensure that all nacos-server's
        // node list is in the same order
        List<String> list = MemberUtil.simpleMembers(MemberUtil.selectTargetMembers(event.getMembers(),
                member -> NodeState.UP.equals(member.getState()) || NodeState.SUSPICIOUS.equals(member.getState())));
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
