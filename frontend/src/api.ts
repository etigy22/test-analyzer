export type Finding = { id: string; category: string; severity: string; ruleId: string; title: string; explanation: string; remediation: string; tool: string; toolVersion: string; language: string; path: string; line: number; fingerprint: string; snippet: { startLine: number; endLine: number; text: string; truncated: boolean } | null };
export type Coverage = { language: string | null; tool: string; check: string; status: string; reason: string; version: string | null };
export type Result = { findings: Finding[]; coverage: Coverage[]; languages: { name: string; files: number; bytes: number }[]; archiveSha256: string; archiveBytes: number; rulesVersion: string; malwareVersion: string; truncated: boolean; durationMillis: number };
export type Scan = { id: string; sourceType: string; source: string; status: string; createdAt: string; updatedAt: string; completedAt: string | null; failureCode: string | null; failureMessage: string | null; result: Result | null };
export type Ready = { ready: boolean; version: string | null; updatedAt: string | null; message: string };
export type Settings = { limits: { maxArchiveBytes: number; maxExpandedBytes: number; maxSingleFileBytes: number; maxFiles: number; maxZipDepth: number; maxCompressionRatio: number; downloadTimeout: string; scannerTimeout: string }; maxParallelScans: number; maxFindings: number; snippetLines: number; maxTextBytes: number; historyDays: number; malware: Ready; lastUpdate: { status: string; message?: string; started_at?: string; completed_at?: string }; rulesVersion: string; advisoriesVersion: string };
export type Comparison = { added: Finding[]; resolved: Finding[]; unchanged: Finding[]; limited: boolean };
export class ApiError extends Error { constructor(public code: string, message: string) { super(message); } }
export async function api<T>(path: string, options: RequestInit = {}): Promise<T> {
  const response = await fetch(`/api${path}`, { ...options, headers: { 'X-RepoGuard-Request': 'true', ...options.headers } });
  if (!response.ok) {
    const error = await response.json().catch(() => ({}));
    throw new ApiError(error.code || error.error || `HTTP_${response.status}`, error.message || `HTTP ${response.status}`);
  }
  if (response.status === 204) return undefined as T;
  return response.json() as Promise<T>;
}
export function upload(file: File, progress: (percent: number) => void): Promise<Scan> {
  return new Promise((resolve, reject) => {
    const request = new XMLHttpRequest(); request.open('POST', '/api/scans/zip'); request.timeout = 150000;
    request.setRequestHeader('X-RepoGuard-Request', 'true');
    request.upload.onprogress = event => { if (event.lengthComputable) progress(Math.round(event.loaded / event.total * 100)); };
    request.onload = () => {
      let body; try { body = JSON.parse(request.responseText); } catch { reject(new ApiError('INVALID_RESPONSE', 'Invalid response.')); return; }
      if (request.status >= 200 && request.status < 300) resolve(body); else reject(new ApiError(body.code || 'UPLOAD_FAILED', body.message || 'Upload failed.'));
    };
    request.onerror = () => reject(new ApiError('NETWORK_ERROR', 'Connection failed.'));
    request.ontimeout = () => reject(new ApiError('UPLOAD_TIMEOUT', 'Upload timed out.'));
    const data = new FormData(); data.append('file', file); request.send(data);
  });
}
export const terminal = (scan: Scan) => ['COMPLETED', 'PARTIAL', 'BLOCKED', 'FAILED'].includes(scan.status);
export const bytes = (value: number) => value < 1024 * 1024 ? `${Math.round(value / 1024)} KB` : `${Math.round(value / 1024 / 1024)} MB`;
