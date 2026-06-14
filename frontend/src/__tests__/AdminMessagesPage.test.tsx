import React from 'react';
import { render, screen, waitFor } from '@testing-library/react';
import '@testing-library/jest-dom';

jest.mock('next/navigation', () => ({
  useRouter: () => ({ push: jest.fn() }),
  usePathname: () => '/admin/messages',
}));

jest.mock('@/context/AuthContext', () => ({
  useAuth: () => ({ user: { id: 1, role: 'CLUB_ADMIN', fullName: 'A', email: 'a@t.com', isFinishedBasicTraining: true, isOnSchoolTeam: false, lessonsAttended: 0, featureCancellation: true, featureAutoScheduler: true }, isLoading: false, isAuthenticated: true }),
  AuthProvider: ({ children }: any) => <>{children}</>,
}));

jest.mock('@/lib/api', () => ({
  __esModule: true,
  default: {
    get: jest.fn().mockResolvedValue({ data: [] }),
    interceptors: { request: { use: jest.fn() }, response: { use: jest.fn() } },
  },
}));

import MessagesPage from '@/app/admin/messages/page';

test('does NOT render System Settings section', async () => {
  render(<MessagesPage />);
  await waitFor(() => expect(screen.getByText('Messages')).toBeInTheDocument());
  expect(screen.queryByText('System Settings')).not.toBeInTheDocument();
  expect(screen.queryByText('Student Next-Day Only')).not.toBeInTheDocument();
  expect(screen.queryByText('Show Booked Members')).not.toBeInTheDocument();
});

test('renders member reports heading', async () => {
  render(<MessagesPage />);
  await waitFor(() => expect(screen.getByText(/Member Reports/)).toBeInTheDocument());
});
