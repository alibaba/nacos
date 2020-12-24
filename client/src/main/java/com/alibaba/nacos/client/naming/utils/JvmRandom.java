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

package com.alibaba.nacos.client.naming.utils;

import java.util.Random;

/**
 * <p><code>JVMRandom</code> is a wrapper that supports all possible Random methods via the {@link
 * java.lang.Math#random()} method and its system-wide {@link Random} object.</p>
 * <p>
 * It does this to allow for a Random class in which the seed is shared between all members of the class - a better name
 * would have been SharedSeedRandom.</p>
 * <p>
 * <b>N.B.</b> the current implementation overrides the methods {@link Random#nextInt(int)} and {@link
 * Random#nextLong()} to produce positive numbers ranging from 0 (inclusive) to MAX_VALUE (exclusive).
 * </p>
 * @author unknown
 * @version $Id: JVMRandom.java 911986 2010-02-19 21:19:05Z niallp $
 * @since 2.0
 */
public final class JvmRandom extends Random {
    
    /**
     * Required for serialization support.
     *
     * @see java.io.Serializable
     */
    private static final long serialVersionUID = 1L;
    
    private static final Random SHARED_RANDOM = new Random();
    
    /**
     * Ensures that only the parent constructor can call reseed.
     */
    private boolean constructed = false;
    
    /**
     * Constructs a new instance.
     */
    public JvmRandom() {
        this.constructed = true;
    }
    
    /**
     * Unsupported in 2.0.
     *
     * @param seed ignored
     * @throws UnsupportedOperationException unsupported operation exception
     */
    @Override
    public synchronized void setSeed(long seed) {
        if (this.constructed) {
            throw new UnsupportedOperationException();
        }
    }
    
    /**
     * Unsupported in 2.0.
     *
     * @return Nothing, this method always throws an UnsupportedOperationException.
     * @throws UnsupportedOperationException unsupported operation exception
     */
    @Override
    public synchronized double nextGaussian() {
        throw new UnsupportedOperationException();
    }
    
    /**
     * Unsupported in 2.0.
     *
     * @param byteArray ignored
     * @throws UnsupportedOperationException unsupported operation exception
     */
    @Override
    public void nextBytes(byte[] byteArray) {
        throw new UnsupportedOperationException();
    }
    
    /**
     * <p>Returns the next pseudorandom, uniformly distributed int value from the Math.random() sequence.</p> Identical
     * to <code>nextInt(Integer.MAX_VALUE)</code> <p> <b>N.B. All values are >= 0.</b> </p>
     *
     * @return the random int
     */
    @Override
    public int nextInt() {
        return nextInt(Integer.MAX_VALUE);
    }
    
    /**
     * <p>Returns a pseudorandom, uniformly distributed int value between <code>0</code> (inclusive) and the specified
     * value (exclusive), from the Math.random() sequence.</p>
     *
     * @param n the specified exclusive max-value
     * @return the random int
     * @throws IllegalArgumentException when <code>n &lt;= 0</code>
     */
    @Override
    public int nextInt(int n) {
        return SHARED_RANDOM.nextInt(n);
    }
    
    /**
     * <p>Returns the next pseudorandom, uniformly distributed long value from the Math.random() sequence.</p>
     * Identical
     * to <code>nextLong(Long.MAX_VALUE)</code> <p> <b>N.B. All values are >= 0.</b> </p>
     *
     * @return the random long
     */
    @Override
    public long nextLong() {
        return nextLong(Long.MAX_VALUE);
    }
    
    /**
     * <p>Returns a pseudorandom, uniformly distributed long value between <code>0</code> (inclusive) and the specified
     * value (exclusive), from the Math.random() sequence.</p>
     *
     * @param n the specified exclusive max-value
     * @return the random long
     * @throws IllegalArgumentException when <code>n &lt;= 0</code>
     */
    public static long nextLong(long n) {
        if (n <= 0) {
            throw new IllegalArgumentException("Upper bound for nextInt must be positive");
        }
        // Code adapted from Harmony Random#nextInt(int)
        // n is power of 2
        if ((n & -n) == n) {
            // dropping lower order bits improves behaviour for low values of n
            return next63bits() >> 63 - bitsRequired(n - 1);
        }
        // Not a power of two
        long val;
        long bits;
        // reject some values to improve distribution
        do {
            bits = next63bits();
            val = bits % n;
        } while (bits - val + (n - 1) < 0);
        return val;
    }
    
    /**
     * <p>Returns the next pseudorandom, uniformly distributed boolean value from the Math.random() sequence.</p>
     *
     * @return the random boolean
     */
    @Override
    public boolean nextBoolean() {
        return SHARED_RANDOM.nextBoolean();
    }
    
    /**
     * <p>Returns the next pseudorandom, uniformly distributed float value between <code>0.0</code> and
     * <code>1.0</code>
     * from the Math.random() sequence.</p>
     *
     * @return the random float
     */
    @Override
    public float nextFloat() {
        return SHARED_RANDOM.nextFloat();
    }
    
    /**
     * <p>Synonymous to the Math.random() call.</p>
     *
     * @return the random double
     */
    @Override
    public double nextDouble() {
        return SHARED_RANDOM.nextDouble();
    }
    
    /**
     * Get the next unsigned random long.
     *
     * @return unsigned random long
     */
    private static long next63bits() {
        // drop the sign bit to leave 63 random bits
        return SHARED_RANDOM.nextLong() & 0x7fffffffffffffffL;
    }
    
    /**
     * Count the number of bits required to represent a long number.
     *
     * @param num long number
     * @return number of bits required
     */
    private static int bitsRequired(long num) {
        // Derived from Hacker's Delight, Figure 5-9
        long y = num;
        int n = 0;
        while (true) {
            // 64 = number of bits in a long
            if (num < 0) {
                return 64 - n;
            }
            if (y == 0) {
                return n;
            }
            n++;
            num = num << 1;
            y = y >> 1;
        }
    }
}
