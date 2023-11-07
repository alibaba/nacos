/*
 * Copyright 2002-2018 the original author or authors.
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

import com.alibaba.nacos.common.packagescan.util.AbstractAssert;
import com.alibaba.nacos.common.packagescan.util.NestedIoException;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URL;

/**
 * Copy from https://github.com/spring-projects/spring-framework.git, with less modifications
 * JBoss VFS based {@link Resource} implementation.
 *
 * <p>As of Spring 4.0, this class supports VFS 3.x on JBoss AS 6+
 * (package {@code org.jboss.vfs}) and is in particular compatible with
 * JBoss AS 7 and WildFly 8+.
 *
 * @author Ales Justin
 * @author Juergen Hoeller
 * @author Costin Leau
 * @author Sam Brannen
 * @see org.jboss.vfs.VirtualFile
 * @since 3.0
 */
public class VfsResource extends AbstractResource {

    private final Object resource;

    /**
     * Create a new {@code VfsResource} wrapping the given resource handle.
     *
     * @param resource a {@code org.jboss.vfs.VirtualFile} instance
     *                 (untyped in order to avoid a static dependency on the VFS API)
     */
    public VfsResource(Object resource) {
        AbstractAssert.notNull(resource, "VirtualFile must not be null");
        this.resource = resource;
    }

    @Override
    public InputStream getInputStream() throws IOException {
        return VfsUtils.getInputStream(this.resource);
    }

    @Override
    public boolean exists() {
        return VfsUtils.exists(this.resource);
    }

    @Override
    public boolean isReadable() {
        return VfsUtils.isReadable(this.resource);
    }

    @Override
    public URL getUrl() throws IOException {
        try {
            return VfsUtils.getUrl(this.resource);
        } catch (Exception ex) {
            throw new NestedIoException("Failed to obtain URL for file " + this.resource, ex);
        }
    }

    @Override
    public URI getUri() throws IOException {
        try {
            return VfsUtils.getUri(this.resource);
        } catch (Exception ex) {
            throw new NestedIoException("Failed to obtain URI for " + this.resource, ex);
        }
    }

    @Override
    public File getFile() throws IOException {
        return VfsUtils.getFile(this.resource);
    }

    @Override
    public long contentLength() throws IOException {
        return VfsUtils.getSize(this.resource);
    }

    @Override
    public long lastModified() throws IOException {
        return VfsUtils.getLastModified(this.resource);
    }

    @Override
    public Resource createRelative(String relativePath) throws IOException {
        if (!relativePath.startsWith(".") && relativePath.contains("/")) {
            try {
                return new VfsResource(VfsUtils.getChild(this.resource, relativePath));
            } catch (IOException ex) {
                // fall back to getRelative
            }
        }

        return new VfsResource(VfsUtils.getRelative(new URL(getUrl(), relativePath)));
    }

    @Override
    public String getFilename() {
        return VfsUtils.getName(this.resource);
    }

    @Override
    public String getDescription() {
        return "VFS resource [" + this.resource + "]";
    }

    @Override
    public boolean equals(Object other) {
        return (this == other || (other instanceof VfsResource
                && this.resource.equals(((VfsResource) other).resource)));
    }

    @Override
    public int hashCode() {
        return this.resource.hashCode();
    }

}
