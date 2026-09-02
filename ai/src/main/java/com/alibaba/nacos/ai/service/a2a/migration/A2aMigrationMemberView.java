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

import java.util.Objects;

/**
 * Temporary compatibility support for migrating Nacos 3.0-3.2 A2A data.
 *
 * <p>TODO(remove in 4.0): remove after the historical A2A migration window closes.</p>
 *
 * @author Nacos
 */
public final class A2aMigrationMemberView {
    
    private final String fingerprint;
    
    private final int memberCount;
    
    A2aMigrationMemberView(String fingerprint, int memberCount) {
        this.fingerprint = fingerprint;
        this.memberCount = memberCount;
    }
    
    public String getFingerprint() {
        return fingerprint;
    }
    
    public int getMemberCount() {
        return memberCount;
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof A2aMigrationMemberView)) {
            return false;
        }
        A2aMigrationMemberView that = (A2aMigrationMemberView) o;
        return memberCount == that.memberCount && fingerprint.equals(that.fingerprint);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(fingerprint, memberCount);
    }
}
