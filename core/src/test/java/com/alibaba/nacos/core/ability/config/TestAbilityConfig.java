/*
 * Copyright 1999-2022 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.core.ability.config;

import com.alibaba.nacos.api.ability.constant.AbilityKey;

import java.util.Set;

/**
 * .
 *
 * @author Daydreamer
 * @description Dynamically load ability from config. just for test
 * @date 2022/8/31 12:27
 **/
public class TestAbilityConfig extends AbilityConfigs {
    
    public TestAbilityConfig() {
        Set<AbilityKey> serverAbilityKeys = super.getServerAbilityKeys();
        serverAbilityKeys.add(AbilityKey.SERVER_TEST_1);
        serverAbilityKeys.add(AbilityKey.SERVER_TEST_2);
    }
}
