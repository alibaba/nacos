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

import com.alibaba.nacos.airegistry.model.ard.ArdCatalogEntry;
import com.alibaba.nacos.airegistry.model.ard.ArdEntry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

/**
 * Separates current publication from the legacy-compatible discovery projection.
 *
 * @author nacos
 */
public final class ArdEntryRenderer {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(ArdEntryRenderer.class);
    
    private ArdEntryRenderer() {
    }
    
    /**
     * Copy complete entry fields into a current manifest entry, retaining query examples.
     * Search-only fields are not copied. Count recommendations never remove an entry.
     *
     * @param source complete source entry
     * @return independently projected manifest entry
     */
    public static ArdEntry manifestEntry(ArdCatalogEntry source) {
        ArdEntry result = new ArdEntry();
        result.setIdentifier(source.getIdentifier());
        result.setDisplayName(source.getDisplayName());
        result.setType(source.getType());
        result.setUrl(source.getUrl());
        result.setDescription(source.getDescription());
        result.setTags(source.getTags() == null ? null : new ArrayList<>(source.getTags()));
        result.setCapabilities(
            source.getCapabilities() == null ? null : new ArrayList<>(source.getCapabilities()));
        List<String> queries = source.getRepresentativeQueries();
        result.setRepresentativeQueries(queries == null ? null : new ArrayList<>(queries));
        result.setVersion(source.getVersion());
        result.setUpdatedAt(source.getUpdatedAt());
        result.setMetadata(
            source.getMetadata() == null ? null : new LinkedHashMap<>(source.getMetadata()));
        result.setTrustManifest(source.getTrustManifest() == null ? null
            : new LinkedHashMap<>(source.getTrustManifest()));
        int count = queries == null ? 0 : queries.size();
        if (count < 2 || count > 5) {
            LOGGER.warn(
                "ARD entry {} publishes {} representative queries; recommended count is 2 to 5",
                source.getIdentifier(), count);
        }
        return result;
    }
    
    /**
     * Select the stable legacy subset without modifying the canonical search data.
     *
     * @param queries canonical query examples
     * @return up to five examples, or null when fewer than two are available
     */
    public static List<String> legacyQueries(List<String> queries) {
        return queries == null || queries.size() < 2 ? null
            : new ArrayList<>(queries.subList(0, Math.min(queries.size(), 5)));
    }
}
