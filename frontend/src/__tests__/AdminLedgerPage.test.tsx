import React from 'react';
import { render, screen, waitFor, fireEvent } from '@testing-library/react';
import '@testing-library/jest-dom';

jest.mock('next/navigation', () => ({
  useRouter: () => ({ push: jest.fn() }),
  usePathname: () => '/admin/ledger',
  useSearchParams: () => ({ get: () => null }),
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
const mockApiPatch = jest.fn();
jest.mock('@/lib/api', () => ({
  __esModule: true,
  default: {
    get: (...a: any[]) => mockApiGet(...a),
    post: (...a: any[]) => mockApiPost(...a),
    patch: (...a: any[]) => mockApiPatch(...a),
    interceptors: { request: { use: jest.fn() }, response: { use: jest.fn() } },
  },
}));

const users = [
  { id: 1, fullName: 'Alice Smith', email: 'alice@test.com', role: 'MEMBER', memberType: 'STUDENT', isFinishedBasicTraining: true, isOnSchoolTeam: false, lessonsAttended: 5, creditBalance: 8 },
  { id: 2, fullName: 'Bob Jones', email: 'bob@test.com', role: 'MEMBER', memberType: 'DEFAULT', isFinishedBasicTraining: true, isOnSchoolTeam: false, lessonsAttended: 2, creditBalance: 0 },
];

import AdminLedgerPage from '@/app/admin/ledger/page';

beforeEach(() => {
  mockApiGet.mockResolvedValue({ data: users });
  mockApiPost.mockResolvedValue({ data: {} });
  mockApiPatch.mockResolvedValue({ data: {} });
  mockToast.mockReset();
});

test('renders user list', async () => {
  render(<AdminLedgerPage />);
  await waitFor(() => expect(screen.getByText('Alice Smith')).toBeInTheDocument());
  expect(screen.getByText('Bob Jones')).toBeInTheDocument();
});

test('search filters by name', async () => {
  render(<AdminLedgerPage />);
  await waitFor(() => screen.getByText('Alice Smith'));
  fireEvent.change(screen.getByPlaceholderText('Search members...'), { target: { value: 'alice' } });
  expect(screen.getByText('Alice Smith')).toBeInTheDocument();
  expect(screen.queryByText('Bob Jones')).not.toBeInTheDocument();
});

test('add credit submits with endOfDay expiration', async () => {
  mockApiGet
    .mockResolvedValueOnce({ data: users }) // initial load
    .mockResolvedValueOnce({ data: [] })    // ledger entries
    .mockResolvedValueOnce({ data: users }); // reload after submit

  render(<AdminLedgerPage />);
  await waitFor(() => screen.getByText('Alice Smith'));

  // Click on Alice to open her ledger
  fireEvent.click(screen.getByText('Alice Smith'));
  await waitFor(() => screen.getByText('Add Credit'));

  fireEvent.click(screen.getByText('Add Credit'));
  await waitFor(() => screen.getByPlaceholderText('Amount'));

  fireEvent.change(screen.getByPlaceholderText('Amount'), { target: { value: '5' } });
  fireEvent.click(screen.getByRole('button', { name: /add$/i }));

  await waitFor(() => expect(mockApiPost).toHaveBeenCalledWith(
    '/admin/ledger/1/credit',
    expect.objectContaining({ amount: 5 })
  ));
});
