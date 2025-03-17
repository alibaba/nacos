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

package com.alibaba.nacos.console.config;

import com.alibaba.nacos.api.model.Page;
import com.alibaba.nacos.api.model.v2.Result;
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.common.utils.VersionUtils;
import io.swagger.v3.core.converter.AnnotatedType;
import io.swagger.v3.core.converter.ModelConverters;
import io.swagger.v3.core.converter.ResolvedSchema;
import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.media.ArraySchema;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import org.springdoc.core.customizers.OpenApiBuilderCustomizer;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springdoc.core.properties.SpringDocConfigProperties;
import org.springdoc.core.utils.PropertyResolverUtils;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.http.MediaType;
import org.springframework.web.method.HandlerMethod;

import javax.annotation.PostConstruct;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * spring doc configuration.
 *
 * @author xiweng.yy
 */
@Configuration
@OpenAPIDefinition(info = @io.swagger.v3.oas.annotations.info.Info(title = "nacos.console.api.title", description = "nacos.console.api.description", license = @io.swagger.v3.oas.annotations.info.License(name = "Apache 2.0", url = "https://github.com/alibaba/nacos/blob/develop/LICENSE")))
public class DocConfig {
    
    private final SpringDocConfigProperties springDocConfigProperties;
    
    public DocConfig(SpringDocConfigProperties springDocConfigProperties) {
        this.springDocConfigProperties = springDocConfigProperties;
    }
    
    @PostConstruct
    public void initSpringDocProperties() {
        springDocConfigProperties.setDefaultProducesMediaType(MediaType.APPLICATION_JSON_VALUE);
    }
    
    @Bean
    public OpenApiCustomizer nacosConsoleOpenApiCustomizer() {
        return openApi -> openApi.getInfo().version(VersionUtils.version);
    }
    
    @Bean
    public OpenApiBuilderCustomizer nacosConsoleOpenApiBuilderCustomizer() {
        return openApiService -> {
            Map<String, Object> needGenerateControllers = openApiService.getMappingsMap().entrySet().parallelStream()
                    .filter(controller -> (AnnotationUtils.findAnnotation(controller.getValue().getClass(), Tag.class)
                            != null)).collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
            openApiService.getMappingsMap().clear();
            openApiService.getMappingsMap().putAll(needGenerateControllers);
            openApiService.getControllerAdviceMap().clear();
        };
    }
    
    private Map<String, Schema> respSchemas = new ConcurrentHashMap<>();
    
    @Bean
    public OpenApiCustomizer genericSchemaOpenApiCustomizer() {
        return openApi -> {
            Map<String, Schema> schemas = openApi.getComponents().getSchemas();
            Set<String> shouldRemoveRespSchemas = schemas.keySet().stream()
                    .filter(schema -> schema.startsWith(Result.class.getSimpleName())).collect(Collectors.toSet());
            shouldRemoveRespSchemas.forEach(schemas::remove);
            schemas.putAll(respSchemas);
        };
    }
    
    @Bean
    public OperationCustomizer genericSchemaOperationCustomize() {
        return new NacosGenericSchemaOperationCustomize();
    }
    
    @Bean
    public OperationCustomizer exampleI18nOperationCustomize(PropertyResolverUtils propertyResolverUtils) {
        return (operation, handlerMethod) -> {
            operation.getResponses().forEach((status, apiResponse) -> {
                apiResponse.getContent();
                apiResponse.getContent().forEach((key, value) -> {
                    Schema<?> schema = value.getSchema();
                    Object example = schema.getExample();
                    if (!(example instanceof String)) {
                        return;
                    }
                    String i18nExample = propertyResolverUtils.resolve((String) example, null);
                    if (key.equals(MediaType.APPLICATION_JSON_VALUE)) {
                        schema.setExample(JacksonUtils.toObj(i18nExample));
                    } else {
                        schema.setExample(i18nExample);
                    }
                });
            });
            return operation;
        };
        
    }
    
    private class NacosGenericSchemaOperationCustomize implements OperationCustomizer {
        
