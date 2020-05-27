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

package com.alibaba.nacos.core.distributed.raft;

import com.alibaba.nacos.core.notify.SlowEvent;

import java.util.Collections;
import java.util.List;

/**
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 */
public class RaftEvent implements SlowEvent {

    private static final long serialVersionUID = -4304258594602886451L;

    private String groupId;

    private String leader = null;

    private Long term = null;

    private List<String> raftClusterInfo = Collections.emptyList();

    public static RaftEventBuilder builder() {
        return new RaftEventBuilder();
    }

    public String getGroupId() {
        return groupId;
    }

    public void setGroupId(String groupId) {
        this.groupId = groupId;
    }

    public String getLeader() {
        return leader;
    }

    public void setLeader(String leader) {
        this.leader = leader;
    }

    public Long getTerm() {
        return term;
    }

    public void setTerm(Long term) {
        this.term = term;
    }

    public List<String> getRaftClusterInfo() {
        return raftClusterInfo;
    }

    public void setRaftClusterInfo(List<String> raftClusterInfo) {
        this.raftClusterInfo = raftClusterInfo;
    }

    public static final class RaftEventBuilder {
        private String groupId;
        private String leader;
        private Long term = null;
        private List<String> raftClusterInfo = Collections.emptyList();

        private RaftEventBuilder() {
        }

        public RaftEventBuilder groupId(String groupId) {
            this.groupId = groupId;
            return this;
        }

        public RaftEventBuilder leader(String leader) {
            this.leader = leader;
            return this;
        }

        public RaftEventBuilder term(long term) {
            this.term = term;
            return this;
        }

        public RaftEventBuilder raftClusterInfo(List<String> raftClusterInfo) {
            this.raftClusterInfo = raftClusterInfo;
            return this;
        }

        public RaftEvent build() {
            RaftEvent raftEvent = new RaftEvent();
            raftEvent.setGroupId(groupId);
            raftEvent.setLeader(leader);
            raftEvent.setTerm(term);
            raftEvent.setRaftClusterInfo(raftClusterInfo);
            return raftEvent;
        }
    }

    @Override
    public String toString() {
        return "RaftEvent{" + "groupId='" + groupId + '\'' + ", leader='" + leader + '\''
                + ", term=" + term + ", raftClusterInfo=" + raftClusterInfo + '}';
    }
}
