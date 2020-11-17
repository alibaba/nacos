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

import com.alibaba.nacos.common.notify.Event;

/**
 * The RAFT protocol runs an exception event. If this event is published, it means that the current raft Group cannot
 * continue to run normally
 *
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 */
public class RaftErrorEvent extends Event {
    
    private static final long serialVersionUID = 3016514657754158167L;
    
    private String groupName;
    
    public String getGroupName() {
        return groupName;
    }
    
    public void setGroupName(String groupName) {
        this.groupName = groupName;
    }
}
