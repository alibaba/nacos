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

/*
 * ARD pgvector schema only.
 *
 * Use this file when Nacos main datasource is not PostgreSQL, but ARD embeddings
 * are stored in an independent PostgreSQL pgvector datasource configured by
 * nacos.ai.ard.vector.postgresql.*.
 *
 * If PostgreSQL is the Nacos main datasource, use pg-schema.sql instead.
 */

CREATE EXTENSION IF NOT EXISTS vector;

DROP TABLE IF EXISTS "ai_resource_ard_embedding_pg";
CREATE TABLE "ai_resource_ard_embedding_pg" (
  "id" bigserial NOT NULL,
  "gmt_create" timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
  "gmt_modified" timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
  "namespace_id" varchar(128) NOT NULL DEFAULT '',
  "entry_id" bigint NOT NULL,
  "chunk_id" bigint NOT NULL,
  "resource_type" varchar(32) NOT NULL,
  "resource_name" varchar(256) NOT NULL,
  "resource_version" varchar(64) NOT NULL,
  "embedding_model" varchar(128) NOT NULL,
  "embedding_dimension" integer NOT NULL,
  "embedding" vector NOT NULL
);

ALTER TABLE "ai_resource_ard_embedding_pg" ADD CONSTRAINT "ai_resource_ard_embedding_pg_pkey" PRIMARY KEY ("id");
CREATE INDEX "idx_ard_embedding_pg_chunk" ON "ai_resource_ard_embedding_pg" USING btree ("chunk_id");
CREATE INDEX "idx_ard_embedding_pg_model" ON "ai_resource_ard_embedding_pg" USING btree (
  "namespace_id",
  "embedding_model",
  "embedding_dimension",
  "resource_type"
);
CREATE INDEX "idx_ard_embedding_pg_resource" ON "ai_resource_ard_embedding_pg" USING btree (
  "namespace_id",
  "resource_type",
  "resource_name",
  "resource_version"
);
