import { useEffect, useMemo, useState, type ReactElement } from 'react';
import { DiffEditor } from '@monaco-editor/react';
import { useTranslation } from 'react-i18next';
import {
  ArrowRightLeft,
  FileMinus2,
  FilePlus2,
  FileText,
  GitCompareArrows,
} from 'lucide-react';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Card, CardContent } from '@/components/ui/card';
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select';
import { Skeleton } from '@/components/ui/skeleton';
import { skillApi } from '@/api/skill';
import type { SkillDocument, SkillVersionSummary } from '@/types/skill';
import { cn } from '@/lib/utils';
import { getFileCategory, getLanguageFromFileName } from './skill-resource-utils';
import {
  compareSkillVersions,
  type SkillVersionFileDiff,
} from './skill-version-diff-utils';

interface SkillVersionDiffPanelProps {
  namespaceId: string;
  skillName: string;
  versions: SkillVersionSummary[];
  selectedVersion: string;
}

interface LoadedDocs {
  key: string;
  before: SkillDocument | null;
  after: SkillDocument | null;
}

const DIFF_EDITOR_HEIGHT = 'clamp(360px, calc(100vh - 440px), 580px)';

export function SkillVersionDiffPanel({
  namespaceId,
  skillName,
  versions,
  selectedVersion,
}: SkillVersionDiffPanelProps) {
  const { t } = useTranslation();
  const [beforeVersionChoice, setBeforeVersionChoice] = useState('');
  const [afterVersionChoice, setAfterVersionChoice] = useState('');
  const [loadedDocs, setLoadedDocs] = useState<LoadedDocs | null>(null);
  const [selectedPath, setSelectedPath] = useState('');

  const versionNames = useMemo(() => versions.map((version) => version.version), [versions]);
  const selectedVersionAvailable =
    !!selectedVersion && versionNames.includes(selectedVersion);
  const beforeVersion = versionNames.includes(beforeVersionChoice)
    ? beforeVersionChoice
    : '';
  const afterVersion = versionNames.includes(afterVersionChoice)
    ? afterVersionChoice
    : selectedVersionAvailable
      ? selectedVersion
      : '';
  const docsKey = beforeVersion && afterVersion ? `${beforeVersion}\n${afterVersion}` : '';
  const docsReady = !!docsKey && loadedDocs?.key === docsKey;
  const beforeDoc = docsReady ? loadedDocs.before : null;
  const afterDoc = docsReady ? loadedDocs.after : null;
  const loading = !!docsKey && !docsReady;

  useEffect(() => {
    if (!skillName || !beforeVersion || !afterVersion || !docsKey) return;

    let cancelled = false;
    Promise.all([
      skillApi.getVersion({ namespaceId, skillName, version: beforeVersion }),
      skillApi.getVersion({ namespaceId, skillName, version: afterVersion }),
    ])
      .then(([beforeResponse, afterResponse]) => {
        if (cancelled) return;
        setLoadedDocs({
          key: docsKey,
          before: beforeResponse.data,
          after: afterResponse.data,
        });
      })
      .catch(() => {
        if (cancelled) return;
        setLoadedDocs({ key: docsKey, before: null, after: null });
      });

    return () => {
      cancelled = true;
    };
  }, [namespaceId, skillName, beforeVersion, afterVersion, docsKey]);

  const diff = useMemo(
    () => compareSkillVersions(beforeDoc, afterDoc),
    [beforeDoc, afterDoc],
  );

  const changedFiles = useMemo(
    () => [...diff.modified, ...diff.added, ...diff.removed],
    [diff.added, diff.modified, diff.removed],
  );

  const selectedDiff =
    changedFiles.find((file) => file.path === selectedPath) ?? changedFiles[0] ?? null;
  const sameVersion = beforeVersion === afterVersion;

  return (
    <div className="space-y-4">
      <Card className="py-0 gap-0 overflow-hidden">
        <CardContent className="p-4">
          <div className="flex flex-col gap-3 lg:flex-row lg:items-end lg:justify-between">
            <div className="grid gap-3 sm:grid-cols-[minmax(0,220px)_auto_minmax(0,220px)] sm:items-end">
              <VersionSelect
                label={t('skill.diffBeforeVersion')}
                placeholder={t('skill.diffSelectVersionPlaceholder')}
                value={beforeVersion}
                versions={versionNames}
                onChange={setBeforeVersionChoice}
              />
              <Button
                variant="outline"
                size="icon"
                className="h-9 w-9 sm:mb-0.5"
                onClick={() => {
                  setBeforeVersionChoice(afterVersion);
                  setAfterVersionChoice(beforeVersion);
                }}
                disabled={!beforeVersion || !afterVersion}
                aria-label={t('skill.diffSwapVersions')}
              >
                <ArrowRightLeft className="h-4 w-4" />
              </Button>
              <VersionSelect
                label={t('skill.diffAfterVersion')}
                placeholder={t('skill.diffSelectVersionPlaceholder')}
                value={afterVersion}
                versions={versionNames}
                onChange={setAfterVersionChoice}
              />
            </div>
            <div className="flex flex-wrap items-center gap-2">
              <DiffCountBadge
                label={t('skill.diffAddedFiles')}
                count={diff.added.length}
                tone="added"
              />
              <DiffCountBadge
                label={t('skill.diffRemovedFiles')}
                count={diff.removed.length}
                tone="removed"
              />
              <DiffCountBadge
                label={t('skill.diffModifiedFiles')}
                count={diff.modified.length}
                tone="modified"
              />
            </div>
          </div>
        </CardContent>
      </Card>

      {!beforeVersion || !afterVersion ? (
        <EmptyDiff icon={<GitCompareArrows />} message={t('skill.diffSelectVersions')} />
      ) : loading ? (
        <div className="space-y-3">
          <Skeleton className="h-16 w-full" />
          <Skeleton className="h-[460px] w-full" />
        </div>
      ) : sameVersion ? (
        <EmptyDiff icon={<GitCompareArrows />} message={t('skill.diffSameVersion')} />
      ) : changedFiles.length === 0 ? (
        <EmptyDiff icon={<FileText />} message={t('skill.diffNoChanges')} />
      ) : (
        <div className="grid gap-4 lg:grid-cols-[320px_minmax(0,1fr)]">
          <Card className="py-0 gap-0 overflow-hidden">
            <div className="border-b bg-muted/30 px-4 py-3">
              <h2 className="flex items-center gap-2 text-sm font-semibold">
                <GitCompareArrows className="h-4 w-4 text-muted-foreground" />
                {t('skill.diffChangedFiles')}
              </h2>
            </div>
            <CardContent
              className="overflow-y-auto p-2"
              style={{ maxHeight: DIFF_EDITOR_HEIGHT }}
            >
              {changedFiles.map((file) => (
                <button
                  key={`${file.status}:${file.path}`}
                  type="button"
                  className={cn(
                    'flex w-full items-center gap-2 rounded-md px-2.5 py-2 text-left text-xs transition-colors',
                    selectedDiff?.path === file.path ? 'bg-primary/10 text-primary' : 'hover:bg-muted',
                  )}
                  onClick={() => setSelectedPath(file.path)}
                >
                  <FileStatusIcon status={file.status} />
                  <span className="min-w-0 flex-1 truncate font-mono">{file.path}</span>
                  <FileStatusBadge status={file.status} />
                </button>
              ))}
            </CardContent>
          </Card>

          <Card className="py-0 gap-0 overflow-hidden">
            <div className="flex items-center justify-between border-b bg-muted/30 px-4 py-3">
              <h2 className="min-w-0 truncate font-mono text-sm font-semibold">
                {selectedDiff?.path ?? '-'}
              </h2>
            </div>
            <CardContent className="p-0">
              {selectedDiff && (
                <div
                  key={`${docsKey}:${selectedDiff.status}:${selectedDiff.path}`}
                  className="overflow-hidden"
                  style={{ height: DIFF_EDITOR_HEIGHT }}
                >
                  {supportsTextDiff(selectedDiff.path) ? (
                    <DiffEditor
                      height="100%"
                      language={getLanguageFromFileName(selectedDiff.path)}
                      original={selectedDiff.beforeContent ?? ''}
                      modified={selectedDiff.afterContent ?? ''}
                      theme="vs"
                      options={{
                        readOnly: true,
                        minimap: { enabled: false },
                        renderSideBySide: true,
                        scrollBeyondLastLine: false,
                        wordWrap: 'on',
                        automaticLayout: true,
                        fontSize: 13,
                        renderOverviewRuler: true,
                        overviewRulerLanes: 3,
                        scrollbar: {
                          verticalScrollbarSize: 10,
                          horizontalScrollbarSize: 10,
                        },
                      }}
                      loading={
                        <div className="flex h-full items-center justify-center text-sm text-muted-foreground">
                          {t('agentSpec.editorLoading')}
                        </div>
                      }
                    />
                  ) : (
                    <NonTextDiffPlaceholder diff={selectedDiff} />
                  )}
                </div>
              )}
            </CardContent>
          </Card>
        </div>
      )}
    </div>
  );
}

