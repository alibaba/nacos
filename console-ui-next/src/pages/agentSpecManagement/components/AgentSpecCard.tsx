import { useTranslation } from 'react-i18next';
import {
  Package,
  Trash2,
  ExternalLink,
  FileEdit,
  Clock,
  Download,
  Globe,
  Lock,
} from 'lucide-react';
import { Card, CardFooter } from '@/components/ui/card';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Checkbox } from '@/components/ui/checkbox';
import { Tooltip, TooltipContent, TooltipTrigger } from '@/components/ui/tooltip';
import { cn } from '@/lib/utils';
import dayjs from 'dayjs';
import { parseBizTags, type AgentSpecListItem } from '@/types/agentspec';

interface AgentSpecCardProps {
  item: AgentSpecListItem;
  selected: boolean;
  onSelect: (name: string) => void;
  onDetail: (name: string) => void;
  onDelete: (name: string) => void;
}

export function AgentSpecCard({
  item,
  selected,
  onSelect,
  onDetail,
  onDelete,
}: AgentSpecCardProps) {
  const { t } = useTranslation();

  const latestVersion = item.labels?.latest;
  const bizTags = parseBizTags(item.bizTags).slice(0, 2);

  return (
    <Card
      className={cn(
        'group relative flex flex-col py-0 gap-0 transition-all duration-200 hover:shadow-sm hover:border-primary/20 cursor-pointer overflow-hidden',
        selected && 'ring-2 ring-primary border-primary/40',
      )}
      onClick={() => onDetail(item.name)}
    >
      {/* Header */}
      <div className="flex items-start gap-3 px-4 pt-3.5 pb-2 relative">
        {/* Checkbox */}
        <div
          className="absolute top-2.5 right-2.5 opacity-0 group-hover:opacity-100 transition-opacity data-[checked=true]:opacity-100"
          data-checked={selected || undefined}
          onClick={(e) => e.stopPropagation()}
        >
          <Checkbox
            checked={selected}
            onCheckedChange={() => onSelect(item.name)}
          />
        </div>

        {/* Icon */}
        <div className="flex h-10 w-10 shrink-0 items-center justify-center rounded-xl bg-gradient-to-br from-indigo-500 to-purple-400 shadow-sm shadow-indigo-500/15">
          <Package className="h-5 w-5 text-white" />
        </div>

        {/* Title + version + status */}
        <div className="flex-1 min-w-0">
          <h3 className="font-semibold text-sm truncate leading-tight">
            {item.name}
          </h3>
          <div className="flex items-center gap-1.5 mt-1">
            <Badge
              className={cn(
                'text-[10px] px-1.5 py-0 h-4 font-medium border-0',
                item.enable
                  ? 'bg-emerald-50 text-emerald-700 dark:bg-emerald-950/40 dark:text-emerald-300'
                  : 'bg-gray-100 text-gray-500 dark:bg-gray-800 dark:text-gray-400',
              )}
            >
              {item.enable ? t('agentSpec.enabled') : t('agentSpec.disabled')}
            </Badge>
            {item.scope && (
              <span className="inline-flex items-center gap-0.5 text-[10px] text-muted-foreground">
                {item.scope === 'PUBLIC' ? <Globe className="h-2.5 w-2.5" /> : <Lock className="h-2.5 w-2.5" />}
                {item.scope === 'PUBLIC' ? t('agentSpec.scopePublic') : t('agentSpec.scopePrivate')}
              </span>
            )}
            {latestVersion && (
              <span className="text-[10px] text-muted-foreground font-mono bg-muted/60 px-1 py-0.5 rounded">
                {latestVersion}
              </span>
            )}
          </div>
        </div>
      </div>

      {/* Content */}
      <div className="px-4 pb-2 flex-1">
        <p className="text-xs text-muted-foreground line-clamp-2 leading-relaxed">
          {item.description || t('agentSpec.noDescription')}
        </p>

        {/* Meta indicators */}
        <div className="flex items-center gap-1.5 mt-2 flex-wrap">
          {bizTags.map((tag) => (
            <span
              key={tag}
              className="inline-flex items-center rounded-md bg-slate-100 dark:bg-slate-900/70 px-1.5 py-0.5 text-[10px] font-medium text-slate-700 dark:text-slate-300"
            >
              {tag}
            </span>
          ))}
          <span className={cn(
            'inline-flex items-center gap-1 rounded-md px-1.5 py-0.5 text-[10px] font-medium',
            item.onlineCnt > 0
              ? 'bg-emerald-50 dark:bg-emerald-950/40 text-emerald-700 dark:text-emerald-300'
              : 'bg-muted text-muted-foreground',
          )}>
            <Globe className="h-2.5 w-2.5" />
            {item.onlineCnt > 0
              ? t('agentSpec.onlineCount', { count: item.onlineCnt })
              : t('agentSpec.noOnlineVersion')}
          </span>
          {item.editingVersion && (
            <span className="inline-flex items-center gap-1 rounded-md bg-amber-50 dark:bg-amber-950/40 px-1.5 py-0.5 text-[10px] font-medium text-amber-700 dark:text-amber-300">
              <FileEdit className="h-2.5 w-2.5" />
              {t('agentSpec.hasDraft')}
            </span>
          )}
          <span className="inline-flex items-center gap-1 rounded-md bg-muted/60 px-1.5 py-0.5 text-[10px] font-medium text-muted-foreground ml-auto">
            <Download className="h-2.5 w-2.5" />
            {item.downloadCount ?? 0}
          </span>
        </div>
      </div>

      {/* Footer */}
      <CardFooter className="px-4 py-1.5 border-t bg-muted/20 flex items-center justify-between [.border-t]:pt-1.5">
        <span className="inline-flex items-center gap-1 text-[10px] text-muted-foreground">
          <Clock className="h-3 w-3" />
          {dayjs(item.updateTime).format('YYYY-MM-DD HH:mm')}
        </span>

        <div className="flex items-center -mr-1" onClick={(e) => e.stopPropagation()}>
          <Tooltip>
            <TooltipTrigger asChild>
              <Button
                variant="ghost"
                size="icon"
                className="h-6 w-6"
                onClick={() => onDetail(item.name)}
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
                onClick={() => onDelete(item.name)}
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
