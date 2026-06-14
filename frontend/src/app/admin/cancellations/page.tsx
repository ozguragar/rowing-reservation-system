'use client';

import { useEffect, useState, useCallback } from 'react';
import ProtectedRoute from '@/components/ProtectedRoute';
import api from '@/lib/api';
import { Booking } from '@/types';
import { Check, X, Inbox } from 'lucide-react';
import { useDialog } from '@/context/DialogContext';

export default function CancellationsPage() {
  const { toast } = useDialog();
  const [requests, setRequests] = useState<Booking[]>([]);
  const [loading, setLoading] = useState(false);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const res = await api.get('/admin/cancellation-requests');
      setRequests(res.data);
    } catch (e) { console.error(e); }
    finally { setLoading(false); }
  }, []);

  useEffect(() => { load(); }, [load]);

  async function approve(id: number) {
    try {
      await api.post(`/admin/cancellation-requests/${id}/approve`);
      load();
    } catch (e: any) { toast(e.response?.data?.message || 'Failed'); }
  }

  async function deny(id: number) {
    try {
      await api.post(`/admin/cancellation-requests/${id}/deny`);
      load();
    } catch (e: any) { toast(e.response?.data?.message || 'Failed'); }
  }

  return (
    <ProtectedRoute adminOnly>
      <div className="space-y-4">
        <div>
          <h1 className="text-2xl font-bold text-gray-900 dark:text-gray-100">Cancellation Requests</h1>
          <p className="text-sm text-gray-500 dark:text-gray-400">Approve to cancel the booking and refund the credit. Deny to keep the booking active.</p>
        </div>

        {loading ? (
          <div className="flex justify-center py-12"><div className="animate-spin rounded-full h-8 w-8 border-b-2 border-primary-600"></div></div>
        ) : requests.length === 0 ? (
          <div className="card text-center text-gray-500 dark:text-gray-400 py-12">
            <Inbox size={48} className="mx-auto mb-3 text-gray-300 dark:text-gray-600" />
            <p>No pending cancellation requests</p>
          </div>
        ) : (
          <div className="space-y-2">
            {requests.map(b => (
              <div key={b.id} className="card flex items-center justify-between">
                <div className="min-w-0">
                  <div className="flex items-center gap-2">
                    <span className={b.userRole === 'MEMBER' ? 'badge-member' : b.userRole === 'TRAINER' ? 'badge-student' : 'badge-member'}>
                      {b.userRole === 'MEMBER' ? 'M' : b.userRole === 'TRAINER' ? 'T' : 'A'}
                    </span>
                    <span className="font-medium text-gray-900 dark:text-gray-100 truncate">{b.userFullName}</span>
                    <span className="text-xs text-gray-500 dark:text-gray-400 truncate">{b.userEmail}</span>
                  </div>
                  <p className="text-sm text-gray-600 dark:text-gray-400 mt-1">
                    Boat <span className="font-medium">{b.boatName}</span> ·
                    requested {b.createdAt ? new Date(b.createdAt).toLocaleDateString() : ''}
                  </p>
                </div>
                <div className="flex items-center gap-2 shrink-0 ml-4">
                  <button
                    onClick={() => approve(b.id)}
                    className="flex items-center gap-1 px-3 py-1.5 rounded-lg bg-green-600 hover:bg-green-700 text-white text-sm"
                  >
                    <Check size={14} /> Approve
                  </button>
                  <button
                    onClick={() => deny(b.id)}
                    className="flex items-center gap-1 px-3 py-1.5 rounded-lg bg-gray-200 dark:bg-gray-700 hover:bg-gray-300 text-gray-700 dark:text-gray-300 text-sm"
                  >
                    <X size={14} /> Deny
                  </button>
                </div>
              </div>
            ))}
          </div>
        )}
      </div>
    </ProtectedRoute>
  );
}
