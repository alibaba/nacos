/*
 * Copyright 2002-2019 the original author or authors.
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

import com.alibaba.nacos.common.utils.AbstractAssert;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.URL;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;

/**
 * Copy from https://github.com/spring-projects/spring-framework.git, with less modifications
 * {@link Resource} implementation for {@link Path} handles,
 * performing all operations and transformations via the {@code Path} API.
 * Supports resolution as a {@link File} and also as a {@link URL}.
 * Implements the extended {@link WritableResource} interface.
 *
 * <p>Note: As of 5.1, {@link Path} support is also available
 * in {@link FileSystemResource#FileSystemResource(Path) FileSystemResource},
 * applying Spring's standard String-based path transformations but
 * performing all operations via the {@link Files} API.
 * This {@code PathResource} is effectively a pure {@code java.nio.path.Path}
 * based alternative with different {@code createRelative} behavior.
 *
 * @author Philippe Marschall
 * @author Juergen Hoeller
 * @see Path
 * @see Files
 * @see FileSystemResource
 * @since 4.0
 */
public class PathResource extends AbstractResource implements WritableResource {

    private final Path path;

    /**
     * Create a new PathResource from a Path handle.
     *
     * <p>Note: Unlike {@link FileSystemResource}, when building relative resources
     * via {@link #createRelative}, the relative path will be built <i>underneath</i>
     * the given root: e.g. Paths.get("C:/dir1/"), relative path "dir2" &rarr; "C:/dir1/dir2"!
     *
     * @param path a Path handle
     */
    public PathResource(Path path) {
        AbstractAssert.notNull(path, "Path must not be null");
        this.path = path.normalize();
    }

    /**
     * Create a new PathResource from a Path handle.
     *
     * <p>Note: Unlike {@link FileSystemResource}, when building relative resources
     * via {@link #createRelative}, the relative path will be built <i>underneath</i>
     * the given root: e.g. Paths.get("C:/dir1/"), relative path "dir2" &rarr; "C:/dir1/dir2"!
     *
     * @param path a path
     * @see Paths#get(String, String...)
     */
    public PathResource(String path) {
        AbstractAssert.notNull(path, "Path must not be null");
        this.path = Paths.get(path).normalize();
    }

    /**
     * Create a new PathResource from a Path handle.
     *
     * <p>Note: Unlike {@link FileSystemResource}, when building relative resources
     * via {@link #createRelative}, the relative path will be built <i>underneath</i>
     * the given root: e.g. Paths.get("C:/dir1/"), relative path "dir2" &rarr; "C:/dir1/dir2"!
     *
     * @param uri a path URI
     * @see Paths#get(URI)
     */
    public PathResource(URI uri) {
        AbstractAssert.notNull(uri, "URI must not be null");
        this.path = Paths.get(uri).normalize();
    }

    /**
     * Return the file path for this resource.
     */
    public final String getPath() {
        return this.path.toString();
    }

    /**
     * This implementation returns whether the underlying file exists.
     *
     * @see Files#exists(Path, LinkOption...)
     */
    @Override
    public boolean exists() {
        return Files.exists(this.path);
    }

    /**
     * This implementation checks whether the underlying file is marked as readable
     * (and corresponds to an actual file with content, not to a directory).
     *
     * @see Files#isReadable(Path)
     * @see Files#isDirectory(Path, LinkOption...)
     */
    @Override
    public boolean isReadable() {
        return (Files.isReadable(this.path) && !Files.isDirectory(this.path));
    }

    /**
     * This implementation opens a InputStream for the underlying file.
     *
     * @see java.nio.file.spi.FileSystemProvider#newInputStream(Path, OpenOption...)
     */
    @Override
    public InputStream getInputStream() throws IOException {
        if (!exists()) {
            throw new FileNotFoundException(getPath() + " (no such file or directory)");
        }
        if (Files.isDirectory(this.path)) {
            throw new FileNotFoundException(getPath() + " (is a directory)");
        }
        return Files.newInputStream(this.path);
    }

