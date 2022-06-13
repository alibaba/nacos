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

package com.alibaba.nacos.core.cluster;

import com.alibaba.nacos.common.notify.Event;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;

/**
 * Publish this event when the node list changes，All interested in the node list change event can listen to this event.
 *
 * <ul>
 *     <li>{@link com.alibaba.nacos.core.distributed.ProtocolManager}</li>
 *     <li>{@link com.alibaba.nacos.naming.core.DistroMapper}</li>
 *     <li>{@link com.alibaba.nacos.naming.consistency.persistent.raft.RaftPeerSet}</li>
 * </ul>
 *
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 */
public class MembersChangeEvent extends Event {
    
    private static final long serialVersionUID = 7308126651076668976L;
    
    private final Collection<Member> members;
    
    private final Collection<Member> triggers;
    
    private MembersChangeEvent(Collection<Member> members, Collection<Member> triggers) {
        this.members = members;
        this.triggers = new HashSet<>();
        if (triggers != null) {
            this.triggers.addAll(triggers);
        }
    }
    
    public static MemberChangeEventBuilder builder() {
        return new MemberChangeEventBuilder();
    }
    
    public Collection<Member> getMembers() {
        return members;
    }
    
    public boolean hasTriggers() {
        return !triggers.isEmpty();
    }
    
    public Collection<Member> getTriggers() {
        return triggers;
    }
    
    @Override
    public String toString() {
        return "MembersChangeEvent{" + "members=" + members + ", triggers=" + triggers + ", no=" + sequence() + '}';
    }
    
    public static final class MemberChangeEventBuilder {
        
        private Collection<Member> allMembers;
        
        private Collection<Member> triggers;
    
        private MemberChangeEventBuilder() {
        }
        
        public MemberChangeEventBuilder members(Collection<Member> allMembers) {
            this.allMembers = allMembers;
            return this;
        }
        
        public MemberChangeEventBuilder triggers(Collection<Member> triggers) {
            this.triggers = triggers;
            return this;
        }
        
        public MemberChangeEventBuilder trigger(Member trigger) {
            this.triggers = Collections.singleton(trigger);
            return this;
        }
    
        /**
         * build MemberChangeEvent.
         *
         * @return {@link MembersChangeEvent}
         */
        public MembersChangeEvent build() {
            return new MembersChangeEvent(allMembers, triggers);
        }
    }
}
