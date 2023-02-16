/*
 * Copyright 2002-2020 the original author or authors.
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

import com.alibaba.nacos.common.packagescan.util.PathMatcher;
import com.alibaba.nacos.common.packagescan.util.ResourceUtils;
import com.alibaba.nacos.common.utils.AbstractAssert;
import com.alibaba.nacos.common.utils.ClassUtils;
import com.alibaba.nacos.common.utils.ReflectUtils;
import com.alibaba.nacos.common.utils.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.net.JarURLConnection;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLClassLoader;
import java.net.URLConnection;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.zip.ZipException;

/**
 * Copy from https://github.com/spring-projects/spring-framework.git, with less modifications
 * A {@link ResourcePatternResolver} implementation that is able to resolve a
 * specified resource location path into one or more matching Resources.
 * The source path may be a simple path which has a one-to-one mapping to a
 * target {@link Resource}, or alternatively
 * may contain the special "{@code classpath*:}" prefix and/or
 * internal Ant-style regular expressions (matched using Spring's
 * {@link AntPathMatcher} utility).
 * Both of the latter are effectively wildcards.
 *
 * <p><b>No Wildcards:</b>
 *
 * <p>In the simple case, if the specified location path does not start with the
 * {@code "classpath*:}" prefix, and does not contain a PathMatcher pattern,
 * this resolver will simply return a single resource via a
 * {@code getResource()} call on the underlying {@code ResourceLoader}.
 * Examples are real URLs such as "{@code file:C:/context.xml}", pseudo-URLs
 * such as "{@code classpath:/context.xml}", and simple unprefixed paths
 * such as "{@code /WEB-INF/context.xml}". The latter will resolve in a
 * fashion specific to the underlying {@code ResourceLoader} (e.g.
 * {@code ServletContextResource} for a {@code WebApplicationContext}).
 *
 * <p><b>Ant-style Patterns:</b>
 *
 * <p>When the path location contains an Ant-style pattern, e.g.:
 * <pre class="code">
 * /WEB-INF/*-context.xml
 * com/mycompany/**&#47;applicationContext.xml
 * file:C:/some/path/*-context.xml
 * classpath:com/mycompany/**&#47;applicationContext.xml</pre>
 * the resolver follows a more complex but defined procedure to try to resolve
 * the wildcard. It produces a {@code Resource} for the path up to the last
 * non-wildcard segment and obtains a {@code URL} from it. If this URL is
 * not a "{@code jar:}" URL or container-specific variant (e.g.
 * "{@code zip:}" in WebLogic, "{@code wsjar}" in WebSphere", etc.),
 * then a {@code java.io.File} is obtained from it, and used to resolve the
 * wildcard by walking the filesystem. In the case of a jar URL, the resolver
 * either gets a {@code java.net.JarURLConnection} from it, or manually parses
 * the jar URL, and then traverses the contents of the jar file, to resolve the
 * wildcards.
 *
 * <p><b>Implications on portability:</b>
 *
 * <p>If the specified path is already a file URL (either explicitly, or
 * implicitly because the base {@code ResourceLoader} is a filesystem one,
 * then wildcarding is guaranteed to work in a completely portable fashion.
 *
 * <p>If the specified path is a classpath location, then the resolver must
 * obtain the last non-wildcard path segment URL via a
 * {@code Classloader.getResource()} call. Since this is just a
 * node of the path (not the file at the end) it is actually undefined
 * (in the ClassLoader Javadocs) exactly what sort of a URL is returned in
 * this case. In practice, it is usually a {@code java.io.File} representing
 * the directory, where the classpath resource resolves to a filesystem
 * location, or a jar URL of some sort, where the classpath resource resolves
 * to a jar location. Still, there is a portability concern on this operation.
 *
 * <p>If a jar URL is obtained for the last non-wildcard segment, the resolver
 * must be able to get a {@code java.net.JarURLConnection} from it, or
 * manually parse the jar URL, to be able to walk the contents of the jar,
 * and resolve the wildcard. This will work in most environments, but will
 * fail in others, and it is strongly recommended that the wildcard
 * resolution of resources coming from jars be thoroughly tested in your
 * specific environment before you rely on it.
 *
 * <p><b>{@code classpath*:} Prefix:</b>
 *
 * <p>There is special support for retrieving multiple class path resources with
 * the same name, via the "{@code classpath*:}" prefix. For example,
 * "{@code classpath*:META-INF/beans.xml}" will find all "beans.xml"
 * files in the class path, be it in "classes" directories or in JAR files.
 * This is particularly useful for autodetecting config files of the same name
 * at the same location within each jar file. Internally, this happens via a
 * {@code ClassLoader.getResources()} call, and is completely portable.
 *
 * <p>The "classpath*:" prefix can also be combined with a PathMatcher pattern in
 * the rest of the location path, for example "classpath*:META-INF/*-beans.xml".
 * In this case, the resolution strategy is fairly simple: a
 * {@code ClassLoader.getResources()} call is used on the last non-wildcard
 * path segment to get all the matching resources in the class loader hierarchy,
 * and then off each resource the same PathMatcher resolution strategy described
 * above is used for the wildcard subpath.
 *
 * <p><b>Other notes:</b>
 *
 * <p><b>WARNING:</b> Note that "{@code classpath*:}" when combined with
 * Ant-style patterns will only work reliably with at least one root directory
 * before the pattern starts, unless the actual target files reside in the file
 * system. This means that a pattern like "{@code classpath*:*.xml}" will
 * <i>not</i> retrieve files from the root of jar files but rather only from the
 * root of expanded directories. This originates from a limitation in the JDK's
 * {@code ClassLoader.getResources()} method which only returns file system
 * locations for a passed-in empty String (indicating potential roots to search).
 * This {@code ResourcePatternResolver} implementation is trying to mitigate the
 * jar root lookup limitation through {@link URLClassLoader} introspection and
 * "java.class.path" manifest evaluation; however, without portability guarantees.
 *
 * <p><b>WARNING:</b> Ant-style patterns with "classpath:" resources are not
 * guaranteed to find matching resources if the root package to search is available
 * in multiple class path locations. This is because a resource such as
 * <pre class="code">
 *     com/mycompany/package1/service-context.xml
 * </pre>
 * may be in only one location, but when a path such as
 * <pre class="code">
 *     classpath:com/mycompany/**&#47;service-context.xml
 * </pre>
 * is used to try to resolve it, the resolver will work off the (first) URL
 * returned by {@code getResource("com/mycompany");}. If this base package node
 * exists in multiple classloader locations, the actual end resource may not be
 * underneath. Therefore, preferably, use "{@code classpath*:}" with the same
 * Ant-style pattern in such a case, which will search <i>all</i> class path
 * locations that contain the root package.
 *
 * @author Juergen Hoeller
 * @author Colin Sampaleanu
 * @author Marius Bogoevici
 * @author Costin Leau
 * @author Phillip Webb
 * @see #CLASSPATH_ALL_URL_PREFIX
 * @see AntPathMatcher
 * @see ResourceLoader#getResource(String)
 * @see ClassLoader#getResources(String)
 * @since 1.0.2
 */
