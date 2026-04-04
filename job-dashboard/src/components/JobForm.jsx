import { useState } from 'react';
import { createJob } from '../services/api';

const defaultPayload = '{\n  "example": "value"\n}';

export default function JobForm({ onJobCreated }) {
  const [jobType, setJobType] = useState('');
  const [priority, setPriority] = useState('LOW');
  const [payloadText, setPayloadText] = useState(defaultPayload);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState(null);
  const [success, setSuccess] = useState(null);

  async function handleSubmit(e) {
    e.preventDefault();
    setError(null);
    setSuccess(null);

    let payloadJson;
    try {
      payloadJson = JSON.parse(payloadText);
    } catch {
      setError('Payload must be valid JSON.');
      return;
    }

    if (!jobType.trim()) {
      setError('Job type is required.');
      return;
    }

    setSubmitting(true);
    try {
      const body = {
        jobType: jobType.trim(),
        payload: payloadJson,
        priority: priority === 'HIGH' ? 'HIGH' : 'LOW',
      };
      const created = await createJob(body);
      setSuccess(`Job created: ${created.id}`);
      setJobType('');
      setPayloadText(defaultPayload);
      setPriority('LOW');
      onJobCreated?.(created);
    } catch (err) {
      const msg =
        err.response?.data?.message ||
        err.response?.data?.title ||
        err.message ||
        'Failed to create job';
      setError(typeof msg === 'string' ? msg : JSON.stringify(msg));
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <form
      onSubmit={handleSubmit}
      className="rounded-2xl border border-slate-200/80 bg-white p-6 shadow-sm shadow-slate-900/5"
    >
      <h2 className="text-lg font-semibold text-slate-800">Create job</h2>
      <p className="mt-1 text-sm text-slate-500">POST /jobs — jobs are queued for workers</p>

      <div className="mt-6 grid gap-5 sm:grid-cols-2">
        <label className="flex flex-col gap-2">
          <span className="text-sm font-medium text-slate-700">Job type</span>
          <input
            type="text"
            value={jobType}
            onChange={(e) => setJobType(e.target.value)}
            placeholder="e.g. EMAIL, REPORT"
            className="rounded-lg border border-slate-200 px-3 py-2 text-sm text-slate-900 placeholder:text-slate-400 focus:border-sky-500 focus:outline-none focus:ring-2 focus:ring-sky-500/20"
          />
        </label>
        <label className="flex flex-col gap-2">
          <span className="text-sm font-medium text-slate-700">Priority</span>
          <select
            value={priority}
            onChange={(e) => setPriority(e.target.value)}
            className="rounded-lg border border-slate-200 px-3 py-2 text-sm text-slate-900 focus:border-sky-500 focus:outline-none focus:ring-2 focus:ring-sky-500/20"
          >
            <option value="LOW">LOW</option>
            <option value="HIGH">HIGH</option>
          </select>
        </label>
      </div>

      <label className="mt-5 flex flex-col gap-2">
        <span className="text-sm font-medium text-slate-700">Payload (JSON)</span>
        <textarea
          value={payloadText}
          onChange={(e) => setPayloadText(e.target.value)}
          rows={8}
          spellCheck={false}
          className="font-mono text-sm rounded-lg border border-slate-200 px-3 py-2 text-slate-900 focus:border-sky-500 focus:outline-none focus:ring-2 focus:ring-sky-500/20"
        />
      </label>

      {error && (
        <div className="mt-4 rounded-lg border border-red-200 bg-red-50 px-3 py-2 text-sm text-red-800">
          {error}
        </div>
      )}
      {success && (
        <div className="mt-4 rounded-lg border border-emerald-200 bg-emerald-50 px-3 py-2 text-sm text-emerald-800">
          {success}
        </div>
      )}

      <div className="mt-6 flex justify-end">
        <button
          type="submit"
          disabled={submitting}
          className="inline-flex items-center justify-center rounded-lg bg-sky-600 px-5 py-2.5 text-sm font-semibold text-white shadow-sm transition hover:bg-sky-700 disabled:cursor-not-allowed disabled:opacity-60"
        >
          {submitting ? 'Submitting…' : 'Submit job'}
        </button>
      </div>
    </form>
  );
}
