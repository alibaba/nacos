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

export interface AiResourceImportSourceInfo {
  sourceId: string;
  displayName?: string;
  description?: string;
  pluginName?: string;
  resourceTypes?: string[];
  enabled?: boolean;
  capabilities?: string[];
}

export interface AiResourceImportCandidateItem {
  externalId?: string;
  name?: string;
  version?: string;
  description?: string;
  metadata?: Record<string, string>;
}

export interface AiResourceImportSearchResponse {
  sourceId: string;
  resourceType: string;
  nextCursor?: string;
  hasMore?: boolean;
  items?: AiResourceImportCandidateItem[];
}

export type AiResourceImportValidationStatus = 'VALID' | 'WARNING' | 'INVALID' | 'CONFLICT';

export interface AiResourceImportValidationItem {
  externalId?: string;
  name?: string;
  version?: string;
  status?: AiResourceImportValidationStatus;
  exists?: boolean;
  conflictType?: string;
  warnings?: string[];
  errors?: string[];
}

export interface AiResourceImportValidateResponse {
  sourceId: string;
  resourceType: string;
  validationToken?: string;
  items?: AiResourceImportValidationItem[];
}

export type AiResourceImportResultStatus = 'SUCCESS' | 'FAILED' | 'SKIPPED';

export interface AiResourceImportResultItem {
  externalId?: string;
  resourceName?: string;
  version?: string;
  status?: AiResourceImportResultStatus;
  errorMessage?: string;
  warnings?: string[];
}

export interface AiResourceImportExecuteResponse {
  success?: boolean;
  totalCount?: number;
  successCount?: number;
  failedCount?: number;
  skippedCount?: number;
  results?: AiResourceImportResultItem[];
}

export interface AiResourceImportItem {
  externalId?: string;
  name?: string;
  version?: string;
  metadata?: Record<string, string>;
}
