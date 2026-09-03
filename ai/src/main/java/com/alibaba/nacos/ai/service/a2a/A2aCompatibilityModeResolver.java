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

package com.alibaba.nacos.ai.service.a2a;

import com.alibaba.nacos.ai.service.a2a.migration.A2aMigrationState;
import com.alibaba.nacos.ai.service.a2a.migration.A2aMigrationStateService;
import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.sys.env.EnvUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.function.Supplier;

/**
 * Resolves the active historical A2A definition implementation.
 *
 * <p>AUTO remains on historical authority until the migration control plane observes the
 * permanent canonical marker.</p>
 *
 * @author Nacos
 */
@Component
public class A2aCompatibilityModeResolver {
    
    public static final String MODE_PROPERTY = "nacos.ai.a2a.compatibility.mode";
    
    private final A2aMigrationStateService migrationStateService;
    
    private final Supplier<String> configuredModeSupplier;
    
    @Autowired
    public A2aCompatibilityModeResolver(A2aMigrationStateService migrationStateService) {
        this(migrationStateService,
            () -> EnvUtil.getProperty(MODE_PROPERTY, A2aCompatibilityMode.CANONICAL.name()));
    }
    
    A2aCompatibilityModeResolver(A2aMigrationStateService migrationStateService,
        Supplier<String> configuredModeSupplier) {
        this.migrationStateService = migrationStateService;
        this.configuredModeSupplier = configuredModeSupplier;
    }
    
    /**
     * Resolve the implementation for the current request.
     *
     * @return CANONICAL or LEGACY; AUTO is resolved before returning
     */
    public A2aCompatibilityMode resolve() {
        A2aCompatibilityMode configured = parse(configuredModeSupplier.get());
        // TODO(remove in 4.0): Temporary migration path for Nacos 3.0-3.2 A2A data.
        // Keep canonical behavior independent from this branch.
        A2aMigrationState migrationState = migrationStateService.resolve(configured);
        if (A2aMigrationState.CANONICAL == migrationState) {
            return A2aCompatibilityMode.CANONICAL;
        }
        return A2aCompatibilityMode.AUTO == configured ? A2aCompatibilityMode.LEGACY
            : configured;
    }
    
    private A2aCompatibilityMode parse(String configured) {
        String value = StringUtils.isBlank(configured) ? A2aCompatibilityMode.CANONICAL.name()
            : configured.trim().toUpperCase(Locale.ROOT);
        return A2aCompatibilityMode.valueOf(value);
    }
    
}
