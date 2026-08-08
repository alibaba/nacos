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

export const SUPPORTED_SCHEMA_TYPES = [
  'string',
  'number',
  'integer',
  'boolean',
  'array',
  'object',
] as const;

export type SupportedSchemaType = (typeof SUPPORTED_SCHEMA_TYPES)[number];
export type JsonSchemaType = string | string[];

export function getPrimarySchemaType(type?: JsonSchemaType): SupportedSchemaType {
  const candidates = Array.isArray(type) ? type : [type];
  return (
    candidates.find(
      (candidate): candidate is SupportedSchemaType =>
        candidate !== undefined &&
        candidate !== 'null' &&
        SUPPORTED_SCHEMA_TYPES.includes(candidate as SupportedSchemaType)
    ) || 'string'
  );
}

export function isNullableSchemaType(type?: JsonSchemaType): boolean {
  return Array.isArray(type) && type.includes('null');
}

export function formatSchemaType(type?: JsonSchemaType): string {
  const primaryType = getPrimarySchemaType(type);
  return isNullableSchemaType(type) ? `${primaryType} | null` : primaryType;
}

export function setSchemaNullable(type: JsonSchemaType | undefined, nullable: boolean): JsonSchemaType {
  const primaryType = getPrimarySchemaType(type);
  return nullable ? [primaryType, 'null'] : primaryType;
}

export function setSchemaPrimaryType(
  type: JsonSchemaType | undefined,
  primaryType: SupportedSchemaType
): JsonSchemaType {
  return isNullableSchemaType(type) ? [primaryType, 'null'] : primaryType;
}
