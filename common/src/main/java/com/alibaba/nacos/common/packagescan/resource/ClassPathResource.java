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

import com.alibaba.nacos.common.utils.AbstractObjectUtils;
import com.alibaba.nacos.common.utils.AbstractAssert;
import com.alibaba.nacos.common.utils.ClassUtils;
import com.alibaba.nacos.common.utils.StringUtils;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;

/**
 * Copy from https://github.com/spring-projects/spring-framework.git, with less modifications
 * {@link Resource} implementation for class path resources. Uses either a
 * given {@link ClassLoader} or a given {@link Class} for loading resources.
 *
 * <p>Supports resolution as {@code java.io.File} if the class path
 * resource resides in the file system, but not for resources in a JAR.
 * Always supports resolution as URL.
 *
 * @author Juergen Hoeller
 * @author Sam Brannen
 * @see ClassLoader#getResourceAsStream(String)
 * @see Class#getResourceAsStream(String)
 * @since 28.12.2003
 */
public class ClassPathResource extends AbstractFileResolvingResource {

    private final String path;

    private ClassLoader classLoader;

    private Class<?> clazz;

    /**
     * Create a new {@code ClassPathResource} for {@code ClassLoader} usage.
     * A leading slash will be removed, as the ClassLoader resource access
     * methods will not accept it.
     *
     * <p>The thread context class loader will be used for
     * loading the resource.
     *
     * @param path the absolute path within the class path
     * @see ClassLoader#getResourceAsStream(String)
     * @see ClassUtils#getDefaultClassLoader()
     */
    public ClassPathResource(String path) {
        this(path, (ClassLoader) null);
    }

    /**
     * Create a new {@code ClassPathResource} for {@code ClassLoader} usage.
     * A leading slash will be removed, as the ClassLoader resource access
     * methods will not accept it.
     *
     * @param path        the absolute path within the classpath
     * @param classLoader the class loader to load the resource with,
     *                    or {@code null} for the thread context class loader
     * @see ClassLoader#getResourceAsStream(String)
     */
    public ClassPathResource(String path, ClassLoader classLoader) {
        AbstractAssert.notNull(path, "Path must not be null");
        String pathToUse = StringUtils.cleanPath(path);
        if (pathToUse.startsWith("/")) {
            pathToUse = pathToUse.substring(1);
        }
        this.path = pathToUse;
        this.classLoader = (classLoader != null ? classLoader : ClassUtils.getDefaultClassLoader());
    }

    /**
     * Create a new {@code ClassPathResource} for {@code Class} usage.
     * The path can be relative to the given class, or absolute within
     * the classpath via a leading slash.
     *
     * @param path  relative or absolute path within the class path
     * @param clazz the class to load resources with
     * @see Class#getResourceAsStream
     */
    public ClassPathResource(String path, Class<?> clazz) {
        AbstractAssert.notNull(path, "Path must not be null");
        this.path = StringUtils.cleanPath(path);
        this.clazz = clazz;
    }

    /**
     * Create a new {@code ClassPathResource} with optional {@code ClassLoader}
     * and {@code Class}. Only for internal usage.
     *
     * @param path        relative or absolute path within the classpath
     * @param classLoader the class loader to load the resource with, if any
     * @param clazz       the class to load resources with, if any
     * @deprecated as of 4.3.13, in favor of selective use of
     * {@link #ClassPathResource(String, ClassLoader)} vs {@link #ClassPathResource(String, Class)}
     */
    @Deprecated
    protected ClassPathResource(String path, ClassLoader classLoader, Class<?> clazz) {
        this.path = StringUtils.cleanPath(path);
        this.classLoader = classLoader;
        this.clazz = clazz;
    }

    /**
     * Return the path for this resource (as resource path within the class path).
     */
    public final String getPath() {
        return this.path;
    }

    /**
     * Return the ClassLoader that this resource will be obtained from.
     */

    public final ClassLoader getClassLoader() {
        return (this.clazz != null ? this.clazz.getClassLoader() : this.classLoader);
    }


