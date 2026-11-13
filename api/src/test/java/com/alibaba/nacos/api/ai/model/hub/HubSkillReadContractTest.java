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

package com.alibaba.nacos.api.ai.model.hub;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Wire contract and redaction tests for dedicated Hub read DTOs.
 *
 * @author nacos
 */
class HubSkillReadContractTest {
    
    private final ObjectMapper mapper = new ObjectMapper();
    
    @Test
    void summaryKeepsLargeHubEntryIdAsStringAndDoesNotExposeSourceId() throws Exception {
        HubSkillSummary summary = new HubSkillSummary();
        summary.setHubEntryId(Long.MAX_VALUE);
        summary.setDisplayName("find-skills");
        summary.setSourceDisplayName("team-a");
        summary.setAuthor("author-a");
        summary.setDescription("Search available skills");
        summary.setSharedVersion("1.0.0");
        summary.setTags(Arrays.asList("search", "tools"));
        summary.setSharedAt(1720000000000L);
        summary.setDownloadCount(42L);
        
        JsonNode json = mapper.readTree(mapper.writeValueAsString(summary));
        assertTrue(json.get("hubEntryId").isTextual());
        assertEquals("9223372036854775807", json.get("hubEntryId").asText());
        assertEquals("HUB", json.get("origin").asText());
        assertEquals("find-skills", json.get("displayName").asText());
        assertEquals(42L, json.get("downloadCount").asLong());
        assertFalse(json.has("sourceNamespaceId"));
        assertFalse(json.has("sourceResourceId"));
        assertFalse(json.has("sourceVersionId"));
        assertFalse(json.has("name"));
        assertFalse(json.has("ext"));
        assertFalse(json.has("storage"));
        
        HubSkillSummary roundTrip = mapper.readValue(json.toString(), HubSkillSummary.class);
        assertEquals(Long.MAX_VALUE, roundTrip.getHubEntryId());
        assertEquals("HUB", roundTrip.getOrigin());
        assertEquals("1.0.0", roundTrip.getSharedVersion());
        assertEquals("team-a", roundTrip.getSourceDisplayName());
        assertEquals(42L, roundTrip.getDownloadCount());
        assertEquals("author-a", roundTrip.getAuthor());
        assertEquals("Search available skills", roundTrip.getDescription());
        assertEquals(Arrays.asList("search", "tools"), roundTrip.getTags());
        assertEquals(Long.valueOf(1720000000000L), roundTrip.getSharedAt());
    }
    
    @Test
    void detailOmitsSourceDeepLinkWithoutSeparateReadPermission() throws Exception {
        HubSkillDetail detail = new HubSkillDetail();
        detail.setHubEntryId(101L);
        detail.setDisplayName("find-skills");
        detail.setSharedVersion("2.0.0");
        detail.setSourceNamespaceId("team-a");
        detail.setSourceName("find-skills");
        detail.setSourceReadable(false);
        
        JsonNode unauthorized = mapper.readTree(mapper.writeValueAsString(detail));
        assertFalse(unauthorized.has("sourceNamespaceId"));
        assertFalse(unauthorized.has("sourceName"));
        assertFalse(unauthorized.get("sourceReadable").asBoolean());
        assertNull(detail.getSourceNamespaceId());
        assertNull(detail.getSourceName());
        
        detail.setSourceReadable(true);
        detail.setSourceNamespaceId("team-a");
        detail.setSourceName("find-skills");
        JsonNode authorized = mapper.readTree(mapper.writeValueAsString(detail));
        assertEquals("team-a", authorized.get("sourceNamespaceId").asText());
        assertEquals("find-skills", authorized.get("sourceName").asText());
        
        detail.setSourceReadable(false);
        JsonNode revoked = mapper.readTree(mapper.writeValueAsString(detail));
        assertFalse(revoked.has("sourceNamespaceId"));
        assertFalse(revoked.has("sourceName"));
    }
    
