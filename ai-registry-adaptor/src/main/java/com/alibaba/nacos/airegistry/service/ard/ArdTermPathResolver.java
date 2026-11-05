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

import java.net.URI;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Resolves vocabulary roots while retaining literal paths inside opaque JSON members.
 *
 * @author nacos
 */
final class ArdTermPathResolver {
    
    private static final String NS = "https://agenticresourcediscovery.org/ns#";
    private static final String UNSUPPORTED = "urn:nacos:ard:unsupported:";
    private static final Pattern PATH = Pattern.compile("^[^.\\s]+(?:\\.[^.\\s]+)*$");
    private static final Set<String> FIELDS = Set.of("displayName", "tags", "capabilities",
        "representativeQueries", "version", "metadata", "trustManifest", "data",
        "publisher", "publisherId", "source", "resourceName", "resourceType", "resourceVersion");
    private static final Set<String> OPAQUE = Set.of("metadata", "trustManifest", "data");
    private final ArdContextResolver.ResolvedContext context;
    
    ArdTermPathResolver(ArdContextResolver.ResolvedContext context) {
        this.context = context;
    }
    
    String resolve(String path) {
        if (path == null || path.isBlank() || path.chars().anyMatch(Character::isWhitespace)) {
            throw new IllegalArgumentException("Invalid ARD field path");
        }
        if (context.isDefined(path)) {
            return canonical(context.expand(path));
        }
        for (int dot = path.indexOf('.'); dot >= 0; dot = path.indexOf('.', dot + 1)) {
            String root = canonical(context.expand(path.substring(0, dot)));
            if (OPAQUE.contains(root)) {
                String suffix = path.substring(dot + 1);
                validateLiteral(suffix);
                return root + "." + suffix;
            }
        }
        if (path.contains(":")) {
            URI.create(path);
        } else {
            validateLiteral(path);
        }
        return canonical(context.expand(path));
    }
    
    static void validateLiteral(String path) {
        if (path == null || !PATH.matcher(path).matches()) {
            throw new IllegalArgumentException("Invalid ARD field path: " + path);
        }
    }
    
    private String canonical(String iri) {
        if ((NS + "mediaType").equals(iri)) {
            return "type";
        }
        if (iri != null && iri.startsWith(NS) && FIELDS.contains(iri.substring(NS.length()))) {
            return iri.substring(NS.length());
        }
        return UNSUPPORTED + iri;
    }
}
