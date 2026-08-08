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

import { describe, expect, it } from 'vitest';
import {
  formatSchemaType,
  getPrimarySchemaType,
  isNullableSchemaType,
  setSchemaNullable,
  setSchemaPrimaryType,
} from '../json-schema';

describe('JSON Schema nullable type helpers', () => {
  it('adds and removes null without changing the primary type', () => {
    expect(setSchemaNullable('string', true)).toEqual(['string', 'null']);
    expect(setSchemaNullable(['string', 'null'], false)).toBe('string');
  });

  it('does not duplicate null when the type is already nullable', () => {
    expect(setSchemaNullable(['number', 'null'], true)).toEqual(['number', 'null']);
  });

  it('changes the primary type while preserving nullability', () => {
    expect(setSchemaPrimaryType(['string', 'null'], 'integer')).toEqual(['integer', 'null']);
    expect(setSchemaPrimaryType('string', 'integer')).toBe('integer');
  });

  it('reads the primary type and nullable state from a union type', () => {
    expect(getPrimarySchemaType(['null', 'boolean'])).toBe('boolean');
    expect(isNullableSchemaType(['null', 'boolean'])).toBe(true);
    expect(isNullableSchemaType('boolean')).toBe(false);
  });

  it('formats nullable types for schema previews', () => {
    expect(formatSchemaType(['string', 'null'])).toBe('string | null');
    expect(formatSchemaType('string')).toBe('string');
  });

  it('uses string as the fallback for missing or unsupported primary types', () => {
    expect(getPrimarySchemaType(undefined)).toBe('string');
    expect(getPrimarySchemaType(['null'])).toBe('string');
  });
});
