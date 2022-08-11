/*
 *
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
 *
 */

package com.alibaba.nacos.istio.xds;

import com.alibaba.nacos.istio.api.ApiGenerator;
import com.alibaba.nacos.istio.common.ResourceSnapshot;
import com.google.protobuf.Any;

import java.util.ArrayList;
import java.util.List;

/**.
 * @author RocketEngine26
 * @date 2022/8/9 18:59
 */
public class EmptyCdsGenerator implements ApiGenerator<Any> {
    
    private static volatile EmptyCdsGenerator singleton = null;
    
    public static EmptyCdsGenerator getInstance() {
        if (singleton == null) {
            synchronized (EmptyCdsGenerator.class) {
                if (singleton == null) {
                    singleton = new EmptyCdsGenerator();
                }
            }
        }
        return singleton;
    }
    
    @Override
    public List<Any> generate(ResourceSnapshot resourceSnapshot) {
        return new ArrayList<>();
    }
}