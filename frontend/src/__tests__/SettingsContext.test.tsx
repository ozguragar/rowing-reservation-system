import React from 'react';
import { render, screen, waitFor, act } from '@testing-library/react';
import '@testing-library/jest-dom';
import { SettingsProvider, useSettings } from '@/context/SettingsContext';

const mockGet = jest.fn();
jest.mock('@/lib/api', () => ({
  __esModule: true,
  default: {
    get: (...a: any[]) => mockGet(...a),
    interceptors: { request: { use: jest.fn() }, response: { use: jest.fn() } },
  },
}));

const mockUseAuth = jest.fn();
jest.mock('@/context/AuthContext', () => ({
  useAuth: () => mockUseAuth(),
  AuthProvider: ({ children }: any) => <>{children}</>,
}));

function Consumer() {
  const { settings, refresh, loaded } = useSettings();
  return (
    <div>
      <span data-testid="loaded">{String(loaded)}</span>
      <span data-testid="val">{settings.disable_availability ?? 'none'}</span>
      <button onClick={() => refresh()}>refresh</button>
    </div>
  );
}

const mockUser = { id: 1, fullName: 'A', email: 'a@t.com', role: 'STUDENT' as const, isFinishedBasicTraining: true, isOnSchoolTeam: false, lessonsAttended: 0 };

beforeEach(() => {
  localStorage.clear();
  mockGet.mockReset();
  mockUseAuth.mockReset();
});

test('does not call api when user is null', async () => {
  mockUseAuth.mockReturnValue({ user: null });
  render(<SettingsProvider><Consumer /></SettingsProvider>);
  await waitFor(() => expect(screen.getByTestId('loaded')).toHaveTextContent('true'));
  expect(mockGet).not.toHaveBeenCalled();
});

test('loads settings when user present', async () => {
  mockUseAuth.mockReturnValue({ user: mockUser });
  mockGet.mockResolvedValueOnce({ data: { disable_availability: 'true' } });
  render(<SettingsProvider><Consumer /></SettingsProvider>);
  await waitFor(() => expect(screen.getByTestId('val')).toHaveTextContent('true'));
});

test('refresh re-fetches settings', async () => {
  mockUseAuth.mockReturnValue({ user: mockUser });
  mockGet
    .mockResolvedValueOnce({ data: { disable_availability: 'false' } })
    .mockResolvedValueOnce({ data: { disable_availability: 'true' } });
  render(<SettingsProvider><Consumer /></SettingsProvider>);
  await waitFor(() => expect(screen.getByTestId('val')).toHaveTextContent('false'));
  await act(async () => { screen.getByText('refresh').click(); });
  await waitFor(() => expect(screen.getByTestId('val')).toHaveTextContent('true'));
});
