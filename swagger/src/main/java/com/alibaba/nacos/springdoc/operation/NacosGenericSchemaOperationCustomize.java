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

package com.alibaba.nacos.springdoc.operation;

import com.alibaba.nacos.api.model.Page;
import com.alibaba.nacos.api.model.v2.Result;
import com.alibaba.nacos.springdoc.cache.SchemaCache;
import io.swagger.v3.core.converter.AnnotatedType;
import io.swagger.v3.core.converter.ModelConverters;
import io.swagger.v3.core.converter.ResolvedSchema;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.media.ArraySchema;
import io.swagger.v3.oas.models.media.MapSchema;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import org.springdoc.core.customizers.GlobalOperationCustomizer;
import org.springframework.web.method.HandlerMethod;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.WildcardType;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * spring doc nacos generic schema operation customize.
 *
 * @author xiweng.yy
 */
public class NacosGenericSchemaOperationCustomize implements GlobalOperationCustomizer {
    
    private final SchemaCache schemaCache;
    
    public NacosGenericSchemaOperationCustomize(SchemaCache schemaCache) {
        this.schemaCache = schemaCache;
    }
    
    @Override
    public Operation customize(Operation operation, HandlerMethod method) {
        if (!method.getMethod().getReturnType().equals(Result.class)) {
            return operation;
        }
        Map<String, Schema> fieldsSchema = getResultFieldsSchema(Result.class);
        Type actualTypeArgument = getGenericType((ParameterizedType) method.getMethod().getGenericReturnType(), 0);
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
        schemaCache.put(newSchema.getName(), newSchema);
        return operation;
    }
    
    private Map<String, Schema> getResultFieldsSchema(Class<?> clazz) {
        Map<String, Schema> result = new LinkedHashMap<>();
        ResolvedSchema baseRespSchema = ModelConverters.getInstance().resolveAsResolvedSchema(new AnnotatedType(clazz));
        result.putAll(baseRespSchema.schema.getProperties());
        return result;
    }
    
    private Type getGenericType(ParameterizedType parameterizedType, int index) {
        return parameterizedType.getActualTypeArguments()[index];
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
            schemaCache.putAll(resolvedSchema.referencedSchemas);
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
        if (Collection.class.isAssignableFrom(rawType)) {
            Type actualTypeArgument = getGenericType(parameterizedType, 0);
            if (isSupportType(actualTypeArgument)) {
                throw new UnsupportedOperationException("Not supported generate Result Schema with multiple Generic");
            }
            dataSchema = new ArraySchema();
            Class<?> actualClass = getActualClass(actualTypeArgument);
            ResolvedSchema resolvedSchema = ModelConverters.getInstance()
                    .resolveAsResolvedSchema(new AnnotatedType(actualTypeArgument));
            dataSchema.setName(rawType.getSimpleName() + "<" + actualClass.getSimpleName() + ">");
            if (resolvedSchema.schema != null) {
                dataSchema.setItems(resolvedSchema.schema);
                if (!resolvedSchema.referencedSchemas.isEmpty()) {
                    schemaCache.putAll(resolvedSchema.referencedSchemas);
                }
                resultSchemaName = "Result<" + dataSchema.getName() + ">";
            } else {
                resultSchemaName = buildResultObjectSchema(fieldsSchema);
            }
        } else if (Map.class.isAssignableFrom(rawType)) {
            Type keyActualTypeArgument = getGenericType(parameterizedType, 0);
            Type valueActualTypeArgument = getGenericType(parameterizedType, 1);
            if (isSupportType(keyActualTypeArgument) || isSupportType(valueActualTypeArgument)) {
                throw new UnsupportedOperationException("Not supported generate Result Schema with multiple Generic");
            }
            Class<?> keyActualClass = getActualClass(keyActualTypeArgument);
            Class<?> valueActualClass = getActualClass(valueActualTypeArgument);
            dataSchema = new MapSchema();
            ResolvedSchema valueResolvedSchema = ModelConverters.getInstance()
                    .resolveAsResolvedSchema(new AnnotatedType(valueActualTypeArgument));
            dataSchema.setName(rawType.getSimpleName() + "<" + keyActualClass.getSimpleName() + ", "
                    + valueActualClass.getSimpleName() + ">");
            if (null != valueResolvedSchema.schema) {
                dataSchema.setAdditionalProperties(valueResolvedSchema.schema);
                if (!valueResolvedSchema.referencedSchemas.isEmpty()) {
                    schemaCache.putAll(valueResolvedSchema.referencedSchemas);
                }
                resultSchemaName = "Result<" + dataSchema.getName() + ">";
            } else {
                resultSchemaName = buildResultObjectSchema(fieldsSchema);
            }
        } else if (Page.class.isAssignableFrom(rawType)) {
            Type actualTypeArgument = getGenericType(parameterizedType, 0);
            if (isSupportType(actualTypeArgument)) {
                throw new UnsupportedOperationException("Not supported generate Result Schema with multiple Generic");
            }
            dataSchema = ModelConverters.getInstance().resolveAsResolvedSchema(new AnnotatedType(Page.class)).schema;
            Class<?> actualClass = getActualClass(actualTypeArgument);
            ResolvedSchema resolvedSchema = ModelConverters.getInstance()
                    .resolveAsResolvedSchema(new AnnotatedType(actualTypeArgument));
            dataSchema.setName("Page<" + actualClass.getSimpleName() + ">");
            if (resolvedSchema.schema != null) {
                ((ArraySchema) dataSchema.getProperties().get("pageItems")).setItems(resolvedSchema.schema);
                if (!resolvedSchema.referencedSchemas.isEmpty()) {
                    schemaCache.putAll(resolvedSchema.referencedSchemas);
                }
                resultSchemaName = "Result<" + dataSchema.getName() + ">";
            } else {
                resultSchemaName = buildResultObjectSchema(fieldsSchema);
            }
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
    
    private boolean isSupportType(Type type) {
        return !(type instanceof Class) && !(type instanceof WildcardType);
    }
    
    private Class<?> getActualClass(Type type) {
        if (type instanceof Class) {
            return (Class<?>) type;
        }
        if (type instanceof WildcardType) {
            return getActualClass(((WildcardType) type).getUpperBounds()[0]);
        }
        throw new UnsupportedOperationException(
                String.format("Not supported generate Result Schema with %s.", type.getTypeName()));
    }
}
