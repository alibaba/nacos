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

import fs from 'node:fs';
import path from 'node:path';
import { describe, expect, it } from 'vitest';
import {
  DEFAULT_LANGUAGE,
  EN_US,
  FALLBACK_LANGUAGE,
  LANGUAGE_CODES,
  RU_RU,
  SUPPORTED_LANGUAGES,
  ZH_CN,
  detectBrowserLanguage,
  getLanguageMeta,
  isSupportedLanguage,
} from '../languages';

function readLocale(code: string) {
  return JSON.parse(
    fs.readFileSync(path.resolve(__dirname, `../${code}.json`), 'utf8'),
  ) as Record<string, unknown>;
}

function flattenKeys(record: Record<string, unknown>, prefix = ''): string[] {
  return Object.entries(record).flatMap(([key, value]) =>
    value && typeof value === 'object'
      ? flattenKeys(value as Record<string, unknown>, `${prefix}${key}.`)
      : [`${prefix}${key}`],
  );
}

describe('console languages', () => {
  // Inputs are raw navigator.language values, so they stay plain strings
  it('detects the browser language by its primary subtag', () => {
    expect(detectBrowserLanguage('zh')).toBe(ZH_CN);
    expect(detectBrowserLanguage('zh-TW')).toBe(ZH_CN);
    expect(detectBrowserLanguage('ru')).toBe(RU_RU);
    expect(detectBrowserLanguage('ru-RU')).toBe(RU_RU);
    expect(detectBrowserLanguage('en-GB')).toBe(EN_US);
    expect(detectBrowserLanguage('fr-FR')).toBe(FALLBACK_LANGUAGE);
    expect(detectBrowserLanguage(undefined)).toBe(FALLBACK_LANGUAGE);
  });

  it('accepts only registered language codes', () => {
    expect(isSupportedLanguage(RU_RU)).toBe(true);
    expect(isSupportedLanguage('ru')).toBe(false);
    expect(isSupportedLanguage(null)).toBe(false);
    expect(isSupportedLanguage('toString')).toBe(false);
  });

  it('resolves language meta with the default language for unknown codes', () => {
    expect(getLanguageMeta(RU_RU)).toBe(SUPPORTED_LANGUAGES[RU_RU]);
    expect(getLanguageMeta('unknown')).toBe(SUPPORTED_LANGUAGES[DEFAULT_LANGUAGE]);
  });

  it('keeps the same translation keys in every locale file', () => {
    const enKeys = flattenKeys(readLocale(EN_US)).sort();
    for (const code of LANGUAGE_CODES) {
      expect(flattenKeys(readLocale(code)).sort()).toEqual(enKeys);
    }
  });
});
