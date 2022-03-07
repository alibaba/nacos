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
import com.alibaba.nacos.common.utils.StringUtils;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.URL;
import java.nio.channels.FileChannel;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/**
 * Copy from https://github.com/spring-projects/spring-framework.git, with less modifications
 * {@link Resource} implementation for {@code java.io.File} and
 * {@code java.nio.file.Path} handles with a file system target.
 * Supports resolution as a {@code File} and also as a {@code URL}.
 * Implements the extended {@link WritableResource} interface.
 *
 * <p>Note: As of Spring Framework 5.0, this {@link Resource} implementation uses
 * NIO.2 API for read/write interactions. As of 5.1, it may be constructed with a
 * {@link Path} handle in which case it will perform all file system
 * interactions via NIO.2, only resorting to {@link File} on {@link #getFile()}.
 *
 * @author Juergen Hoeller
 * @see #FileSystemResource(String)
 * @see #FileSystemResource(File)
 * @see #FileSystemResource(Path)
 * @see File
 * @see Files
 * @since 28.12.2003
 */
public class FileSystemResource extends AbstractResource implements WritableResource {

    private final String path;

    private final File file;

    private final Path filePath;

    /**
     * Create a new {@code FileSystemResource} from a file path.
     *
     * <p>Note: When building relative resources via {@link #createRelative},
     * it makes a difference whether the specified resource base path here
     * ends with a slash or not. In the case of "C:/dir1/", relative paths
     * will be built underneath that root: e.g. relative path "dir2" &rarr;
     * "C:/dir1/dir2". In the case of "C:/dir1", relative paths will apply
     * at the same directory level: relative path "dir2" &rarr; "C:/dir2".
     *
     * @param path a file path
     * @see #FileSystemResource(Path)
     */
    public FileSystemResource(String path) {
        AbstractAssert.notNull(path, "Path must not be null");
        this.path = StringUtils.cleanPath(path);
        this.file = new File(path);
        this.filePath = this.file.toPath();
    }

    /**
     * Create a new {@code FileSystemResource} from a {@link File} handle.
     *
     * <p>Note: When building relative resources via {@link #createRelative},
     * the relative path will apply <i>at the same directory level</i>:
     * e.g. new File("C:/dir1"), relative path "dir2" &rarr; "C:/dir2"!
     * If you prefer to have relative paths built underneath the given root directory,
     * use the {@link #FileSystemResource(String) constructor with a file path}
     * to append a trailing slash to the root path: "C:/dir1/", which indicates
     * this directory as root for all relative paths.
     *
     * @param file a File handle
     * @see #FileSystemResource(Path)
     * @see #getFile()
     */
    public FileSystemResource(File file) {
        AbstractAssert.notNull(file, "File must not be null");
        this.path = StringUtils.cleanPath(file.getPath());
        this.file = file;
        this.filePath = file.toPath();
    }

    /**
     * Create a new {@code FileSystemResource} from a {@link Path} handle,
     * performing all file system interactions via NIO.2 instead of {@link File}.
     *
     *  <p>In contrast to {@link PathResource}, this variant strictly follows the
     * general {@link FileSystemResource} conventions, in particular in terms of
     * path cleaning and {@link #createRelative(String)} handling.
     *
     * <p>Note: When building relative resources via {@link #createRelative},
     * the relative path will apply <i>at the same directory level</i>:
     * e.g. Paths.get("C:/dir1"), relative path "dir2" &rarr; "C:/dir2"!
     * If you prefer to have relative paths built underneath the given root directory,
     * use the {@link #FileSystemResource(String) constructor with a file path}
     * to append a trailing slash to the root path: "C:/dir1/", which indicates
     * this directory as root for all relative paths. Alternatively, consider
     * using {@link PathResource#PathResource(Path)} for {@code java.nio.path.Path}
     * resolution in {@code createRelative}, always nesting relative paths.
     *
     * @param filePath a Path handle to a file
     * @see #FileSystemResource(File)
     * @since 5.1
     */
    public FileSystemResource(Path filePath) {
        AbstractAssert.notNull(filePath, "Path must not be null");
        this.path = StringUtils.cleanPath(filePath.toString());
        this.file = null;
        this.filePath = filePath;
    }

    /**
     * Create a new {@code FileSystemResource} from a {@link FileSystem} handle,
     * locating the specified path.
     *
     * <p>This is an alternative to {@link #FileSystemResource(String)},
     * performing all file system interactions via NIO.2 instead of {@link File}.
     *
     * @param fileSystem the FileSystem to locate the path within
     * @param path       a file path
     * @see #FileSystemResource(File)
     * @since 5.1.1
     */
    public FileSystemResource(FileSystem fileSystem, String path) {
        AbstractAssert.notNull(fileSystem, "FileSystem must not be null");
        AbstractAssert.notNull(path, "Path must not be null");
        this.path = StringUtils.cleanPath(path);
        this.file = null;
        this.filePath = fileSystem.getPath(this.path).normalize();
    }

    /**
     * Return the file path for this resource.
     */
    public final String getPath() {
        return this.path;
    }

