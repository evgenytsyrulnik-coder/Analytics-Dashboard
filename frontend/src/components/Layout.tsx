import { useState, useEffect } from 'react';
import { Outlet, NavLink, useNavigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import api from '../api/client';
import type { TeamInfo } from '../types';

export default function Layout() {
  const { user, logout } = useAuth();
  const navigate = useNavigate();
  const [allTeams, setAllTeams] = useState<TeamInfo[] | null>(null);

  useEffect(() => {
    if (user?.role === 'ORG_ADMIN') {
      api.get(`/orgs/${user.orgId}/teams`)
        .then((res) => {
          const teams: TeamInfo[] = res.data.teams.map((t: { team_id: string; name: string }) => ({
            teamId: t.team_id,
            teamName: t.name,
          }));
          teams.sort((a, b) => a.teamName.localeCompare(b.teamName));
          setAllTeams(teams);
        })
        .catch(() => setAllTeams(null));
    }
  }, [user]);

  if (!user) return null;

  const handleLogout = () => {
    logout();
    navigate('/login');
  };

  const teamsToShow = user.role === 'ORG_ADMIN' ? (allTeams ?? user.teams) : user.teams;

  const navItems: { to: string; label: string }[] = [];

  if (user.role === 'ORG_ADMIN') {
    navItems.push({ to: '/org', label: 'Organization' });
  }

  if (user.role === 'ORG_ADMIN' || user.role === 'TEAM_LEAD') {
    teamsToShow.forEach((team) => {
      navItems.push({ to: `/teams/${team.teamId}`, label: team.teamName });
    });
  }

  navItems.push({ to: '/me', label: 'My Dashboard' });

  return (
    <div className="min-h-screen flex">
      {/* Sidebar */}
      <aside className="w-64 bg-slate-900 text-white flex flex-col">
        <div className="p-4 border-b border-slate-700">
          <h1 className="text-lg font-bold">Analytics Dashboard</h1>
          <p className="text-slate-400 text-xs mt-1">Agent Usage Insights</p>
        </div>

        <nav className="flex-1 p-4 space-y-1 overflow-y-auto">
          {navItems.map((item) => (
            <NavLink
              key={item.to}
              to={item.to}
              className={({ isActive }) =>
                `block px-3 py-2 rounded-md text-sm transition-colors ${
                  isActive
                    ? 'bg-blue-600 text-white'
                    : 'text-slate-300 hover:bg-slate-800 hover:text-white'
                }`
              }
            >
              {item.label}
            </NavLink>
          ))}
        </nav>

        <div className="p-4 border-t border-slate-700">
          <div className="flex items-center justify-between">
            <div>
              <p className="text-sm font-medium">{user.displayName}</p>
              <p className="text-xs text-slate-400">{user.role}</p>
            </div>
            <button
              onClick={handleLogout}
              className="text-xs text-slate-400 hover:text-white px-2 py-1 rounded hover:bg-slate-800"
            >
              Logout
            </button>
          </div>
        </div>
      </aside>

      {/* Main content */}
      <main className="flex-1 overflow-auto">
        <div className="p-6">
          <Outlet />
        </div>
      </main>
    </div>
  );
}
