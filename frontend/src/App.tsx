import { Routes, Route, Navigate } from 'react-router-dom';
import { useAuth } from './context/AuthContext';
import LoginPage from './pages/LoginPage';
import Layout from './components/Layout';
import OrgDashboard from './pages/OrgDashboard';
import TeamDashboard from './pages/TeamDashboard';
import PersonalDashboard from './pages/PersonalDashboard';
import UserDashboard from './pages/UserDashboard';
import RunDetail from './pages/RunDetail';

function PrivateRoute({ children }: { children: React.ReactNode }) {
  const { isAuthenticated } = useAuth();
  return isAuthenticated ? <>{children}</> : <Navigate to="/login" />;
}

function RoleRedirect() {
  const { user } = useAuth();
  if (!user) return <Navigate to="/login" />;
  switch (user.role) {
    case 'ORG_ADMIN':
      return <Navigate to="/org" />;
    case 'TEAM_LEAD':
      return <Navigate to={`/teams/${user.teams[0]?.teamId}`} />;
    default:
      return <Navigate to="/me" />;
  }
}

export default function App() {
  return (
    <Routes>
      <Route path="/login" element={<LoginPage />} />
      <Route
        path="/"
        element={
          <PrivateRoute>
            <RoleRedirect />
          </PrivateRoute>
        }
      />
      <Route
        element={
          <PrivateRoute>
            <Layout />
          </PrivateRoute>
        }
      >
        <Route path="/org" element={<OrgDashboard />} />
        <Route path="/teams/:teamId" element={<TeamDashboard />} />
        <Route path="/me" element={<PersonalDashboard />} />
        <Route path="/users/:userId" element={<UserDashboard />} />
        <Route path="/runs/:runId" element={<RunDetail />} />
      </Route>
      <Route path="*" element={<Navigate to="/" />} />
    </Routes>
  );
}
