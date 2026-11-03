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

package com.alibaba.nacos.api.ai.builder;

import com.alibaba.nacos.api.ai.model.a2a.AgentCard;
import com.alibaba.nacos.api.ai.model.a2a.AgentInterface;
import com.alibaba.nacos.api.ai.model.agent.AgentCallInterface;
import com.alibaba.nacos.api.ai.model.agent.AgentEndpointRegistrationBatch;
import com.alibaba.nacos.api.ai.model.agent.AgentProvider;
import com.alibaba.nacos.api.ai.model.agent.Endpoint;
import com.alibaba.nacos.api.ai.model.agent.EndpointSource;
import com.alibaba.nacos.api.ai.model.agent.client.AgentPublishRequest;
import com.alibaba.nacos.api.ai.utils.A2aAgentCardUtils;
import com.alibaba.nacos.api.ai.utils.A2aRequestUtils;
import com.alibaba.nacos.api.ai.utils.A2aSecurityUtils;
import com.alibaba.nacos.api.ai.utils.AgentModelValidator;
import com.alibaba.nacos.api.ai.utils.EndpointCanonicalizer;
import com.alibaba.nacos.api.ai.utils.RadModelValidator;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.utils.json.JsonUtils;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Offline builders for definition publication and complete runtime endpoint registration.
 * Builders are not thread safe. Build results and caller inputs never share mutable state.
 * Actual publication, namespace binding and authorization belong to the invoking SDK service.
 * @author Nacos
 * @since 3.3.0
 */
public final class AgentRequestBuilder {
    
    private AgentRequestBuilder() {
    }
    
    /**
     * Start an offline definition publication request; autoSubmit defaults to false.
     * @return new protocol-neutral builder
     */
    public static PublishAgentBuilder publishAgent() {
        return new PublishAgentBuilder();
    }
    
    /**
     * Start a complete endpoint replacement for the invoking publisher.
     * @return new protocol-neutral builder
     */
    public static RegisterEndpointsBuilder registerEndpoints() {
        return new RegisterEndpointsBuilder();
    }
    
    /** Common publication fields, returning the concrete fluent builder type. */
    public abstract static class PublishBuilder<B extends PublishBuilder<B>> {
        
        protected final AgentPublishRequest request;
        
        private PublishBuilder(AgentPublishRequest request) {
            this.request = request;
        }
        
        protected abstract B self();
        
        /**
         * Set agentName using the existing request contract.
         * @param value requested value
         * @return this builder
         */
        public B agentName(String value) {
            request.setAgentName(value);
            return self();
        }
        
        /**
         * Set version using the existing request contract.
         * @param value requested value
         * @return this builder
         */
        public B version(String value) {
            request.setVersion(value);
            return self();
        }
        
        /**
         * Set displayName using the existing request contract.
         * @param value requested value
         * @return this builder
         */
        public B displayName(String value) {
            request.setDisplayName(value);
            return self();
        }
        
        /**
         * Set description using the existing request contract.
         * @param value requested value
         * @return this builder
         */
        public B description(String value) {
            request.setDescription(value);
            return self();
        }
        
        /**
         * Set iconUrl using the existing request contract.
         * @param value requested value
         * @return this builder
         */
        public B iconUrl(String value) {
            request.setIconUrl(value);
            return self();
        }
        
        /**
         * Set provider using the existing request contract.
         * @param value requested value
         * @return this builder
         */
        public B provider(AgentProvider value) {
            request.setProvider(copy(value, AgentProvider.class));
            return self();
        }
        
        /**
         * Set tags using the existing request contract.
         * @param value requested value
         * @return this builder
         */
        public B tags(List<String> value) {
            AgentPublishRequest holder = new AgentPublishRequest();
            holder.setTags(value);
            request.setTags(copy(holder, AgentPublishRequest.class).getTags());
            return self();
        }
        
