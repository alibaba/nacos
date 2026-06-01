import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import {
  Send,
  Rocket,
  Globe,
  PowerOff,
  Trash2,
  Clock,
  Download,
  Plus,
  GitBranch,
  Tag,
  ShieldOff,
  AlertCircle,
  ShieldAlert,
} from 'lucide-react';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Tooltip, TooltipContent, TooltipTrigger } from '@/components/ui/tooltip';
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog';
import { cn } from '@/lib/utils';
import dayjs from 'dayjs';
import type { SkillVersionSummary } from '@/types/skill';
import { parsePipelineInfo } from '@/types/skill';
import { getValidActionsWithContext, sortVersionsDescending } from './version-utils';
import { PipelineStatusDisplay } from './PipelineStatusDisplay';
import { LabelBindDialog } from '@/components/ai/LabelBindDialog';

interface SkillVersionTimelineProps {
  versions: SkillVersionSummary[];
  currentVersion: string;
  hasEditingVersion: boolean;
  hasReviewingVersion: boolean;
  onSelectVersion: (version: string) => void;
  onCreateDraft: (basedOnVersion?: string) => void;
  onDeleteDraft: (version: string) => void;
  onSubmit: (version: string) => void;
  onPublish: (version: string) => void;
  onForcePublish?: (version: string) => void;
  onOnline: (version: string) => void;
  onOffline: (version: string) => void;
  onDownload?: (version: string) => void;
  showCreateDraftButton?: boolean;
  allLabels?: Record<string, string>;
  onSaveLabels?: (labels: Record<string, string>) => Promise<void>;
  skillEnabled?: boolean;
  isGlobalAdmin?: boolean;
}

const STATUS_STYLES: Record<string, string> = {
  draft:
    'bg-amber-50 text-amber-700 dark:bg-amber-950/40 dark:text-amber-300',
  reviewing:
    'bg-blue-50 text-blue-700 dark:bg-blue-950/40 dark:text-blue-300',
  pendingPublish:
    'bg-teal-50 text-teal-700 dark:bg-teal-950/40 dark:text-teal-300',
  rejected:
    'bg-red-50 text-red-700 dark:bg-red-950/40 dark:text-red-300',
  online:
    'bg-emerald-50 text-emerald-700 dark:bg-emerald-950/40 dark:text-emerald-300',
  offline:
    'bg-gray-100 text-gray-500 dark:bg-gray-800 dark:text-gray-400',
};

const DOT_STYLES: Record<string, string> = {
  draft: 'bg-amber-400',
  reviewing: 'bg-blue-400',
  pendingPublish: 'bg-teal-400',
  rejected: 'bg-red-400',
  online: 'bg-emerald-400',
  offline: 'bg-gray-400',
};

