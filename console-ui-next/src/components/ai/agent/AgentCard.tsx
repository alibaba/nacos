import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import dayjs from 'dayjs';
import {
  Bot,
  Clock,
  ExternalLink,
  FileEdit,
  Globe,
  Lock,
  Trash2,
} from 'lucide-react';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Card, CardFooter } from '@/components/ui/card';
import { Checkbox } from '@/components/ui/checkbox';
import { Tooltip, TooltipContent, TooltipTrigger } from '@/components/ui/tooltip';
import { cn } from '@/lib/utils';
import type { AgentSummary } from '@/types/agent';

interface AgentCardProps {
  agent: AgentSummary;
  selected?: boolean;
  onSelect?: (name: string) => void;
  onDetail?: (name: string) => void;
  onDelete?: (name: string) => void;
}

export function AgentCard({
  agent,
  selected,
  onSelect,
  onDetail,
  onDelete,
}: AgentCardProps) {
  const { t } = useTranslation();
  const [iconError, setIconError] = useState(false);
  const title = agent.displayName || agent.agentName;
  const latestVersion = agent.versionCatalog?.latestVersion;
  const editingVersion = agent.versionInfo?.editingVersion;
  const reviewingVersion = agent.versionInfo?.reviewingVersion;
  const onlineCount = agent.versionInfo?.onlineCnt || 0;

  return (
    <Card
      className={cn(
        'group relative flex cursor-pointer flex-col gap-0 overflow-hidden py-0 transition-all duration-200 hover:border-primary/20 hover:shadow-sm',
        selected && 'border-primary/40 ring-2 ring-primary',
      )}
      onClick={() => onDetail?.(agent.agentName)}
    >
      <div className="relative flex items-start gap-3 px-4 pb-2 pt-3.5">
        {onSelect && (
          <div
            className="absolute right-2.5 top-2.5 opacity-0 transition-opacity group-hover:opacity-100 data-[checked=true]:opacity-100"
            data-checked={selected || undefined}
            onClick={(event) => event.stopPropagation()}
          >
            <Checkbox checked={selected} onCheckedChange={() => onSelect(agent.agentName)} />
          </div>
        )}
        <div
          className={cn(
            'flex h-10 w-10 shrink-0 items-center justify-center overflow-hidden rounded-xl shadow-sm shadow-violet-500/15',
            agent.iconUrl && !iconError
              ? 'border bg-white dark:bg-muted'
              : 'bg-gradient-to-br from-violet-500 to-fuchsia-400',
          )}
        >
          {agent.iconUrl && !iconError ? (
            <img
              src={agent.iconUrl}
              alt={title}
              className="h-full w-full object-contain p-1.5"
              onError={() => setIconError(true)}
            />
          ) : (
            <Bot className="h-5 w-5 text-white" />
          )}
        </div>
        <div className="min-w-0 flex-1 pr-4">
          <h3 className="truncate text-sm font-semibold leading-tight">{title}</h3>
          <p className="mt-0.5 truncate font-mono text-[10px] text-muted-foreground">
            {agent.agentName}
          </p>
          <div className="mt-1 flex flex-wrap items-center gap-1.5">
            <Badge
              className={cn(
                'h-4 border-0 px-1.5 py-0 text-[10px] font-medium',
                agent.status === 'enable'
                  ? 'bg-emerald-50 text-emerald-700 dark:bg-emerald-950/40 dark:text-emerald-300'
                  : 'bg-gray-100 text-gray-500 dark:bg-gray-800 dark:text-gray-400',
              )}
            >
              {agent.status === 'enable' ? t('agent.enabled') : t('agent.disabled')}
            </Badge>
            {agent.scope && (
              <span className="inline-flex items-center gap-0.5 text-[10px] text-muted-foreground">
                {agent.scope === 'PUBLIC'
                  ? <Globe className="h-2.5 w-2.5" />
                  : <Lock className="h-2.5 w-2.5" />}
                {agent.scope === 'PUBLIC' ? t('agent.publicScope') : t('agent.privateScope')}
              </span>
            )}
            {latestVersion && (
              <span className="rounded bg-muted/60 px-1 py-0.5 font-mono text-[10px] text-muted-foreground">
                {latestVersion}
              </span>
            )}
          </div>
        </div>
      </div>

      <div className="flex-1 px-4 pb-2">
        <p className="line-clamp-2 text-xs leading-relaxed text-muted-foreground">
          {agent.description || t('agent.noDescription')}
        </p>
        <div className="mt-2 flex flex-wrap items-center gap-1.5">
          {(agent.tags || []).slice(0, 2).map((tag) => (
            <span
              key={tag}
              className="inline-flex rounded-md bg-slate-100 px-1.5 py-0.5 text-[10px] font-medium text-slate-700 dark:bg-slate-900/70 dark:text-slate-300"
            >
              {tag}
            </span>
          ))}
          <span
            className={cn(
              'inline-flex items-center gap-1 rounded-md px-1.5 py-0.5 text-[10px] font-medium',
              onlineCount > 0
                ? 'bg-emerald-50 text-emerald-700 dark:bg-emerald-950/40 dark:text-emerald-300'
                : 'bg-muted text-muted-foreground',
            )}
          >
            <Globe className="h-2.5 w-2.5" />
            {t('agent.onlineVersions')}: {onlineCount}
          </span>
          {editingVersion && (
            <span className="inline-flex items-center gap-1 rounded-md bg-amber-50 px-1.5 py-0.5 text-[10px] font-medium text-amber-700 dark:bg-amber-950/40 dark:text-amber-300">
              <FileEdit className="h-2.5 w-2.5" />
              {t('agent.editingVersion')}: {editingVersion}
            </span>
          )}
          {reviewingVersion && (
            <span className="inline-flex items-center gap-1 rounded-md bg-sky-50 px-1.5 py-0.5 text-[10px] font-medium text-sky-700 dark:bg-sky-950/40 dark:text-sky-300">
              {t('agent.reviewingVersion')}: {reviewingVersion}
            </span>
          )}
        </div>
      </div>

      <CardFooter className="flex items-center justify-between border-t bg-muted/20 px-4 py-1.5 [.border-t]:pt-1.5">
        <span className="inline-flex items-center gap-1 text-[10px] text-muted-foreground">
          <Clock className="h-3 w-3" />
          {agent.updateTime ? dayjs(agent.updateTime).format('YYYY-MM-DD HH:mm') : '-'}
        </span>
        <div className="-mr-1 flex items-center" onClick={(event) => event.stopPropagation()}>
          <Tooltip>
            <TooltipTrigger asChild>
              <Button
                variant="ghost"
                size="icon"
                className="h-6 w-6"
                onClick={() => onDetail?.(agent.agentName)}
              >
                <ExternalLink className="h-3 w-3" />
              </Button>
            </TooltipTrigger>
            <TooltipContent>{t('common.detail')}</TooltipContent>
          </Tooltip>
          <Tooltip>
            <TooltipTrigger asChild>
              <Button
                variant="ghost"
                size="icon"
                className="h-6 w-6 text-destructive hover:text-destructive"
                onClick={() => onDelete?.(agent.agentName)}
              >
                <Trash2 className="h-3 w-3" />
              </Button>
            </TooltipTrigger>
            <TooltipContent>{t('common.delete')}</TooltipContent>
          </Tooltip>
        </div>
      </CardFooter>
    </Card>
  );
}
