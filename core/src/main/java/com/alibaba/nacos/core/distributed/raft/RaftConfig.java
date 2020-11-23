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

import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.consistency.Config;
import com.alibaba.nacos.consistency.cp.RequestProcessor4CP;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * raft config.
 *
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 */
@Component
@ConfigurationProperties(prefix = "nacos.core.protocol.raft")
public class RaftConfig implements Config<RequestProcessor4CP> {
    
    private static final long serialVersionUID = 9174789390266064002L;
    
    private Map<String, String> data = Collections.synchronizedMap(new HashMap<>());
    
    private String selfAddress;
    
    private Set<String> members = Collections.synchronizedSet(new HashSet<>());
    
    @Override
    public void setMembers(String self, Set<String> members) {
        this.selfAddress = self;
        this.members.clear();
        this.members.addAll(members);
    }
    
    @Override
    public String getSelfMember() {
        return selfAddress;
    }
    
    @Override
    public Set<String> getMembers() {
        return members;
    }
    
    @Override
    public void addMembers(Set<String> members) {
        this.members.addAll(members);
    }
    
    @Override
    public void removeMembers(Set<String> members) {
        this.members.removeAll(members);
    }
    
    public Map<String, String> getData() {
        return data;
    }
    
    public void setData(Map<String, String> data) {
        this.data = Collections.synchronizedMap(data);
    }
    
    @Override
    public void setVal(String key, String value) {
        data.put(key, value);
    }
    
    @Override
    public String getVal(String key) {
        return data.get(key);
    }
    
    @Override
    public String getValOfDefault(String key, String defaultVal) {
        return data.getOrDefault(key, defaultVal);
    }
    
    @Override
    public String toString() {
        try {
            return JacksonUtils.toJson(data);
        } catch (Exception e) {
            return String.valueOf(data);
        }
    }
}
