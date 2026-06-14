import React from 'react';
import { render, screen, waitFor, act } from '@testing-library/react';
import '@testing-library/jest-dom';
import BookingPage from '@/app/booking/page';

jest.mock('next/navigation', () => ({
  useRouter: () => ({ push: jest.fn() }),
  usePathname: () => '/booking',
}));

jest.mock('@/components/ProtectedRoute', () => ({
  __esModule: true,
  default: ({ children }: { children: React.ReactNode }) => <>{children}</>,
}));

jest.mock('@/context/DialogContext', () => ({
  useDialog: () => ({ toast: jest.fn(), confirm: jest.fn() }),
}));

const currentUser: { role: 'MEMBER'; memberType: 'STUDENT' | 'RECREATIONAL'; isFinishedBasicTraining: boolean } = {
  role: 'MEMBER',
  memberType: 'RECREATIONAL',
  isFinishedBasicTraining: true,
};
jest.mock('@/context/AuthContext', () => ({
  useAuth: () => ({
    user: {
      id: 1, fullName: 'Test User', email: 't@test.com',
      ...currentUser,
      isOnSchoolTeam: false, lessonsAttended: 0,
    },
    isLoading: false, isAuthenticated: true,
    login: jest.fn(), register: jest.fn(), logout: jest.fn(),
  }),
  AuthProvider: ({ children }: { children: React.ReactNode }) => <>{children}</>,
}));

function isoDate(offsetDays: number): string {
  const d = new Date();
  d.setDate(d.getDate() + offsetDays);
  return d.toISOString().split('T')[0];
}

function makeSession(id: number, dateStr: string) {
  return {
    id, date: dateStr,
    startTime: '08:00:00', endTime: '09:00:00',
    status: 'APPROVED',
    boats: [
      { id: id * 10 + 1, sessionId: id, type: 'COASTAL', capacity: 4,
        isBasicTrainingBoat: true, currentBookings: 0, version: 0,
        name: `Basic-${id}`, bookings: [] },
    ],
  };
}

const apiGet = jest.fn();
jest.mock('@/lib/api', () => ({
  __esModule: true,
  default: {
    get: (...args: any[]) => apiGet(...args),
    post: jest.fn(),
    interceptors: { request: { use: jest.fn() }, response: { use: jest.fn() } },
  },
}));

function freezeTime(hour: number) {
  // Pin to a Wednesday so week view always contains today + tomorrow + day-after.
  const fake = new Date(2026, 3, 15, hour, 0, 0);  // 2026-04-15 (Wed)
  jest.useFakeTimers();
  jest.setSystemTime(fake);
}

beforeEach(() => {
  apiGet.mockReset();
  apiGet.mockImplementation((url: string) => {
    if (url === '/settings/public') {
      return Promise.resolve({ data: { student_next_day_only: 'false' } });
    }
    if (url === '/sessions/upcoming') {
      return Promise.resolve({ data: [
        makeSession(1, isoDate(0)),   // today — needed so initial day view is non-empty
        makeSession(2, isoDate(1)),   // tomorrow — the contested day
        makeSession(3, isoDate(2)),   // day after
      ] });
    }
    return Promise.resolve({ data: [] });
  });
});

afterEach(() => {
  jest.useRealTimers();
});

async function renderInDayView() {
  render(<BookingPage />);
  await act(async () => { await Promise.resolve(); });
  await waitFor(() => expect(apiGet).toHaveBeenCalledWith('/sessions/upcoming'));
  // Switch to Day view so per-boat book buttons render.
  await act(async () => {
    screen.getByRole('button', { name: /^day$/i }).click();
  });
}

/** Advance the day navigator N times (header ChevronRight). */
async function advanceDays(n: number) {
  for (let i = 0; i < n; i++) {
    const rightBtn = screen.getAllByRole('button').find((b) =>
      !!b.querySelector('svg.lucide-chevron-right'));
    if (!rightBtn) throw new Error('next-day button not found');
    await act(async () => { rightBtn.click(); });
  }
}

describe('BookingPage — club member after 4pm', () => {
  beforeEach(() => {
    currentUser.memberType = 'RECREATIONAL';
    currentUser.isFinishedBasicTraining = true;
    freezeTime(18);   // past the 4pm cutoff
  });

  test('shows the "tomorrow is reserved for students" banner', async () => {
    await renderInDayView();
    expect(screen.getByText(/tomorrow's sessions are reserved for students/i)).toBeInTheDocument();
  });

  test("disables the book button on tomorrow's session", async () => {
    await renderInDayView();
    await advanceDays(1);   // navigate to tomorrow
    const btn = screen.getByTestId('book-btn-21');
    expect(btn).toBeDisabled();
  });

  test('leaves non-tomorrow sessions bookable', async () => {
    await renderInDayView();
    await advanceDays(2);   // navigate to day-after
    const btn = screen.getByTestId('book-btn-31');
    expect(btn).not.toBeDisabled();
  });
});

describe('BookingPage — club member before 4pm', () => {
  beforeEach(() => {
    currentUser.memberType = 'RECREATIONAL';
    currentUser.isFinishedBasicTraining = true;
    freezeTime(9);
  });

  test('does not show the after-4pm banner', async () => {
    await renderInDayView();
    expect(screen.queryByText(/tomorrow's sessions are reserved for students/i)).not.toBeInTheDocument();
  });

  test("allows booking tomorrow's session", async () => {
    await renderInDayView();
    await advanceDays(1);
    expect(screen.getByTestId('book-btn-21')).not.toBeDisabled();
  });

  test('allows booking day-after sessions', async () => {
    await renderInDayView();
    await advanceDays(2);
    expect(screen.getByTestId('book-btn-31')).not.toBeDisabled();
  });
});

describe('BookingPage — student before 4pm', () => {
  beforeEach(() => {
    currentUser.memberType = 'STUDENT';
    currentUser.isFinishedBasicTraining = true;
    freezeTime(9);
  });

  test('shows the students-only-after-4pm banner', async () => {
    await renderInDayView();
    expect(screen.getByText(/students can only book after 4:00 pm/i)).toBeInTheDocument();
  });

  test("disables the book button on tomorrow's session", async () => {
    await renderInDayView();
    await advanceDays(1);
    expect(screen.getByTestId('book-btn-21')).toBeDisabled();
  });
});