function supportsTextDiff(path: string): boolean {
  return getFileCategory(path) === 'text';
}

function VersionSelect({
  label,
  placeholder,
  value,
  versions,
  onChange,
}: {
  label: string;
  placeholder: string;
  value: string;
  versions: string[];
  onChange: (value: string) => void;
}) {
  return (
    <div className="space-y-1.5">
      <span className="text-xs text-muted-foreground">{label}</span>
      <Select value={value} onValueChange={onChange}>
        <SelectTrigger className="h-9">
          <SelectValue placeholder={placeholder} />
        </SelectTrigger>
        <SelectContent>
          {versions.map((version) => (
            <SelectItem key={version} value={version}>
              {version}
            </SelectItem>
          ))}
        </SelectContent>
      </Select>
    </div>
  );
}

function DiffCountBadge({
  label,
  count,
  tone,
}: {
  label: string;
  count: number;
  tone: 'added' | 'removed' | 'modified';
}) {
  const className =
    tone === 'added'
      ? 'bg-emerald-50 text-emerald-700 dark:bg-emerald-950/40 dark:text-emerald-300'
      : tone === 'removed'
        ? 'bg-red-50 text-red-700 dark:bg-red-950/40 dark:text-red-300'
        : 'bg-blue-50 text-blue-700 dark:bg-blue-950/40 dark:text-blue-300';

  return (
    <Badge className={cn('border-0 font-medium', className)}>
      {label}: {count}
    </Badge>
  );
}

