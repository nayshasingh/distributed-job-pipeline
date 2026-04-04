import axios from 'axios';

/**
 * In development, Vite proxies /jobs to the Spring Boot API (see vite.config.js).
 * Without proxy: set VITE_API_BASE_URL to your API (e.g. http://localhost:8081). With proxy: leave unset.
 */
const baseURL = import.meta.env.VITE_API_BASE_URL ?? '';

const client = axios.create({
  baseURL,
  headers: {
    'Content-Type': 'application/json',
  },
  timeout: 30_000,
});

export async function fetchJobs() {
  const { data } = await client.get('/jobs');
  return data;
}

export async function fetchJobById(id) {
  const { data } = await client.get(`/jobs/${id}`);
  return data;
}

export async function createJob(body) {
  const { data } = await client.post('/jobs', body);
  return data;
}

export { client };
