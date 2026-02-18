import { useState, useEffect } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import api from '../api/client';
import StatusBadge from '../components/StatusBadge';
import type { RunDetail as RunDetailType } from '../types';

export default function RunDetail() {
  const { runId } = useParams<{ runId: string }>();
  const navigate = useNavigate();
  const [run, setRun] = useState<RunDetailType | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  useEffect(() => {
    if (!runId) return;
    setLoading(true);
    api.get(`/runs/${runId}`)
      .then((res) => setRun(res.data))
      .catch((err) => setError(err.response?.data?.detail || 'Failed to load run detail'))
      .finally(() => setLoading(false));
  }, [runId]);

  if (loading) return <div className="text-slate-500">Loading run details...</div>;
  if (error) return <div className="text-red-600">{error}</div>;
  if (!run) return null;

  return (
    <div className="space-y-6">
      <div className="flex items-center gap-4">
        <button
          onClick={() => navigate(-1)}
          className="text-sm text-blue-600 hover:text-blue-800"
        >
          &larr; Back
        </button>
        <h1 className="text-2xl font-bold text-slate-900">Run Detail</h1>
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
        {/* Run Metadata */}
        <div className="bg-white rounded-lg border border-slate-200 p-6 shadow-sm">
          <h2 className="text-lg font-semibold text-slate-800 mb-4">Run Metadata</h2>
          <dl className="space-y-3 text-sm">
            <div className="flex justify-between">
              <dt className="text-slate-500">Run ID</dt>
              <dd className="font-mono text-xs text-slate-700">{run.runId}</dd>
            </div>
            <div className="flex justify-between">
              <dt className="text-slate-500">Agent Type</dt>
              <dd className="font-medium">{run.agentTypeDisplayName}</dd>
            </div>
            <div className="flex justify-between">
              <dt className="text-slate-500">Model</dt>
              <dd>{run.modelName} ({run.modelVersion})</dd>
            </div>
            <div className="flex justify-between items-center">
              <dt className="text-slate-500">Status</dt>
              <dd><StatusBadge status={run.status} /></dd>
            </div>
            <div className="flex justify-between">
              <dt className="text-slate-500">Started</dt>
              <dd>{new Date(run.startedAt).toLocaleString()}</dd>
            </div>
            <div className="flex justify-between">
              <dt className="text-slate-500">Finished</dt>
              <dd>{run.finishedAt ? new Date(run.finishedAt).toLocaleString() : '-'}</dd>
            </div>
            <div className="flex justify-between">
              <dt className="text-slate-500">Duration</dt>
              <dd>{(run.durationMs / 1000).toFixed(1)}s</dd>
            </div>
          </dl>
        </div>

        {/* Token & Cost Breakdown */}
        <div className="bg-white rounded-lg border border-slate-200 p-6 shadow-sm">
          <h2 className="text-lg font-semibold text-slate-800 mb-4">Token & Cost Breakdown</h2>
          <dl className="space-y-3 text-sm">
            <div className="flex justify-between">
              <dt className="text-slate-500">Input Tokens</dt>
              <dd className="font-medium">{run.inputTokens.toLocaleString()}</dd>
            </div>
            <div className="flex justify-between">
              <dt className="text-slate-500">Output Tokens</dt>
              <dd className="font-medium">{run.outputTokens.toLocaleString()}</dd>
            </div>
            <div className="flex justify-between border-t border-slate-100 pt-2">
              <dt className="text-slate-500 font-medium">Total Tokens</dt>
              <dd className="font-bold">{run.totalTokens.toLocaleString()}</dd>
            </div>
            <div className="mt-4"></div>
            <div className="flex justify-between">
              <dt className="text-slate-500">Input Cost</dt>
              <dd>${parseFloat(run.inputCost).toFixed(4)}</dd>
            </div>
            <div className="flex justify-between">
              <dt className="text-slate-500">Output Cost</dt>
              <dd>${parseFloat(run.outputCost).toFixed(4)}</dd>
            </div>
            <div className="flex justify-between border-t border-slate-100 pt-2">
              <dt className="text-slate-500 font-medium">Total Cost</dt>
              <dd className="font-bold">${parseFloat(run.totalCost).toFixed(4)}</dd>
            </div>
          </dl>
        </div>
      </div>

      {/* Error Details */}
      {run.status === 'FAILED' && run.errorCategory && (
        <div className="bg-red-50 border border-red-200 rounded-lg p-6">
          <h2 className="text-lg font-semibold text-red-800 mb-2">Error Details</h2>
          <p className="text-sm text-red-700">
            <strong>Category:</strong> {run.errorCategory}
          </p>
          {run.errorMessage && (
            <p className="text-sm text-red-600 mt-2">{run.errorMessage}</p>
          )}
        </div>
      )}
    </div>
  );
}
