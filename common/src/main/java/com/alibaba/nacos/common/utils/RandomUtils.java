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

import java.util.Random;

/**
 * Random utils.
 *
 * @author zzq
 */
public class RandomUtils {
    
    /**
     * Random object used by random method. This has to be not local to the random method so as to not return the same
     * value in the same millisecond.
     */
    private static final Random RANDOM = new Random();
    
    /**
     * <p>
     * Returns a random long within the specified range.
     * </p>
     *
     * @param startInclusive the smallest value that can be returned, must be non-negative
     * @param endExclusive   the upper bound (not included)
     * @return the random long
     * @throws IllegalArgumentException if {@code startInclusive > endExclusive} or if {@code startInclusive} is
     *                                  negative
     */
    public static long nextLong(final long startInclusive, final long endExclusive) {
        checkParameters(startInclusive, endExclusive);
        if (startInclusive == endExclusive) {
            return startInclusive;
        }
        return (long) (startInclusive + ((endExclusive - startInclusive) * RANDOM.nextDouble()));
    }
    
    /**
     * <p>
     * Returns a random integer within the specified range.
     * </p>
     *
     * @param startInclusive the smallest value that can be returned, must be non-negative
     * @param endExclusive   the upper bound (not included)
     * @return the random integer
     * @throws IllegalArgumentException if {@code startInclusive > endExclusive} or if {@code startInclusive} is
     *                                  negative
     */
    public static int nextInt(final int startInclusive, final int endExclusive) {
        checkParameters(startInclusive, endExclusive);
        if (startInclusive == endExclusive) {
            return startInclusive;
        }
        return startInclusive + RANDOM.nextInt(endExclusive - startInclusive);
    }
    
    private static void checkParameters(final long startInclusive, final long endExclusive) {
        if (endExclusive < startInclusive) {
            throw new IllegalArgumentException("startInclusive must be less than or equal to the endExclusive.");
        }
        if (startInclusive < 0) {
            throw new IllegalArgumentException("Both parameters must be non-negative");
        }
    }
}
