import type { ReactNode } from 'react';
import { Globe, Lock, ShieldAlert } from 'lucide-react';

import { Button } from '@/components/ui/button';
import { Switch } from '@/components/ui/switch';
import { Tooltip, TooltipContent, TooltipTrigger } from '@/components/ui/tooltip';
import { cn } from '@/lib/utils';

interface AiResourceStatusControlsProps {
  enabled: boolean;
  enabledLabel: ReactNode;
  disabledLabel: ReactNode;
  publicLabel: ReactNode;
  privateLabel: ReactNode;
  scope?: string;
  enableDisabled?: boolean;
  scopeDisabled?: boolean;
  onEnabledChange?: (enabled: boolean) => void;
  onScopeChange?: (isPublic: boolean) => void;
  visibilityLabel?: ReactNode;
  visibilityTooltip?: ReactNode;
  onVisibilityClick?: () => void;
  className?: string;
}

/**
 * Shared detail-header controls for AI resource status, scope, and visibility authorization.
 */
export function AiResourceStatusControls({
  enabled,
  enabledLabel,
  disabledLabel,
  publicLabel,
  privateLabel,
  scope,
  enableDisabled = false,
  scopeDisabled = false,
  onEnabledChange,
  onScopeChange,
  visibilityLabel,
  visibilityTooltip,
  onVisibilityClick,
  className,
}: AiResourceStatusControlsProps) {
  const enableInteractive = Boolean(onEnabledChange) && !enableDisabled;
  const scopeInteractive = Boolean(onScopeChange) && !scopeDisabled;
  const showVisibilityAction = Boolean(visibilityLabel) && Boolean(onVisibilityClick);

  return (
    <div className={cn('mt-1.5 mb-1 flex flex-wrap items-center gap-4', className)}>
      <label
        className={cn(
          'inline-flex items-center gap-2 select-none',
          enableInteractive ? 'cursor-pointer' : 'cursor-default'
        )}
      >
        <Switch
          checked={enabled}
          disabled={!enableInteractive}
          onCheckedChange={onEnabledChange}
          className={cn(enabled && 'data-[state=checked]:bg-emerald-500')}
        />
        <span
          className={cn(
            'text-xs font-medium',
            enabled
              ? 'text-emerald-700 dark:text-emerald-300'
              : 'text-muted-foreground'
          )}
        >
          {enabled ? enabledLabel : disabledLabel}
        </span>
      </label>

      {scope && (
        <>
          <div aria-hidden="true" className="h-4 w-px bg-border" />
          <label
            className={cn(
              'inline-flex items-center gap-2 select-none',
              scopeInteractive ? 'cursor-pointer' : 'cursor-default'
            )}
          >
            <Switch
              checked={scope === 'PUBLIC'}
              disabled={!scopeInteractive}
              onCheckedChange={onScopeChange}
            />
            <span className="inline-flex items-center gap-1 text-xs font-medium text-muted-foreground">
              {scope === 'PUBLIC'
                ? <Globe className="h-3 w-3" />
                : <Lock className="h-3 w-3" />}
              {scope === 'PUBLIC' ? publicLabel : privateLabel}
            </span>
          </label>
        </>
      )}

      {showVisibilityAction && (
        <>
          <div aria-hidden="true" className="h-4 w-px bg-border" />
          <Tooltip>
            <TooltipTrigger asChild>
              <Button
                variant="outline"
                size="sm"
                className="h-7 px-2 text-xs"
                onClick={onVisibilityClick}
              >
                <ShieldAlert className="mr-1 h-3.5 w-3.5" />
                {visibilityLabel}
              </Button>
            </TooltipTrigger>
            {visibilityTooltip && <TooltipContent>{visibilityTooltip}</TooltipContent>}
          </Tooltip>
        </>
      )}
    </div>
  );
}
