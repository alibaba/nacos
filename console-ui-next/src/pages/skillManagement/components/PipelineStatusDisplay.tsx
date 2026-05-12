import { useState, useEffect, useMemo } from 'react';
import { useTranslation } from 'react-i18next';
import { Loader2, CheckCircle2, XCircle, Clock, RefreshCw, Eye, Minus, ShieldCheck, ShieldX } from 'lucide-react';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogDescription,
} from '@/components/ui/dialog';
import { Tooltip, TooltipContent, TooltipTrigger, TooltipProvider } from '@/components/ui/tooltip';
import { cn } from '@/lib/utils';
import dayjs from 'dayjs';
import Markdown from 'react-markdown';
import remarkGfm from 'remark-gfm';

interface PipelineCheckpointInfo {
  title: string;
  passed: boolean;
}

interface PipelineNodeInfo {
  nodeId: string;
  executedAt?: string;
  passed: boolean;
  message?: string;
  /** Semantic type: text | json | markdown | html */
  messageType?: string;
  /** Per-criterion audit checkpoints */
  checkpoints?: PipelineCheckpointInfo[];
  durationMs?: number;
}

interface PipelineStatusInfo {
  executionId: string;
  status: 'IN_PROGRESS' | 'APPROVED' | 'REJECTED';
  pipeline: PipelineNodeInfo[];
}

interface PipelineStatusDisplayProps {
  pipelineInfo: PipelineStatusInfo | null;
  compact?: boolean;
  translationPrefix?: 'skill' | 'agentSpec';
  onRefresh?: () => void;
  refreshing?: boolean;
}

const STATUS_CONFIG = {
  IN_PROGRESS: {
    icon: Loader2,
    labelSuffix: 'pipelineInProgress',
    badgeClass: 'bg-blue-50 text-blue-700 dark:bg-blue-950/40 dark:text-blue-300',
    iconClass: 'animate-spin text-blue-500',
    dotClass: 'bg-blue-400',
  },
  APPROVED: {
    icon: CheckCircle2,
    labelSuffix: 'pipelineApproved',
    badgeClass: 'bg-emerald-50 text-emerald-700 dark:bg-emerald-950/40 dark:text-emerald-300',
    iconClass: 'text-emerald-500',
    dotClass: 'bg-emerald-400',
  },
  REJECTED: {
    icon: XCircle,
    labelSuffix: 'pipelineRejected',
    badgeClass: 'bg-red-50 text-red-700 dark:bg-red-950/40 dark:text-red-300',
    iconClass: 'text-red-500',
    dotClass: 'bg-red-400',
  },
} as const;

function formatDuration(ms: number) {
  return ms >= 1000 ? `${(ms / 1000).toFixed(1)}s` : `${ms}ms`;
}

/** Render node message content based on messageType. */
function NodeMessageContent({ message, messageType }: { message: string; messageType?: string }) {
  const formattedJson = useMemo(() => {
    if (messageType !== 'json') return null;
    try {
      return JSON.stringify(JSON.parse(message), null, 2);
    } catch {
      return message;
    }
  }, [message, messageType]);

  const type = messageType || 'text';

  if (type === 'markdown') {
    return (
      <div className="app-markdown prose prose-sm dark:prose-invert max-w-none text-xs">
        <Markdown remarkPlugins={[remarkGfm]}>{message}</Markdown>
      </div>
    );
  }

  if (type === 'html') {
    return (
      <div
        className="text-xs text-muted-foreground prose prose-sm dark:prose-invert max-w-none"
        dangerouslySetInnerHTML={{ __html: message }}
      />
    );
  }

  if (type === 'json') {
    return (
      <pre className="text-xs text-muted-foreground whitespace-pre-wrap break-words font-mono leading-relaxed bg-muted/30 rounded-md p-3 border">
        {formattedJson}
      </pre>
    );
  }

  // Default: plain text
  return (
    <pre className="text-xs text-muted-foreground whitespace-pre-wrap break-words font-mono leading-relaxed">
      {message}
    </pre>
  );
}

