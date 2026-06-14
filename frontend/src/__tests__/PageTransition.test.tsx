import React from 'react';
import { render, screen } from '@testing-library/react';
import '@testing-library/jest-dom';

const mockPathname = jest.fn();
jest.mock('next/navigation', () => ({
  usePathname: () => mockPathname(),
}));

import PageTransition from '@/components/PageTransition';

beforeEach(() => {
  mockPathname.mockReset();
});

test('wraps children in an element with animation class', () => {
  mockPathname.mockReturnValue('/dashboard');
  render(<PageTransition><div>Content</div></PageTransition>);
  const wrapper = screen.getByTestId('page-transition');
  expect(wrapper).toBeInTheDocument();
  expect(wrapper.className).toContain('animate-page-enter');
  expect(screen.getByText('Content')).toBeInTheDocument();
});

test('different pathname uses different key forcing remount', () => {
  mockPathname.mockReturnValue('/dashboard');
  const { rerender } = render(<PageTransition><div>First</div></PageTransition>);
  const firstWrapper = screen.getByTestId('page-transition');

  mockPathname.mockReturnValue('/booking');
  rerender(<PageTransition><div>Second</div></PageTransition>);
  const secondWrapper = screen.getByTestId('page-transition');

  // They must have the same className but different underlying DOM node (remounted)
  expect(firstWrapper).not.toBe(secondWrapper);
  expect(screen.getByText('Second')).toBeInTheDocument();
});
