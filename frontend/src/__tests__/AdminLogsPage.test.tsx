import React from 'react';
import { render, screen, waitFor } from '@testing-library/react';
import '@testing-library/jest-dom';

jest.mock('next/navigation', () => ({
  useRouter: () => ({ push: jest.fn() }),
  usePathname: () => '/admin/logs',
}));

jest.mock('@/context/AuthContext', () => ({
  useAuth: () => ({ user: { id: 1, role: 'ADMIN', fullName: 'Admin', email: 'a@a.com', isFinishedBasicTraining: true, isOnSchoolTeam: false, lessonsAttended: 0 }, isLoading: false, isAuthenticated: true }),
  AuthProvider: ({ children }: any) => <>{children}</>,
}));

jest.mock('@/lib/api', () => ({
  __esModule: true,
  default: {
    get: jest.fn().mockResolvedValue({ data: [{ id: 1, userEmail: 'audit@test.com', action: 'POST /api/bookings', endpoint: '/api/bookings', timestamp: '2026-04-17T10:00:00', details: 'ok' }] }),
    interceptors: { request: { use: jest.fn() }, response: { use: jest.fn() } },
  },
}));

import AdminLogsPage from '@/app/admin/logs/page';

test('renders audit log entries', async () => {
  render(<AdminLogsPage />);
  await waitFor(() => expect(screen.getByText('audit@test.com')).toBeInTheDocument());
});
