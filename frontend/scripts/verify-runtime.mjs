/** Executed in the Frontend container; never shipped as a browser route. */
export async function verifyRuntime({ backendHealthUrl, nginxOrigin }, fetcher = fetch) {
  const request = url => fetcher(url, {signal: AbortSignal.timeout(5000), redirect: "error"});
  const health = await request(backendHealthUrl);
  if (health.status !== 200 || (await health.json()).status !== "UP") throw new Error("Backend/database is not healthy");
  const page = await request(`${nginxOrigin}/`);
  if (page.status !== 200 || !(await page.text()).includes("데일리 이직")) throw new Error("Nginx → Frontend failed");
  // A protected API-prefix request proves proxy routing without inventing an endpoint.
  const api = await request(`${nginxOrigin}/api/v1/`);
  if (api.status !== 401 || !api.headers.get("X-Trace-Id")) throw new Error("Nginx → Backend security boundary failed");
  const management = await request(`${nginxOrigin}/actuator/health`);
  if (management.status !== 404) throw new Error("Internal management must not be exposed by Nginx");
}
