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

import { Navigate, useLocation, useParams } from 'react-router-dom';
import { hubDetailPath, SHARED_SKILLS_PATH, validHubId } from '@/lib/hub-skill-utils';

/** Keep bookmarked shared entries while consolidating navigation under Skill management. */
export function LegacyHubRedirect() {
  const { hubEntryId } = useParams<{ hubEntryId: string }>();
  const { search, hash } = useLocation();
  return <Navigate replace to={{
    pathname: validHubId(hubEntryId) ? hubDetailPath(hubEntryId) : SHARED_SKILLS_PATH,
    search,
    hash,
  }} />;
}