public class PathMatchingResourcePatternResolver implements ResourcePatternResolver {

    private static final Logger LOGGER = LoggerFactory.getLogger(PathMatchingResourcePatternResolver.class);

    private static Method equinoxResolveMethod;

    static {
        try {
            // Detect Equinox OSGi (e.g. on WebSphere 6.1)
            Class<?> fileLocatorClass = ClassUtils.forName("org.eclipse.core.runtime.FileLocator",
                    PathMatchingResourcePatternResolver.class.getClassLoader());
            equinoxResolveMethod = fileLocatorClass.getMethod("resolve", URL.class);
            LOGGER.trace("Found Equinox FileLocator for OSGi bundle URL resolution");
        } catch (Throwable ex) {
            equinoxResolveMethod = null;
        }
    }

    private final ResourceLoader resourceLoader;

    private PathMatcher pathMatcher = new AntPathMatcher();

    /**
     * Create a new PathMatchingResourcePatternResolver with a DefaultResourceLoader.
     *
     * <p>ClassLoader access will happen via the thread context class loader.
     *
     * @see DefaultResourceLoader
     */
    public PathMatchingResourcePatternResolver() {
        this.resourceLoader = new DefaultResourceLoader();
    }

    /**
     * Create a new PathMatchingResourcePatternResolver.
     *
     * <p>ClassLoader access will happen via the thread context class loader.
     *
     * @param resourceLoader the ResourceLoader to load root directories and
     *                       actual resources with
     */
    public PathMatchingResourcePatternResolver(ResourceLoader resourceLoader) {
        AbstractAssert.notNull(resourceLoader, "ResourceLoader must not be null");
        this.resourceLoader = resourceLoader;
    }

