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

package com.alibaba.nacos.common.utils;

import org.checkerframework.checker.nullness.qual.Nullable;

import static com.google.common.base.Strings.lenientFormat;

/**
 * Check precondition, throws an {@code IllegalArgumentException} If the conditions are not met.
 * @author zzq
 * @date 2021/7/29
 */
public class Preconditions {
    
    /**
     * check precondition.
     * @param expression a boolean expression
     * @param errorMessage the exception message to use if the check fails
     * @throws IllegalArgumentException if {@code expression} is false
     */
    public static void checkArgument(boolean expression, @Nullable Object errorMessage) {
        if (!expression) {
            throw new IllegalArgumentException(String.valueOf(errorMessage));
        }
    }
    
    /**
     * check precondition.
     * @param expression a boolean expression
     * @param errorMessageTemplate the exception message template to use if the check fails
     * @param errorMessageArgs the arguments to be substituted into the message template.
     * @throws IllegalArgumentException if {@code expression} is false
     */
    public static void checkArgument(boolean expression, @Nullable String errorMessageTemplate, @Nullable Object @Nullable... errorMessageArgs) {
        if (!expression) {
            throw new IllegalArgumentException(lenientFormat(errorMessageTemplate, errorMessageArgs));
        }
    }
}
