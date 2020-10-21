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

package com.alibaba.nacos.api.common;

/**
 * Response code definitions.
 *
 * <p>This class and inherited classes define codes separated from HTTP code to provide richer and preciser information of
 * the API results. A recommended rule for defining response code is:
 * <li> Global and common code starts with 10001.
 * <li> Naming module code starts with 20001.
 * <li> Config module code starts with 30001.
 * <li> Core module code starts with 40001.
 *
 * @author nkorange
 * @since 1.2.0
 */
public class ResponseCode {
    
    /**
     * Everything normal.
     */
    public static final int OK = 10200;
}
