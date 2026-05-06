/*
 * Copyright 1999-2025 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.core.namespace.filter;

import com.alibaba.nacos.core.config.AbstractDynamicConfig;
import com.alibaba.nacos.sys.env.EnvUtil;

/**
 * The type Namespace validation config, default is disable.
 *
 * @author FangYuan
 * @since 2025-08-13 13:33:16
 */
public class NamespaceValidationConfig extends AbstractDynamicConfig {

    private static final String NAMESPACE_VALIDATION = "NamespaceValidation";

    private static final NamespaceValidationConfig INSTANCE = new NamespaceValidationConfig();

    private boolean namespaceValidationEnabled = true;

    protected NamespaceValidationConfig() {
        super(NAMESPACE_VALIDATION);
        resetConfig();
    }

    public static NamespaceValidationConfig getInstance() {
        return INSTANCE;
    }

    @Override
    protected void getConfigFromEnv() {
        namespaceValidationEnabled = EnvUtil.getProperty("nacos.core.namespace.validation.enabled", Boolean.class, false);
    }

    public boolean isNamespaceValidationEnabled() {
        return namespaceValidationEnabled;
    }

    @Override
    protected String printConfig() {
        return "NamespaceValidationConfig{" + "namespaceValidationEnabled=" + namespaceValidationEnabled + "}";
    }
}