        /**
         * Set extensions using the existing request contract.
         * @param value requested value
         * @return this builder
         */
        public B extensions(Map<String, Object> value) {
            AgentPublishRequest holder = new AgentPublishRequest();
            holder.setExtensions(value);
            request.setExtensions(copy(holder, AgentPublishRequest.class).getExtensions());
            return self();
        }
        
        /**
         * Set author using the existing request contract.
         * @param value requested value
         * @return this builder
         */
        public B author(String value) {
            request.setAuthor(value);
            return self();
        }
        
        /**
         * Set changeDescription using the existing request contract.
         * @param value requested value
         * @return this builder
         */
        public B changeDescription(String value) {
            request.setChangeDescription(value);
            return self();
        }
        
        /**
         * Set basedOnVersion using the existing request contract.
         * @param value requested value
         * @return this builder
         */
        public B basedOnVersion(String value) {
            request.setBasedOnVersion(value);
            return self();
        }
        
        /**
         * Set autoSubmit using the existing request contract.
         * @param value requested value
         * @return this builder
         */
        public B autoSubmit(boolean value) {
            request.setAutoSubmit(value);
            return self();
        }
        
        /**
         * Build an independent request without contacting Nacos.
         * @return publication request
         * @throws IllegalArgumentException when identity or content is invalid
         */
        public AgentPublishRequest build() {
            request.validate();
            if (request.getCallInterfaces() != null) {
                if (request.getCallInterfaces().isEmpty()
                    || request.getCallInterfaces().size() > 16) {
                    throw new IllegalArgumentException(
                        "callInterfaces must contain 1 to 16 entries");
                }
                Set<String> protocols = new HashSet<String>();
                for (AgentCallInterface call : request.getCallInterfaces()) {
                    AgentModelValidator.validateCallInterface(call);
                    if (!protocols.add(call.getProtocol())) {
                        throw new IllegalArgumentException("Duplicate CallInterface protocol");
                    }
                }
            }
            return copy(request, AgentPublishRequest.class);
        }
    }
    
    /** Protocol-neutral publication builder. */
    public static final class PublishAgentBuilder extends PublishBuilder<PublishAgentBuilder> {
        
        private PublishAgentBuilder() {
            super(new AgentPublishRequest());
        }
        
        @Override
        protected PublishAgentBuilder self() {
            return this;
        }
        
        /**
         * Set complete protocol-neutral call interfaces.
         * @param value protocol declarations
         * @return this builder
         */
        public PublishAgentBuilder callInterfaces(List<AgentCallInterface> value) {
            if (value != null) {
                for (AgentCallInterface call : value) {
                    AgentModelValidator.validateCallInterface(call);
                }
            }
            AgentPublishRequest holder = new AgentPublishRequest();
            holder.setCallInterfaces(value);
            request.setCallInterfaces(copy(holder, AgentPublishRequest.class).getCallInterfaces());
            return this;
        }
        
        /**
         * Select A2A conversion, retaining generic publication options.
         * @return independent A2A builder
         */
        public A2aPublishAgentBuilder a2a() {
            if (request.getCallInterfaces() != null) {
                throw new IllegalArgumentException(
                    "A2A publication derives callInterfaces from its Card");
            }
            return new A2aPublishAgentBuilder(copy(request, AgentPublishRequest.class));
        }
    }
    
