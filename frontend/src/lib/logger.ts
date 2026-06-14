/**
 * Lightweight logger wrapper. In production, silences debug/info. Errors are always logged.
 * If NEXT_PUBLIC_SENTRY_DSN is set, errors are also reported to Sentry via a minimal
 * fetch-based client — no SDK dependency. No-op if DSN is empty.
 */

const isDev = process.env.NODE_ENV !== 'production';
const DSN = process.env.NEXT_PUBLIC_SENTRY_DSN || '';
const ENVIRONMENT = process.env.NEXT_PUBLIC_ENVIRONMENT || (isDev ? 'dev' : 'prod');

type SentryTarget = { storeUrl: string; publicKey: string };

function parseDsn(dsn: string): SentryTarget | null {
  if (!dsn) return null;
  try {
    const url = new URL(dsn);
    const publicKey = url.username;
    const projectId = url.pathname.replace(/^\/+/, '');
    if (!publicKey || !projectId) return null;
    const storeUrl = `${url.protocol}//${url.host}/api/${projectId}/store/`;
    return { storeUrl, publicKey };
  } catch {
    return null;
  }
}

const target = parseDsn(DSN);

async function captureToSentry(err: unknown, extra?: Record<string, string>) {
  if (!target || typeof window === 'undefined') return;
  try {
    const error = err instanceof Error ? err : new Error(String(err));
    const eventId = crypto.randomUUID().replace(/-/g, '');
    const payload = {
      event_id: eventId,
      timestamp: new Date().toISOString(),
      platform: 'javascript',
      level: 'error',
      environment: ENVIRONMENT,
      logger: 'browser',
      message: error.message,
      tags: { ...(extra || {}) },
      exception: {
        values: [{
          type: error.name || 'Error',
          value: error.message,
          stacktrace: { frames: parseStack(error.stack) },
        }],
      },
      request: {
        url: window.location.href,
        headers: { 'User-Agent': navigator.userAgent },
      },
    };
    const auth = [
      'Sentry sentry_version=7',
      `sentry_timestamp=${Math.floor(Date.now() / 1000)}`,
      `sentry_key=${target.publicKey}`,
      'sentry_client=rowingclub-manual/1.0',
    ].join(',');
    // Fire-and-forget; do not await (and do not block the caller on network)
    void fetch(target.storeUrl, {
      method: 'POST',
      keepalive: true,
      headers: { 'Content-Type': 'application/json', 'X-Sentry-Auth': auth },
      body: JSON.stringify(payload),
    }).catch(() => { /* telemetry must never throw */ });
  } catch {
    /* never throw from logger */
  }
}

function parseStack(stack?: string): Array<Record<string, unknown>> {
  if (!stack) return [];
  const lines = stack.split('\n').slice(1, 51); // cap depth at 50
  const frames = lines.map((raw) => {
    const m = raw.match(/at\s+(?:(.+?)\s+\()?(.+?):(\d+):(\d+)\)?$/);
    if (!m) return { function: raw.trim() };
    return {
      function: m[1] || '?',
      filename: m[2],
      lineno: Number(m[3]),
      colno: Number(m[4]),
    };
  });
  return frames.reverse();
}

export const logger = {
  debug(...args: unknown[]) {
    if (isDev) console.debug(...args);
  },
  info(...args: unknown[]) {
    if (isDev) console.info(...args);
  },
  warn(...args: unknown[]) {
    if (isDev) console.warn(...args);
  },
  error(...args: unknown[]) {
    console.error(...args);
    const errArg = args.find((a) => a instanceof Error) as Error | undefined;
    if (errArg) void captureToSentry(errArg);
  },
  capture(err: unknown, extra?: Record<string, string>) {
    void captureToSentry(err, extra);
  },
};
