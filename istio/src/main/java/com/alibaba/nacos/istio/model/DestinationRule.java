/*
 * Copyright 1999-2023 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.istio.model;

import java.util.List;

public class DestinationRule {
    
    private String apiVersion;
    
    private String kind;
    
    private Metadata metadata;
    
    private Spec spec;
    
    public static class Metadata {
        
        private String name;
        
        private String namespace;
        
        public String getName() {
            return name;
        }
        
        public void setName(String name) {
            this.name = name;
        }
        
        public String getNamespace() {
            return namespace;
        }
        
        public void setNamespace(String namespace) {
            this.namespace = namespace;
        }
    }
    
    public static class Spec {
        
        private String host;
        
        private List<Subset> subsets;
        
        public static class Subset {
            
            private String name;
            
            private Labels labels;
            
            public static class Labels {
                
                private String version;
                
                public String getVersion() {
                    return version;
                }
                
                public void setVersion(String version) {
                    this.version = version;
                }
            }
            
            public String getName() {
                return name;
            }
            
            public void setName(String name) {
                this.name = name;
            }
            
            public Labels getLabels() {
                return labels;
            }
            
            public void setLabels(Labels labels) {
                this.labels = labels;
            }
        }
        
        public String getHost() {
            return host;
        }
        
        public void setHost(String host) {
            this.host = host;
        }
        
        public List<Subset> getSubsets() {
            return subsets;
        }
        
        public void setSubsets(List<Subset> subsets) {
            this.subsets = subsets;
        }
    }
    
    public String getApiVersion() {
        return apiVersion;
    }
    
    public void setApiVersion(String apiVersion) {
        this.apiVersion = apiVersion;
    }
    
    public String getKind() {
        return kind;
    }
    
    public void setKind(String kind) {
        this.kind = kind;
    }
    
    public Metadata getMetadata() {
        return metadata;
    }
    
    public void setMetadata(Metadata metadata) {
        this.metadata = metadata;
    }
    
    public Spec getSpec() {
        return spec;
    }
    
    public void setSpec(Spec spec) {
        this.spec = spec;
    }
}
