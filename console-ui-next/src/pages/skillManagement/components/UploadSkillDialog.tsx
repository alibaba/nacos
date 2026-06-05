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
import { AlertCircle, Check, Upload } from 'lucide-react';
import { skillApi } from '@/api/skill';
import {
  buildSkillBatchZipExcludingPrefixes,
  parseSkillUploadEntries,
} from '@/utils/skillUploadParser';
import type { SkillUploadPrecheckResult } from '@/types/skill';
import type { ParsedSkillUploadEntry } from '@/utils/skillUploadParser';

function isValidZipFile(file: File): boolean {
  return file.name.toLowerCase().endsWith('.zip') || file.type === 'application/zip';
}

type BatchConflictPolicy = 'SKIP' | 'OVERWRITE';

interface BatchPrecheckItem extends ParsedSkillUploadEntry {
  result?: SkillUploadPrecheckResult;
}

interface BatchPrecheckState {
  items: BatchPrecheckItem[];
}

interface BatchUploadResultData {
  succeeded?: string[];
  failed?: { name: string; reason: string }[];
}

function isBatchItemBlocked(item: BatchPrecheckItem): boolean {
  if (item.kind !== 'SKILL') {
    return false;
  }
  return !item.result
    || item.result.status === 'FORBIDDEN'
    || item.result.status === 'CONFLICT'
    || item.result.actions.length === 0;
}