        @Override
        public Operation customize(Operation operation, HandlerMethod method) {
            if (!method.getMethod().getReturnType().equals(Result.class)) {
                return operation;
            }
            Map<String, Schema> fieldsSchema = getResultFieldsSchema();
            Type actualTypeArgument = getGenericType((ParameterizedType) method.getMethod().getGenericReturnType());
            Schema newSchema = null;
            if (actualTypeArgument instanceof Class<?>) {
                newSchema = buildDirectGenericSchema((Class<?>) actualTypeArgument, fieldsSchema);
            } else if (actualTypeArgument instanceof ParameterizedType) {
                newSchema = buildGenericSchema((ParameterizedType) actualTypeArgument, fieldsSchema);
            } else {
                return operation;
            }
            ApiResponses responses = operation.getResponses();
            // // replace ref '#/components/schemas/ResultXxx' to '#/components/schemas/Result<Xxx>'
            for (ApiResponse apiResponse : responses.values()) {
                for (io.swagger.v3.oas.models.media.MediaType mediaType : apiResponse.getContent().values()) {
                    Schema originApiResponseSchema = mediaType.getSchema();
                    if (originApiResponseSchema.get$ref() != null && originApiResponseSchema.get$ref()
                            .startsWith("#/components/schemas/Result")) {
                        originApiResponseSchema.$ref(newSchema.getName());
                    }
                }
            }
            respSchemas.put(newSchema.getName(), newSchema);
            return operation;
        }
        
        private Map<String, Schema> getResultFieldsSchema() {
            Map<String, Schema> result = new LinkedHashMap<>();
            ResolvedSchema baseRespSchema = ModelConverters.getInstance()
                    .resolveAsResolvedSchema(new AnnotatedType(Result.class));
            result.putAll(baseRespSchema.schema.getProperties());
            return result;
        }
        
        private Type getGenericType(ParameterizedType parameterizedType) {
            return parameterizedType.getActualTypeArguments()[0];
        }
        
        private Schema buildDirectGenericSchema(Class<?> actualTypeArgument, Map<String, Schema> fieldsSchema) {
            ResolvedSchema resolvedSchema = ModelConverters.getInstance()
                    .resolveAsResolvedSchema(new AnnotatedType(actualTypeArgument));
            String respSchemaName;
            if (resolvedSchema.schema != null) {
                // override data field schema
                if (resolvedSchema.referencedSchemas.isEmpty()) {
                    fieldsSchema.put("data", resolvedSchema.schema);
                } else {
                    fieldsSchema.put("data", new ObjectSchema().$ref(actualTypeArgument.getSimpleName()));
                }
                respSchemas.putAll(resolvedSchema.referencedSchemas);
                respSchemaName = "Result<" + actualTypeArgument.getSimpleName() + ">";
            } else {
                respSchemaName = buildResultObjectSchema(fieldsSchema);
            }
            return new ObjectSchema().type("object").properties(fieldsSchema).name(respSchemaName);
        }
        
        private Schema buildGenericSchema(ParameterizedType parameterizedType, Map<String, Schema> fieldsSchema) {
            Class<?> rawType = (Class<?>) parameterizedType.getRawType();
            String resultSchemaName = "";
            Schema dataSchema = null;
            if (rawType.isAssignableFrom(Collection.class)) {
                Type actualTypeArgument = getGenericType(parameterizedType);
                if (!(actualTypeArgument instanceof Class)) {
                    throw new UnsupportedOperationException(
                            "Not supported generate Result Schema with multiple Generic");
                }
                dataSchema = new ArraySchema();
                Class<?> actualClass = (Class<?>) actualTypeArgument;
                ResolvedSchema resolvedSchema = ModelConverters.getInstance()
                        .resolveAsResolvedSchema(new AnnotatedType(actualTypeArgument));
                dataSchema.setName(rawType.getSimpleName() + "<" + actualClass.getSimpleName() + ">");
                if (resolvedSchema.schema != null) {
                    dataSchema.setItems(resolvedSchema.schema);
                    if (!resolvedSchema.referencedSchemas.isEmpty()) {
                        respSchemas.putAll(resolvedSchema.referencedSchemas);
                    }
                    resultSchemaName = "Result<" + dataSchema.getName() + ">";
                } else {
                    resultSchemaName = buildResultObjectSchema(fieldsSchema);
                }
            } else if (rawType.isAssignableFrom(Map.class)) {
                throw new UnsupportedOperationException("TODO support generate Result Schema with Map.");
            } else if (rawType.isAssignableFrom(Page.class)) {
                throw new UnsupportedOperationException("TODO support generate Result Schema with Page.");
            } else {
                throw new UnsupportedOperationException(
                        String.format("Not supported generate Result Schema with %s.", rawType.getCanonicalName()));
            }
            fieldsSchema.put("data", dataSchema);
            return new ObjectSchema().type("object").properties(fieldsSchema).name(resultSchemaName);
        }
        
        private String buildResultObjectSchema(Map<String, Schema> fieldsSchema) {
            fieldsSchema.compute("data",
                    (k, originDataSchema) -> new ObjectSchema().description(originDataSchema.getDescription())
                            .nullable(originDataSchema.getNullable()));
            return "Result<Object>";
        }
        
    }
}
