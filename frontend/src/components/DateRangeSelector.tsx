interface DateRangeSelectorProps {
  from: string;
  to: string;
  onChange: (from: string, to: string) => void;
}

export default function DateRangeSelector({ from, to, onChange }: DateRangeSelectorProps) {
  const today = new Date().toISOString().split('T')[0];

  const presets = [
    { label: 'Last 7 days', days: 7 },
    { label: 'Last 30 days', days: 30 },
    { label: 'Last 90 days', days: 90 },
  ];

  const applyPreset = (days: number) => {
    const end = new Date();
    const start = new Date();
    start.setDate(start.getDate() - days);
    onChange(start.toISOString().split('T')[0], end.toISOString().split('T')[0]);
  };

  return (
    <div className="flex items-center gap-2 flex-wrap">
      {presets.map((p) => (
        <button
          key={p.days}
          onClick={() => applyPreset(p.days)}
          className="px-3 py-1.5 text-xs font-medium rounded-md border border-slate-200 hover:bg-slate-100 text-slate-600"
        >
          {p.label}
        </button>
      ))}
      <div className="flex items-center gap-1 text-sm">
        <input
          type="date"
          value={from}
          max={to || today}
          onChange={(e) => onChange(e.target.value, to)}
          className="px-2 py-1.5 border border-slate-300 rounded-md text-sm"
        />
        <span className="text-slate-400">to</span>
        <input
          type="date"
          value={to}
          min={from}
          max={today}
          onChange={(e) => onChange(from, e.target.value)}
          className="px-2 py-1.5 border border-slate-300 rounded-md text-sm"
        />
      </div>
    </div>
  );
}
