import { useTranslation } from 'react-i18next';
import { Loader2, CheckCircle2, XCircle, Clock } from 'lucide-react';
import { Badge } from '@/components/ui/badge';
import { cn } from '@/lib/utils';
import dayjs from 'dayjs';
import type { PublishPipelineInfo } from '@/types/skill';

interface PipelineStatusDisplayProps {
  pipelineInfo: PublishPipelineInfo | null;
  compact?: boolean;
}

const STATUS_CONFIG = {
  IN_PROGRESS: {
    icon: Loader2,
    labelKey: 'skill.pipelineInProgress',
    badgeClass: 'bg-blue-50 text-blue-700 dark:bg-blue-950/40 dark:text-blue-300',
    iconClass: 'animate-spin text-blue-500',
    dotClass: 'bg-blue-400',
  },
  APPROVED: {
    icon: CheckCircle2,
    labelKey: 'skill.pipelineApproved',
    badgeClass: 'bg-emerald-50 text-emerald-700 dark:bg-emerald-950/40 dark:text-emerald-300',
    iconClass: 'text-emerald-500',
    dotClass: 'bg-emerald-400',
  },
  REJECTED: {
    icon: XCircle,
    labelKey: 'skill.pipelineRejected',
    badgeClass: 'bg-red-50 text-red-700 dark:bg-red-950/40 dark:text-red-300',
    iconClass: 'text-red-500',
    dotClass: 'bg-red-400',
  },
} as const;

export function PipelineStatusDisplay({ pipelineInfo, compact = false }: PipelineStatusDisplayProps) {
  const { t } = useTranslation();

  if (!pipelineInfo) {
    if (compact) return null;
    return (
      <p className="text-xs text-muted-foreground py-2">{t('skill.pipelineNone')}</p>
    );
  }

  const config = STATUS_CONFIG[pipelineInfo.status];
  const StatusIcon = config.icon;

  // Compact badge mode (for Timeline inline)
  if (compact) {
    return (
      <Badge className={cn('text-[10px] px-1.5 py-0 h-4 font-medium border-0 gap-1', config.badgeClass)}>
        <StatusIcon className={cn('h-2.5 w-2.5', config.iconClass)} />
        {t(config.labelKey)}
      </Badge>
    );
  }

  // Full panel mode (for detail page)
  const nodes = pipelineInfo.pipeline || [];

  return (
    <div className="space-y-3">
      {/* Overall status */}
      <div className="flex items-center gap-2">
        <StatusIcon className={cn('h-4 w-4', config.iconClass)} />
        <span className="text-sm font-medium">{t(config.labelKey)}</span>
      </div>

      {/* Pipeline nodes */}
      {nodes.length > 0 && (
        <div className="space-y-2">
          {nodes.map((node) => (
            <div
              key={node.nodeId}
              className="flex items-start gap-2.5 rounded-md border bg-muted/20 px-3 py-2"
            >
              {/* Node status icon */}
              {node.passed ? (
                <CheckCircle2 className="h-3.5 w-3.5 mt-0.5 shrink-0 text-emerald-500" />
              ) : (
                <XCircle className="h-3.5 w-3.5 mt-0.5 shrink-0 text-red-500" />
              )}

              <div className="flex-1 min-w-0">
                <div className="flex items-center gap-2">
                  <span className="text-xs font-medium font-mono">{node.nodeId}</span>
                  {node.durationMs != null && (
                    <span className="text-[10px] text-muted-foreground">
                      {node.durationMs >= 1000
                        ? `${(node.durationMs / 1000).toFixed(1)}s`
                        : `${node.durationMs}ms`}
                    </span>
                  )}
                </div>
                {node.message && (
                  <p className="text-[11px] text-muted-foreground mt-0.5 break-words">
                    {node.message}
                  </p>
                )}
                {node.executedAt && (
                  <span className="inline-flex items-center gap-1 text-[10px] text-muted-foreground/70 mt-0.5">
                    <Clock className="h-2.5 w-2.5" />
                    {dayjs(node.executedAt).format('YYYY-MM-DD HH:mm:ss')}
                  </span>
                )}
              </div>
            </div>
          ))}
        </div>
      )}
    </div>
  );
}