/** Horizontal pipeline stepper dialog. */
function PipelineDetailDialog({
  open,
  onOpenChange,
  pipelineInfo,
  translationPrefix = 'skill',
  onRefresh,
  refreshing,
}: {
  open: boolean;
  onOpenChange: (v: boolean) => void;
  pipelineInfo: PipelineStatusInfo;
  translationPrefix?: string;
  onRefresh?: () => void;
  refreshing?: boolean;
}) {
  const { t } = useTranslation();
  const nodes = pipelineInfo.pipeline || [];
  // Auto-select first failed node, or first node
  const firstFailedIdx = nodes.findIndex((n) => !n.passed);
  const [selectedIdx, setSelectedIdx] = useState(firstFailedIdx >= 0 ? firstFailedIdx : 0);

  // Reset selection when dialog opens or pipeline data changes
  useEffect(() => {
    if (open) {
      const idx = nodes.findIndex((n) => !n.passed);
      setSelectedIdx(idx >= 0 ? idx : 0);
    }
  }, [open, nodes.length]);

  const config = STATUS_CONFIG[pipelineInfo.status];
  const StatusIcon = config.icon;
  const selectedNode = nodes[selectedIdx];

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-w-3xl max-h-[85vh] flex flex-col gap-0 p-0" onOpenAutoFocus={(e) => e.preventDefault()}>
        {/* Header */}
        <DialogHeader className="px-6 pt-5 pb-4 border-b shrink-0">
          <div className="flex items-center gap-3">
            <DialogTitle className="text-base">{t(`${translationPrefix}.pipelineStatus`)}</DialogTitle>
            <Badge className={cn('text-[11px] px-2 py-0.5 h-5 font-medium border-0 gap-1', config.badgeClass)}>
              <StatusIcon className={cn('h-3 w-3', config.iconClass)} />
              {t(`${translationPrefix}.${config.labelSuffix}`)}
            </Badge>
            {pipelineInfo.status === 'IN_PROGRESS' && onRefresh && (
              <Button
                variant="ghost"
                size="icon"
                className="h-7 w-7 ml-auto"
                onClick={onRefresh}
                disabled={refreshing}
              >
                <RefreshCw className={cn('h-3.5 w-3.5', refreshing && 'animate-spin')} />
              </Button>
            )}
          </div>
          <DialogDescription className="sr-only">
            {t(`${translationPrefix}.pipelineStatus`)}
          </DialogDescription>
        </DialogHeader>

        {/* Horizontal stepper */}
        {nodes.length > 0 && (
          <div className="px-6 py-4 border-b shrink-0 overflow-x-auto">
            <div className="flex items-center min-w-max">
              {nodes.map((node, idx) => {
                const isSelected = idx === selectedIdx;
                const isLast = idx === nodes.length - 1;
                return (
                  <div key={node.nodeId} className="flex items-center">
                    {/* Node circle + label */}
                    <TooltipProvider delayDuration={200}>
                      <Tooltip>
                        <TooltipTrigger asChild>
                          <button
                            type="button"
                            className="flex flex-col items-center gap-1.5 group"
                            onClick={() => setSelectedIdx(idx)}
                          >
                            <div
                              className={cn(
                                'relative flex items-center justify-center rounded-full transition-all',
                                'w-8 h-8 border-2',
                                node.passed
                                  ? 'border-emerald-400 bg-emerald-50 dark:bg-emerald-950/30'
                                  : 'border-red-400 bg-red-50 dark:bg-red-950/30',
                                isSelected && 'ring-2 ring-offset-2 ring-primary',
                              )}
                            >
                              {node.passed ? (
                                <CheckCircle2 className="h-4 w-4 text-emerald-500" />
                              ) : (
                                <XCircle className="h-4 w-4 text-red-500" />
                              )}
                            </div>
                            <span
                              className={cn(
                                'text-[10px] font-mono max-w-[72px] truncate',
                                isSelected ? 'font-semibold text-foreground' : 'text-muted-foreground',
                              )}
                            >
                              {node.nodeId}
                            </span>
                          </button>
                        </TooltipTrigger>
                        <TooltipContent side="bottom" className="text-xs">
                          <p>{node.nodeId}</p>
                          {node.durationMs != null && <p>{formatDuration(node.durationMs)}</p>}
                        </TooltipContent>
                      </Tooltip>
                    </TooltipProvider>

                    {/* Connector line */}
                    {!isLast && (
                      <div className="flex items-center px-1 -mt-5">
                        <Minus className="h-3 w-6 text-border" />
                      </div>
                    )}
                  </div>
                );
              })}
            </div>
          </div>
        )}

        {/* Selected node detail */}
        {selectedNode && (
          <div className="flex-1 min-h-0 flex flex-col overflow-hidden">
            {/* Node info bar */}
            <div className="px-6 py-3 border-b bg-muted/20 flex items-center gap-3 flex-wrap shrink-0">
              {selectedNode.passed ? (
                <CheckCircle2 className="h-4 w-4 shrink-0 text-emerald-500" />
              ) : (
                <XCircle className="h-4 w-4 shrink-0 text-red-500" />
              )}
              <span className="text-sm font-medium font-mono">{selectedNode.nodeId}</span>
              {selectedNode.durationMs != null && (
                <Badge variant="secondary" className="text-[10px] h-5">
                  {formatDuration(selectedNode.durationMs)}
                </Badge>
              )}
              {selectedNode.executedAt && (
                <span className="inline-flex items-center gap-1 text-[11px] text-muted-foreground ml-auto">
                  <Clock className="h-3 w-3" />
                  {dayjs(selectedNode.executedAt).format('YYYY-MM-DD HH:mm:ss')}
                </span>
              )}
            </div>

            {/* Message area */}
            <div className="flex-1 min-h-0 overflow-y-auto">
              <div className="px-6 py-4 space-y-4">
                {/* Checkpoints */}
                {selectedNode.checkpoints && selectedNode.checkpoints.length > 0 && (
                  <div className="space-y-1.5">
                    <h4 className="text-[11px] font-semibold text-muted-foreground uppercase tracking-wider">
                      Checkpoints
                    </h4>
                    <div className="rounded-lg border divide-y">
                      {selectedNode.checkpoints.map((cp, i) => (
                        <div key={i} className="flex items-center gap-2.5 px-3 py-2">
                          {cp.passed ? (
                            <ShieldCheck className="h-3.5 w-3.5 shrink-0 text-emerald-500" />
                          ) : (
                            <ShieldX className="h-3.5 w-3.5 shrink-0 text-red-500" />
                          )}
                          <span className={cn(
                            'text-xs',
                            cp.passed ? 'text-muted-foreground' : 'text-red-600 dark:text-red-400 font-medium',
                          )}>
                            {cp.title}
                          </span>
                        </div>
                      ))}
                    </div>
                  </div>
                )}

                {/* Message content rendered by messageType */}
                {selectedNode.message ? (
                  <NodeMessageContent
                    message={selectedNode.message}
                    messageType={selectedNode.messageType}
                  />
                ) : (
                  <p className="text-xs text-muted-foreground/60 italic">
                    {t(`${translationPrefix}.pipelineNoMessage`)}
                  </p>
                )}
              </div>
            </div>
          </div>
        )}

        {/* Empty state */}
        {nodes.length === 0 && (
          <div className="px-6 py-12 text-center text-sm text-muted-foreground">
            {t(`${translationPrefix}.pipelineNone`)}
          </div>
        )}
      </DialogContent>
    </Dialog>
  );
}

