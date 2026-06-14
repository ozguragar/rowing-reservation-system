'use client';

export default function GlobalError({ error, reset }: { error: Error & { digest?: string }; reset: () => void }) {
  return (
    <div className="min-h-[60vh] flex items-center justify-center p-4">
      <div className="card max-w-md text-center">
        <h1 className="text-xl font-bold text-gray-900 dark:text-gray-100 mb-2">
          Something went wrong
        </h1>
        <p className="text-sm text-gray-500 dark:text-gray-400 mb-4">
          We hit an unexpected error. You can try again or head back to your dashboard.
        </p>
        <div className="flex gap-2 justify-center">
          <button onClick={reset} className="btn-secondary text-sm">Try again</button>
          <a href="/dashboard" className="btn-primary text-sm">Dashboard</a>
        </div>
      </div>
    </div>
  );
}
