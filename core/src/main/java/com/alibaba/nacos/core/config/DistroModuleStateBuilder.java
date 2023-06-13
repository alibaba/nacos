/*
 * Copyright 1999-2020 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.core.config;

import com.alibaba.nacos.core.distributed.distro.DistroConstants;
import com.alibaba.nacos.sys.env.EnvUtil;
import com.alibaba.nacos.sys.module.ModuleState;
import com.alibaba.nacos.sys.module.ModuleStateBuilder;

/**
 * distro state builder.
 * @author 985492783@qq.com
 * @date 2023/4/7 0:37
 */
public class DistroModuleStateBuilder implements ModuleStateBuilder {
    
    @Override
    public ModuleState build() {
        ModuleState moduleState = new ModuleState(DistroConstants.DISTRO_MODULE);
        moduleState.newState(DistroConstants.DATA_SYNC_DELAY_MILLISECONDS_STATE,
                EnvUtil.getProperty(DistroConstants.DATA_SYNC_DELAY_MILLISECONDS, Long.class,
                        DistroConstants.DEFAULT_DATA_SYNC_DELAY_MILLISECONDS));
        moduleState.newState(DistroConstants.DATA_SYNC_TIMEOUT_MILLISECONDS_STATE,
                EnvUtil.getProperty(DistroConstants.DATA_SYNC_TIMEOUT_MILLISECONDS, Long.class,
                        DistroConstants.DEFAULT_DATA_SYNC_TIMEOUT_MILLISECONDS));
        moduleState.newState(DistroConstants.DATA_SYNC_RETRY_DELAY_MILLISECONDS_STATE,
                EnvUtil.getProperty(DistroConstants.DATA_SYNC_RETRY_DELAY_MILLISECONDS, Long.class,
                        DistroConstants.DEFAULT_DATA_SYNC_RETRY_DELAY_MILLISECONDS));
        moduleState.newState(DistroConstants.DATA_VERIFY_INTERVAL_MILLISECONDS_STATE,
                EnvUtil.getProperty(DistroConstants.DATA_VERIFY_INTERVAL_MILLISECONDS, Long.class,
                        DistroConstants.DEFAULT_DATA_VERIFY_INTERVAL_MILLISECONDS));
        moduleState.newState(DistroConstants.DATA_VERIFY_TIMEOUT_MILLISECONDS_STATE,
                EnvUtil.getProperty(DistroConstants.DATA_VERIFY_TIMEOUT_MILLISECONDS, Long.class,
                        DistroConstants.DEFAULT_DATA_VERIFY_TIMEOUT_MILLISECONDS));
        moduleState.newState(DistroConstants.DATA_LOAD_RETRY_DELAY_MILLISECONDS_STATE,
                EnvUtil.getProperty(DistroConstants.DATA_LOAD_RETRY_DELAY_MILLISECONDS, Long.class,
                        DistroConstants.DEFAULT_DATA_LOAD_RETRY_DELAY_MILLISECONDS));
        moduleState.newState(DistroConstants.DATA_LOAD_TIMEOUT_MILLISECONDS_STATE,
                EnvUtil.getProperty(DistroConstants.DATA_LOAD_TIMEOUT_MILLISECONDS, Long.class,
                        DistroConstants.DEFAULT_DATA_LOAD_TIMEOUT_MILLISECONDS));
        return moduleState;
    }
    
    @Override
    public boolean isIgnore() {
        return EnvUtil.getStandaloneMode();
    }
}