function FileStatusIcon({ status }: { status: SkillVersionFileDiff['status'] }) {
  if (status === 'added') return <FilePlus2 className="h-3.5 w-3.5 text-emerald-600" />;
  if (status === 'removed') return <FileMinus2 className="h-3.5 w-3.5 text-red-600" />;
  return <FileText className="h-3.5 w-3.5 text-blue-600" />;
}

function FileStatusBadge({ status }: { status: SkillVersionFileDiff['status'] }) {
  const { t } = useTranslation();
  const labelKey =
    status === 'added'
      ? 'skill.diffStatusAdded'
      : status === 'removed'
        ? 'skill.diffStatusRemoved'
        : 'skill.diffStatusModified';
  return (
    <Badge variant="outline" className="h-5 px-1.5 text-[10px]">
      {t(labelKey)}
    </Badge>
  );
}

function NonTextDiffPlaceholder({ diff }: { diff: SkillVersionFileDiff }) {
  const { t } = useTranslation();

  return (
    <div className="flex h-full flex-col items-center justify-center gap-3 bg-muted/10 p-6 text-center text-muted-foreground">
      <FileText className="h-8 w-8 text-muted-foreground/60" />
      <div className="space-y-1">
        <h3 className="text-sm font-medium text-foreground">
          {t('skill.diffTextUnsupported')}
        </h3>
        <p className="max-w-md text-xs leading-relaxed">
          {t('skill.diffTextUnsupportedDesc')}
        </p>
      </div>
      <div className="flex max-w-full items-center gap-2 rounded-md border bg-background px-3 py-2 text-xs">
        <span className="min-w-0 truncate font-mono text-foreground">{diff.path}</span>
        <FileStatusBadge status={diff.status} />
      </div>
    </div>
  );
}

function EmptyDiff({ icon, message }: { icon: ReactElement; message: string }) {
  return (
    <div className="flex min-h-[320px] flex-col items-center justify-center gap-3 rounded-lg border bg-card text-muted-foreground">
      {icon}
      <p className="text-sm">{message}</p>
    </div>
  );
}
