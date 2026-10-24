export class ApiError extends Error {
  constructor(message: string, public status: number) {
    super(message);
  }
}

export async function apiFetch(path: string, init: RequestInit = {}): Promise<Response> {
  let response: Response;
  try {
    response = await fetch('/api/' + path, init);
  } catch {
    throw new ApiError('Cannot connect to the server. Check your connection and try again.', 0);
  }
  if (!response.ok) {
    const body = await response.json().catch(() => null);
    const fallback = response.status === 401
      ? 'Your session has expired. Please sign in again.'
      : response.status >= 500
        ? 'The service is unavailable. Please try again shortly.'
        : 'The request could not be completed. Please try again.';
    throw new ApiError(typeof body?.message === 'string' ? body.message : fallback, response.status);
  }
  return response;
}
