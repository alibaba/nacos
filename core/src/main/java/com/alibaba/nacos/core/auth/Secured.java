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

import org.apache.commons.lang3.StringUtils;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * Annotation indicating that the annotated request should be authorized.
 *
 * @author nkorange
 * @since 1.2.0
 */
@Retention(RetentionPolicy.RUNTIME)
public @interface Secured {

    /**
     * The action type of the request
     *
     * @return action type, default READ
     */
    ActionTypes action() default ActionTypes.READ;

    /**
     * The name of resource related to the request
     *
     * @return resource name
     */
    String resource() default StringUtils.EMPTY;

    /**
     * Resource name parser. Should have lower priority than resource()
     *
     * @return class type of resource parser
     */
    Class<? extends ResourceParser> parser() default DefaultResourceParser.class;
}
