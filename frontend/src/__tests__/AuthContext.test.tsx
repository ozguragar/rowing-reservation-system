import React from 'react';
import { render, screen, act, waitFor } from '@testing-library/react';
import '@testing-library/jest-dom';
import { AuthProvider, useAuth } from '@/context/AuthContext';

const mockPush = jest.fn();
jest.mock('next/navigation', () => ({
  useRouter: () => ({ push: mockPush }),
  usePathname: () => '/',
}));

const mockApiPost = jest.fn();
const mockApiGet = jest.fn();
jest.mock('@/lib/api', () => ({
  __esModule: true,
  default: {
    post: (...args: any[]) => mockApiPost(...args),
    get: (...args: any[]) => mockApiGet(...args),
    interceptors: { request: { use: jest.fn() }, response: { use: jest.fn() } },
  },
}));

function TestConsumer() {
  const { user, isAuthenticated, isLoading, login, logout, register } = useAuth();
  return (
    <div>
      <span data-testid="loading">{String(isLoading)}</span>
      <span data-testid="auth">{String(isAuthenticated)}</span>
      <span data-testid="user">{user?.email ?? 'none'}</span>
      <button onClick={() => login('a@b.com', 'password1')}>login</button>
      <button onClick={logout}>logout</button>
      <button onClick={() => register('Name', 'a@b.com', 'password1', 'STUDENT')}>register</button>
    </div>
  );
}

function setup() {
  return render(
    <AuthProvider>
      <TestConsumer />
    </AuthProvider>
  );
}

beforeEach(() => {
  localStorage.clear();
  mockApiPost.mockReset();
  mockApiGet.mockReset();
  mockPush.mockReset();
  // Default: /users/me probe rejects (no session)
  mockApiGet.mockRejectedValue({ response: { status: 401 } });
  // Stub window.location.href assignment during logout redirect
  Object.defineProperty(window, 'location', {
    writable: true,
    value: { href: '/', pathname: '/' },
  });
});

test('starts unauthenticated when the session probe fails', async () => {
  setup();
  await waitFor(() => expect(screen.getByTestId('loading')).toHaveTextContent('false'));
  expect(screen.getByTestId('auth')).toHaveTextContent('false');
  expect(screen.getByTestId('user')).toHaveTextContent('none');
  // The legacy tokens (if any) should have been cleared from localStorage.
  expect(localStorage.getItem('accessToken')).toBeNull();
});

test('hydrates user from cached snapshot, then verifies via /users/me', async () => {
  const cached = { id: 1, fullName: 'Alice', email: 'alice@test.com', role: 'STUDENT' as const, isFinishedBasicTraining: true, isOnSchoolTeam: false, lessonsAttended: 0 };
  localStorage.setItem('user', JSON.stringify(cached));
  const server = { ...cached, lessonsAttended: 2 };
  mockApiGet.mockResolvedValueOnce({ data: server });

  setup();
  // Cached user is visible immediately; the server-confirmed one lands after the probe.
  await waitFor(() => expect(screen.getByTestId('user')).toHaveTextContent('alice@test.com'));
  await waitFor(() => expect(screen.getByTestId('loading')).toHaveTextContent('false'));
  expect(screen.getByTestId('auth')).toHaveTextContent('true');
  expect(mockApiGet).toHaveBeenCalledWith('/users/me');
});

test('login stores no tokens; cookies are httpOnly and owned by the browser', async () => {
  const responseUser = { id: 2, fullName: 'Bob', email: 'bob@test.com', role: 'STUDENT' as const, isFinishedBasicTraining: true, isOnSchoolTeam: false, lessonsAttended: 0 };
  mockApiPost.mockResolvedValueOnce({ data: { accessToken: 'at', refreshToken: 'rt', user: responseUser } });
  setup();
  await waitFor(() => expect(screen.getByTestId('loading')).toHaveTextContent('false'));
  await act(async () => { screen.getByText('login').click(); });
  expect(screen.getByTestId('user')).toHaveTextContent('bob@test.com');
  expect(localStorage.getItem('accessToken')).toBeNull();
  expect(localStorage.getItem('refreshToken')).toBeNull();
  // A cached user snapshot is kept for instant-hydration UX.
  expect(localStorage.getItem('user')).toContain('bob@test.com');
});

test('logout posts to /auth/logout and clears cached user', async () => {
  const cached = { id: 1, fullName: 'Alice', email: 'alice@test.com', role: 'STUDENT' as const, isFinishedBasicTraining: true, isOnSchoolTeam: false, lessonsAttended: 0 };
  localStorage.setItem('user', JSON.stringify(cached));
  mockApiGet.mockResolvedValueOnce({ data: cached });
  mockApiPost.mockResolvedValueOnce({ data: { message: 'Logged out' } });

  setup();
  await waitFor(() => expect(screen.getByTestId('auth')).toHaveTextContent('true'));
  await act(async () => { screen.getByText('logout').click(); });
  expect(mockApiPost).toHaveBeenCalledWith('/auth/logout');
  expect(localStorage.getItem('user')).toBeNull();
});

test('register calls POST /auth/register and sets user', async () => {
  const responseUser = { id: 3, fullName: 'C', email: 'c@test.com', role: 'STUDENT' as const, isFinishedBasicTraining: false, isOnSchoolTeam: false, lessonsAttended: 0 };
  mockApiPost.mockResolvedValueOnce({ data: { accessToken: 'at', refreshToken: 'rt', user: responseUser } });
  setup();
  await waitFor(() => expect(screen.getByTestId('loading')).toHaveTextContent('false'));
  await act(async () => { screen.getByText('register').click(); });
  expect(mockApiPost).toHaveBeenCalledWith('/auth/register', expect.objectContaining({ email: 'a@b.com' }));
  expect(screen.getByTestId('user')).toHaveTextContent('c@test.com');
});
