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

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

/**
 * Objects utils.
 *
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 */
@SuppressWarnings("PMD.RemoveCommentedCodeRule")
public class Objects {
    
    /**
     * Returns {@code true} if the arguments are equal to each other and {@code false} otherwise. Consequently, if both
     * arguments are {@code null}, {@code true} is returned and if exactly one argument is {@code null}, {@code false}
     * is returned.  Otherwise, equality is determined by using the {@link Object#equals equals} method of the first
     * argument.
     *
     * @param a an object
     * @param b an object to be compared with {@code a} for equality
     * @return {@code true} if the arguments are equal to each other and {@code false} otherwise
     * @see Object#equals(Object)
     */
    public static boolean equals(Object a, Object b) {
        return (a == b) || (a != null && a.equals(b));
    }
    
    /**
     * Returns the hash code of a non-{@code null} argument and 0 for a {@code null} argument.
     *
     * @param o an object
     * @return the hash code of a non-{@code null} argument and 0 for a {@code null} argument
     * @see Object#hashCode
     */
    public static int hashCode(Object o) {
        return o != null ? o.hashCode() : 0;
    }
    
    /**
     * Generates a hash code for a sequence of input values. The hash code is generated as if all the input values were
     * placed into an array, and that array were hashed by calling {@link Arrays#hashCode(Object[])}.
     *
     * <p>This method is useful for implementing {@link
     * Object#hashCode()} on objects containing multiple fields. For example, if an object that has three fields, {@code
     * x}, {@code y}, and {@code z}, one could write:
     *
     * <blockquote><pre>
     * &#064;Override
     * public int hashCode() {
     *     return Objects.hash(x, y, z);
     * }
     * </pre></blockquote>
     *
     * <b>Warning: When a single object reference is supplied, the returned
     * value does not equal the hash code of that object reference.</b> This value can be computed by calling {@link
     * #hashCode(Object)}.
     *
     * @param values the values to be hashed
     * @return a hash value of the sequence of input values
     * @see Arrays#hashCode(Object[])
     * @see List#hashCode
     */
    public static int hash(Object... values) {
        return Arrays.hashCode(values);
    }
    
    /**
     * Returns the result of calling {@code toString} for a non-{@code null} argument and {@code "null"} for a {@code
     * null} argument.
     *
     * @param o an object
     * @return the result of calling {@code toString} for a non-{@code null} argument and {@code "null"} for a {@code
     * null} argument
     * @see Object#toString
     * @see String#valueOf(Object)
     */
    public static String toString(Object o) {
        return String.valueOf(o);
    }
    
    /**
     * Returns the result of calling {@code toString} on the first argument if the first argument is not {@code null}
     * and returns the second argument otherwise.
     *
     * @param o           an object
     * @param nullDefault string to return if the first argument is {@code null}
     * @return the result of calling {@code toString} on the first argument if it is not {@code null} and the second
     * argument otherwise.
     */
    public static String toString(Object o, String nullDefault) {
        return (o != null) ? o.toString() : nullDefault;
    }
    
    /**
     * Returns 0 if the arguments are identical and {@code c.compare(a, b)} otherwise. Consequently, if both arguments
     * are {@code null} 0 is returned.
     *
     * <p>Note that if one of the arguments is {@code null}, a {@code
     * NullPointerException} may or may not be thrown depending on what ordering policy, if any, the {@link Comparator
     * Comparator} chooses to have for {@code null} values.
     *
     * @param <T> the type of the objects being compared
     * @param a   an object
     * @param b   an object to be compared with {@code a}
     * @param c   the {@code Comparator} to compare the first two arguments
     * @return 0 if the arguments are identical and {@code c.compare(a, b)} otherwise.
     * @see Comparable
     * @see Comparator
     */
    public static <T> int compare(T a, T b, Comparator<? super T> c) {
        return (a == b) ? 0 : c.compare(a, b);
    }
    
    /**
     * Checks that the specified object reference is not {@code null}. This method is designed primarily for doing
     * parameter validation in methods and constructors, as demonstrated below:
     * <blockquote><pre>
     * public Foo(Bar bar) {
     *     this.bar = Objects.requireNonNull(bar);
     * }
     * </pre></blockquote>
     *
     * @param obj the object reference to check for nullity
     * @param <T> the type of the reference
     * @return {@code obj} if not {@code null}
     * @throws NullPointerException if {@code obj} is {@code null}
     */
    public static <T> T requireNonNull(T obj) {
        if (obj == null) {
            throw new NullPointerException();
        }
        return obj;
    }
    
    /**
     * Checks that the specified object reference is not {@code null} and throws a customized {@link
     * NullPointerException} if it is. This method is designed primarily for doing parameter validation in methods and
     * constructors with multiple parameters, as demonstrated below:
     * <blockquote><pre>
     * public Foo(Bar bar, Baz baz) {
     *     this.bar = Objects.requireNonNull(bar, "bar must not be null");
     *     this.baz = Objects.requireNonNull(baz, "baz must not be null");
     * }
     * </pre></blockquote>
     *
     * @param obj     the object reference to check for nullity
     * @param message detail message to be used in the event that a {@code NullPointerException} is thrown
     * @param <T>     the type of the reference
     * @return {@code obj} if not {@code null}
     * @throws NullPointerException if {@code obj} is {@code null}
     */
    public static <T> T requireNonNull(T obj, String message) {
        if (obj == null) {
            throw new NullPointerException(message);
        }
        return obj;
    }
    
    /**
     * Returns {@code true} if the provided reference is {@code null} otherwise returns {@code false}.
     *
     * @param obj a reference to be checked against {@code null}
     * @return {@code true} if the provided reference is {@code null} otherwise {@code false}
     * @apiNote This method exists to be used as a
     * @since 1.8
     */
    public static boolean isNull(Object obj) {
        return obj == null;
    }
    
    /**
     * Returns {@code true} if the provided reference is non-{@code null} otherwise returns {@code false}.
     *
     * @param obj a reference to be checked against {@code null}
     * @return {@code true} if the provided reference is non-{@code null} otherwise {@code false}
     * @apiNote This method exists to be used as a
     * @since 1.8
     */
    public static boolean nonNull(Object obj) {
        return obj != null;
    }
    
    /**
     * Returns the first argument if it is non-{@code null} and otherwise returns the non-{@code null} second argument.
     *
     * @param obj        an object
     * @param defaultObj a non-{@code null} object to return if the first argument is {@code null}
     * @param <T>        the type of the reference
     * @return the first argument if it is non-{@code null} and otherwise the second argument if it is non-{@code null}
     * @throws NullPointerException if both {@code obj} is null and {@code defaultObj} is {@code null}
     * @since 9
     */
    public static <T> T requireNonNullElse(T obj, T defaultObj) {
        return (obj != null) ? obj : requireNonNull(defaultObj, "defaultObj");
    }
}
