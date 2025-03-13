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

import com.alibaba.nacos.api.model.v2.Result;
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.common.utils.VersionUtils;
import io.swagger.v3.core.converter.AnnotatedType;
import io.swagger.v3.core.converter.ModelConverters;
import io.swagger.v3.core.converter.ResolvedSchema;
import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.tags.Tag;
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

import javax.annotation.PostConstruct;
import java.lang.reflect.ParameterizedType;
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
        return (operation, method) -> {
            ApiResponses responses = operation.getResponses();
            if (method.getMethod().getReturnType().equals(Result.class)) {
                
                ResolvedSchema baseRespSchema = ModelConverters.getInstance()
                        .resolveAsResolvedSchema(new AnnotatedType(Result.class));
                
                Map<String, Schema> fieldsSchema = new LinkedHashMap<>();
                fieldsSchema.putAll(baseRespSchema.schema.getProperties());
                
                Class actualTypeArgument = (Class) ((ParameterizedType) method.getMethod()
                        .getGenericReturnType()).getActualTypeArguments()[0];
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
                    // override data field schema
                    fieldsSchema.compute("data",
                            (k, originDataSchema) -> new ObjectSchema().description(originDataSchema.getDescription())
                                    .nullable(originDataSchema.getNullable()));
                    respSchemaName = "Result<Object>";
                }
                Schema schema = new ObjectSchema().type("object").properties(fieldsSchema).name(respSchemaName);
                
                // // replace ref '#/components/schemas/RespXxx' to '#/components/schemas/Resp<Xxx>'
                for (ApiResponse apiResponse : responses.values()) {
                    for (io.swagger.v3.oas.models.media.MediaType mediaType : apiResponse.getContent().values()) {
                        Schema originApiResponseSchema = mediaType.getSchema();
                        if (originApiResponseSchema.get$ref() != null && originApiResponseSchema.get$ref()
                                .startsWith("#/components/schemas/Result")) {
                            originApiResponseSchema.$ref(schema.getName());
                        }
                    }
                }
                
                respSchemas.put(respSchemaName, schema);
            }
            
            return operation;
        };
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
}
