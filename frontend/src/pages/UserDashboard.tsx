import { useState, useEffect } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { LineChart, Line, XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer } from 'recharts';
import { useAuth } from '../context/AuthContext';
import api from '../api/client';
import MetricCard from '../components/MetricCard';
import DateRangeSelector from '../components/DateRangeSelector';
import StatusBadge from '../components/StatusBadge';
import type { UserSummary, TimeseriesData, RunListData } from '../types';

export default function UserDashboard() {
  const { userId } = useParams<{ userId: string }>();
  const { user } = useAuth();
  const navigate = useNavigate();
  const [from, setFrom] = useState(() => {
    const d = new Date(); d.setDate(d.getDate() - 30);
    return d.toISOString().split('T')[0];
  });
  const [to, setTo] = useState(() => new Date().toISOString().split('T')[0]);
  const [summary, setSummary] = useState<UserSummary | null>(null);
  const [timeseries, setTimeseries] = useState<TimeseriesData | null>(null);
  const [runs, setRuns] = useState<RunListData | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const canView = user?.role === 'ORG_ADMIN' || user?.role === 'TEAM_LEAD';

  const fetchData = async () => {
    if (!userId || !canView) return;
    setLoading(true);
    setError(null);
    try {
      const [sumRes, tsRes, runsRes] = await Promise.all([
        api.get(`/users/${userId}/analytics/summary`, { params: { from, to } }),
        api.get(`/users/${userId}/analytics/timeseries`, { params: { from, to } }),
        api.get(`/users/${userId}/runs`, { params: { from, to, limit: 20 } }),
      ]);
      setSummary(sumRes.data);
      setTimeseries(tsRes.data);
      setRuns(runsRes.data);
    } catch (err: any) {
      if (err.response?.status === 403) {
        setError('You do not have permission to view this user\'s dashboard.');
      } else if (err.response?.status === 404) {
        setError('User not found.');
      } else {
        setError('Failed to load user analytics.');
      }
      console.error('Failed to load user analytics', err);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => { fetchData(); }, [userId, from, to]);

  if (!canView) {
    return <div className="text-red-600">You do not have permission to view this page.</div>;
  }

  if (error) {
    return <div className="text-red-600">{error}</div>;
  }

  if (loading && !summary) {
    return <div className="text-slate-500">Loading user analytics...</div>;
  }

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between flex-wrap gap-4">
        <div>
          <button
            onClick={() => navigate(-1)}
            className="text-sm text-blue-600 hover:text-blue-800 mb-1"
          >
            &larr; Back
          </button>
          <h1 className="text-2xl font-bold text-slate-900">{summary?.displayName ? `${summary.displayName} — Analytics` : 'User Analytics'}</h1>
        </div>
        <DateRangeSelector from={from} to={to} onChange={(f, t) => { setFrom(f); setTo(t); }} />
      </div>

      {summary && (
        <>
          <div className="grid grid-cols-2 md:grid-cols-4 gap-4">
            <MetricCard label="Total Runs" value={summary.totalRuns} />
            <MetricCard label="Success Rate" value={summary.succeededRuns / Math.max(1, summary.totalRuns)} variant="percentage" />
            <MetricCard label="Total Tokens" value={summary.totalTokens} />
            <MetricCard label="Total Cost" value={summary.totalCost} variant="currency" />
          </div>

          {summary.teamRank > 0 && (
            <div className="bg-blue-50 border border-blue-200 rounded-lg p-4 text-blue-800 text-sm">
              Ranked <strong>#{summary.teamRank}</strong> of <strong>{summary.teamSize}</strong> engineers in the organization this period.
            </div>
          )}
        </>
      )}

      {timeseries && timeseries.dataPoints.length > 0 && (
        <div className="bg-white rounded-lg border border-slate-200 p-4 shadow-sm">
          <h2 className="text-lg font-semibold text-slate-800 mb-4">Daily Usage</h2>
          <ResponsiveContainer width="100%" height={300}>
            <LineChart data={timeseries.dataPoints}>
              <CartesianGrid strokeDasharray="3 3" />
              <XAxis dataKey="timestamp" tickFormatter={(v) => v.split('T')[0].slice(5)} />
              <YAxis />
              <Tooltip labelFormatter={(v) => v.split('T')[0]} />
              <Line type="monotone" dataKey="totalRuns" stroke="#3b82f6" name="Total Runs" strokeWidth={2} />
            </LineChart>
          </ResponsiveContainer>
        </div>
      )}

      {runs && runs.runs.length > 0 && (
        <div className="bg-white rounded-lg border border-slate-200 p-4 shadow-sm">
          <h2 className="text-lg font-semibold text-slate-800 mb-4">Recent Runs</h2>
          <div className="overflow-x-auto">
            <table className="w-full text-sm">
              <thead>
                <tr className="border-b border-slate-200">
                  <th className="text-left py-2 text-slate-600">Time</th>
                  <th className="text-left py-2 text-slate-600">Agent Type</th>
                  <th className="text-left py-2 text-slate-600">Status</th>
                  <th className="text-right py-2 text-slate-600">Duration</th>
                  <th className="text-right py-2 text-slate-600">Tokens</th>
                  <th className="text-right py-2 text-slate-600">Cost</th>
                </tr>
              </thead>
              <tbody>
                {runs.runs.map((r) => (
                  <tr
                    key={r.runId}
                    className="border-b border-slate-100 cursor-pointer hover:bg-slate-50"
                    onClick={() => navigate(`/runs/${r.runId}`)}
                  >
                    <td className="py-2 text-slate-500">
                      {new Date(r.startedAt).toLocaleString()}
                    </td>
                    <td className="py-2 font-medium">{r.agentTypeDisplayName}</td>
                    <td className="py-2"><StatusBadge status={r.status} /></td>
                    <td className="text-right py-2">{(r.durationMs / 1000).toFixed(1)}s</td>
                    <td className="text-right py-2">{r.totalTokens.toLocaleString()}</td>
                    <td className="text-right py-2">${parseFloat(r.totalCost).toFixed(4)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>
      )}
    </div>
  );
}
