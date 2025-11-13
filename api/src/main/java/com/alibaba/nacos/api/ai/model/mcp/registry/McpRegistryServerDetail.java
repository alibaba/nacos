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

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * McpRegistryServerDetail (renamed from ServerDetail).
 *
 * @author xinluo
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class McpRegistryServerDetail {

    private String name;

    private String description;

    private String title;

    private Repository repository;

    private String version;

    private String websiteUrl;

    private List<Icon> icons;

    @JsonProperty("$schema")
    private String schema;

    private List<Package> packages;

    private List<Remote> remotes;

    @JsonProperty("_meta")
    private Meta meta;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Meta {
        @JsonProperty("io.modelcontextprotocol.registry/publisher-provided")
        private Map<String, Object> publisherMeta;

        @JsonAnySetter
        private Map<String, Object> extensionMeta = new HashMap<>();

        public Map<String, Object> getPublisherMeta() {
            return publisherMeta;
        }

        public void setPublisherMeta(Map<String, Object> publisherMeta) {
            this.publisherMeta = publisherMeta;
        }

        @JsonAnyGetter
        public Map<String, Object> getExtensionMeta() {
            return extensionMeta;
        }
    }

    public String getSchema() {
        return schema;
    }

    public void setSchema(String schema) {
        this.schema = schema;
    }

    public List<Package> getPackages() {
        return packages;
    }

    public void setPackages(List<Package> packages) {
        this.packages = packages;
    }

    public List<Remote> getRemotes() {
        return remotes;
    }

    public void setRemotes(List<Remote> remotes) {
        this.remotes = remotes;
    }

    public Meta getMeta() {
        return meta;
    }

    public void setMeta(Meta meta) {
        this.meta = meta;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public Repository getRepository() {
        return repository;
    }

    public void setRepository(Repository repository) {
        this.repository = repository;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public String getWebsiteUrl() {
        return websiteUrl;
    }

    public void setWebsiteUrl(String websiteUrl) {
        this.websiteUrl = websiteUrl;
    }

    public List<Icon> getIcons() {
        return icons;
    }

    public void setIcons(List<Icon> icons) {
        this.icons = icons;
    }
}
