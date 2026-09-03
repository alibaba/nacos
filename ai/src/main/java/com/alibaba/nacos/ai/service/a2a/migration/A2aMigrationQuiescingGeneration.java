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

package com.alibaba.nacos.ai.service.a2a.migration;

import com.alibaba.nacos.common.utils.StringUtils;

/**
 * Temporary compatibility support for migrating Nacos 3.0-3.2 A2A data.
 *
 * <p>TODO(remove in 4.0): remove after the historical A2A migration window closes.</p>
 *
 * @author Nacos
 */
public final class A2aMigrationQuiescingGeneration {
    
    private static final String PREFIX = "q1:";
    
    private static final char SEPARATOR = ':';
    
    private A2aMigrationQuiescingGeneration() {
    }
    
    /**
     * Bind one opaque quiescing generation to the complete ready member view.
     *
     * @param memberView stable member view
     * @param nonce unique opaque nonce
     * @return opaque marker generation
     */
    public static String create(A2aMigrationMemberView memberView, String nonce) {
        if (memberView == null || StringUtils.isBlank(memberView.getFingerprint())
            || StringUtils.isBlank(nonce) || nonce.indexOf(SEPARATOR) >= 0) {
            throw new IllegalArgumentException(
                "A2A quiescing member view and nonce are required");
        }
        return PREFIX + memberView.getFingerprint() + SEPARATOR + nonce;
    }
    
    /**
     * Verify that an opaque quiescing generation still names the current member view.
     *
     * @param generation marker generation
     * @param memberView current ready member view
     * @return whether the generation is bound to the view
     */
    public static boolean matches(String generation, A2aMigrationMemberView memberView) {
        if (StringUtils.isBlank(generation) || memberView == null
            || !generation.startsWith(PREFIX)) {
            return false;
        }
        int separator = generation.indexOf(SEPARATOR, PREFIX.length());
        if (separator <= PREFIX.length() || separator == generation.length() - 1) {
            return false;
        }
        return memberView.getFingerprint().equals(
            generation.substring(PREFIX.length(), separator));
    }
}
