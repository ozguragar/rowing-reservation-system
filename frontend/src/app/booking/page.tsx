'use client';

import { useEffect, useState, useCallback } from 'react';
import ProtectedRoute from '@/components/ProtectedRoute';
import { useAuth } from '@/context/AuthContext';
import api from '@/lib/api';
import { Session, Boat } from '@/types';
import { useDialog } from '@/context/DialogContext';
import { fmt as formatDate, tomorrowStr, getWeekDates, seatCount } from '@/lib/dateUtils';
import { ChevronLeft, ChevronRight, ChevronDown, ChevronUp, Calendar, Users, Lock, AlertTriangle } from 'lucide-react';
import clsx from 'clsx';

export default function BookingPage() {
  const { user } = useAuth();
  const { toast } = useDialog();
  const [sessions, setSessions] = useState<Session[]>([]);
  const [viewMode, setViewMode] = useState<'week' | 'day'>('week');
  const [currentDate, setCurrentDate] = useState(new Date());
  const [selectedSession, setSelectedSession] = useState<Session | null>(null);
  const [confirmBoat, setConfirmBoat] = useState<Boat | null>(null);
  const [loading, setLoading] = useState(false);
  const [bookingLoading, setBookingLoading] = useState(false);
  const [collapsed, setCollapsed] = useState<Set<number>>(new Set());
  const [nextDayOnly, setNextDayOnly] = useState(false);

  function toggleCollapse(id: number) {
    setCollapsed(prev => {
      const next = new Set(prev);
      if (next.has(id)) next.delete(id); else next.add(id);
      return next;
    });
  }

  const loadSessions = useCallback(async () => {
    setLoading(true);
    try {
      const res = await api.get('/sessions/upcoming');
      setSessions(res.data);
    } catch (e) {
      console.error(e);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => { loadSessions(); }, [loadSessions]);

  useEffect(() => {
    api.get('/settings/public')
      .then(res => setNextDayOnly(res.data.student_next_day_only === 'true'))
      .catch(() => {});
  }, []);

  const now = new Date();
  const isPast4pm = now.getHours() >= 16;
  const isStudent = user?.role === 'STUDENT';
  const isClubMember = user?.role === 'CLUB_MEMBER';
  // Students: blocked before 4pm. Members: after 4pm, tomorrow's sessions are
  // reserved for students; before 4pm, members have no time-of-day restriction.
  const isStudentBlocked = isStudent && !isPast4pm;
  const memberBlockedFromTomorrow = isClubMember && isPast4pm;

  function formatDateDisplay(d: Date) {
    return d.toISOString().split('T')[0];
  }

  const weekDates = getWeekDates(currentDate);
  const displaySessions = viewMode === 'day'
    ? sessions.filter(s => s.date === formatDate(currentDate))
    : sessions.filter(s => weekDates.some(d => formatDate(d) === s.date));

  function navigateDate(dir: number) {
    const d = new Date(currentDate);
    d.setDate(d.getDate() + (viewMode === 'week' ? 7 * dir : dir));
    setCurrentDate(d);
  }

  async function handleBook(boat: Boat, sessionId: number) {
    setBookingLoading(true);
    try {
      await api.post('/bookings', { boatId: boat.id, sessionId });
      setConfirmBoat(null);
      setSelectedSession(null);
      loadSessions();
    } catch (e: any) {
      toast(e.response?.data?.message || 'Booking failed');
    } finally {
      setBookingLoading(false);
    }
  }

  const canBook = (boat: Boat, sessionDate?: string) => {
    if (isStudentBlocked) return false;
    if (!user?.isFinishedBasicTraining && !boat.isBasicTrainingBoat) return false;
    if (boat.currentBookings >= boat.capacity) return false;
    if (isStudent && nextDayOnly && sessionDate && sessionDate !== tomorrowStr()) return false;
    if (memberBlockedFromTomorrow && sessionDate === tomorrowStr()) return false;
    return true;
  };

  const sessionBlockedByNextDay = (sessionDate: string) =>
    isStudent && nextDayOnly && sessionDate !== tomorrowStr();

  return (
    <ProtectedRoute>
      <div className="space-y-4">
        <div className="flex items-center justify-between">
          <h1 className="text-2xl font-bold text-gray-900 dark:text-gray-100">RSVP & Booking</h1>
          <div className="flex items-center gap-2">
            <button onClick={() => setViewMode('week')}
              className={clsx('px-3 py-1.5 rounded-lg text-sm font-medium', viewMode === 'week' ? 'bg-primary-600 text-white' : 'bg-gray-100 dark:bg-gray-800 text-gray-600 dark:text-gray-400')}>
              Week
            </button>
            <button onClick={() => setViewMode('day')}
              className={clsx('px-3 py-1.5 rounded-lg text-sm font-medium', viewMode === 'day' ? 'bg-primary-600 text-white' : 'bg-gray-100 dark:bg-gray-800 text-gray-600 dark:text-gray-400')}>
              Day
            </button>
          </div>
        </div>

        {isStudentBlocked && (
          <div className="bg-amber-50 dark:bg-amber-900/30 border border-amber-200 dark:border-amber-800 rounded-lg p-4 flex items-center gap-3">
            <AlertTriangle className="text-amber-500 dark:text-amber-400" size={20} />
            <p className="text-sm text-amber-700 dark:text-amber-300">
              Students can only book after 4:00 PM. Current time restriction is active.
            </p>
          </div>
        )}

        {isStudent && nextDayOnly && (
          <div className="bg-amber-50 dark:bg-amber-900/30 border border-amber-200 dark:border-amber-800 rounded-lg p-4 flex items-center gap-3">
            <AlertTriangle className="text-amber-500 dark:text-amber-400" size={20} />
            <p className="text-sm text-amber-700 dark:text-amber-300">
              Students can only book sessions for tomorrow while the next-day-only restriction is active.
              Other days are view-only.
            </p>
          </div>
        )}

        {memberBlockedFromTomorrow && (
          <div className="bg-amber-50 dark:bg-amber-900/30 border border-amber-200 dark:border-amber-800 rounded-lg p-4 flex items-center gap-3">
            <AlertTriangle className="text-amber-500 dark:text-amber-400" size={20} />
            <p className="text-sm text-amber-700 dark:text-amber-300">
              After 4:00 PM, tomorrow&apos;s sessions are reserved for students. Pick a different day or book tomorrow&apos;s before 4:00 PM.
            </p>
          </div>
        )}

        <div className="flex items-center justify-between">
          <button onClick={() => navigateDate(-1)} className="p-2 hover:bg-gray-100 hover:dark:bg-gray-800 rounded-lg">
            <ChevronLeft size={20} />
          </button>
          <h2 className="text-lg font-semibold">
            {viewMode === 'week'
              ? `${weekDates[0].toLocaleDateString('en-US', { month: 'short', day: 'numeric' })} — ${weekDates[6].toLocaleDateString('en-US', { month: 'short', day: 'numeric', year: 'numeric' })}`
              : currentDate.toLocaleDateString('en-US', { weekday: 'long', month: 'long', day: 'numeric', year: 'numeric' })}
          </h2>
          <button onClick={() => navigateDate(1)} className="p-2 hover:bg-gray-100 hover:dark:bg-gray-800 rounded-lg">
            <ChevronRight size={20} />
          </button>
        </div>

        {loading ? (
          <div className="flex justify-center py-12">
            <div className="animate-spin rounded-full h-8 w-8 border-b-2 border-primary-600"></div>
          </div>
        ) : viewMode === 'week' ? (
          <div key={`week-${formatDate(weekDates[0])}`} className="animate-page-enter grid grid-cols-2 sm:grid-cols-4 md:grid-cols-7 gap-2">
            {weekDates.map(date => {
              const dateStr = formatDate(date);
              const daySessions = displaySessions.filter(s => s.date === dateStr);
              const isToday = formatDate(new Date()) === dateStr;
              return (
                <div key={dateStr} className={clsx('min-h-[160px] md:min-h-[200px] rounded-lg border p-2', isToday ? 'border-primary-300 bg-primary-50/30' : 'border-gray-200 dark:border-gray-700')}>
                  <p className={clsx('text-xs font-medium mb-2', isToday ? 'text-primary-700 dark:text-primary-300' : 'text-gray-500 dark:text-gray-400')}>
                    {date.toLocaleDateString('en-US', { weekday: 'short', day: 'numeric' })}
                  </p>
                  {daySessions.map(session => {
                    const { booked, capacity } = seatCount(session);
                    const isFull = capacity > 0 && booked >= capacity;
                    return (
                      <button key={session.id} onClick={() => { setSelectedSession(session); if (viewMode === 'week') { setCurrentDate(date); setViewMode('day'); }}}
                        className="w-full text-left p-2 mb-1 rounded bg-white dark:bg-gray-800 border border-gray-100 dark:border-gray-700 hover:border-primary-300 hover:shadow-sm transition-all text-xs">
                        <p className="font-medium text-gray-800 dark:text-gray-200">{session.startTime?.substring(0, 5)}</p>
                        <p className="text-gray-400 dark:text-gray-500">{session.boats?.length || 0} boats</p>
                        <p className={clsx('flex items-center gap-1 mt-0.5', isFull ? 'text-red-500 dark:text-red-400' : 'text-gray-500 dark:text-gray-400')}>
                          <Users size={10} /> {booked}/{capacity}
                        </p>
                      </button>
                    );
                  })}
                  {daySessions.length === 0 && <p className="text-xs text-gray-300 dark:text-gray-600 text-center mt-8">No sessions</p>}
                </div>
              );
            })}
          </div>
        ) : (
          <div key={`day-${formatDate(currentDate)}`} className="animate-page-enter space-y-4">
            {displaySessions.length === 0 && (
              <div className="card text-center py-12 text-gray-500 dark:text-gray-400">No sessions available for this day</div>
            )}
            {displaySessions.length > 0 && (
              <div className="flex justify-end">
                <button
                  onClick={() => setCollapsed(displaySessions.every(s => collapsed.has(s.id)) ? new Set() : new Set(displaySessions.map(s => s.id)))}
                  className="text-xs text-gray-500 dark:text-gray-400 hover:text-gray-700 flex items-center gap-1"
                >
                  {displaySessions.every(s => collapsed.has(s.id)) ? <ChevronDown size={12} /> : <ChevronUp size={12} />}
                  {displaySessions.every(s => collapsed.has(s.id)) ? 'Expand all' : 'Collapse all'}
                </button>
              </div>
            )}
            {displaySessions.map(session => {
              const isCollapsed = collapsed.has(session.id);
              const { booked, capacity } = seatCount(session);
              return (
              <div key={session.id} className="card">
                <button
                  onClick={() => toggleCollapse(session.id)}
                  className="w-full flex items-center justify-between gap-2 mb-4 text-left hover:bg-gray-50 hover:dark:bg-gray-900 -m-2 p-2 rounded transition-colors"
                >
                  <div className="flex items-center gap-2 sm:gap-3 flex-1 min-w-0 flex-wrap">
                    <div className="flex items-center gap-2">
                      <Calendar size={18} className="text-primary-500 shrink-0" />
                      <h3 className="font-semibold text-gray-900 dark:text-gray-100 text-sm sm:text-base">
                        {session.startTime?.substring(0, 5)} — {session.endTime?.substring(0, 5)}
                      </h3>
                    </div>
                    <span className="flex items-center gap-1 text-xs text-gray-500 dark:text-gray-400">
                      <Users size={12} />
                      <span className={booked >= capacity && capacity > 0 ? 'text-red-500 font-medium' : ''}>
                        {booked}/{capacity} seats
                      </span>
                    </span>
                    {isCollapsed && (
                      <span className="text-xs text-gray-400 dark:text-gray-500">· {session.boats?.length || 0} boats</span>
                    )}
                  </div>
                  <div className="flex items-center gap-2 shrink-0">
                    <span className="hidden sm:inline-block px-2 py-0.5 rounded-full text-xs font-medium bg-green-100 dark:bg-green-900/40 text-green-700 dark:text-green-300">
                      {session.status}
                    </span>
                    {isCollapsed ? <ChevronDown size={16} className="text-gray-400 dark:text-gray-500" /> : <ChevronUp size={16} className="text-gray-400 dark:text-gray-500" />}
                  </div>
                </button>
                {!isCollapsed && (
                <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-3">
                  {session.boats?.map(boat => {
                    const isFull = boat.currentBookings >= boat.capacity;
                    const isDisabled = !canBook(boat, session.date);
                    const isGrayed = !user?.isFinishedBasicTraining && !boat.isBasicTrainingBoat;
                    const isNextDayBlocked = sessionBlockedByNextDay(session.date);
                    return (
                      <div key={boat.id}
                        className={clsx('p-4 rounded-lg border transition-all',
                          isGrayed ? 'bg-gray-100 dark:bg-gray-800 border-gray-200 dark:border-gray-700 opacity-60' :
                          isFull ? 'bg-red-50 dark:bg-red-900/30 border-red-200 dark:border-red-800' :
                          'bg-white dark:bg-gray-800 border-gray-200 dark:border-gray-700 hover:border-primary-300 hover:shadow-sm')}>
                        <div className="flex items-center justify-between mb-2">
                          <span className="font-medium text-sm text-gray-800 dark:text-gray-200">{boat.name}</span>
                          {boat.isBasicTrainingBoat && (
                            <span className="px-1.5 py-0.5 bg-amber-100 dark:bg-amber-900/40 text-amber-700 dark:text-amber-300 text-xs rounded">Beginner</span>
                          )}
                        </div>
                        <div className="flex items-center gap-1 mb-3 text-sm text-gray-500 dark:text-gray-400">
                          <Users size={14} />
                          <span>{boat.currentBookings}/{boat.capacity}</span>
                          <span className="mx-1">·</span>
                          <span>{boat.type}</span>
                        </div>
                        {boat.bookings && boat.bookings.length > 0 && (
                          <div className="mb-3 space-y-1">
                            {boat.bookings.map(bk => (
                              <div key={bk.id} className="flex items-center gap-1.5 text-xs text-gray-500 dark:text-gray-400">
                                <span className={bk.userRole === 'STUDENT' ? 'badge-student' : 'badge-member'}>
                                  {bk.userRole === 'STUDENT' ? 'S' : 'M'}
                                </span>
                                <span>{bk.userFullName}</span>
                              </div>
                            ))}
                          </div>
                        )}
                        <button
                          disabled={isDisabled}
                          onClick={() => setConfirmBoat(boat)}
                          data-testid={`book-btn-${boat.id}`}
                          className={clsx('w-full py-2 rounded-lg text-sm font-medium transition-colors',
                            isDisabled ? 'bg-gray-200 dark:bg-gray-700 text-gray-400 dark:text-gray-500 cursor-not-allowed' : 'btn-primary')}>
                          {isFull
                            ? 'Full'
                            : isNextDayBlocked
                              ? <span className="flex items-center justify-center gap-1"><Lock size={12}/>Tomorrow only</span>
                              : isGrayed
                                ? <span className="flex items-center justify-center gap-1"><Lock size={12}/>Training Required</span>
                                : 'Book Seat'}
                        </button>
                      </div>
                    );
                  })}
                </div>
                )}
              </div>
              );
            })}
          </div>
        )}

        {confirmBoat && selectedSession === null && displaySessions.length > 0 && (
          <ConfirmModal boat={confirmBoat} sessionId={displaySessions.find(s => s.boats?.some(b => b.id === confirmBoat.id))?.id || 0}
            onConfirm={handleBook} onClose={() => setConfirmBoat(null)} loading={bookingLoading} />
        )}
        {confirmBoat && (
          <ConfirmModal boat={confirmBoat}
            sessionId={displaySessions.find(s => s.boats?.some(b => b.id === confirmBoat.id))?.id || 0}
            onConfirm={handleBook} onClose={() => setConfirmBoat(null)} loading={bookingLoading} />
        )}
      </div>
    </ProtectedRoute>
  );
}

function ConfirmModal({ boat, sessionId, onConfirm, onClose, loading }: {
  boat: Boat; sessionId: number; onConfirm: (boat: Boat, sessionId: number) => void;
  onClose: () => void; loading: boolean;
}) {
  return (
    <div className="fixed inset-0 bg-black/40 flex items-center justify-center z-50" onClick={onClose}>
      <div className="bg-white dark:bg-gray-800 rounded-xl shadow-xl p-6 max-w-sm w-full mx-4" onClick={e => e.stopPropagation()}>
        <h3 className="text-lg font-semibold text-gray-900 dark:text-gray-100 mb-2">Confirm Booking</h3>
        <p className="text-sm text-gray-600 dark:text-gray-400 mb-4">
          Book a seat on <strong>{boat.name}</strong>?
          <br />1 credit will be deducted from your balance.
        </p>
        <div className="flex gap-3">
          <button onClick={onClose} className="btn-secondary flex-1">Cancel</button>
          <button onClick={() => onConfirm(boat, sessionId)} disabled={loading} className="btn-primary flex-1">
            {loading ? 'Booking...' : 'Confirm'}
          </button>
        </div>
      </div>
    </div>
  );
}
