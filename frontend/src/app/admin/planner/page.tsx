'use client';

import { useEffect, useState, useCallback, useRef } from 'react';
import ProtectedRoute from '@/components/ProtectedRoute';
import { useAuth } from '@/context/AuthContext';
import api from '@/lib/api';
import { useDialog } from '@/context/DialogContext';
import { Session, User } from '@/types';
import { fmt, getWeekDates } from '@/lib/dateUtils';
import {
  ChevronLeft, ChevronRight, Plus, Copy, Check, Trash2, X, Users,
  ChevronDown, ChevronUp, UserPlus, Calendar as CalendarIcon, Clock as ClockIcon,
} from 'lucide-react';
import clsx from 'clsx';

function maskHHMM(raw: string): string {
  const digits = raw.replace(/\D/g, '').slice(0, 4);
  if (digits.length <= 2) return digits;
  return digits.slice(0, 2) + ':' + digits.slice(2);
}

export default function PlannerPage() {
  const { toast, confirm } = useDialog();
  const { user } = useAuth();
  const isSuperadmin = user?.role === 'SUPERADMIN';
  const [sessions, setSessions] = useState<Session[]>([]);
  const [currentDate, setCurrentDate] = useState(new Date());
  const [viewMode, setViewMode] = useState<'week' | 'day'>('day');
  const [showCreateForm, setShowCreateForm] = useState(false);
  const [showCopyModal, setShowCopyModal] = useState(false);
  const [loading, setLoading] = useState(false);
  const [formData, setFormData] = useState({ date: '', startTime: '', endTime: '' });
  const [copyTarget, setCopyTarget] = useState('');
  const [selectedSessions, setSelectedSessions] = useState<number[]>([]);
  const [showBoatForm, setShowBoatForm] = useState<number | null>(null);
  const [boatForm, setBoatForm] = useState({ type: 'COASTAL', capacity: 4, isBasicTrainingBoat: false, hasCoxSeat: false, name: '' });

  const [collapsed, setCollapsed] = useState<Set<number>>(new Set());
  const [addingTo, setAddingTo] = useState<{ boatId: number; sessionId: number; hasCoxSeat: boolean } | null>(null);
  const hasCoxSeat = addingTo?.hasCoxSeat ?? false;
  const [addAsCox, setAddAsCox] = useState(false);
  const [userQuery, setUserQuery] = useState('');
  const [userResults, setUserResults] = useState<User[]>([]);
  const [dragOverBoat, setDragOverBoat] = useState<number | null>(null);
  const dragRef = useRef<{ userId: number; fromBoatId: number } | null>(null);
  const [collapsedDays, setCollapsedDays] = useState<Set<string>>(new Set());

  function toggleDayCollapse(dateStr: string) {
    setCollapsedDays(prev => {
      const next = new Set(prev);
      if (next.has(dateStr)) next.delete(dateStr); else next.add(dateStr);
      return next;
    });
  }

  const loadSessions = useCallback(async () => {
    setLoading(true);
    const start = new Date(currentDate);
    if (viewMode === 'week') {
      const day = start.getDay();
      start.setDate(start.getDate() - (day === 0 ? 6 : day - 1));
    }
    const end = new Date(start);
    end.setDate(end.getDate() + (viewMode === 'week' ? 6 : 0));
    try {
      const res = await api.get(`/admin/sessions?start=${fmt(start)}&end=${fmt(end)}`);
      setSessions(res.data);
    } catch (e) { console.error(e); }
    finally { setLoading(false); }
  }, [currentDate, viewMode]);

  useEffect(() => { loadSessions(); }, [loadSessions]);

  useEffect(() => {
    if (!addingTo) { setUserResults([]); setUserQuery(''); return; }
    const t = setTimeout(async () => {
      const q = userQuery.trim();
      const url = q ? `/admin/users/search?q=${encodeURIComponent(q)}` : '/admin/users';
      try {
        const res = await api.get(url);
        setUserResults((res.data as User[]).slice(0, 10));
      } catch (e) { console.error(e); }
    }, 200);
    return () => clearTimeout(t);
  }, [userQuery, addingTo]);

  function toggleCollapse(id: number) {
    setCollapsed(prev => {
      const next = new Set(prev);
      if (next.has(id)) next.delete(id); else next.add(id);
      return next;
    });
  }

  async function createSession() {
    try {
      await api.post('/admin/sessions', {
        date: formData.date || fmt(currentDate),
        startTime: formData.startTime,
        endTime: formData.endTime,
      });
      setShowCreateForm(false);
      setFormData({ date: '', startTime: '', endTime: '' });
      loadSessions();
    } catch (e: any) { toast(e.response?.data?.message || 'Failed to create session'); }
  }

  async function addBoat(sessionId: number) {
    try {
      await api.post(`/admin/sessions/${sessionId}/boats`, {
        type: boatForm.type, capacity: boatForm.capacity,
        isBasicTrainingBoat: boatForm.isBasicTrainingBoat,
        hasCoxSeat: boatForm.hasCoxSeat,
        name: boatForm.name || `${boatForm.type.toLowerCase()}-${boatForm.capacity}x`,
      });
      setShowBoatForm(null);
      loadSessions();
    } catch (e: any) { toast(e.response?.data?.message || 'Failed to add boat'); }
  }

  async function approveSession(id: number) {
    try {
      await api.patch(`/admin/sessions/${id}/approve`);
      loadSessions();
    } catch (e: any) { toast(e.response?.data?.message || 'Failed to approve'); }
  }

  async function bulkApprove() {
    if (selectedSessions.length === 0) return;
    try {
      await api.post('/admin/sessions/bulk-approve', selectedSessions);
      setSelectedSessions([]);
      loadSessions();
    } catch (e: any) { toast(e.response?.data?.message || 'Failed to approve'); }
  }

  async function deleteSession(id: number) {
    if (!await confirm({ title: 'Delete session', message: 'Delete this session and all its boats?', confirmLabel: 'Delete', danger: true })) return;
    try {
      await api.delete(`/admin/sessions/${id}`);
      loadSessions();
    } catch (e: any) { toast(e.response?.data?.message || 'Cannot delete session'); }
  }

  async function bulkDelete() {
    if (!await confirm({ title: 'Delete sessions', message: `Delete ${selectedSessions.length} selected sessions?`, confirmLabel: 'Delete all', danger: true })) return;
    try {
      await api.post('/admin/sessions/bulk-delete', selectedSessions);
      setSelectedSessions([]);
      loadSessions();
    } catch (e: any) { toast(e.response?.data?.message || 'Failed to delete'); }
  }

  async function copyDay() {
    if (!copyTarget) return;
    try {
      await api.post('/admin/sessions/copy-day', { sourceDate: fmt(currentDate), targetDate: copyTarget });
      setShowCopyModal(false);
      setCopyTarget('');
      loadSessions();
      toast('Day copied successfully', 'success');
    } catch (e: any) { toast(e.response?.data?.message || 'Failed to copy day'); }
  }

  async function copyWeek() {
    if (!copyTarget) return;
    try {
      await api.post('/admin/sessions/copy-week', {
        sourceWeekStart: fmt(weekStart),
        targetWeekStart: copyTarget,
      });
      setShowCopyModal(false);
      setCopyTarget('');
      loadSessions();
      toast('Week copied successfully', 'success');
    } catch (e: any) {
      toast(e.response?.data?.message || 'Failed to copy week');
    }
  }

  async function removeBooking(bookingId: number) {
    if (!await confirm({ title: 'Remove member', message: 'Remove this member from the boat? Their credit will be refunded.', confirmLabel: 'Remove', danger: true })) return;
    try {
      await api.delete(`/admin/bookings/${bookingId}`);
      loadSessions();
    } catch (e: any) { toast(e.response?.data?.message || 'Failed to remove'); }
  }

  async function deleteBoat(boatId: number) {
    if (!await confirm({ title: 'Delete boat', message: 'Delete this boat?', confirmLabel: 'Delete', danger: true })) return;
    try {
      await api.delete(`/admin/boats/${boatId}`);
      loadSessions();
    } catch (e: any) { toast(e.response?.data?.message || 'Cannot delete boat'); }
  }

  async function adminAddUser(userId: number) {
    if (!addingTo) return;
    try {
      await api.post('/admin/bookings', {
        userId, boatId: addingTo.boatId, sessionId: addingTo.sessionId,
        isCoxSeat: addAsCox || undefined,
      });
      setAddingTo(null);
      loadSessions();
      toast('User added successfully', 'success');
    } catch (e: any) {
      toast(e.response?.data?.message || 'Failed to add user');
    }
  }

  async function moveUser(userId: number, fromBoatId: number, toBoatId: number, isCoxSeat = false) {
    if (fromBoatId === toBoatId) return;
    try {
      await api.post('/admin/bookings/move', { userId, fromBoatId, toBoatId, isCoxSeat });
      loadSessions();
    } catch (e: any) {
      toast(e.response?.data?.message || 'Failed to move user');
    }
  }

  const weekDates = getWeekDates(currentDate);
  const weekStart = weekDates[0];

  const visibleSessions = viewMode === 'day'
    ? sessions.filter(s => s.date === fmt(currentDate))
    : sessions.filter(s => weekDates.some(d => fmt(d) === s.date));

  const groupedByDate = viewMode === 'week'
    ? weekDates.map(d => ({
        date: d,
        dateStr: fmt(d),
        sessions: sessions.filter(s => s.date === fmt(d))
                         .sort((a, b) => (a.startTime || '').localeCompare(b.startTime || '')),
      }))
    : [];

  const daySessions = visibleSessions;

  function renderSession(session: Session) {
    const isCollapsed = collapsed.has(session.id);
    const totalBooked = session.boats?.reduce((a, b) => a + b.currentBookings, 0) || 0;
    const totalCapacity = session.boats?.reduce((a, b) => a + b.capacity, 0) || 0;
    const boatCount = session.boats?.length || 0;

    return (
      <div key={session.id} className="card">
        <div className="flex items-start sm:items-center justify-between gap-2 mb-3">
          <div className="flex items-center gap-2 sm:gap-3 flex-1 min-w-0 flex-wrap">
            <div className="flex items-center gap-2 shrink-0">
              <input type="checkbox" checked={selectedSessions.includes(session.id)}
                onChange={(e) => setSelectedSessions(prev => e.target.checked ? [...prev, session.id] : prev.filter(id => id !== session.id))}
                className="w-4 h-4 rounded border-gray-300 dark:border-gray-600" />
              <button
                onClick={() => toggleCollapse(session.id)}
                className="p-1 hover:bg-gray-100 hover:dark:bg-gray-800 rounded text-gray-500 dark:text-gray-400"
                title={isCollapsed ? 'Expand' : 'Collapse'}
              >
                {isCollapsed ? <ChevronDown size={16} /> : <ChevronUp size={16} />}
              </button>
              <h3 className="font-semibold text-gray-900 dark:text-gray-100 text-sm sm:text-base">
                {session.startTime?.substring(0, 5)} — {session.endTime?.substring(0, 5)}
              </h3>
            </div>
            <span className={clsx('px-2 py-0.5 rounded-full text-xs font-medium shrink-0',
              session.status === 'APPROVED' ? 'bg-green-100 dark:bg-green-900/40 text-green-700 dark:text-green-300' : 'bg-amber-100 dark:bg-amber-900/40 text-amber-700 dark:text-amber-300')}>{session.status}</span>
            {isSuperadmin && session.clubName && (
              <span className="px-2 py-0.5 rounded-full text-xs font-medium bg-purple-100 dark:bg-purple-900/40 text-purple-700 dark:text-purple-300 shrink-0">{session.clubName}</span>
            )}
            <span className="text-xs text-gray-500 dark:text-gray-400 flex items-center gap-1 shrink-0">
              <Users size={12} /> {boatCount} · {totalBooked}/{totalCapacity}
            </span>
          </div>
          <div className="flex items-center gap-1 sm:gap-2 shrink-0">
            {session.status === 'DRAFT' && (
              <button onClick={() => approveSession(session.id)} className="text-green-600 dark:text-green-400 hover:bg-green-50 hover:dark:bg-green-900/30 p-1.5 rounded" title="Approve"><Check size={16} /></button>
            )}
            <button onClick={() => setShowBoatForm(session.id)} className="text-primary-600 dark:text-primary-400 hover:bg-primary-50 hover:dark:bg-primary-900/30 p-1.5 rounded" title="Add boat"><Plus size={16} /></button>
            <button onClick={() => deleteSession(session.id)} className="text-red-500 dark:text-red-400 hover:bg-red-50 hover:dark:bg-red-900/30 p-1.5 rounded" title="Delete"><Trash2 size={16} /></button>
          </div>
        </div>

        {!isCollapsed && (
          <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-3">
            {session.boats?.map(boat => {
              const rowingBookings = (boat.bookings || []).filter(bk => !bk.isCoxSeat);
              const coxBookings = (boat.bookings || []).filter(bk => bk.isCoxSeat);
              return (
              <div key={boat.id} className={clsx('p-3 border rounded-lg', dragOverBoat === boat.id ? 'border-primary-500 bg-primary-50 dark:bg-primary-900/30' : 'border-gray-200 dark:border-gray-700')}>
                <div className="flex items-center justify-between mb-2">
                  <span className="font-medium text-sm truncate">{boat.name}</span>
                  <div className="flex items-center gap-1">
                    <Users size={12} className="text-gray-400 dark:text-gray-500" />
                    <span className="text-xs text-gray-500 dark:text-gray-400">{boat.currentBookings}/{boat.capacity}</span>
                    <button
                      onClick={() => { setAddingTo({ boatId: boat.id, sessionId: session.id, hasCoxSeat: boat.hasCoxSeat ?? false }); setAddAsCox(false); }}
                      className="text-gray-400 dark:text-gray-500 hover:text-primary-600 hover:dark:text-primary-400 ml-1"
                      title="Add user"
                      disabled={!boat.hasCoxSeat && boat.currentBookings >= boat.capacity}
                    >
                      <UserPlus size={12} />
                    </button>
                    <button onClick={() => deleteBoat(boat.id)} className="text-red-400 hover:text-red-600 hover:dark:text-red-400 ml-1" title="Delete boat"><Trash2 size={12} /></button>
                  </div>
                </div>
                <div className="flex items-center gap-2 mb-2 text-xs text-gray-500 dark:text-gray-400">
                  <span>{boat.type}</span>
                  {boat.isBasicTrainingBoat && <span className="px-1 py-0.5 bg-amber-100 dark:bg-amber-900/40 text-amber-700 dark:text-amber-300 rounded">Beginner</span>}
                  {boat.hasCoxSeat && <span className="px-1 py-0.5 bg-blue-100 dark:bg-blue-900/40 text-blue-700 dark:text-blue-300 rounded">Cox</span>}
                </div>
                <div className="text-xs font-medium text-gray-500 dark:text-gray-400 mb-1">Rowers</div>
                <div
                  onDragOver={(e) => { e.preventDefault(); setDragOverBoat(boat.id); }}
                  onDragLeave={() => setDragOverBoat(prev => prev === boat.id ? null : prev)}
                  onDrop={(e) => {
                    e.preventDefault();
                    setDragOverBoat(null);
                    const d = dragRef.current;
                    if (d) moveUser(d.userId, d.fromBoatId, boat.id, false);
                    dragRef.current = null;
                  }}
                  className="space-y-1 min-h-[24px] rounded p-1 -mx-1 transition-colors"
                >
                  {rowingBookings.length === 0 && (
                    <div className="text-xs text-gray-300 dark:text-gray-600 italic px-2 py-1">Drop here</div>
                  )}
                  {rowingBookings.map(bk => (
                    <div
                      key={bk.id}
                      draggable
                      onDragStart={() => { dragRef.current = { userId: bk.userId, fromBoatId: boat.id }; }}
                      onDragEnd={() => { dragRef.current = null; setDragOverBoat(null); }}
                      className="flex items-center justify-between bg-gray-50 dark:bg-gray-900 hover:bg-gray-100 px-2 py-1 rounded text-xs cursor-grab active:cursor-grabbing"
                    >
                      <div className="flex items-center gap-1.5 min-w-0">
                        <span className={bk.userRole === 'MEMBER' ? 'badge-member' : bk.userRole === 'TRAINER' ? 'badge-student' : 'badge-member'}>
                          {bk.userRole === 'MEMBER' ? 'M' : bk.userRole === 'TRAINER' ? 'T' : 'A'}
                        </span>
                        <span className="text-gray-700 dark:text-gray-300 truncate">{bk.userFullName}</span>
                      </div>
                      <button onClick={() => removeBooking(bk.id)} className="text-gray-400 dark:text-gray-500 hover:text-red-500 hover:dark:text-red-400 shrink-0" title="Remove">
                        <X size={12} />
                      </button>
                    </div>
                  ))}
                </div>
                {boat.hasCoxSeat && (
                  <>
                    <div className="text-xs font-medium text-blue-600 dark:text-blue-400 mt-2 mb-1">Cox Seat</div>
                    <div
                      onDragOver={(e) => { e.preventDefault(); setDragOverBoat(boat.id); }}
                      onDragLeave={() => setDragOverBoat(prev => prev === boat.id ? null : prev)}
                      onDrop={(e) => {
                        e.preventDefault();
                        setDragOverBoat(null);
                        const d = dragRef.current;
                        if (d) moveUser(d.userId, d.fromBoatId, boat.id, true);
                        dragRef.current = null;
                      }}
                      className="space-y-1 min-h-[24px] rounded p-1 -mx-1 transition-colors"
                    >
                      {coxBookings.length === 0 && (
                        <div className="text-xs text-gray-300 dark:text-gray-600 italic px-2 py-1">Drop here</div>
                      )}
                      {coxBookings.map(bk => (
                        <div
                          key={bk.id}
                          draggable
                          onDragStart={() => { dragRef.current = { userId: bk.userId, fromBoatId: boat.id }; }}
                          onDragEnd={() => { dragRef.current = null; setDragOverBoat(null); }}
                          className="flex items-center justify-between bg-blue-50 dark:bg-blue-900/20 hover:bg-blue-100 hover:dark:bg-blue-900/40 px-2 py-1 rounded text-xs cursor-grab active:cursor-grabbing border border-blue-200 dark:border-blue-800"
                        >
                          <div className="flex items-center gap-1.5 min-w-0">
                            <span className={bk.userRole === 'MEMBER' ? 'badge-member' : bk.userRole === 'TRAINER' ? 'badge-student' : 'badge-member'}>
                              {bk.userRole === 'MEMBER' ? 'M' : bk.userRole === 'TRAINER' ? 'T' : 'A'}
                            </span>
                            <span className="text-gray-700 dark:text-gray-300 truncate">{bk.userFullName}</span>
                            <span className="text-[10px] text-blue-500 dark:text-blue-400 font-medium">(cox)</span>
                          </div>
                          <button onClick={() => removeBooking(bk.id)} className="text-gray-400 dark:text-gray-500 hover:text-red-500 hover:dark:text-red-400 shrink-0" title="Remove">
                            <X size={12} />
                          </button>
                        </div>
                      ))}
                    </div>
                  </>
                )}
              </div>
              );
            })}
          </div>
        )}

        {showBoatForm === session.id && (
          <div className="mt-4 p-4 bg-gray-50 dark:bg-gray-900 rounded-lg">
            <h4 className="font-medium text-sm mb-3">Add Boat</h4>
            <div className="grid grid-cols-2 gap-3">
              <select value={boatForm.type} onChange={e => setBoatForm(p => ({ ...p, type: e.target.value }))} className="input text-sm">
                <option value="COASTAL">Coastal</option>
                <option value="OLYMPIC">Olympic</option>
              </select>
              <select value={boatForm.capacity} onChange={e => setBoatForm(p => ({ ...p, capacity: parseInt(e.target.value) }))} className="input text-sm">
                {(boatForm.type === 'COASTAL' ? [1, 2, 4] : [1, 2, 4, 8]).map(c => (
                  <option key={c} value={c}>{c} seats</option>
                ))}
              </select>
              <input value={boatForm.name} onChange={e => setBoatForm(p => ({ ...p, name: e.target.value }))} placeholder="Boat name" className="input text-sm" />
              <label className="flex items-center gap-2 text-sm">
                <input type="checkbox" checked={boatForm.isBasicTrainingBoat} onChange={e => setBoatForm(p => ({ ...p, isBasicTrainingBoat: e.target.checked }))} className="w-4 h-4 rounded" />
                Basic Training
              </label>
              <label className="flex items-center gap-2 text-sm">
                <input type="checkbox" checked={boatForm.hasCoxSeat} onChange={e => setBoatForm(p => ({ ...p, hasCoxSeat: e.target.checked }))} className="w-4 h-4 rounded" />
                Cox Seat
              </label>
            </div>
            <div className="flex gap-2 mt-3">
              <button onClick={() => addBoat(session.id)} className="btn-primary text-sm">Add</button>
              <button onClick={() => setShowBoatForm(null)} className="btn-secondary text-sm">Cancel</button>
            </div>
          </div>
        )}
      </div>
    );
  }

  return (
    <ProtectedRoute adminOnly>
      <div className="space-y-4">
        <div className="flex items-center justify-between">
          <h1 className="text-2xl font-bold text-gray-900 dark:text-gray-100">Daily Planner</h1>
          <div className="flex items-center gap-2">
            <button onClick={() => setViewMode('week')} className={clsx('px-3 py-1.5 rounded-lg text-sm font-medium', viewMode === 'week' ? 'bg-primary-600 text-white' : 'bg-gray-100 dark:bg-gray-800')}>Week</button>
            <button onClick={() => setViewMode('day')} className={clsx('px-3 py-1.5 rounded-lg text-sm font-medium', viewMode === 'day' ? 'bg-primary-600 text-white' : 'bg-gray-100 dark:bg-gray-800')}>Day</button>
          </div>
        </div>

        <div className="flex items-center justify-between">
          <button onClick={() => { const d = new Date(currentDate); d.setDate(d.getDate() - (viewMode === 'week' ? 7 : 1)); setCurrentDate(d); }} className="p-2 hover:bg-gray-100 hover:dark:bg-gray-800 rounded-lg"><ChevronLeft size={20} /></button>
          <h2 className="text-sm sm:text-lg font-semibold text-center flex-1 min-w-0">
            {viewMode === 'week'
              ? `${weekDates[0].toLocaleDateString('en-US', { month: 'short', day: 'numeric' })} — ${weekDates[6].toLocaleDateString('en-US', { month: 'short', day: 'numeric', year: 'numeric' })}`
              : currentDate.toLocaleDateString('en-US', { weekday: 'short', month: 'short', day: 'numeric', year: 'numeric' })}
          </h2>
          <button onClick={() => { const d = new Date(currentDate); d.setDate(d.getDate() + (viewMode === 'week' ? 7 : 1)); setCurrentDate(d); }} className="p-2 hover:bg-gray-100 hover:dark:bg-gray-800 rounded-lg"><ChevronRight size={20} /></button>
        </div>

        <div className="flex flex-wrap gap-2">
          <button onClick={() => setShowCreateForm(true)} className="btn-primary text-sm flex items-center gap-1"><Plus size={14} />New Session</button>
          <button onClick={() => setShowCopyModal(true)} className="btn-secondary text-sm flex items-center gap-1">
            <Copy size={14} />{viewMode === 'week' ? 'Copy Week' : 'Copy Day'}
          </button>
          <button
            onClick={() => setCollapsed(daySessions.length === collapsed.size ? new Set() : new Set(daySessions.map(s => s.id)))}
            className="btn-secondary text-sm flex items-center gap-1"
          >
            {daySessions.length > 0 && daySessions.every(s => collapsed.has(s.id)) ? <ChevronDown size={14} /> : <ChevronUp size={14} />}
            {daySessions.length > 0 && daySessions.every(s => collapsed.has(s.id)) ? 'Expand all' : 'Collapse all'}
          </button>
          {selectedSessions.length > 0 && (
            <>
              <button onClick={bulkApprove} className="btn-primary text-sm flex items-center gap-1"><Check size={14} />Approve ({selectedSessions.length})</button>
              <button onClick={bulkDelete} className="btn-danger text-sm flex items-center gap-1"><Trash2 size={14} />Delete ({selectedSessions.length})</button>
            </>
          )}
        </div>

        {loading ? (
          <div className="flex justify-center py-12"><div className="animate-spin rounded-full h-8 w-8 border-b-2 border-primary-600"></div></div>
        ) : viewMode === 'week' ? (
          <div key={`week-${fmt(weekStart)}`} className="animate-page-enter space-y-6">
            {visibleSessions.length === 0 && <div className="card text-center text-gray-500 dark:text-gray-400 py-12">No sessions this week</div>}
            {groupedByDate.map(group => {
              if (group.sessions.length === 0) return null;
              const dayBooked = group.sessions.reduce((a, s) => a + (s.boats?.reduce((x, b) => x + b.currentBookings, 0) || 0), 0);
              const dayCapacity = group.sessions.reduce((a, s) => a + (s.boats?.reduce((x, b) => x + b.capacity, 0) || 0), 0);
              const isToday = fmt(new Date()) === group.dateStr;
              const isDayCollapsed = collapsedDays.has(group.dateStr);
              return (
                <div key={group.dateStr} className="space-y-3">
                  <div className={clsx('flex items-center justify-between gap-2 pb-2 border-b', isToday ? 'border-primary-300' : 'border-gray-200 dark:border-gray-700')}>
                    <div className="flex items-center gap-2 min-w-0">
                      <button
                        onClick={() => toggleDayCollapse(group.dateStr)}
                        className="p-1 hover:bg-gray-100 hover:dark:bg-gray-800 rounded text-gray-500 dark:text-gray-400 shrink-0"
                        title={isDayCollapsed ? 'Expand day' : 'Collapse day'}
                      >
                        {isDayCollapsed ? <ChevronDown size={16} /> : <ChevronUp size={16} />}
                      </button>
                      <button
                        onClick={() => { setCurrentDate(group.date); setViewMode('day'); }}
                        className={clsx('text-sm sm:text-base font-semibold hover:underline text-left truncate', isToday ? 'text-primary-700 dark:text-primary-300' : 'text-gray-900 dark:text-gray-100')}
                      >
                        {group.date.toLocaleDateString('en-US', { weekday: 'short', month: 'short', day: 'numeric' })}
                      </button>
                    </div>
                    <span className="text-xs text-gray-500 dark:text-gray-400 flex items-center gap-1 shrink-0">
                      <Users size={12} />
                      {group.sessions.length} · {dayBooked}/{dayCapacity}
                    </span>
                  </div>
                  {!isDayCollapsed && group.sessions.map(session => renderSession(session))}
                </div>
              );
            })}
          </div>
        ) : (
          <div key={`day-${fmt(currentDate)}`} className="animate-page-enter space-y-4">
            {daySessions.length === 0 && <div className="card text-center text-gray-500 dark:text-gray-400 py-12">No sessions for this day</div>}
            {daySessions.map(session => renderSession(session))}
          </div>
        )}


        {showCreateForm && (
          <div className="fixed inset-0 bg-black/40 flex items-center justify-center z-50" onClick={() => setShowCreateForm(false)}>
            <div className="bg-white dark:bg-gray-800 rounded-xl shadow-xl p-6 max-w-sm w-full mx-4" onClick={e => e.stopPropagation()}>
              <h3 className="text-lg font-semibold mb-4 text-gray-900 dark:text-gray-100">New Session</h3>
              <div className="space-y-3">
                <div>
                  <label className="flex items-center gap-1.5 text-xs font-medium text-gray-600 dark:text-gray-400 mb-1">
                    <CalendarIcon size={12} /> Date
                  </label>
                  <input
                    type="date"
                    value={formData.date || fmt(currentDate)}
                    onChange={e => setFormData(p => ({ ...p, date: e.target.value }))}
                    className="input"
                  />
                </div>
                <div className="grid grid-cols-2 gap-2">
                  <div>
                    <label className="flex items-center gap-1.5 text-xs font-medium text-gray-600 dark:text-gray-400 mb-1">
                      <ClockIcon size={12} /> Start
                    </label>
                    <input
                      type="text"
                      inputMode="numeric"
                      pattern="[0-9]{2}:[0-9]{2}"
                      maxLength={5}
                      placeholder="HH:MM"
                      value={formData.startTime}
                      onChange={e => setFormData(p => ({ ...p, startTime: maskHHMM(e.target.value) }))}
                      className="input tabular-nums"
                    />
                  </div>
                  <div>
                    <label className="flex items-center gap-1.5 text-xs font-medium text-gray-600 dark:text-gray-400 mb-1">
                      <ClockIcon size={12} /> End
                    </label>
                    <input
                      type="text"
                      inputMode="numeric"
                      pattern="[0-9]{2}:[0-9]{2}"
                      maxLength={5}
                      placeholder="HH:MM"
                      value={formData.endTime}
                      onChange={e => setFormData(p => ({ ...p, endTime: maskHHMM(e.target.value) }))}
                      className="input tabular-nums"
                    />
                  </div>
                </div>
              </div>
              <div className="flex gap-3 mt-5">
                <button onClick={() => setShowCreateForm(false)} className="btn-secondary flex-1">Cancel</button>
                <button onClick={createSession} className="btn-primary flex-1">Create</button>
              </div>
            </div>
          </div>
        )}

        {showCopyModal && (
          <div className="fixed inset-0 bg-black/40 flex items-center justify-center z-50" onClick={() => setShowCopyModal(false)}>
            <div className="bg-white dark:bg-gray-800 rounded-xl shadow-xl p-6 max-w-sm w-full mx-4" onClick={e => e.stopPropagation()}>
              <h3 className="text-lg font-semibold mb-4">
                {viewMode === 'week' ? 'Copy Week Plan' : 'Copy Day Plan'}
              </h3>
              <p className="text-sm text-gray-600 dark:text-gray-400 mb-3">
                {viewMode === 'week'
                  ? <>Copy all sessions from week of <strong>{fmt(weekStart)}</strong> to the week starting:</>
                  : <>Copy all sessions from <strong>{fmt(currentDate)}</strong> to:</>}
              </p>
              <input type="date" value={copyTarget} onChange={e => setCopyTarget(e.target.value)} className="input" />
              {viewMode === 'week' && (
                <p className="text-xs text-gray-400 dark:text-gray-500 mt-2">Pick the Monday of the target week. Each day of the source week will be copied to the matching weekday.</p>
              )}
              <div className="flex gap-3 mt-4">
                <button onClick={() => setShowCopyModal(false)} className="btn-secondary flex-1">Cancel</button>
                <button onClick={viewMode === 'week' ? copyWeek : copyDay} className="btn-primary flex-1">Copy</button>
              </div>
            </div>
          </div>
        )}

        {addingTo && (
          <div className="fixed inset-0 bg-black/40 flex items-center justify-center z-50" onClick={() => setAddingTo(null)}>
            <div className="bg-white dark:bg-gray-800 rounded-xl shadow-xl p-6 max-w-md w-full mx-4" onClick={e => e.stopPropagation()}>
              <h3 className="text-lg font-semibold mb-3">Add User to Boat</h3>
              {hasCoxSeat && (
                <label className="flex items-center gap-2 text-sm mb-3">
                  <input type="checkbox" checked={addAsCox} onChange={e => setAddAsCox(e.target.checked)} className="w-4 h-4 rounded" />
                  <span>Book as <strong>Cox Seat</strong></span>
                </label>
              )}
              <input
                autoFocus
                value={userQuery}
                onChange={e => setUserQuery(e.target.value)}
                placeholder="Search by name or email…"
                className="input mb-3"
              />
              <div className="max-h-64 overflow-y-auto divide-y divide-gray-100">
                {userResults.length === 0 && (
                  <div className="text-sm text-gray-400 dark:text-gray-500 py-4 text-center">No users found</div>
                )}
                {userResults.map(u => {
                  const noCredit = (u.creditBalance ?? 0) < 1;
                  return (
                    <button
                      key={u.id}
                      disabled={noCredit || u.role === 'CLUB_ADMIN' || u.role === 'SUPERADMIN'}
                      onClick={() => adminAddUser(u.id)}
                      className="w-full flex items-center justify-between py-2 px-1 hover:bg-gray-50 hover:dark:bg-gray-900 disabled:opacity-50 disabled:cursor-not-allowed text-left"
                    >
                      <div className="min-w-0">
                        <div className="text-sm font-medium text-gray-900 dark:text-gray-100 truncate">{u.fullName}</div>
                        <div className="text-xs text-gray-500 dark:text-gray-400 truncate">{u.email} · {u.role}</div>
                      </div>
                      <div className={clsx('text-xs font-medium shrink-0 ml-3', noCredit ? 'text-red-500 dark:text-red-400' : 'text-gray-600 dark:text-gray-400')}>
                        {u.creditBalance ?? 0} cr
                      </div>
                    </button>
                  );
                })}
              </div>
              <div className="flex gap-3 mt-4">
                <button onClick={() => setAddingTo(null)} className="btn-secondary flex-1">Cancel</button>
              </div>
            </div>
          </div>
        )}
      </div>
    </ProtectedRoute>
  );
}