    /**
     * Create a new PathMatchingResourcePatternResolver with a DefaultResourceLoader.
     *
     * @param classLoader the ClassLoader to load classpath resources with,
     *                    or {@code null} for using the thread context class loader
     *                    at the time of actual resource access
     * @see DefaultResourceLoader
     */
    public PathMatchingResourcePatternResolver(ClassLoader classLoader) {
        this.resourceLoader = new DefaultResourceLoader(classLoader);
    }

    /**
     * Return the ResourceLoader that this pattern resolver works with.
     */
    public ResourceLoader getResourceLoader() {
        return this.resourceLoader;
    }

    @Override

    public ClassLoader getClassLoader() {
        return getResourceLoader().getClassLoader();
    }

    /**
     * Set the PathMatcher implementation to use for this
     * resource pattern resolver. Default is AntPathMatcher.
     *
     * @see AntPathMatcher
     */
    public void setPathMatcher(PathMatcher pathMatcher) {
        AbstractAssert.notNull(pathMatcher, "PathMatcher must not be null");
        this.pathMatcher = pathMatcher;
    }

    /**
     * Return the PathMatcher that this resource pattern resolver uses.
     */
    public PathMatcher getPathMatcher() {
        return this.pathMatcher;
    }

    @Override
    public Resource getResource(String location) {
        return getResourceLoader().getResource(location);
    }

    @Override
    public Resource[] getResources(String locationPattern) throws IOException {
        AbstractAssert.notNull(locationPattern, "Location pattern must not be null");
        if (locationPattern.startsWith(CLASSPATH_ALL_URL_PREFIX)) {
            // a class path resource (multiple resources for same name possible)
            if (getPathMatcher().isPattern(locationPattern.substring(CLASSPATH_ALL_URL_PREFIX.length()))) {
                // a class path resource pattern
                return findPathMatchingResources(locationPattern);
            } else {
                // all class path resources with the given name
                return findAllClassPathResources(locationPattern.substring(CLASSPATH_ALL_URL_PREFIX.length()));
            }
        } else {
            // Generally only look for a pattern after a prefix here,
            // and on Tomcat only after the "*/" separator for its "war:" protocol.
            int prefixEnd = (locationPattern.startsWith("war:") ? locationPattern.indexOf("*/") + 1 :
                    locationPattern.indexOf(':') + 1);
            if (getPathMatcher().isPattern(locationPattern.substring(prefixEnd))) {
                // a file pattern
                return findPathMatchingResources(locationPattern);
            } else {
                // a single resource with the given name
                return new Resource[]{getResourceLoader().getResource(locationPattern)};
            }
        }
    }

    /**
     * Find all class location resources with the given location via the ClassLoader.
     * Delegates to {@link #doFindAllClassPathResources(String)}.
     *
     * @param location the absolute path within the classpath
     * @return the result as Resource array
     * @throws IOException in case of I/O errors
     * @see ClassLoader#getResources
     * @see #convertClassLoaderUrl
     */
    protected Resource[] findAllClassPathResources(String location) throws IOException {
        String path = location;
        if (path.startsWith("/")) {
            path = path.substring(1);
        }
        Set<Resource> result = doFindAllClassPathResources(path);
        if (LOGGER.isTraceEnabled()) {
            LOGGER.trace("Resolved classpath location [" + location + "] to resources " + result);
        }
        return result.toArray(new Resource[0]);
    }

    /**
     * Find all class location resources with the given path via the ClassLoader.
     * Called by {@link #findAllClassPathResources(String)}.
     *
     * @param path the absolute path within the classpath (never a leading slash)
     * @return a mutable Set of matching Resource instances
     * @since 4.1.1
     */
    protected Set<Resource> doFindAllClassPathResources(String path) throws IOException {
        Set<Resource> result = new LinkedHashSet<>(16);
        ClassLoader cl = getClassLoader();
        Enumeration<URL> resourceUrls = (cl != null ? cl.getResources(path) : ClassLoader.getSystemResources(path));
        while (resourceUrls.hasMoreElements()) {
            URL url = resourceUrls.nextElement();
            result.add(convertClassLoaderUrl(url));
        }
        if (!StringUtils.hasLength(path)) {
            // The above result is likely to be incomplete, i.e. only containing file system references.
            // We need to have pointers to each of the jar files on the classpath as well...
            addAllClassLoaderJarRoots(cl, result);
        }
        return result;
    }

