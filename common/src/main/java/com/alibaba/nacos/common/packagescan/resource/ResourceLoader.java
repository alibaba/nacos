/*
 * Copyright 2002-2021 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.alibaba.nacos.common.packagescan.resource;

import com.alibaba.nacos.common.packagescan.util.ResourceUtils;

/**
 * Copy from https://github.com/spring-projects/spring-framework.git, with less modifications
 * Strategy interface for loading resources (e.g., class path or file system
 * resources).
 *
 * <p>{@link DefaultResourceLoader} is a standalone implementation
 *
 * <p>Bean properties of type {@code Resource} and {@code Resource[]} can be populated
 * from Strings when running in an ApplicationContext, using the particular
 * context's resource loading strategy.
 *
 * @author Juergen Hoeller
 * @see Resource
 * @see ResourcePatternResolver
 * @since 10.03.2004
 */
public interface ResourceLoader {

    /**
     * Pseudo URL prefix for loading from the class path: "classpath:".
     */
    String CLASSPATH_URL_PREFIX = ResourceUtils.CLASSPATH_URL_PREFIX;


    /**
     * Return a {@code Resource} handle for the specified resource location.
     *
     * <p>The handle should always be a reusable resource descriptor,
     * allowing for multiple {@link Resource#getInputStream()} calls.
     *
     * <p><ul>
     * <li>Must support fully qualified URLs, e.g. "file:C:/test.dat".
     * <li>Must support classpath pseudo-URLs, e.g. "classpath:test.dat".
     * <li>Should support relative file paths, e.g. "WEB-INF/test.dat".
     * (This will be implementation-specific, typically provided by an
     * ApplicationContext implementation.)
     * </ul>
     *
     * <p>Note that a {@code Resource} handle does not imply an existing resource;
     * you need to invoke {@link Resource#exists} to check for existence.
     *
     * @param location the resource location
     * @return a corresponding {@code Resource} handle (never {@code null})
     * @see #CLASSPATH_URL_PREFIX
     * @see Resource#exists()
     * @see Resource#getInputStream()
     */
    Resource getResource(String location);

    /**
     * Expose the {@link ClassLoader} used by this {@code ResourceLoader}.
     *
     * <p>Clients which need to access the {@code ClassLoader} directly can do so
     * in a uniform manner with the {@code ResourceLoader}, rather than relying
     * on the thread context {@code ClassLoader}.
     *
     * @return the {@code ClassLoader}
     * (only {@code null} if even the system {@code ClassLoader} isn't accessible)
     */

    ClassLoader getClassLoader();

}
