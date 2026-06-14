'use client';

import { useEffect, useState, Suspense } from 'react';
import { useSearchParams } from 'next/navigation';
import ProtectedRoute from '@/components/ProtectedRoute';
import api from '@/lib/api';
import { User, LedgerEntry } from '@/types';
import { endOfDay, toDateInput } from '@/lib/dateUtils';
import { useDialog } from '@/context/DialogContext';
import { Search, Plus, Minus, Edit, ArrowLeft, Users, Clock, CreditCard, Calendar as CalendarIcon } from 'lucide-react';
import clsx from 'clsx';

function addMonths(months: number): string {
  const d = new Date();
  d.setMonth(d.getMonth() + months);
  return d.toISOString().split('T')[0];
}
function addDays(days: number): string {
  const d = new Date();
  d.setDate(d.getDate() + days);
  return d.toISOString().split('T')[0];
}

const EXPIRATION_PRESETS: Array<{ label: string; value: () => string }> = [
  { label: '1w', value: () => addDays(7) },
  { label: '1m', value: () => addMonths(1) },
  { label: '3m', value: () => addMonths(3) },
  { label: '6m', value: () => addMonths(6) },
  { label: '1y', value: () => addMonths(12) },
];

export default function AdminLedgerPage() {
  return (
    <Suspense fallback={null}>
      <LedgerInner />
    </Suspense>
  );
}