    /**
     * Convert the given URL as returned from the ClassLoader into a {@link Resource}.
     *
     * <p>The default implementation simply creates a {@link UrlResource} instance.
     *
     * @param url a URL as returned from the ClassLoader
     * @return the corresponding Resource object
     * @see ClassLoader#getResources
     * @see Resource
     */
    protected Resource convertClassLoaderUrl(URL url) {
        return new UrlResource(url);
    }

    /**
     * Search all {@link URLClassLoader} URLs for jar file references and add them to the
     * given set of resources in the form of pointers to the root of the jar file content.
     *
     * @param classLoader the ClassLoader to search (including its ancestors)
     * @param result      the set of resources to add jar roots to
     * @since 4.1.1
     */
    protected void addAllClassLoaderJarRoots(ClassLoader classLoader, Set<Resource> result) {
        if (classLoader instanceof URLClassLoader) {
            try {
                for (URL url : ((URLClassLoader) classLoader).getURLs()) {
                    try {
                        UrlResource jarResource = (ResourceUtils.URL_PROTOCOL_JAR.equals(url.getProtocol())
                                ? new UrlResource(url) :
                                new UrlResource(ResourceUtils.JAR_URL_PREFIX + url + ResourceUtils.JAR_URL_SEPARATOR));
                        if (jarResource.exists()) {
                            result.add(jarResource);
                        }
                    } catch (MalformedURLException ex) {
                        if (LOGGER.isDebugEnabled()) {
                            LOGGER.debug("Cannot search for matching files underneath [" + url
                                    + "] because it cannot be converted to a valid 'jar:' URL: " + ex.getMessage());
                        }
                    }
                }
            } catch (Exception ex) {
                if (LOGGER.isDebugEnabled()) {
                    LOGGER.debug("Cannot introspect jar files since ClassLoader [" + classLoader
                            + "] does not support 'getURLs()': " + ex);
                }
            }
        }

        if (classLoader == ClassLoader.getSystemClassLoader()) {
            // "java.class.path" manifest evaluation...
            addClassPathManifestEntries(result);
        }

        if (classLoader != null) {
            try {
                // Hierarchy traversal...
                addAllClassLoaderJarRoots(classLoader.getParent(), result);
            } catch (Exception ex) {
                if (LOGGER.isDebugEnabled()) {
                    LOGGER.debug("Cannot introspect jar files in parent ClassLoader since [" + classLoader
                            + "] does not support 'getParent()': " + ex);
                }
            }
        }
    }

