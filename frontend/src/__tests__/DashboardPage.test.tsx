import React from 'react';
import { render, screen, waitFor, fireEvent } from '@testing-library/react';
import '@testing-library/jest-dom';

const mockPush = jest.fn();
jest.mock('next/navigation', () => ({
  useRouter: () => ({ push: mockPush }),
  usePathname: () => '/dashboard',
}));

const mockToast = jest.fn();
const mockConfirm = jest.fn();
jest.mock('@/context/DialogContext', () => ({
  useDialog: () => ({ toast: mockToast, confirm: mockConfirm }),
  DialogProvider: ({ children }: any) => <>{children}</>,
}));

const mockUseAuth = jest.fn();
jest.mock('@/context/AuthContext', () => ({
  useAuth: () => mockUseAuth(),
  AuthProvider: ({ children }: any) => <>{children}</>,
}));

const mockApiGet = jest.fn();
const mockApiDelete = jest.fn();
jest.mock('@/lib/api', () => ({
  __esModule: true,
  default: {
    get: (...args: any[]) => mockApiGet(...args),
    delete: (...args: any[]) => mockApiDelete(...args),
    interceptors: { request: { use: jest.fn() }, response: { use: jest.fn() } },
  },
}));

const studentUser = { id: 1, fullName: 'Alice', email: 'alice@test.com', role: 'STUDENT' as const, isFinishedBasicTraining: true, isOnSchoolTeam: false, lessonsAttended: 5 };
const adminUser = { ...studentUser, role: 'ADMIN' as const, fullName: 'Admin User' };

function makeBooking(status = 'MANUAL') {
  return { id: 10, userId: 1, userFullName: 'Alice', userEmail: 'alice@test.com', userRole: 'STUDENT', boatId: 1, boatName: 'Boat A', sessionId: 1, status, createdAt: '2026-04-17T10:00:00' };
}

import DashboardPage from '@/app/dashboard/page';

beforeEach(() => {
  mockApiGet.mockReset();
  mockApiDelete.mockReset();
  mockToast.mockReset();
  mockConfirm.mockReset();
  mockPush.mockReset();
  mockUseAuth.mockReturnValue({ user: studentUser, isLoading: false, isAuthenticated: true, logout: jest.fn() });
});

function setupMocks(balance = 5, bookings: any[] = [], lessonsAttended = 3) {
  mockApiGet.mockImplementation((url: string) => {
    if (url === '/ledger/balance') return Promise.resolve({ data: { balance, expirationDate: 'none' } });
    if (url === '/bookings/my') return Promise.resolve({ data: bookings });
    if (url === '/users/me') return Promise.resolve({ data: { ...studentUser, lessonsAttended } });
    return Promise.reject(new Error(`Unexpected: ${url}`));
  });
}

test('renders balance, lessons, bookings count', async () => {
  setupMocks(7, [], 12);
  render(<DashboardPage />);
  await waitFor(() => expect(screen.getByText('7')).toBeInTheDocument());
  expect(screen.getByText('12')).toBeInTheDocument();
  expect(screen.getByText('0')).toBeInTheDocument(); // upcoming bookings
});

test('credit balance card links to /ledger', async () => {
  setupMocks();
  render(<DashboardPage />);
  await waitFor(() => expect(screen.getByText('View ledger →')).toBeInTheDocument());
  const link = screen.getByText('View ledger →').closest('a');
  expect(link).toHaveAttribute('href', '/ledger');
});

test('shows cancellation pending badge for CANCELLATION_REQUESTED booking', async () => {
  setupMocks(5, [makeBooking('CANCELLATION_REQUESTED')]);
  render(<DashboardPage />);
  await waitFor(() => expect(screen.getByText('Cancellation pending admin')).toBeInTheDocument());
});

test('admin sees Quick Actions with Settings link', async () => {
  mockUseAuth.mockReturnValue({ user: adminUser, isLoading: false, isAuthenticated: true, logout: jest.fn() });
  mockApiGet.mockImplementation((url: string) => {
    if (url === '/ledger/balance') return Promise.resolve({ data: { balance: 0, expirationDate: 'none' } });
    if (url === '/bookings/my') return Promise.resolve({ data: [] });
    if (url === '/users/me') return Promise.resolve({ data: { ...adminUser } });
    return Promise.reject(new Error(url));
  });
  render(<DashboardPage />);
  await waitFor(() => expect(screen.getByText('Admin Dashboard')).toBeInTheDocument());
  expect(screen.getByText('Admin Quick Actions')).toBeInTheDocument();
});

test('cancel button disabled when status is CANCELLATION_REQUESTED', async () => {
  setupMocks(5, [makeBooking('CANCELLATION_REQUESTED')]);
  render(<DashboardPage />);
  await waitFor(() => expect(screen.getByTitle('Already pending admin approval')).toBeDisabled());
});
