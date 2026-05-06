import type { SkillResource } from './skill';

// ===== MCP Tool Selection =====

export interface SelectedMcpTool {
  name: string;
  description?: string;
  inputSchema?: Record<string, unknown>;
}

// ===== Conversation History =====

export interface ConversationMessage {
  type: 'user' | 'model';
  content: string;
}

export interface ConversationHistory {
  messages: ConversationMessage[];
  context?: string;
  title?: string;
}

// ===== Skill Generation =====

export interface SkillGenerationPayload {
  backgroundInfo: string;
  selectedMcpTools?: SelectedMcpTool[];
  conversationHistory?: ConversationHistory;
}

export interface GeneratedSkill {
  name: string;
  description: string;
  skillMd: string;
  resource: Record<string, SkillResource>;
}

export interface SkillGenerationResponse {
  type: string | { code?: string };
  chunk?: string;
  skill?: GeneratedSkill;
  explanation?: string;
  done?: boolean;
}

// ===== Skill Optimization =====

export interface SkillOptimizationPayload {
  skill: {
    name: string;
    description: string;
    skillMd: string;
    resource: Record<string, SkillResource>;
  };
  optimizationGoal?: string;
  selectedMcpTools?: SelectedMcpTool[];
  conversationHistory?: ConversationHistory;
  targetFileName?: string;
}

export interface SkillOptimizationResponse {
  type: string | { code?: string };
  chunk?: string;
  optimizedSkill?: GeneratedSkill;
  changes?: string[];
  qualityScore?: number;
  explanation?: string;
  done?: boolean;
}
