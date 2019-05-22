package com.alibaba.nacos.naming.pojo;

import com.alibaba.fastjson.JSON;


/**
 * @author: universefeeler
 * @Date: 2019/05/19 15:51
 * @Description:
 */
public class ClusterStateView{

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
        return JSON.toJSONString(this);
    }
}
