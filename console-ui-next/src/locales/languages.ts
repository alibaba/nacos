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

// Registry of console UI languages. Keep free of i18n imports so storage can use it.
export const ZH_CN = 'zh-CN';
export const EN_US = 'en-US';
export const RU_RU = 'ru-RU';

// Key order defines the order in the language selector.
export const SUPPORTED_LANGUAGES = {
  [ZH_CN]: { label: '中文', shortLabel: '中', docsBase: 'https://nacos.io/' },
  [EN_US]: { label: 'English', shortLabel: 'EN', docsBase: 'https://nacos.io/en/' },
  [RU_RU]: { label: 'Русский', shortLabel: 'RU', docsBase: 'https://nacos.io/en/' },
} as const;

export type Language = keyof typeof SUPPORTED_LANGUAGES;

export const LANGUAGE_CODES = Object.keys(SUPPORTED_LANGUAGES) as Language[];

// Console language used until the stored or browser language is resolved.
export const DEFAULT_LANGUAGE: Language = ZH_CN;

// Language for browsers whose locale is not supported, and the i18n fallback for missing keys.
export const FALLBACK_LANGUAGE: Language = EN_US;

export function isSupportedLanguage(value: unknown): value is Language {
  // hasOwn instead of `in`, so inherited keys like "toString" are rejected
  return typeof value === 'string' && Object.hasOwn(SUPPORTED_LANGUAGES, value);
}

export function getLanguageMeta(language: string) {
  return SUPPORTED_LANGUAGES[isSupportedLanguage(language) ? language : DEFAULT_LANGUAGE];
}

// Match a browser locale such as "ru", "ru-RU" or "zh-TW" by its primary subtag
export function detectBrowserLanguage(browserLang?: string | null): Language {
  const prefix = browserLang?.toLowerCase().split('-')[0];
  const match = LANGUAGE_CODES.find((code) => code.toLowerCase().startsWith(`${prefix}-`));
  return match ?? FALLBACK_LANGUAGE;
}
