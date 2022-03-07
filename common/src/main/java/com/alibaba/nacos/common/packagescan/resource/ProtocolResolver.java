/*
 * Copyright 2002-2016 the original author or authors.
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


/**
 * Copy from https://github.com/spring-projects/spring-framework.git, with less modifications
 * A resolution strategy for protocol-specific resource handles.
 *
 * <p>Used as an SPI for {@link DefaultResourceLoader}, allowing for
 * custom protocols to be handled without subclassing the loader
 * implementation (or application context implementation).
 *
 * @author Juergen Hoeller
 * @see DefaultResourceLoader#addProtocolResolver
 * @since 4.3
 */
@FunctionalInterface
public interface ProtocolResolver {

    /**
     * Resolve the given location against the given resource loader
     * if this implementation's protocol matches.
     *
     * @param location       the user-specified resource location
     * @param resourceLoader the associated resource loader
     * @return a corresponding {@code Resource} handle if the given location
     * matches this resolver's protocol, or {@code null} otherwise
     */

    Resource resolve(String location, ResourceLoader resourceLoader);

}
