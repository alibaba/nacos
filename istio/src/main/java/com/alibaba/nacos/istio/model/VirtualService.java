package com.alibaba.nacos.istio.model;
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

import java.util.List;

public class VirtualService {
    
    private String apiVersion;
    
    private String kind;
    
    private Metadata metadata;
    
    private Spec spec;
    
    public VirtualService() {}
    
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
        
        private List<String> hosts;
        
        private List<Http> http;
        
        public static class Http {
            
            private String name;
            
            private List<Match> match;
            
            private Rewrite rewrite;
            
            private List<Route> route;
    
            private Redirect redirect;
            
            public static class Match {
                
                private Uri uri;
                
                public static class Uri {
                    
                    private String prefix;
    
                    private String exact;
    
                    private String regex;
    
                    public String getPrefix() {
                        return prefix;
                    }
                    
                    public void setPrefix(String prefix) {
                        this.prefix = prefix;
                    }
    
                    public String getExact() {
                        return exact;
                    }
    
                    public void setExact(String exact) {
                        this.exact = exact;
                    }
    
                    public String getRegex() {
                        return regex;
                    }
    
                    public void setRegex(String regex) {
                        this.regex = regex;
                    }
                }
                
                public Uri getUri() {
                    return uri;
                }
                
                public void setUri(Uri uri) {
                    this.uri = uri;
                }
            }
            
            public static class Rewrite {
                
                private String uri;
                
                public String getUri() {
                    return uri;
                }
                
                public void setUri(String uri) {
                    this.uri = uri;
                }
            }
            
            public static class Route {
                
                private Destination destination;
                
                public static class Destination {
                    
                    private String host;
                    
                    private String subset;
    
                    private Port port;
    
                    public static class Port {
        
                        private int number;
        
                        public int getNumber() {
                            return number;
                        }
        
                        public void setNumber(int number) {
                            this.number = number;
                        }
                    }
    
                    public Port getPort() {
                        return port;
                    }
    
                    public void setPort(Port port) {
                        this.port = port;
                    }
                    
                    public String getHost() {
                        return host;
                    }
                    
                    public void setHost(String host) {
                        this.host = host;
                    }
                    
                    public String getSubset() {
                        return subset;
                    }
                    
                    public void setSubset(String subset) {
                        this.subset = subset;
                    }
                }
                
                public Destination getDestination() {
                    return destination;
                }
                
                public void setDestination(Destination destination) {
                    this.destination = destination;
                }
            }
    
            public static class Redirect {
        
                private String uri;
                
                private String authority;
        
                public String getUri() {
                    return uri;
                }
        
                public void setUri(String uri) {
                    this.uri = uri;
                }
        
                public String getAuthority() {
                    return authority;
                }
        
                public void setAuthority(String authority) {
                    this.authority = authority;
                }
            }
            
            public String getName() {
                return name;
            }
            
            public void setName(String name) {
                this.name = name;
            }
            
            public List<Match> getMatch() {
                return match;
            }
            
            public void setMatch(List<Match> match) {
                this.match = match;
            }
            
            public Rewrite getRewrite() {
                return rewrite;
            }
            
            public void setRewrite(Rewrite rewrite) {
                this.rewrite = rewrite;
            }
            
            public List<Route> getRoute() {
                return route;
            }
            
            public void setRoute(List<Route> route) {
                this.route = route;
            }
    
            public Redirect getRedirect() {
                return redirect;
            }
    
            public void setRedirect(Redirect redirect) {
                this.redirect = redirect;
            }
        }
        
        public List<String> getHosts() {
            return hosts;
        }
        
        public void setHosts(List<String> hosts) {
            this.hosts = hosts;
        }
        
        public List<Http> getHttp() {
            return http;
        }
        
        public void setHttp(List<Http> http) {
            this.http = http;
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
