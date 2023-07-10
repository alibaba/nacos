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

package com.alibaba.nacos.core.cluster.health;

import com.alibaba.nacos.common.utils.StringUtils;

import java.util.LinkedList;
import java.util.List;

/**
 * Holder of namespace detail injector.
 *
 * @author xiweng.yy
 */
public class ModuleHealthCheckerHolder {
    
    private static final ModuleHealthCheckerHolder INSTANCE = new ModuleHealthCheckerHolder();
    
    private final List<AbstractModuleHealthChecker> moduleHealthCheckers;
    
    private ModuleHealthCheckerHolder() {
        this.moduleHealthCheckers = new LinkedList<>();
    }
    
    public static ModuleHealthCheckerHolder getInstance() {
        return INSTANCE;
    }
    
    public void registerChecker(AbstractModuleHealthChecker checker) {
        this.moduleHealthCheckers.add(checker);
    }
    
    /**
     * Do check readiness for modules.
     *
     * @return readiness result
     */
    public ReadinessResult checkReadiness() {
        List<String> readinessFailedModule = new LinkedList<>();
        for (AbstractModuleHealthChecker each : this.moduleHealthCheckers) {
            boolean moduleReadiness = each.readiness();
            if (!moduleReadiness) {
                readinessFailedModule.add(each.getModuleName());
            }
        }
        if (readinessFailedModule.isEmpty()) {
            return new ReadinessResult(true, "OK");
        } else {
            String modules = StringUtils.join(readinessFailedModule, " and ");
            return new ReadinessResult(false, String.format("%s not in readiness", modules));
        }
    }
}
