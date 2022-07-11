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

class IntegerConverter extends AbstractPropertyConverter<Integer> {
    
    @Override
    Integer convert(String property) {
        if (StringUtils.isEmpty(property)) {
            return null;
        }
        try {
            return Integer.valueOf(property);
        } catch (Exception e) {
            throw new IllegalArgumentException("Cannot convert String [" + property + "] to Integer");
        }
    }
}
