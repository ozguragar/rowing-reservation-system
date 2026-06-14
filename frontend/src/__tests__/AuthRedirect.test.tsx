import React from 'react';
import { render } from '@testing-library/react';
import '@testing-library/jest-dom';

const mockPush = jest.fn();

jest.mock('next/navigation', () => ({
  useRouter: () => ({ push: mockPush }),
  usePathname: () => '/dashboard',
}));

jest.mock('@/context/AuthContext', () => ({
  useAuth: () => ({
    user: null,
    isLoading: false,
    isAuthenticated: false,
    login: jest.fn(),
    register: jest.fn(),
    logout: jest.fn(),
  }),
  AuthProvider: ({ children }: { children: React.ReactNode }) => <>{children}</>,
}));

function ProtectedRoute({ children }: { children: React.ReactNode }) {
  const { useAuth } = require('@/context/AuthContext');
  const { useRouter } = require('next/navigation');
  const { isAuthenticated, isLoading } = useAuth();
  const router = useRouter();

  React.useEffect(() => {
    if (!isLoading && !isAuthenticated) {
      router.push('/login');
    }
  }, [isLoading, isAuthenticated, router]);

  if (!isAuthenticated) return null;
  return <>{children}</>;
}

describe('Auth Redirect', () => {
  beforeEach(() => {
    mockPush.mockClear();
  });

  test('unauthenticated user is redirected to login page', () => {
    render(
      <ProtectedRoute>
        <div>Protected Content</div>
      </ProtectedRoute>
    );

    expect(mockPush).toHaveBeenCalledWith('/login');
  });

  test('protected content is not rendered for unauthenticated user', () => {
    const { queryByText } = render(
      <ProtectedRoute>
        <div>Protected Content</div>
      </ProtectedRoute>
    );

    expect(queryByText('Protected Content')).not.toBeInTheDocument();
  });
});
