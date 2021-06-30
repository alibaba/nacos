/*
 * Copyright 1999-2020 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.naming.core.v2.upgrade;

import com.alibaba.nacos.common.spi.NacosServiceLoader;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * SPI holder for self upgrade checker.
 *
 * @author xiweng.yy
 */
public class SelfUpgradeCheckerSpiHolder {
    
    private static final SelfUpgradeCheckerSpiHolder INSTANCE = new SelfUpgradeCheckerSpiHolder();
    
    private static final DefaultSelfUpgradeChecker DEFAULT_SELF_UPGRADE_CHECKER = new DefaultSelfUpgradeChecker();
    
    private final Map<String, SelfUpgradeChecker> selfUpgradeCheckerMap;
    
    private SelfUpgradeCheckerSpiHolder() {
        Collection<SelfUpgradeChecker> checkers = NacosServiceLoader.load(SelfUpgradeChecker.class);
        selfUpgradeCheckerMap = new HashMap<>(checkers.size());
        for (SelfUpgradeChecker each : checkers) {
            selfUpgradeCheckerMap.put(each.checkType(), each);
        }
    }
    
    /**
     * Find target type self checker.
     *
     * @param type target type
     * @return target {@link SelfUpgradeChecker} if exist, otherwise {@link DefaultSelfUpgradeChecker}
     */
    public static SelfUpgradeChecker findSelfChecker(String type) {
        return INSTANCE.selfUpgradeCheckerMap.getOrDefault(type, DEFAULT_SELF_UPGRADE_CHECKER);
    }
}
