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

package com.alibaba.nacos.sys.env;

import com.alibaba.nacos.common.utils.VersionUtils;
import com.alibaba.nacos.sys.module.ModuleState;
import com.alibaba.nacos.sys.module.ModuleStateBuilder;

/**
 * Module state builder for env module.
 *
 * @author xiweng.yy
 */
public class EnvModuleStateBuilder implements ModuleStateBuilder {
    
    @Override
    public ModuleState build() {
        ModuleState result = new ModuleState(Constants.SYS_MODULE);
        result.newState(Constants.STANDALONE_MODE_STATE,
                EnvUtil.getStandaloneMode() ? EnvUtil.STANDALONE_MODE_ALONE : EnvUtil.STANDALONE_MODE_CLUSTER);
        result.newState(Constants.FUNCTION_MODE_STATE, EnvUtil.getFunctionMode());
        result.newState(Constants.NACOS_VERSION, VersionUtils.version);
        return result;
    }
}
