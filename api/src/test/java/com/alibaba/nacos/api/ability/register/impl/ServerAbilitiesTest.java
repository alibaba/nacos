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

package com.alibaba.nacos.api.ability.register.impl;

import com.alibaba.nacos.api.ability.constant.AbilityKey;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ServerAbilitiesTest {
    
    @Test
    public void testGetStaticAbilities() {
        assertFalse(ServerAbilities.getStaticAbilities().isEmpty());
    }

    @Test
    public void testSupportPersistentInstanceByGrpcAbilities() {
        assertTrue(ServerAbilities.getStaticAbilities().get(AbilityKey.SERVER_SUPPORT_PERSISTENT_INSTANCE_BY_GRPC));
    }
}