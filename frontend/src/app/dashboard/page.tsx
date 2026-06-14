'use client';

import { useEffect, useState } from 'react';
import ProtectedRoute from '@/components/ProtectedRoute';
import { useAuth } from '@/context/AuthContext';
import api from '@/lib/api';
import { Booking } from '@/types';
import { useDialog } from '@/context/DialogContext';
import { Wallet, GraduationCap, CalendarDays, Clock, X } from 'lucide-react';

export default function DashboardPage() {
  const { user } = useAuth();
  const { toast, confirm } = useDialog();
  const [balance, setBalance] = useState(0);
  const [expiration, setExpiration] = useState<string | null>(null);
  const [bookings, setBookings] = useState<Booking[]>([]);
  const [lessonsAttended, setLessonsAttended] = useState(0);

  useEffect(() => {
    loadData();
  }, []);

  async function loadData() {
    try {
      const [balRes, bookRes, userRes] = await Promise.all([
        api.get('/ledger/balance'),
        api.get('/bookings/my'),
        api.get('/users/me'),
      ]);
      setBalance(balRes.data.balance);
      setExpiration(balRes.data.expirationDate !== 'none' ? balRes.data.expirationDate : null);
      setBookings(bookRes.data);
      setLessonsAttended(userRes.data.lessonsAttended);
    } catch (e) {
      console.error(e);
    }
  }

  async function cancelBooking(id: number) {
    if (!await confirm({ title: 'Request cancellation', message: 'Request cancellation? Your credit will be refunded only after an admin approves.', confirmLabel: 'Request', danger: true })) return;
    try {
      await api.delete(`/bookings/${id}`);
      loadData();
      toast('Cancellation request submitted', 'success');
    } catch (e: any) {
      toast(e.response?.data?.message || 'Failed to request cancellation');
    }
  }

  const isAdmin = user?.role === 'CLUB_ADMIN' || user?.role === 'SUPERADMIN' || user?.role === 'TRAINER';

  return (
    <ProtectedRoute>
      <div className="space-y-6">
        <div>
          <h1 className="text-2xl font-bold text-gray-900 dark:text-gray-100">
            {isAdmin ? 'Admin Dashboard' : 'My Dashboard'}
          </h1>
          <p className="text-gray-500 dark:text-gray-400">Welcome back, {user?.fullName}</p>
        </div>

        <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
          <a href="/ledger" className="card flex items-center gap-4 hover:shadow-md hover:border-primary-200 transition-all cursor-pointer">
            <div className="p-3 bg-primary-50 dark:bg-primary-900/30 rounded-lg">
              <Wallet className="text-primary-600 dark:text-primary-400" size={24} />
            </div>
            <div>
              <p className="text-sm text-gray-500 dark:text-gray-400">Credit Balance</p>
              <p className="text-2xl font-bold text-gray-900 dark:text-gray-100">{balance}</p>
              {expiration && (
                <p className="text-xs text-orange-500 dark:text-orange-400">
                  Expires: {new Date(expiration).toLocaleDateString()}
                </p>
              )}
              <p className="text-xs text-primary-600 dark:text-primary-400 mt-1">View ledger →</p>
            </div>
          </a>

          <div className="card flex items-center gap-4">
            <div className="p-3 bg-accent-50 dark:bg-green-900/30 rounded-lg">
              <GraduationCap className="text-accent-600 dark:text-green-400" size={24} />
            </div>
            <div>
              <p className="text-sm text-gray-500 dark:text-gray-400">Lessons Attended</p>
              <p className="text-2xl font-bold text-gray-900 dark:text-gray-100">{lessonsAttended}</p>
              <p className="text-xs text-gray-400 dark:text-gray-500">
                {user?.isFinishedBasicTraining ? 'Basic training completed' : 'In basic training'}
              </p>
            </div>
          </div>

          <div className="card flex items-center gap-4">
            <div className="p-3 bg-purple-50 dark:bg-purple-900/30 rounded-lg">
              <CalendarDays className="text-purple-600 dark:text-purple-400" size={24} />
            </div>
            <div>
              <p className="text-sm text-gray-500 dark:text-gray-400">Upcoming Bookings</p>
              <p className="text-2xl font-bold text-gray-900 dark:text-gray-100">{bookings.length}</p>
            </div>
          </div>
        </div>

          {isAdmin && (
            <div className="card bg-purple-50 dark:bg-purple-900/30 border-purple-200 dark:border-purple-800">
              <h3 className="font-semibold text-purple-900 dark:text-purple-200 mb-2">Admin Quick Actions</h3>
              <div className="flex flex-wrap gap-2">
                <a href="/admin/planner" className="btn-primary text-sm">Daily Planner</a>
                {user?.featureCancellationRequests !== false && (
                  <a href="/admin/cancellations" className="btn-secondary text-sm">Cancellations</a>
                )}
                <a href="/admin/analytics" className="btn-secondary text-sm">Analytics</a>
                <a href="/admin/logs" className="btn-secondary text-sm">System Logs</a>
                <a href="/admin/ledger" className="btn-secondary text-sm">Manage Ledger</a>
                {user?.featureAutoScheduler !== false && (
                  <a href="/admin/scheduler" className="btn-secondary text-sm">Auto-Scheduler</a>
                )}
              </div>
            </div>
          )}

        <div>
          <h2 className="text-lg font-semibold text-gray-900 dark:text-gray-100 mb-4">Upcoming Bookings</h2>
          {bookings.length === 0 ? (
            <div className="card text-center text-gray-500 dark:text-gray-400 py-12">
              <CalendarDays size={48} className="mx-auto mb-3 text-gray-300 dark:text-gray-600" />
              <p>No upcoming bookings</p>
              <a href="/booking" className="text-primary-600 dark:text-primary-400 hover:underline text-sm mt-1 inline-block">
                Browse sessions to book
              </a>
            </div>
          ) : (
            <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
              {bookings.map((b) => {
                const pending = b.status === 'CANCELLATION_REQUESTED';
                return (
                <div key={b.id} className="card hover:shadow-md transition-shadow">
                  <div className="flex justify-between items-start">
                    <div>
                      <div className="flex items-center gap-2 mb-1">
                        <Clock size={14} className="text-gray-400 dark:text-gray-500" />
                        <span className="text-sm font-medium text-gray-900 dark:text-gray-100">
                          {b.createdAt ? new Date(b.createdAt).toLocaleDateString() : 'N/A'}
                        </span>
                      </div>
                      <p className="text-sm text-gray-600 dark:text-gray-400">Boat: {b.boatName}</p>
                      <span className={`inline-block mt-2 px-2 py-0.5 rounded-full text-xs font-medium ${pending ? 'bg-amber-100 text-amber-700 dark:text-amber-300 dark:bg-amber-900/40 dark:text-amber-200' : 'bg-blue-100 text-blue-700 dark:text-blue-300 dark:bg-blue-900/40 dark:text-blue-200'}`}>
                        {pending ? 'Cancellation pending admin' : b.status.replace('_', ' ')}
                      </span>
                    </div>
                    {user?.featureCancellationRequests !== false && (
                      <button
                        onClick={() => cancelBooking(b.id)}
                        disabled={pending}
                        className="text-gray-400 dark:text-gray-500 hover:text-red-500 hover:dark:text-red-400 disabled:opacity-40 disabled:cursor-not-allowed transition-colors p-1"
                        title={pending ? 'Already pending admin approval' : 'Request cancellation'}
                      >
                        <X size={18} />
                      </button>
                    )}
                  </div>
                </div>
                );
              })}
            </div>
          )}
        </div>
      </div>
    </ProtectedRoute>
  );
}