    /** Publication from a complete native Card. */
    public static final class A2aPublishAgentBuilder
        extends PublishBuilder<A2aPublishAgentBuilder> {
        
        private String cardName;
        
        private String cardVersion;
        
        private List<EndpointSource> sourceOrder =
            Arrays.asList(EndpointSource.RUNTIME, EndpointSource.DECLARED);
        
        private A2aPublishAgentBuilder(AgentPublishRequest request) {
            super(request);
        }
        
        @Override
        protected A2aPublishAgentBuilder self() {
            return this;
        }
        
        /**
         * Copy a complete Card, deriving identity, presentation and protocol declaration.
         * Presentation setters after this method override the derived presentation fields.
         * @param source native Card
         * @return this builder
         * @throws IllegalArgumentException when conversion or identity validation fails
         */
        public A2aPublishAgentBuilder fromAgentCard(AgentCard source) {
            if (source == null) {
                throw new IllegalArgumentException("AgentCard must not be null");
            }
            // Validate the original security containers before a codec can omit explicit nulls.
            AgentCard security = new AgentCard();
            security.setSecurity(source.getSecurity());
            security.setSecurityRequirements(source.getSecurityRequirements());
            security.setSkills(source.getSkills());
            A2aSecurityUtils.normalize(security);
            if (source.getSupportedInterfaces() != null) {
                A2aRequestUtils.fromAgentInterfaces(source.getSupportedInterfaces());
            }
            AgentCard card = copy(source, AgentCard.class);
            try {
                A2aAgentCardUtils.validateAgentCard(card);
            } catch (NacosException invalid) {
                throw new IllegalArgumentException("Invalid A2A AgentCard", invalid);
            }
            agree(request.getAgentName(), card.getName(), "agentName");
            agree(request.getVersion(), card.getVersion(), "version");
            A2aRequestUtils.populateDraft(request, card, sourceOrder,
                A2aRequestUtils.fromAgentInterfaces(card.getSupportedInterfaces()), true);
            cardName = card.getName();
            cardVersion = card.getVersion();
            return this;
        }
        
        /**
         * Set the preferred source order; both DECLARED and RUNTIME must occur exactly once.
         * @param value source preference
         * @return this builder
         */
        public A2aPublishAgentBuilder endpointSourceOrder(List<EndpointSource> value) {
            if (value == null || value.size() != 2 || !value.contains(EndpointSource.DECLARED)
                || !value.contains(EndpointSource.RUNTIME)) {
                throw new IllegalArgumentException(
                    "Both endpoint sources are required exactly once");
            }
            sourceOrder = new ArrayList<EndpointSource>(value);
            return this;
        }
        
        @Override
        public AgentPublishRequest build() {
            if (cardName == null || !Objects.equals(cardName, request.getAgentName())
                || !Objects.equals(cardVersion, request.getVersion())) {
                throw new IllegalArgumentException("Publication identity must match its AgentCard");
            }
            request.getCallInterfaces().get(0)
                .setEndpointSourceOrder(new ArrayList<EndpointSource>(sourceOrder));
            return super.build();
        }
    }
    
    /** Common runtime registration defaults. */
    public abstract static class EndpointBuilder<B extends EndpointBuilder<B>> {
        
        protected final AgentEndpointRegistrationBatch request;
        
        private EndpointBuilder(AgentEndpointRegistrationBatch request) {
            this.request = request;
        }
        
        protected abstract B self();
        
        /**
         * Set agentName using the existing batch contract.
         * @param value requested value
         * @return this builder
         */
        public B agentName(String value) {
            request.setAgentName(value);
            return self();
        }
        
        /**
         * Set runtimeVersion using the existing batch contract.
         * @param value requested value
         * @return this builder
         */
        public B runtimeVersion(String value) {
            request.setRuntimeVersion(value);
            return self();
        }
        
        /**
         * Set versionRange using the existing batch contract.
         * @param value requested value
         * @return this builder
         */
        public B versionRange(String value) {
            request.setVersionRange(value);
            return self();
        }
        
        /**
         * Build an isolated, canonical complete batch with resolved version bindings.
         * The synthetic namespace is used only for within-batch natural-key validation.
         * @return registration batch
         * @throws IllegalArgumentException when any endpoint or binding is invalid
         */
        public AgentEndpointRegistrationBatch build() {
            RadModelValidator.validate("validation", request);
            AgentEndpointRegistrationBatch result = new AgentEndpointRegistrationBatch();
            result.setAgentName(request.getAgentName());
            result.setProtocol(request.getProtocol());
            result.setRuntimeVersion(request.getRuntimeVersion());
            result.setVersionRange(request.getVersionRange());
            List<Endpoint> endpoints = new ArrayList<Endpoint>();
            for (Endpoint endpoint : request.getEndpoints()) {
                Endpoint copy = EndpointCanonicalizer.canonicalize(endpoint);
                copy.setBindings(Arrays.asList(EndpointCanonicalizer.canonicalizeRuntimeBinding(
                    endpoint, request.getRuntimeVersion(), request.getVersionRange())));
                endpoints.add(copy);
            }
            result.setEndpoints(endpoints);
            return result;
        }
    }
    
