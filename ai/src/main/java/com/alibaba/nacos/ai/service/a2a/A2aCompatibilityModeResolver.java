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

import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.common.utils.VersionUtils;
import com.alibaba.nacos.core.cluster.Member;
import com.alibaba.nacos.core.cluster.MemberMetaDataConstants;
import com.alibaba.nacos.core.cluster.ServerMemberManager;
import com.alibaba.nacos.sys.env.EnvUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

/**
 * Resolves the active historical A2A definition implementation.
 *
 * <p>AUTO is deliberately conservative and one-way. It reserves a future cutover hook but does
 * not provide data migration, dual reads, dual writes, or rolling-upgrade guarantees.</p>
 *
 * @author Nacos
 */
@Component
public class A2aCompatibilityModeResolver {
    
    public static final String MODE_PROPERTY = "nacos.ai.a2a.compatibility.mode";
    
    static final String MIN_CANONICAL_VERSION = "3.3.0";
    
    private final ServerMemberManager serverMemberManager;
    
    private final Supplier<String> configuredModeSupplier;
    
    private final AtomicBoolean autoCanonical = new AtomicBoolean(false);
    
    @Autowired
    public A2aCompatibilityModeResolver(ServerMemberManager serverMemberManager) {
        this(serverMemberManager,
            () -> EnvUtil.getProperty(MODE_PROPERTY, A2aCompatibilityMode.CANONICAL.name()));
    }
    
    A2aCompatibilityModeResolver(ServerMemberManager serverMemberManager,
        Supplier<String> configuredModeSupplier) {
        this.serverMemberManager = serverMemberManager;
        this.configuredModeSupplier = configuredModeSupplier;
    }
    
    /**
     * Resolve the implementation for the current request.
     *
     * @return CANONICAL or LEGACY; AUTO is resolved before returning
     */
    public A2aCompatibilityMode resolve() {
        A2aCompatibilityMode configured = parse(configuredModeSupplier.get());
        if (A2aCompatibilityMode.AUTO != configured) {
            return configured;
        }
        if (!autoCanonical.get() && supportsCanonical(serverMemberManager.allMembers())) {
            autoCanonical.compareAndSet(false, true);
        }
        return autoCanonical.get() ? A2aCompatibilityMode.CANONICAL
            : A2aCompatibilityMode.LEGACY;
    }
    
    private A2aCompatibilityMode parse(String configured) {
        String value = StringUtils.isBlank(configured) ? A2aCompatibilityMode.CANONICAL.name()
            : configured.trim().toUpperCase(Locale.ROOT);
        return A2aCompatibilityMode.valueOf(value);
    }
    
    private boolean supportsCanonical(Collection<Member> members) {
        if (members == null || members.isEmpty()) {
            return false;
        }
        for (Member member : members) {
            Object value = member.getExtendVal(MemberMetaDataConstants.VERSION);
            if (!(value instanceof String) || !supportsCanonical((String) value)) {
                return false;
            }
        }
        return true;
    }
    
    private boolean supportsCanonical(String version) {
        if (StringUtils.isBlank(version)) {
            return false;
        }
        try {
            return VersionUtils.compareVersion(version, MIN_CANONICAL_VERSION) >= 0;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
