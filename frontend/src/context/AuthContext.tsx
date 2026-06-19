'use client';

import React, { createContext, useContext, useState, useEffect, useCallback } from 'react';
import { User, AuthResponse } from '@/types';
import api from '@/lib/api';

interface AuthContextType {
  user: User | null;
  login: (email: string, password: string) => Promise<void>;
  register: (fullName: string, email: string, password: string, clubId?: number) => Promise<void>;
  logout: () => void;
  isLoading: boolean;
  isAuthenticated: boolean;
}

const AuthContext = createContext<AuthContextType | null>(null);

const USER_CACHE_KEY = 'user';   // cached user profile only — NOT the token

export function AuthProvider({ children }: { children: React.ReactNode }) {
  const [user, setUser] = useState<User | null>(null);
  const [isLoading, setIsLoading] = useState(true);

  // Hydrate from a cached user snapshot for instant UI, then verify against the
  // server. The real source of truth is the httpOnly cookie — if it is missing
  // or expired, /users/me returns 401 and we fall back to unauthenticated.
  useEffect(() => {
    let cancelled = false;

    // Clean up legacy tokens left over from the localStorage era.
    try {
      localStorage.removeItem('accessToken');
      localStorage.removeItem('refreshToken');
    } catch { /* ignore */ }

    const cached = typeof window !== 'undefined' ? localStorage.getItem(USER_CACHE_KEY) : null;
    if (cached) {
      try { setUser(JSON.parse(cached)); } catch { /* ignore bad cache */ }
    }

    (async () => {
      try {
        const res = await api.get<User>('/users/me');
        if (!cancelled) {
          setUser(res.data);
          localStorage.setItem(USER_CACHE_KEY, JSON.stringify(res.data));
        }
      } catch {
        if (!cancelled) {
          setUser(null);
          localStorage.removeItem(USER_CACHE_KEY);
        }
      } finally {
        if (!cancelled) setIsLoading(false);
      }
    })();

    return () => { cancelled = true; };
  }, []);

  const login = useCallback(async (email: string, password: string) => {
    const res = await api.post<AuthResponse>('/auth/login', { email, password });
    setUser(res.data.user);
    localStorage.setItem(USER_CACHE_KEY, JSON.stringify(res.data.user));
  }, []);

  const register = useCallback(async (fullName: string, email: string, password: string, clubId?: number) => {
    const res = await api.post<AuthResponse>('/auth/register', { fullName, email, password, clubId });
    setUser(res.data.user);
    localStorage.setItem(USER_CACHE_KEY, JSON.stringify(res.data.user));
  }, []);

  const logout = useCallback(async () => {
    try { await api.post('/auth/logout'); } catch { /* server side clear is best-effort */ }
    localStorage.removeItem(USER_CACHE_KEY);
    setUser(null);
    window.location.href = '/login';
  }, []);

  return (
    <AuthContext.Provider value={{ user, login, register, logout, isLoading, isAuthenticated: !!user }}>
      {children}
    </AuthContext.Provider>
  );
}

export function useAuth() {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error('useAuth must be used within AuthProvider');
  return ctx;
}
