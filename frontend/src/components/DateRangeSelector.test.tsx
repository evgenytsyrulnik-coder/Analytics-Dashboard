import { render, screen, fireEvent } from '@testing-library/react';
import { vi } from 'vitest';
import DateRangeSelector from './DateRangeSelector';

describe('DateRangeSelector', () => {
  const defaultProps = {
    from: '2025-01-01',
    to: '2025-01-31',
    onChange: vi.fn(),
  };

  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('renders with the correct from and to dates in inputs', () => {
    render(<DateRangeSelector {...defaultProps} />);

    const fromInput = screen.getByDisplayValue('2025-01-01');
    const toInput = screen.getByDisplayValue('2025-01-31');

    expect(fromInput).toBeInTheDocument();
    expect(toInput).toBeInTheDocument();
  });

  it('renders all three preset buttons', () => {
    render(<DateRangeSelector {...defaultProps} />);

    expect(screen.getByText('Last 7 days')).toBeInTheDocument();
    expect(screen.getByText('Last 30 days')).toBeInTheDocument();
    expect(screen.getByText('Last 90 days')).toBeInTheDocument();
  });

  it('calls onChange with a 7-day range when "Last 7 days" is clicked', () => {
    const onChange = vi.fn();
    vi.useFakeTimers();
    vi.setSystemTime(new Date('2025-06-15T12:00:00Z'));

    render(<DateRangeSelector from="2025-01-01" to="2025-01-31" onChange={onChange} />);

    fireEvent.click(screen.getByText('Last 7 days'));

    expect(onChange).toHaveBeenCalledTimes(1);
    const [from, to] = onChange.mock.calls[0];
    expect(from).toBe('2025-06-08');
    expect(to).toBe('2025-06-15');

    vi.useRealTimers();
  });

  it('calls onChange with a 90-day range when "Last 90 days" is clicked', () => {
    const onChange = vi.fn();
    vi.useFakeTimers();
    vi.setSystemTime(new Date('2025-06-15T12:00:00Z'));

    render(<DateRangeSelector from="2025-01-01" to="2025-01-31" onChange={onChange} />);

    fireEvent.click(screen.getByText('Last 90 days'));

    expect(onChange).toHaveBeenCalledTimes(1);
    const [from, to] = onChange.mock.calls[0];
    expect(from).toBe('2025-03-17');
    expect(to).toBe('2025-06-15');

    vi.useRealTimers();
  });

  it('calls onChange with updated from date when from input changes', () => {
    const onChange = vi.fn();

    render(<DateRangeSelector from="2025-01-01" to="2025-01-31" onChange={onChange} />);

    const fromInput = screen.getByDisplayValue('2025-01-01');
    fireEvent.change(fromInput, { target: { value: '2025-01-10' } });

    expect(onChange).toHaveBeenCalledWith('2025-01-10', '2025-01-31');
  });

  it('calls onChange with updated to date when to input changes', () => {
    const onChange = vi.fn();

    render(<DateRangeSelector from="2025-01-01" to="2025-01-31" onChange={onChange} />);

    const toInput = screen.getByDisplayValue('2025-01-31');
    fireEvent.change(toInput, { target: { value: '2025-01-20' } });

    expect(onChange).toHaveBeenCalledWith('2025-01-01', '2025-01-20');
  });
});
