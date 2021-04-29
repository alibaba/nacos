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
package com.alibaba.nacos.test.base;

import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

/**
 * @author nkorange
 */
public class Params {

    private MultiValueMap<String, String> paramMap;

    public static Params newParams() {
        Params params = new Params();
        params.paramMap = new LinkedMultiValueMap<String, String>();
        return params;
    }

    public Params appendParam(String name, String value) {
        this.paramMap.add(name, value);
        return this;
    }

    public MultiValueMap<String, String> done() {
        return paramMap;
    }
}
