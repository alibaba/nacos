import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { Bot, Pencil, Trash2, ExternalLink, Zap, Radio, History } from 'lucide-react';
import { Card, CardFooter } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Checkbox } from '@/components/ui/checkbox';
import { Tooltip, TooltipContent, TooltipTrigger } from '@/components/ui/tooltip';
import { cn } from '@/lib/utils';
import type { AgentBasicInfo } from '@/types/agent';

interface AgentCardProps {
  agent: AgentBasicInfo;
  selected?: boolean;
  onSelect?: (name: string) => void;
  onDetail?: (name: string) => void;
  onEdit?: (name: string) => void;
  onDelete?: (name: string) => void;
}

const CAPABILITY_CONFIG = [
  { key: 'streaming' as const, icon: Zap, label: 'agent.streaming', color: 'text-amber-500' },
  { key: 'pushNotifications' as const, icon: Radio, label: 'agent.pushNotifications', color: 'text-blue-500' },
  { key: 'stateTransitionHistory' as const, icon: History, label: 'agent.stateHistory', color: 'text-emerald-500' },
];

export function AgentCard({
  agent,
  selected,
  onSelect,
  onDetail,
  onEdit,
  onDelete,
}: AgentCardProps) {
  const { t } = useTranslation();
  const [iconError, setIconError] = useState(false);
  const version = agent.latestPublishedVersion || agent.version || '';
  const skills = agent.skills || [];
  const capabilities = agent.capabilities || {};
  const activeCapabilities = CAPABILITY_CONFIG.filter((cap) => capabilities[cap.key]);

  return (
    <Card
      className={cn(
        'group relative flex flex-col py-0 gap-0 transition-all duration-200 hover:shadow-sm hover:border-primary/20 cursor-pointer overflow-hidden',
        selected && 'ring-2 ring-primary border-primary/40'
      )}
      onClick={() => onDetail?.(agent.name)}
    >
      {/* Header */}
      <div className="flex items-start gap-3 px-4 pt-3.5 pb-2 relative">
        {/* Checkbox - top right */}
        {onSelect && (
          <div
            className="absolute top-2.5 right-2.5 opacity-0 group-hover:opacity-100 transition-opacity data-[checked=true]:opacity-100"
            data-checked={selected || undefined}
            onClick={(e) => e.stopPropagation()}
          >
            <Checkbox
              checked={selected}
              onCheckedChange={() => onSelect(agent.name)}
            />
          </div>
        )}

        {/* Icon */}
        <div className={cn(
          'flex h-10 w-10 shrink-0 items-center justify-center rounded-xl shadow-sm overflow-hidden',
          agent.iconUrl && !iconError
            ? 'bg-white dark:bg-muted border border-border/60'
            : 'bg-gradient-to-br from-violet-500 to-fuchsia-400 shadow-violet-500/15'
        )}>
          {agent.iconUrl && !iconError ? (
            <img
              src={agent.iconUrl}
              alt={agent.name}
              className="h-full w-full object-contain p-1.5"
              onError={() => setIconError(true)}
            />
          ) : (
            <Bot className="h-5 w-5 text-white" />
          )}
        </div>

        {/* Title + meta */}
        <div className="flex-1 min-w-0">
          <h3 className="font-semibold text-sm truncate leading-tight">{agent.name}</h3>
          <div className="flex items-center gap-1.5 mt-1">
            {/* Version */}
            {version && (
              <span className="text-[10px] text-muted-foreground font-mono bg-muted/60 px-1 py-0.5 rounded">
                {version}
              </span>
            )}
          </div>
        </div>
      </div>

      {/* Content */}
      <div className="px-4 pb-2 flex-1">
        <p className="text-xs text-muted-foreground line-clamp-2 leading-relaxed">
          {agent.description || t('agent.noDescription')}
        </p>

        {/* Capabilities + skills row */}
        {(activeCapabilities.length > 0 || skills.length > 0) && (
          <div className="flex items-center gap-2 mt-2 flex-wrap">
            {activeCapabilities.map((cap) => {
              const Icon = cap.icon;
              return (
                <Tooltip key={cap.key}>
                  <TooltipTrigger asChild>
                    <span className="inline-flex items-center gap-1 rounded-md bg-muted/60 px-1.5 py-0.5 text-[10px] font-medium text-muted-foreground">
                      <Icon className={cn('h-3 w-3', cap.color)} />
                      {t(cap.label)}
                    </span>
                  </TooltipTrigger>
                  <TooltipContent>{t(cap.label)}</TooltipContent>
                </Tooltip>
              );
            })}

            {skills.length > 0 && (
              <span className="inline-flex items-center gap-1 text-[10px] text-muted-foreground">
                <Zap className="h-3 w-3" />
                {t('agent.skillCount', { count: skills.length })}
              </span>
            )}
          </div>
        )}
      </div>

      {/* Footer */}
      <CardFooter className="px-4 py-1.5 border-t bg-muted/20 flex items-center justify-end [.border-t]:pt-1.5">
        <div className="flex items-center -mr-1" onClick={(e) => e.stopPropagation()}>
          <Tooltip>
            <TooltipTrigger asChild>
              <Button variant="ghost" size="icon" className="h-6 w-6" onClick={() => onDetail?.(agent.name)}>
                <ExternalLink className="h-3 w-3" />
              </Button>
            </TooltipTrigger>
            <TooltipContent>{t('common.detail')}</TooltipContent>
          </Tooltip>
          <Tooltip>
            <TooltipTrigger asChild>
              <Button variant="ghost" size="icon" className="h-6 w-6" onClick={() => onEdit?.(agent.name)}>
                <Pencil className="h-3 w-3" />
              </Button>
            </TooltipTrigger>
            <TooltipContent>{t('common.edit')}</TooltipContent>
          </Tooltip>
          <Tooltip>
            <TooltipTrigger asChild>
              <Button variant="ghost" size="icon" className="h-6 w-6 text-destructive hover:text-destructive" onClick={() => onDelete?.(agent.name)}>
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
