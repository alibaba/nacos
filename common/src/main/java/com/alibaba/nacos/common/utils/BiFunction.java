package com.alibaba.nacos.common.utils;


/**
 * Represents a function that accepts two arguments and produces a result.
 *
 * <p>This is a <a href="package-summary.html">functional interface</a>
 * whose functional method is {@link #apply(Object, Object)}.
 *
 * @author zongtanghu
 *
 */
public interface BiFunction<T, U, R> {

    //    The following utility functions are extracted from <link>org.apache.commons.lang3</link>
    //    start

    /**
     * Applies this function to the given arguments.
     *
     * @param t the first function argument
     * @param u the second function argument
     * @return the function result
     */
    R apply(T t, U u);

    //    end
}
