const STATUS_OPTIONS = ['', 'PENDING', 'RUNNING', 'SUCCESS', 'FAILED'];
const PRIORITY_OPTIONS = ['', 'HIGH', 'LOW'];

export default function Filters({ statusFilter, priorityFilter, onStatusChange, onPriorityChange }) {
  return (
    <div className="flex flex-col gap-4 sm:flex-row sm:items-end sm:justify-between">
      <div>
        <h2 className="text-lg font-semibold text-slate-800">Filters</h2>
        <p className="text-sm text-slate-500">Narrow the table by status and priority</p>
      </div>
      <div className="flex flex-col gap-3 sm:flex-row sm:gap-4">
        <label className="flex flex-col gap-1">
          <span className="text-xs font-medium uppercase tracking-wide text-slate-500">Status</span>
          <select
            value={statusFilter}
            onChange={(e) => onStatusChange(e.target.value)}
            className="min-w-[160px] rounded-lg border border-slate-200 bg-white px-3 py-2 text-sm text-slate-800 shadow-sm focus:border-sky-500 focus:outline-none focus:ring-2 focus:ring-sky-500/20"
          >
            {STATUS_OPTIONS.map((v) => (
              <option key={v || 'all'} value={v}>
                {v === '' ? 'All statuses' : v}
              </option>
            ))}
          </select>
        </label>
        <label className="flex flex-col gap-1">
          <span className="text-xs font-medium uppercase tracking-wide text-slate-500">Priority</span>
          <select
            value={priorityFilter}
            onChange={(e) => onPriorityChange(e.target.value)}
            className="min-w-[160px] rounded-lg border border-slate-200 bg-white px-3 py-2 text-sm text-slate-800 shadow-sm focus:border-sky-500 focus:outline-none focus:ring-2 focus:ring-sky-500/20"
          >
            {PRIORITY_OPTIONS.map((v) => (
              <option key={v || 'all'} value={v}>
                {v === '' ? 'All priorities' : v}
              </option>
            ))}
          </select>
        </label>
      </div>
    </div>
  );
}