export function SkillVersionTimeline({
  versions,
  currentVersion,
  hasEditingVersion,
  hasReviewingVersion,
  onSelectVersion,
  onCreateDraft,
  onDeleteDraft,
  onSubmit,
  onPublish,
  onForcePublish,
  onOnline,
  onOffline,
  onDownload,
  showCreateDraftButton = true,
  allLabels,
  onSaveLabels,
  skillEnabled = true,
  isGlobalAdmin = false,
}: SkillVersionTimelineProps) {
  const { t } = useTranslation();
  const [labelEditVersion, setLabelEditVersion] = useState<string | null>(null);
  const [forcePublishConfirmVersion, setForcePublishConfirmVersion] = useState<string | null>(null);

  const sorted = sortVersionsDescending(versions);

  // Extract labels for a specific version (filter out 'latest')
  const getLabelsForVersion = (version: string): Record<string, string> => {
    if (!allLabels) return {};
    const result: Record<string, string> = {};
    for (const [key, val] of Object.entries(allLabels)) {
      if (val === version && key !== 'latest') {
        result[key] = val;
      }
    }
    return result;
  };

  const actionHandlers: Record<string, (version: string) => void> = {
    submit: onSubmit,
    publish: onPublish,
    forcePublish: (version: string) => setForcePublishConfirmVersion(version),
    online: onOnline,
    offline: onOffline,
    createDraftFrom: (version: string) => onCreateDraft(version),
  };

  const actionMeta: Record<string, { icon: React.ReactNode; labelKey: string; variant?: 'default' | 'outline' | 'destructive' | 'ghost' }> = {
    submit: { icon: <Send className="h-3 w-3" />, labelKey: 'skill.submit' },
    publish: { icon: <Rocket className="h-3 w-3" />, labelKey: 'skill.publish' },
    forcePublish: { icon: <ShieldAlert className="h-3 w-3" />, labelKey: 'skill.forcePublish', variant: 'outline' },
    online: { icon: <Globe className="h-3 w-3" />, labelKey: 'skill.online' },
    offline: { icon: <PowerOff className="h-3 w-3" />, labelKey: 'skill.offline', variant: 'outline' },
    deleteDraft: { icon: <Trash2 className="h-3 w-3" />, labelKey: 'common.delete', variant: 'destructive' },
    createDraftFrom: { icon: <GitBranch className="h-3 w-3" />, labelKey: 'skill.createDraftFrom' },
  };

  return (
    <div className="space-y-1">
      {/* Create draft button */}
      {showCreateDraftButton && ((() => {
        const hasDraft = hasEditingVersion || hasReviewingVersion;
        const btn = (
          <Button
            variant="outline"
            size="sm"
            className="mb-3 w-full"
            disabled={hasDraft}
            onClick={() => onCreateDraft()}
          >
            <Plus className="h-3.5 w-3.5 mr-1" />
            {t('skill.createDraft')}
          </Button>
        );
        return hasDraft ? (
          <Tooltip>
            <TooltipTrigger asChild>
              <span className="w-full">{btn}</span>
            </TooltipTrigger>
            <TooltipContent className="bg-amber-50 border border-amber-200 text-amber-800 dark:bg-amber-950 dark:border-amber-800 dark:text-amber-200">
              <span className="flex items-center gap-1.5">
                <AlertCircle className="h-3 w-3 shrink-0" />
                {t('skill.draftExistsTip')}
              </span>
            </TooltipContent>
          </Tooltip>
        ) : btn;
      })())}

      {/* Skill disabled banner */}
      {!skillEnabled && (
        <div className="flex items-center gap-2 px-3 py-2 mb-2 rounded-md bg-amber-50/60 border border-amber-200/60 dark:bg-amber-950/20 dark:border-amber-800/40">
          <ShieldOff className="h-3.5 w-3.5 text-amber-600 dark:text-amber-400 shrink-0" />
          <span className="text-[11px] text-amber-700 dark:text-amber-300">{t('skill.skillDisabledWarning')}</span>
        </div>
      )}

      {/* Timeline */}
      <div className="relative">
        {sorted.map((v, idx) => {
          const isActive = v.version === currentVersion;
          const pipelineInfo = parsePipelineInfo(v.publishPipelineInfo);
          const actionItems = getValidActionsWithContext(
            v.status,
            hasEditingVersion || hasReviewingVersion,
            pipelineInfo?.status,
            isGlobalAdmin,
            pipelineInfo?.historical,
          );

          const isPendingPublish = (v.status === 'reviewed' && pipelineInfo?.status !== 'REJECTED') || (v.status === 'reviewing' && pipelineInfo?.status === 'APPROVED');
          const isRejected = v.status === 'reviewed' && pipelineInfo?.status === 'REJECTED';
          const displayStatus = isRejected ? 'rejected' : isPendingPublish ? 'pendingPublish' : v.status;

          return (
            <div key={v.version} className="relative flex gap-3 pb-4">
              {/* Vertical line */}
              {idx < sorted.length - 1 && (
                <div className="absolute left-[7px] top-5 bottom-0 w-px bg-border" />
              )}

              {/* Dot */}
              <div
                className={cn(
                  'relative z-10 mt-1.5 h-[15px] w-[15px] shrink-0 rounded-full border-2 border-background',
                  DOT_STYLES[displayStatus] ?? 'bg-gray-400',
                  isActive && 'ring-2 ring-primary ring-offset-1',
                )}
              />

              {/* Content */}
              <div
                className={cn(
                  'flex-1 rounded-lg border p-3 cursor-pointer transition-colors',
                  isActive
                    ? 'border-primary/40 bg-primary/5'
                    : 'hover:bg-muted/40',
                )}
                onClick={() => onSelectVersion(v.version)}
              >
                {/* Header row */}
                <div className="flex items-center gap-2 flex-wrap">
                  <span className="font-medium text-sm">{v.version}</span>
                  <Badge
                    className={cn(
                      'text-[10px] px-1.5 py-0 h-4 font-medium border-0',
                      STATUS_STYLES[displayStatus],
                    )}
                  >
                    {t(`skill.versionStatus.${displayStatus}`)}
                  </Badge>
                  {/* Pipeline status badge */}
                  {pipelineInfo && (
                    <PipelineStatusDisplay pipelineInfo={pipelineInfo} compact />
                  )}
                  {v.downloadCount > 0 && (
                    <span className="inline-flex items-center gap-0.5 text-[10px] text-muted-foreground">
                      <Download className="h-2.5 w-2.5" />
                      {v.downloadCount}
                    </span>
                  )}
                </div>

                {/* Labels for this version */}
                {allLabels && (() => {
                  const vLabels = getLabelsForVersion(v.version);
                  const labelKeys = Object.keys(vLabels);
                  return labelKeys.length > 0 ? (
                    <div className="flex items-center gap-1 mt-1.5 flex-wrap">
                      <Tag className="h-3 w-3 text-muted-foreground shrink-0" />
                      {labelKeys.map((key) => (
                        <Badge
                          key={key}
                          variant="outline"
                          className="text-[10px] px-1.5 py-0 h-4 font-mono"
                        >
                          {key}
                        </Badge>
                      ))}
                    </div>
                  ) : null;
                })()}

                {/* Meta */}
                <div className="flex items-center gap-3 mt-1 text-[11px] text-muted-foreground">
                  {v.author && <span>{v.author}</span>}
                  <span className="inline-flex items-center gap-1">
                    <Clock className="h-3 w-3" />
                    {dayjs(v.updateTime).format('YYYY-MM-DD HH:mm')}
                  </span>
                </div>

                {v.commitMsg && (
                  <p className="mt-1 text-xs text-muted-foreground line-clamp-2">
                    {v.commitMsg}
                  </p>
                )}

                {/* Action buttons */}
                {(actionItems.length > 0 || onDownload || onSaveLabels) && (
                  <div
                    className="flex items-center gap-1.5 mt-2 flex-wrap"
                    onClick={(e) => e.stopPropagation()}
                  >
                    {actionItems.map((item) => {
                      const meta = actionMeta[item.action];
                      if (!meta) return null;

                      const handler =
                        item.action === 'deleteDraft'
                          ? onDeleteDraft
                          : actionHandlers[item.action];

                      const button = (
                        <Button
                          key={item.action}
                          variant={meta.variant ?? 'ghost'}
                          size="sm"
                          className="h-6 px-2 text-[11px]"
                          disabled={item.disabled}
                          onClick={() => handler?.(v.version)}
                        >
                          {meta.icon}
                          {t(meta.labelKey)}
                        </Button>
                      );

                      if (item.disabled && item.disabledReason) {
                        return (
                          <Tooltip key={item.action}>
                            <TooltipTrigger asChild>
                              <span>{button}</span>
                            </TooltipTrigger>
                            <TooltipContent className="bg-amber-50 border border-amber-200 text-amber-800 dark:bg-amber-950 dark:border-amber-800 dark:text-amber-200">
                              <span className="flex items-center gap-1.5">
                                <AlertCircle className="h-3 w-3 shrink-0" />
                                {t(item.disabledReason)}
                              </span>
                            </TooltipContent>
                          </Tooltip>
                        );
                      }

                      return button;
                    })}
                    {onDownload && v.status !== 'draft' && (
                      <Button
                        variant="ghost"
                        size="sm"
                        className="h-6 px-2 text-[11px]"
                        onClick={() => onDownload(v.version)}
                      >
                        <Download className="h-3 w-3" />
                        {t('skill.download')}
                      </Button>
                    )}
                    {onSaveLabels && v.status !== 'draft' && v.status !== 'reviewing' && (
                      <Button
                        variant="ghost"
                        size="sm"
                        className="h-6 px-2 text-[11px]"
                        onClick={() => setLabelEditVersion(v.version)}
                      >
                        <Tag className="h-3 w-3" />
                        {t('common.versionLabels.editLabels')}
                      </Button>
                    )}
                  </div>
                )}
              </div>
            </div>
          );
        })}

        {sorted.length === 0 && (
          <p className="text-sm text-muted-foreground text-center py-4">
            {t('skill.noVersions')}
          </p>
        )}
      </div>

      {/* Label bind dialog */}
      {onSaveLabels && labelEditVersion && (
        <LabelBindDialog
          open={!!labelEditVersion}
          onOpenChange={(open) => !open && setLabelEditVersion(null)}
          version={labelEditVersion}
          allLabels={allLabels ?? {}}
          onSave={onSaveLabels}
        />
      )}

      {/* Force-publish confirmation dialog */}
      <Dialog
        open={!!forcePublishConfirmVersion}
        onOpenChange={(open) => !open && setForcePublishConfirmVersion(null)}
      >
        <DialogContent>
          <DialogHeader>
            <DialogTitle className="flex items-center gap-2">
              <ShieldAlert className="h-5 w-5 text-destructive" />
              {t('skill.forcePublishConfirmTitle')}
            </DialogTitle>
            <DialogDescription>
              {t('skill.forcePublishConfirmDesc', { version: forcePublishConfirmVersion ?? '' })}
            </DialogDescription>
          </DialogHeader>
          <DialogFooter>
            <Button variant="outline" onClick={() => setForcePublishConfirmVersion(null)}>
              {t('common.cancel')}
            </Button>
            <Button
              variant="destructive"
              onClick={() => {
                if (forcePublishConfirmVersion) {
                  onForcePublish?.(forcePublishConfirmVersion);
                }
                setForcePublishConfirmVersion(null);
              }}
            >
              <ShieldAlert className="h-4 w-4 mr-1" />
              {t('skill.forcePublishConfirm')}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  );
}
