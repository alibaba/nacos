import { useState, useCallback, useRef, useMemo } from 'react';
import { useTranslation } from 'react-i18next';
import Markdown from 'react-markdown';
import remarkGfm from 'remark-gfm';
import remarkFrontmatter from 'remark-frontmatter';
import {
  Sparkles,
  Loader2,
  AlertCircle,
  Brain,
  ChevronDown,
  ChevronRight,
} from 'lucide-react';
import {
  Dialog,
  DialogContent,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select';
import {
  Collapsible,
  CollapsibleContent,
  CollapsibleTrigger,
} from '@/components/ui/collapsible';
import { McpToolSelector } from '@/components/ai/skill/McpToolSelector';
import {
  buildSSEUrl,
  startSSEStream,
  parseSkillFromContent,
  filterSkillMdFromResources,
} from '@/lib/sse-utils';
import type { SSEStreamHandle } from '@/lib/sse-utils';
import type { SkillDocument } from '@/types/skill';
import type {
  SelectedMcpTool,
  GeneratedSkill,
  SkillOptimizationResponse,
} from '@/types/skill-ai';

interface SkillOptimizeDialogProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  skill: SkillDocument;
  namespaceId: string;
  onApply: (optimizedSkill: SkillDocument) => void;
}

export function SkillOptimizeDialog({
  open,
  onOpenChange,
  skill,
  namespaceId,
  onApply,
}: SkillOptimizeDialogProps) {
  const { t } = useTranslation();

  const SKILL_MD_VALUE = 'SKILL.md';

  const fileOptions = useMemo(() => {
    const options = [{ value: SKILL_MD_VALUE, label: 'SKILL.md' }];
    if (skill.resource) {
      Object.entries(skill.resource).forEach(([key, res]) => {
        if (key === 'skill-md' || key === 'SKILL.md') return;
        options.push({ value: key, label: res.name || key });
      });
    }
    return options;
  }, [skill.resource]);

  const [targetFileName, setTargetFileName] = useState(SKILL_MD_VALUE);
  const [optimizationGoal, setOptimizationGoal] = useState('');
  const [selectedMcpTools, setSelectedMcpTools] = useState<SelectedMcpTool[]>([]);
  const [isOptimizing, setIsOptimizing] = useState(false);
  const [thinkingContent, setThinkingContent] = useState('');
  const [streamContent, setStreamContent] = useState('');
  const [optimizedSkill, setOptimizedSkill] = useState<GeneratedSkill | null>(null);
  const [optimizeError, setOptimizeError] = useState<string | null>(null);
  const [thinkingCollapsed, setThinkingCollapsed] = useState(false);

  const streamRef = useRef<SSEStreamHandle | null>(null);
  const optimizedPanelRef = useRef<HTMLDivElement>(null);
  const accumulatedContentRef = useRef('');

  const resetState = useCallback(() => {
    setTargetFileName(SKILL_MD_VALUE);
    setOptimizationGoal('');
    setSelectedMcpTools([]);
    setIsOptimizing(false);
    setThinkingContent('');
    setStreamContent('');
    setOptimizedSkill(null);
    setOptimizeError(null);
    setThinkingCollapsed(false);
    accumulatedContentRef.current = '';
    streamRef.current?.abort();
    streamRef.current = null;
  }, []);

  const handleClose = useCallback(
    (nextOpen: boolean) => {
      if (!nextOpen) {
        streamRef.current?.abort();
        resetState();
      }
      onOpenChange(nextOpen);
    },
    [onOpenChange, resetState],
  );

  const handleStartOptimize = useCallback(() => {
    setIsOptimizing(true);
    setThinkingContent('');
    setStreamContent('');
    setOptimizedSkill(null);
    setOptimizeError(null);
    setThinkingCollapsed(false);
    accumulatedContentRef.current = '';

    const url = buildSSEUrl('v3/console/copilot/skill/optimize');
    const payload: Record<string, unknown> = {
      skill: {
        name: skill.name,
        description: skill.description,
        skillMd: skill.skillMd,
        resource: skill.resource,
      },
      targetFileName,
    };
    if (optimizationGoal.trim()) {
      payload.optimizationGoal = optimizationGoal.trim();
    }
    if (selectedMcpTools.length > 0) {
      payload.selectedMcpTools = selectedMcpTools;
    }

    streamRef.current = startSSEStream<SkillOptimizationResponse>({
      url,
      payload,
      onThinking: (chunk) => {
        setThinkingContent((prev) => prev + chunk);
      },
      onContent: (chunk) => {
        accumulatedContentRef.current += chunk;
        setStreamContent((prev) => prev + chunk);
        setThinkingCollapsed(true);
        optimizedPanelRef.current?.scrollTo(
          0,
          optimizedPanelRef.current.scrollHeight,
        );
      },
      onToolCall: (chunk) => {
        accumulatedContentRef.current += chunk;
        setStreamContent((prev) => prev + chunk);
        setThinkingCollapsed(true);
      },
      onDone: (data) => {
        setIsOptimizing(false);

        let result = data.optimizedSkill || null;

        if (!result && accumulatedContentRef.current) {
          result = parseSkillFromContent<GeneratedSkill>(
            accumulatedContentRef.current,
            'optimizedSkill',
          );
        }

        if (result) {
          result = filterSkillMdFromResources(result);
          if (!result.skillMd && (result as unknown as { instruction?: string }).instruction) {
            result.skillMd = (result as unknown as { instruction: string }).instruction;
          }
          setOptimizedSkill(result);
        } else {
          setOptimizeError(data.explanation || t('skill.optimizeFailed'));
        }
      },
      onError: (err) => {
        setIsOptimizing(false);
        setOptimizeError(err);
      },
      onFinish: () => {
        setIsOptimizing(false);
      },
    });
  }, [skill, optimizationGoal, selectedMcpTools, targetFileName, t]);

  const handleApply = useCallback(() => {
    if (!optimizedSkill) return;
    onApply({
      ...skill,
      description: optimizedSkill.description || skill.description,
      skillMd: optimizedSkill.skillMd || skill.skillMd,
      resource: optimizedSkill.resource || skill.resource,
    });
    handleClose(false);
  }, [optimizedSkill, skill, onApply, handleClose]);

  return (
    <Dialog
      open={open}
      onOpenChange={(nextOpen) => {
        if (!isOptimizing) handleClose(nextOpen);
      }}
    >
      <DialogContent className="max-w-4xl max-h-[85vh] overflow-auto">
        <DialogHeader>
          <DialogTitle className="flex items-center gap-2">
            <Sparkles className="h-4 w-4 text-amber-500" />
            {t('skill.aiOptimize')}
          </DialogTitle>
        </DialogHeader>

        {/* Target file selector + Goal + Start button */}
        <div className="flex gap-2">
          <Select
            value={targetFileName}
            onValueChange={setTargetFileName}
            disabled={isOptimizing}
          >
            <SelectTrigger className="w-[180px] shrink-0">
              <SelectValue placeholder={t('skill.selectTargetFile')} />
            </SelectTrigger>
            <SelectContent>
              {fileOptions.map((opt) => (
                <SelectItem key={opt.value} value={opt.value}>
                  {opt.label}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
          <Input
            value={optimizationGoal}
            onChange={(e) => setOptimizationGoal(e.target.value)}
            placeholder={t('skill.optimizationGoalPlaceholder')}
            className="flex-1"
            disabled={isOptimizing}
          />
          <Button
            onClick={handleStartOptimize}
            disabled={isOptimizing}
            className="gap-1.5"
          >
            {isOptimizing ? (
              <Loader2 className="h-4 w-4 animate-spin" />
            ) : (
              <Sparkles className="h-4 w-4" />
            )}
            {isOptimizing ? t('skill.optimizing') : t('skill.startOptimize')}
          </Button>
        </div>

        {/* MCP Tool Selector */}
        <McpToolSelector
          namespaceId={namespaceId}
          selectedTools={selectedMcpTools}
          onSelectionChange={setSelectedMcpTools}
          disabled={isOptimizing}
        />

        {/* Error */}
        {optimizeError && (
          <div className="flex items-center gap-2 text-sm text-destructive bg-destructive/10 rounded-md px-3 py-2">
            <AlertCircle className="h-4 w-4 shrink-0" />
            {optimizeError}
          </div>
        )}

        {/* Thinking (collapsible) */}
        {thinkingContent && (
          <Collapsible
            open={!thinkingCollapsed}
            onOpenChange={(o) => setThinkingCollapsed(!o)}
          >
            <CollapsibleTrigger className="flex items-center gap-2 text-xs font-medium text-muted-foreground hover:text-foreground">
              {thinkingCollapsed ? (
                <ChevronRight className="h-3 w-3" />
              ) : (
                <ChevronDown className="h-3 w-3" />
              )}
              <Brain className="h-3 w-3" />
              {t('skill.thinking')}
              {isOptimizing && (
                <Loader2 className="h-3 w-3 animate-spin" />
              )}
            </CollapsibleTrigger>
            <CollapsibleContent className="mt-1.5">
              <div className="rounded-md border bg-muted/20 p-3 max-h-[150px] overflow-y-auto">
                <pre className="text-[11px] text-muted-foreground whitespace-pre-wrap break-words leading-relaxed">
                  {thinkingContent}
                </pre>
              </div>
            </CollapsibleContent>
          </Collapsible>
        )}

        {/* Side-by-side comparison */}
        <div className="grid grid-cols-2 gap-3">
          <div className="space-y-2">
            <h3 className="text-xs font-medium text-muted-foreground">
              {t('skill.originalContent')}
            </h3>
            <div className="app-markdown rounded-md border bg-muted/20 p-3 max-h-[400px] overflow-y-auto prose prose-sm dark:prose-invert max-w-none">
              <Markdown remarkPlugins={[remarkGfm, remarkFrontmatter]}>
                {targetFileName === SKILL_MD_VALUE
                    ? skill.skillMd || ''
                    : skill.resource?.[targetFileName]?.content || ''}
              </Markdown>
            </div>
          </div>
          <div className="space-y-2">
            <h3 className="text-xs font-medium text-muted-foreground flex items-center gap-1.5">
              {t('skill.optimizedContent')}
              {isOptimizing && <Loader2 className="h-3 w-3 animate-spin" />}
            </h3>
            <div
              ref={optimizedPanelRef}
              className="rounded-md border bg-muted/20 p-3 max-h-[400px] overflow-y-auto"
            >
              {optimizedSkill ? (
                <div className="app-markdown prose prose-sm dark:prose-invert max-w-none">
                  <Markdown remarkPlugins={[remarkGfm, remarkFrontmatter]}>
                    {optimizedSkill.skillMd || ''}
                  </Markdown>
                </div>
              ) : streamContent ? (
                <pre className="text-xs whitespace-pre-wrap break-words leading-relaxed">
                  {streamContent}
                </pre>
              ) : (
                <p className="text-xs text-muted-foreground/60 text-center py-8">
                  {t('skill.startOptimize')}...
                </p>
              )}
            </div>
          </div>
        </div>

        <DialogFooter>
          <Button
            variant="outline"
            onClick={() => {
              if (!isOptimizing) handleClose(false);
            }}
          >
            {t('common.cancel')}
          </Button>
          {optimizedSkill && !isOptimizing && (
            <Button onClick={handleApply} className="gap-1.5">
              <Sparkles className="h-3.5 w-3.5" />
              {t('skill.applyOptimize')}
            </Button>
          )}
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
