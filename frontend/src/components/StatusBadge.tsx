interface StatusBadgeProps {
  status: string;
}

export default function StatusBadge({ status }: StatusBadgeProps) {
  const styles: Record<string, string> = {
    SUCCEEDED: 'bg-green-100 text-green-700',
    FAILED: 'bg-red-100 text-red-700',
    RUNNING: 'bg-blue-100 text-blue-700',
    CANCELLED: 'bg-slate-100 text-slate-600',
  };

  return (
    <span className={`inline-block px-2 py-0.5 text-xs font-medium rounded-full ${styles[status] || 'bg-slate-100 text-slate-600'}`}>
      {status}
    </span>
  );
}
