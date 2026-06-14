'use client';

import { useState } from 'react';
import ProtectedRoute from '@/components/ProtectedRoute';
import api from '@/lib/api';
import { useDialog } from '@/context/DialogContext';
import { useTheme } from '@/context/ThemeContext';
import { Moon, Sun, KeyRound } from 'lucide-react';

export default function UserSettingsPage() {
  const { toast } = useDialog();
  const { theme, toggle } = useTheme();
  const [currentPassword, setCurrentPassword] = useState('');
  const [newPassword, setNewPassword] = useState('');
  const [confirm, setConfirm] = useState('');
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState('');

  async function submitPassword(e: React.FormEvent) {
    e.preventDefault();
    setError('');
    if (newPassword.length < 6) {
      setError('New password must be at least 6 characters');
      return;
    }
    if (newPassword !== confirm) {
      setError('New password and confirmation do not match');
      return;
    }
    setSaving(true);
    try {
      await api.post('/users/me/password', { currentPassword, newPassword });
      toast('Password updated', 'success');
      setCurrentPassword('');
      setNewPassword('');
      setConfirm('');
    } catch (e: any) {
      setError(e.response?.data?.message || 'Failed to update password');
    } finally {
      setSaving(false);
    }
  }

  return (
    <ProtectedRoute>
      <div className="space-y-6 max-w-xl">
        <div>
          <h1 className="text-2xl font-bold text-gray-900 dark:text-gray-100">User Settings</h1>
          <p className="text-sm text-gray-500 dark:text-gray-400">Manage your password and preferences.</p>
        </div>

        <div className="card">
          <div className="flex items-center gap-2 mb-3">
            <KeyRound size={18} className="text-primary-600 dark:text-primary-400" />
            <h2 className="font-semibold text-gray-900 dark:text-gray-100">Change password</h2>
          </div>
          <form onSubmit={submitPassword} className="space-y-3">
            <div>
              <label className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">Current password</label>
              <input
                type="password"
                value={currentPassword}
                onChange={e => setCurrentPassword(e.target.value)}
                className="input"
                required
              />
            </div>
            <div>
              <label className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">New password</label>
              <input
                type="password"
                value={newPassword}
                onChange={e => setNewPassword(e.target.value)}
                className="input"
                minLength={6}
                required
              />
            </div>
            <div>
              <label className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">Confirm new password</label>
              <input
                type="password"
                value={confirm}
                onChange={e => setConfirm(e.target.value)}
                className="input"
                required
              />
            </div>
            {error && (
              <p className="text-sm text-red-600 dark:text-red-400" data-testid="password-error">{error}</p>
            )}
            <button type="submit" disabled={saving} className="btn-primary">
              {saving ? 'Saving…' : 'Update password'}
            </button>
          </form>
        </div>

        <div className="card flex items-center justify-between">
          <div>
            <div className="flex items-center gap-2 font-medium text-gray-900 dark:text-gray-100">
              {theme === 'dark' ? <Moon size={16} /> : <Sun size={16} />}
              Appearance
            </div>
            <p className="text-xs text-gray-500 dark:text-gray-400 mt-0.5">Switch between light and dark themes.</p>
          </div>
          <button
            onClick={toggle}
            aria-label="Toggle theme"
            className="relative inline-flex items-center"
            data-testid="theme-toggle"
          >
            <span className={`w-11 h-6 rounded-full transition-colors ${theme === 'dark' ? 'bg-primary-600' : 'bg-gray-300'}`} />
            <span className={`absolute top-[2px] w-5 h-5 bg-white dark:bg-gray-800 rounded-full transition-all ${theme === 'dark' ? 'left-[22px]' : 'left-[2px]'}`} />
          </button>
        </div>
      </div>
    </ProtectedRoute>
  );
}
