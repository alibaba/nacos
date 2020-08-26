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

package com.alibaba.nacos.naming.consistency.ephemeral.distro.newimpl.event;

import com.alibaba.nacos.common.notify.Event;
import com.alibaba.nacos.naming.consistency.ApplyAction;
import com.alibaba.nacos.naming.consistency.ephemeral.distro.newimpl.entity.DistroKey;

/**
 * Distro task retry event.
 *
 * @author xiweng.yy
 */
public class DistroTaskRetryEvent extends Event {
    
    private static final long serialVersionUID = 2039399053393791138L;
    
    private final DistroKey distroKey;
    
    private final ApplyAction action;
    
    public DistroTaskRetryEvent(DistroKey distroKey, ApplyAction action) {
        this.distroKey = distroKey;
        this.action = action;
    }
    
    public DistroKey getDistroKey() {
        return distroKey;
    }
    
    public ApplyAction getAction() {
        return action;
    }
}
