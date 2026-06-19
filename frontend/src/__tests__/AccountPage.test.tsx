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

const student = { id: 1, fullName: 'S', email: 's@t.com', role: 'MEMBER' as const, isFinishedBasicTraining: false, isOnSchoolTeam: false, lessonsAttended: 3 };
const admin = { ...student, id: 99, role: 'CLUB_ADMIN' as const };
const superadmin = { ...student, id: 98, role: 'SUPERADMIN' as const };
const trainer = { ...student, id: 97, role: 'TRAINER' as const };
const owner = { ...student, id: 5 };

const profileUntrained = { id: 5, fullName: 'Target User', email: 'target@t.com', role: 'MEMBER', memberType: 'STUDENT', isFinishedBasicTraining: false, isOnSchoolTeam: false, lessonsAttended: 4, creditBalance: 7, earliestCreditExpiration: null };
const profileTrained = { ...profileUntrained, isFinishedBasicTraining: true };

const bookings = [
  { id: 11, userId: 5, boatId: 1, boatName: 'Coastal 4x A', sessionId: 1, sessionDate: '2999-01-01', sessionStartTime: '08:00:00', sessionEndTime: '09:00:00', status: 'MANUAL' },
  { id: 12, userId: 5, boatId: 2, boatName: 'Coastal 2x B', sessionId: 2, sessionDate: '2000-01-01', sessionStartTime: '07:00:00', sessionEndTime: '08:00:00', status: 'AUTO_ASSIGNED' },
];

import AccountPage from '@/app/account/[id]/page';

// Make api.get URL-aware: profile vs reservations.
function mockData(profile: any, list: any[] = []) {
  mockGet.mockImplementation((url: string) =>
    url.endsWith('/bookings')
      ? Promise.resolve({ data: list })
      : Promise.resolve({ data: profile }));
}

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
  mockData(profileUntrained, bookings);
  render(<AccountPage />);
  await waitFor(() => expect(screen.getByText('Target User')).toBeInTheDocument());
  expect(screen.getByText('4')).toBeInTheDocument(); // lessons attended
});

test('training warning visible when NOT finished', async () => {
  mockUseAuth.mockReturnValue({ user: owner, isLoading: false, isAuthenticated: true });
  mockData(profileUntrained, bookings);
  render(<AccountPage />);
  await waitFor(() => expect(screen.getByTestId('training-warning')).toBeInTheDocument());
});

test('training warning hidden when finished', async () => {
  mockUseAuth.mockReturnValue({ user: owner, isLoading: false, isAuthenticated: true });
  mockData(profileTrained, bookings);
  render(<AccountPage />);
  await waitFor(() => expect(screen.getByText('Target User')).toBeInTheDocument());
  expect(screen.queryByTestId('training-warning')).not.toBeInTheDocument();
});

test('admin sees mark-complete button on untrained user', async () => {
  mockUseAuth.mockReturnValue({ user: admin, isLoading: false, isAuthenticated: true });
  mockData(profileUntrained, bookings);
  render(<AccountPage />);
  await waitFor(() => expect(screen.getByTestId('mark-training-complete')).toBeInTheDocument());
});

test('non-admin owner does NOT see mark-complete button', async () => {
  mockUseAuth.mockReturnValue({ user: owner, isLoading: false, isAuthenticated: true });
  mockData(profileUntrained, bookings);
  render(<AccountPage />);
  await waitFor(() => expect(screen.getByTestId('training-warning')).toBeInTheDocument());
  expect(screen.queryByTestId('mark-training-complete')).not.toBeInTheDocument();
});

test('admin clicking mark-complete fires PATCH', async () => {
  mockUseAuth.mockReturnValue({ user: admin, isLoading: false, isAuthenticated: true });
  mockData(profileUntrained, bookings);
  mockConfirm.mockResolvedValue(true);
  mockPatch.mockResolvedValue({ data: profileTrained });
  render(<AccountPage />);
  await waitFor(() => screen.getByTestId('mark-training-complete'));
  fireEvent.click(screen.getByTestId('mark-training-complete'));
  await waitFor(() => expect(mockPatch).toHaveBeenCalledWith(
    '/admin/users/5/basic-training', { finished: true }
  ));
});

