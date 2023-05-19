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

/**
 * The necessary metadata information for the node.
 *
 * @author <a href="mailto:liaochunyhm@live.com">liaochuntao</a>
 */
public class MemberMetaDataConstants {
    
    /**
     * Raft port，This parameter is dropped when RPC is used as a whole.
     */
    public static final String RAFT_PORT = "raftPort";
    
    public static final String SITE_KEY = "site";
    
    public static final String AD_WEIGHT = "adWeight";
    
    public static final String WEIGHT = "weight";
    
    public static final String LAST_REFRESH_TIME = "lastRefreshTime";
    
    public static final String VERSION = "version";
    
    public static final String SUPPORT_REMOTE_C_TYPE = "remoteConnectType";
    
    public static final String READY_TO_UPGRADE = "readyToUpgrade";
    
    public static final String[] BASIC_META_KEYS = new String[] {SITE_KEY, AD_WEIGHT, RAFT_PORT, WEIGHT, VERSION,
            READY_TO_UPGRADE};
}
