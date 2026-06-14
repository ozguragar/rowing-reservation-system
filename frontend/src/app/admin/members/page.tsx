'use client';

import { useEffect, useMemo, useState } from 'react';
import Link from 'next/link';
import ProtectedRoute from '@/components/ProtectedRoute';
import api from '@/lib/api';
import { User } from '@/types';
import { Search, Wallet, Users as UsersIcon, CheckCircle2, Circle, GraduationCap } from 'lucide-react';
import clsx from 'clsx';

interface MemberUser extends User {
  creditBalance?: number;
  earliestCreditExpiration?: string | null;
}

type SortKey = 'name' | 'lessons' | 'credits' | 'expiration' | 'training';

export default function AdminMembersPage() {
  const [users, setUsers] = useState<MemberUser[]>([]);
  const [loading, setLoading] = useState(true);
  const [query, setQuery] = useState('');
  const [roleFilter, setRoleFilter] = useState<'all' | 'STUDENT' | 'CLUB_MEMBER'>('all');
  const [sortBy, setSortBy] = useState<SortKey>('name');

  useEffect(() => { load(); }, []);

  async function load() {
    try {
      const res = await api.get('/admin/users');
      setUsers(res.data);
    } catch (e) {
      console.error(e);
    } finally {
      setLoading(false);
    }
  }

  const nonAdmins = useMemo(() => users.filter(u => u.role !== 'ADMIN'), [users]);
  const activeCount = useMemo(
    () => nonAdmins.filter(u => (u.creditBalance ?? 0) > 0).length,
    [nonAdmins]
  );

  const visible = useMemo(() => {
    let list = nonAdmins;
    const q = query.trim().toLowerCase();
    if (q) list = list.filter(u => u.fullName.toLowerCase().includes(q) || u.email.toLowerCase().includes(q));
    if (roleFilter !== 'all') list = list.filter(u => u.role === roleFilter);

    const sorted = [...list];
    switch (sortBy) {
      case 'name':
        sorted.sort((a, b) => a.fullName.localeCompare(b.fullName));
        break;
      case 'lessons':
        sorted.sort((a, b) => (b.lessonsAttended || 0) - (a.lessonsAttended || 0));
        break;
      case 'credits':
        sorted.sort((a, b) => (b.creditBalance || 0) - (a.creditBalance || 0));
        break;
      case 'expiration':
        sorted.sort((a, b) => {
          const ae = a.earliestCreditExpiration ? Date.parse(a.earliestCreditExpiration) : Infinity;
          const be = b.earliestCreditExpiration ? Date.parse(b.earliestCreditExpiration) : Infinity;
          return ae - be;
        });
        break;
      case 'training':
        sorted.sort((a, b) => Number(a.isFinishedBasicTraining) - Number(b.isFinishedBasicTraining));
        break;
    }
    return sorted;
  }, [nonAdmins, query, roleFilter, sortBy]);

  return (
    <ProtectedRoute adminOnly>
      <div className="space-y-4">
        <div className="flex items-center justify-between">
          <h1 className="text-2xl font-bold text-gray-900 dark:text-gray-100">Members</h1>
          <div className="text-sm text-gray-500 dark:text-gray-400 flex items-center gap-2">
            <UsersIcon size={14} />
            <span data-testid="active-count">{activeCount} active</span>
          </div>
        </div>

        <div className="card p-3 md:p-3">
          <div className="flex flex-wrap items-center gap-2">
            <div className="relative flex-1 min-w-[200px]">
              <Search size={14} className="absolute left-3 top-1/2 -translate-y-1/2 text-gray-400 dark:text-gray-500" />
              <input
                value={query}
                onChange={e => setQuery(e.target.value)}
                placeholder="Search by name or email…"
                className="input pl-9"
                data-testid="members-search"
              />
            </div>
            <select
              value={roleFilter}
              onChange={e => setRoleFilter(e.target.value as any)}
              className="input w-auto"
              data-testid="role-filter"
            >
              <option value="all">All roles</option>
              <option value="STUDENT">Students</option>
              <option value="CLUB_MEMBER">Members</option>
            </select>
            <select
              value={sortBy}
              onChange={e => setSortBy(e.target.value as SortKey)}
              className="input w-auto"
              data-testid="sort-by"
            >
              <option value="name">Sort: Name</option>
              <option value="lessons">Sort: Lessons</option>
              <option value="credits">Sort: Credits</option>
              <option value="expiration">Sort: Earliest expiration</option>
              <option value="training">Sort: Training status</option>
            </select>
          </div>
        </div>

        {loading ? (
          <div className="flex justify-center py-12">
            <div className="animate-spin rounded-full h-8 w-8 border-b-2 border-primary-600"></div>
          </div>
        ) : visible.length === 0 ? (
          <div className="card text-center text-gray-500 dark:text-gray-400 py-12">No members match your filters</div>
        ) : (
          <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-3">
            {visible.map(u => (
              <div key={u.id} className="card" data-testid={`member-card-${u.id}`}>
                <div className="flex items-start justify-between gap-2">
                  <div className="min-w-0 flex-1">
                    <div className="flex items-center gap-2">
                      <span className={u.role === 'STUDENT' ? 'badge-student' : 'badge-member'}>
                        {u.role === 'STUDENT' ? 'S' : 'M'}
                      </span>
                      <Link
                        href={`/account/${u.id}`}
                        className="font-medium text-gray-900 dark:text-gray-100 hover:underline truncate"
                      >
                        {u.fullName}
                      </Link>
                    </div>
                    <p className="text-xs text-gray-500 dark:text-gray-400 truncate mt-0.5">{u.email}</p>
                  </div>
                  <Link
                    href={`/admin/ledger?user=${u.id}`}
                    className="text-gray-400 dark:text-gray-500 hover:text-primary-600 hover:dark:text-primary-400 shrink-0"
                    title="Open ledger"
                    aria-label={`Open ledger for ${u.fullName}`}
                    data-testid={`wallet-${u.id}`}
                  >
                    <Wallet size={18} />
                  </Link>
                </div>

                <div className="mt-3 flex items-center gap-3 text-xs text-gray-500 dark:text-gray-400">
                  <span className="flex items-center gap-1">
                    <GraduationCap size={12} /> {u.lessonsAttended ?? 0} lessons
                  </span>
                  <span className={clsx((u.creditBalance ?? 0) > 0 ? 'text-green-600 dark:text-green-400' : 'text-red-500 dark:text-red-400', 'font-medium')}>
                    {u.creditBalance ?? 0} credits
                  </span>
                </div>

                {u.earliestCreditExpiration && (
                  <p className="text-xs text-orange-500 dark:text-orange-400 mt-1">
                    Expires {new Date(u.earliestCreditExpiration).toLocaleDateString()}
                  </p>
                )}

                <div className="mt-2 flex items-center gap-1 text-xs">
                  {u.isFinishedBasicTraining ? (
                    <span className="flex items-center gap-1 text-green-600 dark:text-green-400">
                      <CheckCircle2 size={12} /> Training complete
                    </span>
                  ) : (
                    <span className="flex items-center gap-1 text-amber-600 dark:text-amber-400">
                      <Circle size={12} /> Training pending
                    </span>
                  )}
                </div>
              </div>
            ))}
          </div>
        )}
      </div>
    </ProtectedRoute>
  );
}