// --- reservations ---

test('reservations split into upcoming and past', async () => {
  mockUseAuth.mockReturnValue({ user: owner, isLoading: false, isAuthenticated: true });
  mockData(profileTrained, bookings);
  render(<AccountPage />);
  await waitFor(() => expect(screen.getByTestId('reservations')).toBeInTheDocument());
  expect(screen.getByText('Upcoming (1)')).toBeInTheDocument();
  expect(screen.getByText('Past (1)')).toBeInTheDocument();
  expect(screen.getAllByTestId('reservation-row')).toHaveLength(2);
});

test('empty reservations shows placeholder', async () => {
  mockUseAuth.mockReturnValue({ user: owner, isLoading: false, isAuthenticated: true });
  mockData(profileTrained, []);
  render(<AccountPage />);
  await waitFor(() => expect(screen.getByTestId('reservations')).toBeInTheDocument());
  expect(screen.getByText('No reservations yet.')).toBeInTheDocument();
});

// --- admin manage controls ---

test('club admin sees manage controls; owner and trainer do not', async () => {
  mockUseAuth.mockReturnValue({ user: admin, isLoading: false, isAuthenticated: true });
  mockData(profileTrained, bookings);
  const { unmount } = render(<AccountPage />);
  await waitFor(() => expect(screen.getByTestId('admin-controls')).toBeInTheDocument());
  unmount();

  mockUseAuth.mockReturnValue({ user: owner, isLoading: false, isAuthenticated: true });
  mockData(profileTrained, bookings);
  const ownerView = render(<AccountPage />);
  await waitFor(() => expect(screen.getByText('Target User')).toBeInTheDocument());
  expect(screen.queryByTestId('admin-controls')).not.toBeInTheDocument();
  ownerView.unmount();

  mockUseAuth.mockReturnValue({ user: trainer, isLoading: false, isAuthenticated: true });
  mockData(profileTrained, bookings);
  render(<AccountPage />);
  await waitFor(() => expect(screen.getByText('Target User')).toBeInTheDocument());
  expect(screen.queryByTestId('admin-controls')).not.toBeInTheDocument();
});

test('SUPERADMIN option only offered to superadmin viewers', async () => {
  mockUseAuth.mockReturnValue({ user: admin, isLoading: false, isAuthenticated: true });
  mockData(profileTrained, bookings);
  const { unmount } = render(<AccountPage />);
  await waitFor(() => screen.getByTestId('role-select'));
  expect(screen.queryByRole('option', { name: 'Superadmin' })).not.toBeInTheDocument();
  unmount();

  mockUseAuth.mockReturnValue({ user: superadmin, isLoading: false, isAuthenticated: true });
  mockData(profileTrained, bookings);
  render(<AccountPage />);
  await waitFor(() => screen.getByTestId('role-select'));
  expect(screen.getByRole('option', { name: 'Superadmin' })).toBeInTheDocument();
});

test('saving role + member type fires PATCH with new values', async () => {
  mockUseAuth.mockReturnValue({ user: admin, isLoading: false, isAuthenticated: true });
  mockData(profileTrained, bookings);
  mockPatch.mockResolvedValue({ data: { ...profileTrained, role: 'TRAINER', memberType: 'DEFAULT' } });
  render(<AccountPage />);
  await waitFor(() => screen.getByTestId('role-select'));

  fireEvent.change(screen.getByTestId('role-select'), { target: { value: 'TRAINER' } });
  fireEvent.change(screen.getByTestId('member-type-select'), { target: { value: 'DEFAULT' } });
  fireEvent.click(screen.getByTestId('save-member'));

  await waitFor(() => expect(mockPatch).toHaveBeenCalledWith(
    '/admin/users/5', { role: 'TRAINER', memberType: 'DEFAULT' }
  ));
});
