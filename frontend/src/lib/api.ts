import axios from 'axios';

const API_URL = process.env.NEXT_PUBLIC_API_URL || 'http://localhost:8080/api';

/**
 * Auth lives in httpOnly cookies — the browser attaches them automatically when
 * `withCredentials: true` is set. There are deliberately no request interceptors
 * reading tokens from localStorage: the JS layer cannot read the cookie, and that
 * is the whole point (XSS cannot exfiltrate the token).
 *
 * On 401, we attempt one silent refresh (the refresh cookie is path-scoped to
 * /api/auth so it only travels on the refresh call itself). If the refresh
 * fails, the user is redirected to /login.
 */
const api = axios.create({
  baseURL: API_URL,
  headers: { 'Content-Type': 'application/json' },
  withCredentials: true,
});

let refreshPromise: Promise<void> | null = null;

async function doRefresh(): Promise<void> {
  await axios.post(`${API_URL}/auth/refresh`, {}, { withCredentials: true });
}

api.interceptors.response.use(
  (response) => response,
  async (error) => {
    const originalRequest = error.config;
    if (!originalRequest) return Promise.reject(error);

    const url: string = originalRequest.url || '';
    const isAuthCall = url.includes('/auth/login') || url.includes('/auth/refresh') || url.includes('/auth/register');

    if (error.response?.status === 401 && !originalRequest._retry && !isAuthCall) {
      originalRequest._retry = true;
      try {
        // Coalesce concurrent refreshes so a burst of 401s doesn't fan out to
        // N refresh calls — only one actually runs.
        if (!refreshPromise) {
          refreshPromise = doRefresh().finally(() => { refreshPromise = null; });
        }
        await refreshPromise;
        return api(originalRequest);
      } catch {
        if (typeof window !== 'undefined') {
          // Clear any legacy localStorage residue from the pre-cookie era.
          try {
            localStorage.removeItem('accessToken');
            localStorage.removeItem('refreshToken');
            localStorage.removeItem('user');
          } catch { /* ignore */ }
          if (!window.location.pathname.startsWith('/login')) {
            window.location.href = '/login';
          }
        }
      }
    }
    return Promise.reject(error);
  }
);

export default api;
