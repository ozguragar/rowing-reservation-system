import React from 'react';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import '@testing-library/jest-dom';
import LoginPage from '@/app/login/page';

const mockPush = jest.fn();
jest.mock('next/navigation', () => ({
  useRouter: () => ({ push: mockPush }),
  usePathname: () => '/login',
}));

const mockLogin = jest.fn();
jest.mock('@/context/AuthContext', () => ({
  useAuth: () => ({ login: mockLogin, user: null, isLoading: false, isAuthenticated: false }),
  AuthProvider: ({ children }: any) => <>{children}</>,
}));

beforeEach(() => {
  mockLogin.mockReset();
  mockPush.mockReset();
});

test('renders email and password inputs', () => {
  render(<LoginPage />);
  expect(screen.getByPlaceholderText('your@email.com')).toBeInTheDocument();
  expect(screen.getByPlaceholderText('Enter password')).toBeInTheDocument();
});

test('successful login pushes to /dashboard', async () => {
  mockLogin.mockResolvedValueOnce(undefined);
  render(<LoginPage />);
  fireEvent.change(screen.getByPlaceholderText('your@email.com'), { target: { value: 'a@b.com' } });
  fireEvent.change(screen.getByPlaceholderText('Enter password'), { target: { value: 'pass123' } });
  fireEvent.click(screen.getByRole('button', { name: /sign in/i }));
  await waitFor(() => expect(mockPush).toHaveBeenCalledWith('/dashboard'));
});

test('shows error when login fails', async () => {
  mockLogin.mockRejectedValueOnce({ response: { data: { message: 'Invalid credentials' } } });
  render(<LoginPage />);
  fireEvent.change(screen.getByPlaceholderText('your@email.com'), { target: { value: 'a@b.com' } });
  fireEvent.change(screen.getByPlaceholderText('Enter password'), { target: { value: 'wrong' } });
  fireEvent.click(screen.getByRole('button', { name: /sign in/i }));
  await waitFor(() => expect(screen.getByText('Invalid credentials')).toBeInTheDocument());
});
