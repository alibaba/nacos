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

package com.alibaba.nacos.istio.xds;

import com.alibaba.nacos.istio.api.ApiGenerator;
import com.alibaba.nacos.istio.common.ResourceSnapshot;
import com.google.protobuf.Any;

import java.util.ArrayList;
import java.util.List;

/**
 * @author special.fy
 */
public class EmptyXdsGenerator implements ApiGenerator<Any> {

    private volatile static EmptyXdsGenerator singleton = null;

    public static EmptyXdsGenerator getInstance() {
        if (singleton == null) {
            synchronized (EmptyXdsGenerator.class) {
                if (singleton == null) {
                    singleton = new EmptyXdsGenerator();
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