function getBatchItemName(item: BatchPrecheckItem): string {
  return item.result?.skillName || item.request?.skillName || item.entryKey;
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
      const messages: string[] = [];
      const createAction = result.actions.find((item) => item.type === 'CREATE_DRAFT');

      if (result.status === 'FORBIDDEN' || !result.writable) {
        messages.push(t('skill.precheckNoPermission'));
        return messages;
      }

      if (result.reviewingExists) {
        messages.push(t('skill.precheckReviewingBlocked', {
          version: result.reviewingVersion ?? '',
        }));
        return messages;
      }

      if (!result.exists) {
        messages.push(t('skill.precheckNewSkill', {
          version: createAction?.resultVersion ?? result.resolvedVersion,
        }));
        return messages;
      }

      if (result.versionExists) {
        messages.push(t('skill.precheckVersionExists', {
          version: result.parsedVersion,
        }));
      }

      if (result.draftExists) {
        messages.push(t('skill.precheckDraftOverwriteOnly', {
          draftVersion: result.editingVersion ?? result.resolvedVersion,
          parsedVersion: result.parsedVersion,
          version: result.resolvedVersion,
        }));
        return messages;
      }

      messages.push(t('skill.precheckExistingSkillCreateDraft', {
        version: createAction?.resultVersion ?? result.resolvedVersion,
      }));
      if (createAction && createAction.resultVersion !== result.parsedVersion) {
        messages.push(t('skill.precheckCreateVersionAdjusted', {
          parsedVersion: result.parsedVersion,
          version: createAction.resultVersion,
        }));
      }
      return messages.length > 0
        ? messages
        : [...result.errors, ...result.warnings, t('skill.uploadPrecheckBlocked')];
    },
    [t],
  );

  const showBatchUploadResult = useCallback(
    (data: BatchUploadResultData | undefined, skippedCount = 0) => {
      const succeededList = data?.succeeded ?? [];
      const failedList = data?.failed ?? [];
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
          {succeededList.map((name) => (
            <div key={name} style={{ color: '#16a34a' }}>✓ {name}</div>
          ))}
          {skippedCount > 0 && (
            <div style={{ color: '#64748b' }}>
              - {t('skill.batchUploadSkipped', { count: skippedCount })}
            </div>
          )}
          {failedList.map((item) => (
            <div key={item.name} style={{ color: '#dc2626' }}>
              ✗ {item.name}<span style={{ opacity: 0.8 }}> — {item.reason}</span>
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
        const parsedEntries = await parseSkillUploadEntries(namespaceId, selectedFile);
        if (requestId !== precheckRequestRef.current) {
          return;
        }
        const validEntries = parsedEntries.filter(
          (entry) => entry.kind === 'SKILL' && entry.request,
        );
        if (validEntries.length === 0) {
          throw new Error(
            parsedEntries[0]?.error || 'SKILL.md file not found in zip');
        }
        const requests = validEntries.map((entry) => entry.request!);
        let resultList: SkillUploadPrecheckResult[] = [];
        try {
          const res = await skillApi.batchPrecheckUpload(requests);
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
        if (parsedEntries.length > 1) {
          let cursor = 0;
          const items = parsedEntries.map((entry) => {
            if (entry.kind !== 'SKILL' || !entry.request) {
              return entry;
            }
            const result = resultList[cursor++];
            return result ? { ...entry, result } : entry;
          });
          setPrecheck(null);
          setBatchConflictPolicy('SKIP');
          setBatchPrecheck({ items });
          return;
        }
        const result = resultList[0] ?? null;
        setBatchPrecheck(null);
        setPrecheck(result);
        if (!result || result.actions.length === 0) {
          setError((result?.errors ?? []).join('; ') || t('skill.uploadPrecheckBlocked'));
        }
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
        overwrite: result.draftExists,
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
    const skillItems = items.filter((item) => item.kind === 'SKILL');
    const uploadOnlyItems = items.filter((item) => item.kind !== 'SKILL');
    const draftItems = items.filter((item) => item.result?.draftExists);
    if (batchConflictPolicy === 'SKIP' && draftItems.length > 0) {
      if (draftItems.length === skillItems.length && uploadOnlyItems.length === 0) {
        setError(t('skill.batchUploadNothingToUpload'));
        return;
      }
      const uploadFile = await buildSkillBatchZipExcludingPrefixes(
        file,
        draftItems.map((item) => item.rootPrefix),
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
      if (currentPrecheck.actions.length === 0) {
        setError(currentPrecheck.errors.join('; ') || t('skill.uploadPrecheckBlocked'));
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

  const precheckTargetVersion = precheck?.actions[0]?.resultVersion ?? precheck?.resolvedVersion;
  const precheckMessages = precheck ? getPrecheckMessages(precheck) : [];
  const batchItems = batchPrecheck?.items ?? [];
  const batchSkillItems = batchItems.filter((item) => item.kind === 'SKILL');
  const batchUploadOnlyCount = batchItems.length - batchSkillItems.length;
  const batchExistingCount = batchSkillItems.filter((item) => item.result?.exists).length;
  const batchBlockedCount = batchSkillItems.filter(isBatchItemBlocked).length;
  const batchNewCount = batchSkillItems.filter((item) => item.result && !item.result.exists).length;
  const batchDraftCount = batchSkillItems.filter((item) => item.result?.draftExists).length;
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
    : precheck?.draftExists
      ? t('skill.confirmForceOverwriteUpload')
      : t('skill.confirmUpload');
  const precheckMessageClass = precheck?.status === 'CONFLICT'
    || precheck?.status === 'FORBIDDEN'
    || precheck?.actions.length === 0
    ? 'text-destructive'
    : 'text-muted-foreground';
  const canSubmit = !!file && !loading && !error
    && (
      !!batchPrecheck
      || (!!precheck && precheck.actions.length > 0)
    );

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
                <span className="text-muted-foreground">{t('skill.parsedVersion')}</span>
                <span>{precheck.parsedVersion}</span>
                <span className="text-muted-foreground">{t('skill.resolvedVersion')}</span>
                <span>{precheckTargetVersion}</span>
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
                  const blocked = isBatchItemBlocked(item);
                  const uploadOnly = item.kind !== 'SKILL';
                  return (
                    <div
                      key={item.entryKey}
                      className="flex items-center justify-between gap-3 py-1"
                    >
                      <div className="min-w-0">
                        <p className="truncate font-medium">{getBatchItemName(item)}</p>
                        <p className="text-xs text-muted-foreground truncate">
                          {item.kind === 'INVALID_SKILL'
                            ? t('skill.batchItemInvalidSkillDesc')
                          : item.kind === 'NON_SKILL_FOLDER'
                            ? t('skill.batchItemNonSkillFolderDesc')
                            : item.result?.parsedVersion ?? item.request?.parsedVersion ?? item.entryKey}
                        </p>
                      </div>
                      <span className={`shrink-0 text-xs ${
                        blocked
                          ? 'text-destructive'
                          : uploadOnly
                            ? 'text-muted-foreground'
                          : item.result?.exists
                            ? 'text-amber-600'
                            : 'text-emerald-600'
                      }`}
                      >
                        {blocked
                          ? t('skill.batchItemBlocked')
                          : item.kind === 'INVALID_SKILL'
                            ? t('skill.batchItemInvalidSkill')
                          : item.kind === 'NON_SKILL_FOLDER'
                            ? t('skill.batchItemNonSkillFolder')
                          : item.result?.draftExists
                            ? t('skill.batchItemDraft')
                          : item.result?.exists
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
