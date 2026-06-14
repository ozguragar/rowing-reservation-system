'use client';

import { usePathname } from 'next/navigation';
import { ReactNode } from 'react';

/**
 * Lightweight page transition: triggers a short fade-in + slide-up animation
 * whenever the pathname changes. The `key={pathname}` forces React to remount
 * the children subtree, which re-runs the CSS animation defined in globals.css.
 *
 * Respects prefers-reduced-motion — the CSS class becomes a no-op for users
 * who opt out of motion.
 */
export default function PageTransition({ children }: { children: ReactNode }) {
  const pathname = usePathname();
  return (
    <div key={pathname} className="animate-page-enter" data-testid="page-transition">
      {children}
    </div>
  );
}