    @Test
    void contentContainsOnlyPublicSkillFieldsNotReferenceStorage() throws Exception {
        HubSkillContent content = new HubSkillContent();
        content.setHubEntryId(Long.MAX_VALUE);
        content.setDisplayName("find-skills");
        content.setSharedVersion("1.0.0");
        content.setSkillMd("# find-skills");
        HubSkillResource resource = new HubSkillResource();
        resource.setName("README.txt");
        resource.setType("reference");
        resource.setContent("hello");
        content.setResource(Collections.singletonMap("README.txt", resource));
        
        JsonNode json = mapper.readTree(mapper.writeValueAsString(content));
        assertTrue(json.get("hubEntryId").isTextual());
        assertEquals("9223372036854775807", json.get("hubEntryId").asText());
        assertEquals("# find-skills", json.get("skillMd").asText());
        assertTrue(json.get("resource").has("README.txt"));
        assertFalse(json.has("sourceNamespaceId"));
        assertFalse(json.has("sourceResourceId"));
        assertFalse(json.has("sourceVersionId"));
        assertFalse(json.has("storage"));
        assertFalse(json.has("ext"));
        assertFalse(json.get("resource").get("README.txt").has("metadata"));
        
        HubSkillContent roundTrip = mapper.readValue(json.toString(), HubSkillContent.class);
        assertEquals(Long.MAX_VALUE, roundTrip.getHubEntryId());
        assertEquals("1.0.0", roundTrip.getSharedVersion());
        assertEquals("# find-skills", roundTrip.getSkillMd());
        assertEquals("README.txt", roundTrip.getResource().get("README.txt").getName());
        assertEquals("reference", roundTrip.getResource().get("README.txt").getType());
        assertEquals("hello", roundTrip.getResource().get("README.txt").getContent());
    }
    
    @Test
    void listRequestDefaultsAndFiltersRoundTrip() throws Exception {
        HubSkillListRequest request = mapper.readValue(
            "{\"query\":\"find-skills\",\"tagsAll\":[\"search\"]}",
            HubSkillListRequest.class);
        assertEquals(1, request.getPageNo());
        assertEquals(20, request.getPageSize());
        assertEquals("find-skills", request.getQuery());
        assertEquals(Collections.singletonList("search"), request.getTagsAll());
        assertFalse(mapper.readTree(mapper.writeValueAsString(request)).has("namespaceId"));
    }
    
    @Test
    void authorizedDetailRoundTripsLargeEntryIdAndSourceDeepLink() throws Exception {
        HubSkillDetail detail = new HubSkillDetail();
        detail.setHubEntryId(Long.MAX_VALUE);
        detail.setSourceReadable(true);
        detail.setSourceNamespaceId("team-a");
        detail.setSourceName("find-skills");
        
        JsonNode json = mapper.readTree(mapper.writeValueAsString(detail));
        assertTrue(json.get("hubEntryId").isTextual());
        assertFalse(json.has("sourceResourceId"));
        assertFalse(json.has("sourceVersionId"));
        assertFalse(json.has("storage"));
        assertFalse(json.has("ext"));
        
        HubSkillDetail roundTrip = mapper.readValue(json.toString(), HubSkillDetail.class);
        assertEquals(Long.MAX_VALUE, roundTrip.getHubEntryId());
        assertTrue(roundTrip.isSourceReadable());
        assertEquals("team-a", roundTrip.getSourceNamespaceId());
        assertEquals("find-skills", roundTrip.getSourceName());
        assertEquals("HUB", roundTrip.getOrigin());
    }
    
    @Test
    void originCannotBeOverriddenByInput() throws Exception {
        HubSkillSummary summary = mapper.readValue("{\"origin\":\"LOCAL\"}", HubSkillSummary.class);
        assertEquals("HUB", summary.getOrigin());
        assertEquals("HUB",
            mapper.readTree(mapper.writeValueAsString(summary)).get("origin").asText());
    }
    
    @Test
    void detailStatisticsAndBinaryDownloadRoundTripWithoutInternalFields() throws Exception {
        HubSkillDetail detail = new HubSkillDetail();
        detail.setVersionStatus("online");
        detail.setVersionDownloadCount(7L);
        detail.setCommitMsg("fixed commit");
        detail.setVersionLabels(Collections.singletonList("stable"));
        HubSkillDetail copy =
            mapper.readValue(mapper.writeValueAsString(detail), HubSkillDetail.class);
        assertEquals(7L, copy.getVersionDownloadCount());
        assertEquals(Collections.singletonList("stable"), copy.getVersionLabels());
        HubSkillDownload download = new HubSkillDownload();
        download.setHubEntryId(Long.MAX_VALUE);
        download.setSharedVersion("1.0.0");
        download.setZipBytes(new byte[] {0, (byte) 255});
        JsonNode json = mapper.readTree(mapper.writeValueAsString(download));
        assertEquals("9223372036854775807", json.path("hubEntryId").asText());
        assertFalse(json.has("storage"));
        assertFalse(json.has("sourceNamespaceId"));
        org.junit.jupiter.api.Assertions.assertArrayEquals(download.getZipBytes(),
            mapper.readValue(json.toString(), HubSkillDownload.class).getZipBytes());
    }
    
}
