'use client';

import { useEffect, useState } from 'react';
import ProtectedRoute from '@/components/ProtectedRoute';
import { useAuth } from '@/context/AuthContext';
import api from '@/lib/api';
import { Club } from '@/types';
import { useDialog } from '@/context/DialogContext';
import { Building2, ToggleLeft, ToggleRight } from 'lucide-react';
import clsx from 'clsx';
import { useRouter } from 'next/navigation';

export default function SuperadminPage() {
  const { user } = useAuth();
  const { toast } = useDialog();
  const router = useRouter();
  const [clubs, setClubs] = useState<Club[]>([]);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState<number | null>(null);

  useEffect(() => {
    if (user?.role !== 'SUPERADMIN') {
      router.push('/dashboard');
      return;
    }
    loadClubs();
  }, [user, router]);

  async function loadClubs() {
    try {
      const res = await api.get('/superadmin/clubs');
      setClubs(res.data);
    } catch (e) {
      console.error(e);
    } finally {
      setLoading(false);
    }
  }

  async function toggleFeature(clubId: number, feature: keyof Club, value: boolean) {
    setSaving(clubId);
    try {
      await api.put(`/superadmin/clubs/${clubId}/features`, { [feature]: value });
      setClubs(prev => prev.map(c => c.id === clubId ? { ...c, [feature]: value } : c));
      toast('Feature updated', 'success');
    } catch (e: any) {
      toast(e.response?.data?.message || 'Failed to update feature');
    } finally {
      setSaving(null);
    }
  }

  async function impersonate(email: string) {
    try {
      const res = await api.post(`/superadmin/impersonate?email=${encodeURIComponent(email)}`);
      localStorage.setItem('user', JSON.stringify(res.data.user));
      window.location.href = '/dashboard';
    } catch (e: any) {
      toast(e.response?.data?.message || 'Impersonation failed');
    }
  }

  const features: { key: keyof Club; label: string }[] = [
    { key: 'featureAvailabilityModule', label: 'Availability Module' },
    { key: 'featureCancellationRequests', label: 'Cancellation Requests' },
    { key: 'featureAutoScheduler', label: 'Auto-Scheduler' },
    { key: 'featureShowBookedMembers', label: 'Show Booked Members' },
  ];

  if (loading) {
    return (
      <ProtectedRoute>
        <div className="flex justify-center py-12">
          <div className="animate-spin rounded-full h-8 w-8 border-b-2 border-primary-600"></div>
        </div>
      </ProtectedRoute>
    );
  }

  return (
    <ProtectedRoute>
      <div className="space-y-6">
        <div className="flex items-center gap-3">
          <Building2 className="text-primary-600" size={28} />
          <h1 className="text-2xl font-bold text-gray-900 dark:text-gray-100">Club Management</h1>
        </div>

        <div className="grid grid-cols-1 lg:grid-cols-2 xl:grid-cols-3 gap-6">
          {clubs.map(club => (
            <div key={club.id} className="card">
              <div className="flex items-center justify-between mb-4">
                <h2 className="text-lg font-semibold text-gray-900 dark:text-gray-100">{club.name}</h2>
                <span className="text-xs text-gray-400 dark:text-gray-500">ID: {club.id}</span>
              </div>

              <div className="space-y-3 mb-4">
                {features.map(({ key, label }) => {
                  const enabled = club[key] as boolean;
                  return (
                    <div key={key} className="flex items-center justify-between">
                      <span className="text-sm text-gray-700 dark:text-gray-300">{label}</span>
                      <button
                        onClick={() => toggleFeature(club.id, key, !enabled)}
                        disabled={saving === club.id}
                        className={clsx('transition-colors', enabled ? 'text-green-500' : 'text-gray-400')}
                        title={enabled ? 'Enabled - click to disable' : 'Disabled - click to enable'}
                      >
                        {enabled ? <ToggleRight size={28} /> : <ToggleLeft size={28} />}
                      </button>
                    </div>
                  );
                })}
              </div>

              <div className="pt-3 border-t border-gray-200 dark:border-gray-700">
                <p className="text-xs text-gray-500 dark:text-gray-400 mb-2">
                  Created: {new Date(club.createdAt).toLocaleDateString()}
                </p>
              </div>
            </div>
          ))}
        </div>
      </div>
    </ProtectedRoute>
  );
}
