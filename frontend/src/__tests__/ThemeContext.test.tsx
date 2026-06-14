import React from 'react';
import { render, screen, act } from '@testing-library/react';
import '@testing-library/jest-dom';
import { ThemeProvider, useTheme } from '@/context/ThemeContext';

function Consumer() {
  const { theme, toggle, set } = useTheme();
  return (
    <div>
      <span data-testid="theme">{theme}</span>
      <button onClick={toggle}>toggle</button>
      <button onClick={() => set('dark')}>setDark</button>
    </div>
  );
}

beforeEach(() => {
  localStorage.clear();
  document.documentElement.classList.remove('dark');
});

test('defaults to light mode', () => {
  render(<ThemeProvider><Consumer /></ThemeProvider>);
  expect(screen.getByTestId('theme')).toHaveTextContent('light');
  expect(document.documentElement.classList.contains('dark')).toBe(false);
});

test('toggle flips theme and applies dark class', () => {
  render(<ThemeProvider><Consumer /></ThemeProvider>);
  act(() => { screen.getByText('toggle').click(); });
  expect(screen.getByTestId('theme')).toHaveTextContent('dark');
  expect(document.documentElement.classList.contains('dark')).toBe(true);
});

test('persists to localStorage', () => {
  render(<ThemeProvider><Consumer /></ThemeProvider>);
  act(() => { screen.getByText('setDark').click(); });
  expect(localStorage.getItem('theme')).toBe('dark');
});

test('loads dark from localStorage', () => {
  localStorage.setItem('theme', 'dark');
  render(<ThemeProvider><Consumer /></ThemeProvider>);
  expect(screen.getByTestId('theme')).toHaveTextContent('dark');
  expect(document.documentElement.classList.contains('dark')).toBe(true);
});
