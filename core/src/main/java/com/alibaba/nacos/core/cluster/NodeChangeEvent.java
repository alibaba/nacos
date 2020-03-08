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

import com.alibaba.nacos.core.notify.Event;
import java.util.Collection;

/**
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 */
public class NodeChangeEvent implements Event {

    private static final long serialVersionUID = 7308126651076668976L;

    private Collection<Member> changeMembers;

    private Collection<Member> allMembers;

    private String kind;

    public static NodeChangeEventBuilder builder() {
        return new NodeChangeEventBuilder();
    }

    public Collection<Member> getChangeMembers() {
        return changeMembers;
    }

    public void setChangeMembers(Collection<Member> changeMembers) {
        this.changeMembers = changeMembers;
    }

    public Collection<Member> getAllMembers() {
        return allMembers;
    }

    public void setAllMembers(Collection<Member> allMembers) {
        this.allMembers = allMembers;
    }

    public String getKind() {
        return kind;
    }

    public void setKind(String kind) {
        this.kind = kind;
    }

    @Override
    public Class<? extends Event> eventType() {
        return NodeChangeEvent.class;
    }

    public static final class NodeChangeEventBuilder {
        private Collection<Member> changeMembers;
        private Collection<Member> allMembers;
        private String kind;

        private NodeChangeEventBuilder() {
        }

        public NodeChangeEventBuilder changeNodes(Collection<Member> changeMembers) {
            this.changeMembers = changeMembers;
            return this;
        }

        public NodeChangeEventBuilder allNodes(Collection<Member> allMembers) {
            this.allMembers = allMembers;
            return this;
        }

        public NodeChangeEventBuilder kind(String kind) {
            this.kind = kind;
            return this;
        }

        public NodeChangeEvent build() {
            NodeChangeEvent nodeChangeEvent = new NodeChangeEvent();
            nodeChangeEvent.setChangeMembers(changeMembers);
            nodeChangeEvent.setKind(kind);
            nodeChangeEvent.setAllMembers(allMembers);
            return nodeChangeEvent;
        }
    }
}
