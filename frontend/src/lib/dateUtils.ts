import { Session } from '@/types';

export function fmt(d: Date): string {
  return d.toISOString().split('T')[0];
}

export function tomorrowStr(): string {
  const t = new Date();
  t.setDate(t.getDate() + 1);
  return fmt(t);
}

export function getWeekDates(date: Date): Date[] {
  const start = new Date(date);
  const day = start.getDay();
  start.setDate(start.getDate() - (day === 0 ? 6 : day - 1));
  return Array.from({ length: 7 }, (_, i) => {
    const d = new Date(start);
    d.setDate(d.getDate() + i);
    return d;
  });
}

export function endOfDay(dateStr: string | null | undefined): string | null {
  if (!dateStr) return null;
  return dateStr.includes('T') ? dateStr : `${dateStr}T23:59:00`;
}

export function toDateInput(isoDateTime: string | null | undefined): string {
  if (!isoDateTime) return '';
  return isoDateTime.split('T')[0];
}

export function seatCount(session: Session): { booked: number; capacity: number } {
  const booked = session.boats?.reduce((a, b) => a + b.currentBookings, 0) || 0;
  const capacity = session.boats?.reduce((a, b) => a + b.capacity, 0) || 0;
  return { booked, capacity };
}
