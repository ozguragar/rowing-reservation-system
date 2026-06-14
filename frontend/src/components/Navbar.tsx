'use client';

import { useState, useEffect } from 'react';
import { useAuth } from '@/context/AuthContext';
import { useSettings } from '@/context/SettingsContext';
import Link from 'next/link';
import { usePathname } from 'next/navigation';
import {
  LogOut, LayoutDashboard, Calendar, Wallet, Clock, BarChart3,
  FileText, Users, Zap, Menu, X, Inbox, Settings, Building2,
} from 'lucide-react';
import clsx from 'clsx';

export default function Navbar() {
  const { user, logout } = useAuth();
  const { settings } = useSettings();
  const pathname = usePathname();
  const [open, setOpen] = useState(false);
  const [rendered, setRendered] = useState(false);

  // Keep dropdown mounted while it animates open; unmount only after close animation completes.
  useEffect(() => {
    if (open) setRendered(true);
  }, [open]);

  useEffect(() => { setOpen(false); }, [pathname]);

  if (!user) return null;

  const isAdmin = user.role === 'CLUB_ADMIN' || user.role === 'SUPERADMIN' || user.role === 'TRAINER';
  const isSuperadmin = user.role === 'SUPERADMIN';
  const availabilityDisabled = settings.disable_availability === 'true' || user.featureAvailabilityModule === false;

  const userLinksAll = [
    { href: '/dashboard', label: 'Dashboard', icon: LayoutDashboard },
    { href: '/booking', label: 'RSVP & Book', icon: Calendar },
    { href: '/ledger', label: 'My Ledger', icon: Wallet },
    { href: '/availability', label: 'Availability', icon: Clock },
  ];
  const userLinks = availabilityDisabled
    ? userLinksAll.filter(l => l.href !== '/availability')
    : userLinksAll;

  const adminLinks = [
    { href: '/dashboard', label: 'Dashboard', icon: LayoutDashboard },
    { href: '/admin/planner', label: 'Planner', icon: Calendar },
    { href: '/admin/cancellations', label: 'Cancellations', icon: Inbox },
    { href: '/admin/analytics', label: 'Analytics', icon: BarChart3 },
    { href: '/admin/logs', label: 'Logs', icon: FileText },
    { href: '/admin/members', label: 'Members', icon: Users },
    { href: '/admin/scheduler', label: 'Scheduler', icon: Zap },
    { href: '/admin/messages', label: 'Messages', icon: Inbox },
    { href: '/admin/settings', label: 'System', icon: Settings },
  ];

  const superadminLinks = [
    { href: '/superadmin', label: 'Clubs', icon: Building2 },
    { href: '/dashboard', label: 'Dashboard', icon: LayoutDashboard },
    { href: '/admin/planner', label: 'Planner', icon: Calendar },
    { href: '/admin/members', label: 'Members', icon: Users },
    { href: '/admin/logs', label: 'Logs', icon: FileText },
    { href: '/admin/settings', label: 'System', icon: Settings },
  ];

  const links = isSuperadmin ? superadminLinks : isAdmin ? adminLinks : userLinks;

  const roleBadge = (
    <span className={clsx('px-2 py-0.5 rounded-full text-xs font-medium',
      (user.role === 'CLUB_ADMIN' || user.role === 'SUPERADMIN') ? 'bg-purple-100 text-purple-700 dark:bg-purple-900 dark:text-purple-200' :
      user.role === 'TRAINER' ? 'bg-blue-100 text-blue-700 dark:bg-blue-900 dark:text-blue-200' :
      'bg-green-100 text-green-700 dark:bg-green-900 dark:text-green-200'
    )}>
      {user.role === 'CLUB_ADMIN' ? 'Admin' : user.role === 'SUPERADMIN' ? 'Superadmin' : user.role.charAt(0) + user.role.slice(1).toLowerCase()}
    </span>
  );

  return (
    <nav className="bg-white border-b border-gray-200 sticky top-0 z-50 dark:bg-gray-900 dark:border-gray-700">
      <div className="max-w-7xl mx-auto px-3 sm:px-4">
        <div className="flex items-center justify-between h-14 md:h-16 gap-2">
          <div className="flex items-center gap-1 min-w-0">
            <Link href="/dashboard" className="text-lg md:text-xl font-bold text-primary-700 dark:text-primary-400 md:mr-6 shrink-0">
              Rowing Club
            </Link>
            <div className={clsx('hidden items-center gap-1', isAdmin ? 'xl:flex' : 'lg:flex')}>
              {links.map(({ href, label, icon: Icon }) => (
                <Link key={href} href={href}
                  className={clsx(
                    'flex items-center gap-1.5 py-2 rounded-lg text-sm font-medium whitespace-nowrap transition-colors',
                    isAdmin ? 'px-2' : 'px-3',
                    pathname === href
                      ? 'bg-primary-50 text-primary-700 dark:bg-primary-900/30 dark:text-primary-300'
                      : 'text-gray-600 hover:bg-gray-100 dark:text-gray-300 dark:hover:bg-gray-800'
                  )}>
                  <Icon size={16} />
                  {label}
                </Link>
              ))}
            </div>
          </div>
          <div className="flex items-center gap-2 sm:gap-3 shrink-0">
            <Link
              href={`/account/${user.id}`}
              className="hidden sm:inline-block text-sm text-gray-600 hover:text-primary-600 dark:text-gray-300 dark:hover:text-primary-400 hover:underline truncate max-w-[8rem] xl:max-w-[10rem] pr-2 border-r border-gray-200 dark:border-gray-700"
            >
              {user.fullName}
            </Link>
            <Link
              href="/settings"
              className="hidden sm:inline-block text-gray-400 hover:text-primary-600 dark:text-gray-500 dark:hover:text-primary-400 transition-colors duration-150 hover:rotate-45 active:scale-90"
              title="User settings"
              aria-label="User settings"
            >
              <Settings size={18} />
            </Link>
            <button
              onClick={logout}
              className="hidden sm:inline-block text-gray-400 hover:text-red-500 transition-all duration-150 active:scale-90"
              title="Logout"
              aria-label="Logout"
            >
              <LogOut size={18} />
            </button>
            <button
              onClick={() => setOpen(o => !o)}
              className={clsx(
                'p-2 -mr-2 text-gray-600 hover:bg-gray-100 rounded-lg dark:text-gray-300 dark:hover:bg-gray-800',
                'transition-transform duration-150 active:scale-90',
                isAdmin ? 'xl:hidden' : 'lg:hidden'
              )}
              aria-label={open ? 'Close menu' : 'Open menu'}
            >
              <span className="relative block w-[22px] h-[22px]">
                <Menu
                  size={22}
                  className={clsx(
                    'absolute inset-0 transition-all duration-200 ease-out',
                    open ? 'opacity-0 rotate-90 scale-75' : 'opacity-100 rotate-0 scale-100'
                  )}
                />
                <X
                  size={22}
                  className={clsx(
                    'absolute inset-0 transition-all duration-200 ease-out',
                    open ? 'opacity-100 rotate-0 scale-100' : 'opacity-0 -rotate-90 scale-75'
                  )}
                />
              </span>
            </button>
          </div>
        </div>

        {rendered && (
          <div
            className={clsx(
              'border-t border-gray-100 dark:border-gray-700 py-2 space-y-1',
              open ? 'animate-menu-open' : 'animate-menu-close',
              isAdmin ? 'xl:hidden' : 'lg:hidden'
            )}
            onAnimationEnd={(e) => {
              // Only unmount after the CLOSE animation of the wrapper itself finishes.
              // Ignore the stagger animations bubbling from child links.
              if (!open && e.target === e.currentTarget) setRendered(false);
            }}
          >
            <div className="px-2 pb-2 flex items-center justify-between gap-2 sm:hidden">
              <div className="flex items-center gap-2 min-w-0 text-sm">
                <Link href={`/account/${user.id}`} className="text-gray-700 dark:text-gray-200 truncate hover:underline">
                  {user.fullName}
                </Link>
                {roleBadge}
              </div>
              <div className="flex items-center gap-2">
                <Link href="/settings" className="text-gray-400 hover:text-primary-600 p-1" title="User settings" aria-label="User settings">
                  <Settings size={18} />
                </Link>
                <button onClick={logout} className="text-gray-400 hover:text-red-500 p-1" title="Logout">
                  <LogOut size={18} />
                </button>
              </div>
            </div>
            {links.map(({ href, label, icon: Icon }) => (
              <Link key={href} href={href}
                className={clsx(
                  'flex items-center gap-2 px-3 py-2 rounded-lg text-sm font-medium',
                  pathname === href
                    ? 'bg-primary-50 text-primary-700 dark:bg-primary-900/30 dark:text-primary-300'
                    : 'text-gray-700 hover:bg-gray-100 dark:text-gray-300 dark:hover:bg-gray-800'
                )}>
                <Icon size={16} />
                {label}
              </Link>
            ))}
          </div>
        )}
      </div>
    </nav>
  );
}
