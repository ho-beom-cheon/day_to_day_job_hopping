const response = await fetch("http://127.0.0.1:3000/", {signal: AbortSignal.timeout(5000)});
if (response.status !== 200) process.exit(1);
await response.body?.cancel();
