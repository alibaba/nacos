import { useState, useCallback, useRef } from 'react';
import { useTranslation } from 'react-i18next';
import { toast } from 'sonner';
import MDEditor from '@uiw/react-md-editor';
import Markdown from 'react-markdown';
import remarkGfm from 'remark-gfm';
import remarkFrontmatter from 'remark-frontmatter';
import { useServerStore } from '@/stores/server-store';
import {
  Sparkles,
  Loader2,
  AlertCircle,
  Brain,
  ChevronDown,
  ChevronRight,
  MessageSquare,
  FileText,
  Wand2,
} from 'lucide-react';
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Textarea } from '@/components/ui/textarea';
import { Badge } from '@/components/ui/badge';
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs';
import {
  Collapsible,
  CollapsibleContent,
  CollapsibleTrigger,
} from '@/components/ui/collapsible';
import { skillApi } from '@/api/skill';
import { McpToolSelector } from '@/components/ai/skill/McpToolSelector';
import {
  buildSSEUrl,
  startSSEStream,
  parseSkillFromContent,
  filterSkillMdFromResources,
} from '@/lib/sse-utils';
import { hasNonFrontmatterMarkdownBody } from '@/lib/markdown-utils';
import type { SSEStreamHandle } from '@/lib/sse-utils';
import type {
  SelectedMcpTool,
  ConversationHistory,
  GeneratedSkill,
  SkillGenerationResponse,
} from '@/types/skill-ai';

const FRONTMATTER_REGEX = /^---\r?\n([\s\S]*?)\r?\n---(\r?\n|$)/;
const SKILL_NAME_PATTERN = /^[a-z0-9]([a-z0-9-]*[a-z0-9])?$/;
const SKILL_NAME_MAX_LENGTH = 64;

function toYamlQuotedValue(value: string): string {
  return `"${value.replace(/\\/g, '\\\\').replace(/"/g, '\\"')}"`;
}

function syncSkillMdFrontmatter(skillMd: string, name: string, description: string): string {
  const content = skillMd || '';
  const match = content.match(FRONTMATTER_REGEX);
  const normalizedName = name.trim();
  const normalizedDescription = description.trim();
  const descriptionLine = `description: ${toYamlQuotedValue(normalizedDescription)}`;

  if (!match) {
    const body = content.trim().length > 0 ? `\n${content.replace(/^\r?\n+/, '')}` : '';
    return `---\nname: ${normalizedName}\n${descriptionLine}\n---${body}`;
  }

  const frontmatterLines = match[1]
    .split(/\r?\n/)
    .filter((line) => !/^\s*(name|description)\s*:/.test(line));
  const body = content.slice(match[0].length);
  const rebuiltFrontmatter = [
    '---',
    `name: ${normalizedName}`,
    descriptionLine,
    ...frontmatterLines,
    '---',
  ].join('\n');

  return `${rebuiltFrontmatter}${body ? (body.startsWith('\n') ? '' : '\n') + body : '\n'}`;
}

interface CreateSkillDialogProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  namespaceId: string;
  onSuccess: (skillName: string) => void;
}

