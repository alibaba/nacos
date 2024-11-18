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

package com.alibaba.nacos.console.config;

import com.alibaba.nacos.sys.module.ModuleState;
import com.alibaba.nacos.sys.module.ModuleStateBuilder;
import com.alibaba.nacos.sys.utils.ApplicationUtils;

/**
 * Console module state builder.
 *
 * @author xiweng.yy
 */
public class ConsoleModuleStateBuilder implements ModuleStateBuilder {
    
    public static final String CONSOLE_MODULE = "console";
    
    private static final String CONSOLE_UI_ENABLED = "console_ui_enabled";
    
    @Override
    public ModuleState build() {
        ModuleState result = new ModuleState(CONSOLE_MODULE);
        try {
            ConsoleConfig consoleConfig = ApplicationUtils.getBean(ConsoleConfig.class);
            result.newState(CONSOLE_UI_ENABLED, consoleConfig.isConsoleUiEnabled());
        } catch (Exception ignored) {
        }
        return result;
    }
}
