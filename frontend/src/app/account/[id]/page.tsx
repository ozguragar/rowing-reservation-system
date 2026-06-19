'use client';

import { useEffect, useState, useCallback } from 'react';
import { useParams, useRouter } from 'next/navigation';
import ProtectedRoute from '@/components/ProtectedRoute';
import { useAuth } from '@/context/AuthContext';
import { useDialog } from '@/context/DialogContext';
import api from '@/lib/api';
import { fmt } from '@/lib/dateUtils';
import { User, Booking } from '@/types';
import { GraduationCap, Mail, Award, Wallet, AlertTriangle, CheckCircle2, Calendar, Clock, ShieldCheck } from 'lucide-react';
import clsx from 'clsx';

interface AccountUser extends User {
  creditBalance?: number;
  earliestCreditExpiration?: string | null;
}

const ROLE_LABELS: Record<string, string> = {
  MEMBER: 'Member', TRAINER: 'Trainer', CLUB_ADMIN: 'Club admin', SUPERADMIN: 'Superadmin',
};
const MEMBER_TYPE_LABELS: Record<string, string> = {
  STUDENT: 'Student', RECREATIONAL: 'Recreational', DEFAULT: 'Default',
};
const STATUS_LABELS: Record<string, string> = {
  MANUAL: 'Booked', AUTO_ASSIGNED: 'Auto-assigned', CANCELLATION_REQUESTED: 'Cancellation requested',
};

