import { useState, useCallback, useEffect, useRef } from 'react';
import { useTranslation } from 'react-i18next';
import { toast } from 'sonner';
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog';
import { Button } from '@/components/ui/button';
import { Tooltip, TooltipContent, TooltipTrigger } from '@/components/ui/tooltip';
import { AlertCircle, Check, Upload } from 'lucide-react';
import { skillApi } from '@/api/skill';
import {
  buildSkillBatchZipExcludingPrefixes,
  getSkillEntryDisplayName,
  isInvalidSkillEntryCode,
} from '@/utils/skillUploadParser';
import type { SkillUploadPrecheckResult } from '@/types/skill';

function isValidZipFile(file: File): boolean {
  return file.name.toLowerCase().endsWith('.zip') || file.type === 'application/zip';
}

type BatchConflictPolicy = 'SKIP' | 'OVERWRITE';

interface BatchPrecheckState {
  items: SkillUploadPrecheckResult[];
}

interface BatchUploadItemResultData {
  name: string;
  success: boolean;
  errorCode: string;
  errorMessage: string;
  owner?: string;
}

interface BatchUploadResultData {
  results: BatchUploadItemResultData[];
}

function isPrecheckBlocked(result: SkillUploadPrecheckResult): boolean {
  return result.precheckCode !== 'READY'
    && result.precheckCode !== 'VERSION_ADJUSTED'
    && result.precheckCode !== 'DRAFT_EXISTS';
}

function getBatchItemName(item: SkillUploadPrecheckResult): string {
  if (isInvalidSkillEntryCode(item.precheckCode)) {
    return getSkillEntryDisplayName(item.entryPath) || item.skillName || '-';
  }
  return item.skillName || getSkillEntryDisplayName(item.entryPath) || '-';
}

interface UploadSkillDialogProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  namespaceId: string;
  onSuccess: () => void;
  initialFile?: File | null;
}

