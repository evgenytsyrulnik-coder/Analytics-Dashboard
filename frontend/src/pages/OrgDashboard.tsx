import { useState, useEffect, useRef, useCallback } from 'react';
import { useNavigate } from 'react-router-dom';
import { LineChart, Line, XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer, BarChart, Bar, PieChart, Pie, Cell, Legend } from 'recharts';
import { useAuth } from '../context/AuthContext';
import api from '../api/client';
import MetricCard from '../components/MetricCard';
import DateRangeSelector from '../components/DateRangeSelector';
import type { AnalyticsSummary, TimeseriesData, ByTeamData, ByAgentTypeData, TopUsersData } from '../types';

const COLORS = ['#3b82f6', '#10b981', '#f59e0b', '#ef4444', '#8b5cf6', '#ec4899'];
const AUTO_REFRESH_INTERVAL = 15_000;

export default function OrgDashboard() {
  const { user } = useAuth();
  const navigate = useNavigate();
  const [from, setFrom] = useState(() => {
    const d = new Date(); d.setDate(d.getDate() - 30);
    return d.toISOString().split('T')[0];
  });
  const [to, setTo] = useState(() => new Date().toISOString().split('T')[0]);
  const [summary, setSummary] = useState<AnalyticsSummary | null>(null);
  const [timeseries, setTimeseries] = useState<TimeseriesData | null>(null);
  const [byTeam, setByTeam] = useState<ByTeamData | null>(null);
  const [byAgentType, setByAgentType] = useState<ByAgentTypeData | null>(null);
  const [topUsers, setTopUsers] = useState<TopUsersData | null>(null);
  const [loading, setLoading] = useState(true);
  const [lastUpdated, setLastUpdated] = useState<Date>(new Date());

  const orgId = user?.orgId;

  const fetchData = useCallback(async () => {
    if (!orgId) return;
    setLoading(true);
    try {
      const [sumRes, tsRes, teamRes, atRes, tuRes] = await Promise.all([
        api.get(`/orgs/${orgId}/analytics/summary`, { params: { from, to } }),
        api.get(`/orgs/${orgId}/analytics/timeseries`, { params: { from, to } }),
        api.get(`/orgs/${orgId}/analytics/by-team`, { params: { from, to } }),
        api.get(`/orgs/${orgId}/analytics/by-agent-type`, { params: { from, to } }),
        api.get(`/orgs/${orgId}/analytics/top-users`, { params: { from, to, sort_by: 'cost' } }),
      ]);
      setSummary(sumRes.data);
      setTimeseries(tsRes.data);
      setByTeam(teamRes.data);
      setByAgentType(atRes.data);
      setTopUsers(tuRes.data);
      setLastUpdated(new Date());
    } catch (err) {
      console.error('Failed to load org analytics', err);
    } finally {
      setLoading(false);
    }
  }, [orgId, from, to]);

  useEffect(() => { fetchData(); }, [fetchData]);

  // Auto-refresh
  const fetchRef = useRef(fetchData);
  fetchRef.current = fetchData;
  useEffect(() => {
    const id = setInterval(() => fetchRef.current(), AUTO_REFRESH_INTERVAL);
    return () => clearInterval(id);
  }, []);

  if (loading && !summary) {
    return <div className="text-slate-500">Loading organization analytics...</div>;
  }

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="flex items-center justify-between flex-wrap gap-4">
        <h1 className="text-2xl font-bold text-slate-900">Organization Analytics</h1>
        <div className="flex items-center gap-4">
          <DateRangeSelector from={from} to={to} onChange={(f, t) => { setFrom(f); setTo(t); }} />
          <button onClick={fetchData} className="px-3 py-1.5 text-sm bg-slate-100 rounded-md hover:bg-slate-200">
            Refresh
          </button>
        </div>
      </div>

      <p className="text-xs text-slate-400">Last updated: {lastUpdated.toLocaleTimeString()}</p>

      {/* Metric Cards */}
      {summary && (
        <div className="grid grid-cols-2 md:grid-cols-3 lg:grid-cols-5 gap-4">
          <MetricCard label="Total Runs" value={summary.totalRuns} />
          <MetricCard label="Success Rate" value={summary.successRate} variant="percentage" />
          <MetricCard label="Failed Runs" value={summary.failedRuns} />
          <MetricCard label="Total Tokens" value={summary.totalTokens} />
          <MetricCard label="Total Cost" value={summary.totalCost} variant="currency" />
        </div>
      )}

      {/* Timeseries Chart */}
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

      {/* Usage by Team & Agent Type */}
      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
        {byTeam && byTeam.teams.length > 0 && (
          <div className="bg-white rounded-lg border border-slate-200 p-4 shadow-sm">
            <h2 className="text-lg font-semibold text-slate-800 mb-4">Usage by Team</h2>
            <ResponsiveContainer width="100%" height={250}>
              <BarChart data={byTeam.teams}>
                <CartesianGrid strokeDasharray="3 3" />
                <XAxis dataKey="teamName" />
                <YAxis />
                <Tooltip />
                <Bar dataKey="totalRuns" fill="#3b82f6" name="Total Runs" />
              </BarChart>
            </ResponsiveContainer>
            <table className="w-full mt-4 text-sm">
              <thead>
                <tr className="border-b border-slate-200">
                  <th className="text-left py-2 text-slate-600">Team</th>
                  <th className="text-right py-2 text-slate-600">Runs</th>
                  <th className="text-right py-2 text-slate-600">Cost</th>
                  <th className="text-right py-2 text-slate-600">Success Rate</th>
                </tr>
              </thead>
              <tbody>
                {byTeam.teams.map((t) => (
                  <tr
                    key={t.teamId}
                    className="border-b border-slate-100 cursor-pointer hover:bg-slate-50"
                    onClick={() => navigate(`/teams/${t.teamId}`)}
                  >
                    <td className="py-2 font-medium">
                      {t.teamName}
                      <span className="text-blue-500 text-xs ml-1">&rarr;</span>
                    </td>
                    <td className="text-right py-2">{t.totalRuns.toLocaleString()}</td>
                    <td className="text-right py-2">${parseFloat(t.totalCost).toFixed(2)}</td>
                    <td className="text-right py-2">{(t.successRate * 100).toFixed(1)}%</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}

        {byAgentType && byAgentType.agentTypes.length > 0 && (
          <div className="bg-white rounded-lg border border-slate-200 p-4 shadow-sm">
            <h2 className="text-lg font-semibold text-slate-800 mb-4">Usage by Agent Type</h2>
            <ResponsiveContainer width="100%" height={300}>
              <PieChart>
                <Pie data={byAgentType.agentTypes} dataKey="totalRuns" nameKey="displayName"
                     cx="50%" cy="45%" outerRadius={80}>
                  {byAgentType.agentTypes.map((_, i) => (
                    <Cell key={i} fill={COLORS[i % COLORS.length]} />
                  ))}
                </Pie>
                <Tooltip />
                <Legend
                  verticalAlign="bottom"
                  layout="horizontal"
                  wrapperStyle={{ paddingTop: 8, fontSize: '12px', overflowWrap: 'break-word' }}
                  formatter={(value: string) => <span style={{ color: '#475569' }}>{value}</span>}
                />
              </PieChart>
            </ResponsiveContainer>
            <table className="w-full mt-4 text-sm">
              <thead>
                <tr className="border-b border-slate-200">
                  <th className="text-left py-2 text-slate-600">Agent Type</th>
                  <th className="text-right py-2 text-slate-600">Runs</th>
                  <th className="text-right py-2 text-slate-600">Cost</th>
                </tr>
              </thead>
              <tbody>
                {byAgentType.agentTypes.map((at) => (
                  <tr key={at.agentType} className="border-b border-slate-100">
                    <td className="py-2 font-medium">{at.displayName}</td>
                    <td className="text-right py-2">{at.totalRuns.toLocaleString()}</td>
                    <td className="text-right py-2">${parseFloat(at.totalCost).toFixed(2)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>

      {/* Top Users */}
      {topUsers && topUsers.users.length > 0 && (
        <div className="bg-white rounded-lg border border-slate-200 p-4 shadow-sm">
          <h2 className="text-lg font-semibold text-slate-800 mb-4">Top Users by Cost</h2>
          <table className="w-full text-sm">
            <thead>
              <tr className="border-b border-slate-200">
                <th className="text-left py-2 text-slate-600">Rank</th>
                <th className="text-left py-2 text-slate-600">Name</th>
                <th className="text-left py-2 text-slate-600">Team</th>
                <th className="text-right py-2 text-slate-600">Runs</th>
                <th className="text-right py-2 text-slate-600">Tokens</th>
                <th className="text-right py-2 text-slate-600">Cost</th>
              </tr>
            </thead>
            <tbody>
              {topUsers.users.map((u, i) => (
                <tr
                  key={u.userId}
                  className="border-b border-slate-100 cursor-pointer hover:bg-slate-50"
                  onClick={() => navigate(`/users/${u.userId}`)}
                >
                  <td className="py-2">{i + 1}</td>
                  <td className="py-2 font-medium">
                    {u.displayName}
                    <span className="text-blue-500 text-xs ml-1">&rarr;</span>
                  </td>
                  <td className="py-2 text-slate-500">{u.teamName}</td>
                  <td className="text-right py-2">{u.totalRuns.toLocaleString()}</td>
                  <td className="text-right py-2">{u.totalTokens.toLocaleString()}</td>
                  <td className="text-right py-2">${parseFloat(u.totalCost).toFixed(2)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
}
