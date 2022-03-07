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

import com.alibaba.nacos.common.utils.AbstractAssert;

import java.io.IOException;
import java.io.InputStream;

/**
 * Copy from https://github.com/spring-projects/spring-framework.git, with less modifications
 * {@link Resource} implementation for a given {@link InputStream}.
 *
 * <p>Should only be used if no other specific {@code Resource} implementation
 * is applicable. In particular, prefer {@link ByteArrayResource} or any of the
 * file-based {@code Resource} implementations where possible.
 *
 * <p>In contrast to other {@code Resource} implementations, this is a descriptor
 * for an <i>already opened</i> resource - therefore returning {@code true} from
 * {@link #isOpen()}. Do not use an {@code InputStreamResource} if you need to
 * keep the resource descriptor somewhere, or if you need to read from a stream
 * multiple times.
 *
 * @author Juergen Hoeller
 * @author Sam Brannen
 * @see ByteArrayResource
 * @see ClassPathResource
 * @see FileSystemResource
 * @see UrlResource
 * @since 28.12.2003
 */
public class InputStreamResource extends AbstractResource {

    private final InputStream inputStream;

    private final String description;

    private boolean read = false;

    /**
     * Create a new InputStreamResource.
     *
     * @param inputStream the InputStream to use
     */
    public InputStreamResource(InputStream inputStream) {
        this(inputStream, "resource loaded through InputStream");
    }

    /**
     * Create a new InputStreamResource.
     *
     * @param inputStream the InputStream to use
     * @param description where the InputStream comes from
     */
    public InputStreamResource(InputStream inputStream, String description) {
        AbstractAssert.notNull(inputStream, "InputStream must not be null");
        this.inputStream = inputStream;
        this.description = (description != null ? description : "");
    }


    /**
     * This implementation always returns {@code true}.
     */
    @Override
    public boolean exists() {
        return true;
    }

    /**
     * This implementation always returns {@code true}.
     */
    @Override
    public boolean isOpen() {
        return true;
    }

    /**
     * This implementation throws IllegalStateException if attempting to
     * read the underlying stream multiple times.
     */
    @Override
    public InputStream getInputStream() throws IOException, IllegalStateException {
        if (this.read) {
            throw new IllegalStateException("InputStream has already been read - "
                    + "do not use InputStreamResource if a stream needs to be read multiple times");
        }
        this.read = true;
        return this.inputStream;
    }

    /**
     * This implementation returns a description that includes the passed-in
     * description, if any.
     */
    @Override
    public String getDescription() {
        return "InputStream resource [" + this.description + "]";
    }


    /**
     * This implementation compares the underlying InputStream.
     */
    @Override
    public boolean equals(Object other) {
        return (this == other || (other instanceof InputStreamResource
                && ((InputStreamResource) other).inputStream.equals(this.inputStream)));
    }

    /**
     * This implementation returns the hash code of the underlying InputStream.
     */
    @Override
    public int hashCode() {
        return this.inputStream.hashCode();
    }

}