export function PipelineStatusDisplay({
  pipelineInfo,
  compact = false,
  translationPrefix = 'skill',
  onRefresh,
  refreshing,
}: PipelineStatusDisplayProps) {
  const { t } = useTranslation();
  const [dialogOpen, setDialogOpen] = useState(false);

  if (!pipelineInfo) {
    if (compact) return null;
    return (
      <p className="text-xs text-muted-foreground py-2">{t(`${translationPrefix}.pipelineNone`)}</p>
    );
  }

  const config = STATUS_CONFIG[pipelineInfo.status];
  const StatusIcon = config.icon;

  // Compact badge mode (for Timeline inline)
  if (compact) {
    return (
      <Badge className={cn('text-xs px-2.5 h-7 font-medium border-0 gap-1.5 rounded-md inline-flex items-center', config.badgeClass)}>
        <StatusIcon className={cn('h-3.5 w-3.5', config.iconClass)} />
        {t(`${translationPrefix}.${config.labelSuffix}`)}
      </Badge>
    );
  }

  // Full panel mode (for detail page) — compact summary + click to open dialog
  const nodes = pipelineInfo.pipeline || [];

  return (
    <>
      <div className="space-y-3">
        {/* Overall status row */}
        <div className="flex items-center gap-2">
          <StatusIcon className={cn('h-4 w-4', config.iconClass)} />
          <span className="text-sm font-medium">{t(`${translationPrefix}.${config.labelSuffix}`)}</span>

          <div className="flex items-center gap-1 ml-auto">
            {/* Refresh */}
            {pipelineInfo.status === 'IN_PROGRESS' && onRefresh && (
              <Button
                variant="ghost"
                size="icon"
                className="h-6 w-6"
                onClick={onRefresh}
                disabled={refreshing}
              >
                <RefreshCw className={cn('h-3.5 w-3.5', refreshing && 'animate-spin')} />
              </Button>
            )}
          </div>
        </div>

        {/* Node summary strip */}
        {nodes.length > 0 && (
          <button
            type="button"
            className="group w-full flex items-center gap-3 rounded-lg border bg-muted/10 px-3 py-2.5 transition-colors hover:bg-muted/30 cursor-pointer"
            onClick={() => setDialogOpen(true)}
          >
            {/* Node list with names */}
            <div className="flex items-center gap-2 flex-wrap flex-1 min-w-0">
              {nodes.map((node, idx) => (
                <div key={node.nodeId} className="flex items-center gap-2">
                  <div className="flex items-center gap-1.5 min-w-0">
                    <div
                      className={cn(
                        'w-2.5 h-2.5 rounded-full shrink-0',
                        node.passed
                          ? 'bg-emerald-400 dark:bg-emerald-500'
                          : 'bg-red-400 dark:bg-red-500',
                      )}
                    />
                    <span className={cn(
                      'text-[11px] font-mono truncate max-w-[120px]',
                      node.passed ? 'text-muted-foreground' : 'text-red-600 dark:text-red-400 font-medium',
                    )}>
                      {node.nodeId}
                    </span>
                  </div>
                  {idx < nodes.length - 1 && (
                    <span className="text-border text-[10px]">/</span>
                  )}
                </div>
              ))}
            </div>

            {/* View detail icon */}
            <Eye className="h-3.5 w-3.5 shrink-0 text-muted-foreground group-hover:text-foreground transition-colors" />
          </button>
        )}
      </div>

      {/* Detail dialog */}
      <PipelineDetailDialog
        open={dialogOpen}
        onOpenChange={setDialogOpen}
        pipelineInfo={pipelineInfo}
        translationPrefix={translationPrefix}
        onRefresh={onRefresh}
        refreshing={refreshing}
      />
    </>
  );
}
