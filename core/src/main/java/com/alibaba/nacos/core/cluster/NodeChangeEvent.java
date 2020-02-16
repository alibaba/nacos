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

import com.alibaba.nacos.consistency.cluster.Node;
import com.alibaba.nacos.core.notify.Event;

import java.util.Collection;

/**
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 */
public class NodeChangeEvent implements Event {

    private static final long serialVersionUID = 7308126651076668976L;

    private Collection<Node> changeNodes;

    private Collection<Node> allNodes;

    private String kind;

    public Collection<Node> getChangeNodes() {
        return changeNodes;
    }

    public void setChangeNodes(Collection<Node> changeNodes) {
        this.changeNodes = changeNodes;
    }

    public Collection<Node> getAllNodes() {
        return allNodes;
    }

    public void setAllNodes(Collection<Node> allNodes) {
        this.allNodes = allNodes;
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

    public static NodeChangeEventBuilder builder() {
        return new NodeChangeEventBuilder();
    }

    public static final class NodeChangeEventBuilder {
        private Collection<Node> changeNodes;
        private Collection<Node> allNodes;
        private String kind;

        private NodeChangeEventBuilder() {
        }

        public NodeChangeEventBuilder changeNodes(Collection<Node> changeNodes) {
            this.changeNodes = changeNodes;
            return this;
        }

        public NodeChangeEventBuilder allNodes(Collection<Node> allNodes) {
            this.allNodes = allNodes;
            return this;
        }

        public NodeChangeEventBuilder kind(String kind) {
            this.kind = kind;
            return this;
        }

        public NodeChangeEvent build() {
            NodeChangeEvent nodeChangeEvent = new NodeChangeEvent();
            nodeChangeEvent.setChangeNodes(changeNodes);
            nodeChangeEvent.setKind(kind);
            nodeChangeEvent.setAllNodes(allNodes);
            return nodeChangeEvent;
        }
    }
}
