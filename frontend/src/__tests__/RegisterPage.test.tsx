import React from 'react';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import '@testing-library/jest-dom';
import RegisterPage from '@/app/register/page';

const mockPush = jest.fn();
jest.mock('next/navigation', () => ({
  useRouter: () => ({ push: mockPush }),
  usePathname: () => '/register',
}));

const mockRegister = jest.fn();
jest.mock('@/context/AuthContext', () => ({
  useAuth: () => ({ register: mockRegister, user: null, isLoading: false, isAuthenticated: false }),
  AuthProvider: ({ children }: any) => <>{children}</>,
}));

const mockApiGet = jest.fn();
jest.mock('@/lib/api', () => ({
  __esModule: true,
  default: { get: (...args: any[]) => mockApiGet(...args) },
}));

beforeEach(() => {
  mockRegister.mockReset();
  mockPush.mockReset();
  mockApiGet.mockReset();
  mockApiGet.mockResolvedValue({ data: [{ id: 1, name: 'Riverside Rowing Club' }] });
});

test('renders the registration form fields', async () => {
  render(<RegisterPage />);
  expect(screen.getByPlaceholderText('John Doe')).toBeInTheDocument();
  expect(screen.getByPlaceholderText('your@email.com')).toBeInTheDocument();
  expect(screen.getByPlaceholderText('Min 8 characters')).toBeInTheDocument();
  await waitFor(() => expect(mockApiGet).toHaveBeenCalledWith('/auth/clubs'));
});

test('password field enforces an 8-character minimum (matches backend)', () => {
  render(<RegisterPage />);
  const pw = screen.getByPlaceholderText('Min 8 characters') as HTMLInputElement;
  expect(pw.minLength).toBe(8);
});

test('does not offer a role/trainer selector (no self-elevation)', async () => {
  render(<RegisterPage />);
  await waitFor(() => expect(mockApiGet).toHaveBeenCalled());
  expect(screen.queryByText('Trainer')).not.toBeInTheDocument();
});

test('a single club is auto-selected and hidden; register sends its id', async () => {
  mockRegister.mockResolvedValueOnce(undefined);
  render(<RegisterPage />);
  await waitFor(() => expect(mockApiGet).toHaveBeenCalled());
  // Only one club -> no dropdown shown
  expect(screen.queryByText('Club')).not.toBeInTheDocument();

  fireEvent.change(screen.getByPlaceholderText('John Doe'), { target: { value: 'New Rower' } });
  fireEvent.change(screen.getByPlaceholderText('your@email.com'), { target: { value: 'new@rower.com' } });
  fireEvent.change(screen.getByPlaceholderText('Min 8 characters'), { target: { value: 'password1' } });
  fireEvent.click(screen.getByRole('button', { name: /create account/i }));

  await waitFor(() => expect(mockRegister).toHaveBeenCalledWith('New Rower', 'new@rower.com', 'password1', 1));
  await waitFor(() => expect(mockPush).toHaveBeenCalledWith('/dashboard'));
});

test('shows a club dropdown when multiple clubs exist', async () => {
  mockApiGet.mockResolvedValue({ data: [{ id: 1, name: 'Club A' }, { id: 2, name: 'Club B' }] });
  render(<RegisterPage />);
  await waitFor(() => expect(screen.getByText('Club')).toBeInTheDocument());
  expect(screen.getByRole('option', { name: 'Club A' })).toBeInTheDocument();
  expect(screen.getByRole('option', { name: 'Club B' })).toBeInTheDocument();
});

test('shows an error message when registration fails', async () => {
  mockRegister.mockRejectedValueOnce({ response: { data: { message: 'Email already registered' } } });
  render(<RegisterPage />);
  await waitFor(() => expect(mockApiGet).toHaveBeenCalled());
  fireEvent.change(screen.getByPlaceholderText('John Doe'), { target: { value: 'Dup' } });
  fireEvent.change(screen.getByPlaceholderText('your@email.com'), { target: { value: 'dup@rower.com' } });
  fireEvent.change(screen.getByPlaceholderText('Min 8 characters'), { target: { value: 'password1' } });
  fireEvent.click(screen.getByRole('button', { name: /create account/i }));
  await waitFor(() => expect(screen.getByText('Email already registered')).toBeInTheDocument());
});
