/*
 * Copyright 1999-2025 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.console.cluster;

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.common.notify.Event;
import com.alibaba.nacos.common.notify.NotifyCenter;
import com.alibaba.nacos.console.handler.impl.remote.EnabledRemoteHandler;
import com.alibaba.nacos.core.cluster.Member;
import com.alibaba.nacos.core.cluster.MemberLookup;
import com.alibaba.nacos.core.cluster.MembersChangeEvent;
import com.alibaba.nacos.core.cluster.NacosMemberManager;
import com.alibaba.nacos.core.cluster.lookup.LookupFactory;
import com.alibaba.nacos.core.utils.Loggers;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.util.Collection;
import java.util.HashSet;
import java.util.concurrent.ConcurrentSkipListMap;

/**
 * Nacos remote server members manager. Only working on console mode to keep and update the remote server members.
 *
 * @author xiweng.yy
 */
@Service
@EnabledRemoteHandler
public class RemoteServerMemberManager implements NacosMemberManager {
    
    /**
     * Nacos remote servers cluster node list.
     */
    private volatile ConcurrentSkipListMap<String, Member> serverList;
    
    /**
     * Addressing pattern instances.
     */
    private MemberLookup lookup;
    
    public RemoteServerMemberManager() {
        this.serverList = new ConcurrentSkipListMap<>();
    }
    
    @PostConstruct
    public void init() throws NacosException {
        initAndStartLookup();
    }
    
    private void initAndStartLookup() throws NacosException {
        this.lookup = LookupFactory.createLookUp();
        this.lookup.injectMemberManager(this);
        this.lookup.start();
    }
    
    @Override
    public synchronized boolean memberChange(Collection<Member> members) {
        ConcurrentSkipListMap<String, Member> newServerList = new ConcurrentSkipListMap<>();
        for (Member each : members) {
            newServerList.put(each.getAddress(), each);
        }
        Loggers.CLUSTER.info("[serverlist] nacos remote server members changed to : {}", newServerList);
        this.serverList = newServerList;
        Event event = MembersChangeEvent.builder().members(members).build();
        NotifyCenter.publishEvent(event);
        return true;
    }
    
    @Override
    public Collection<Member> allMembers() {
        return new HashSet<>(serverList.values());
    }
}
