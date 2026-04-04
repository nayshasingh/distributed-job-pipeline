# Job Scheduler Dashboard

React + Tailwind + Axios UI for the **Distributed Job Scheduler** API (`GET/POST /jobs`).

## Run

1. Start the API (default **http://localhost:8080**, or e.g. **8081** with `SERVER_PORT=8081` — see below).
2. From this directory:

```bash
npm install
npm start
```

Opens **http://localhost:3000** with a Vite dev server that **proxies** `/jobs` to the API.

If the API is **not** on port 8080, create `job-dashboard/.env`:

```bash
VITE_API_PROXY_TARGET=http://localhost:8081
```

Then restart `npm start`.

## Build

```bash
npm run build
npm run preview
```

For production builds served separately, set `VITE_API_BASE_URL=http://localhost:8080` in `.env` and ensure the API allows your UI origin (CORS is preconfigured for ports 3000 and 5173).

## Structure

- `src/services/api.js` — Axios client
- `src/components/` — `JobTable`, `JobForm`, `Filters`
- `src/pages/Dashboard.jsx` — layout, auto-refresh (5s), summary cards
