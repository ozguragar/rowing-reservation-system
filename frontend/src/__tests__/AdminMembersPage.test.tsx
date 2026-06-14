import React from 'react';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import '@testing-library/jest-dom';

jest.mock('next/navigation', () => ({
  useRouter: () => ({ push: jest.fn() }),
  usePathname: () => '/admin/members',
}));

jest.mock('@/context/AuthContext', () => ({
  useAuth: () => ({ user: { id: 1, role: 'ADMIN', fullName: 'A', email: 'a@t.com', isFinishedBasicTraining: true, isOnSchoolTeam: false, lessonsAttended: 0 }, isLoading: false, isAuthenticated: true }),
  AuthProvider: ({ children }: any) => <>{children}</>,
}));

jest.mock('@/lib/api', () => ({
  __esModule: true,
  default: {
    get: jest.fn().mockResolvedValue({ data: [
      { id: 2, fullName: 'Alice Smith', email: 'alice@test.com', role: 'STUDENT', isFinishedBasicTraining: true, isOnSchoolTeam: false, lessonsAttended: 10, creditBalance: 8, earliestCreditExpiration: '2026-06-01T23:59:00' },
      { id: 3, fullName: 'Bob Jones', email: 'bob@test.com', role: 'CLUB_MEMBER', isFinishedBasicTraining: false, isOnSchoolTeam: false, lessonsAttended: 2, creditBalance: 0, earliestCreditExpiration: null },
      { id: 4, fullName: 'Charlie Zed', email: 'charlie@example.org', role: 'CLUB_MEMBER', isFinishedBasicTraining: true, isOnSchoolTeam: false, lessonsAttended: 20, creditBalance: 12, earliestCreditExpiration: '2026-05-15T23:59:00' },
      { id: 99, fullName: 'Admin One', email: 'admin1@test.com', role: 'ADMIN', isFinishedBasicTraining: true, isOnSchoolTeam: false, lessonsAttended: 0, creditBalance: 0 },
    ] }),
    interceptors: { request: { use: jest.fn() }, response: { use: jest.fn() } },
  },
}));

import AdminMembersPage from '@/app/admin/members/page';

test('renders non-admin users as cards', async () => {
  render(<AdminMembersPage />);
  await waitFor(() => expect(screen.getByText('Alice Smith')).toBeInTheDocument());
  expect(screen.getByText('Bob Jones')).toBeInTheDocument();
  expect(screen.getByText('Charlie Zed')).toBeInTheDocument();
  expect(screen.queryByText('Admin One')).not.toBeInTheDocument();
});

test('active count shows number of members with credit > 0', async () => {
  render(<AdminMembersPage />);
  await waitFor(() => expect(screen.getByTestId('active-count')).toHaveTextContent('2 active'));
});

test('search filters by email', async () => {
  render(<AdminMembersPage />);
  await waitFor(() => screen.getByText('Alice Smith'));
  fireEvent.change(screen.getByTestId('members-search'), { target: { value: 'example.org' } });
  expect(screen.queryByText('Alice Smith')).not.toBeInTheDocument();
  expect(screen.getByText('Charlie Zed')).toBeInTheDocument();
});

test('role filter restricts to CLUB_MEMBER', async () => {
  render(<AdminMembersPage />);
  await waitFor(() => screen.getByText('Alice Smith'));
  fireEvent.change(screen.getByTestId('role-filter'), { target: { value: 'CLUB_MEMBER' } });
  expect(screen.queryByText('Alice Smith')).not.toBeInTheDocument();
  expect(screen.getByText('Bob Jones')).toBeInTheDocument();
});

test('wallet link points to /admin/ledger?user=<id>', async () => {
  render(<AdminMembersPage />);
  await waitFor(() => screen.getByText('Alice Smith'));
  const wallet = screen.getByTestId('wallet-2');
  expect(wallet).toHaveAttribute('href', '/admin/ledger?user=2');
});

test('name link points to /account/<id>', async () => {
  render(<AdminMembersPage />);
  await waitFor(() => screen.getByText('Alice Smith'));
  expect(screen.getByText('Alice Smith').closest('a')).toHaveAttribute('href', '/account/2');
});
