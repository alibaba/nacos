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

-- PostgreSQL upgrade script for deployments that still contain nullable tenant_id
-- values in config-related tables.
--
-- IMPORTANT:
-- 1. Review and clean duplicate logical rows before replacing NULL tenant_id
--    values with '' on config_info, otherwise the UPDATE may conflict with the
--    existing unique index on (data_id, group_id, tenant_id).
-- 2. Apply this script before upgrading to a Nacos build that validates the
--    PostgreSQL tenant schema on startup.
--
-- Suggested pre-check for duplicate logical config rows:
-- SELECT data_id, group_id, COUNT(*)
-- FROM config_info
-- WHERE tenant_id IS NULL
-- GROUP BY data_id, group_id
-- HAVING COUNT(*) > 1;

ALTER TABLE config_info ALTER COLUMN tenant_id SET DEFAULT '';
UPDATE config_info SET tenant_id = '' WHERE tenant_id IS NULL;
ALTER TABLE config_info ALTER COLUMN tenant_id SET NOT NULL;

ALTER TABLE config_info_gray ALTER COLUMN tenant_id SET DEFAULT '';
UPDATE config_info_gray SET tenant_id = '' WHERE tenant_id IS NULL;
ALTER TABLE config_info_gray ALTER COLUMN tenant_id SET NOT NULL;

ALTER TABLE config_tags_relation ALTER COLUMN tenant_id SET DEFAULT '';
UPDATE config_tags_relation SET tenant_id = '' WHERE tenant_id IS NULL;
ALTER TABLE config_tags_relation ALTER COLUMN tenant_id SET NOT NULL;

ALTER TABLE his_config_info ALTER COLUMN tenant_id SET DEFAULT '';
UPDATE his_config_info SET tenant_id = '' WHERE tenant_id IS NULL;
ALTER TABLE his_config_info ALTER COLUMN tenant_id SET NOT NULL;