    /**
     * This implementation checks whether the underlying file is marked as writable
     * (and corresponds to an actual file with content, not to a directory).
     *
     * @see Files#isWritable(Path)
     * @see Files#isDirectory(Path, LinkOption...)
     */
    @Override
    public boolean isWritable() {
        return (Files.isWritable(this.path) && !Files.isDirectory(this.path));
    }

    /**
     * This implementation opens a OutputStream for the underlying file.
     *
     * @see java.nio.file.spi.FileSystemProvider#newOutputStream(Path, OpenOption...)
     */
    @Override
    public OutputStream getOutputStream() throws IOException {
        if (Files.isDirectory(this.path)) {
            throw new FileNotFoundException(getPath() + " (is a directory)");
        }
        return Files.newOutputStream(this.path);
    }

    /**
     * This implementation returns a URL for the underlying file.
     *
     * @see Path#toUri()
     * @see URI#toURL()
     */
    @Override
    public URL getUrl() throws IOException {
        return this.path.toUri().toURL();
    }

    /**
     * This implementation returns a URI for the underlying file.
     *
     * @see Path#toUri()
     */
    @Override
    public URI getUri() throws IOException {
        return this.path.toUri();
    }

    /**
     * This implementation always indicates a file.
     */
    @Override
    public boolean isFile() {
        return true;
    }

    /**
     * This implementation returns the underlying File reference.
     */
    @Override
    public File getFile() throws IOException {
        try {
            return this.path.toFile();
        } catch (UnsupportedOperationException ex) {
            // Only paths on the default file system can be converted to a File:
            // Do exception translation for cases where conversion is not possible.
            throw new FileNotFoundException(this.path + " cannot be resolved to absolute file path");
        }
    }

    /**
     * This implementation opens a Channel for the underlying file.
     *
     * @see Files#newByteChannel(Path, OpenOption...)
     */
    @Override
    public ReadableByteChannel readableChannel() throws IOException {
        try {
            return Files.newByteChannel(this.path, StandardOpenOption.READ);
        } catch (NoSuchFileException ex) {
            throw new FileNotFoundException(ex.getMessage());
        }
    }

    /**
     * This implementation opens a Channel for the underlying file.
     *
     * @see Files#newByteChannel(Path, OpenOption...)
     */
    @Override
    public WritableByteChannel writableChannel() throws IOException {
        return Files.newByteChannel(this.path, StandardOpenOption.WRITE);
    }

    /**
     * This implementation returns the underlying file's length.
     */
    @Override
    public long contentLength() throws IOException {
        return Files.size(this.path);
    }

    /**
     * This implementation returns the underlying File's timestamp.
     *
     * @see Files#getLastModifiedTime(Path, LinkOption...)
     */
    @Override
    public long lastModified() throws IOException {
        // We can not use the superclass method since it uses conversion to a File and
        // only a Path on the default file system can be converted to a File...
        return Files.getLastModifiedTime(this.path).toMillis();
    }

    /**
     * This implementation creates a PathResource, applying the given path
     * relative to the path of the underlying file of this resource descriptor.
     *
     * @see Path#resolve(String)
     */
    @Override
    public Resource createRelative(String relativePath) {
        return new PathResource(this.path.resolve(relativePath));
    }

    /**
     * This implementation returns the name of the file.
     *
     * @see Path#getFileName()
     */
    @Override
    public String getFilename() {
        return this.path.getFileName().toString();
    }

    @Override
    public String getDescription() {
        return "path [" + this.path.toAbsolutePath() + "]";
    }


    /**
     * This implementation compares the underlying Path references.
     */
    @Override
    public boolean equals(Object other) {
        return (this == other || (other instanceof PathResource
                && this.path.equals(((PathResource) other).path)));
    }

    /**
     * This implementation returns the hash code of the underlying Path reference.
     */
    @Override
    public int hashCode() {
        return this.path.hashCode();
    }

}