export function CreateSkillDialog({
  open,
  onOpenChange,
  namespaceId,
  onSuccess,
}: CreateSkillDialogProps) {
  const { t } = useTranslation();
  const copilotEnabled = useServerStore((s) => s.copilotEnabled);

  // Manual tab state
  const [skillName, setSkillName] = useState('');
  const [description, setDescription] = useState('');
  const [createCommitMsg, setCreateCommitMsg] = useState('');
  const [instruction, setInstruction] = useState('');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  // AI generate tab state
  const [backgroundInfo, setBackgroundInfo] = useState('');
  const [selectedMcpTools, setSelectedMcpTools] = useState<SelectedMcpTool[]>([]);
  const [conversationHistory, setConversationHistory] = useState<ConversationHistory | null>(null);
  const [conversationHistoryJson, setConversationHistoryJson] = useState('');
  const [showConversationHistory, setShowConversationHistory] = useState(false);
  const [isGenerating, setIsGenerating] = useState(false);
  const [thinkingContent, setThinkingContent] = useState('');
  const [streamContent, setStreamContent] = useState('');
  const [generatedSkill, setGeneratedSkill] = useState<GeneratedSkill | null>(null);
  const [generateError, setGenerateError] = useState<string | null>(null);
  const [thinkingCollapsed, setThinkingCollapsed] = useState(false);
  const streamRef = useRef<SSEStreamHandle | null>(null);
  const contentRef = useRef<HTMLDivElement>(null);

  // Track accumulated content for parsing
  const accumulatedContentRef = useRef('');

  const reset = useCallback(() => {
    setSkillName('');
    setDescription('');
    setCreateCommitMsg('');
    setInstruction('');
    setError(null);
    setLoading(false);
    // Reset AI state
    setBackgroundInfo('');
    setSelectedMcpTools([]);
    setConversationHistory(null);
    setConversationHistoryJson('');
    setShowConversationHistory(false);
    setIsGenerating(false);
    setThinkingContent('');
    setStreamContent('');
    setGeneratedSkill(null);
    setGenerateError(null);
    setThinkingCollapsed(false);
    accumulatedContentRef.current = '';
    streamRef.current?.abort();
    streamRef.current = null;
  }, []);

  const handleClose = useCallback(
    (nextOpen: boolean) => {
      if (!nextOpen) {
        streamRef.current?.abort();
        reset();
      }
      onOpenChange(nextOpen);
    },
    [onOpenChange, reset],
  );

  // ===== Skill name validation (consistent with backend DefaultParamChecker) =====
  const validateSkillName = useCallback(
    (name: string): string | null => {
      if (!name) {
        return t('skill.nameRequired');
      }
      if (name.length > SKILL_NAME_MAX_LENGTH) {
        return t('skill.nameTooLong');
      }
      if (!SKILL_NAME_PATTERN.test(name)) {
        return t('skill.nameInvalidFormat');
      }
      if (name.includes('--')) {
        return t('skill.nameNoConsecutiveHyphens');
      }
      return null;
    },
    [t],
  );

  // ===== Manual Create =====
  const handleCreate = useCallback(async () => {
    const trimmedName = skillName.trim();
    const nameError = validateSkillName(trimmedName);
    if (nameError) {
      setError(nameError);
      return;
    }
    if (!description.trim()) {
      setError(t('skill.descriptionRequired'));
      return;
    }
    if (!hasNonFrontmatterMarkdownBody(instruction)) {
      setError(t('skill.instructionRequired'));
      return;
    }
    setLoading(true);
    setError(null);
    try {
      const skillCard = JSON.stringify({
        name: trimmedName,
        description: description.trim(),
        skillMd: instruction.trim(),
        resource: {},
      });
      await skillApi.createDraft({
        namespaceId,
        skillCard,
        commitMsg: createCommitMsg.trim() || undefined,
      });
      toast.success(t('skill.createSuccess'));
      handleClose(false);
      onSuccess(trimmedName);
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : t('skill.createFailed');
      setError(msg);
    } finally {
      setLoading(false);
    }
  }, [skillName, description, createCommitMsg, instruction, namespaceId, t, handleClose, onSuccess, validateSkillName]);

  // ===== AI Generate =====
  const handleGenerate = useCallback(() => {
    if (!backgroundInfo.trim()) {
      setGenerateError(t('skill.backgroundInfoRequired'));
      return;
    }

    // Parse conversation history JSON if provided
    let history = conversationHistory;
    if (conversationHistoryJson.trim()) {
      try {
        history = JSON.parse(conversationHistoryJson);
      } catch {
        setGenerateError(t('skill.conversationHistoryInvalid'));
        return;
      }
    }

    setIsGenerating(true);
    setThinkingContent('');
    setStreamContent('');
    setGeneratedSkill(null);
    setGenerateError(null);
    setThinkingCollapsed(false);
    accumulatedContentRef.current = '';

    const url = buildSSEUrl('v3/console/copilot/skill/generate');
    const payload: Record<string, unknown> = {
      backgroundInfo: backgroundInfo.trim(),
    };
    if (selectedMcpTools.length > 0) {
      payload.selectedMcpTools = selectedMcpTools;
    }
    if (history) {
      payload.conversationHistory = history;
    }

    streamRef.current = startSSEStream<SkillGenerationResponse>({
      url,
      payload,
      onThinking: (chunk) => {
        setThinkingContent((prev) => prev + chunk);
      },
      onContent: (chunk) => {
        accumulatedContentRef.current += chunk;
        setStreamContent((prev) => prev + chunk);
        setThinkingCollapsed(true);
        contentRef.current?.scrollTo(0, contentRef.current.scrollHeight);
      },
      onToolCall: (chunk) => {
        accumulatedContentRef.current += chunk;
        setStreamContent((prev) => prev + chunk);
        setThinkingCollapsed(true);
      },
      onDone: (data) => {
        setIsGenerating(false);

        // Try to get skill from DONE event first
        let skill = data.skill || null;

        // Fallback: parse from accumulated content
        if (!skill && accumulatedContentRef.current) {
          skill = parseSkillFromContent<GeneratedSkill>(
            accumulatedContentRef.current,
            'skill',
          );
        }

        if (skill) {
          skill = filterSkillMdFromResources(skill);
          if (!skill.skillMd && (skill as unknown as { instruction?: string }).instruction) {
            skill.skillMd = (skill as unknown as { instruction: string }).instruction;
          }
          setGeneratedSkill(skill);

          // Update conversation history for multi-round
          setConversationHistory((prev) => ({
            messages: [
              ...(prev?.messages || []),
              { type: 'user' as const, content: backgroundInfo },
              { type: 'model' as const, content: accumulatedContentRef.current },
            ],
          }));
        } else {
          setGenerateError(data.explanation || t('skill.generateFailed'));
        }
      },
      onError: (err) => {
        setIsGenerating(false);
        setGenerateError(err);
      },
      onFinish: () => {
        setIsGenerating(false);
      },
    });
  }, [backgroundInfo, selectedMcpTools, conversationHistory, conversationHistoryJson, t]);

  // Apply generated skill → create draft
  const handleApplyGenerated = useCallback(async () => {
    if (!generatedSkill) return;

    const name = generatedSkill.name?.trim();
    if (!name) {
      setGenerateError(t('skill.nameRequired'));
      return;
    }
    if (!hasNonFrontmatterMarkdownBody(generatedSkill.skillMd || '')) {
      setGenerateError(t('skill.instructionRequired'));
      return;
    }

    setLoading(true);
    setGenerateError(null);
    try {
      const skillCard = JSON.stringify({
        name,
        description: generatedSkill.description || '',
        skillMd: generatedSkill.skillMd || '',
        resource: generatedSkill.resource || {},
      });
      await skillApi.createDraft({
        namespaceId,
        skillCard,
        commitMsg: createCommitMsg.trim() || undefined,
      });

      toast.success(t('skill.generateSuccess'));
      handleClose(false);
      onSuccess(name);
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : t('skill.createFailed');
      setGenerateError(msg);
    } finally {
      setLoading(false);
    }
  }, [generatedSkill, namespaceId, createCommitMsg, t, handleClose, onSuccess]);

  return (
    <Dialog open={open} onOpenChange={handleClose}>
      <DialogContent className="max-w-3xl max-h-[85vh] overflow-auto">
        <DialogHeader>
          <DialogTitle>{t('skill.createSkill')}</DialogTitle>
          <DialogDescription>{t('skill.createSkillDesc')}</DialogDescription>
        </DialogHeader>

        <Tabs defaultValue="manual" className="w-full">
          <TabsList className="w-full">
            <TabsTrigger value="manual" className="flex-1 gap-1.5">
              <FileText className="h-3.5 w-3.5" />
              {t('skill.manualCreate')}
            </TabsTrigger>
            {copilotEnabled && (
              <TabsTrigger value="ai" className="flex-1 gap-1.5">
                <Sparkles className="h-3.5 w-3.5" />
                {t('skill.aiGenerate')}
              </TabsTrigger>
            )}
          </TabsList>

          {/* ===== Manual Tab ===== */}
          <TabsContent value="manual">
            <div className="space-y-4 pt-2">
              <div className="space-y-2">
                <Label htmlFor="skill-name">{t('skill.skillName')} *</Label>
                <Input
                  id="skill-name"
                  placeholder={t('skill.namePlaceholder')}
                  value={skillName}
                  maxLength={SKILL_NAME_MAX_LENGTH}
                  onChange={(e) => {
                    const nextName = e.target.value;
                    setSkillName(nextName);
                    setInstruction((prev) =>
                      syncSkillMdFrontmatter(prev, nextName, description),
                    );
                    const nameError = validateSkillName(nextName.trim());
                    setError(nameError);
                  }}
                  onKeyDown={(e) => e.key === 'Enter' && handleCreate()}
                />
              </div>

              <div className="space-y-2">
                <Label htmlFor="skill-desc">{t('skill.description')} *</Label>
                <Input
                  id="skill-desc"
                  placeholder={t('skill.descPlaceholder')}
                  value={description}
                  onChange={(e) => {
                    const nextDescription = e.target.value;
                    setDescription(nextDescription);
                    setInstruction((prev) =>
                      syncSkillMdFrontmatter(prev, skillName, nextDescription),
                    );
                    setError(null);
                  }}
                />
              </div>

              <div className="space-y-2">
                <Label htmlFor="skill-create-commit-msg">{t('skill.commitMsg')}</Label>
                <Textarea
                  id="skill-create-commit-msg"
                  value={createCommitMsg}
                  onChange={(e) => {
                    setCreateCommitMsg(e.target.value);
                    setError(null);
                  }}
                  placeholder={t('skill.commitMsgPlaceholder')}
                  rows={2}
                  className="text-sm resize-y"
                />
                <p className="text-xs text-muted-foreground">{t('skill.commitMsgHint')}</p>
              </div>

              <div className="space-y-2">
                <Label>{t('skill.instruction')} *</Label>
                <p className="text-xs text-muted-foreground">
                  {t('skill.skillMdHint')}
                </p>
                <div data-color-mode="light" className="dark:hidden">
                  <MDEditor
                    value={instruction}
                    onChange={(val) => setInstruction(val || '')}
                    height={200}
                    preview="live"
                    textareaProps={{ placeholder: t('skill.skillMdPlaceholder') }}
                    previewOptions={{ remarkPlugins: [remarkGfm, remarkFrontmatter] }}
                  />
                </div>
                <div data-color-mode="dark" className="hidden dark:block">
                  <MDEditor
                    value={instruction}
                    onChange={(val) => setInstruction(val || '')}
                    height={200}
                    preview="live"
                    textareaProps={{ placeholder: t('skill.skillMdPlaceholder') }}
                    previewOptions={{ remarkPlugins: [remarkGfm, remarkFrontmatter] }}
                  />
                </div>
              </div>

              {error && <p className="text-sm text-destructive">{error}</p>}

              <DialogFooter>
                <Button
                  variant="outline"
                  onClick={() => handleClose(false)}
                  disabled={loading}
                >
                  {t('common.cancel')}
                </Button>
                <Button
                  onClick={handleCreate}
                  disabled={
                    !!validateSkillName(skillName.trim()) ||
                    !description.trim() ||
                    !hasNonFrontmatterMarkdownBody(instruction) ||
                    loading
                  }
                >
                  {loading ? t('common.loading') : t('skill.createSkill')}
                </Button>
              </DialogFooter>
            </div>
          </TabsContent>

          {/* ===== AI Generate Tab ===== */}
          <TabsContent value="ai">
            <div className="space-y-4 pt-2">
              {/* Background info */}
              <div className="space-y-2">
                <Label>{t('skill.backgroundInfo')}</Label>
                <Textarea
                  placeholder={t('skill.backgroundInfoPlaceholder')}
                  value={backgroundInfo}
                  onChange={(e) => {
                    setBackgroundInfo(e.target.value);
                    setGenerateError(null);
                  }}
                  rows={4}
                  disabled={isGenerating}
                />
              </div>

              {/* MCP Tool Selector */}
              <McpToolSelector
                namespaceId={namespaceId}
                selectedTools={selectedMcpTools}
                onSelectionChange={setSelectedMcpTools}
                disabled={isGenerating}
              />

              {/* Conversation History */}
              <Collapsible
                open={showConversationHistory}
                onOpenChange={setShowConversationHistory}
              >
                <CollapsibleTrigger className="flex items-center gap-2 text-sm font-medium text-muted-foreground hover:text-foreground transition-colors py-1">
                  {showConversationHistory ? (
                    <ChevronDown className="h-3.5 w-3.5" />
                  ) : (
                    <ChevronRight className="h-3.5 w-3.5" />
                  )}
                  <MessageSquare className="h-3.5 w-3.5" />
                  {t('skill.conversationHistory')}
                  {conversationHistory && (
                    <Badge
                      variant="secondary"
                      className="text-[10px] px-1.5 py-0 h-4 ml-1"
                    >
                      {conversationHistory.messages.length}
                    </Badge>
                  )}
                </CollapsibleTrigger>
                <CollapsibleContent className="mt-2 pl-5">
                  <Textarea
                    placeholder={t('skill.conversationHistoryPlaceholder')}
                    value={conversationHistoryJson}
                    onChange={(e) => setConversationHistoryJson(e.target.value)}
                    rows={3}
                    className="font-mono text-xs"
                    disabled={isGenerating}
                  />
                </CollapsibleContent>
              </Collapsible>

              {/* Generate button */}
              <Button
                onClick={handleGenerate}
                disabled={!backgroundInfo.trim() || isGenerating}
                className="w-full gap-2"
              >
                {isGenerating ? (
                  <Loader2 className="h-4 w-4 animate-spin" />
                ) : (
                  <Sparkles className="h-4 w-4" />
                )}
                {isGenerating
                  ? t('skill.generating')
                  : generatedSkill
                    ? t('skill.regenerate')
                    : t('skill.generateSkill')}
              </Button>

              {/* Error */}
              {generateError && (
                <div className="flex items-center gap-2 text-sm text-destructive bg-destructive/10 rounded-md px-3 py-2">
                  <AlertCircle className="h-4 w-4 shrink-0" />
                  {generateError}
                </div>
              )}

              {/* Streaming display */}
              {(thinkingContent || streamContent) && (
                <div className="space-y-3">
                  {/* Thinking */}
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
                        {isGenerating && (
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

                  {/* Content stream */}
                  {streamContent && (
                    <div className="space-y-1.5">
                      <div className="flex items-center gap-2 text-xs font-medium text-muted-foreground">
                        <Wand2 className="h-3 w-3" />
                        {t('skill.generatedContent')}
                        {isGenerating && (
                          <Loader2 className="h-3 w-3 animate-spin" />
                        )}
                      </div>
                      <div
                        ref={contentRef}
                        className="rounded-md border bg-muted/20 p-3 max-h-[250px] overflow-y-auto"
                      >
                        <pre className="text-xs whitespace-pre-wrap break-words leading-relaxed">
                          {streamContent}
                        </pre>
                      </div>
                    </div>
                  )}
                </div>
              )}

              {/* Generated Skill Preview */}
              {generatedSkill && !isGenerating && (
                <div className="space-y-3 rounded-lg border bg-card p-4">
                  <h3 className="text-sm font-semibold flex items-center gap-2">
                    <Sparkles className="h-4 w-4 text-amber-500" />
                    {t('skill.generateSuccess')}
                  </h3>

                  <div className="space-y-2">
                    <div>
                      <span className="text-xs text-muted-foreground">
                        {t('skill.skillName')}:
                      </span>
                      <span className="text-sm font-medium ml-2">
                        {generatedSkill.name}
                      </span>
                    </div>
                    {generatedSkill.description && (
                      <div>
                        <span className="text-xs text-muted-foreground">
                          {t('skill.description')}:
                        </span>
                        <span className="text-sm ml-2">
                          {generatedSkill.description}
                        </span>
                      </div>
                    )}
                    {generatedSkill.skillMd && (
                      <div>
                        <span className="text-xs text-muted-foreground">
                          {t('skill.instruction')}:
                        </span>
                        <div className="app-markdown mt-1 rounded-md border bg-muted/20 p-3 max-h-[200px] overflow-y-auto prose prose-sm dark:prose-invert max-w-none">
                          <Markdown remarkPlugins={[remarkGfm, remarkFrontmatter]}>
                            {generatedSkill.skillMd}
                          </Markdown>
                        </div>
                      </div>
                    )}
                    {generatedSkill.resource &&
                      Object.keys(generatedSkill.resource).length > 0 && (
                        <div>
                          <span className="text-xs text-muted-foreground">
                            {t('skill.resources')}:
                          </span>
                          <div className="flex flex-wrap gap-1.5 mt-1">
                            {Object.entries(generatedSkill.resource).map(
                              ([key, res]) => (
                                <Badge
                                  key={key}
                                  variant="secondary"
                                  className="text-[10px]"
                                >
                                  {res.name || key}
                                </Badge>
                              ),
                            )}
                          </div>
                        </div>
                      )}
                  </div>

                  <div className="space-y-2 pt-1">
                    <Label htmlFor="skill-ai-create-commit-msg">{t('skill.commitMsg')}</Label>
                    <Textarea
                      id="skill-ai-create-commit-msg"
                      value={createCommitMsg}
                      onChange={(e) => setCreateCommitMsg(e.target.value)}
                      placeholder={t('skill.commitMsgPlaceholder')}
                      rows={2}
                      className="text-sm resize-y"
                    />
                    <p className="text-xs text-muted-foreground">{t('skill.commitMsgHint')}</p>
                  </div>

                  <div className="flex gap-2 pt-2">
                    <Button
                      onClick={handleApplyGenerated}
                      disabled={loading}
                      className="gap-1.5"
                    >
                      {loading ? (
                        <Loader2 className="h-3.5 w-3.5 animate-spin" />
                      ) : (
                        <Sparkles className="h-3.5 w-3.5" />
                      )}
                      {loading
                        ? t('common.loading')
                        : t('skill.applyGenerated')}
                    </Button>
                    <Button
                      variant="outline"
                      onClick={() => handleClose(false)}
                      disabled={loading}
                    >
                      {t('common.cancel')}
                    </Button>
                  </div>
                </div>
              )}
            </div>
          </TabsContent>
        </Tabs>
      </DialogContent>
    </Dialog>
  );
}
