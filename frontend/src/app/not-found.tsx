export default function NotFound() {
  return (
    <div className="min-h-[60vh] flex items-center justify-center p-4">
      <div className="card max-w-md text-center">
        <p className="text-4xl font-bold text-primary-600 dark:text-primary-400 mb-2">404</p>
        <h1 className="text-lg font-semibold text-gray-900 dark:text-gray-100 mb-2">Page not found</h1>
        <p className="text-sm text-gray-500 dark:text-gray-400 mb-4">
          This page doesn&apos;t exist or was moved.
        </p>
        <a href="/dashboard" className="btn-primary text-sm">Go to dashboard</a>
      </div>
    </div>
  );
}
