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
import com.alibaba.nacos.core.utils.ReuseUploadFileHttpServletRequest;
import com.alibaba.nacos.plugin.auth.constant.Constants;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.Part;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockPart;

import java.io.IOException;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AbstractConfigNamespaceHttpResourceParserTest {
    
    private final ConfigNamespaceHttpResourceParser scalar =
        new ConfigNamespaceHttpResourceParser();
    
    private final ConfigExportHttpResourceParser form = new ConfigExportHttpResourceParser();
    
    @Test
    void testScalarAndFormDefaultsIgnoreTenant() {
        for (AbstractConfigNamespaceHttpResourceParser parser : new AbstractConfigNamespaceHttpResourceParser[] {
            scalar, form}) {
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.addParameter("tenant", "ignored", "also-ignored");
            assertEquals("public", parser.getNamespaceId(request, null));
            for (String namespace : new String[] {"", " ", "public", "selected"}) {
                request.setParameter("namespaceId", namespace);
                assertEquals("selected".equals(namespace) ? "selected" : "public",
                    parser.getNamespaceId(request, null));
            }
        }
    }
    
    @Test
    void testRepeatedValuesAndArrayFallbackRejected() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addParameter("namespaceId[]", "fallback");
        assertThrows(IllegalArgumentException.class, () -> scalar.getNamespaceId(request, null));
        request.setParameter("namespaceId", "same", "same");
        assertThrows(IllegalArgumentException.class, () -> scalar.getNamespaceId(request, null));
        request.setParameter("namespaceId", "first", "second");
        assertThrows(IllegalArgumentException.class, () -> form.getNamespaceId(request, null));
    }
    
    @Test
    void testExplicitFieldTakesPrecedenceOverBindingDefaults() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addParameter("!namespaceId", "default");
        assertEquals("public", scalar.getNamespaceId(request, null));
        assertThrows(IllegalArgumentException.class, () -> form.getNamespaceId(request, null));
        request.addParameter("namespaceId[]", "array");
        request.addParameter("namespaceId", "");
        assertEquals("public", form.getNamespaceId(request, null));
        request.setParameter("namespaceId", "selected");
        assertEquals("selected", scalar.getNamespaceId(request, null));
        assertEquals("selected", form.getNamespaceId(request, null));
    }
    
    @Test
    void testEmptyServletValueArray() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setParameter("namespaceId", new String[0]);
        assertNull(scalar.readNamespaceParameter(request, "namespaceId", false));
    }
    
    @Test
    void testMultipartNamespaceFilesAndFallbacks() {
        for (String name : new String[] {"namespaceId", "namespaceId[]", "!namespaceId"}) {
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.setContentType("MULTIPART/form-data");
            request.addPart(new MockPart(name, "namespace.txt", new byte[0]));
            assertThrows(IllegalArgumentException.class, () -> form.getNamespaceId(request, null));
            if ("!namespaceId".equals(name)) {
                assertEquals("public", scalar.getNamespaceId(request, null));
            } else {
                assertThrows(IllegalArgumentException.class,
                    () -> scalar.getNamespaceId(request, null));
            }
            request.addParameter("namespaceId", "selected");
            if ("namespaceId".equals(name)) {
                assertThrows(IllegalArgumentException.class,
                    () -> scalar.getNamespaceId(request, null));
            } else {
                assertEquals("selected", scalar.getNamespaceId(request, null));
                assertEquals("selected", form.getNamespaceId(request, null));
            }
        }
    }
    
    @Test
    void testMultipartMetadataOnlyAndUnrelatedParts() throws Exception {
        HttpServletRequest request = mock(HttpServletRequest.class);
        Part part = mock(Part.class);
        when(request.getContentType()).thenReturn("multipart/form-data");
        when(request.getParts()).thenReturn(Collections.singletonList(part));
        when(part.getName()).thenReturn("file");
        when(part.getSubmittedFileName()).thenReturn("config.zip");
        assertEquals("public", form.getNamespaceId(request, null));
        when(part.getSubmittedFileName()).thenReturn(null);
        assertEquals("public", scalar.getNamespaceId(request, null));
        when(request.getParameterValues("namespaceId")).thenReturn(new String[] {"selected"});
        when(part.getSubmittedFileName()).thenReturn("file.zip");
        assertEquals("selected", scalar.getNamespaceId(request, null));
        verify(part, never()).getInputStream();
        verify(request, never()).getInputStream();
    }
    
    @Test
    void testReusableMultipartRequestPreservesMetadataChecks() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setContentType("multipart/form-data");
        request.addParameter("namespaceId", "selected");
        request.addPart(new MockPart("file", "config.zip", new byte[0]));
        ReuseUploadFileHttpServletRequest wrapped = new ReuseUploadFileHttpServletRequest(request);
        assertEquals("selected", scalar.getNamespaceId(wrapped, null));
        request.addPart(new MockPart("namespaceId", "namespace.txt", new byte[0]));
        assertThrows(IllegalArgumentException.class, () -> scalar.getNamespaceId(wrapped, null));
    }
    
    @Test
    void testNonMultipartAndEmptyMultipart() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setContentType("application/json");
        assertEquals("public", form.getNamespaceId(request, null));
        request.setContentType("multipart/form-data");
        assertEquals("public", form.getNamespaceId(request, null));
    }
    
    @Test
    void testMultipartParsingFailures() throws Exception {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getContentType()).thenReturn("multipart/form-data");
        when(request.getParts()).thenThrow(new IOException("unreadable"),
            new ServletException("invalid"));
        assertThrows(IllegalArgumentException.class, () -> scalar.getNamespaceId(request, null));
        assertThrows(IllegalArgumentException.class, () -> form.getNamespaceId(request, null));
    }
    
    @Test
    void testCloneSourceAndTargetAreIndependent() {
        ConfigCloneHttpResourceParser parser = new ConfigCloneHttpResourceParser();
        for (boolean console : new boolean[] {false, true}) {
            Secured secured = mock(Secured.class);
            when(secured.tags()).thenReturn(console
                ? new String[] {Constants.Tag.SECURED_SPECIAL_TAGS} : new String[0]);
            String target = console ? "targetNamespaceId" : "namespaceId";
            String source = console ? "namespaceId" : "sourceNamespaceId";
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.addParameter("tenant", "ignored");
            assertEquals("public", parser.getNamespaceId(request, secured));
            request.addParameter(source, "source");
            request.addParameter(target, "");
            assertEquals("public", parser.getNamespaceId(request, secured));
            request.setParameter(target, "target");
            assertEquals("target", parser.getNamespaceId(request, secured));
            request.setParameter(source, "source", "other");
            assertThrows(IllegalArgumentException.class,
                () -> parser.getNamespaceId(request, secured));
        }
    }
}