export default function AccountPage() {
  const params = useParams<{ id: string }>();
  const id = Number(params.id);
  const router = useRouter();
  const { user: viewer } = useAuth();
  const { toast, confirm } = useDialog();
  const [profile, setProfile] = useState<AccountUser | null>(null);
  const [reservations, setReservations] = useState<Booking[]>([]);
  const [roleEdit, setRoleEdit] = useState('');
  const [typeEdit, setTypeEdit] = useState('');
  const [saving, setSaving] = useState(false);
  const [loading, setLoading] = useState(true);
  const [notFound, setNotFound] = useState(false);

  const isAdmin = viewer?.role === 'CLUB_ADMIN' || viewer?.role === 'SUPERADMIN' || viewer?.role === 'TRAINER';
  const isOwner = viewer?.id === id;
  // Only club admins / superadmins may change roles and member types (not trainers).
  const canManage = (viewer?.role === 'CLUB_ADMIN' || viewer?.role === 'SUPERADMIN') && !isOwner;

  const load = useCallback(async () => {
    try {
      const [profRes, bookingsRes] = await Promise.all([
        api.get<AccountUser>(`/users/${id}`),
        api.get<Booking[]>(`/users/${id}/bookings`),
      ]);
      setProfile(profRes.data);
      setRoleEdit(profRes.data.role);
      setTypeEdit(profRes.data.memberType || 'DEFAULT');
      setReservations(bookingsRes.data);
    } catch {
      setNotFound(true);
    } finally {
      setLoading(false);
    }
  }, [id]);

  useEffect(() => {
    if (!viewer) return;
    if (!isAdmin && !isOwner) {
      router.replace('/dashboard');
      return;
    }
    load();
  }, [viewer, isAdmin, isOwner, load, router]);

  async function saveUser() {
    if (!profile) return;
    setSaving(true);
    try {
      const res = await api.patch<AccountUser>(`/admin/users/${id}`, { role: roleEdit, memberType: typeEdit });
      setProfile(res.data);
      setRoleEdit(res.data.role);
      setTypeEdit(res.data.memberType || 'DEFAULT');
      toast('Member updated', 'success');
    } catch (e: any) {
      toast(e.response?.data?.message || 'Failed to update member', 'error');
    } finally {
      setSaving(false);
    }
  }

  async function markTrainingComplete() {
    if (!profile) return;
    if (!await confirm({
      title: 'Mark training complete',
      message: `Confirm that ${profile.fullName} has completed basic training?`,
      confirmLabel: 'Mark complete',
    })) return;
    try {
      const res = await api.patch<AccountUser>(`/admin/users/${id}/basic-training`, { finished: true });
      setProfile(res.data);
      toast('Basic training marked complete', 'success');
    } catch (e: any) {
      toast(e.response?.data?.message || 'Failed to update', 'error');
    }
  }

  if (!viewer) return null;
  if (!isAdmin && !isOwner) return null;

  const today = fmt(new Date());
  const upcoming = reservations.filter(b => (b.sessionDate || '') >= today);
  const past = reservations.filter(b => (b.sessionDate || '') < today);
  const dirty = profile ? (roleEdit !== profile.role || typeEdit !== (profile.memberType || 'DEFAULT')) : false;

  const roleOptions = ['MEMBER', 'TRAINER', 'CLUB_ADMIN', ...(viewer.role === 'SUPERADMIN' ? ['SUPERADMIN'] : [])];

  const formatTime = (t?: string) => (t ? t.slice(0, 5) : '');

  function ReservationRow({ b }: { b: Booking }) {
    return (
      <div className="flex items-center justify-between py-2 px-3 rounded-lg bg-gray-50 dark:bg-gray-900" data-testid="reservation-row">
        <div className="min-w-0">
          <p className="text-sm font-medium text-gray-900 dark:text-gray-100 flex items-center gap-1.5">
            <Calendar size={12} className="shrink-0" />
            {b.sessionDate ? new Date(b.sessionDate).toLocaleDateString() : '—'}
            <Clock size={12} className="shrink-0 ml-1" />
            {formatTime(b.sessionStartTime)}–{formatTime(b.sessionEndTime)}
          </p>
          <p className="text-xs text-gray-500 dark:text-gray-400 truncate">
            {b.boatName}{b.isCoxSeat && ' · cox'}
          </p>
        </div>
        <span className={clsx('text-xs px-2 py-0.5 rounded-full shrink-0',
          b.status === 'CANCELLATION_REQUESTED'
            ? 'bg-amber-100 dark:bg-amber-900/40 text-amber-700 dark:text-amber-300'
            : 'bg-green-100 dark:bg-green-900/40 text-green-700 dark:text-green-300')}>
          {STATUS_LABELS[b.status] || b.status}
        </span>
      </div>
    );
  }

  return (
    <ProtectedRoute>
      <div className="space-y-4 max-w-2xl">
        {loading ? (
          <div className="flex justify-center py-12">
            <div className="animate-spin rounded-full h-8 w-8 border-b-2 border-primary-600"></div>
          </div>
        ) : notFound || !profile ? (
          <div className="card text-center text-gray-500 dark:text-gray-400 py-12">User not found</div>
        ) : (
          <>
            <div className="card">
              <div className="flex items-center gap-4 mb-4">
                <div className={clsx('w-14 h-14 rounded-full flex items-center justify-center text-xl font-bold',
                  (profile.role === 'CLUB_ADMIN' || profile.role === 'SUPERADMIN') ? 'bg-purple-100 dark:bg-purple-900/40 text-purple-700 dark:text-purple-300' :
                  profile.role === 'TRAINER' ? 'bg-blue-100 dark:bg-blue-900/40 text-blue-700 dark:text-blue-300' :
                  'bg-green-100 dark:bg-green-900/40 text-green-700 dark:text-green-300'
                )}>
                  {profile.fullName.charAt(0)}
                </div>
                <div className="min-w-0">
                  <h1 className="text-xl font-bold text-gray-900 dark:text-gray-100 truncate">{profile.fullName}</h1>
                  <p className="text-sm text-gray-500 dark:text-gray-400 flex items-center gap-1 truncate">
                    <Mail size={12} /> {profile.email}
                  </p>
                </div>
              </div>

              <div className="grid grid-cols-2 gap-3">
                <div className="p-3 bg-gray-50 dark:bg-gray-900 rounded-lg">
                  <div className="flex items-center gap-1.5 text-xs text-gray-500 dark:text-gray-400">
                    <GraduationCap size={12} /> Lessons attended
                  </div>
                  <p className="text-xl font-bold text-gray-900 dark:text-gray-100 mt-1">{profile.lessonsAttended}</p>
                </div>
                {typeof profile.creditBalance === 'number' && (
                  <div className="p-3 bg-gray-50 dark:bg-gray-900 rounded-lg">
                    <div className="flex items-center gap-1.5 text-xs text-gray-500 dark:text-gray-400">
                      <Wallet size={12} /> Credit balance
                    </div>
                    <p className="text-xl font-bold text-gray-900 dark:text-gray-100 mt-1">
                      {profile.creditBalance}
                    </p>
                  </div>
                )}
              </div>

              {profile.earliestCreditExpiration && (
                <p className="text-xs text-orange-600 dark:text-orange-400 mt-3">
                  Earliest credit expiration: {new Date(profile.earliestCreditExpiration).toLocaleDateString()}
                </p>
              )}
            </div>

            {!profile.isFinishedBasicTraining && (
              <div className="card border-amber-300 dark:border-amber-700 bg-amber-50 dark:bg-amber-900/30" data-testid="training-warning">
                <div className="flex items-start gap-3">
                  <AlertTriangle className="text-amber-600 dark:text-amber-400 shrink-0" size={20} />
                  <div className="flex-1">
                    <h2 className="font-semibold text-amber-900 dark:text-amber-200">Basic training not yet completed</h2>
                    <p className="text-sm text-amber-700 dark:text-amber-300 mt-1">
                      {isOwner
                        ? 'You still need to complete basic training before booking advanced boats.'
                        : 'This member has not finished basic training yet.'}
                    </p>
                    {canManage && (
                      <button
                        onClick={markTrainingComplete}
                        className="mt-3 btn-primary text-sm flex items-center gap-1"
                        data-testid="mark-training-complete"
                      >
                        <CheckCircle2 size={14} />
                        Mark basic training complete
                      </button>
                    )}
                  </div>
                </div>
              </div>
            )}

            {(profile.role === 'MEMBER' || profile.role === 'TRAINER') && (
              <div className="flex items-center gap-2 text-sm text-gray-500 dark:text-gray-400">
                <Award size={14} />
                <span>
                  {profile.role === 'TRAINER' ? 'Trainer' : 'Member'}
                  {profile.memberType && profile.memberType !== 'DEFAULT' && ` · ${MEMBER_TYPE_LABELS[profile.memberType]}`}
                  {profile.isOnSchoolTeam && ' · School team'}
                </span>
              </div>
            )}

            {canManage && (
              <div className="card" data-testid="admin-controls">
                <h2 className="font-semibold text-gray-900 dark:text-gray-100 flex items-center gap-1.5 mb-3">
                  <ShieldCheck size={16} /> Manage member
                </h2>
                <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
                  <div>
                    <label className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">Role</label>
                    <select value={roleEdit} onChange={(e) => setRoleEdit(e.target.value)} className="input" data-testid="role-select">
                      {roleOptions.map(r => <option key={r} value={r}>{ROLE_LABELS[r]}</option>)}
                    </select>
                  </div>
                  <div>
                    <label className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">Member type</label>
                    <select value={typeEdit} onChange={(e) => setTypeEdit(e.target.value)} className="input" data-testid="member-type-select">
                      {Object.keys(MEMBER_TYPE_LABELS).map(t => <option key={t} value={t}>{MEMBER_TYPE_LABELS[t]}</option>)}
                    </select>
                  </div>
                </div>
                <button
                  onClick={saveUser}
                  disabled={saving || !dirty}
                  className="btn-primary text-sm mt-3"
                  data-testid="save-member"
                >
                  {saving ? 'Saving...' : 'Save changes'}
                </button>
              </div>
            )}

            <div className="card" data-testid="reservations">
              <h2 className="font-semibold text-gray-900 dark:text-gray-100 flex items-center gap-1.5 mb-3">
                <Calendar size={16} /> Reservations
              </h2>
              {reservations.length === 0 ? (
                <p className="text-sm text-gray-500 dark:text-gray-400">No reservations yet.</p>
              ) : (
                <div className="space-y-4">
                  <div>
                    <p className="text-xs font-semibold uppercase tracking-wide text-gray-400 mb-2">Upcoming ({upcoming.length})</p>
                    {upcoming.length === 0 ? (
                      <p className="text-sm text-gray-500 dark:text-gray-400">No upcoming reservations.</p>
                    ) : (
                      <div className="space-y-2">{upcoming.map(b => <ReservationRow key={b.id} b={b} />)}</div>
                    )}
                  </div>
                  {past.length > 0 && (
                    <div>
                      <p className="text-xs font-semibold uppercase tracking-wide text-gray-400 mb-2">Past ({past.length})</p>
                      <div className="space-y-2 opacity-80">{past.map(b => <ReservationRow key={b.id} b={b} />)}</div>
                    </div>
                  )}
                </div>
              )}
            </div>

            {isAdmin && !isOwner && (
              <a href={`/admin/ledger?user=${profile.id}`} className="btn-secondary text-sm inline-flex items-center gap-1">
                <Wallet size={14} /> View ledger
              </a>
            )}
          </>
        )}
      </div>
    </ProtectedRoute>
  );
}
