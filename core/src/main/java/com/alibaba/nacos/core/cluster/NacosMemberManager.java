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

package com.alibaba.nacos.core.cluster;

import com.alibaba.nacos.core.cluster.lookup.AbstractMemberLookup;

import java.util.Collection;

/**
 * Nacos member managers.
 *
 * @author xiweng.yy
 */
public interface NacosMemberManager {
    
    /**
     * Nacos members changed, called in {@link AbstractMemberLookup#afterLookup(Collection)}.
     *
     * @param members new members
     * @return {@code true} if changed, {@code false} otherwise
     */
    boolean memberChange(Collection<Member> members);
    
    /**
     * Get all members.
     *
     * @return {@link Collection} all member
     */
    Collection<Member> allMembers();
}

