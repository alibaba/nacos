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

package com.alibaba.nacos.core.auth;

/**
 * Resource parser.
 *
 * @author nkorange
 * @since 1.2.0
 */
public interface ResourceParser {
    
    /**
     * Parse a unique name of the resource from the request.
     *
     * @param request where we can find the resource info. Given it may vary from Http request to gRPC request, we use a
     *                Object type for future accommodation.
     * @return resource name
     */
    String parseName(Object request);
}
