'use client';

import React, { createContext, useContext, useEffect, useState, useCallback } from 'react';
import api from '@/lib/api';
import { useAuth } from '@/context/AuthContext';

type Settings = Record<string, string>;

interface SettingsContextType {
  settings: Settings;
  refresh: () => Promise<void>;
  loaded: boolean;
}

const SettingsContext = createContext<SettingsContextType | null>(null);

export function SettingsProvider({ children }: { children: React.ReactNode }) {
  const { user } = useAuth();
  const [settings, setSettings] = useState<Settings>({});
  const [loaded, setLoaded] = useState(false);

  const refresh = useCallback(async () => {
    try {
      const res = await api.get('/settings/public');
      setSettings(res.data || {});
    } catch {
      // unauthenticated users just stay with empty settings
    } finally {
      setLoaded(true);
    }
  }, []);

  useEffect(() => {
    // Fetch whenever the authenticated user changes (login, logout, token changes)
    if (user) {
      refresh();
    } else {
      setSettings({});
      setLoaded(true);
    }
  }, [user, refresh]);

  return (
    <SettingsContext.Provider value={{ settings, refresh, loaded }}>
      {children}
    </SettingsContext.Provider>
  );
}

export function useSettings() {
  const ctx = useContext(SettingsContext);
  if (!ctx) throw new Error('useSettings must be used within SettingsProvider');
  return ctx;
}