    /**
     * This implementation returns whether the underlying file exists.
     *
     * @see File#exists()
     */
    @Override
    public boolean exists() {
        return (this.file != null ? this.file.exists() : Files.exists(this.filePath));
    }

    /**
     * This implementation checks whether the underlying file is marked as readable
     * (and corresponds to an actual file with content, not to a directory).
     *
     * @see File#canRead()
     * @see File#isDirectory()
     */
    @Override
    public boolean isReadable() {
        return (this.file != null ? this.file.canRead() && !this.file.isDirectory() :
                Files.isReadable(this.filePath) && !Files.isDirectory(this.filePath));
    }

    /**
     * This implementation opens a NIO file stream for the underlying file.
     *
     * @see java.io.FileInputStream
     */
    @Override
    public InputStream getInputStream() throws IOException {
        try {
            return Files.newInputStream(this.filePath);
        } catch (NoSuchFileException ex) {
            throw new FileNotFoundException(ex.getMessage());
        }
    }

    /**
     * This implementation checks whether the underlying file is marked as writable
     * (and corresponds to an actual file with content, not to a directory).
     *
     * @see File#canWrite()
     * @see File#isDirectory()
     */
    @Override
    public boolean isWritable() {
        return (this.file != null ? this.file.canWrite() && !this.file.isDirectory() :
                Files.isWritable(this.filePath) && !Files.isDirectory(this.filePath));
    }

    /**
     * This implementation opens a FileOutputStream for the underlying file.
     *
     * @see java.io.FileOutputStream
     */
    @Override
    public OutputStream getOutputStream() throws IOException {
        return Files.newOutputStream(this.filePath);
    }

    /**
     * This implementation returns a URL for the underlying file.
     *
     * @see File#toURI()
     */
    @Override
    public URL getUrl() throws IOException {
        return (this.file != null ? this.file.toURI().toURL() : this.filePath.toUri().toURL());
    }

    /**
     * This implementation returns a URI for the underlying file.
     *
     * @see File#toURI()
     */
    @Override
    public URI getUri() throws IOException {
        return (this.file != null ? this.file.toURI() : this.filePath.toUri());
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
    public File getFile() {
        return (this.file != null ? this.file : this.filePath.toFile());
    }

    /**
     * This implementation opens a FileChannel for the underlying file.
     *
     * @see FileChannel
     */
    @Override
    public ReadableByteChannel readableChannel() throws IOException {
        try {
            return FileChannel.open(this.filePath, StandardOpenOption.READ);
        } catch (NoSuchFileException ex) {
            throw new FileNotFoundException(ex.getMessage());
        }
    }

    /**
     * This implementation opens a FileChannel for the underlying file.
     *
     * @see FileChannel
     */
    @Override
    public WritableByteChannel writableChannel() throws IOException {
        return FileChannel.open(this.filePath, StandardOpenOption.WRITE);
    }

    /**
     * This implementation returns the underlying File/Path length.
     */
    @Override
    public long contentLength() throws IOException {
        if (this.file != null) {
            long length = this.file.length();
            if (length == 0L && !this.file.exists()) {
                throw new FileNotFoundException(getDescription()
                        + " cannot be resolved in the file system for checking its content length");
            }
            return length;
        } else {
            try {
                return Files.size(this.filePath);
            } catch (NoSuchFileException ex) {
                throw new FileNotFoundException(ex.getMessage());
            }
        }
    }

    /**
     * This implementation returns the underlying File/Path last-modified time.
     */
    @Override
    public long lastModified() throws IOException {
        if (this.file != null) {
            return super.lastModified();
        } else {
            try {
                return Files.getLastModifiedTime(this.filePath).toMillis();
            } catch (NoSuchFileException ex) {
                throw new FileNotFoundException(ex.getMessage());
            }
        }
    }

    /**
     * This implementation creates a FileSystemResource, applying the given path
     * relative to the path of the underlying file of this resource descriptor.
     *
     * @see StringUtils#applyRelativePath(String, String)
     */
    @Override
    public Resource createRelative(String relativePath) {
        String pathToUse = StringUtils.applyRelativePath(this.path, relativePath);
        return (this.file != null ? new FileSystemResource(pathToUse) :
                new FileSystemResource(this.filePath.getFileSystem(), pathToUse));
    }

    /**
     * This implementation returns the name of the file.
     *
     * @see File#getName()
     */
    @Override
    public String getFilename() {
        return (this.file != null ? this.file.getName() : this.filePath.getFileName().toString());
    }

    /**
     * This implementation returns a description that includes the absolute
     * path of the file.
     *
     * @see File#getAbsolutePath()
     */
    @Override
    public String getDescription() {
        return "file [" + (this.file != null ? this.file.getAbsolutePath() : this.filePath.toAbsolutePath()) + "]";
    }


    /**
     * This implementation compares the underlying File references.
     */
    @Override
    public boolean equals(Object other) {
        return (this == other || (other instanceof FileSystemResource
                && this.path.equals(((FileSystemResource) other).path)));
    }

    /**
     * This implementation returns the hash code of the underlying File reference.
     */
    @Override
    public int hashCode() {
        return this.path.hashCode();
    }

}
