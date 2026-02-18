import { useState, useEffect } from 'react';
import { Outlet, NavLink, useNavigate, useLocation } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import api from '../api/client';
import type { TeamInfo } from '../types';

export default function Layout() {
  const { user, logout } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const [allTeams, setAllTeams] = useState<TeamInfo[] | null>(null);
  const [sidebarOpen, setSidebarOpen] = useState(false);

  useEffect(() => {
    if (user?.role === 'ORG_ADMIN') {
      let cancelled = false;
      api.get(`/orgs/${user.orgId}/teams`)
        .then((res) => {
          if (cancelled) return;
          const teams: TeamInfo[] = res.data.teams.map((t: { team_id: string; name: string }) => ({
            teamId: t.team_id,
            teamName: t.name,
          }));
          teams.sort((a, b) => a.teamName.localeCompare(b.teamName));
          setAllTeams(teams);
        })
        .catch(() => {
          if (!cancelled) setAllTeams(null);
        });
      return () => { cancelled = true; };
    }
  }, [user?.role, user?.orgId]);

  // Close sidebar on route change (mobile)
  useEffect(() => {
    setSidebarOpen(false);
  }, [location.pathname]);

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
      {/* Mobile header bar */}
      <div className="fixed top-0 left-0 right-0 z-30 bg-slate-900 text-white flex items-center justify-between p-3 md:hidden">
        <button
          onClick={() => setSidebarOpen(!sidebarOpen)}
          className="p-1 rounded hover:bg-slate-800"
          aria-label="Toggle menu"
        >
          <svg className="w-6 h-6" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            {sidebarOpen ? (
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12" />
            ) : (
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M4 6h16M4 12h16M4 18h16" />
            )}
          </svg>
        </button>
        <h1 className="text-sm font-bold">Analytics Dashboard</h1>
        <div className="w-6" />
      </div>

      {/* Overlay for mobile */}
      {sidebarOpen && (
        <div
          className="fixed inset-0 z-30 bg-black/50 md:hidden"
          onClick={() => setSidebarOpen(false)}
        />
      )}

      {/* Sidebar */}
      <aside className={`
        fixed inset-y-0 left-0 z-40 w-64 bg-slate-900 text-white flex flex-col
        transform transition-transform duration-200 ease-in-out
        ${sidebarOpen ? 'translate-x-0' : '-translate-x-full'}
        md:translate-x-0 md:static md:z-auto
      `}>
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
      <main className="flex-1 overflow-auto pt-14 md:pt-0">
        <div className="p-6">
          <Outlet />
        </div>
      </main>
    </div>
  );
}
