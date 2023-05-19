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

import com.alibaba.nacos.common.packagescan.util.NestedIoException;
import com.alibaba.nacos.common.packagescan.util.ResourceUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;

/**
 * Copy from https://github.com/spring-projects/spring-framework.git, with less modifications
 * Convenience base class for {@link Resource} implementations,
 * pre-implementing typical behavior.
 *
 * <p>The "exists" method will check whether a File or InputStream can
 * be opened; "isOpen" will always return false; "getURL" and "getFile"
 * throw an exception; and "toString" will return the description.
 *
 * @author Juergen Hoeller
 * @author Sam Brannen
 * @since 28.12.2003
 */
public abstract class AbstractResource implements Resource {

    /**
     * This implementation checks whether a File can be opened,
     * falling back to whether an InputStream can be opened.
     * This will cover both directories and content resources.
     */
    @Override
    public boolean exists() {
        // Try file existence: can we find the file in the file system?
        if (isFile()) {
            try {
                return getFile().exists();
            } catch (IOException ex) {
                Logger logger = LoggerFactory.getLogger(getClass());
                if (logger.isDebugEnabled()) {
                    logger.debug("Could not retrieve File for existence check of " + getDescription(), ex);
                }
            }
        }
        // Fall back to stream existence: can we open the stream?
        try {
            getInputStream().close();
            return true;
        } catch (Throwable ex) {
            Logger logger = LoggerFactory.getLogger(getClass());
            if (logger.isDebugEnabled()) {
                logger.debug("Could not retrieve InputStream for existence check of " + getDescription(), ex);
            }
            return false;
        }
    }

    /**
     * This implementation always returns {@code true} for a resource
     * that {@link #exists() exists} (revised as of 5.1).
     */
    @Override
    public boolean isReadable() {
        return exists();
    }

    /**
     * This implementation always returns {@code false}.
     */
    @Override
    public boolean isOpen() {
        return false;
    }

    /**
     * This implementation always returns {@code false}.
     */
    @Override
    public boolean isFile() {
        return false;
    }

    /**
     * This implementation throws a FileNotFoundException, assuming
     * that the resource cannot be resolved to a URL.
     */
    @Override
    public URL getUrl() throws IOException {
        throw new FileNotFoundException(getDescription() + " cannot be resolved to URL");
    }

    /**
     * This implementation builds a URI based on the URL returned
     * by {@link #getUrl()}.
     */
    @Override
    public URI getUri() throws IOException {
        URL url = getUrl();
        try {
            return ResourceUtils.toUri(url);
        } catch (URISyntaxException ex) {
            throw new NestedIoException("Invalid URI [" + url + "]", ex);
        }
    }

    /**
     * This implementation throws a FileNotFoundException, assuming
     * that the resource cannot be resolved to an absolute file path.
     */
    @Override
    public File getFile() throws IOException {
        throw new FileNotFoundException(getDescription() + " cannot be resolved to absolute file path");
    }

    /**
     * This implementation returns {@link Channels#newChannel(InputStream)}
     * with the result of {@link #getInputStream()}.
     * This is the same as in {@link Resource}'s corresponding default method
     * but mirrored here for efficient JVM-level dispatching in a class hierarchy.
     */
    @Override
    public ReadableByteChannel readableChannel() throws IOException {
        return Channels.newChannel(getInputStream());
    }

    /**
     * This method reads the entire InputStream to determine the content length.
     * For a custom sub-class of {@code InputStreamResource}, we strongly
     * recommend overriding this method with a more optimal implementation, e.g.
     * checking File length, or possibly simply returning -1 if the stream can
     * only be read once.
     *
     * @see #getInputStream()
     */
    @Override
    public long contentLength() throws IOException {
        InputStream is = getInputStream();
        try {
            long size = 0;
            byte[] buf = new byte[256];
            int read;
            while ((read = is.read(buf)) != -1) {
                size += read;
            }
            return size;
        } finally {
            try {
                is.close();
            } catch (IOException ex) {
                Logger logger = LoggerFactory.getLogger(getClass());
                if (logger.isDebugEnabled()) {
                    logger.debug("Could not close content-length InputStream for " + getDescription(), ex);
                }
            }
        }
    }

    /**
     * This implementation checks the timestamp of the underlying File,
     * if available.
     *
     * @see #getFileForLastModifiedCheck()
     */
    @Override
    public long lastModified() throws IOException {
        File fileToCheck = getFileForLastModifiedCheck();
        long lastModified = fileToCheck.lastModified();
        if (lastModified == 0L && !fileToCheck.exists()) {
            throw new FileNotFoundException(getDescription()
                    + " cannot be resolved in the file system for checking its last-modified timestamp");
        }
        return lastModified;
    }

    /**
     * Determine the File to use for timestamp checking.
     * The default implementation delegates to {@link #getFile()}.
     *
     * @return the File to use for timestamp checking (never {@code null})
     * @throws FileNotFoundException if the resource cannot be resolved as
     *                               an absolute file path, i.e. is not available in a file system
     * @throws IOException           in case of general resolution/reading failures
     */
    protected File getFileForLastModifiedCheck() throws IOException {
        return getFile();
    }

    /**
     * This implementation throws a FileNotFoundException, assuming
     * that relative resources cannot be created for this resource.
     */
    @Override
    public Resource createRelative(String relativePath) throws IOException {
        throw new FileNotFoundException("Cannot create a relative resource for " + getDescription());
    }

    /**
     * This implementation always returns {@code null},
     * assuming that this resource type does not have a filename.
     */
    @Override

    public String getFilename() {
        return null;
    }


    /**
     * This implementation compares description strings.
     *
     * @see #getDescription()
     */
    @Override
    public boolean equals(Object other) {
        return (this == other || (other instanceof Resource
                && ((Resource) other).getDescription().equals(getDescription())));
    }

    /**
     * This implementation returns the description's hash code.
     *
     * @see #getDescription()
     */
    @Override
    public int hashCode() {
        return getDescription().hashCode();
    }

    /**
     * This implementation returns the description of this resource.
     *
     * @see #getDescription()
     */
    @Override
    public String toString() {
        return getDescription();
    }

}
