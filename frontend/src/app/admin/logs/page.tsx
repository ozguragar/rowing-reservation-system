'use client';

import { useEffect, useState } from 'react';
import ProtectedRoute from '@/components/ProtectedRoute';
import api from '@/lib/api';
import { AuditLog } from '@/types';
import { Search, Filter } from 'lucide-react';

export default function LogsPage() {
  const [logs, setLogs] = useState<AuditLog[]>([]);
  const [loading, setLoading] = useState(true);
  const [filter, setFilter] = useState('');
  const [quickFilter, setQuickFilter] = useState('');

  useEffect(() => {
    loadLogs();
  }, [quickFilter]);

  async function loadLogs() {
    setLoading(true);
    try {
      const params = quickFilter ? `?filter=${quickFilter}` : '';
      const res = await api.get(`/admin/audit-logs${params}`);
      setLogs(res.data);
    } catch (e) {
      console.error(e);
    } finally {
      setLoading(false);
    }
  }

  const filteredLogs = filter
    ? logs.filter(l => l.userEmail.toLowerCase().includes(filter.toLowerCase()) ||
        l.action.toLowerCase().includes(filter.toLowerCase()) ||
        l.endpoint.toLowerCase().includes(filter.toLowerCase()))
    : logs;

  return (
    <ProtectedRoute adminOnly>
      <div className="space-y-6">
        <h1 className="text-2xl font-bold text-gray-900 dark:text-gray-100">System Logs</h1>

        <div className="flex flex-wrap items-center gap-3">
          <div className="relative flex-1 min-w-[200px]">
            <Search size={16} className="absolute left-3 top-1/2 -translate-y-1/2 text-gray-400 dark:text-gray-500" />
            <input value={filter} onChange={e => setFilter(e.target.value)}
              className="input pl-9" placeholder="Search logs..." />
          </div>
          <div className="flex gap-2">
            <button onClick={() => setQuickFilter('')}
              className={`px-3 py-2 rounded-lg text-sm ${!quickFilter ? 'bg-primary-600 text-white' : 'bg-gray-100 text-gray-600'}`}>All</button>
            <button onClick={() => setQuickFilter('book')}
              className={`px-3 py-2 rounded-lg text-sm ${quickFilter === 'book' ? 'bg-primary-600 text-white' : 'bg-gray-100 text-gray-600'}`}>
              <Filter size={14} className="inline mr-1" />Reservations
            </button>
            <button onClick={() => setQuickFilter('cancel')}
              className={`px-3 py-2 rounded-lg text-sm ${quickFilter === 'cancel' ? 'bg-primary-600 text-white' : 'bg-gray-100 text-gray-600'}`}>
              <Filter size={14} className="inline mr-1" />Cancellations
            </button>
          </div>
        </div>

        {loading ? (
          <div className="flex justify-center py-12"><div className="animate-spin rounded-full h-8 w-8 border-b-2 border-primary-600"></div></div>
        ) : (
          <div className="card overflow-hidden p-0">
            <table className="w-full">
              <thead>
                <tr className="bg-gray-50 dark:bg-gray-900 border-b">
                  <th className="text-left px-4 py-3 text-xs font-medium text-gray-500 dark:text-gray-400 uppercase">Time</th>
                  <th className="text-left px-4 py-3 text-xs font-medium text-gray-500 dark:text-gray-400 uppercase">User</th>
                  <th className="text-left px-4 py-3 text-xs font-medium text-gray-500 dark:text-gray-400 uppercase">Action</th>
                  <th className="text-left px-4 py-3 text-xs font-medium text-gray-500 dark:text-gray-400 uppercase">Endpoint</th>
                  <th className="text-left px-4 py-3 text-xs font-medium text-gray-500 dark:text-gray-400 uppercase">Details</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-gray-100">
                {filteredLogs.map(log => (
                  <tr key={log.id} className="hover:bg-gray-50 hover:dark:bg-gray-900">
                    <td className="px-4 py-3 text-sm text-gray-600 dark:text-gray-400 whitespace-nowrap">
                      {new Date(log.timestamp).toLocaleString()}
                    </td>
                    <td className="px-4 py-3 text-sm">{log.userEmail}</td>
                    <td className="px-4 py-3 text-sm font-medium">{log.action}</td>
                    <td className="px-4 py-3 text-sm text-gray-500 dark:text-gray-400 font-mono text-xs">{log.endpoint}</td>
                    <td className="px-4 py-3 text-sm text-gray-400 dark:text-gray-500 text-xs">{log.details}</td>
                  </tr>
                ))}
                {filteredLogs.length === 0 && (
                  <tr><td colSpan={5} className="px-4 py-12 text-center text-gray-400 dark:text-gray-500">No logs found</td></tr>
                )}
              </tbody>
            </table>
          </div>
        )}
      </div>
    </ProtectedRoute>
  );
}
