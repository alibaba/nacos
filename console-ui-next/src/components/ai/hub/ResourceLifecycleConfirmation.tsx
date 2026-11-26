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

import { useCallback, useEffect, useRef, useState } from 'react';
import { useTranslation } from 'react-i18next';
import type { HubResourceType } from '@/api/hub-resource';
import type { HubShareState } from '@/types/hub-skill';
import { revokeWarning } from '@/lib/hub-skill-utils';
import { useAuthStore } from '@/stores/auth-store';
import { useServerStore } from '@/stores/server-store';
import { Button } from '@/components/ui/button';
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog';

type LifecycleAction = 'offline' | 'disable' | 'private';
interface Confirmation {
  context: string;
  title: string;
  resolve: (confirmed: boolean) => void;
}

/** Reuse the sharing card's state without changing source mutations or adding permission checks. */
export function useResourceLifecycleConfirmation(type: HubResourceType, namespaceId: string,
  name: string, version?: string | null) {
  const { t } = useTranslation();
  const hubEnabled = useServerStore(state => state.hubEnabled);
  const authenticated = useAuthStore(state => state.isAuthenticated);
  const available = hubEnabled && authenticated;
  const identity = JSON.stringify([type, namespaceId, name]);
  const context = JSON.stringify([identity, version]);
  const [sourceShare, setSourceShare] = useState<{ identity: string; share: HubShareState | null } | null>(null);
  const [pending, setPending] = useState<Confirmation | null>(null);
  const pendingRef = useRef<Confirmation | null>(null);
  const onShareStateChange = useCallback((share: HubShareState | null) => {
    setSourceShare({ identity, share });
  }, [identity]);
  const share = sourceShare?.identity === identity ? sourceShare.share : null;
  const warns = (affectedVersion?: string) => available &&
    revokeWarning(share?.active === true, affectedVersion, share?.sharedVersion);
  const warningText = t('hubResource.lifecycleConfirm', { resource: t(`hubResource.${type}`) });

  if (pending && (!available || pending.context !== context)) setPending(null);

  useEffect(() => {
    return () => {
      pendingRef.current?.resolve(false);
      pendingRef.current = null;
    };
  }, [context, available]);

  const finish = (confirmed: boolean) => {
    const current = pendingRef.current;
    pendingRef.current = null;
    setPending(null);
    current?.resolve(confirmed && available && current.context === context);
  };
  const confirmLifecycle = (action: LifecycleAction, affectedVersion?: string): Promise<boolean> => {
    if (!warns(affectedVersion)) return Promise.resolve(true);
    pendingRef.current?.resolve(false);
    return new Promise(resolve => {
      const value = { context, resolve, title: t(`hubResource.${action}Title`, {
        resource: t(`hubResource.${type}`), version: affectedVersion,
      }) };
      pendingRef.current = value;
      setPending(value);
    });
  };
  const lifecycleDialog = <Dialog open={available && pending?.context === context}
    onOpenChange={open => { if (!open) finish(false); }}>
    <DialogContent><DialogHeader>
      <DialogTitle>{pending?.title}</DialogTitle>
      <DialogDescription>{warningText}</DialogDescription>
    </DialogHeader><DialogFooter>
      <Button variant="outline" onClick={() => finish(false)}>{t('common.cancel')}</Button>
      <Button variant="destructive" onClick={() => finish(true)}>{t('common.confirm')}</Button>
    </DialogFooter></DialogContent>
  </Dialog>;
  return { onShareStateChange, confirmLifecycle, warns, warningText, lifecycleDialog };
}

