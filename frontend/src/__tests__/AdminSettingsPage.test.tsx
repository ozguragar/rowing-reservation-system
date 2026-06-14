import React from 'react';
import { render, screen, waitFor, fireEvent } from '@testing-library/react';
import '@testing-library/jest-dom';

jest.mock('next/navigation', () => ({
  useRouter: () => ({ push: jest.fn() }),
  usePathname: () => '/admin/settings',
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

const mockRefreshGlobalSettings = jest.fn();
jest.mock('@/context/SettingsContext', () => ({
  useSettings: () => ({ settings: {}, refresh: mockRefreshGlobalSettings, loaded: true }),
  SettingsProvider: ({ children }: any) => <>{children}</>,
}));

const mockApiGet = jest.fn();
const mockApiPut = jest.fn();
jest.mock('@/lib/api', () => ({
  __esModule: true,
  default: {
    get: (...a: any[]) => mockApiGet(...a),
    put: (...a: any[]) => mockApiPut(...a),
    interceptors: { request: { use: jest.fn() }, response: { use: jest.fn() } },
  },
}));

import SettingsPage from '@/app/admin/settings/page';

const defaultSettings = {
  student_booking_hour: '16',
  student_next_day_only: 'false',
  allow_cancellations: 'true',
  show_booked_members: 'true',
};

beforeEach(() => {
  mockApiGet.mockResolvedValue({ data: defaultSettings });
  mockApiPut.mockResolvedValue({ data: {} });
  mockToast.mockReset();
});

test('renders all toggle settings', async () => {
  render(<SettingsPage />);
  await waitFor(() => expect(screen.getByText('Allow cancellation requests')).toBeInTheDocument());
  expect(screen.getByText('Next-day-only for students')).toBeInTheDocument();
  expect(screen.getByText('Show booked member names')).toBeInTheDocument();
  expect(screen.getByText('Student booking hour')).toBeInTheDocument();
});

test('toggling allow_cancellations calls PUT with false', async () => {
  render(<SettingsPage />);
  await waitFor(() => screen.getByText('Allow cancellation requests'));
  const checkboxes = screen.getAllByRole('checkbox');
  // First checkbox is allow_cancellations (checked = true by default)
  fireEvent.click(checkboxes[0]);
  await waitFor(() => expect(mockApiPut).toHaveBeenCalledWith(
    '/admin/settings/allow_cancellations', { value: 'false' }
  ));
});

test('saves student_booking_hour on Save click', async () => {
  render(<SettingsPage />);
  await waitFor(() => screen.getByText('Student booking hour'));
  // Select defaults to "16" — change to "14"
  const hourSelect = screen.getByLabelText('Student booking hour');
  fireEvent.change(hourSelect, { target: { value: '14' } });
  fireEvent.click(screen.getByRole('button', { name: /save/i }));
  await waitFor(() => expect(mockApiPut).toHaveBeenCalledWith(
    '/admin/settings/student_booking_hour', { value: '14' }
  ));
});

test('shows success toast after save', async () => {
  render(<SettingsPage />);
  await waitFor(() => screen.getByRole('button', { name: /save/i }));
  fireEvent.click(screen.getByRole('button', { name: /save/i }));
  await waitFor(() => expect(mockToast).toHaveBeenCalledWith('Setting saved', 'success'));
});
