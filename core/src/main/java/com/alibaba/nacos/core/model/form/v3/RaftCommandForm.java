/*
 * Copyright 1999-2023 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.core.model.form.v3;

import com.alibaba.nacos.api.exception.api.NacosApiException;
import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.core.distributed.raft.utils.JRaftConstants;
import com.alibaba.nacos.api.model.NacosForm;

import java.util.HashMap;
import java.util.Map;

/**
 * Raft command form.
 *
 * @author yunye
 * @since 3.0.0-beta
 */
public class RaftCommandForm implements NacosForm {
    
    /**
     * Target raft group id, If null or empty, will do command for all group.
     */
    private String groupId;
    
    /**
     * Raft command. Valid values:  "transferLeader", "doSnapshot", "resetRaftCluster", "removePeer".
     */
    private String command;
    
    /**
     * Command value. The format: {raft_server_ip}:{raft_port}[,{raft_server_ip}:{raft_port}]
     */
    private String value;
    
    @Override
    public void validate() throws NacosApiException {
    }
    
    public String getGroupId() {
        return groupId;
    }
    
    public void setGroupId(String groupId) {
        this.groupId = groupId;
    }
    
    public String getCommand() {
        return command;
    }
    
    public void setCommand(String command) {
        this.command = command;
    }
    
    public String getValue() {
        return value;
    }
    
    public void setValue(String value) {
        this.value = value;
    }
    
    /**
     * convert to raft execute arguments.
     *
     * @return args map.
     */
    public Map<String, String> toMap() {
        Map<String, String> map = new HashMap<>(4);
        if (StringUtils.isNotBlank(groupId)) {
            map.put(JRaftConstants.GROUP_ID, groupId);
        }
        map.put(JRaftConstants.COMMAND_NAME, command);
        map.put(JRaftConstants.COMMAND_VALUE, value);
        return map;
    }
}
