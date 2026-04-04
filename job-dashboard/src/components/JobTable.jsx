function StatusBadge({ status }) {
  const styles = {
    PENDING: 'bg-amber-100 text-amber-900 ring-amber-600/20',
    RUNNING: 'bg-sky-100 text-sky-900 ring-sky-600/20',
    SUCCESS: 'bg-emerald-100 text-emerald-900 ring-emerald-600/20',
    FAILED: 'bg-red-100 text-red-900 ring-red-600/20',
  };
  const cls = styles[status] || 'bg-slate-100 text-slate-800 ring-slate-600/10';
  return (
    <span
      className={`inline-flex items-center rounded-md px-2 py-1 text-xs font-semibold ring-1 ring-inset ${cls}`}
    >
      {status}
    </span>
  );
}

function PriorityBadge({ priority }) {
  const isHigh = priority === 'HIGH';
  return (
    <span
      className={`inline-flex rounded-md px-2 py-1 text-xs font-semibold ${
        isHigh
          ? 'bg-violet-100 text-violet-900 ring-1 ring-inset ring-violet-600/20'
          : 'bg-slate-100 text-slate-700 ring-1 ring-inset ring-slate-500/15'
      }`}
    >
      {priority || 'LOW'}
    </span>
  );
}

function formatDate(iso) {
  if (!iso) return '—';
  try {
    return new Date(iso).toLocaleString(undefined, {
      dateStyle: 'medium',
      timeStyle: 'short',
    });
  } catch {
    return iso;
  }
}

function shortenId(id) {
  if (!id || id.length < 12) return id;
  return `${id.slice(0, 8)}…${id.slice(-4)}`;
}

export default function JobTable({ jobs, loading }) {
  if (loading && jobs.length === 0) {
    return (
      <div className="flex min-h-[200px] items-center justify-center rounded-2xl border border-dashed border-slate-200 bg-slate-50/50">
        <div className="flex items-center gap-3 text-slate-600">
          <span className="h-5 w-5 animate-spin rounded-full border-2 border-sky-600 border-t-transparent" />
          Loading jobs…
        </div>
      </div>
    );
  }

  if (!loading && jobs.length === 0) {
    return (
      <div className="rounded-2xl border border-slate-200 bg-white p-12 text-center text-slate-500 shadow-sm">
        No jobs match the current filters.
      </div>
    );
  }

  return (
    <div className="overflow-hidden rounded-2xl border border-slate-200/80 bg-white shadow-sm shadow-slate-900/5">
      <div className="overflow-x-auto">
        <table className="min-w-full divide-y divide-slate-200 text-left text-sm">
          <thead className="bg-slate-50">
            <tr>
              <th className="px-4 py-3 font-semibold text-slate-600">Job ID</th>
              <th className="px-4 py-3 font-semibold text-slate-600">Job Type</th>
              <th className="px-4 py-3 font-semibold text-slate-600">Priority</th>
              <th className="px-4 py-3 font-semibold text-slate-600">Status</th>
              <th className="px-4 py-3 font-semibold text-slate-600">Retry Count</th>
              <th className="px-4 py-3 font-semibold text-slate-600">Created At</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-slate-100">
            {jobs.map((job) => (
              <tr key={job.id} className="hover:bg-slate-50/80">
                <td className="whitespace-nowrap px-4 py-3 font-mono text-xs text-slate-700" title={job.id}>
                  {shortenId(job.id)}
                </td>
                <td className="px-4 py-3 font-medium text-slate-800">{job.jobType}</td>
                <td className="px-4 py-3">
                  <PriorityBadge priority={job.priority} />
                </td>
                <td className="px-4 py-3">
                  <StatusBadge status={job.status} />
                </td>
                <td className="px-4 py-3 tabular-nums text-slate-700">{job.retryCount ?? 0}</td>
                <td className="whitespace-nowrap px-4 py-3 text-slate-600">{formatDate(job.createdAt)}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  );
}
