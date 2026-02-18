import { render, screen } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { http, HttpResponse } from 'msw';
import { server } from '@/test/mocks/server';
import RunDetail from './RunDetail';

// ── Mocks ───────────────────────────────────────────────────────────────────
const mockNavigate = vi.fn();
vi.mock('react-router-dom', async () => {
  const actual = await vi.importActual('react-router-dom');
  return { ...actual, useNavigate: () => mockNavigate };
});

// ── Helpers ─────────────────────────────────────────────────────────────────
const SUCCEEDED_RUN_ID = '00000000-0000-0000-0000-000000000100';
const FAILED_RUN_ID = '00000000-0000-0000-0000-000000000200';

function renderPage(runId: string = SUCCEEDED_RUN_ID) {
  return render(
    <MemoryRouter initialEntries={[`/runs/${runId}`]}>
      <Routes>
        <Route path="/runs/:runId" element={<RunDetail />} />
      </Routes>
    </MemoryRouter>,
  );
}

// ── Tests ───────────────────────────────────────────────────────────────────
describe('RunDetail', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  // FE-PG-051
  it('renders run metadata: Run ID, Agent Type, Model, Status, Started, Finished, Duration', async () => {
    renderPage();

    expect(await screen.findByText('Run Metadata')).toBeInTheDocument();
    expect(screen.getByText('Run ID')).toBeInTheDocument();
    expect(screen.getByText('Agent Type')).toBeInTheDocument();
    expect(screen.getByText('Model')).toBeInTheDocument();
    expect(screen.getByText('Status')).toBeInTheDocument();
    expect(screen.getByText('Started')).toBeInTheDocument();
    expect(screen.getByText('Finished')).toBeInTheDocument();
    expect(screen.getByText('Duration')).toBeInTheDocument();

    // From buildRunDetail: agentTypeDisplayName = 'Code Review', modelName = 'claude-sonnet', modelVersion = '4.0'
    expect(screen.getByText('Code Review')).toBeInTheDocument();
    expect(screen.getByText(/claude-sonnet/)).toBeInTheDocument();
    expect(screen.getByText(/4\.0/)).toBeInTheDocument();
  });

  // FE-PG-052
  it('renders token breakdown: Input, Output, Total', async () => {
    renderPage();

    expect(await screen.findByText('Token & Cost Breakdown')).toBeInTheDocument();
    expect(screen.getByText('Input Tokens')).toBeInTheDocument();
    expect(screen.getByText('Output Tokens')).toBeInTheDocument();
    expect(screen.getByText('Total Tokens')).toBeInTheDocument();

    // From buildRunDetail: inputTokens=8500, outputTokens=4000, totalTokens=12500
    expect(screen.getByText('8,500')).toBeInTheDocument();
    expect(screen.getByText('4,000')).toBeInTheDocument();
    expect(screen.getByText('12,500')).toBeInTheDocument();
  });

  // FE-PG-053
  it('renders cost breakdown: Input, Output, Total', async () => {
    renderPage();

    expect(await screen.findByText('Input Cost')).toBeInTheDocument();
    expect(screen.getByText('Output Cost')).toBeInTheDocument();
    expect(screen.getByText('Total Cost')).toBeInTheDocument();

    // From buildRunDetail: inputCost='2.55', outputCost='1.20', totalCost='3.75'
    expect(screen.getByText('$2.5500')).toBeInTheDocument();
    expect(screen.getByText('$1.2000')).toBeInTheDocument();
    expect(screen.getByText('$3.7500')).toBeInTheDocument();
  });

  // FE-PG-054
  it('renders StatusBadge with SUCCEEDED status', async () => {
    renderPage();

    // StatusBadge renders the status text
    expect(await screen.findByText('SUCCEEDED')).toBeInTheDocument();
  });

  it('shows error details section for FAILED run', async () => {
    server.use(
      http.get('/api/v1/runs/:runId', () => {
        return HttpResponse.json({
          runId: FAILED_RUN_ID,
          orgId: '00000000-0000-0000-0000-000000000001',
          teamId: '00000000-0000-0000-0000-000000000010',
          userId: '00000000-0000-0000-0000-000000000002',
          agentType: 'CODE_REVIEW',
          agentTypeDisplayName: 'Code Review',
          modelName: 'claude-sonnet',
          modelVersion: '4.0',
          status: 'FAILED',
          startedAt: '2025-01-15T10:30:00Z',
          finishedAt: '2025-01-15T10:31:00Z',
          durationMs: 60000,
          inputTokens: 5000,
          outputTokens: 2000,
          totalTokens: 7000,
          inputCost: '1.50',
          outputCost: '0.60',
          totalCost: '2.10',
          errorCategory: 'RATE_LIMIT',
          errorMessage: 'Rate limit exceeded for model',
        });
      }),
    );

    renderPage(FAILED_RUN_ID);

    expect(await screen.findByText('Error Details')).toBeInTheDocument();
    expect(screen.getByText(/RATE_LIMIT/)).toBeInTheDocument();
    expect(screen.getByText(/Rate limit exceeded for model/)).toBeInTheDocument();
  });

  it('does not show error section for SUCCEEDED run', async () => {
    renderPage();

    await screen.findByText('Run Metadata');

    expect(screen.queryByText('Error Details')).not.toBeInTheDocument();
  });

  it('shows Back button', async () => {
    renderPage();

    expect(await screen.findByText(/back/i)).toBeInTheDocument();
  });

  it('shows loading state', () => {
    renderPage();

    expect(screen.getByText(/loading run details/i)).toBeInTheDocument();
  });
});
