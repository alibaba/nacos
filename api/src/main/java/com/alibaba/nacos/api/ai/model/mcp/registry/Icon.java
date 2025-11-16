/*
 * Copyright 1999-2025 Alibaba Group Holding Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.alibaba.nacos.api.ai.model.mcp.registry;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.List;

/**
 * Icon per Icon schema:
 * An optionally-sized icon that can be displayed in a user interface.
 * Required: src (HTTPS URI). Optional: mimeType, sizes, theme.
 *
 * <p>
 * Fields align with components.schemas.Icon.
 * </p>
 *
 * @author xinluo
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class Icon {

    @JsonProperty(value = "src", required = true)
    private String src;

    private MimeType mimeType;

    private List<String> sizes;

    private Theme theme;

    /**
     * Get src.
     *
     * @return src
     */
    public String getSrc() {
        return src;
    }

    /**
     * Set src.
     *
     * @param src src
     */
    public void setSrc(String src) {
        this.src = src;
    }

    /**
     * Get mime type.
     *
     * @return mime type
     */
    public MimeType getMimeType() {
        return mimeType;
    }

    /**
     * Set mime type.
     *
     * @param mimeType mime type
     */
    public void setMimeType(MimeType mimeType) {
        this.mimeType = mimeType;
    }

    /**
     * Get sizes.
     *
     * @return sizes
     */
    public List<String> getSizes() {
        return sizes;
    }

    /**
     * Set sizes.
     *
     * @param sizes sizes
     */
    public void setSizes(List<String> sizes) {
        this.sizes = sizes;
    }

    /**
     * Get theme.
     *
     * @return theme
     */
    public Theme getTheme() {
        return theme;
    }

    /**
     * Set theme.
     *
     * @param theme theme
     */
    public void setTheme(Theme theme) {
        this.theme = theme;
    }

    /**
     * Mime type enum: image/png, image/jpeg, image/jpg, image/svg+xml, image/webp.
     * Serialized/deserialized as the lowercase string value.
     */
    public static enum MimeType {
        /**
         * PNG mime type.
         */
        IMAGE_PNG("image/png"),
        /**
         * JPEG mime type.
         */
        IMAGE_JPEG("image/jpeg"),
        /**
         * JPG mime type.
         */
        IMAGE_JPG("image/jpg"),
        /**
         * SVG XML mime type.
         */
        IMAGE_SVG_XML("image/svg+xml"),
        /**
         * WebP mime type.
         */
        IMAGE_WEBP("image/webp");

        private final String value;

        /**
         * Constructor.
         *
         * @param value value
         */
        MimeType(String value) {
            this.value = value;
        }

        /**
         * Get value.
         *
         * @return value
         */
        @JsonValue
        public String getValue() {
            return value;
        }

        /**
         * Create from value.
         *
         * @param value value
         * @return MimeType
         */
        @JsonCreator
        public static MimeType fromValue(String value) {
            for (MimeType t : MimeType.values()) {
                if (t.value.equalsIgnoreCase(value)) {
                    return t;
                }
            }
            throw new IllegalArgumentException("Unknown mimeType: " + value);
        }
    }

    /**
     * Theme enum: light or dark.
     * Serialized/deserialized as the lowercase string value.
     */
    public static enum Theme {
        /**
         * Light theme.
         */
        LIGHT("light"),
        /**
         * Dark theme.
         */
        DARK("dark");

        private final String value;

        /**
         * Constructor.
         *
         * @param value value
         */
        Theme(String value) {
            this.value = value;
        }

        /**
         * Get value.
         *
         * @return value
         */
        @JsonValue
        public String getValue() {
            return value;
        }

        /**
         * Create from value.
         *
         * @param value value
         * @return Theme
         */
        @JsonCreator
        public static Theme fromValue(String value) {
            for (Theme t : Theme.values()) {
                if (t.value.equalsIgnoreCase(value)) {
                    return t;
                }
            }
            throw new IllegalArgumentException("Unknown theme: " + value);
        }
    }
}