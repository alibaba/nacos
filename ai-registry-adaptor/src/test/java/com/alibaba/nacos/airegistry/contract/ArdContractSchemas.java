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

package com.alibaba.nacos.airegistry.contract;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.Error;
import com.networknt.schema.Schema;
import com.networknt.schema.SchemaLocation;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SchemaRegistryConfig;
import com.networknt.schema.SpecificationVersion;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Offline upstream contracts shared by adaptor unit and standalone HTTP tests.
 *
 * @author nacos
 */
public final class ArdContractSchemas {
    
    public static final String CURRENT = "f073b1ea9b01a5f068d0b0986426f2ea49fa30b2";
    
    public static final String LEGACY = "5fa2f5aef790b478319f6a3b43adf4661b0ed0e0";
    
    private static final String SOURCE = "https://raw.githubusercontent.com/ards-project/ard-spec/";
    
    private static final ObjectMapper MAPPER = new ObjectMapper();
    
    private static final Map<String, String> HASHES = Map.of(
        LEGACY + "/ai-catalog.schema.json",
        "c55238483a4738e08b250bdd6af1f4dc05a91afe882c649d224d09c19cd8fe09",
        LEGACY + "/ard.openapi.yaml",
        "60b2460da5e8289f2293d72fb2839317f15a554698309ce1e33ac55b9c55fcab",
        CURRENT + "/ard-entry.schema.json",
        "3032dad432607ddae0ec088db5759fa456f36308e0e9f340165aaed504d535f4",
        CURRENT + "/ard.openapi.yaml",
        "13767927d9985fb4503fffd228c371ac08297b2ac80a7e6c8a1125cd3cf1f025",
        CURRENT + "/ard.context.jsonld",
        "bd80a73de23c4279409a429f43edcf944e07c09511678511bc9467924067c093");
    
    private final SchemaRegistry registry;
    
    /**
     * Load and verify every pinned fixture without network access.
     */
    public ArdContractSchemas() {
        this(ArdContractSchemas.class::getResourceAsStream);
    }
    
    /**
     * Load fixtures through the supplied source, rejecting missing or modified bytes.
     *
     * @param resources classpath resource reader
     */
    public ArdContractSchemas(Function<String, InputStream> resources) {
        Map<String, String> documents = new LinkedHashMap<>();
        HASHES.forEach((path, expected) -> {
            try (InputStream stream = resources.apply("/ard-spec/" + path)) {
                if (stream == null) {
                    throw new IllegalStateException("Missing ARD fixture: " + path);
                }
                byte[] bytes = stream.readAllBytes();
                String actual =
                    HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
                if (!expected.equals(actual)) {
                    throw new IllegalStateException("Modified ARD fixture: " + path);
                }
                String content = new String(bytes, StandardCharsets.UTF_8);
                if (path.endsWith(".yaml")) {
                    content = MAPPER.writeValueAsString(new Yaml().load(content));
                }
                String[] parts = path.split("/", 2);
                documents.put(SOURCE + parts[0] + "/spec/schemas/" + parts[1], content);
                // Preserve the original $id; resolve its canonical URI to the pinned bytes.
                if (path.endsWith(".schema.json")) {
                    documents.put(MAPPER.readTree(content).get("$id").asText(), content);
                }
            } catch (Exception e) {
                throw new IllegalStateException("Cannot load ARD fixture: " + path, e);
            }
        });
        registry = SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_2020_12,
            builder -> builder.schemaRegistryConfig(SchemaRegistryConfig.builder()
                .formatAssertionsEnabled(true).build())
                .schemaLoader(loader -> loader.fetchRemoteResources(false)
                    .resourceLoaders(loaders -> loaders.add(iri -> {
                        String document = documents.get(iri.toString());
                        if (document == null) {
                            throw new IllegalStateException(
                                "Unpinned ARD schema reference: " + iri);
                        }
                        return () -> new java.io.ByteArrayInputStream(
                            document.getBytes(StandardCharsets.UTF_8));
                    }))));
    }
    
    /**
     * Resolve an explicitly pinned schema entry point. Unknown references fail closed.
     *
     * @param version pinned commit
     * @param file fixture filename
     * @param pointer JSON pointer fragment
     * @return offline schema
     */
    public Schema schema(String version, String file, String pointer) {
        return registry.getSchema(
            SchemaLocation.of(SOURCE + version + "/spec/schemas/" + file + "#" + pointer));
    }
    
    /**
     * Validate a current complete entry or manifest.
     *
     * @param definition definition name
     * @param value JSON instance
     * @return structural errors from the unmodified upstream schema
     */
    public List<Error> validateEntry(String definition, JsonNode value) {
        return schema(CURRENT, "ard-entry.schema.json", "/$defs/" + definition).validate(value);
    }
    
    /**
     * Validate a current or legacy API response with format assertions enabled.
     *
     * @param version pinned commit
     * @param definition response definition
     * @param value JSON response
     * @return structural errors
     */
    public List<Error> validateResponse(String version, String definition, JsonNode value) {
        return schema(version, "ard.openapi.yaml", "/components/schemas/" + definition)
            .validate(value);
    }
    
    /**
     * Validate a legacy catalog without changing its original schema.
     *
     * @param value JSON catalog
     * @return structural errors
     */
    public List<Error> validateLegacyCatalog(JsonNode value) {
        return schema(LEGACY, "ai-catalog.schema.json", "").validate(value);
    }
    
    /**
     * Supplement the upstream uppercase property with the specified lowercase trust semantics.
     * This is structural validation, not identity or signature verification.
     *
     * @param entry JSON entry
     * @return trust errors, or an empty list when the member is absent
     */
    public List<Error> validateLowercaseTrust(JsonNode entry) {
        return entry.has("trustManifest")
            ? validateEntry("TrustManifest", entry.get("trustManifest")) : List.of();
    }
}
