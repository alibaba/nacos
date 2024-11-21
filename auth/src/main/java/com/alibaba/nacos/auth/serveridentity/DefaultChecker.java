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

package com.alibaba.nacos.auth.serveridentity;

import com.alibaba.nacos.auth.annotation.Secured;
import com.alibaba.nacos.auth.config.AuthConfigs;

/**
 * Nacos default server identity checker.
 *
 * @author xiweng.yy
 */
public class DefaultChecker implements ServerIdentityChecker {
    
    private AuthConfigs authConfigs;
    
    @Override
    public void init(AuthConfigs authConfigs) {
        this.authConfigs = authConfigs;
    }
    
    @Override
    public ServerIdentityResult check(ServerIdentity serverIdentity, Secured secured) {
        if (authConfigs.getServerIdentityValue().equals(serverIdentity.getIdentityValue())) {
            return ServerIdentityResult.success();
        }
        return ServerIdentityResult.noMatched();
    }
}
