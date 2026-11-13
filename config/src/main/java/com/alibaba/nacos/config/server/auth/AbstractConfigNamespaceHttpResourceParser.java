/*
 * Copyright 1999-2026 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.config.server.auth;

import com.alibaba.nacos.auth.annotation.Secured;
import com.alibaba.nacos.auth.parser.http.ConfigHttpResourceParser;
import com.alibaba.nacos.common.utils.StringUtils;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.Part;

import java.io.IOException;

/**
 * Namespace binding validation for dedicated Config management resource parsers.
 *
 * <p>Keeps Servlet parameter access aligned with MVC scalar and form binding. Subclasses select
 * source and target fields; this helper does not interpret legacy aliases. Ordinary v3 APIs
 * continue to use the default resource parser.</p>
 *
 * @author xiweng.yy
 */
public abstract class AbstractConfigNamespaceHttpResourceParser extends ConfigHttpResourceParser {
    
    @Override
    protected abstract String getNamespaceId(HttpServletRequest request, Secured secured);
    
    /**
     * Read a single text namespace value, preserving absent and explicitly empty values.
     *
     * <p>The explicit field takes precedence over {@code []} and form {@code !} fallback fields.
     * Scalar parameters do not interpret {@code !} defaults. Multipart checks inspect only Part
     * metadata, without reading file streams or the request input stream. Subclasses determine
     * namespace defaults.</p>
     *
     * @param request current HTTP request
     * @param parameter field bound by the Controller
     * @param formBinding whether form default-field binding applies
     * @return raw value, or {@code null} when absent
     */
    protected String readNamespaceParameter(HttpServletRequest request, String parameter,
        boolean formBinding) {
        String[] values = request.getParameterValues(parameter);
        if (values == null && request.getParameterValues(parameter + "[]") != null) {
            throw new IllegalArgumentException("Namespace must be supplied as " + parameter);
        }
        if (values != null && values.length > 1) {
            throw new IllegalArgumentException("Ambiguous namespace parameter '" + parameter + "'");
        }
        String primary = values == null || values.length == 0 ? null : values[0];
        if (formBinding && primary == null && request.getParameter("!" + parameter) != null) {
            throw new IllegalArgumentException("Namespace must be supplied as " + parameter);
        }
        String contentType = request.getContentType();
        if (contentType != null && StringUtils.startsWithIgnoreCase(contentType, "multipart/")) {
            try {
                for (Part part : request.getParts()) {
                    String name = part.getName();
                    if (part.getSubmittedFileName() != null && (parameter.equals(name)
                        || (primary == null && (parameter + "[]").equals(name))
                        || (formBinding && primary == null && ("!" + parameter).equals(name)))) {
                        throw new IllegalArgumentException(
                            "Namespace parameter must be a text field");
                    }
                }
            } catch (IOException | ServletException e) {
                throw new IllegalArgumentException("Invalid multipart namespace parameter", e);
            }
        }
        return primary;
    }
}
