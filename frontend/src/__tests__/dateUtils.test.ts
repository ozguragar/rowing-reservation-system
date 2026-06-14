import { fmt, tomorrowStr, getWeekDates, endOfDay, toDateInput, seatCount } from '@/lib/dateUtils';
import { Session } from '@/types';

const FIXED_DATE = new Date('2026-04-17T10:00:00Z');

beforeEach(() => {
  jest.useFakeTimers();
  jest.setSystemTime(FIXED_DATE);
});

afterEach(() => {
  jest.useRealTimers();
});

describe('fmt', () => {
  test('formats date as YYYY-MM-DD', () => {
    expect(fmt(new Date('2026-04-17T10:00:00Z'))).toBe('2026-04-17');
  });

  test('formats different date correctly', () => {
    expect(fmt(new Date('2026-12-31T00:00:00Z'))).toBe('2026-12-31');
  });
});

describe('tomorrowStr', () => {
  test('returns tomorrow in YYYY-MM-DD', () => {
    const result = tomorrowStr();
    const tomorrow = new Date(FIXED_DATE);
    tomorrow.setDate(tomorrow.getDate() + 1);
    expect(result).toBe(tomorrow.toISOString().split('T')[0]);
  });
});

describe('getWeekDates', () => {
  test('returns 7 dates', () => {
    const dates = getWeekDates(new Date('2026-04-17'));
    expect(dates).toHaveLength(7);
  });

  test('starts on Monday', () => {
    const dates = getWeekDates(new Date('2026-04-17')); // Friday
    expect(dates[0].getDay()).toBe(1); // Monday
  });

  test('ends on Sunday', () => {
    const dates = getWeekDates(new Date('2026-04-17'));
    expect(dates[6].getDay()).toBe(0); // Sunday
  });

  test('consecutive dates', () => {
    const dates = getWeekDates(new Date('2026-04-17'));
    for (let i = 1; i < 7; i++) {
      expect(dates[i].getTime() - dates[i - 1].getTime()).toBe(24 * 60 * 60 * 1000);
    }
  });

  test('starting from Sunday returns correct Monday', () => {
    const dates = getWeekDates(new Date('2026-04-19')); // Sunday
    expect(dates[0].getDay()).toBe(1);
    expect(fmt(dates[0])).toBe('2026-04-13');
  });
});

describe('endOfDay', () => {
  test('appends T23:59:00 to date string', () => {
    expect(endOfDay('2026-04-30')).toBe('2026-04-30T23:59:00');
  });

  test('returns null for falsy input', () => {
    expect(endOfDay(null)).toBeNull();
    expect(endOfDay('')).toBeNull();
    expect(endOfDay(undefined)).toBeNull();
  });

  test('passes through ISO datetime unchanged', () => {
    expect(endOfDay('2026-04-30T12:00:00')).toBe('2026-04-30T12:00:00');
  });
});

describe('toDateInput', () => {
  test('extracts date part from ISO datetime', () => {
    expect(toDateInput('2026-04-30T23:59:00')).toBe('2026-04-30');
  });

  test('returns empty string for null', () => {
    expect(toDateInput(null)).toBe('');
    expect(toDateInput(undefined)).toBe('');
  });

  test('returns date unchanged if no T', () => {
    expect(toDateInput('2026-04-30')).toBe('2026-04-30');
  });
});

describe('seatCount', () => {
  const makeSession = (boats: { currentBookings: number; capacity: number }[]): Session => ({
    id: 1, date: '2026-04-17', startTime: '08:00:00', endTime: '09:00:00', status: 'APPROVED',
    boats: boats.map((b, i) => ({
      id: i + 1, sessionId: 1, type: 'COASTAL', capacity: b.capacity,
      isBasicTrainingBoat: false, currentBookings: b.currentBookings, version: 0, name: 'Boat',
    })),
  });

  test('sums booked and capacity across boats', () => {
    const s = makeSession([{ currentBookings: 2, capacity: 4 }, { currentBookings: 1, capacity: 2 }]);
    expect(seatCount(s)).toEqual({ booked: 3, capacity: 6 });
  });

  test('returns zeros when no boats', () => {
    const s: Session = { id: 1, date: '2026-04-17', startTime: '08:00', endTime: '09:00', status: 'APPROVED', boats: [] };
    expect(seatCount(s)).toEqual({ booked: 0, capacity: 0 });
  });
});