    /** Protocol-neutral complete runtime publication. */
    public static final class RegisterEndpointsBuilder
        extends EndpointBuilder<RegisterEndpointsBuilder> {
        
        private RegisterEndpointsBuilder() {
            super(new AgentEndpointRegistrationBatch());
        }
        
        @Override
        protected RegisterEndpointsBuilder self() {
            return this;
        }
        
        /**
         * Set the protocol token.
         * @param value protocol
         * @return this builder
         */
        public RegisterEndpointsBuilder protocol(String value) {
            request.setProtocol(value);
            return this;
        }
        
        /**
         * Replace the complete endpoint list, retaining optional per-endpoint bindings.
         * @param value endpoints
         * @return this builder
         */
        public RegisterEndpointsBuilder endpoints(List<Endpoint> value) {
            if (value == null) {
                throw new IllegalArgumentException("endpoints must not be null");
            }
            List<Endpoint> endpoints = new ArrayList<Endpoint>();
            for (Endpoint endpoint : value) {
                endpoints.add(EndpointCanonicalizer.canonicalize(endpoint));
            }
            request.setEndpoints(endpoints);
            return this;
        }
        
        /**
         * Select A2A interface conversion while retaining identity and version defaults.
         * @return independent A2A builder
         */
        public A2aRegisterEndpointsBuilder a2a() {
            agree(request.getProtocol(), "a2a", "protocol");
            if (request.getEndpoints() != null) {
                throw new IllegalArgumentException("A2A endpoints derive from native interfaces");
            }
            return new A2aRegisterEndpointsBuilder(
                copy(request, AgentEndpointRegistrationBatch.class));
        }
    }
    
    /** Runtime endpoints derived from native interfaces, independently of definition publication. */
    public static final class A2aRegisterEndpointsBuilder
        extends EndpointBuilder<A2aRegisterEndpointsBuilder> {
        
        private A2aRegisterEndpointsBuilder(AgentEndpointRegistrationBatch request) {
            super(request);
            request.setProtocol("a2a");
        }
        
        @Override
        protected A2aRegisterEndpointsBuilder self() {
            return this;
        }
        
        /**
         * Extract name, business version and optional default supportedInterfaces from a Card.
         * No other Card fields are required. A subsequent fromAgentInterfaces replaces defaults.
         * @param card native Card supplying registration defaults
         * @return this builder
         */
        public A2aRegisterEndpointsBuilder fromAgentCard(AgentCard card) {
            if (card == null) {
                throw new IllegalArgumentException("AgentCard must not be null");
            }
            request.setAgentName(card.getName());
            request.setRuntimeVersion(card.getVersion());
            request.setEndpoints(card.getSupportedInterfaces() == null ? null
                : A2aRequestUtils.fromAgentInterfaces(card.getSupportedInterfaces()));
            return this;
        }
        
        /**
         * Replace the complete interface list, overriding any Card defaults.
         * @param value actual instance interfaces
         * @return this builder
         */
        public A2aRegisterEndpointsBuilder fromAgentInterfaces(List<AgentInterface> value) {
            request.setEndpoints(A2aRequestUtils.fromAgentInterfaces(value));
            return this;
        }
    }
    
    private static void agree(String explicit, String inferred, String field) {
        if (explicit != null && !explicit.equals(inferred)) {
            throw new IllegalArgumentException(field + " conflicts with the A2A input");
        }
    }
    
    private static <T> T copy(Object source, Class<T> type) {
        if (source == null) {
            return null;
        }
        try {
            return JsonUtils.toObj(JsonUtils.toJson(source), type);
        } catch (RuntimeException invalid) {
            throw new IllegalArgumentException("Cannot copy Agent request input", invalid);
        }
    }
}
