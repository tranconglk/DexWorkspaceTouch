export interface AdminOperation { method: string; path: string; body?: unknown }
export function parseCommand(commandName: string | undefined, values: string[]): AdminOperation;
export function readConfiguration(environment: Record<string, string | undefined>): { apiUrl: string; token: string };
export function formatError(status: number, payload: unknown, requestId: string | null, retryAfter?: string | null): string;
export function safeRetryAfter(value: unknown): string | null;
