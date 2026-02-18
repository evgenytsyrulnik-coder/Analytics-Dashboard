import { render, screen } from '@testing-library/react';
import StatusBadge from './StatusBadge';

describe('StatusBadge', () => {
  it('renders SUCCEEDED status with green styling', () => {
    render(<StatusBadge status="SUCCEEDED" />);

    const badge = screen.getByText('SUCCEEDED');
    expect(badge).toBeInTheDocument();
    expect(badge.className).toContain('bg-green-100');
    expect(badge.className).toContain('text-green-700');
  });

  it('renders FAILED status with red styling', () => {
    render(<StatusBadge status="FAILED" />);

    const badge = screen.getByText('FAILED');
    expect(badge).toBeInTheDocument();
    expect(badge.className).toContain('bg-red-100');
    expect(badge.className).toContain('text-red-700');
  });

  it('renders RUNNING status with blue styling', () => {
    render(<StatusBadge status="RUNNING" />);

    const badge = screen.getByText('RUNNING');
    expect(badge).toBeInTheDocument();
    expect(badge.className).toContain('bg-blue-100');
    expect(badge.className).toContain('text-blue-700');
  });

  it('renders CANCELLED status with slate/gray styling', () => {
    render(<StatusBadge status="CANCELLED" />);

    const badge = screen.getByText('CANCELLED');
    expect(badge).toBeInTheDocument();
    expect(badge.className).toContain('bg-slate-100');
    expect(badge.className).toContain('text-slate-600');
  });

  it('renders an unknown status with fallback gray styling', () => {
    render(<StatusBadge status="UNKNOWN_STATUS" />);

    const badge = screen.getByText('UNKNOWN_STATUS');
    expect(badge).toBeInTheDocument();
    expect(badge.className).toContain('bg-slate-100');
    expect(badge.className).toContain('text-slate-600');
  });
});
