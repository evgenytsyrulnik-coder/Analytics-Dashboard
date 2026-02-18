import { useState, useEffect, useCallback, useRef } from 'react';
import { useSearchParams, useNavigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import api from '../api/client';
import StatusBadge from '../components/StatusBadge';
import type { PagedRunList, TeamInfo, OrgUser } from '../types';

const PAGE_SIZE = 25;
const STATUS_OPTIONS = ['SUCCEEDED', 'FAILED', 'CANCELLED', 'RUNNING'] as const;

export default function RunsListPage() {
  const { user } = useAuth();
  const navigate = useNavigate();
  const [searchParams, setSearchParams] = useSearchParams();
  const orgId = user?.orgId;

  // Filters from URL params
  const [from, setFrom] = useState(() => searchParams.get('from') || defaultFrom());
  const [to, setTo] = useState(() => searchParams.get('to') || defaultTo());
  const [selectedStatuses, setSelectedStatuses] = useState<string[]>(() => {
    const s = searchParams.get('status');
    return s ? s.split(',').filter(Boolean) : [];
  });
  const [selectedTeam, setSelectedTeam] = useState<string>(() => searchParams.get('team_id') || '');
  const [selectedUsers, setSelectedUsers] = useState<string[]>(() => {
    const u = searchParams.get('user_id');
    return u ? u.split(',').filter(Boolean) : [];
  });
  const [page, setPage] = useState(() => {
    const p = searchParams.get('page');
    return p ? parseInt(p, 10) : 0;
  });

  // Data
  const [data, setData] = useState<PagedRunList | null>(null);
  const [loading, setLoading] = useState(true);
  const [teams, setTeams] = useState<TeamInfo[]>([]);
  const [users, setUsers] = useState<OrgUser[]>([]);

  // Dropdowns open state
  const [statusDropdownOpen, setStatusDropdownOpen] = useState(false);
  const [userDropdownOpen, setUserDropdownOpen] = useState(false);
  const statusRef = useRef<HTMLDivElement>(null);
  const userRef = useRef<HTMLDivElement>(null);

  // Close dropdowns when clicking outside
  useEffect(() => {
    function handleClick(e: MouseEvent) {
      if (statusRef.current && !statusRef.current.contains(e.target as Node)) {
        setStatusDropdownOpen(false);
      }
      if (userRef.current && !userRef.current.contains(e.target as Node)) {
        setUserDropdownOpen(false);
      }
    }
    document.addEventListener('mousedown', handleClick);
    return () => document.removeEventListener('mousedown', handleClick);
  }, []);

  // Sync URL params when filters change
  useEffect(() => {
    const params: Record<string, string> = { from, to };
    if (selectedStatuses.length > 0) params.status = selectedStatuses.join(',');
    if (selectedTeam) params.team_id = selectedTeam;
    if (selectedUsers.length > 0) params.user_id = selectedUsers.join(',');
    if (page > 0) params.page = String(page);
    setSearchParams(params, { replace: true });
  }, [from, to, selectedStatuses, selectedTeam, selectedUsers, page, setSearchParams]);

  // Load teams and users for filter dropdowns
  useEffect(() => {
    if (!orgId) return;
    Promise.all([
      api.get(`/orgs/${orgId}/teams`),
      api.get(`/orgs/${orgId}/users`),
    ]).then(([teamsRes, usersRes]) => {
      const teamList: TeamInfo[] = teamsRes.data.teams.map((t: { team_id: string; name: string }) => ({
        teamId: t.team_id,
        teamName: t.name,
      }));
      teamList.sort((a, b) => a.teamName.localeCompare(b.teamName));
      setTeams(teamList);

      const userList: OrgUser[] = usersRes.data.users;
      userList.sort((a, b) => a.display_name.localeCompare(b.display_name));
      setUsers(userList);
    }).catch(err => console.error('Failed to load filter options', err));
  }, [orgId]);

  // Fetch runs data
  const fetchRuns = useCallback(async () => {
    if (!orgId) return;
    setLoading(true);
    try {
      const params: Record<string, string | number> = { from, to, page, size: PAGE_SIZE };
      if (selectedStatuses.length > 0) params.status = selectedStatuses.join(',');
      if (selectedTeam) params.team_id = selectedTeam;
      if (selectedUsers.length > 0) params.user_id = selectedUsers.join(',');
      const res = await api.get(`/orgs/${orgId}/runs`, { params });
      setData(res.data);
    } catch (err) {
      console.error('Failed to load runs', err);
    } finally {
      setLoading(false);
    }
  }, [orgId, from, to, selectedStatuses, selectedTeam, selectedUsers, page]);

  useEffect(() => { fetchRuns(); }, [fetchRuns]);

  const handleStatusToggle = (status: string) => {
    setSelectedStatuses(prev =>
      prev.includes(status) ? prev.filter(s => s !== status) : [...prev, status]
    );
    setPage(0);
  };

  const handleUserToggle = (userId: string) => {
    setSelectedUsers(prev =>
      prev.includes(userId) ? prev.filter(u => u !== userId) : [...prev, userId]
    );
    setPage(0);
  };

  const clearFilters = () => {
    setFrom(defaultFrom());
    setTo(defaultTo());
    setSelectedStatuses([]);
    setSelectedTeam('');
    setSelectedUsers([]);
    setPage(0);
  };

  const hasActiveFilters = selectedStatuses.length > 0 || selectedTeam || selectedUsers.length > 0;

  return (
    <div className="space-y-4">
      {/* Header */}
      <div className="flex items-center justify-between flex-wrap gap-4">
        <div>
          <button
            onClick={() => navigate('/org')}
            className="text-sm text-blue-600 hover:text-blue-800 mb-1"
          >
            &larr; Back to Dashboard
          </button>
          <h1 className="text-2xl font-bold text-slate-900">Runs</h1>
        </div>
      </div>

      {/* Filters */}
      <div className="bg-white rounded-lg border border-slate-200 p-4 shadow-sm space-y-3">
        <div className="flex items-center justify-between">
          <h2 className="text-sm font-semibold text-slate-700">Filters</h2>
          {hasActiveFilters && (
            <button
              onClick={clearFilters}
              className="text-xs text-blue-600 hover:text-blue-800"
            >
              Clear all filters
            </button>
          )}
        </div>

        <div className="flex flex-wrap gap-3 items-end">
          {/* Date range */}
          <div className="flex items-center gap-1 text-sm">
            <label className="text-xs text-slate-500 mr-1">From</label>
            <input
              type="date"
              value={from}
              max={to}
              onChange={(e) => { setFrom(e.target.value); setPage(0); }}
              className="px-2 py-1.5 border border-slate-300 rounded-md text-sm"
            />
            <span className="text-slate-400 mx-1">to</span>
            <label className="text-xs text-slate-500 mr-1">To</label>
            <input
              type="date"
              value={to}
              min={from}
              max={new Date().toISOString().split('T')[0]}
              onChange={(e) => { setTo(e.target.value); setPage(0); }}
              className="px-2 py-1.5 border border-slate-300 rounded-md text-sm"
            />
          </div>

          {/* Team filter */}
          <div>
            <label className="text-xs text-slate-500 block mb-1">Team</label>
            <select
              value={selectedTeam}
              onChange={(e) => { setSelectedTeam(e.target.value); setPage(0); }}
              className="px-2 py-1.5 border border-slate-300 rounded-md text-sm min-w-[140px]"
            >
              <option value="">All teams</option>
              {teams.map(t => (
                <option key={t.teamId} value={t.teamId}>{t.teamName}</option>
              ))}
            </select>
          </div>

          {/* Status filter (multi-select dropdown) */}
          <div ref={statusRef} className="relative">
            <label className="text-xs text-slate-500 block mb-1">Result</label>
            <button
              onClick={() => setStatusDropdownOpen(!statusDropdownOpen)}
              className="px-2 py-1.5 border border-slate-300 rounded-md text-sm min-w-[160px] text-left flex items-center justify-between bg-white"
            >
              <span className="truncate">
                {selectedStatuses.length === 0
                  ? 'All results'
                  : selectedStatuses.length === 1
                    ? selectedStatuses[0]
                    : `${selectedStatuses.length} selected`}
              </span>
              <svg className="w-4 h-4 ml-1 text-slate-400 shrink-0" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M19 9l-7 7-7-7" />
              </svg>
            </button>
            {statusDropdownOpen && (
              <div className="absolute z-20 mt-1 w-full bg-white border border-slate-200 rounded-md shadow-lg py-1">
                {STATUS_OPTIONS.map(status => (
                  <label
                    key={status}
                    className="flex items-center px-3 py-1.5 hover:bg-slate-50 cursor-pointer text-sm"
                  >
                    <input
                      type="checkbox"
                      checked={selectedStatuses.includes(status)}
                      onChange={() => handleStatusToggle(status)}
                      className="mr-2 rounded border-slate-300"
                    />
                    <StatusBadge status={status} />
                  </label>
                ))}
              </div>
            )}
          </div>

          {/* Person filter (multi-select dropdown) */}
          <div ref={userRef} className="relative">
            <label className="text-xs text-slate-500 block mb-1">Person</label>
            <button
              onClick={() => setUserDropdownOpen(!userDropdownOpen)}
              className="px-2 py-1.5 border border-slate-300 rounded-md text-sm min-w-[180px] text-left flex items-center justify-between bg-white"
            >
              <span className="truncate">
                {selectedUsers.length === 0
                  ? 'All people'
                  : selectedUsers.length === 1
                    ? users.find(u => u.user_id === selectedUsers[0])?.display_name || '1 selected'
                    : `${selectedUsers.length} selected`}
              </span>
              <svg className="w-4 h-4 ml-1 text-slate-400 shrink-0" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M19 9l-7 7-7-7" />
              </svg>
            </button>
            {userDropdownOpen && (
              <div className="absolute z-20 mt-1 w-full bg-white border border-slate-200 rounded-md shadow-lg py-1 max-h-60 overflow-y-auto min-w-[220px]">
                {users.map(u => (
                  <label
                    key={u.user_id}
                    className="flex items-center px-3 py-1.5 hover:bg-slate-50 cursor-pointer text-sm"
                  >
                    <input
                      type="checkbox"
                      checked={selectedUsers.includes(u.user_id)}
                      onChange={() => handleUserToggle(u.user_id)}
                      className="mr-2 rounded border-slate-300"
                    />
                    <span className="truncate">{u.display_name}</span>
                  </label>
                ))}
                {users.length === 0 && (
                  <div className="px-3 py-2 text-sm text-slate-400">No users found</div>
                )}
              </div>
            )}
          </div>
        </div>

        {/* Active filter pills */}
        {hasActiveFilters && (
          <div className="flex flex-wrap gap-1.5 pt-1">
            {selectedStatuses.map(s => (
              <span
                key={s}
                className="inline-flex items-center gap-1 px-2 py-0.5 bg-slate-100 rounded-full text-xs text-slate-600"
              >
                {s}
                <button
                  onClick={() => handleStatusToggle(s)}
                  className="text-slate-400 hover:text-slate-600"
                >
                  &times;
                </button>
              </span>
            ))}
            {selectedTeam && (
              <span className="inline-flex items-center gap-1 px-2 py-0.5 bg-slate-100 rounded-full text-xs text-slate-600">
                Team: {teams.find(t => t.teamId === selectedTeam)?.teamName || selectedTeam}
                <button
                  onClick={() => { setSelectedTeam(''); setPage(0); }}
                  className="text-slate-400 hover:text-slate-600"
                >
                  &times;
                </button>
              </span>
            )}
            {selectedUsers.map(uid => (
              <span
                key={uid}
                className="inline-flex items-center gap-1 px-2 py-0.5 bg-slate-100 rounded-full text-xs text-slate-600"
              >
                {users.find(u => u.user_id === uid)?.display_name || uid}
                <button
                  onClick={() => handleUserToggle(uid)}
                  className="text-slate-400 hover:text-slate-600"
                >
                  &times;
                </button>
              </span>
            ))}
          </div>
        )}
      </div>

      {/* Results */}
      <div className="bg-white rounded-lg border border-slate-200 shadow-sm">
        {loading && !data ? (
          <div className="p-8 text-center text-slate-500">Loading runs...</div>
        ) : data && data.runs.length > 0 ? (
          <>
            <div className="px-4 py-3 border-b border-slate-200 flex items-center justify-between">
              <span className="text-sm text-slate-500">
                {data.totalElements.toLocaleString()} run{data.totalElements !== 1 ? 's' : ''} found
              </span>
              {loading && <span className="text-xs text-slate-400">Updating...</span>}
            </div>
            <div className="overflow-x-auto">
              <table className="w-full text-sm">
                <thead>
                  <tr className="border-b border-slate-200">
                    <th className="text-left py-2 px-4 text-slate-600 font-medium">Time</th>
                    <th className="text-left py-2 px-4 text-slate-600 font-medium">User</th>
                    <th className="text-left py-2 px-4 text-slate-600 font-medium">Team</th>
                    <th className="text-left py-2 px-4 text-slate-600 font-medium">Agent Type</th>
                    <th className="text-left py-2 px-4 text-slate-600 font-medium">Status</th>
                    <th className="text-right py-2 px-4 text-slate-600 font-medium">Duration</th>
                    <th className="text-right py-2 px-4 text-slate-600 font-medium">Tokens</th>
                    <th className="text-right py-2 px-4 text-slate-600 font-medium">Cost</th>
                  </tr>
                </thead>
                <tbody>
                  {data.runs.map(r => (
                    <tr
                      key={r.runId}
                      className="border-b border-slate-100 cursor-pointer hover:bg-slate-50"
                      onClick={() => navigate(`/runs/${r.runId}`)}
                    >
                      <td className="py-2 px-4 text-slate-500 whitespace-nowrap">
                        {new Date(r.startedAt).toLocaleString()}
                      </td>
                      <td className="py-2 px-4 font-medium">{r.userName}</td>
                      <td className="py-2 px-4 text-slate-500">{r.teamName}</td>
                      <td className="py-2 px-4">{r.agentTypeDisplayName}</td>
                      <td className="py-2 px-4"><StatusBadge status={r.status} /></td>
                      <td className="text-right py-2 px-4 whitespace-nowrap">
                        {r.durationMs > 0 ? `${(r.durationMs / 1000).toFixed(1)}s` : '-'}
                      </td>
                      <td className="text-right py-2 px-4">{r.totalTokens.toLocaleString()}</td>
                      <td className="text-right py-2 px-4">${parseFloat(r.totalCost).toFixed(4)}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>

            {/* Pagination */}
            {data.totalPages > 1 && (
              <div className="px-4 py-3 border-t border-slate-200 flex items-center justify-between">
                <button
                  onClick={() => setPage(p => Math.max(0, p - 1))}
                  disabled={page === 0}
                  className="px-3 py-1.5 text-sm border border-slate-300 rounded-md hover:bg-slate-50 disabled:opacity-40 disabled:cursor-not-allowed"
                >
                  Previous
                </button>
                <div className="flex items-center gap-1">
                  {generatePageNumbers(page, data.totalPages).map((p, i) =>
                    p === -1 ? (
                      <span key={`ellipsis-${i}`} className="px-2 text-slate-400">...</span>
                    ) : (
                      <button
                        key={p}
                        onClick={() => setPage(p)}
                        className={`px-3 py-1.5 text-sm rounded-md ${
                          p === page
                            ? 'bg-blue-600 text-white'
                            : 'border border-slate-300 hover:bg-slate-50'
                        }`}
                      >
                        {p + 1}
                      </button>
                    )
                  )}
                </div>
                <button
                  onClick={() => setPage(p => Math.min(data.totalPages - 1, p + 1))}
                  disabled={page >= data.totalPages - 1}
                  className="px-3 py-1.5 text-sm border border-slate-300 rounded-md hover:bg-slate-50 disabled:opacity-40 disabled:cursor-not-allowed"
                >
                  Next
                </button>
              </div>
            )}
          </>
        ) : (
          <div className="p-8 text-center text-slate-500">
            No runs found for the selected filters.
          </div>
        )}
      </div>
    </div>
  );
}

function defaultFrom(): string {
  const d = new Date();
  d.setDate(d.getDate() - 30);
  return d.toISOString().split('T')[0];
}

function defaultTo(): string {
  return new Date().toISOString().split('T')[0];
}

function generatePageNumbers(current: number, total: number): number[] {
  if (total <= 7) return Array.from({ length: total }, (_, i) => i);

  const pages: number[] = [];
  pages.push(0);

  if (current > 2) pages.push(-1); // ellipsis

  for (let i = Math.max(1, current - 1); i <= Math.min(total - 2, current + 1); i++) {
    pages.push(i);
  }

  if (current < total - 3) pages.push(-1); // ellipsis

  pages.push(total - 1);
  return pages;
}
