'use client';

import React from 'react';
import { logger } from '@/lib/logger';

interface State {
  error: Error | null;
}

/**
 * Global error boundary. Catches uncaught render errors in the child tree
 * and shows a graceful fallback instead of a white screen. Only active in production —
 * dev mode lets Next.js's overlay show.
 */
export default class ErrorBoundary extends React.Component<{ children: React.ReactNode }, State> {
  state: State = { error: null };

  static getDerivedStateFromError(error: Error): State {
    return { error };
  }

  componentDidCatch(error: Error, info: React.ErrorInfo) {
    if (process.env.NODE_ENV !== 'production') {
      console.error('ErrorBoundary caught:', error, info);
    }
    logger.capture(error, { boundary: 'root', componentStack: (info.componentStack || '').slice(0, 500) });
  }

  reset = () => this.setState({ error: null });

  render() {
    if (this.state.error) {
      return (
        <div className="min-h-[60vh] flex items-center justify-center p-4">
          <div className="card max-w-md text-center">
            <h1 className="text-xl font-bold text-gray-900 dark:text-gray-100 mb-2">
              Something went wrong
            </h1>
            <p className="text-sm text-gray-500 dark:text-gray-400 mb-4">
              The page couldn&apos;t render. Try reloading, or return to the dashboard.
            </p>
            <div className="flex gap-2 justify-center">
              <button onClick={this.reset} className="btn-secondary text-sm">Try again</button>
              <a href="/dashboard" className="btn-primary text-sm">Dashboard</a>
            </div>
          </div>
        </div>
      );
    }
    return this.props.children;
  }
}
