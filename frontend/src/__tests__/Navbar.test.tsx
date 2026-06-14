import React from 'react';
import { render, screen, fireEvent } from '@testing-library/react';
import '@testing-library/jest-dom';
import Navbar from '@/components/Navbar';

const mockPush = jest.fn();
const mockPathname = jest.fn().mockReturnValue('/dashboard');
jest.mock('next/navigation', () => ({
  useRouter: () => ({ push: mockPush }),
  usePathname: () => mockPathname(),
}));

const mockLogout = jest.fn();
const mockUseAuth = jest.fn();
jest.mock('@/context/AuthContext', () => ({
  useAuth: () => mockUseAuth(),
}));

const mockUseSettings = jest.fn().mockReturnValue({ settings: {}, refresh: jest.fn(), loaded: true });
jest.mock('@/context/SettingsContext', () => ({
  useSettings: () => mockUseSettings(),
  SettingsProvider: ({ children }: any) => <>{children}</>,
}));

const studentUser = { id: 1, fullName: 'Alice Student', email: 'alice@test.com', role: 'STUDENT' as const, isFinishedBasicTraining: true, isOnSchoolTeam: false, lessonsAttended: 0 };
const adminUser = { ...studentUser, role: 'ADMIN' as const, fullName: 'Admin User' };

beforeEach(() => {
  mockLogout.mockReset();
  mockUseAuth.mockReturnValue({ user: studentUser, logout: mockLogout });
});

test('renders nothing when user is null', () => {
  mockUseAuth.mockReturnValue({ user: null, logout: mockLogout });
  const { container } = render(<Navbar />);
  expect(container).toBeEmptyDOMElement();
});

test('shows user links for student', () => {
  render(<Navbar />);
  expect(screen.getByText('RSVP & Book')).toBeInTheDocument();
  expect(screen.getByText('My Ledger')).toBeInTheDocument();
  expect(screen.queryByText('Planner')).not.toBeInTheDocument();
});

test('shows admin links for admin user', () => {
  mockUseAuth.mockReturnValue({ user: adminUser, logout: mockLogout });
  render(<Navbar />);
  expect(screen.getByText('Planner')).toBeInTheDocument();
  expect(screen.getByText('Members')).toBeInTheDocument();
  expect(screen.queryByText('RSVP & Book')).not.toBeInTheDocument();
});

test('admin navbar shows Members not Ledger', () => {
  mockUseAuth.mockReturnValue({ user: adminUser, logout: mockLogout });
  render(<Navbar />);
  expect(screen.getByText('Members')).toBeInTheDocument();
  // "Ledger" text should not appear as a standalone link label
  const ledgerLinks = screen.queryAllByText('Ledger');
  expect(ledgerLinks).toHaveLength(0);
});

test('hides Availability when disable_availability=true', () => {
  mockUseSettings.mockReturnValueOnce({ settings: { disable_availability: 'true' }, refresh: jest.fn(), loaded: true });
  mockUseAuth.mockReturnValue({ user: { ...studentUser }, logout: mockLogout });
  render(<Navbar />);
  expect(screen.queryByText('Availability')).not.toBeInTheDocument();
});

test('gear icon links to /settings', () => {
  render(<Navbar />);
  const gear = screen.getAllByLabelText('User settings')[0];
  expect(gear).toHaveAttribute('href', '/settings');
});

test('user name links to /account/<id>', () => {
  render(<Navbar />);
  const nameLink = screen.getAllByText(studentUser.fullName)[0].closest('a');
  expect(nameLink).toHaveAttribute('href', `/account/${studentUser.id}`);
});

test('admin inline link container uses xl: breakpoint', () => {
  mockUseAuth.mockReturnValue({ user: adminUser, logout: mockLogout });
  const { container } = render(<Navbar />);
  const linkContainers = container.querySelectorAll('div.xl\\:flex');
  expect(linkContainers.length).toBeGreaterThan(0);
});

test('regular user inline link container uses lg: breakpoint', () => {
  mockUseAuth.mockReturnValue({ user: studentUser, logout: mockLogout });
  const { container } = render(<Navbar />);
  const linkContainers = container.querySelectorAll('div.lg\\:flex');
  expect(linkContainers.length).toBeGreaterThan(0);
});

test('nav links have whitespace-nowrap so multi-word labels stay on one line', () => {
  mockUseAuth.mockReturnValue({ user: adminUser, logout: mockLogout });
  render(<Navbar />);
  const cancellationsLink = screen.getByText('Cancellations').closest('a');
  expect(cancellationsLink?.className).toContain('whitespace-nowrap');
});

test('hamburger toggles mobile menu', () => {
  render(<Navbar />);
  const hamburger = screen.getByLabelText('Open menu');
  fireEvent.click(hamburger);
  // After toggle, links appear in mobile dropdown too (duplicated via hidden lg:flex)
  // Just check toggle happened — aria-label changes
  expect(screen.getByLabelText('Close menu')).toBeInTheDocument();
  fireEvent.click(screen.getByLabelText('Close menu'));
  expect(screen.getByLabelText('Open menu')).toBeInTheDocument();
});

test('role badge is rendered only in the mobile dropdown', () => {
  // Desktop header no longer shows the badge to save space for admin nav
  render(<Navbar />);
  // Badge text should not appear until the hamburger menu is toggled open
  const badgeMatches = screen.queryAllByText('Student');
  // In test env, jsdom ignores media queries — all elements render, but the dropdown
  // doesn't render until `open=true`. So no badge should be present initially.
  expect(badgeMatches.length).toBe(0);
});
