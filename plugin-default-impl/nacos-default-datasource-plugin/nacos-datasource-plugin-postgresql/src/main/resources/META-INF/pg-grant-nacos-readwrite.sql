/*
 * Copyright 1999-2018 Alibaba Group Holding Ltd.
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

/*
 * Optional: grant read/write on existing Nacos tables to application role `nacos` only.
 *
 * When to use:
 *   Load pg-schema.sql as a DBA/superuser (tables then belong to that role). Create the app user:
 *     CREATE USER nacos WITH PASSWORD 'your-password';
 *   Then run this script while connected to the Nacos database as the same role that owns the tables
 *   (often superuser), so privileges apply to all current objects.
 *
 * What you get:
 *   USAGE on public schema, SELECT/INSERT/UPDATE/DELETE on all tables, USAGE+SELECT on sequences
 *   (covers bigserial / identity). No CREATE/DROP on schema, no superuser.
 *
 * CONNECT: use `psql -d your_nacos_db` so the session can open the DB, or from another database run:
 *   GRANT CONNECT ON DATABASE your_nacos_db TO nacos;
 */

GRANT USAGE ON SCHEMA public TO nacos;
GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO nacos;
GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA public TO nacos;

-- Objects created later by the same owner (e.g. future migrations run as superuser):
ALTER DEFAULT PRIVILEGES IN SCHEMA public
  GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO nacos;
ALTER DEFAULT PRIVILEGES IN SCHEMA public
  GRANT USAGE, SELECT ON SEQUENCES TO nacos;
