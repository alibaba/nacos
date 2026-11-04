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

package com.alibaba.nacos.airegistry.service.ard;

import com.alibaba.nacos.airegistry.constant.ArdProtocolConstants;
import com.apicatalog.jsonld.JsonLdError;
import com.apicatalog.jsonld.JsonLdOptions;
import com.apicatalog.jsonld.JsonLdVersion;
import com.apicatalog.jsonld.context.ActiveContext;
import com.apicatalog.jsonld.processor.ProcessingRuntime;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.json.Json;
import jakarta.json.JsonArrayBuilder;
import jakarta.json.JsonObject;
import jakarta.json.JsonReader;
import jakarta.json.JsonValue;

import java.io.InputStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;

/**
 * Resolves query vocabulary using JSON-LD 1.1 and the pinned ARD base context.
 *
 * @author nacos
 */
public class ArdContextResolver {
    
    private static final String CONTEXT = "@context";
    private static final String BASE_RESOURCE =
        "/ard-spec/f073b1ea9b01a5f068d0b0986426f2ea49fa30b2/ard.context.jsonld";
    private static final JsonObject BASE_DOCUMENT = readBase();
    private final ArdContextLoader loader;
    
    public ArdContextResolver() {
        this.loader = null;
    }
    
    ArdContextResolver(ArdContextLoader loader) {
        this.loader = loader;
    }
    
    /**
     * Creates an isolated context; Java null means absent, JSON null is invalid.
     *
     * @param context optional query context
     * @return effective vocabulary for this query only
     */
    public ResolvedContext resolve(JsonNode context) {
        if (context != null && !context.isTextual() && !context.isObject() && !context.isArray()) {
            throw new IllegalArgumentException("query.@context must be a URI, object or array");
        }
        ArdContextLoader.Session session =
            (loader == null ? ArdContextLoader.fromEnvironment() : loader)
                .newSession(BASE_DOCUMENT);
        JsonLdOptions options = new JsonLdOptions(session);
        options.setProcessingMode(JsonLdVersion.V1_1);
        try {
            ActiveContext active = new ActiveContext(ProcessingRuntime.of(options)).newContext()
                .create(BASE_DOCUMENT.get(CONTEXT), null);
            if (context != null) {
                String content = context.toString();
                session.consume(content.getBytes(StandardCharsets.UTF_8).length);
                try (JsonReader reader = Json.createReader(new StringReader(content))) {
                    active = active.newContext().create(preserveBase(reader.readValue()), null);
                }
            }
            return new ResolvedContext(active);
        } catch (JsonLdError | RuntimeException e) {
            throw new IllegalArgumentException("Unable to resolve query.@context", e);
        }
    }
    
    static JsonValue preserveBase(JsonValue value) {
        if (value == JsonValue.NULL) {
            return Json.createArrayBuilder().addNull().add(BASE_DOCUMENT.get(CONTEXT)).build();
        }
        if (value.getValueType() != JsonValue.ValueType.ARRAY) {
            return value;
        }
        JsonArrayBuilder result = Json.createArrayBuilder();
        for (JsonValue item : value.asJsonArray()) {
            result.add(item);
            if (item == JsonValue.NULL) {
                result.add(BASE_DOCUMENT.get(CONTEXT));
            }
        }
        return result.build();
    }
    
    private static JsonObject readBase() {
        try (InputStream input = ArdContextResolver.class.getResourceAsStream(BASE_RESOURCE)) {
            if (input == null) {
                throw new IllegalStateException(
                    "Missing bundled ARD context: " + ArdProtocolConstants.BASE_CONTEXT);
            }
            try (JsonReader reader = Json.createReader(input)) {
                return reader.readObject();
            }
        } catch (Exception e) {
            throw new IllegalStateException("Unable to read bundled ARD context", e);
        }
    }
    
    /**
     * Query-local vocabulary. A disabled term expands to null, with no literal fallback.
     */
    public static final class ResolvedContext {
        
        private final ActiveContext context;
        
        private ResolvedContext(ActiveContext context) {
            this.context = context;
        }
        
        /**
         * Whether a complete spelling is explicitly defined, including dotted terms.
         *
         * @param term query spelling
         * @return whether the effective context defines the spelling
         */
        public boolean isDefined(String term) {
            return context.containsTerm(term);
        }
        
        /**
         * Expands a term or IRI without changing case or interpreting JSON path members.
         *
         * @param term vocabulary term or IRI
         * @return expanded IRI, JSON-LD keyword or null for a disabled term
         */
        public String expand(String term) {
            try {
                return context.uriExpansion().vocab(true).expand(term);
            } catch (JsonLdError e) {
                throw new IllegalArgumentException("Unable to expand ARD query term", e);
            }
        }
    }
}
