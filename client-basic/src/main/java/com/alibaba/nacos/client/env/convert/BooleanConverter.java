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

package com.alibaba.nacos.client.env.convert;

import com.alibaba.nacos.common.utils.StringUtils;

import java.util.HashSet;
import java.util.Set;

class BooleanConverter extends AbstractPropertyConverter<Boolean> {
    
    private static final Set<String> TRUE_VALUES = new HashSet<>(8);
    
    private static final Set<String> FALSE_VALUES = new HashSet<>(8);
    
    static {
        TRUE_VALUES.add("true");
        TRUE_VALUES.add("on");
        TRUE_VALUES.add("yes");
        TRUE_VALUES.add("1");
        
        FALSE_VALUES.add("false");
        FALSE_VALUES.add("off");
        FALSE_VALUES.add("no");
        FALSE_VALUES.add("0");
    }
    
    @Override
    Boolean convert(String property) {
        if (StringUtils.isEmpty(property)) {
            return null;
        }
        property = property.toLowerCase();
        if (TRUE_VALUES.contains(property)) {
            return Boolean.TRUE;
        } else if (FALSE_VALUES.contains(property)) {
            return Boolean.FALSE;
        } else {
            throw new IllegalArgumentException("Invalid boolean value '" + property + "'");
        }
    }
}
