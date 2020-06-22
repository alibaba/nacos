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

package com.alibaba.nacos.naming.pojo;

/**
 * Cluster state view.
 *
 * @author: universefeeler
 */
public class ClusterStateView {
    
    private String nodeIp;
    
    private String nodeState;
    
    private long clusterTerm;
    
    private long leaderDueMs;
    
    private String voteFor;
    
    private long heartbeatDueMs;
    
    public long getLeaderDueMs() {
        return leaderDueMs;
    }
    
    public void setLeaderDueMs(long leaderDueMs) {
        this.leaderDueMs = leaderDueMs;
    }
    
    public long getHeartbeatDueMs() {
        return heartbeatDueMs;
    }
    
    public void setHeartbeatDueMs(long heartbeatDueMs) {
        this.heartbeatDueMs = heartbeatDueMs;
    }
    
    public String getVoteFor() {
        return voteFor;
    }
    
    public void setVoteFor(String voteFor) {
        this.voteFor = voteFor;
    }
    
    public String getNodeIp() {
        return nodeIp;
    }
    
    public void setNodeIp(String nodeIp) {
        this.nodeIp = nodeIp;
    }
    
    public String getNodeState() {
        return nodeState;
    }
    
    public void setNodeState(String nodeState) {
        this.nodeState = nodeState;
    }
    
    public long getClusterTerm() {
        return clusterTerm;
    }
    
    public void setClusterTerm(long clusterTerm) {
        this.clusterTerm = clusterTerm;
    }
    
    @Override
    public String toString() {
        return "ClusterStateView{" + "nodeIp='" + nodeIp + '\'' + ", nodeState='" + nodeState + '\'' + ", clusterTerm="
                + clusterTerm + ", leaderDueMs=" + leaderDueMs + ", voteFor='" + voteFor + '\'' + ", heartbeatDueMs="
                + heartbeatDueMs + '}';
    }
}
