/*
 * Copyright 1999-2026 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.client.ai.remote.capability;

import com.alibaba.nacos.api.ability.constant.AbilityStatus;

/**
 * One AI instance's A2A routing choice, independent of refreshed capability caches.
 *
 * @author Nacos
 */
public final class A2aModeSelector {
    
    public enum Mode {
        UNDECIDED, LEGACY, RAD
    }
    
    private Mode mode = Mode.UNDECIDED;
    
    /**
     * Resolve once using evidence for the selected binding; errors must not call this method.
     * Legacy A2A evidence does not assert that native RAD is unsupported.
     *
     * @param rad capability of the selected RAD binding
     * @param legacyA2a capability of the selected legacy gRPC binding
     * @return stable routing choice, or undecided when evidence is insufficient
     */
    public synchronized Mode select(AbilityStatus rad, AbilityStatus legacyA2a) {
        if (mode == Mode.UNDECIDED) {
            if (rad == AbilityStatus.SUPPORTED) {
                mode = Mode.RAD;
            } else if (rad == AbilityStatus.NOT_SUPPORTED
                || legacyA2a == AbilityStatus.SUPPORTED) {
                mode = Mode.LEGACY;
            }
        }
        return mode;
    }
}
