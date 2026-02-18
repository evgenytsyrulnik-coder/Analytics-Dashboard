interface MetricCardProps {
  label: string;
  value: string | number;
  variant?: 'number' | 'currency' | 'percentage' | 'duration';
}

export default function MetricCard({ label, value, variant = 'number' }: MetricCardProps) {
  const format = () => {
    if (variant === 'currency') {
      const num = typeof value === 'string' ? parseFloat(value) : value;
      return `$${num.toLocaleString(undefined, { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`;
    }
    if (variant === 'percentage') {
      const num = typeof value === 'string' ? parseFloat(value) : value;
      return `${(num * 100).toFixed(1)}%`;
    }
    if (variant === 'duration') {
      const ms = typeof value === 'string' ? parseInt(value) : value;
      if (ms < 1000) return `${ms}ms`;
      return `${(ms / 1000).toFixed(1)}s`;
    }
    if (typeof value === 'number') return value.toLocaleString();
    return value;
  };

  return (
    <div className="bg-white rounded-lg border border-slate-200 p-4 shadow-sm">
      <p className="text-sm text-slate-500 mb-1">{label}</p>
      <p className="text-2xl font-bold text-slate-900">{format()}</p>
    </div>
  );
}
