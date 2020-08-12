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

package com.alibaba.nacos.core.distributed.raft.utils;

/**
 * constant.
 *
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 */
@SuppressWarnings("PMD.ClassNamingShouldBeCamelRule")
public class JRaftConstants {
    
    public static final String JRAFT_EXTEND_INFO_KEY = JRaftLogOperation.class.getCanonicalName();
    
    public static final String GROUP_ID = "groupId";
    
    public static final String COMMAND_NAME = "command";
    
    public static final String COMMAND_VALUE = "value";
    
    public static final String TRANSFER_LEADER = "transferLeader";
    
    public static final String RESET_RAFT_CLUSTER = "restRaftCluster";
    
    public static final String DO_SNAPSHOT = "doSnapshot";
    
    public static final String REMOVE_PEER = "removePeer";
    
    public static final String REMOVE_PEERS = "removePeers";
    
    public static final String CHANGE_PEERS = "changePeers";
    
}
