/**
 * SSE (Server-Sent Events) streaming utilities.
 *
 * Uses fetch + ReadableStream to handle POST-based SSE endpoints
 * that return streaming AI responses (thinking, tool_call, content, done).
 */

/** Get accessToken from localStorage */
export function getAccessToken(): string {
  try {
    const tokenStr = localStorage.getItem('token');
    if (tokenStr) {
      const tokenData = JSON.parse(tokenStr);
      return tokenData.accessToken || '';
    }
  } catch {
    /* ignore */
  }
  return '';
}

/** Compute context path from current URL */
export function getContextPath(): string {
  return (
    window.location.pathname.replace(/\/(next|legacy)(\/.*)?$/, '/') || '/'
  );
}

/** Build full URL for an SSE endpoint path */
export function buildSSEUrl(path: string): string {
  const ctxPath = getContextPath();
  return `${window.location.origin}${ctxPath}${path}`;
}

export interface SSEStreamOptions<T = unknown> {
  /** Full URL or relative path (will be prefixed with context path) */
  url: string;
  /** POST body payload */
  payload: Record<string, unknown>;
  /** Called with each THINKING chunk */
  onThinking?: (chunk: string) => void;
  /** Called with each CONTENT chunk */
  onContent?: (chunk: string) => void;
  /** Called with each TOOL_CALL chunk */
  onToolCall?: (chunk: string) => void;
  /** Called when stream is DONE with the final parsed data */
  onDone?: (data: T) => void;
  /** Called on error */
  onError?: (error: string) => void;
  /** Called when streaming ends (success or error) */
  onFinish?: () => void;
}

export interface SSEStreamHandle {
  /** Abort the stream */
  abort: () => void;
}

/**
 * Start an SSE stream via POST request.
 *
 * Returns a handle with an abort() method to cancel the stream.
 */
export function startSSEStream<T = unknown>(
  options: SSEStreamOptions<T>,
): SSEStreamHandle {
  const {
    url,
    payload,
    onThinking,
    onContent,
    onToolCall,
    onDone,
    onError,
    onFinish,
  } = options;

  const controller = new AbortController();
  const token = getAccessToken();

  fetch(url, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      Accept: 'text/event-stream',
      ...(token
        ? { Authorization: `Bearer ${token}`, AccessToken: token }
        : {}),
    },
    body: JSON.stringify(payload),
    signal: controller.signal,
  })
    .then((response) => {
      if (!response.ok) {
        throw new Error(`HTTP ${response.status}`);
      }

      const reader = response.body!.getReader();
      const decoder = new TextDecoder();
      let buffer = '';
      let currentEventType = 'message';
      let pendingData: T | null = null;

      const read = (): Promise<void> =>
        reader.read().then(({ done, value }) => {
          if (done) {
            onFinish?.();
            return;
          }

          buffer += decoder.decode(value, { stream: true });
          const lines = buffer.split('\n');
          buffer = lines.pop() || '';

          lines.forEach((line) => {
            if (line.startsWith('event:')) {
              const eventType = line.substring(6).trim();
              currentEventType = eventType;

              // Handle error event with pending data
              if (eventType === 'error' && pendingData) {
                const errorData = pendingData as Record<string, unknown>;
                const errorMessage =
                  (errorData.explanation as string) ||
                  (errorData.message as string) ||
                  'Stream error';
                onError?.(errorMessage);
                onFinish?.();
                pendingData = null;
              }
            } else if (line.startsWith('data:')) {
              const dataStr = line.substring(5).trim();
              if (!dataStr) return;

              try {
                const data = JSON.parse(dataStr) as T & {
                  type?: string | { code?: string };
                  chunk?: string;
                  done?: boolean;
                  explanation?: string;
                };

                // Handle error event type
                if (currentEventType === 'error') {
                  const errorMessage =
                    data.explanation ||
                    (data as Record<string, unknown>).message as string ||
                    'Stream error';
                  onError?.(errorMessage);
                  onFinish?.();
                  return;
                }

                // Store as pending data for potential error event
                pendingData = data as T;

                const typeStr =
                  (typeof data.type === 'object'
                    ? data.type?.code
                    : data.type) || 'CONTENT';

                if (typeStr === 'THINKING') {
                  onThinking?.(data.chunk || '');
                } else if (typeStr === 'TOOL_CALL') {
                  onToolCall?.(data.chunk || '');
                } else if (typeStr === 'CONTENT') {
                  onContent?.(data.chunk || '');
                } else if (typeStr === 'DONE' || data.done) {
                  onDone?.(data as T);
                  onFinish?.();
                  return;
                }
              } catch {
                /* ignore JSON parse errors for incomplete chunks */
              }
            }
          });

          return read();
        });

      return read();
    })
    .catch((err: Error) => {
      if (err.name === 'AbortError') {
        // User cancelled — no error callback
        onFinish?.();
        return;
      }
      onError?.(err.message || 'Request failed');
      onFinish?.();
    });

  return {
    abort: () => controller.abort(),
  };
}

/**
 * Parse a Skill JSON object from accumulated stream content.
 *
 * Tries multiple strategies:
 * 1. Direct JSON.parse
 * 2. Extract from ```json ... ``` code block
 * 3. Extract from ``` ... ``` code block
 * 4. Regex match for top-level JSON object
 */
export function parseSkillFromContent<T = Record<string, unknown>>(
  content: string,
  resultKey: 'skill' | 'optimizedSkill' = 'skill',
): T | null {
  if (!content?.trim()) return null;

  let jsonContent = content.trim();

  // Strategy 1: try direct parse
  try {
    const parsed = JSON.parse(jsonContent);
    return (parsed[resultKey] || parsed) as T;
  } catch {
    /* continue */
  }

  // Strategy 2: extract from ```json ... ```
  if (jsonContent.includes('```json')) {
    const start = jsonContent.indexOf('```json') + 7;
    const end = jsonContent.indexOf('```', start);
    if (end > start) {
      jsonContent = jsonContent.substring(start, end).trim();
      try {
        const parsed = JSON.parse(jsonContent);
        return (parsed[resultKey] || parsed) as T;
      } catch {
        /* continue */
      }
    }
  }

  // Strategy 3: extract from ``` ... ```
  if (jsonContent.includes('```')) {
    const start = jsonContent.indexOf('```') + 3;
    const end = jsonContent.indexOf('```', start);
    if (end > start) {
      jsonContent = jsonContent.substring(start, end).trim();
      try {
        const parsed = JSON.parse(jsonContent);
        return (parsed[resultKey] || parsed) as T;
      } catch {
        /* continue */
      }
    }
  }

  // Strategy 4: regex match for JSON object
  const match = content.match(/\{[\s\S]*\}/);
  if (match) {
    try {
      const parsed = JSON.parse(match[0]);
      return (parsed[resultKey] || parsed) as T;
    } catch {
      /* give up */
    }
  }

  return null;
}

/**
 * Filter SKILL.md entries from a skill's resource map.
 */
export function filterSkillMdFromResources<
  T extends { resource?: Record<string, { name?: string }> },
>(skill: T): T {
  if (!skill?.resource) return skill;

  const filtered: Record<string, { name?: string }> = {};
  for (const [key, res] of Object.entries(skill.resource)) {
    const resourceName = res?.name || '';
    if (
      resourceName.toUpperCase() === 'SKILL.MD' ||
      key.toUpperCase() === 'SKILL.MD' ||
      resourceName.toUpperCase().includes('SKILL.MD') ||
      key.toUpperCase().includes('SKILL.MD')
    ) {
      continue;
    }
    filtered[key] = res;
  }

  return { ...skill, resource: filtered };
}
