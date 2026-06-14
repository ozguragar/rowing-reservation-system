'use client';

import React, { createContext, useContext, useState, useCallback, useRef } from 'react';
import { X, CheckCircle, AlertCircle, AlertTriangle } from 'lucide-react';

type ToastType = 'success' | 'error' | 'warning';

interface Toast {
  id: number;
  message: string;
  type: ToastType;
}

interface ConfirmOptions {
  title?: string;
  message: string;
  confirmLabel?: string;
  cancelLabel?: string;
  danger?: boolean;
}

interface DialogContextType {
  toast: (message: string, type?: ToastType) => void;
  confirm: (options: ConfirmOptions | string) => Promise<boolean>;
}

const DialogContext = createContext<DialogContextType | null>(null);

let toastCounter = 0;

export function DialogProvider({ children }: { children: React.ReactNode }) {
  const [toasts, setToasts] = useState<Toast[]>([]);
  const [confirmState, setConfirmState] = useState<{
    opts: ConfirmOptions;
    resolve: (v: boolean) => void;
  } | null>(null);
  const resolveRef = useRef<((v: boolean) => void) | null>(null);

  const toast = useCallback((message: string, type: ToastType = 'error') => {
    const id = ++toastCounter;
    setToasts(prev => [...prev, { id, message, type }]);
    setTimeout(() => setToasts(prev => prev.filter(t => t.id !== id)), 4000);
  }, []);

  const confirm = useCallback((options: ConfirmOptions | string): Promise<boolean> => {
    const opts: ConfirmOptions = typeof options === 'string' ? { message: options } : options;
    return new Promise(resolve => {
      resolveRef.current = resolve;
      setConfirmState({ opts, resolve });
    });
  }, []);

  function handleConfirm(value: boolean) {
    if (resolveRef.current) resolveRef.current(value);
    resolveRef.current = null;
    setConfirmState(null);
  }

  const iconMap: Record<ToastType, React.ReactNode> = {
    success: <CheckCircle size={18} className="text-green-600 dark:text-green-400 shrink-0" />,
    error: <AlertCircle size={18} className="text-red-600 dark:text-red-400 shrink-0" />,
    warning: <AlertTriangle size={18} className="text-amber-500 dark:text-amber-400 shrink-0" />,
  };
  const bgMap: Record<ToastType, string> = {
    success: 'bg-green-50 border-green-200 dark:bg-green-900/30 dark:border-green-800',
    error: 'bg-red-50 border-red-200 dark:bg-red-900/30 dark:border-red-800',
    warning: 'bg-amber-50 border-amber-200 dark:bg-amber-900/30 dark:border-amber-800',
  };
  const textMap: Record<ToastType, string> = {
    success: 'text-green-800 dark:text-green-200',
    error: 'text-red-800 dark:text-red-200',
    warning: 'text-amber-800 dark:text-amber-200',
  };

  return (
    <DialogContext.Provider value={{ toast, confirm }}>
      {children}

      {/* Toast stack */}
      <div className="fixed top-4 left-1/2 -translate-x-1/2 z-[200] flex flex-col gap-2 w-full max-w-sm px-3 pointer-events-none">
        {toasts.map(t => (
          <div
            key={t.id}
            role="alert"
            className={`flex items-start gap-2 px-4 py-3 rounded-lg border shadow-md pointer-events-auto ${bgMap[t.type]}`}
          >
            {iconMap[t.type]}
            <p className={`text-sm flex-1 ${textMap[t.type]}`}>{t.message}</p>
            <button
              onClick={() => setToasts(prev => prev.filter(x => x.id !== t.id))}
              className="text-gray-400 hover:text-gray-600 dark:text-gray-500 dark:hover:text-gray-300 shrink-0"
            >
              <X size={14} />
            </button>
          </div>
        ))}
      </div>

      {/* Confirm dialog */}
      {confirmState && (
        <div
          className="fixed inset-0 bg-black/40 flex items-center justify-center z-[100]"
          onClick={() => handleConfirm(false)}
        >
          <div
            className="bg-white dark:bg-gray-800 rounded-xl shadow-xl p-6 max-w-sm w-full mx-4"
            onClick={e => e.stopPropagation()}
            role="dialog"
            aria-modal="true"
          >
            {confirmState.opts.title && (
              <h3 className="text-lg font-semibold text-gray-900 dark:text-gray-100 mb-2">{confirmState.opts.title}</h3>
            )}
            <p className="text-sm text-gray-600 dark:text-gray-300">{confirmState.opts.message}</p>
            <div className="flex gap-3 mt-5">
              <button
                onClick={() => handleConfirm(false)}
                className="btn-secondary flex-1"
              >
                {confirmState.opts.cancelLabel || 'Cancel'}
              </button>
              <button
                onClick={() => handleConfirm(true)}
                className={`flex-1 ${confirmState.opts.danger ? 'btn-danger' : 'btn-primary'}`}
                data-testid="confirm-ok"
              >
                {confirmState.opts.confirmLabel || 'Confirm'}
              </button>
            </div>
          </div>
        </div>
      )}
    </DialogContext.Provider>
  );
}

export function useDialog() {
  const ctx = useContext(DialogContext);
  if (!ctx) throw new Error('useDialog must be used within DialogProvider');
  return ctx;
}
