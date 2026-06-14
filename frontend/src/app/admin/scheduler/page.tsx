'use client';

import { useState } from 'react';
import ProtectedRoute from '@/components/ProtectedRoute';
import api from '@/lib/api';
import { Zap, CheckCircle, Users } from 'lucide-react';

export default function SchedulerPage() {
  const [weekStart, setWeekStart] = useState(() => {
    const d = new Date();
    d.setDate(d.getDate() - d.getDay() + 1);
    return d.toISOString().split('T')[0];
  });
  const [result, setResult] = useState<any>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');

  async function runScheduler() {
    setLoading(true);
    setError('');
    setResult(null);
    try {
      const res = await api.post(`/admin/scheduler/run?weekStart=${weekStart}`);
      setResult(res.data);
    } catch (e: any) {
      setError(e.response?.data?.message || 'Scheduler failed');
    } finally {
      setLoading(false);
    }
  }

  return (
    <ProtectedRoute adminOnly>
      <div className="space-y-6">
        <h1 className="text-2xl font-bold text-gray-900 dark:text-gray-100">Auto-Scheduler</h1>

        <div className="card">
          <h2 className="font-semibold text-gray-900 dark:text-gray-100 mb-2">How it works</h2>
          <ul className="text-sm text-gray-600 dark:text-gray-400 space-y-1 list-disc list-inside">
            <li>Uses user availability selections to auto-assign people to 4-person coastal boats</li>
            <li>Only uses 4-person boats; ignores 1, 2-person and olympic boats</li>
            <li>Minimum crew: 3 people. Will not create boats with 1 or 2 people</li>
            <li>Students and club members are never mixed in the same boat</li>
            <li>1 credit is deducted per assignment</li>
            <li>Notifications are sent to assigned users</li>
          </ul>
        </div>

        <div className="card">
          <h2 className="font-semibold text-gray-900 dark:text-gray-100 mb-4">Run Scheduler</h2>
          <div className="flex items-end gap-4">
            <div>
              <label className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">Week Starting</label>
              <input type="date" value={weekStart} onChange={e => setWeekStart(e.target.value)} className="input" />
            </div>
            <button onClick={runScheduler} disabled={loading}
              className="btn-primary flex items-center gap-2">
              <Zap size={16} />
              {loading ? 'Running...' : 'Run Auto-Scheduler'}
            </button>
          </div>
        </div>

        {error && (
          <div className="bg-red-50 dark:bg-red-900/30 text-red-700 dark:text-red-300 px-4 py-3 rounded-lg text-sm">{error}</div>
        )}

        {result && (
          <div className="space-y-4">
            <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
              <div className="card flex items-center gap-3">
                <CheckCircle className="text-green-500 dark:text-green-400" size={20} />
                <div>
                  <p className="text-sm text-gray-500 dark:text-gray-400">Total Assigned</p>
                  <p className="text-2xl font-bold">{result.totalAssigned}</p>
                </div>
              </div>
              <div className="card flex items-center gap-3">
                <Users className="text-primary-500" size={20} />
                <div>
                  <p className="text-sm text-gray-500 dark:text-gray-400">Boats Used</p>
                  <p className="text-2xl font-bold">{result.totalBoatsUsed}</p>
                </div>
              </div>
              <div className="card">
                <p className="text-sm text-gray-500 dark:text-gray-400">Period</p>
                <p className="text-sm font-medium">{result.weekStart} to {result.weekEnd}</p>
              </div>
            </div>

            {result.assignments && result.assignments.length > 0 && (
              <div className="card overflow-hidden p-0">
                <table className="w-full">
                  <thead><tr className="bg-gray-50 dark:bg-gray-900 border-b">
                    <th className="text-left px-4 py-3 text-xs font-medium text-gray-500 dark:text-gray-400 uppercase">User</th>
                    <th className="text-left px-4 py-3 text-xs font-medium text-gray-500 dark:text-gray-400 uppercase">Boat</th>
                    <th className="text-left px-4 py-3 text-xs font-medium text-gray-500 dark:text-gray-400 uppercase">Session</th>
                  </tr></thead>
                  <tbody className="divide-y divide-gray-100">
                    {result.assignments.map((a: any, i: number) => (
                      <tr key={i} className="hover:bg-gray-50 hover:dark:bg-gray-900">
                        <td className="px-4 py-3 text-sm">{a.userName}</td>
                        <td className="px-4 py-3 text-sm">{a.boatName}</td>
                        <td className="px-4 py-3 text-sm text-gray-500 dark:text-gray-400">{a.sessionInfo}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
          </div>
        )}
      </div>
    </ProtectedRoute>
  );
}
