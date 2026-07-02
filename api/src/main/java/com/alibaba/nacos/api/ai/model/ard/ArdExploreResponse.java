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

package com.alibaba.nacos.api.ai.model.ard;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * ARD Explore facets response.
 *
 * @author nacos
 */
public class ArdExploreResponse {
    
    private String resultType = "facets";
    
    private Map<String, FacetResult> facets = new LinkedHashMap<>();
    
    public String getResultType() {
        return resultType;
    }
    
    public void setResultType(String resultType) {
        this.resultType = resultType;
    }
    
    public Map<String, FacetResult> getFacets() {
        return facets;
    }
    
    public void setFacets(Map<String, FacetResult> facets) {
        this.facets = facets;
    }
    
    /**
     * Facet buckets for one field.
     */
    public static class FacetResult {
        
        private List<FacetBucket> buckets = new ArrayList<>();
        
        private Integer otherCount;
        
        public List<FacetBucket> getBuckets() {
            return buckets;
        }
        
        public void setBuckets(List<FacetBucket> buckets) {
            this.buckets = buckets;
        }
        
        public Integer getOtherCount() {
            return otherCount;
        }
        
        public void setOtherCount(Integer otherCount) {
            this.otherCount = otherCount;
        }
    }
    
    /**
     * Single facet bucket.
     */
    public static class FacetBucket {
        
        private String value;
        
        private Integer count;
        
        public String getValue() {
            return value;
        }
        
        public void setValue(String value) {
            this.value = value;
        }
        
        public Integer getCount() {
            return count;
        }
        
        public void setCount(Integer count) {
            this.count = count;
        }
    }
}