export function UploadSkillDialog({
  open,
  onOpenChange,
  namespaceId,
  onSuccess,
  initialFile,
}: UploadSkillDialogProps) {
  const { t } = useTranslation();
  const [file, setFile] = useState<File | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);
  const [checking, setChecking] = useState(false);
  const [isDragOver, setIsDragOver] = useState(false);
  const [precheck, setPrecheck] = useState<SkillUploadPrecheckResult | null>(null);
  const [batchPrecheck, setBatchPrecheck] = useState<BatchPrecheckState | null>(null);
  const [batchConflictPolicy, setBatchConflictPolicy] =
    useState<BatchConflictPolicy>('SKIP');
  const inputRef = useRef<HTMLInputElement>(null);
  const initialFileRef = useRef<File | null>(null);
  const precheckRequestRef = useRef(0);

  const reset = useCallback(() => {
    precheckRequestRef.current += 1;
    setFile(null);
    setError(null);
    setLoading(false);
    setChecking(false);
    setIsDragOver(false);
    setPrecheck(null);
    setBatchPrecheck(null);
    setBatchConflictPolicy('SKIP');
    initialFileRef.current = null;
  }, []);

  const handleClose = useCallback(
    (nextOpen: boolean) => {
      if (!nextOpen) reset();
      onOpenChange(nextOpen);
    },
    [onOpenChange, reset],
  );

  const handleFileSelect = useCallback(
    (selected: File | null) => {
      precheckRequestRef.current += 1;
      setError(null);
      setLoading(false);
      setChecking(false);
      setPrecheck(null);
      setBatchPrecheck(null);
      setBatchConflictPolicy('SKIP');
      if (selected && !isValidZipFile(selected)) {
        setError(t('skill.invalidZipFile'));
        setFile(null);
        return;
      }
      setFile(selected);
    },
    [t],
  );

  const handleFileChange = useCallback(
    (e: React.ChangeEvent<HTMLInputElement>) => {
      handleFileSelect(e.target.files?.[0] ?? null);
    },
    [handleFileSelect],
  );

  const handleDragOver = useCallback((e: React.DragEvent) => {
    e.preventDefault();
    e.stopPropagation();
    if (e.dataTransfer) {
      e.dataTransfer.dropEffect = 'copy';
    }
    setIsDragOver(true);
  }, []);

  const handleDragLeave = useCallback((e: React.DragEvent) => {
    e.preventDefault();
    e.stopPropagation();
    setIsDragOver(false);
  }, []);

  const handleDrop = useCallback(
    (e: React.DragEvent) => {
      e.preventDefault();
      e.stopPropagation();
      setIsDragOver(false);
      const droppedFile = e.dataTransfer?.files?.[0] ?? null;
      handleFileSelect(droppedFile);
    },
    [handleFileSelect],
  );

  const getPrecheckMessages = useCallback(
    (result: SkillUploadPrecheckResult) => {
      switch (result.precheckCode) {
        case 'NO_PERMISSION':
          return [t('skill.precheckNoPermission', { owner: result.owner || '-' })];
        case 'REVIEWING_EXISTS':
          return [t('skill.precheckReviewingBlocked', {
            version: result.reviewingVersion ?? '',
          })];
        case 'DRAFT_EXISTS':
          return [t('skill.precheckDraftOverwriteOnly', {
            draftVersion: result.editingVersion ?? result.targetVersion ?? '',
            version: result.targetVersion ?? '',
          })];
        case 'VERSION_ADJUSTED':
          return [t('skill.precheckVersionConverted', {
            parsedVersion: result.parsedVersion ?? '-',
            version: result.targetVersion ?? '-',
          })];
        case 'READY':
          return [t(result.exists
            ? 'skill.precheckExistingSkillCreateDraft'
            : 'skill.precheckNewSkill', {
            version: result.targetVersion ?? '-',
          })];
        case 'NOT_A_SKILL':
          return [t('skill.batchItemNonSkillFolderDesc')];
        case 'INVALID_SKILL':
          return [t('skill.batchItemInvalidSkillDesc')];
        default:
          return [t('skill.uploadPrecheckBlocked')];
      }
    },
    [t],
  );

  const getBatchItemDescription = useCallback(
    (item: SkillUploadPrecheckResult) => {
      switch (item.precheckCode) {
        case 'NO_PERMISSION':
          return t('skill.batchItemNoPermissionWithOwner', {
            owner: item.owner || '-',
          });
        case 'REVIEWING_EXISTS':
          return t('skill.precheckReviewingBlocked', {
            version: item.reviewingVersion ?? '',
          });
        case 'DRAFT_EXISTS':
        case 'READY':
          return t(item.exists
            ? 'skill.batchItemExistingVersionSummary'
            : 'skill.batchItemNewVersionSummary', {
            maxPublishedVersion: item.maxPublishedVersion ?? '-',
            targetVersion: item.targetVersion ?? '-',
          });
        case 'VERSION_ADJUSTED':
          return t(item.exists
            ? 'skill.batchItemExistingVersionAdjusted'
            : 'skill.batchItemVersionConverted', {
            maxPublishedVersion: item.maxPublishedVersion ?? '-',
            parsedVersion: item.parsedVersion ?? '-',
            version: item.targetVersion ?? '-',
            targetVersion: item.targetVersion ?? '-',
          });
        case 'NOT_A_SKILL':
          return t('skill.batchItemNonSkillFolderDesc');
        case 'INVALID_SKILL':
          return t('skill.batchItemInvalidSkillDesc');
        default:
          return t('skill.uploadPrecheckBlocked');
      }
    },
    [t],
  );

  const showBatchUploadResult = useCallback(
    (data: BatchUploadResultData | undefined, skippedCount = 0) => {
      const results = data?.results ?? [];
      const succeededList = results.filter(item => item.success);
      const failedList = results.filter(item => !item.success);
      if (failedList.length === 0) {
        const message = skippedCount > 0
          ? t('skill.batchUploadSuccessWithSkipped', {
            succeeded: succeededList.length,
            skipped: skippedCount,
          })
          : t('skill.batchUploadAllSuccess', { count: succeededList.length });
        toast.success(message, { duration: 5000 });
        return;
      }
      const title = succeededList.length > 0
        ? t('skill.batchUploadResult', {
          succeeded: succeededList.length,
          failed: failedList.length,
        })
        : t('skill.batchUploadAllFailed', { count: failedList.length });
      const description = (
        <div className="flex flex-col gap-0.5 text-xs">
          {succeededList.map((item) => (
            <div key={item.name} style={{ color: '#16a34a' }}>✓ {item.name}</div>
          ))}
          {skippedCount > 0 && (
            <div style={{ color: '#64748b' }}>
              - {t('skill.batchUploadSkipped', { count: skippedCount })}
            </div>
          )}
          {failedList.map((item) => (
            <div key={item.name} style={{ color: '#dc2626' }}>
              ✗ {item.name}
              <span style={{ opacity: 0.8 }}>
                {' '}— [{item.errorCode}] {item.errorMessage}
              </span>
            </div>
          ))}
        </div>
      );
      const toastFn = succeededList.length > 0 ? toast.warning : toast.error;
      toastFn(title, { description, duration: 8000 });
    },
    [t],
  );

  const runPrecheck = useCallback(
    async (selectedFile: File) => {
      const requestId = precheckRequestRef.current + 1;
      precheckRequestRef.current = requestId;
      setLoading(true);
      setChecking(true);
      setError(null);
      try {
        let resultList: SkillUploadPrecheckResult[];
        try {
          const res = await skillApi.precheckUpload(namespaceId, selectedFile);
          resultList = res.data ?? [];
        } catch (err: unknown) {
          if (requestId !== precheckRequestRef.current) {
            return;
          }
          const msg = err instanceof Error ? err.message : t('skill.uploadFailed');
          setError(msg);
          return;
        }
        if (requestId !== precheckRequestRef.current) {
          return;
        }
        if (resultList.length === 0) {
          setBatchPrecheck(null);
          setPrecheck(null);
          setError(t('skill.uploadPrecheckBlocked'));
          return;
        }
        if (resultList.length > 1
          || isInvalidSkillEntryCode(resultList[0].precheckCode)) {
          setPrecheck(null);
          setBatchConflictPolicy('SKIP');
          setBatchPrecheck({ items: resultList });
          return;
        }
        const result = resultList[0];
        setBatchPrecheck(null);
        setPrecheck(result);
      } catch (err: unknown) {
        if (requestId !== precheckRequestRef.current) {
          return;
        }
        const msg = err instanceof Error ? err.message : t('skill.uploadFailed');
        setError(msg);
      } finally {
        if (requestId === precheckRequestRef.current) {
          setChecking(false);
          setLoading(false);
        }
      }
    },
    [namespaceId, t],
  );

  const runUpload = useCallback(
    async (result: SkillUploadPrecheckResult) => {
      const res = await skillApi.upload(namespaceId, file as File, {
        overwrite: result.precheckCode === 'DRAFT_EXISTS',
      });
      toast.success(t('skill.uploadSuccessWithName', {
        name: res.data ?? result.skillName,
      }));
      handleClose(false);
      onSuccess();
    },
    [file, handleClose, namespaceId, onSuccess, t],
  );

  const runBatchUpload = useCallback(async () => {
    if (!file || !batchPrecheck) return;
    const items = batchPrecheck.items;
    const skillItems = items.filter(
      (item) => !isInvalidSkillEntryCode(item.precheckCode),
    );
    const draftItems = skillItems.filter((item) => item.precheckCode === 'DRAFT_EXISTS');
    if (batchConflictPolicy === 'SKIP' && draftItems.length > 0) {
      const uploadableNonDraftExists = skillItems.some(
        (item) => item.precheckCode !== 'DRAFT_EXISTS' && !isPrecheckBlocked(item),
      );
      if (!uploadableNonDraftExists) {
        setError(t('skill.batchUploadNothingToUpload'));
        return;
      }
      const uploadFile = await buildSkillBatchZipExcludingPrefixes(
        file,
        draftItems.map((item) => item.entryPath || ''),
      );
      const res = await skillApi.batchUpload(namespaceId, uploadFile, { overwrite: false });
      showBatchUploadResult(res.data, draftItems.length);
      handleClose(false);
      onSuccess();
      return;
    }
    const res = await skillApi.batchUpload(namespaceId, file, {
      overwrite: batchConflictPolicy === 'OVERWRITE',
    });
    showBatchUploadResult(res.data);
    handleClose(false);
    onSuccess();
  }, [
    batchConflictPolicy,
    batchPrecheck,
    file,
    handleClose,
    namespaceId,
    onSuccess,
    showBatchUploadResult,
    t,
  ]);

  useEffect(() => {
    if (file) {
      void runPrecheck(file);
    }
  }, [file, runPrecheck]);

  useEffect(() => {
    if (!open) {
      initialFileRef.current = null;
      return;
    }
    if (initialFile && initialFileRef.current !== initialFile) {
      initialFileRef.current = initialFile;
      handleFileSelect(initialFile);
    }
  }, [handleFileSelect, initialFile, open]);

  const handleUpload = useCallback(async () => {
    if (!file || (!precheck && !batchPrecheck)) return;
    setLoading(true);
    try {
      if (batchPrecheck) {
        await runBatchUpload();
        return;
      }
      const currentPrecheck = precheck;
      if (!currentPrecheck) {
        return;
      }
      if (isPrecheckBlocked(currentPrecheck)) {
        return;
      }
      await runUpload(currentPrecheck);
    } catch (err: unknown) {
      const msg =
        err instanceof Error ? err.message : t('skill.uploadFailed');
      setError(msg);
    } finally {
      setLoading(false);
    }
  }, [
    file,
    batchPrecheck,
    precheck,
    runBatchUpload,
    runUpload,
    t,
  ]);

  const precheckTargetVersion = precheck?.targetVersion;
  const precheckVersionConverted = precheck?.precheckCode === 'VERSION_ADJUSTED';
  const precheckMessages = precheck ? getPrecheckMessages(precheck) : [];
  const batchItems = batchPrecheck?.items ?? [];
  const batchSkillItems = batchItems.filter(
    (item) => !isInvalidSkillEntryCode(item.precheckCode),
  );
  const batchUploadOnlyCount = batchItems.length - batchSkillItems.length;
  const batchExistingCount = batchSkillItems.filter((item) => item.exists).length;
  const batchBlockedCount = batchSkillItems.filter(isPrecheckBlocked).length;
  const batchNewCount = batchSkillItems.filter((item) => !item.exists).length;
  const batchDraftCount = batchSkillItems.filter(
    (item) => item.precheckCode === 'DRAFT_EXISTS',
  ).length;
  const batchHasUploadableSkill = batchSkillItems.some((item) => {
    if (isPrecheckBlocked(item)) {
      return false;
    }
    return batchConflictPolicy === 'OVERWRITE' || item.precheckCode !== 'DRAFT_EXISTS';
  });
  const batchPolicyOptions = [
    {
      value: 'SKIP' as const,
      label: t('skill.batchPolicySkipDrafts'),
      description: t('skill.batchPolicySkipDraftsDesc'),
    },
    {
      value: 'OVERWRITE' as const,
      label: t('skill.batchPolicyOverwriteDrafts'),
      description: t('skill.batchPolicyOverwriteDraftsDesc'),
    },
  ];
  const buttonLabel = loading
    ? checking ? t('skill.uploadChecking') : t('common.loading')
    : batchPrecheck
      ? t('skill.confirmBatchUpload')
      : precheck?.precheckCode === 'DRAFT_EXISTS'
        ? t('skill.confirmForceOverwriteUpload')
        : t('skill.confirmUpload');
  const precheckMessageClass = precheck && isPrecheckBlocked(precheck)
    ? 'text-destructive'
    : 'text-muted-foreground';
  const canSubmit = !!file && !loading && !error
    && (batchPrecheck
      ? batchHasUploadableSkill
      : !!precheck && !isPrecheckBlocked(precheck));

  return (
    <Dialog open={open} onOpenChange={handleClose}>
      <DialogContent className="max-w-2xl">
        <DialogHeader>
          <DialogTitle>{t('skill.uploadZip')}</DialogTitle>
          <DialogDescription>
            {t('skill.uploadZipDesc')}
          </DialogDescription>
        </DialogHeader>

        <div className="space-y-4">
          <div
            className={`flex flex-col items-center justify-center gap-2 rounded-md border-2 border-dashed p-6 cursor-pointer transition-colors ${
              isDragOver
                ? 'border-primary bg-primary/5'
                : 'hover:border-primary/50'
            }`}
            onClick={() => inputRef.current?.click()}
            onDragOver={handleDragOver}
            onDragEnter={handleDragOver}
            onDragLeave={handleDragLeave}
            onDrop={handleDrop}
          >
            <Upload className="h-8 w-8 text-muted-foreground" />
            <p className="text-sm text-muted-foreground text-center">
              {isDragOver
                ? t('skill.dropFileHere')
                : file
                  ? file.name
                  : t('skill.dragOrClick')}
            </p>
            <input
              ref={inputRef}
              type="file"
              accept=".zip,application/zip"
              className="hidden"
              onChange={handleFileChange}
            />
          </div>

          {precheck && (
            <div className="rounded-md border p-3 text-sm space-y-3">
              <div className="grid grid-cols-[auto_1fr] gap-x-3 gap-y-1">
                <span className="text-muted-foreground">{t('skill.skillName')}</span>
                <span className="font-medium break-all">{precheck.skillName}</span>
                {precheck.owner && (
                  <>
                    <span className="text-muted-foreground">{t('skill.owner')}</span>
                    <span className="break-all">{precheck.owner}</span>
                  </>
                )}
                {precheck.exists && precheck.precheckCode !== 'NO_PERMISSION' && (
                  <>
                    <span className="text-muted-foreground">
                      {t('skill.maxPublishedVersion')}
                    </span>
                    <span>{precheck.maxPublishedVersion || '-'}</span>
                  </>
                )}
                <span className="text-muted-foreground">
                  {t(precheckVersionConverted ? 'skill.uploadedVersion' : 'skill.parsedVersion')}
                </span>
                <span>{precheck.parsedVersion || '-'}</span>
                <span className="text-muted-foreground">{t('skill.resolvedVersion')}</span>
                <span>{precheckTargetVersion || '-'}</span>
              </div>

              {precheckMessages.length > 0 && (
                <div className="flex gap-2 rounded-md bg-muted p-2">
                  <AlertCircle className={`h-4 w-4 mt-0.5 shrink-0 ${
                    precheckMessageClass
                  }`} />
                  <div className="space-y-1">
                    {precheckMessages.map((item) => (
                      <p key={item} className={precheckMessageClass}>{item}</p>
                    ))}
                  </div>
                </div>
              )}

            </div>
          )}

          {batchPrecheck && (
            <div className="rounded-md border p-3 text-sm space-y-3">
              <div className="grid grid-cols-[auto_1fr] gap-x-3 gap-y-1">
                <span className="text-muted-foreground">{t('skill.targetNamespace')}</span>
                <span className="font-medium break-all">{namespaceId}</span>
                <span className="text-muted-foreground">{t('skill.batchSkillCount')}</span>
                <span>{batchSkillItems.length}</span>
                {batchUploadOnlyCount > 0 && (
                  <>
                    <span className="text-muted-foreground">
                      {t('skill.batchUploadOnlyEntryCount')}
                    </span>
                    <span>{batchUploadOnlyCount}</span>
                  </>
                )}
              </div>

              <div className="flex gap-2 rounded-md bg-muted p-2">
                <AlertCircle className={`h-4 w-4 mt-0.5 shrink-0 ${
                  batchBlockedCount > 0 ? 'text-destructive' : 'text-muted-foreground'
                }`} />
                <div className="space-y-1 text-muted-foreground">
                  <p>{t('skill.batchPrecheckSummary', {
                    total: batchSkillItems.length,
                    fresh: batchNewCount,
                    existing: batchExistingCount,
                    blocked: batchBlockedCount,
                  })}
                  </p>
                  {batchBlockedCount > 0 && (
                    <p className="text-destructive">{t('skill.batchPrecheckBlockedTip')}</p>
                  )}
                  {batchUploadOnlyCount > 0 && (
                    <p>{t('skill.batchPrecheckUploadOnlyTip', {
                      count: batchUploadOnlyCount,
                    })}
                    </p>
                  )}
                </div>
              </div>

              {batchDraftCount > 0 && (
                <div className="space-y-2">
                  <label className="block font-medium">{t('skill.sameSkillPolicy')}</label>
                  <div className="space-y-2" role="radiogroup">
                    {batchPolicyOptions.map((option) => {
                      const selected = batchConflictPolicy === option.value;
                      return (
                        <button
                          key={option.value}
                          type="button"
                          role="radio"
                          aria-checked={selected}
                          className={`flex w-full gap-3 rounded-md border p-3 text-left transition-colors ${
                            selected
                              ? 'border-primary bg-primary/5 ring-1 ring-primary/20'
                              : 'hover:border-primary/50'
                          }`}
                          onClick={() => {
                            setError(null);
                            setBatchConflictPolicy(option.value);
                          }}
                        >
                          <span className={`mt-0.5 flex h-5 w-5 shrink-0 items-center justify-center rounded-full border ${
                            selected
                              ? 'border-primary bg-primary text-primary-foreground'
                              : 'border-muted-foreground/40'
                          }`}
                          >
                            {selected && <Check className="h-3.5 w-3.5" />}
                          </span>
                          <span className="min-w-0">
                            <span className="block font-medium">{option.label}</span>
                            <span className="block text-xs text-muted-foreground">
                              {option.description}
                            </span>
                          </span>
                        </button>
                      );
                    })}
                  </div>
                </div>
              )}

              <div className="max-h-44 space-y-1 overflow-y-auto rounded-md border p-2">
                {batchItems.map((item) => {
                  const uploadOnly = isInvalidSkillEntryCode(item.precheckCode);
                  const nonSkill = item.precheckCode === 'NOT_A_SKILL';
                  const blocked = !uploadOnly && isPrecheckBlocked(item);
                  const itemName = getBatchItemName(item);
                  return (
                    <div
                      key={`${item.entryPath || ''}:${item.skillName || ''}`}
                      className="flex items-center justify-between gap-3 py-1"
                    >
                      <div className="min-w-0">
                        {uploadOnly && item.entryPath ? (
                          <Tooltip>
                            <TooltipTrigger asChild>
                              <p className="truncate font-medium cursor-help" tabIndex={0}>
                                {itemName}
                              </p>
                            </TooltipTrigger>
                            <TooltipContent className="max-w-sm break-all">
                              {item.entryPath}
                            </TooltipContent>
                          </Tooltip>
                        ) : (
                          <p className="truncate font-medium">{itemName}</p>
                        )}
                        <p className="text-xs text-muted-foreground truncate">
                          {getBatchItemDescription(item)}
                        </p>
                      </div>
                      <span className={`shrink-0 text-xs ${
                        blocked
                          ? 'text-destructive'
                          : uploadOnly
                            ? 'text-muted-foreground'
                            : item.exists
                              ? 'text-amber-600'
                              : 'text-emerald-600'
                      }`}
                      >
                        {blocked
                          ? t('skill.batchItemBlocked')
                          : uploadOnly
                            ? t(nonSkill
                              ? 'skill.batchItemNonSkillFolder'
                              : 'skill.batchItemInvalidSkill')
                            : item.precheckCode === 'DRAFT_EXISTS'
                              ? t('skill.batchItemDraft')
                              : item.exists
                                ? t('skill.batchItemExisting')
                                : t('skill.batchItemNew')}
                      </span>
                    </div>
                  );
                })}
              </div>
            </div>
          )}

          {error && <p className="text-sm text-destructive">{error}</p>}
        </div>

        <DialogFooter>
          <Button variant="outline" onClick={() => handleClose(false)} disabled={loading}>
            {t('common.cancel')}
          </Button>
          <Button onClick={handleUpload} disabled={!canSubmit}>
            {buttonLabel}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
