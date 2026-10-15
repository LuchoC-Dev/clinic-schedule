/**
 * The applications' way to the API (D-97): same origin, the session's cookie, and the CSRF token on every write.
 * The token is kept in memory only, as everything the applications read: nothing goes to the browser's storage.
 *
 * A refusal comes back as an {@link ApiError} with the server's body (D-23), for the screen to put into words
 * (see `errors.ts`). A 401 means the session ended — logged out, run out, or ended by a deactivation (D-68, D-79):
 * the application goes back to its login, reloading the page so nothing read stays in memory.
 */

/** The body of every error the API answers (D-23). */
export interface ErrorBody {
  status: number;
  error: string;
  message: string;
  /** The refusal's stable name, which a screen says in its own words (D-100). */
  code?: string;
  fields?: Record<string, string>;
}

/** The API answered, and said no. */
export class ApiError extends Error {
  readonly status: number;
  readonly body: ErrorBody | null;

  constructor(status: number, body: ErrorBody | null) {
    super(body?.message ?? `HTTP ${status}`);
    this.name = 'ApiError';
    this.status = status;
    this.body = body;
  }
}

/** The API could not be reached at all. */
export class NetworkError extends Error {
  constructor(cause: unknown) {
    super('The API could not be reached', { cause });
    this.name = 'NetworkError';
  }
}

export type Query = Record<string, string | null | undefined>;

export interface ApiClient {
  get<T>(path: string, query?: Query): Promise<T>;
  post<T>(path: string, body?: unknown): Promise<T>;
  put<T>(path: string, body?: unknown): Promise<T>;
  delete<T>(path: string): Promise<T>;
  /** One file, as `multipart/form-data` with a part named `file`: the browser sets its type and boundary. */
  upload<T>(path: string, file: File): Promise<T>;
  /** A login: a form, as Spring Security's reads it, whose 401 is an answer and not a session ended. */
  logIn<T>(path: string, fields: Record<string, string>): Promise<T>;
  setCsrfToken(token: string | null): void;
}

export interface ApiClientOptions {
  /** What to do when the session is over: by default, reload the application at its login. */
  onSessionEnded: () => void;
  fetch?: typeof fetch;
}

const CSRF_HEADER = 'X-CSRF-TOKEN';

export function createApiClient(options: ApiClientOptions): ApiClient {
  let csrfToken: string | null = null;
  const doFetch = options.fetch ?? ((input, init) => fetch(input, init));

  async function send<T>(method: string, url: string, body: BodyInit | undefined, contentType: string | undefined,
      sessionEndsOn401: boolean): Promise<T> {
    const headers: Record<string, string> = { Accept: 'application/json' };
    if (contentType) {
      headers['Content-Type'] = contentType;
    }
    if (method !== 'GET' && csrfToken) {
      headers[CSRF_HEADER] = csrfToken;
    }
    let response: Response;
    try {
      response = await doFetch(url, { method, headers, body, credentials: 'same-origin', cache: 'no-store' });
    } catch (e) {
      throw new NetworkError(e);
    }
    if (!response.ok) {
      const error = new ApiError(response.status, await errorBodyOf(response));
      if (response.status === 401 && sessionEndsOn401) {
        options.onSessionEnded();
      }
      throw error;
    }
    if (response.status === 204) {
      return undefined as T;
    }
    return (await response.json()) as T;
  }

  return {
    get: (path, query) => send('GET', withQuery(path, query), undefined, undefined, true),
    post: (path, body) => send('POST', path, body === undefined ? undefined : JSON.stringify(body),
      body === undefined ? undefined : 'application/json', true),
    put: (path, body) => send('PUT', path, body === undefined ? undefined : JSON.stringify(body),
      body === undefined ? undefined : 'application/json', true),
    delete: (path) => send('DELETE', path, undefined, undefined, true),
    upload: (path, file) => {
      const form = new FormData();
      form.append('file', file);
      return send('POST', path, form, undefined, true);
    },
    logIn: (path, fields) => send('POST', path, new URLSearchParams(fields).toString(),
      'application/x-www-form-urlencoded', false),
    setCsrfToken: (token) => {
      csrfToken = token;
    },
  };
}

function withQuery(path: string, query?: Query): string {
  if (!query) {
    return path;
  }
  const params = new URLSearchParams();
  for (const [name, value] of Object.entries(query)) {
    if (value !== null && value !== undefined && value !== '') {
      params.set(name, value);
    }
  }
  const text = params.toString();
  return text ? `${path}?${text}` : path;
}

async function errorBodyOf(response: Response): Promise<ErrorBody | null> {
  try {
    const body = (await response.json()) as Partial<ErrorBody>;
    return typeof body?.message === 'string' ? (body as ErrorBody) : null;
  } catch {
    return null;
  }
}
