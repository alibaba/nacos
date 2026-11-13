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

import { useTranslation } from 'react-i18next';
import type { HubResourceType } from '@/api/hub-resource';
import { useServerStore } from '@/stores/server-store';

/** Deletion dialogs also cover resources whose sharing state has not been loaded. */
export function ResourceDeleteWarning({ type }: { type: HubResourceType }) {
  const { t } = useTranslation();
  const hubEnabled = useServerStore(state => state.hubEnabled);
  return hubEnabled ? <span className="mt-2 block text-amber-600">
    {t('hubResource.deleteWarning', { resource: t(`hubResource.${type}`) })}
  </span> : null;
}
