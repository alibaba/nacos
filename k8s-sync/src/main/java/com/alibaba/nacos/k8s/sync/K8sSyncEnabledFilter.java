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

package com.alibaba.nacos.k8s.sync;

import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.sys.env.EnvUtil;
import com.alibaba.nacos.sys.filter.NacosPackageExcludeFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;

import static com.alibaba.nacos.sys.env.EnvUtil.FUNCTION_MODE_NAMING;

/**
 * K8s Sync module enabled filter by spring packages scan.
 *
 * @author xiweng.yy
 */
public class K8sSyncEnabledFilter implements NacosPackageExcludeFilter {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(K8sSyncEnabledFilter.class);
    
    private static final String K8S_SYNC_ENABLED_KEY = "nacos.k8s.sync.enabled";
    
    @Override
    public String getResponsiblePackagePrefix() {
        return K8sSyncEnabledFilter.class.getPackage().getName();
    }
    
    @Override
    public boolean isExcluded(String className, Set<String> annotationNames) {
        String functionMode = EnvUtil.getFunctionMode();
        // When not specified naming mode or specified all mode, the naming module not start and load.
        if (isNamingDisabled(functionMode)) {
            LOGGER.warn("K8s Sync module disabled because function mode is {}, and K8s Sync depend naming module",
                    functionMode);
            return true;
        }
        boolean k8sSyncDisabled = !EnvUtil.getProperty(K8S_SYNC_ENABLED_KEY, Boolean.class, false);
        if (k8sSyncDisabled) {
            LOGGER.warn("K8s Sync module disabled because set {} as false", K8S_SYNC_ENABLED_KEY);
        }
        return k8sSyncDisabled;
    }
    
    private boolean isNamingDisabled(String functionMode) {
        if (StringUtils.isEmpty(functionMode)) {
            return false;
        }
        return !FUNCTION_MODE_NAMING.equals(functionMode);
    }
}
