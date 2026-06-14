import React from 'react';
import { render, screen, waitFor, fireEvent } from '@testing-library/react';
import '@testing-library/jest-dom';

jest.mock('next/navigation', () => ({
  useRouter: () => ({ push: jest.fn() }),
  usePathname: () => '/admin/cancellations',
}));

const mockToast = jest.fn();
jest.mock('@/context/DialogContext', () => ({
  useDialog: () => ({ toast: mockToast, confirm: jest.fn() }),
  DialogProvider: ({ children }: any) => <>{children}</>,
}));

jest.mock('@/context/AuthContext', () => ({
  useAuth: () => ({ user: { id: 1, role: 'CLUB_ADMIN', fullName: 'Admin', email: 'a@a.com', isFinishedBasicTraining: true, isOnSchoolTeam: false, lessonsAttended: 0, featureCancellation: true, featureAutoScheduler: true }, isLoading: false, isAuthenticated: true }),
  AuthProvider: ({ children }: any) => <>{children}</>,
}));

const mockApiGet = jest.fn();
const mockApiPost = jest.fn();
jest.mock('@/lib/api', () => ({
  __esModule: true,
  default: {
    get: (...a: any[]) => mockApiGet(...a),
    post: (...a: any[]) => mockApiPost(...a),
    interceptors: { request: { use: jest.fn() }, response: { use: jest.fn() } },
  },
}));

const request = {
  id: 5, userId: 10, userFullName: 'Bob User', userEmail: 'bob@test.com',
  userRole: 'STUDENT', boatId: 2, boatName: 'Coastal 4x', sessionId: 3,
  status: 'CANCELLATION_REQUESTED', createdAt: '2026-04-17T10:00:00',
};

import CancellationsPage from '@/app/admin/cancellations/page';

beforeEach(() => {
  mockApiGet.mockResolvedValue({ data: [request] });
  mockApiPost.mockResolvedValue({ data: {} });
  mockToast.mockReset();
});

test('lists pending cancellation requests', async () => {
  render(<CancellationsPage />);
  await waitFor(() => expect(screen.getByText('Bob User')).toBeInTheDocument());
  expect(screen.getByText('Coastal 4x')).toBeInTheDocument();
});

test('approve calls POST approve endpoint and reloads', async () => {
  render(<CancellationsPage />);
  await waitFor(() => screen.getByText('Bob User'));
  fireEvent.click(screen.getByRole('button', { name: /approve/i }));
  await waitFor(() => expect(mockApiPost).toHaveBeenCalledWith('/admin/cancellation-requests/5/approve'));
});

test('deny calls POST deny endpoint', async () => {
  render(<CancellationsPage />);
  await waitFor(() => screen.getByText('Bob User'));
  fireEvent.click(screen.getByRole('button', { name: /deny/i }));
  await waitFor(() => expect(mockApiPost).toHaveBeenCalledWith('/admin/cancellation-requests/5/deny'));
});

test('shows empty state when no requests', async () => {
  mockApiGet.mockResolvedValue({ data: [] });
  render(<CancellationsPage />);
  await waitFor(() => expect(screen.getByText('No pending cancellation requests')).toBeInTheDocument());
});
