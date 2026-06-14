'use client';

import { useEffect, useState, useCallback } from 'react';
import { useRouter } from 'next/navigation';
import ProtectedRoute from '@/components/ProtectedRoute';
import api from '@/lib/api';
import { Session } from '@/types';
import { useDialog } from '@/context/DialogContext';
import { useSettings } from '@/context/SettingsContext';
import { ChevronLeft, ChevronRight, Check, Calendar } from 'lucide-react';
import clsx from 'clsx';

export default function AvailabilityPage() {
  const router = useRouter();
  const { settings, loaded } = useSettings();
  useEffect(() => {
    if (loaded && settings.disable_availability === 'true') router.replace('/dashboard');
  }, [loaded, settings, router]);
  const [sessions, setSessions] = useState<Session[]>([]);
  const [myAvailability, setMyAvailability] = useState<number[]>([]);
  const [weekStart, setWeekStart] = useState(() => {
    const d = new Date();
    d.setDate(d.getDate() - d.getDay() + 1);
    return d;
  });
  const [loading, setLoading] = useState(true);
  const { toast } = useDialog();

  const loadData = useCallback(async () => {
    setLoading(true);
    try {
      const dateStr = weekStart.toISOString().split('T')[0];
      const [sessRes, availRes] = await Promise.all([
        api.get(`/availability/week?weekStart=${dateStr}`),
        api.get('/availability/my'),
      ]);
      setSessions(sessRes.data);
      setMyAvailability(availRes.data);
    } catch (e) {
      console.error(e);
    } finally {
      setLoading(false);
    }
  }, [weekStart]);

  useEffect(() => { loadData(); }, [loadData]);

  async function toggleAvailability(sessionId: number) {
    try {
      if (myAvailability.includes(sessionId)) {
        await api.delete(`/availability/${sessionId}`);
        setMyAvailability(prev => prev.filter(id => id !== sessionId));
      } else {
        await api.post(`/availability/${sessionId}`);
        setMyAvailability(prev => [...prev, sessionId]);
      }
    } catch (e: any) {
      toast(e.response?.data?.message || 'Failed to update availability');
    }
  }

  function navigateWeek(dir: number) {
    const d = new Date(weekStart);
    d.setDate(d.getDate() + 7 * dir);
    setWeekStart(d);
  }

  const weekDates = Array.from({ length: 7 }, (_, i) => {
    const d = new Date(weekStart);
    d.setDate(d.getDate() + i);
    return d;
  });

  return (
    <ProtectedRoute>
      <div className="space-y-6">
        <div>
          <h1 className="text-2xl font-bold text-gray-900 dark:text-gray-100">Weekly Availability</h1>
          <p className="text-gray-500 dark:text-gray-400">Select which sessions you&apos;re available to row</p>
        </div>

        <div className="flex items-center justify-between">
          <button onClick={() => navigateWeek(-1)} className="p-2 hover:bg-gray-100 hover:dark:bg-gray-800 rounded-lg">
            <ChevronLeft size={20} />
          </button>
          <h2 className="text-lg font-semibold">
            {weekDates[0].toLocaleDateString('en-US', { month: 'short', day: 'numeric' })} — {weekDates[6].toLocaleDateString('en-US', { month: 'short', day: 'numeric', year: 'numeric' })}
          </h2>
          <button onClick={() => navigateWeek(1)} className="p-2 hover:bg-gray-100 hover:dark:bg-gray-800 rounded-lg">
            <ChevronRight size={20} />
          </button>
        </div>

        {loading ? (
          <div className="flex justify-center py-12">
            <div className="animate-spin rounded-full h-8 w-8 border-b-2 border-primary-600"></div>
          </div>
        ) : (
          <div className="grid grid-cols-7 gap-2">
            {weekDates.map(date => {
              const dateStr = date.toISOString().split('T')[0];
              const daySessions = sessions.filter(s => s.date === dateStr);
              const isToday = new Date().toISOString().split('T')[0] === dateStr;
              return (
                <div key={dateStr} className={clsx('min-h-[200px] rounded-lg border p-2', isToday ? 'border-primary-300 bg-primary-50/30' : 'border-gray-200 dark:border-gray-700')}>
                  <p className={clsx('text-xs font-medium mb-2', isToday ? 'text-primary-700 dark:text-primary-300' : 'text-gray-500 dark:text-gray-400')}>
                    {date.toLocaleDateString('en-US', { weekday: 'short', day: 'numeric' })}
                  </p>
                  {daySessions.map(session => {
                    const isAvailable = myAvailability.includes(session.id);
                    return (
                      <button key={session.id} onClick={() => toggleAvailability(session.id)}
                        className={clsx('w-full text-left p-2 mb-1 rounded border text-xs transition-all',
                          isAvailable ? 'bg-green-50 dark:bg-green-900/30 border-green-300 text-green-700 dark:text-green-300' : 'bg-white dark:bg-gray-800 border-gray-100 dark:border-gray-700 text-gray-600 dark:text-gray-400 hover:border-gray-300 hover:dark:border-gray-600')}>
                        <div className="flex items-center justify-between">
                          <span className="font-medium">{session.startTime?.substring(0, 5)}</span>
                          {isAvailable && <Check size={12} className="text-green-600 dark:text-green-400" />}
                        </div>
                      </button>
                    );
                  })}
                  {daySessions.length === 0 && <p className="text-xs text-gray-300 dark:text-gray-600 text-center mt-8">No sessions</p>}
                </div>
              );
            })}
          </div>
        )}

        <div className="card bg-blue-50 dark:bg-blue-900/30 border-blue-200 dark:border-blue-800">
          <div className="flex items-start gap-3">
            <Calendar className="text-blue-500 dark:text-blue-400 mt-0.5" size={18} />
            <div className="text-sm text-blue-700 dark:text-blue-300">
              <p className="font-medium">About Auto-Scheduling</p>
              <p className="mt-1">When the admin runs the auto-scheduler, it uses your availability to assign you to 4-person boats. Make sure to mark all sessions you can attend.</p>
            </div>
          </div>
        </div>
      </div>
    </ProtectedRoute>
  );
}
