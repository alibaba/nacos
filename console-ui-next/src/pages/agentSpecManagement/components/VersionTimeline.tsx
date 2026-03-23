import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import {
  Send,
  Rocket,
  Globe,
  PowerOff,
  Trash2,
  Clock,
  Plus,
  Tag,
} from 'lucide-react';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogDescription,
} from '@/components/ui/dialog';
import { cn } from '@/lib/utils';
import dayjs from 'dayjs';
import type { AgentSpecVersionSummary } from '@/types/agentspec';
import { parsePipelineInfo } from '@/types/agentspec';
import { getValidActions, sortVersionsDescending } from './version-utils';
import { PipelineStatusDisplay } from '@/pages/skillManagement/components/PipelineStatusDisplay';
import { VersionLabelEditor } from '@/components/ai/VersionLabelEditor';

interface VersionTimelineProps {
  versions: AgentSpecVersionSummary[];
  currentVersion: string;
  onSelectVersion: (version: string) => void;
  onCreateDraft: (basedOnVersion?: string) => void;
  onDeleteDraft: (version: string) => void;
  onSubmit: (version: string) => void;
  onPublish: (version: string) => void;
  onOnline: (version: string) => void;
  onOffline: (version: string) => void;
  showCreateDraftButton?: boolean;
  allLabels?: Record<string, string>;
  onSaveLabels?: (labels: Record<string, string>) => Promise<void>;
}

const STATUS_STYLES: Record<string, string> = {
  draft:
    'bg-amber-50 text-amber-700 dark:bg-amber-950/40 dark:text-amber-300',
  reviewing:
    'bg-blue-50 text-blue-700 dark:bg-blue-950/40 dark:text-blue-300',
  online:
    'bg-emerald-50 text-emerald-700 dark:bg-emerald-950/40 dark:text-emerald-300',
  offline:
    'bg-gray-100 text-gray-500 dark:bg-gray-800 dark:text-gray-400',
};

const DOT_STYLES: Record<string, string> = {
  draft: 'bg-amber-400',
  reviewing: 'bg-blue-400',
  online: 'bg-emerald-400',
  offline: 'bg-gray-400',
};

export function VersionTimeline({
  versions,
  currentVersion,
  onSelectVersion,
  onCreateDraft,
  onDeleteDraft,
  onSubmit,
  onPublish,
  onOnline,
  onOffline,
  showCreateDraftButton = true,
  allLabels,
  onSaveLabels,
}: VersionTimelineProps) {
  const { t } = useTranslation();
  const [labelEditVersion, setLabelEditVersion] = useState<string | null>(null);
  const [labelEditSaving, setLabelEditSaving] = useState(false);

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

  const handleLabelSave = async (versionLabels: Record<string, string>) => {
    if (!onSaveLabels || !labelEditVersion) return;
    setLabelEditSaving(true);
    try {
      const merged: Record<string, string> = {};
      if (allLabels) {
        for (const [key, val] of Object.entries(allLabels)) {
          if (val !== labelEditVersion) {
            merged[key] = val;
          }
        }
      }
      Object.assign(merged, versionLabels);
      await onSaveLabels(merged);
      setLabelEditVersion(null);
    } finally {
      setLabelEditSaving(false);
    }
  };

  const actionHandlers: Record<string, (version: string) => void> = {
    submit: onSubmit,
    publish: onPublish,
    online: onOnline,
    offline: onOffline,
  };

  const actionMeta: Record<string, { icon: React.ReactNode; labelKey: string; variant?: 'default' | 'outline' | 'destructive' | 'ghost' }> = {
    submit: { icon: <Send className="h-3 w-3" />, labelKey: 'agentSpec.submit' },
    publish: { icon: <Rocket className="h-3 w-3" />, labelKey: 'agentSpec.publish' },
    online: { icon: <Globe className="h-3 w-3" />, labelKey: 'agentSpec.online' },
    offline: { icon: <PowerOff className="h-3 w-3" />, labelKey: 'agentSpec.offline', variant: 'outline' },
    deleteDraft: { icon: <Trash2 className="h-3 w-3" />, labelKey: 'common.delete', variant: 'destructive' },
  };

  return (
    <div className="space-y-1">
      {/* Create draft button */}
      {showCreateDraftButton && (
        <Button
          variant="outline"
          size="sm"
          className="mb-3 w-full"
          onClick={() => onCreateDraft()}
        >
          <Plus className="h-3.5 w-3.5 mr-1" />
          {t('agentSpec.createDraft')}
        </Button>
      )}

      {/* Timeline */}
      <div className="relative">
        {sorted.map((v, idx) => {
          const isActive = v.version === currentVersion;
          const actions = getValidActions(v.status);
          const pipelineInfo = parsePipelineInfo(v.publishPipelineInfo);

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
                  DOT_STYLES[v.status] ?? 'bg-gray-400',
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
                      STATUS_STYLES[v.status],
                    )}
                  >
                    {t(`agentSpec.versionStatus.${v.status}`)}
                  </Badge>
                  {v.status === 'reviewing' && pipelineInfo && (
                    <PipelineStatusDisplay
                      pipelineInfo={pipelineInfo}
                      compact
                      translationPrefix="agentSpec"
                    />
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

                {v.description && (
                  <p className="mt-1 text-xs text-muted-foreground line-clamp-2">
                    {v.description}
                  </p>
                )}

                {/* Action buttons */}
                {(actions.length > 0 || onSaveLabels) && (
                  <div
                    className="flex items-center gap-1.5 mt-2 flex-wrap"
                    onClick={(e) => e.stopPropagation()}
                  >
                    {actions.map((action) => {
                      const meta = actionMeta[action];
                      if (!meta) return null;

                      const handler =
                        action === 'deleteDraft'
                          ? onDeleteDraft
                          : actionHandlers[action];

                      return (
                        <Button
                          key={action}
                          variant={meta.variant ?? 'ghost'}
                          size="sm"
                          className="h-6 px-2 text-[11px]"
                          onClick={() => handler?.(v.version)}
                        >
                          {meta.icon}
                          {t(meta.labelKey)}
                        </Button>
                      );
                    })}
                    {onSaveLabels && (
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
            {t('agentSpec.noVersions')}
          </p>
        )}
      </div>

      {/* Label edit dialog */}
      <Dialog open={!!labelEditVersion} onOpenChange={(open) => !open && setLabelEditVersion(null)}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>{t('common.versionLabels.editLabels')} - {labelEditVersion}</DialogTitle>
            <DialogDescription>
              {t('common.versionLabels.title')}
            </DialogDescription>
          </DialogHeader>
          {labelEditVersion && (
            <VersionLabelEditor
              labels={getLabelsForVersion(labelEditVersion)}
              onSave={handleLabelSave}
              isSaving={labelEditSaving}
            />
          )}
        </DialogContent>
      </Dialog>
    </div>
  );
}
