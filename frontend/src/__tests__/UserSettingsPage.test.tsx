import React from 'react';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import '@testing-library/jest-dom';

jest.mock('next/navigation', () => ({
  useRouter: () => ({ push: jest.fn() }),
  usePathname: () => '/settings',
}));

const mockToast = jest.fn();
jest.mock('@/context/DialogContext', () => ({
  useDialog: () => ({ toast: mockToast, confirm: jest.fn() }),
  DialogProvider: ({ children }: any) => <>{children}</>,
}));

jest.mock('@/context/AuthContext', () => ({
  useAuth: () => ({ user: { id: 1, role: 'STUDENT', fullName: 'A', email: 'a@t.com', isFinishedBasicTraining: true, isOnSchoolTeam: false, lessonsAttended: 0 }, isLoading: false, isAuthenticated: true }),
  AuthProvider: ({ children }: any) => <>{children}</>,
}));

const mockToggle = jest.fn();
jest.mock('@/context/ThemeContext', () => ({
  useTheme: () => ({ theme: 'light', toggle: mockToggle, set: jest.fn() }),
  ThemeProvider: ({ children }: any) => <>{children}</>,
}));

const mockPost = jest.fn();
jest.mock('@/lib/api', () => ({
  __esModule: true,
  default: {
    post: (...a: any[]) => mockPost(...a),
    interceptors: { request: { use: jest.fn() }, response: { use: jest.fn() } },
  },
}));

import UserSettingsPage from '@/app/settings/page';

beforeEach(() => {
  mockToast.mockReset();
  mockToggle.mockReset();
  mockPost.mockReset();
});

test('submits password change with correct payload', async () => {
  mockPost.mockResolvedValueOnce({ data: { message: 'Password updated' } });
  render(<UserSettingsPage />);
  const pwInputsA = document.querySelectorAll('input[type="password"]');
  fireEvent.change(pwInputsA[0], { target: { value: 'oldpass' } });
  fireEvent.change(pwInputsA[1], { target: { value: 'newpass1' } });
  fireEvent.change(pwInputsA[2], { target: { value: 'newpass1' } });
  fireEvent.click(screen.getByRole('button', { name: /update password/i }));
  await waitFor(() => expect(mockPost).toHaveBeenCalledWith(
    '/users/me/password',
    { currentPassword: 'oldpass', newPassword: 'newpass1' }
  ));
});

test('shows error when new password != confirm', async () => {
  render(<UserSettingsPage />);
  const pwInputs = document.querySelectorAll('input[type="password"]');
  fireEvent.change(pwInputs[0], { target: { value: 'oldpass' } });
  fireEvent.change(pwInputs[1], { target: { value: 'newpass1' } });
  fireEvent.change(pwInputs[2], { target: { value: 'mismatch' } });
  fireEvent.click(screen.getByRole('button', { name: /update password/i }));
  await waitFor(() => expect(screen.getByTestId('password-error')).toBeInTheDocument());
});

test('toggle calls theme toggle', () => {
  render(<UserSettingsPage />);
  fireEvent.click(screen.getByTestId('theme-toggle'));
  expect(mockToggle).toHaveBeenCalled();
});
