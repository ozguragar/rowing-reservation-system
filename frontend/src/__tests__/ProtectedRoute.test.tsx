import React from 'react';
import { render, screen, waitFor } from '@testing-library/react';
import '@testing-library/jest-dom';
import ProtectedRoute from '@/components/ProtectedRoute';

const mockPush = jest.fn();
jest.mock('next/navigation', () => ({
  useRouter: () => ({ push: mockPush }),
  usePathname: () => '/',
}));

const mockUseAuth = jest.fn();
jest.mock('@/context/AuthContext', () => ({
  useAuth: () => mockUseAuth(),
  AuthProvider: ({ children }: any) => <>{children}</>,
}));

beforeEach(() => {
  mockPush.mockReset();
  mockUseAuth.mockReset();
});

const studentUser = { id: 1, fullName: 'S', email: 's@t.com', role: 'STUDENT' as const, isFinishedBasicTraining: true, isOnSchoolTeam: false, lessonsAttended: 0 };
const adminUser = { ...studentUser, role: 'CLUB_ADMIN' as const };

test('shows spinner while loading', () => {
  mockUseAuth.mockReturnValue({ user: null, isLoading: true, isAuthenticated: false });
  render(<ProtectedRoute><div>content</div></ProtectedRoute>);
  expect(screen.queryByText('content')).not.toBeInTheDocument();
});

test('unauthenticated user redirects to /login', async () => {
  mockUseAuth.mockReturnValue({ user: null, isLoading: false, isAuthenticated: false });
  render(<ProtectedRoute><div>content</div></ProtectedRoute>);
  await waitFor(() => expect(mockPush).toHaveBeenCalledWith('/login'));
  expect(screen.queryByText('content')).not.toBeInTheDocument();
});

test('authenticated non-admin can access non-admin route', async () => {
  mockUseAuth.mockReturnValue({ user: studentUser, isLoading: false, isAuthenticated: true });
  render(<ProtectedRoute><div>content</div></ProtectedRoute>);
  await waitFor(() => expect(screen.getByText('content')).toBeInTheDocument());
  expect(mockPush).not.toHaveBeenCalled();
});

test('non-admin hitting adminOnly route redirects to /dashboard', async () => {
  mockUseAuth.mockReturnValue({ user: studentUser, isLoading: false, isAuthenticated: true });
  render(<ProtectedRoute adminOnly><div>admin content</div></ProtectedRoute>);
  await waitFor(() => expect(mockPush).toHaveBeenCalledWith('/dashboard'));
  expect(screen.queryByText('admin content')).not.toBeInTheDocument();
});

test('admin can access adminOnly route', async () => {
  mockUseAuth.mockReturnValue({ user: adminUser, isLoading: false, isAuthenticated: true });
  render(<ProtectedRoute adminOnly><div>admin content</div></ProtectedRoute>);
  await waitFor(() => expect(screen.getByText('admin content')).toBeInTheDocument());
  expect(mockPush).not.toHaveBeenCalledWith('/dashboard');
});
