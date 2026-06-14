import React from 'react';
import { render, screen, waitFor, fireEvent } from '@testing-library/react';
import '@testing-library/jest-dom';

const mockReplace = jest.fn();
jest.mock('next/navigation', () => ({
  useRouter: () => ({ push: jest.fn(), replace: mockReplace }),
  usePathname: () => '/account/1',
  useParams: () => ({ id: '5' }),
}));

const mockUseAuth = jest.fn();
jest.mock('@/context/AuthContext', () => ({
  useAuth: () => mockUseAuth(),
  AuthProvider: ({ children }: any) => <>{children}</>,
}));

const mockToast = jest.fn();
const mockConfirm = jest.fn();
jest.mock('@/context/DialogContext', () => ({
  useDialog: () => ({ toast: mockToast, confirm: mockConfirm }),
  DialogProvider: ({ children }: any) => <>{children}</>,
}));

const mockGet = jest.fn();
const mockPatch = jest.fn();
jest.mock('@/lib/api', () => ({
  __esModule: true,
  default: {
    get: (...a: any[]) => mockGet(...a),
    patch: (...a: any[]) => mockPatch(...a),
    interceptors: { request: { use: jest.fn() }, response: { use: jest.fn() } },
  },
}));

const student = { id: 1, fullName: 'S', email: 's@t.com', role: 'STUDENT' as const, isFinishedBasicTraining: false, isOnSchoolTeam: false, lessonsAttended: 3 };
const admin = { ...student, id: 99, role: 'ADMIN' as const };
const owner = { ...student, id: 5 };

const profileUntrained = { id: 5, fullName: 'Target User', email: 'target@t.com', role: 'STUDENT', isFinishedBasicTraining: false, isOnSchoolTeam: false, lessonsAttended: 4, creditBalance: 7, earliestCreditExpiration: null };
const profileTrained = { ...profileUntrained, isFinishedBasicTraining: true };

import AccountPage from '@/app/account/[id]/page';

beforeEach(() => {
  mockReplace.mockReset();
  mockToast.mockReset();
  mockConfirm.mockReset();
  mockGet.mockReset();
  mockPatch.mockReset();
});

test('non-owner non-admin is redirected', async () => {
  mockUseAuth.mockReturnValue({ user: student, isLoading: false, isAuthenticated: true });
  render(<AccountPage />);
  await waitFor(() => expect(mockReplace).toHaveBeenCalledWith('/dashboard'));
});

test('owner sees own account', async () => {
  mockUseAuth.mockReturnValue({ user: owner, isLoading: false, isAuthenticated: true });
  mockGet.mockResolvedValue({ data: profileUntrained });
  render(<AccountPage />);
  await waitFor(() => expect(screen.getByText('Target User')).toBeInTheDocument());
  expect(screen.getByText('4')).toBeInTheDocument(); // lessons attended
});

test('training warning visible when NOT finished', async () => {
  mockUseAuth.mockReturnValue({ user: owner, isLoading: false, isAuthenticated: true });
  mockGet.mockResolvedValue({ data: profileUntrained });
  render(<AccountPage />);
  await waitFor(() => expect(screen.getByTestId('training-warning')).toBeInTheDocument());
});

test('training warning hidden when finished', async () => {
  mockUseAuth.mockReturnValue({ user: owner, isLoading: false, isAuthenticated: true });
  mockGet.mockResolvedValue({ data: profileTrained });
  render(<AccountPage />);
  await waitFor(() => expect(screen.getByText('Target User')).toBeInTheDocument());
  expect(screen.queryByTestId('training-warning')).not.toBeInTheDocument();
});

test('admin sees mark-complete button on untrained user', async () => {
  mockUseAuth.mockReturnValue({ user: admin, isLoading: false, isAuthenticated: true });
  mockGet.mockResolvedValue({ data: profileUntrained });
  render(<AccountPage />);
  await waitFor(() => expect(screen.getByTestId('mark-training-complete')).toBeInTheDocument());
});

test('non-admin owner does NOT see mark-complete button', async () => {
  mockUseAuth.mockReturnValue({ user: owner, isLoading: false, isAuthenticated: true });
  mockGet.mockResolvedValue({ data: profileUntrained });
  render(<AccountPage />);
  await waitFor(() => expect(screen.getByTestId('training-warning')).toBeInTheDocument());
  expect(screen.queryByTestId('mark-training-complete')).not.toBeInTheDocument();
});

test('admin clicking mark-complete fires PATCH', async () => {
  mockUseAuth.mockReturnValue({ user: admin, isLoading: false, isAuthenticated: true });
  mockGet.mockResolvedValue({ data: profileUntrained });
  mockConfirm.mockResolvedValue(true);
  mockPatch.mockResolvedValue({ data: profileTrained });
  render(<AccountPage />);
  await waitFor(() => screen.getByTestId('mark-training-complete'));
  fireEvent.click(screen.getByTestId('mark-training-complete'));
  await waitFor(() => expect(mockPatch).toHaveBeenCalledWith(
    '/admin/users/5/basic-training', { finished: true }
  ));
});
