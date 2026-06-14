import React from 'react';
import { render, screen, waitFor } from '@testing-library/react';
import '@testing-library/jest-dom';

jest.mock('next/navigation', () => ({
  useRouter: () => ({ push: jest.fn() }),
  usePathname: () => '/admin/scheduler',
}));

jest.mock('@/context/AuthContext', () => ({
  useAuth: () => ({ user: { id: 1, role: 'ADMIN', fullName: 'Admin', email: 'a@a.com', isFinishedBasicTraining: true, isOnSchoolTeam: false, lessonsAttended: 0 }, isLoading: false, isAuthenticated: true }),
  AuthProvider: ({ children }: any) => <>{children}</>,
}));

jest.mock('@/lib/api', () => ({
  __esModule: true,
  default: {
    get: jest.fn().mockResolvedValue({ data: {} }),
    interceptors: { request: { use: jest.fn() }, response: { use: jest.fn() } },
  },
}));

import AdminSchedulerPage from '@/app/admin/scheduler/page';

test('renders scheduler page without crashing', async () => {
  render(<AdminSchedulerPage />);
  await waitFor(() => expect(screen.getByRole('heading', { name: /auto-scheduler/i })).toBeInTheDocument());
});
