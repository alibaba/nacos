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

import { Link, useLocation } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { buttonVariants } from '@/components/ui/button';
import { cn } from '@/lib/utils';
import { SHARED_SKILLS_PATH } from '@/lib/hub-skill-utils';
import { useServerStore } from '@/stores/server-store';

/** Resource-specific navigation keeps namespace management separate from shared discovery. */
export function SkillManagementTabs({ shared = false }: { shared?: boolean }) {
  const { t } = useTranslation();
  const { search } = useLocation();
  const hubEnabled = useServerStore(s => s.hubEnabled);
  if (!hubEnabled) return null;

  return <nav aria-label={t('skill.title')} className="flex gap-2 border-b pb-3">
    <Link to={{ pathname: '/skill', search }} aria-current={!shared ? 'page' : undefined}
      className={cn(buttonVariants({ variant: shared ? 'ghost' : 'secondary', size: 'sm' }))}>
      {t('hub.currentNamespace')}
    </Link>
    <Link to={{ pathname: SHARED_SKILLS_PATH, search }} aria-current={shared ? 'page' : undefined}
      className={cn(buttonVariants({ variant: shared ? 'secondary' : 'ghost', size: 'sm' }))}>
      {t('hub.title')}
    </Link>
  </nav>;
}
