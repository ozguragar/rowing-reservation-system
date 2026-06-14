'use client';

import { useEffect, useState } from 'react';
import ProtectedRoute from '@/components/ProtectedRoute';
import api from '@/lib/api';
import { Save } from 'lucide-react';
import { useDialog } from '@/context/DialogContext';
import { useSettings } from '@/context/SettingsContext';

type Settings = {
  student_booking_hour?: string;
  student_next_day_only?: string;
  allow_cancellations?: string;
  show_booked_members?: string;
  booking_hour_disabled?: string;
  disable_availability?: string;
};

export default function SettingsPage() {
  const { toast } = useDialog();
  const { refresh: refreshGlobalSettings } = useSettings();
  const [settings, setSettings] = useState<Settings>({});
  const [loading, setLoading] = useState(true);
  const [savingKey, setSavingKey] = useState<string | null>(null);

  useEffect(() => { load(); }, []);

  async function load() {
    try {
      const res = await api.get('/admin/settings');
      setSettings(res.data);
    } catch (e) { console.error(e); }
    finally { setLoading(false); }
  }

  async function save(key: keyof Settings, value: string) {
    setSavingKey(key);
    try {
      await api.put(`/admin/settings/${key}`, { value });
      setSettings(s => ({ ...s, [key]: value }));
      // Refresh the global SettingsContext so Navbar / Availability page pick up the change
      await refreshGlobalSettings();
      toast('Setting saved', 'success');
    } catch (e: any) {
      toast(e.response?.data?.message || 'Failed to save');
    } finally {
      setSavingKey(null);
    }
  }

  const bookingHour = parseInt(settings.student_booking_hour || '16', 10);
  const nextDayOnly = settings.student_next_day_only === 'true';
  const allowCancellations = settings.allow_cancellations !== 'false';
  const showBookedMembers = settings.show_booked_members !== 'false';
  const bookingHourDisabled = settings.booking_hour_disabled === 'true';
  const availabilityDisabled = settings.disable_availability === 'true';

  return (
    <ProtectedRoute adminOnly>
      <div className="space-y-4 max-w-2xl">
        <div>
          <h1 className="text-2xl font-bold text-gray-900 dark:text-gray-100">System Settings</h1>
          <p className="text-sm text-gray-500 dark:text-gray-400">Changes take effect immediately across the app.</p>
        </div>

        {loading ? (
          <div className="flex justify-center py-12"><div className="animate-spin rounded-full h-8 w-8 border-b-2 border-primary-600"></div></div>
        ) : (
          <div className="space-y-3">
            <div className="card flex items-start justify-between gap-3">
              <div className="min-w-0 flex-1">
                <h3 className="font-medium text-gray-900 dark:text-gray-100">Student booking hour</h3>
                <p className="text-xs text-gray-500 dark:text-gray-400 mt-0.5">Cutoff hour. Students book after this hour; club members book before it.</p>
              </div>
              <select
                value={bookingHour}
                onChange={e => setSettings(s => ({ ...s, student_booking_hour: e.target.value }))}
                className="input w-24 shrink-0"
                aria-label="Student booking hour"
              >
                {Array.from({ length: 24 }, (_, i) => (
                  <option key={i} value={i}>{String(i).padStart(2, '0')}:00</option>
                ))}
              </select>
              <button
                onClick={() => save('student_booking_hour', String(bookingHour))}
                disabled={savingKey === 'student_booking_hour'}
                className="btn-primary text-sm flex items-center gap-1 shrink-0"
              >
                <Save size={14} /> Save
              </button>
            </div>

            <Toggle
              label="Allow cancellation requests"
              description="When off, users cannot request cancellation of their bookings."
              checked={allowCancellations}
              saving={savingKey === 'allow_cancellations'}
              onChange={v => save('allow_cancellations', String(v))}
            />

            <Toggle
              label="Next-day-only for students"
              description="When on, students can only book sessions for tomorrow."
              checked={nextDayOnly}
              saving={savingKey === 'student_next_day_only'}
              onChange={v => save('student_next_day_only', String(v))}
            />

            <Toggle
              label="Show booked member names"
              description="When off, members' names are hidden on boat cards."
              checked={showBookedMembers}
              saving={savingKey === 'show_booked_members'}
              onChange={v => save('show_booked_members', String(v))}
            />

            <Toggle
              label="Disable booking hour"
              description="When on, bypass all time-of-day booking restrictions for every user."
              checked={bookingHourDisabled}
              saving={savingKey === 'booking_hour_disabled'}
              onChange={v => save('booking_hour_disabled', String(v))}
            />

            <Toggle
              label="Disable availability feature"
              description="When on, hides the Availability tab for users."
              checked={availabilityDisabled}
              saving={savingKey === 'disable_availability'}
              onChange={v => save('disable_availability', String(v))}
            />
          </div>
        )}
      </div>
    </ProtectedRoute>
  );
}

function Toggle({ label, description, checked, saving, onChange }: {
  label: string; description: string; checked: boolean; saving: boolean;
  onChange: (v: boolean) => void;
}) {
  return (
    <div className="card flex items-start justify-between gap-4">
      <div className="min-w-0">
        <h3 className="font-medium text-gray-900 dark:text-gray-100">{label}</h3>
        <p className="text-xs text-gray-500 dark:text-gray-400 mt-0.5">{description}</p>
      </div>
      <label className="relative inline-flex items-center cursor-pointer shrink-0">
        <input
          type="checkbox"
          className="sr-only peer"
          checked={checked}
          disabled={saving}
          onChange={e => onChange(e.target.checked)}
        />
        <div className="w-11 h-6 bg-gray-200 dark:bg-gray-700 peer-checked:bg-primary-600 rounded-full peer peer-checked:after:translate-x-full after:content-[''] after:absolute after:top-[2px] after:left-[2px] after:bg-white after:rounded-full after:h-5 after:w-5 after:transition-all"></div>
      </label>
    </div>
  );
}