    /**
     * Determine jar file references from the "java.class.path." manifest property and add them
     * to the given set of resources in the form of pointers to the root of the jar file content.
     *
     * @param result the set of resources to add jar roots to
     * @since 4.3
     */
    protected void addClassPathManifestEntries(Set<Resource> result) {
        try {
            String javaClassPathProperty = System.getProperty("java.class.path");
            for (String path : StringUtils.delimitedListToStringArray(
                    javaClassPathProperty, System.getProperty("path.separator"))) {
                try {
                    String filePath = new File(path).getAbsolutePath();
                    int prefixIndex = filePath.indexOf(':');
                    if (prefixIndex == 1) {
                        // Possibly "c:" drive prefix on Windows, to be upper-cased for proper duplicate detection
                        filePath = StringUtils.capitalize(filePath);
                    }
                    // # can appear in directories/filenames, java.net.URL should not treat it as a fragment
                    filePath = StringUtils.replace(filePath, "#", "%23");
                    // Build URL that points to the root of the jar file
                    UrlResource jarResource = new UrlResource(ResourceUtils.JAR_URL_PREFIX
                            + ResourceUtils.FILE_URL_PREFIX + filePath + ResourceUtils.JAR_URL_SEPARATOR);
                    // Potentially overlapping with URLClassLoader.getURLs() result above!
                    if (!result.contains(jarResource) && !hasDuplicate(filePath, result) && jarResource.exists()) {
                        result.add(jarResource);
                    }
                } catch (MalformedURLException ex) {
                    if (LOGGER.isDebugEnabled()) {
                        LOGGER.debug("Cannot search for matching files underneath [" + path
                                + "] because it cannot be converted to a valid 'jar:' URL: " + ex.getMessage());
                    }
                }
            }
        } catch (Exception ex) {
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("Failed to evaluate 'java.class.path' manifest entries: " + ex);
            }
        }
    }

    /**
     * Check whether the given file path has a duplicate but differently structured entry
     * in the existing result, i.e. with or without a leading slash.
     *
     * @param filePath the file path (with or without a leading slash)
     * @param result   the current result
     * @return {@code true} if there is a duplicate (i.e. to ignore the given file path),
     * {@code false} to proceed with adding a corresponding resource to the current result
     */
    private boolean hasDuplicate(String filePath, Set<Resource> result) {
        if (result.isEmpty()) {
            return false;
        }
        String duplicatePath = (filePath.startsWith("/") ? filePath.substring(1) : "/" + filePath);
        try {
            return result.contains(new UrlResource(ResourceUtils.JAR_URL_PREFIX + ResourceUtils.FILE_URL_PREFIX
                    + duplicatePath + ResourceUtils.JAR_URL_SEPARATOR));
        } catch (MalformedURLException ex) {
            // Ignore: just for testing against duplicate.
            return false;
        }
    }

    /**
     * Find all resources that match the given location pattern via the
     * Ant-style PathMatcher. Supports resources in jar files and zip files
     * and in the file system.
     *
     * @param locationPattern the location pattern to match
     * @return the result as Resource array
     * @throws IOException in case of I/O errors
     * @see #doFindPathMatchingJarResources
     * @see #doFindPathMatchingFileResources
     * @see PathMatcher
     */
    protected Resource[] findPathMatchingResources(String locationPattern) throws IOException {
        String rootDirPath = determineRootDir(locationPattern);
        String subPattern = locationPattern.substring(rootDirPath.length());
        Resource[] rootDirResources = getResources(rootDirPath);
        Set<Resource> result = new LinkedHashSet<>(16);
        for (Resource rootDirResource : rootDirResources) {
            rootDirResource = resolveRootDirResource(rootDirResource);
            URL rootDirUrl = rootDirResource.getUrl();
            if (equinoxResolveMethod != null && rootDirUrl.getProtocol().startsWith("bundle")) {
                URL resolvedUrl = (URL) ReflectUtils.invokeMethod(equinoxResolveMethod, null, rootDirUrl);
                if (resolvedUrl != null) {
                    rootDirUrl = resolvedUrl;
                }
                rootDirResource = new UrlResource(rootDirUrl);
            }
            if (rootDirUrl.getProtocol().startsWith(ResourceUtils.URL_PROTOCOL_VFS)) {
                result.addAll(VfsResourceMatchingDelegate.findMatchingResources(rootDirUrl, subPattern, getPathMatcher()));
            } else if (ResourceUtils.isJarUrl(rootDirUrl) || isJarResource(rootDirResource)) {
                result.addAll(doFindPathMatchingJarResources(rootDirResource, rootDirUrl, subPattern));
            } else {
                result.addAll(doFindPathMatchingFileResources(rootDirResource, subPattern));
            }
        }
        if (LOGGER.isTraceEnabled()) {
            LOGGER.trace("Resolved location pattern [" + locationPattern + "] to resources " + result);
        }
        return result.toArray(new Resource[0]);
    }

    /**
     * Determine the root directory for the given location.
     *
     * <p>Used for determining the starting point for file matching,
     * resolving the root directory location to a {@code java.io.File}
     * and passing it into {@code retrieveMatchingFiles}, with the
     * remainder of the location as pattern.
     *
     * <p>Will return "/WEB-INF/" for the pattern "/WEB-INF/*.xml",
     * for example.
     *
     * @param location the location to check
     * @return the part of the location that denotes the root directory
     * @see #retrieveMatchingFiles
     */
    protected String determineRootDir(String location) {
        int prefixEnd = location.indexOf(':') + 1;
        int rootDirEnd = location.length();
        while (rootDirEnd > prefixEnd && getPathMatcher().isPattern(location.substring(prefixEnd, rootDirEnd))) {
            rootDirEnd = location.lastIndexOf('/', rootDirEnd - 2) + 1;
        }
        if (rootDirEnd == 0) {
            rootDirEnd = prefixEnd;
        }
        return location.substring(0, rootDirEnd);
    }

    /**
     * Resolve the specified resource for path matching.
     *
     * <p>By default, Equinox OSGi "bundleresource:" / "bundleentry:" URL will be
     * resolved into a standard jar file URL that be traversed using Spring's
     * standard jar file traversal algorithm. For any preceding custom resolution,
     * override this method and replace the resource handle accordingly.
     *
     * @param original the resource to resolve
     * @return the resolved resource (may be identical to the passed-in resource)
     * @throws IOException in case of resolution failure
     */
    protected Resource resolveRootDirResource(Resource original) throws IOException {
        return original;
    }

    /**
     * Return whether the given resource handle indicates a jar resource
     * that the {@code doFindPathMatchingJarResources} method can handle.
     *
     * <p>By default, the URL protocols "jar", "zip", "vfszip and "wsjar"
     * will be treated as jar resources. This template method allows for
     * detecting further kinds of jar-like resources, e.g. through
     * {@code instanceof} checks on the resource handle type.
     *
     * @param resource the resource handle to check
     *                 (usually the root directory to start path matching from)
     * @see #doFindPathMatchingJarResources
     * @see ResourceUtils#isJarUrl
     */
    protected boolean isJarResource(Resource resource) throws IOException {
        return false;
    }

    /**
     * Find all resources in jar files that match the given location pattern
     * via the Ant-style PathMatcher.
     *
     * @param rootDirResource the root directory as Resource
     * @param rootDirUrl      the pre-resolved root directory URL
     * @param subPattern      the sub pattern to match (below the root directory)
     * @return a mutable Set of matching Resource instances
     * @throws IOException in case of I/O errors
     * @see PathMatcher
     * @since 4.3
     */
    protected Set<Resource> doFindPathMatchingJarResources(Resource rootDirResource, URL rootDirUrl, String subPattern)
            throws IOException {

        URLConnection con = rootDirUrl.openConnection();
        JarFile jarFile;
        String jarFileUrl;
        String rootEntryPath;
        boolean closeJarFile;

        if (con instanceof JarURLConnection) {
            // Should usually be the case for traditional JAR files.
            JarURLConnection jarCon = (JarURLConnection) con;
            ResourceUtils.useCachesIfNecessary(jarCon);
            jarFile = jarCon.getJarFile();
            jarFileUrl = jarCon.getJarFileURL().toExternalForm();
            JarEntry jarEntry = jarCon.getJarEntry();
            rootEntryPath = (jarEntry != null ? jarEntry.getName() : "");
            closeJarFile = !jarCon.getUseCaches();
        } else {
            // No JarURLConnection -> need to resort to URL file parsing.
            // We'll assume URLs of the format "jar:path!/entry", with the protocol
            // being arbitrary as long as following the entry format.
            // We'll also handle paths with and without leading "file:" prefix.
            String urlFile = rootDirUrl.getFile();
            try {
                int separatorIndex = urlFile.indexOf(ResourceUtils.WAR_URL_SEPARATOR);
                if (separatorIndex == -1) {
                    separatorIndex = urlFile.indexOf(ResourceUtils.JAR_URL_SEPARATOR);
                }
                if (separatorIndex != -1) {
                    jarFileUrl = urlFile.substring(0, separatorIndex);
                    rootEntryPath = urlFile.substring(separatorIndex + 2);  // both separators are 2 chars
                    jarFile = getJarFile(jarFileUrl);
                } else {
                    jarFile = new JarFile(urlFile);
                    jarFileUrl = urlFile;
                    rootEntryPath = "";
                }
                closeJarFile = true;
            } catch (ZipException ex) {
                if (LOGGER.isDebugEnabled()) {
                    LOGGER.debug("Skipping invalid jar classpath entry [" + urlFile + "]");
                }
                return Collections.emptySet();
            }
        }

        try {
            if (LOGGER.isTraceEnabled()) {
                LOGGER.trace("Looking for matching resources in jar file [" + jarFileUrl + "]");
            }
            if (StringUtils.hasLength(rootEntryPath) && !rootEntryPath.endsWith("/")) {
                // Root entry path must end with slash to allow for proper matching.
                // The Sun JRE does not return a slash here, but BEA JRockit does.
                rootEntryPath = rootEntryPath + "/";
            }
            Set<Resource> result = new LinkedHashSet<>(8);
            for (Enumeration<JarEntry> entries = jarFile.entries(); entries.hasMoreElements(); ) {
                JarEntry entry = entries.nextElement();
                String entryPath = entry.getName();
                if (entryPath.startsWith(rootEntryPath)) {
                    String relativePath = entryPath.substring(rootEntryPath.length());
                    if (getPathMatcher().match(subPattern, relativePath)) {
                        result.add(rootDirResource.createRelative(relativePath));
                    }
                }
            }
            return result;
        } finally {
            if (closeJarFile) {
                jarFile.close();
            }
        }
    }

    /**
     * Resolve the given jar file URL into a JarFile object.
     */
    protected JarFile getJarFile(String jarFileUrl) throws IOException {
        if (jarFileUrl.startsWith(ResourceUtils.FILE_URL_PREFIX)) {
            try {
                return new JarFile(ResourceUtils.toUri(jarFileUrl).getSchemeSpecificPart());
            } catch (URISyntaxException ex) {
                // Fallback for URLs that are not valid URIs (should hardly ever happen).
                return new JarFile(jarFileUrl.substring(ResourceUtils.FILE_URL_PREFIX.length()));
            }
        } else {
            return new JarFile(jarFileUrl);
        }
    }

    /**
     * Find all resources in the file system that match the given location pattern
     * via the Ant-style PathMatcher.
     *
     * @param rootDirResource the root directory as Resource
     * @param subPattern      the sub pattern to match (below the root directory)
     * @return a mutable Set of matching Resource instances
     * @throws IOException in case of I/O errors
     * @see #retrieveMatchingFiles
     * @see PathMatcher
     */
    protected Set<Resource> doFindPathMatchingFileResources(Resource rootDirResource, String subPattern)
            throws IOException {

        File rootDir;
        try {
            rootDir = rootDirResource.getFile().getAbsoluteFile();
        } catch (FileNotFoundException ex) {
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("Cannot search for matching files underneath " + rootDirResource
                        + " in the file system: " + ex.getMessage());
            }
            return Collections.emptySet();
        } catch (Exception ex) {
            if (LOGGER.isInfoEnabled()) {
                LOGGER.info("Failed to resolve " + rootDirResource + " in the file system: " + ex);
            }
            return Collections.emptySet();
        }
        return doFindMatchingFileSystemResources(rootDir, subPattern);
    }

    /**
     * Find all resources in the file system that match the given location pattern
     * via the Ant-style PathMatcher.
     *
     * @param rootDir    the root directory in the file system
     * @param subPattern the sub pattern to match (below the root directory)
     * @return a mutable Set of matching Resource instances
     * @throws IOException in case of I/O errors
     * @see #retrieveMatchingFiles
     * @see PathMatcher
     */
    protected Set<Resource> doFindMatchingFileSystemResources(File rootDir, String subPattern) throws IOException {
        if (LOGGER.isTraceEnabled()) {
            LOGGER.trace("Looking for matching resources in directory tree [" + rootDir.getPath() + "]");
        }
        Set<File> matchingFiles = retrieveMatchingFiles(rootDir, subPattern);
        Set<Resource> result = new LinkedHashSet<>(matchingFiles.size());
        for (File file : matchingFiles) {
            result.add(new FileSystemResource(file));
        }
        return result;
    }

    /**
     * Retrieve files that match the given path pattern,
     * checking the given directory and its subdirectories.
     *
     * @param rootDir the directory to start from
     * @param pattern the pattern to match against,
     *                relative to the root directory
     * @return a mutable Set of matching Resource instances
     * @throws IOException if directory contents could not be retrieved
     */
    protected Set<File> retrieveMatchingFiles(File rootDir, String pattern) throws IOException {
        if (!rootDir.exists()) {
            // Silently skip non-existing directories.
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("Skipping [" + rootDir.getAbsolutePath() + "] because it does not exist");
            }
            return Collections.emptySet();
        }
        if (!rootDir.isDirectory()) {
            // Complain louder if it exists but is no directory.
            if (LOGGER.isInfoEnabled()) {
                LOGGER.info("Skipping [" + rootDir.getAbsolutePath() + "] because it does not denote a directory");
            }
            return Collections.emptySet();
        }
        if (!rootDir.canRead()) {
            if (LOGGER.isInfoEnabled()) {
                LOGGER.info("Skipping search for matching files underneath directory [" + rootDir.getAbsolutePath()
                        + "] because the application is not allowed to read the directory");
            }
            return Collections.emptySet();
        }
        String fullPattern = StringUtils.replace(rootDir.getAbsolutePath(), File.separator, "/");
        if (!pattern.startsWith("/")) {
            fullPattern += "/";
        }
        fullPattern = fullPattern + StringUtils.replace(pattern, File.separator, "/");
        Set<File> result = new LinkedHashSet<>(8);
        doRetrieveMatchingFiles(fullPattern, rootDir, result);
        return result;
    }

    /**
     * Recursively retrieve files that match the given pattern,
     * adding them to the given result list.
     *
     * @param fullPattern the pattern to match against,
     *                    with prepended root directory path
     * @param dir         the current directory
     * @param result      the Set of matching File instances to add to
     * @throws IOException if directory contents could not be retrieved
     */
    protected void doRetrieveMatchingFiles(String fullPattern, File dir, Set<File> result) throws IOException {
        if (LOGGER.isTraceEnabled()) {
            LOGGER.trace("Searching directory [" + dir.getAbsolutePath()
                    + "] for files matching pattern [" + fullPattern + "]");
        }
        for (File content : listDirectory(dir)) {
            String currPath = StringUtils.replace(content.getAbsolutePath(), File.separator, "/");
            if (content.isDirectory() && getPathMatcher().matchStart(fullPattern, currPath + "/")) {
                if (!content.canRead()) {
                    if (LOGGER.isDebugEnabled()) {
                        LOGGER.debug("Skipping subdirectory [" + dir.getAbsolutePath()
                                + "] because the application is not allowed to read the directory");
                    }
                } else {
                    doRetrieveMatchingFiles(fullPattern, content, result);
                }
            }
            if (getPathMatcher().match(fullPattern, currPath)) {
                result.add(content);
            }
        }
    }

    /**
     * Determine a sorted list of files in the given directory.
     *
     * @param dir the directory to introspect
     * @return the sorted list of files (by default in alphabetical order)
     * @see File#listFiles()
     * @since 5.1
     */
    protected File[] listDirectory(File dir) {
        File[] files = dir.listFiles();
        if (files == null) {
            if (LOGGER.isInfoEnabled()) {
                LOGGER.info("Could not retrieve contents of directory [" + dir.getAbsolutePath() + "]");
            }
            return new File[0];
        }
        Arrays.sort(files, Comparator.comparing(File::getName));
        return files;
    }

    /**
     * Inner delegate class, avoiding a hard JBoss VFS API dependency at runtime.
     */
    private static class VfsResourceMatchingDelegate {

        public static Set<Resource> findMatchingResources(
                URL rootDirUrl, String locationPattern, PathMatcher pathMatcher) throws IOException {

            Object root = VfsPatternUtils.findRoot(rootDirUrl);
            PatternVirtualFileVisitor visitor =
                    new PatternVirtualFileVisitor(VfsPatternUtils.getPath(root), locationPattern, pathMatcher);
            VfsPatternUtils.visit(root, visitor);
            return visitor.getResources();
        }
    }


    /**
     * VFS visitor for path matching purposes.
     */
    @SuppressWarnings("unused")
    private static class PatternVirtualFileVisitor implements InvocationHandler {

        private final String subPattern;

        private final PathMatcher pathMatcher;

        private final String rootPath;

        private final Set<Resource> resources = new LinkedHashSet<>();

        public PatternVirtualFileVisitor(String rootPath, String subPattern, PathMatcher pathMatcher) {
            this.subPattern = subPattern;
            this.pathMatcher = pathMatcher;
            this.rootPath = (rootPath.isEmpty() || rootPath.endsWith("/") ? rootPath : rootPath + "/");
        }

        @Override

        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            String methodName = method.getName();
            if (Object.class == method.getDeclaringClass()) {
                if ("equals".equals(methodName)) {
                    // Only consider equal when proxies are identical.
                    return (proxy == args[0]);
                } else if ("hashCode".equals(methodName)) {
                    return System.identityHashCode(proxy);
                }
            } else if ("getAttributes".equals(methodName)) {
                return getAttributes();
            } else if ("visit".equals(methodName)) {
                visit(args[0]);
                return null;
            } else if ("toString".equals(methodName)) {
                return toString();
            }

            throw new IllegalStateException("Unexpected method invocation: " + method);
        }

        public void visit(Object vfsResource) {
            if (this.pathMatcher.match(this.subPattern,
                    VfsPatternUtils.getPath(vfsResource).substring(this.rootPath.length()))) {
                this.resources.add(new VfsResource(vfsResource));
            }
        }

        public Object getAttributes() {
            return VfsPatternUtils.getVisitorAttributes();
        }

        public Set<Resource> getResources() {
            return this.resources;
        }

        public int size() {
            return this.resources.size();
        }

        @Override
        public String toString() {
            return "sub-pattern: " + this.subPattern + ", resources: " + this.resources;
        }
    }

}
