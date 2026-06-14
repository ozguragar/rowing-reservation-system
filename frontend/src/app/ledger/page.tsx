'use client';

import { useEffect, useState } from 'react';
import ProtectedRoute from '@/components/ProtectedRoute';
import api from '@/lib/api';
import { LedgerEntry } from '@/types';
import { useDialog } from '@/context/DialogContext';
import { Wallet, AlertTriangle, X } from 'lucide-react';

export default function LedgerPage() {
  const [entries, setEntries] = useState<LedgerEntry[]>([]);
  const [loading, setLoading] = useState(true);
  const [reportingId, setReportingId] = useState<number | null>(null);
  const [reportMessage, setReportMessage] = useState('');
  const [hoveredRow, setHoveredRow] = useState<number | null>(null);
  const { toast } = useDialog();

  useEffect(() => {
    loadLedger();
  }, []);

  async function loadLedger() {
    try {
      const res = await api.get('/ledger/my');
      setEntries(res.data);
    } catch (e) {
      console.error(e);
    } finally {
      setLoading(false);
    }
  }

  async function submitReport() {
    if (!reportingId || !reportMessage.trim()) return;
    try {
      await api.post('/ledger/report', {
        ledgerEntryId: reportingId,
        message: reportMessage,
      });
      setReportingId(null);
      setReportMessage('');
      toast('Report sent to admin', 'success');
    } catch (e: any) {
      toast(e.response?.data?.message || 'Failed to send report');
    }
  }

  return (
    <ProtectedRoute>
      <div className="space-y-6">
        <h1 className="text-2xl font-bold text-gray-900 dark:text-gray-100">My Ledger</h1>

        {loading ? (
          <div className="flex justify-center py-12">
            <div className="animate-spin rounded-full h-8 w-8 border-b-2 border-primary-600"></div>
          </div>
        ) : entries.length === 0 ? (
          <div className="card text-center text-gray-500 dark:text-gray-400 py-12">
            <Wallet size={48} className="mx-auto mb-3 text-gray-300 dark:text-gray-600" />
            <p>No ledger entries yet</p>
          </div>
        ) : (
          <div className="card overflow-hidden p-0">
            <table className="w-full">
              <thead>
                <tr className="bg-gray-50 dark:bg-gray-900 border-b border-gray-200 dark:border-gray-700">
                  <th className="text-left px-6 py-3 text-xs font-medium text-gray-500 dark:text-gray-400 uppercase">Date</th>
                  <th className="text-left px-6 py-3 text-xs font-medium text-gray-500 dark:text-gray-400 uppercase">Reason</th>
                  <th className="text-right px-6 py-3 text-xs font-medium text-gray-500 dark:text-gray-400 uppercase">Amount</th>
                  <th className="text-right px-6 py-3 text-xs font-medium text-gray-500 dark:text-gray-400 uppercase">Balance</th>
                  <th className="text-right px-6 py-3 text-xs font-medium text-gray-500 dark:text-gray-400 uppercase">Expires</th>
                  <th className="px-6 py-3 w-10"></th>
                </tr>
              </thead>
              <tbody className="divide-y divide-gray-100">
                {entries.map((entry) => (
                  <tr key={entry.id} className="hover:bg-gray-50 hover:dark:bg-gray-900 transition-colors"
                    onMouseEnter={() => setHoveredRow(entry.id)}
                    onMouseLeave={() => setHoveredRow(null)}>
                    <td className="px-6 py-4 text-sm text-gray-600 dark:text-gray-400">
                      {new Date(entry.timestamp).toLocaleDateString()}
                    </td>
                    <td className="px-6 py-4 text-sm text-gray-900 dark:text-gray-100">{entry.reason}</td>
                    <td className={`px-6 py-4 text-sm text-right font-medium ${entry.amount >= 0 ? 'text-green-600' : 'text-red-600'}`}>
                      {entry.amount >= 0 ? '+' : ''}{entry.amount}
                    </td>
                    <td className="px-6 py-4 text-sm text-right font-medium text-gray-900 dark:text-gray-100">
                      {entry.runningBalance}
                    </td>
                    <td className="px-6 py-4 text-sm text-right text-gray-500 dark:text-gray-400">
                      {entry.expirationDate ? new Date(entry.expirationDate).toLocaleDateString() : '—'}
                    </td>
                    <td className="px-6 py-4">
                      {hoveredRow === entry.id && (
                        <button onClick={() => setReportingId(entry.id)}
                          className="text-gray-400 dark:text-gray-500 hover:text-amber-500 hover:dark:text-amber-400 transition-colors" title="Report issue">
                          <AlertTriangle size={16} />
                        </button>
                      )}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}

        {reportingId && (
          <div className="fixed inset-0 bg-black/40 flex items-center justify-center z-50" onClick={() => setReportingId(null)}>
            <div className="bg-white dark:bg-gray-800 rounded-xl shadow-xl p-6 max-w-md w-full mx-4" onClick={e => e.stopPropagation()}>
              <div className="flex items-center justify-between mb-4">
                <h3 className="text-lg font-semibold">Report Issue</h3>
                <button onClick={() => setReportingId(null)}><X size={18} /></button>
              </div>
              <p className="text-sm text-gray-600 dark:text-gray-400 mb-3">Describe the issue with this ledger entry:</p>
              <textarea value={reportMessage} onChange={e => setReportMessage(e.target.value)}
                className="input h-24 resize-none" placeholder="What seems wrong?" />
              <div className="flex gap-3 mt-4">
                <button onClick={() => setReportingId(null)} className="btn-secondary flex-1">Cancel</button>
                <button onClick={submitReport} className="btn-primary flex-1">Send Report</button>
              </div>
            </div>
          </div>
        )}
      </div>
    </ProtectedRoute>
  );
}
