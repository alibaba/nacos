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

import type { ReactNode } from 'react';
import { AlertCircle, Plus } from 'lucide-react';

import { Button } from '@/components/ui/button';
import { Tooltip, TooltipContent, TooltipTrigger } from '@/components/ui/tooltip';
import { VersionLifecycleActionDivider } from '@/components/ai/VersionLifecycleActionBar';

interface CreateDraftFromVersionButtonProps {
  label: ReactNode;
  blockedMessage?: ReactNode;
  blocked?: boolean;
  disabled?: boolean;
  divider?: boolean;
  onClick: () => void;
}

/**
 * Shared lifecycle action for creating a new draft from the selected Version.
 */
export function CreateDraftFromVersionButton({
  label,
  blockedMessage,
  blocked = false,
  disabled = false,
  divider = false,
  onClick,
}: CreateDraftFromVersionButtonProps) {
  const button = (
    <Button
      variant="outline"
      size="sm"
      className="h-7 text-xs gap-1.5"
      disabled={disabled || blocked}
      onClick={onClick}
    >
      <Plus className="h-3 w-3" />
      {label}
    </Button>
  );

  return (
    <>
      {divider && <VersionLifecycleActionDivider />}
      {blocked && blockedMessage ? (
        <Tooltip>
          <TooltipTrigger asChild>
            <span>{button}</span>
          </TooltipTrigger>
          <TooltipContent className="bg-amber-50 border border-amber-200 text-amber-800 dark:bg-amber-950 dark:border-amber-800 dark:text-amber-200">
            <span className="flex items-center gap-1.5">
              <AlertCircle className="h-3 w-3 shrink-0" />
              {blockedMessage}
            </span>
          </TooltipContent>
        </Tooltip>
      ) : button}
    </>
  );
}