    /**
     * This implementation checks for the resolution of a resource URL.
     *
     * @see ClassLoader#getResource(String)
     * @see Class#getResource(String)
     */
    @Override
    public boolean exists() {
        return (resolveUrl() != null);
    }

    /**
     * This implementation checks for the resolution of a resource URL upfront,
     * then proceeding with {@link AbstractFileResolvingResource}'s length check.
     *
     * @see ClassLoader#getResource(String)
     * @see Class#getResource(String)
     */
    @Override
    public boolean isReadable() {
        URL url = resolveUrl();
        return (url != null && checkReadable(url));
    }

    /**
     * Resolves a URL for the underlying class path resource.
     *
     * @return the resolved URL, or {@code null} if not resolvable
     */

    protected URL resolveUrl() {
        try {
            if (this.clazz != null) {
                return this.clazz.getResource(this.path);
            } else if (this.classLoader != null) {
                return this.classLoader.getResource(this.path);
            } else {
                return ClassLoader.getSystemResource(this.path);
            }
        } catch (IllegalArgumentException ex) {
            // Should not happen according to the JDK's contract:
            // see https://github.com/openjdk/jdk/pull/2662
            return null;
        }
    }

    /**
     * This implementation opens an InputStream for the given class path resource.
     *
     * @see ClassLoader#getResourceAsStream(String)
     * @see Class#getResourceAsStream(String)
     */
    @Override
    public InputStream getInputStream() throws IOException {
        InputStream is;
        if (this.clazz != null) {
            is = this.clazz.getResourceAsStream(this.path);
        } else if (this.classLoader != null) {
            is = this.classLoader.getResourceAsStream(this.path);
        } else {
            is = ClassLoader.getSystemResourceAsStream(this.path);
        }
        if (is == null) {
            throw new FileNotFoundException(getDescription() + " cannot be opened because it does not exist");
        }
        return is;
    }

    /**
     * This implementation returns a URL for the underlying class path resource,
     * if available.
     *
     * @see ClassLoader#getResource(String)
     * @see Class#getResource(String)
     */
    @Override
    public URL getUrl() throws IOException {
        URL url = resolveUrl();
        if (url == null) {
            throw new FileNotFoundException(getDescription() + " cannot be resolved to URL because it does not exist");
        }
        return url;
    }

    /**
     * This implementation creates a ClassPathResource, applying the given path
     * relative to the path of the underlying resource of this descriptor.
     *
     * @see StringUtils#applyRelativePath(String, String)
     */
    @Override
    public Resource createRelative(String relativePath) {
        String pathToUse = StringUtils.applyRelativePath(this.path, relativePath);
        return (this.clazz != null ? new ClassPathResource(pathToUse, this.clazz) :
                new ClassPathResource(pathToUse, this.classLoader));
    }

    /**
     * This implementation returns the name of the file that this class path
     * resource refers to.
     *
     * @see StringUtils#getFilename(String)
     */
    @Override

    public String getFilename() {
        return StringUtils.getFilename(this.path);
    }

    /**
     * This implementation returns a description that includes the class path location.
     */
    @Override
    public String getDescription() {
        StringBuilder builder = new StringBuilder("class path resource [");
        String pathToUse = this.path;
        if (this.clazz != null && !pathToUse.startsWith("/")) {
            builder.append(ClassUtils.classPackageAsResourcePath(this.clazz));
            builder.append('/');
        }
        if (pathToUse.startsWith("/")) {
            pathToUse = pathToUse.substring(1);
        }
        builder.append(pathToUse);
        builder.append(']');
        return builder.toString();
    }


    /**
     * This implementation compares the underlying class path locations.
     */
    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof ClassPathResource)) {
            return false;
        }
        ClassPathResource otherRes = (ClassPathResource) other;
        return (this.path.equals(otherRes.path)
                && AbstractObjectUtils.nullSafeEquals(this.classLoader, otherRes.classLoader)
                && AbstractObjectUtils.nullSafeEquals(this.clazz, otherRes.clazz));
    }

    /**
     * This implementation returns the hash code of the underlying
     * class path location.
     */
    @Override
    public int hashCode() {
        return this.path.hashCode();
    }

}
