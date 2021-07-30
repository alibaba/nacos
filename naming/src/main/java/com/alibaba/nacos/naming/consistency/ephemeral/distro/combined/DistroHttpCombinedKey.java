/*
 * Copyright 1999-2018 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.naming.consistency.ephemeral.distro.combined;

import com.alibaba.nacos.core.distributed.distro.entity.DistroKey;

import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Distro http key.
 *
 * @author xiweng.yy
 */
public class DistroHttpCombinedKey extends DistroKey {
    
    private static final AtomicLong SEQUENCE = new AtomicLong(0);
    
    private final List<String> actualResourceTypes = new LinkedList<>();
    
    public DistroHttpCombinedKey(String resourceType, String targetServer) {
        super(DistroHttpCombinedKey.getSequenceKey(), resourceType, targetServer);
    }
    
    public List<String> getActualResourceTypes() {
        return actualResourceTypes;
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof DistroHttpCombinedKey)) {
            return false;
        }
        if (!super.equals(o)) {
            return false;
        }
        DistroHttpCombinedKey that = (DistroHttpCombinedKey) o;
        return Objects.equals(getResourceKey(), that.getResourceKey())
                && Objects.equals(getResourceType(), that.getResourceType())
                && Objects.equals(getTargetServer(), that.getTargetServer())
                && Objects.equals(actualResourceTypes, that.actualResourceTypes);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), actualResourceTypes);
    }
    
    @Override
    public String toString() {
        return getResourceKey() + "{" + "actualResourceTypes=" + actualResourceTypes + "} to " + getTargetServer();
    }
    
    public static String getSequenceKey() {
        return DistroHttpCombinedKey.class.getSimpleName() + "-" + SEQUENCE.get();
    }
    
    public static void incrementSequence() {
        SEQUENCE.incrementAndGet();
    }
}
