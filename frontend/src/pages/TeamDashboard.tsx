import { useState, useEffect } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { LineChart, Line, XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer } from 'recharts';
import { useAuth } from '../context/AuthContext';
import api from '../api/client';
import MetricCard from '../components/MetricCard';
import DateRangeSelector from '../components/DateRangeSelector';
import type { AnalyticsSummary, TimeseriesData, ByTeamData } from '../types';

export default function TeamDashboard() {
  const { teamId } = useParams<{ teamId: string }>();
  const { user } = useAuth();
  const navigate = useNavigate();
  const [from, setFrom] = useState(() => {
    const d = new Date(); d.setDate(d.getDate() - 30);
    return d.toISOString().split('T')[0];
  });
  const [to, setTo] = useState(() => new Date().toISOString().split('T')[0]);
  const [summary, setSummary] = useState<AnalyticsSummary | null>(null);
  const [timeseries, setTimeseries] = useState<TimeseriesData | null>(null);
  const [byUser, setByUser] = useState<ByTeamData | null>(null);
  const [loading, setLoading] = useState(true);
  const [resolvedTeamName, setResolvedTeamName] = useState<string | null>(null);

  // Try to get team name from user's teams first; fall back to API for ORG_ADMIN
  const localTeamName = user?.teams.find(t => t.teamId === teamId)?.teamName;

  useEffect(() => {
    if (!localTeamName && user?.role === 'ORG_ADMIN' && user.orgId) {
      api.get(`/orgs/${user.orgId}/teams`)
        .then((res) => {
          const match = res.data.teams.find((t: { team_id: string }) => t.team_id === teamId);
          if (match) setResolvedTeamName(match.name);
        })
        .catch(() => {});
    }
  }, [teamId, localTeamName, user]);

  const teamName = localTeamName || resolvedTeamName || 'Team';

  const canViewUserDashboard = user?.role === 'ORG_ADMIN' || user?.role === 'TEAM_LEAD';

  const fetchData = async () => {
    if (!teamId) return;
    setLoading(true);
    try {
      const [sumRes, tsRes, userRes] = await Promise.all([
        api.get(`/teams/${teamId}/analytics/summary`, { params: { from, to } }),
        api.get(`/teams/${teamId}/analytics/timeseries`, { params: { from, to } }),
        api.get(`/teams/${teamId}/analytics/by-user`, { params: { from, to } }),
      ]);
      setSummary(sumRes.data);
      setTimeseries(tsRes.data);
      setByUser(userRes.data);
    } catch (err) {
      console.error('Failed to load team analytics', err);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => { fetchData(); }, [teamId, from, to]);

  if (loading && !summary) {
    return <div className="text-slate-500">Loading team analytics...</div>;
  }

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between flex-wrap gap-4">
        <h1 className="text-2xl font-bold text-slate-900">Team: {teamName}</h1>
        <DateRangeSelector from={from} to={to} onChange={(f, t) => { setFrom(f); setTo(t); }} />
      </div>

      {summary && (
        <div className="grid grid-cols-2 md:grid-cols-3 lg:grid-cols-5 gap-4">
          <MetricCard label="Total Runs" value={summary.totalRuns} />
          <MetricCard label="Success Rate" value={summary.successRate} variant="percentage" />
          <MetricCard label="Failed Runs" value={summary.failedRuns} />
          <MetricCard label="Total Tokens" value={summary.totalTokens} />
          <MetricCard label="Total Cost" value={summary.totalCost} variant="currency" />
        </div>
      )}

      {timeseries && timeseries.dataPoints.length > 0 && (
        <div className="bg-white rounded-lg border border-slate-200 p-4 shadow-sm">
          <h2 className="text-lg font-semibold text-slate-800 mb-4">Daily Runs</h2>
          <ResponsiveContainer width="100%" height={300}>
            <LineChart data={timeseries.dataPoints}>
              <CartesianGrid strokeDasharray="3 3" />
              <XAxis dataKey="timestamp" tickFormatter={(v) => v.split('T')[0].slice(5)} />
              <YAxis />
              <Tooltip labelFormatter={(v) => v.split('T')[0]} />
              <Line type="monotone" dataKey="totalRuns" stroke="#3b82f6" name="Total Runs" strokeWidth={2} />
              <Line type="monotone" dataKey="succeededRuns" stroke="#10b981" name="Succeeded" strokeWidth={2} />
              <Line type="monotone" dataKey="failedRuns" stroke="#ef4444" name="Failed" strokeWidth={2} />
            </LineChart>
          </ResponsiveContainer>
        </div>
      )}

      {byUser && byUser.teams.length > 0 && (
        <div className="bg-white rounded-lg border border-slate-200 p-4 shadow-sm">
          <h2 className="text-lg font-semibold text-slate-800 mb-4">Usage by User</h2>
          <table className="w-full text-sm">
            <thead>
              <tr className="border-b border-slate-200">
                <th className="text-left py-2 text-slate-600">User</th>
                <th className="text-right py-2 text-slate-600">Runs</th>
                <th className="text-right py-2 text-slate-600">Tokens</th>
                <th className="text-right py-2 text-slate-600">Cost</th>
                <th className="text-right py-2 text-slate-600">Success Rate</th>
                <th className="text-right py-2 text-slate-600">Avg Duration</th>
              </tr>
            </thead>
            <tbody>
              {byUser.teams.map((u) => (
                <tr
                  key={u.teamId}
                  className={`border-b border-slate-100 ${canViewUserDashboard ? 'cursor-pointer hover:bg-slate-50' : ''}`}
                  onClick={canViewUserDashboard ? () => navigate(`/users/${u.teamId}`) : undefined}
                >
                  <td className="py-2 font-medium">
                    {u.teamName}
                    {canViewUserDashboard && (
                      <span className="text-blue-500 text-xs ml-1">&rarr;</span>
                    )}
                  </td>
                  <td className="text-right py-2">{u.totalRuns.toLocaleString()}</td>
                  <td className="text-right py-2">{u.totalTokens.toLocaleString()}</td>
                  <td className="text-right py-2">${parseFloat(u.totalCost).toFixed(2)}</td>
                  <td className="text-right py-2">{(u.successRate * 100).toFixed(1)}%</td>
                  <td className="text-right py-2">{(u.avgDurationMs / 1000).toFixed(1)}s</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
}
