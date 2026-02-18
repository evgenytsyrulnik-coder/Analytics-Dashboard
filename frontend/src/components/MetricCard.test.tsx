import { render, screen } from '@testing-library/react';
import MetricCard from './MetricCard';

describe('MetricCard', () => {
  describe('number variant (default)', () => {
    it('displays a locale-formatted number when value is a number', () => {
      render(<MetricCard label="Total Runs" value={142857} />);

      expect(screen.getByText('142,857')).toBeInTheDocument();
    });

    it('displays the raw string when value is a string and variant is number', () => {
      render(<MetricCard label="Status" value="active" />);

      expect(screen.getByText('active')).toBeInTheDocument();
    });
  });

  describe('currency variant', () => {
    it('displays formatted currency with dollar sign and two decimal places', () => {
      render(<MetricCard label="Total Cost" value={24350.12} variant="currency" />);

      expect(screen.getByText('$24,350.12')).toBeInTheDocument();
    });

    it('parses a string value and formats as currency', () => {
      render(<MetricCard label="Total Cost" value="24350.12" variant="currency" />);

      expect(screen.getByText('$24,350.12')).toBeInTheDocument();
    });
  });

  describe('percentage variant', () => {
    it('displays the value multiplied by 100 with a percent sign', () => {
      render(<MetricCard label="Success Rate" value={0.945} variant="percentage" />);

      expect(screen.getByText('94.5%')).toBeInTheDocument();
    });

    it('parses a string value and formats as percentage', () => {
      render(<MetricCard label="Success Rate" value="0.945" variant="percentage" />);

      expect(screen.getByText('94.5%')).toBeInTheDocument();
    });
  });

  describe('duration variant', () => {
    it('displays milliseconds when value is less than 1000', () => {
      render(<MetricCard label="Avg Duration" value={500} variant="duration" />);

      expect(screen.getByText('500ms')).toBeInTheDocument();
    });

    it('displays seconds when value is 1000 or greater', () => {
      render(<MetricCard label="Avg Duration" value={34500} variant="duration" />);

      expect(screen.getByText('34.5s')).toBeInTheDocument();
    });

    it('parses a string value and formats as duration', () => {
      render(<MetricCard label="Avg Duration" value="750" variant="duration" />);

      expect(screen.getByText('750ms')).toBeInTheDocument();
    });
  });

  describe('label display', () => {
    it('displays the label text', () => {
      render(<MetricCard label="Total Runs" value={100} />);

      expect(screen.getByText('Total Runs')).toBeInTheDocument();
    });
  });
});