function LedgerInner() {
  const { toast } = useDialog();
  const searchParams = useSearchParams();
  const queryUserId = searchParams.get('user');
  const [users, setUsers] = useState<User[]>([]);
  const [filteredUsers, setFilteredUsers] = useState<User[]>([]);
  const [selectedUser, setSelectedUser] = useState<User | null>(null);
  const [ledgerEntries, setLedgerEntries] = useState<LedgerEntry[]>([]);
  const [searchQuery, setSearchQuery] = useState('');
  const [loading, setLoading] = useState(true);
  const [showAddCredit, setShowAddCredit] = useState(false);
  const [creditAmount, setCreditAmount] = useState('');
  const [creditReason, setCreditReason] = useState('');
  const [creditExpiration, setCreditExpiration] = useState('');
  const [isDeduction, setIsDeduction] = useState(false);
  const [editingEntry, setEditingEntry] = useState<number | null>(null);
  const [newExpiration, setNewExpiration] = useState('');
  const [sortBy, setSortBy] = useState<'credits' | 'expiration'>('credits');
  const [filterRole, setFilterRole] = useState<string>('all');

  useEffect(() => { loadUsers(); }, []);

  async function loadUsers() {
    try {
      const res = await api.get('/admin/users');
      setUsers(res.data);
      setFilteredUsers(res.data);
      if (queryUserId) {
        const target = res.data.find((u: User) => String(u.id) === queryUserId);
        if (target) {
          setSelectedUser(target);
          const ledger = await api.get(`/admin/ledger/${target.id}`);
          setLedgerEntries(ledger.data);
        }
      }
    } catch (e) { console.error(e); }
    finally { setLoading(false); }
  }

  useEffect(() => {
    let result = users;
    if (searchQuery) {
      result = result.filter(u => u.fullName.toLowerCase().includes(searchQuery.toLowerCase()) || u.email.toLowerCase().includes(searchQuery.toLowerCase()));
    }
    if (filterRole !== 'all') {
      result = result.filter(u => u.role === filterRole);
    }
    result.sort((a, b) => {
      if (sortBy === 'credits') return (b.creditBalance || 0) - (a.creditBalance || 0);
      return 0;
    });
    setFilteredUsers(result);
  }, [searchQuery, users, filterRole, sortBy]);

  async function loadUserLedger(userId: number) {
    const res = await api.get(`/admin/ledger/${userId}`);
    setLedgerEntries(res.data);
  }

  async function selectUser(user: User) {
    setSelectedUser(user);
    await loadUserLedger(user.id);
  }

  async function handleCreditAction() {
    if (!selectedUser || !creditAmount) return;
    try {
      const endpoint = isDeduction ? 'deduct' : 'credit';
      await api.post(`/admin/ledger/${selectedUser.id}/${endpoint}`, {
        amount: parseFloat(creditAmount),
        reason: creditReason || (isDeduction ? 'Admin deduction' : 'Admin credit'),
        expirationDate: endOfDay(creditExpiration),
      });
      setShowAddCredit(false);
      setCreditAmount('');
      setCreditReason('');
      setCreditExpiration('');
      await loadUserLedger(selectedUser.id);
      loadUsers();
    } catch (e: any) { toast(e.response?.data?.message || 'Failed'); }
  }

  async function updateExpiration(entryId: number) {
    try {
      await api.patch(`/admin/ledger/entry/${entryId}`, { expirationDate: endOfDay(newExpiration) });
      setEditingEntry(null);
      setNewExpiration('');
      if (selectedUser) await loadUserLedger(selectedUser.id);
    } catch (e: any) { toast(e.response?.data?.message || 'Failed'); }
  }

  const activeMembers = users.filter(u => (u.creditBalance || 0) > 0 && u.role === 'MEMBER').length;
  const activeStudents = users.filter(u => (u.creditBalance || 0) > 0 && u.role === 'TRAINER').length;

  if (selectedUser) {
    return (
      <ProtectedRoute adminOnly>
        <div className="space-y-6">
          <div className="flex items-center gap-4">
            <button onClick={() => setSelectedUser(null)} className="p-2 hover:bg-gray-100 hover:dark:bg-gray-800 rounded-lg"><ArrowLeft size={20} /></button>
            <div>
              <h1 className="text-2xl font-bold text-gray-900 dark:text-gray-100">{selectedUser.fullName}</h1>
              <p className="text-gray-500 dark:text-gray-400">{selectedUser.email} · {selectedUser.role}</p>
            </div>
          </div>
          <div className="flex gap-2">
            <button onClick={() => { setIsDeduction(false); setShowAddCredit(true); }} className="btn-primary text-sm flex items-center gap-1"><Plus size={14} />Add Credit</button>
            <button onClick={() => { setIsDeduction(true); setShowAddCredit(true); }} className="btn-danger text-sm flex items-center gap-1"><Minus size={14} />Remove Credit</button>
          </div>
          <div className="card overflow-hidden p-0">
            <table className="w-full">
              <thead><tr className="bg-gray-50 dark:bg-gray-900 border-b">
                <th className="text-left px-4 py-3 text-xs font-medium text-gray-500 dark:text-gray-400 uppercase">Date</th>
                <th className="text-left px-4 py-3 text-xs font-medium text-gray-500 dark:text-gray-400 uppercase">Reason</th>
                <th className="text-right px-4 py-3 text-xs font-medium text-gray-500 dark:text-gray-400 uppercase">Amount</th>
                <th className="text-right px-4 py-3 text-xs font-medium text-gray-500 dark:text-gray-400 uppercase">Balance</th>
                <th className="text-right px-4 py-3 text-xs font-medium text-gray-500 dark:text-gray-400 uppercase">Expires</th>
                <th className="w-10"></th>
              </tr></thead>
              <tbody className="divide-y divide-gray-100">
                {ledgerEntries.map(entry => (
                  <tr key={entry.id} className="hover:bg-gray-50 hover:dark:bg-gray-900">
                    <td className="px-4 py-3 text-sm">{new Date(entry.timestamp).toLocaleDateString()}</td>
                    <td className="px-4 py-3 text-sm">{entry.reason}</td>
                    <td className={`px-4 py-3 text-sm text-right font-medium ${entry.amount >= 0 ? 'text-green-600' : 'text-red-600'}`}>{entry.amount >= 0 ? '+' : ''}{entry.amount}</td>
                    <td className="px-4 py-3 text-sm text-right font-medium">{entry.runningBalance}</td>
                    <td className="px-4 py-3 text-sm text-right">
                      {editingEntry === entry.id ? (
                        <div className="flex flex-col items-end gap-1">
                          <div className="flex items-center gap-1">
                            <input type="date" value={newExpiration} onChange={e => setNewExpiration(e.target.value)} className="input text-xs py-1 px-2 w-36" />
                            <button onClick={() => updateExpiration(entry.id)} className="text-green-600 dark:text-green-400 text-xs font-medium">Save</button>
                          </div>
                          <div className="flex gap-1">
                            {EXPIRATION_PRESETS.map(p => (
                              <button
                                key={p.label}
                                type="button"
                                onClick={() => setNewExpiration(p.value())}
                                className="text-[10px] px-1.5 py-0.5 rounded-full bg-gray-100 dark:bg-gray-700 hover:bg-primary-100 dark:hover:bg-primary-900/40 text-gray-600 dark:text-gray-300 hover:text-primary-700 dark:hover:text-primary-300 transition-colors active:scale-95"
                              >
                                +{p.label}
                              </button>
                            ))}
                          </div>
                        </div>
                      ) : (
                        entry.expirationDate ? new Date(entry.expirationDate).toLocaleDateString() : '—'
                      )}
                    </td>
                    <td className="px-4 py-3">
                      <button onClick={() => { setEditingEntry(entry.id); setNewExpiration(toDateInput(entry.expirationDate)); }}
                        className="text-gray-400 dark:text-gray-500 hover:text-primary-600 hover:dark:text-primary-400"><Edit size={14} /></button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          {showAddCredit && (
            <div className="fixed inset-0 bg-black/40 flex items-center justify-center z-50" onClick={() => setShowAddCredit(false)}>
              <div className="bg-white dark:bg-gray-800 rounded-xl shadow-xl p-6 max-w-sm w-full mx-4" onClick={e => e.stopPropagation()}>
                <h3 className="text-lg font-semibold mb-4">{isDeduction ? 'Remove Credits' : 'Add Credits'}</h3>
                <div className="space-y-3">
                  <input type="number" value={creditAmount} onChange={e => setCreditAmount(e.target.value)} className="input" placeholder="Amount" min="1" />
                  <input value={creditReason} onChange={e => setCreditReason(e.target.value)} className="input" placeholder="Reason" />
                  {!isDeduction && (
                    <div>
                      <label className="flex items-center gap-1.5 text-xs font-medium text-gray-600 dark:text-gray-400 mb-1">
                        <CalendarIcon size={12} /> Expiration <span className="text-gray-400 dark:text-gray-500 font-normal">(optional, ends 23:59)</span>
                      </label>
                      <input type="date" value={creditExpiration} onChange={e => setCreditExpiration(e.target.value)} className="input" />
                      <div className="flex flex-wrap gap-1 mt-1.5">
                        {EXPIRATION_PRESETS.map(p => (
                          <button
                            key={p.label}
                            type="button"
                            onClick={() => setCreditExpiration(p.value())}
                            className="text-[10px] px-2 py-0.5 rounded-full bg-gray-100 dark:bg-gray-700 hover:bg-primary-100 dark:hover:bg-primary-900/40 text-gray-600 dark:text-gray-300 hover:text-primary-700 dark:hover:text-primary-300 transition-colors active:scale-95"
                          >
                            +{p.label}
                          </button>
                        ))}
                        <button
                          type="button"
                          onClick={() => setCreditExpiration('')}
                          className="text-[10px] px-2 py-0.5 rounded-full bg-gray-100 dark:bg-gray-700 hover:bg-red-100 dark:hover:bg-red-900/40 text-gray-500 dark:text-gray-400 hover:text-red-600 dark:hover:text-red-400 transition-colors active:scale-95"
                        >
                          clear
                        </button>
                      </div>
                    </div>
                  )}
                </div>
                <div className="flex gap-3 mt-4">
                  <button onClick={() => setShowAddCredit(false)} className="btn-secondary flex-1">Cancel</button>
                  <button onClick={handleCreditAction} className={isDeduction ? 'btn-danger flex-1' : 'btn-primary flex-1'}>
                    {isDeduction ? 'Deduct' : 'Add'}
                  </button>
                </div>
              </div>
            </div>
          )}
        </div>
      </ProtectedRoute>
    );
  }

  return (
    <ProtectedRoute adminOnly>
      <div className="space-y-6">
        <h1 className="text-2xl font-bold text-gray-900 dark:text-gray-100">Ledger Management</h1>

        <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
          <div className="card flex items-center gap-3">
            <CreditCard className="text-green-500 dark:text-green-400" size={20} />
            <div>
              <p className="text-sm text-gray-500 dark:text-gray-400">Active Club Members</p>
              <p className="text-2xl font-bold">{activeMembers}</p>
            </div>
          </div>
          <div className="card flex items-center gap-3">
            <Users className="text-blue-500 dark:text-blue-400" size={20} />
            <div>
              <p className="text-sm text-gray-500 dark:text-gray-400">Active Students</p>
              <p className="text-2xl font-bold">{activeStudents}</p>
            </div>
          </div>
          <div className="card flex items-center gap-3">
            <Clock className="text-orange-500 dark:text-orange-400" size={20} />
            <div>
              <p className="text-sm text-gray-500 dark:text-gray-400">Total Active</p>
              <p className="text-2xl font-bold">{activeMembers + activeStudents}</p>
            </div>
          </div>
        </div>

        <div className="flex flex-wrap items-center gap-3">
          <div className="relative flex-1 min-w-[200px]">
            <Search size={16} className="absolute left-3 top-1/2 -translate-y-1/2 text-gray-400 dark:text-gray-500" />
            <input value={searchQuery} onChange={e => setSearchQuery(e.target.value)} className="input pl-9" placeholder="Search members..." />
          </div>
          <select value={filterRole} onChange={e => setFilterRole(e.target.value)} className="input w-auto">
            <option value="all">All Roles</option>
            <option value="MEMBER">Members</option>
            <option value="TRAINER">Trainers</option>
          </select>
          <select value={sortBy} onChange={e => setSortBy(e.target.value as any)} className="input w-auto">
            <option value="credits">Sort by Credits</option>
            <option value="expiration">Sort by Expiration</option>
          </select>
        </div>

        {loading ? (
          <div className="flex justify-center py-12"><div className="animate-spin rounded-full h-8 w-8 border-b-2 border-primary-600"></div></div>
        ) : (
          <div className="card overflow-hidden p-0">
            <table className="w-full">
              <thead><tr className="bg-gray-50 dark:bg-gray-900 border-b">
                <th className="text-left px-4 py-3 text-xs font-medium text-gray-500 dark:text-gray-400 uppercase">Member</th>
                <th className="text-left px-4 py-3 text-xs font-medium text-gray-500 dark:text-gray-400 uppercase">Role</th>
                <th className="text-right px-4 py-3 text-xs font-medium text-gray-500 dark:text-gray-400 uppercase">Credits</th>
                <th className="text-right px-4 py-3 text-xs font-medium text-gray-500 dark:text-gray-400 uppercase">Lessons</th>
              </tr></thead>
              <tbody className="divide-y divide-gray-100">
                {filteredUsers.filter(u => u.role === 'MEMBER' || u.role === 'TRAINER').map(user => (
                  <tr key={user.id} className="hover:bg-gray-50 hover:dark:bg-gray-900 cursor-pointer" onClick={() => selectUser(user)}>
                    <td className="px-4 py-3">
                      <p className="text-sm font-medium text-gray-900 dark:text-gray-100">{user.fullName}</p>
                      <p className="text-xs text-gray-500 dark:text-gray-400">{user.email}</p>
                    </td>
                    <td className="px-4 py-3">
                      <span className={clsx('px-2 py-0.5 rounded-full text-xs font-medium',
                        user.role === 'TRAINER' ? 'bg-blue-100 dark:bg-blue-900/40 text-blue-700 dark:text-blue-300' : 'bg-green-100 dark:bg-green-900/40 text-green-700 dark:text-green-300')}>
                        {user.role === 'TRAINER' ? 'Trainer' : 'Member'}
                      </span>
                    </td>
                    <td className="px-4 py-3 text-right">
                      <span className={clsx('font-medium text-sm', (user.creditBalance || 0) > 0 ? 'text-green-600 dark:text-green-400' : 'text-red-500 dark:text-red-400')}>
                        {user.creditBalance || 0}
                      </span>
                    </td>
                    <td className="px-4 py-3 text-right text-sm text-gray-600 dark:text-gray-400">{user.lessonsAttended}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>
    </ProtectedRoute>
  );
}
