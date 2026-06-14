'use client';

import { useEffect, useState } from 'react';
import ProtectedRoute from '@/components/ProtectedRoute';
import api from '@/lib/api';
import { CheckCircle, MessageSquare } from 'lucide-react';

interface AdminMessage {
  id: number;
  user: { fullName: string; email: string };
  ledgerEntryId: number;
  message: string;
  createdAt: string;
  isResolved: boolean;
}

export default function MessagesPage() {
  const [messages, setMessages] = useState<AdminMessage[]>([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    loadData();
  }, []);

  async function loadData() {
    try {
      const res = await api.get('/admin/messages');
      setMessages(res.data);
    } catch (e) { console.error(e); }
    finally { setLoading(false); }
  }

  async function resolveMessage(id: number) {
    await api.patch(`/admin/messages/${id}/resolve`);
    setMessages(prev => prev.filter(m => m.id !== id));
  }

  return (
    <ProtectedRoute adminOnly>
      <div className="space-y-6">
        <h1 className="text-2xl font-bold text-gray-900 dark:text-gray-100">Messages</h1>

        <div>
          <h2 className="text-lg font-semibold text-gray-900 dark:text-gray-100 mb-4">
            <MessageSquare size={18} className="inline mr-2" />
            Member Reports ({messages.length})
          </h2>
          {loading ? (
            <div className="flex justify-center py-12"><div className="animate-spin rounded-full h-8 w-8 border-b-2 border-primary-600"></div></div>
          ) : messages.length === 0 ? (
            <div className="card text-center text-gray-500 dark:text-gray-400 py-8">No pending reports</div>
          ) : (
            <div className="space-y-3">
              {messages.map(msg => (
                <div key={msg.id} className="card">
                  <div className="flex items-start justify-between">
                    <div>
                      <p className="text-sm font-medium text-gray-900 dark:text-gray-100">{msg.user.fullName}</p>
                      <p className="text-xs text-gray-500 dark:text-gray-400">{msg.user.email} · Ledger #{msg.ledgerEntryId}</p>
                      <p className="text-sm text-gray-700 dark:text-gray-300 mt-2">{msg.message}</p>
                      <p className="text-xs text-gray-400 dark:text-gray-500 mt-1">{new Date(msg.createdAt).toLocaleString()}</p>
                    </div>
                    <button onClick={() => resolveMessage(msg.id)}
                      className="text-green-500 dark:text-green-400 hover:bg-green-50 hover:dark:bg-green-900/30 p-2 rounded-lg" title="Resolve">
                      <CheckCircle size={18} />
                    </button>
                  </div>
                </div>
              ))}
            </div>
          )}
        </div>
      </div>
    </ProtectedRoute>
  );
}
