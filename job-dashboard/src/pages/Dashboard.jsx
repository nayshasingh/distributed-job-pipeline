import { useCallback, useEffect, useMemo, useState } from 'react';
import { fetchJobs } from '../services/api';
import JobForm from '../components/JobForm';
import JobTable from '../components/JobTable';
import Filters from '../components/Filters';

const REFRESH_MS = 5000;

function countByStatus(jobs) {
  const acc = { PENDING: 0, RUNNING: 0, SUCCESS: 0, FAILED: 0 };
  for (const j of jobs) {
    if (acc[j.status] !== undefined) acc[j.status] += 1;
  }
  return acc;
}

function SummaryCard({ label, value, accent }) {
  const accents = {
    amber: 'from-amber-500/10 to-amber-500/5 border-amber-200/60',
    sky: 'from-sky-500/10 to-sky-500/5 border-sky-200/60',
    emerald: 'from-emerald-500/10 to-emerald-500/5 border-emerald-200/60',
    red: 'from-red-500/10 to-red-500/5 border-red-200/60',
    slate: 'from-slate-500/10 to-slate-500/5 border-slate-200/60',
  };
  return (
    <div
      className={`rounded-xl border bg-gradient-to-br p-4 ${accents[accent] || accents.slate}`}
    >
      <p className="text-xs font-semibold uppercase tracking-wide text-slate-500">{label}</p>
      <p className="mt-1 text-2xl font-bold tabular-nums text-slate-900">{value}</p>
    </div>
  );
}

export default function Dashboard() {
  const [jobs, setJobs] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [statusFilter, setStatusFilter] = useState('');
  const [priorityFilter, setPriorityFilter] = useState('');
  const [lastUpdated, setLastUpdated] = useState(null);

  const loadJobs = useCallback(async () => {
    setError(null);
    try {
      const data = await fetchJobs();
      setJobs(Array.isArray(data) ? data : []);
      setLastUpdated(new Date());
    } catch (err) {
      const msg =
        err.response?.data?.message ||
        err.response?.statusText ||
        err.message ||
        'Failed to load jobs';
      setError(typeof msg === 'string' ? msg : 'Failed to load jobs');
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    loadJobs();
  }, [loadJobs]);

  useEffect(() => {
    const id = setInterval(() => {
      loadJobs();
    }, REFRESH_MS);
    return () => clearInterval(id);
  }, [loadJobs]);

  const filteredJobs = useMemo(() => {
    return jobs.filter((j) => {
      if (statusFilter && j.status !== statusFilter) return false;
      if (priorityFilter && (j.priority || 'LOW') !== priorityFilter) return false;
      return true;
    });
  }, [jobs, statusFilter, priorityFilter]);

  const statusCounts = useMemo(() => countByStatus(jobs), [jobs]);

  return (
    <div className="min-h-screen bg-gradient-to-b from-slate-100 to-slate-200/80">
      <header className="border-b border-slate-200/80 bg-white/90 backdrop-blur">
        <div className="mx-auto flex max-w-6xl flex-col gap-2 px-4 py-6 sm:flex-row sm:items-center sm:justify-between">
          <div>
            <h1 className="text-2xl font-bold tracking-tight text-slate-900">
              Distributed Job Scheduler
            </h1>
            <p className="text-sm text-slate-500">
              Dashboard · Auto-refresh every {REFRESH_MS / 1000}s
              {lastUpdated && (
                <span className="ml-2 text-slate-400">
                  · Last sync {lastUpdated.toLocaleTimeString()}
                </span>
              )}
            </p>
          </div>
          <button
            type="button"
            onClick={() => {
              setLoading(true);
              loadJobs();
            }}
            className="inline-flex items-center justify-center gap-2 rounded-lg border border-slate-200 bg-white px-4 py-2 text-sm font-semibold text-slate-700 shadow-sm hover:bg-slate-50"
          >
            <svg className="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor">
              <path
                strokeLinecap="round"
                strokeLinejoin="round"
                strokeWidth={2}
                d="M4 4v5h.582m15.356 2A8.001 8.001 0 004.582 9m0 0H9m11 11v-5h-.581m0 0a8.003 8.003 0 01-15.357-2m15.357 2H15"
              />
            </svg>
            Refresh now
          </button>
        </div>
      </header>

      <main className="mx-auto max-w-6xl space-y-8 px-4 py-8">
        {error && (
          <div className="rounded-xl border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-800">
            <strong className="font-semibold">API error:</strong> {error}
            <p className="mt-1 text-red-700/90">
              Ensure the API is running (same port as VITE_API_PROXY_TARGET or 8080) and CORS allows this origin, or use{' '}
              <code className="rounded bg-red-100 px-1">npm start</code> with the Vite proxy.
            </p>
          </div>
        )}

        <section className="grid gap-4 sm:grid-cols-2 lg:grid-cols-5">
          <SummaryCard label="Pending" value={statusCounts.PENDING} accent="amber" />
          <SummaryCard label="Running" value={statusCounts.RUNNING} accent="sky" />
          <SummaryCard label="Success" value={statusCounts.SUCCESS} accent="emerald" />
          <SummaryCard label="Failed" value={statusCounts.FAILED} accent="red" />
          <SummaryCard label="Total" value={jobs.length} accent="slate" />
        </section>

        <JobForm onJobCreated={() => loadJobs()} />

        <section className="rounded-2xl border border-slate-200/80 bg-white p-6 shadow-sm shadow-slate-900/5">
          <Filters
            statusFilter={statusFilter}
            priorityFilter={priorityFilter}
            onStatusChange={setStatusFilter}
            onPriorityChange={setPriorityFilter}
          />
          <div className="mt-6">
            {loading && jobs.length > 0 && (
              <div className="mb-4 flex items-center gap-2 text-sm text-slate-500">
                <span className="h-4 w-4 animate-spin rounded-full border-2 border-sky-600 border-t-transparent" />
                Refreshing…
              </div>
            )}
            <JobTable jobs={filteredJobs} loading={loading && jobs.length === 0} />
          </div>
        </section>
      </main>

      <footer className="border-t border-slate-200/80 bg-white/80 py-6 text-center text-xs text-slate-400">
        Job Scheduler Dashboard · React + Tailwind + Axios
      </footer>
    </div>
  );
}
