const cors = {
  "access-control-allow-origin": "*",
  "access-control-allow-headers": "authorization, content-type",
  "access-control-allow-methods": "GET,POST,DELETE,OPTIONS"
};

const json = (data, status = 200, extraHeaders = {}) =>
  new Response(JSON.stringify(data), {
    status,
    headers: {
      "content-type": "application/json; charset=utf-8",
      "cache-control": "no-store",
      ...cors,
      ...extraHeaders
    }
  });

const text = (data, status = 200, extraHeaders = {}) =>
  new Response(data, {
    status,
    headers: {
      "content-type": "text/plain; charset=utf-8",
      ...cors,
      ...extraHeaders
    }
  });

function cleanBase(value) {
  return String(value || "").replace(/\/+$/, "");
}

function supabaseHeaders(env, token, service = false) {
  const bearer = service
    ? env.SUPABASE_SERVICE_ROLE_KEY
    : (token || env.SUPABASE_SERVICE_ROLE_KEY || env.SUPABASE_PUBLISHABLE_KEY);

  return {
    apikey: env.SUPABASE_PUBLISHABLE_KEY || "",
    Authorization: bearer ? `Bearer ${bearer}` : "",
    "Content-Type": "application/json"
  };
}

async function supabaseFetch(env, path, options = {}, token = null, service = false) {
  if (!env.SUPABASE_URL || !env.SUPABASE_PUBLISHABLE_KEY) {
    return new Response(JSON.stringify({ error: "Supabase is not configured" }), { status: 503 });
  }

  return fetch(`${cleanBase(env.SUPABASE_URL)}${path}`, {
    ...options,
    headers: { ...supabaseHeaders(env, token, service), ...(options.headers || {}) }
  });
}

async function readJson(request) {
  try {
    return await request.json();
  } catch {
    return {};
  }
}

async function requireUser(request, env) {
  const auth = request.headers.get("authorization") || "";
  const token = auth.replace(/^Bearer\s+/i, "").trim();

  if (!token) return { error: "Missing bearer token" };

  const r = await supabaseFetch(env, "/auth/v1/user", { method: "GET" }, token);
  if (!r.ok) return { error: "Invalid or expired session" };

  const user = await r.json();
  return user?.id ? { user, token } : { error: "Invalid user session" };
}

async function postgrestJson(env, path, body, options = {}, token = null) {
  const r = await supabaseFetch(
    env,
    path,
    {
      method: "POST",
      headers: {
        Prefer: "return=representation",
        ...(options.headers || {})
      },
      body: JSON.stringify(body)
    },
    token,
    Boolean(options.service)
  );

  const raw = await r.text();
  let data;
  try { data = JSON.parse(raw); } catch { data = { raw }; }
  return { ok: r.ok, status: r.status, data };
}

async function ensureProfile(env, user) {
  if (!user?.id || !env.SUPABASE_SERVICE_ROLE_KEY || !env.SUPABASE_PUBLISHABLE_KEY) return;
  const usernameBase = String(
    user.user_metadata?.username ||
    user.email?.split("@")[0] ||
    "traveler"
  )
    .toLowerCase()
    .replace(/[^a-z0-9_]/g, "_")
    .replace(/^_+|_+$/g, "")
    .slice(0, 28) || "traveler";

  const existing = await supabaseFetch(
    env,
    `/rest/v1/profiles?id=eq.${encodeURIComponent(user.id)}&select=id,username`,
    { method: "GET" },
    null,
    true
  );
  if (existing.ok && (await existing.json()).length) return;

  let username = usernameBase;
  for (let attempt = 0; attempt < 4; attempt++) {
    const suffix = attempt === 0 ? "" : "_" + Math.floor(Math.random() * 900 + 100);
    const candidate = (usernameBase + suffix).slice(0, 32);
    const payload = [{
      id: user.id,
      username: candidate,
      display_name: user.user_metadata?.display_name || user.email?.split("@")[0] || "WakeWay traveler"
    }];

    const created = await supabaseFetch(
      env,
      "/rest/v1/profiles",
      {
        method: "POST",
        headers: { Prefer: "resolution=ignore-duplicates,return=minimal" },
        body: JSON.stringify(payload)
      },
      null,
      true
    );
    if (created.ok) return;
    username = candidate;
  }
}

async function authProxy(request, env, action) {
  const body = await readJson(request);

  if (!body.email || !body.password) {
    return json({ error: "Email and password are required" }, 400);
  }

  if (!env.SUPABASE_URL || !env.SUPABASE_PUBLISHABLE_KEY) {
    return json({ error: "Supabase is not configured on the Worker" }, 503);
  }

  const endpoint = action === "signup"
    ? "/auth/v1/signup"
    : "/auth/v1/token?grant_type=password";

  const r = await fetch(`${cleanBase(env.SUPABASE_URL)}${endpoint}`, {
    method: "POST",
    headers: {
      apikey: env.SUPABASE_PUBLISHABLE_KEY,
      "Content-Type": "application/json"
    },
    body: JSON.stringify({
      email: String(body.email).trim(),
      password: String(body.password)
    })
  });

  const data = await r.json();
  if (r.ok && data?.user?.id) {
    await ensureProfile(env, data.user);
  }
  return json(data, r.status);
}

async function profile(request, env, method) {
  const auth = await requireUser(request, env);
  if (auth.error) return json(auth, 401);

  if (method === "GET") {
    const r = await supabaseFetch(
      env,
      `/rest/v1/profiles?id=eq.${encodeURIComponent(auth.user.id)}&select=id,username,display_name,avatar_url,created_at`,
      { method: "GET" },
      null,
      true
    );
    const data = r.ok ? await r.json() : [];
    return json(data[0] || { id: auth.user.id });
  }

  const body = await readJson(request);
  const payload = {
    id: auth.user.id,
    username: body.username || null,
    display_name: body.display_name || null,
    avatar_url: body.avatar_url || null
  };

  const r = await supabaseFetch(
    env,
    "/rest/v1/profiles",
    {
      method: "POST",
      headers: { Prefer: "resolution=merge-duplicates,return=representation" },
      body: JSON.stringify(payload)
    },
    null,
    true
  );

  const data = await r.text();
  let parsed;
  try { parsed = JSON.parse(data); } catch { parsed = { raw: data }; }
  return json(Array.isArray(parsed) ? (parsed[0] || payload) : parsed, r.ok ? 200 : 400);
}

async function places(request, env, method, id = null) {
  const auth = await requireUser(request, env);
  if (auth.error) return json(auth, 401);

  if (method === "GET") {
    const r = await supabaseFetch(
      env,
      `/rest/v1/saved_places?user_id=eq.${encodeURIComponent(auth.user.id)}&select=id,name,address,latitude,longitude,created_at&order=created_at.desc`,
      { method: "GET" },
      null,
      true
    );
    return r.ok ? json(await r.json()) : json({ error: await r.text() }, 500);
  }

  if (method === "POST") {
    const body = await readJson(request);
    if (!body.name || body.latitude == null || body.longitude == null) {
      return json({ error: "name, latitude and longitude are required" }, 400);
    }

    const result = await postgrestJson(
      env,
      "/rest/v1/saved_places",
      {
        user_id: auth.user.id,
        name: String(body.name).slice(0, 120),
        address: String(body.address || "").slice(0, 300),
        latitude: Number(body.latitude),
        longitude: Number(body.longitude)
      },
      { service: true },
      null
    );

    return json(result.data, result.ok ? 201 : result.status);
  }

  if (method === "DELETE" && id) {
    const r = await supabaseFetch(
      env,
      `/rest/v1/saved_places?id=eq.${encodeURIComponent(id)}&user_id=eq.${encodeURIComponent(auth.user.id)}`,
      { method: "DELETE", headers: { Prefer: "return=minimal" } },
      null,
      true
    );
    return r.ok ? json({ ok: true }) : json({ error: await r.text() }, 500);
  }

  return json({ error: "Unsupported places operation" }, 405);
}

async function journeys(request, env, method, id = null) {
  const auth = await requireUser(request, env);
  if (auth.error) return json(auth, 401);

  if (method === "GET") {
    const u = new URL(request.url);
    const limit = Math.min(Math.max(Number(u.searchParams.get("limit") || 50), 1), 100);
    const r = await supabaseFetch(
      env,
      `/rest/v1/journeys?user_id=eq.${encodeURIComponent(auth.user.id)}&select=*&order=started_at.desc&limit=${limit}`,
      { method: "GET" },
      null,
      true
    );
    return r.ok ? json(await r.json()) : json({ error: await r.text() }, 500);
  }

  if (method === "POST" && !id) {
    const body = await readJson(request);
    if (!body.id || !body.destination_name || body.destination_lat == null || body.destination_lon == null) {
      return json({ error: "journey id and destination are required" }, 400);
    }

    const payload = {
      id: body.id,
      user_id: auth.user.id,
      destination_name: String(body.destination_name).slice(0, 160),
      destination_address: String(body.destination_address || "").slice(0, 300),
      destination_lat: Number(body.destination_lat),
      destination_lon: Number(body.destination_lon),
      transport_mode: String(body.transport_mode || "CAR"),
      started_at: body.started_at || new Date().toISOString(),
      ended_at: body.ended_at || null,
      status: String(body.status || "active")
    };

    const r = await supabaseFetch(
      env,
      "/rest/v1/journeys",
      {
        method: "POST",
        headers: { Prefer: "resolution=merge-duplicates,return=representation" },
        body: JSON.stringify(payload)
      },
      null,
      true
    );
    const raw = await r.text();
    let data;
    try { data = JSON.parse(raw); } catch { data = { raw }; }
    return json(Array.isArray(data) ? (data[0] || payload) : data, r.ok ? 200 : 400);
  }

  if (method === "POST" && id) {
    const body = await readJson(request);
    const payload = {
      ended_at: body.ended_at || new Date().toISOString(),
      status: body.status || "completed"
    };
    const r = await supabaseFetch(
      env,
      `/rest/v1/journeys?id=eq.${encodeURIComponent(id)}&user_id=eq.${encodeURIComponent(auth.user.id)}`,
      {
        method: "PATCH",
        headers: { Prefer: "return=representation" },
        body: JSON.stringify(payload)
      },
      null,
      true
    );
    const raw = await r.text();
    let data;
    try { data = JSON.parse(raw); } catch { data = { raw }; }
    return json(data, r.ok ? 200 : 400);
  }

  return json({ error: "Unsupported journey operation" }, 405);
}

async function placeSearch(request, env) {
  const u = new URL(request.url);
  const q = String(u.searchParams.get("q") || "").trim();
  if (q.length < 2) return json({ results: [], error: "q must contain at least 2 characters" }, 400);

  const r = await fetch(
    `https://geocoding-api.open-meteo.com/v1/search?name=${encodeURIComponent(q)}&count=8&language=en&format=json`
  );
  if (!r.ok) return json({ error: "Geocoding service unavailable" }, 502);

  const data = await r.json();
  const results = (data.results || []).map(x => ({
    name: [x.name, x.admin1, x.country].filter(Boolean).join(", "),
    shortName: x.name || "",
    address: [x.admin1, x.country].filter(Boolean).join(", "),
    latitude: x.latitude,
    longitude: x.longitude,
    country: x.country,
    countryCode: x.country_code,
    timezone: x.timezone
  }));
  return json({ results });
}

async function weather(request) {
  const u = new URL(request.url);
  const lat = Number(u.searchParams.get("lat"));
  const lon = Number(u.searchParams.get("lon"));
  if (!Number.isFinite(lat) || !Number.isFinite(lon)) return json({ error: "Valid lat/lon required" }, 400);

  const params = new URLSearchParams({
    latitude: String(lat),
    longitude: String(lon),
    current: "temperature_2m,relative_humidity_2m,apparent_temperature,is_day,precipitation,rain,weather_code,wind_speed_10m",
    hourly: "temperature_2m,precipitation_probability,weather_code,wind_speed_10m",
    daily: "weather_code,temperature_2m_max,temperature_2m_min,precipitation_probability_max",
    forecast_days: "3",
    timezone: "auto"
  });

  const r = await fetch(`https://api.open-meteo.com/v1/forecast?${params}`);
  if (!r.ok) return json({ error: "Weather service unavailable" }, 502);

  const data = await r.json();
  return json(data, 200, { "cache-control": "public, max-age=300" });
}

async function railRadar(request, env, endpoint, query = "") {
  const u = new URL(request.url);
  const number = String(u.searchParams.get("train") || "").trim();

  if (!env.RAILRADAR_API_KEY) {
    return json({
      configured: false,
      message: "RailRadar API key is not configured.",
      train: number || null
    });
  }

  const path = endpoint(number, u);
  const r = await fetch(`https://api.railradar.in${path}`, {
    headers: {
      Authorization: `Bearer ${env.RAILRADAR_API_KEY}`,
      Accept: "application/json"
    }
  });

  const raw = await r.text();
  let data;
  try { data = JSON.parse(raw); } catch { data = { raw }; }

  return json(data, r.status, { "cache-control": "public, max-age=30" });
}

async function trainLive(request, env) {
  const u = new URL(request.url);
  const train = String(u.searchParams.get("train") || "").trim();
  if (!/^\d{4,6}$/.test(train)) return json({ error: "Enter a valid train number" }, 400);

  return railRadar(
    request,
    env,
    number => `/v1/trains/${encodeURIComponent(number)}/live${buildQuery(u, ["train"])}`
  );
}

async function trainStations(request, env) {
  const u = new URL(request.url);
  const q = String(u.searchParams.get("q") || "").trim();
  if (q.length < 2) return json({ error: "q required" }, 400);

  return railRadar(
    request,
    env,
    () => `/v1/lookup/search/stations?q=${encodeURIComponent(q)}&limit=10`
  );
}

async function trainBetween(request, env) {
  const u = new URL(request.url);
  const from = String(u.searchParams.get("from") || "").trim().toUpperCase();
  const to = String(u.searchParams.get("to") || "").trim().toUpperCase();
  if (!from || !to) return json({ error: "from and to station codes are required" }, 400);

  const suffix = new URLSearchParams();
  for (const key of ["date", "type", "category", "byCity", "live"]) {
    if (u.searchParams.has(key)) suffix.set(key, u.searchParams.get(key));
  }
  return railRadar(
    request,
    env,
    () => `/v1/trains/between/${encodeURIComponent(from)}/${encodeURIComponent(to)}?${suffix.toString()}`
  );
}
async function trainRoute(request, env) {
  const u = new URL(request.url);
  const train = String(u.searchParams.get("train") || "").trim();
  if (!/^\d{4,6}$/.test(train)) return json({ error: "Valid train number required" }, 400);
  const format = u.searchParams.get("format") || "geojson";
  const stops = u.searchParams.get("stops") || "true";
  return railRadar(request, env, number => "/v1/trains/" + encodeURIComponent(number) + "/route?format=" + encodeURIComponent(format) + "&stops=" + encodeURIComponent(stops));
}

async function trainSeats(request, env) {
  const u = new URL(request.url);
  const train = String(u.searchParams.get("train") || "").trim();
  if (!/^\d{4,6}$/.test(train)) return json({ error: "Valid train number required" }, 400);
  const source = u.searchParams.get("source") || u.searchParams.get("from");
  const destination = u.searchParams.get("destination") || u.searchParams.get("to");
  if (!source || !destination) return json({ error: "source and destination are required" }, 400);
  const params = new URLSearchParams({
    source,
    destination,
    journeyDate: u.searchParams.get("journeyDate") || u.searchParams.get("date") || "",
    classCode: u.searchParams.get("classCode") || u.searchParams.get("class") || "SL",
    quotaCode: u.searchParams.get("quotaCode") || u.searchParams.get("quota") || "GN"
  });
  return railRadar(request, env, number => "/v1/trains/" + encodeURIComponent(number) + "/seats?" + params.toString());
}

async function trainCoach(request, env) {
  const u = new URL(request.url);
  const train = String(u.searchParams.get("train") || "").trim();
  const station = String(u.searchParams.get("station") || "").trim().toUpperCase();
  if (!/^\d{4,6}$/.test(train) || !station) return json({ error: "train and station are required" }, 400);
  return railRadar(request, env, number => "/v1/trains/" + encodeURIComponent(number) + "/coaches/" + encodeURIComponent(station));
}

async function trainStationLive(request, env) {
  const u = new URL(request.url);
  const code = String(u.searchParams.get("code") || "").trim().toUpperCase();
  if (!code) return json({ error: "station code required" }, 400);
  const hours = ["2", "4", "6", "8"].includes(u.searchParams.get("hours") || "4") ? (u.searchParams.get("hours") || "4") : "4";
  return railRadar(request, env, () => "/v1/stations/" + encodeURIComponent(code) + "/live?hours=" + hours);
}



async function stationBoard(request, env) {
  const u = new URL(request.url);
  const code = String(u.searchParams.get("code") || "").trim().toUpperCase();
  if (!code) return json({ error: "station code required" }, 400);
  const includeIntermediate = u.searchParams.get("includeIntermediate") === "true";
  return railRadar(request, env, () => "/v1/stations/" + encodeURIComponent(code) + "/trains?includeIntermediate=" + includeIntermediate);
}

async function stationDirectory(request, env, ntes = false) {
  return railRadar(request, env, () => ntes ? "/v1/lookup/stations/ntes" : "/v1/lookup/stations");
}

async function trainDirectory(request, env, variant = "prs") {
  const path = variant === "ntes" ? "/v1/lookup/trains/ntes" : variant === "compressed" ? "/v1/lookup/trains/compressed" : "/v1/lookup/trains/prs";
  return railRadar(request, env, () => path);
}

async function trainFilter(request, env) {
  const u = new URL(request.url);
  const params = new URLSearchParams();
  if (u.searchParams.has("category")) params.set("category", u.searchParams.get("category"));
  if (u.searchParams.has("type")) params.set("type", u.searchParams.get("type"));
  return railRadar(request, env, () => "/v1/lookup/trains/filter?" + params.toString());
}
function buildQuery(url, skip = []) {
  const params = new URLSearchParams();
  for (const [k, v] of url.searchParams.entries()) {
    if (!skip.includes(k)) params.set(k, v);
  }
  const q = params.toString();
  return q ? `?${q}` : "";
}

async function ai(request, env) {
  const body = await readJson(request);
  const prompt = String(body.prompt || "").trim();
  if (!prompt) return json({ error: "prompt required" }, 400);

  if (!env.GEMINI_API_KEY) {
    return json({ configured: false, answer: "AI is not configured. Add GEMINI_API_KEY to the Worker." });
  }

  const model = env.GEMINI_MODEL || "gemini-3.8-flash";
  const interactionResponse = await fetch("https://generativelanguage.googleapis.com/v1beta/interactions", {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      "x-goog-api-key": env.GEMINI_API_KEY,
      "Api-Revision": "2026-05-20"
    },
    body: JSON.stringify({
      model,
      input: prompt,
      generation_config: { temperature: 0.4, max_output_tokens: 700 }
    })
  });

  const interactionData = await interactionResponse.json();
  const outputText = interactionData?.steps?.filter(s => s?.type === "model_output")
    ?.flatMap(s => s?.content || [])
    ?.filter(x => x?.type === "text")
    ?.map(x => x?.text || "").join("").trim();

  if (interactionResponse.ok && outputText) {
    return json({ configured: true, provider: "gemini-interactions", model, interaction_id: interactionData?.id || null, answer: outputText });
  }

  const legacyResponse = await fetch(
    "https://generativelanguage.googleapis.com/v1beta/models/" + encodeURIComponent(model) + ":generateContent?key=" + encodeURIComponent(env.GEMINI_API_KEY),
    {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        systemInstruction: { parts: [{ text: "You are WakeWay, a practical travel assistant. Never invent live train, weather, GPS or ETA data." }] },
        contents: [{ parts: [{ text: prompt }] }],
        generationConfig: { temperature: 0.4, maxOutputTokens: 700 }
      })
    }
  );
  const legacyData = await legacyResponse.json();
  const legacyAnswer = legacyData?.candidates?.[0]?.content?.parts?.map(x => x.text || "").join("").trim();
  if (!legacyResponse.ok) {
    return json({ configured: true, error: legacyData?.error?.message || interactionData?.error?.message || "Gemini request failed" }, legacyResponse.status);
  }
  return json({ configured: true, provider: "gemini-generate-content-fallback", model, answer: legacyAnswer || "AI returned no text." });
}

async function family(request, env, action) {
  const auth = await requireUser(request, env);
  if (auth.error) return json(auth, 401);
  const userId = auth.user.id;

  if (action === "list") {
    const r = await supabaseFetch(
      env,
      `/rest/v1/family_members?user_id=eq.${encodeURIComponent(userId)}&select=family_id,role,joined_at,family_groups(id,name,invite_code,owner_id,created_at)`,
      { method: "GET" },
      null,
      true
    );
    return r.ok ? json(await r.json()) : json({ error: await r.text() }, 500);
  }

  if (action === "create") {
    const body = await readJson(request);
    const inviteCode = crypto.randomUUID().replaceAll("-", "").slice(0, 8).toUpperCase();
    const groupResult = await postgrestJson(
      env,
      "/rest/v1/family_groups",
      { name: String(body.name || "My Family").slice(0, 120), owner_id: userId, invite_code: inviteCode },
      { service: true },
      null
    );
    if (!groupResult.ok) return json({ error: groupResult.data }, groupResult.status);

    const group = Array.isArray(groupResult.data) ? groupResult.data[0] : groupResult.data;
    await postgrestJson(
      env,
      "/rest/v1/family_members",
      { family_id: group.id, user_id: userId, role: "owner" },
      { service: true },
      null
    );

    return json({ family_id: group.id, name: group.name, invite_code: inviteCode }, 201);
  }

  if (action === "join") {
    const body = await readJson(request);
    const code = String(body.invite_code || "").trim().toUpperCase();
    if (!code) return json({ error: "invite_code required" }, 400);

    const groupR = await supabaseFetch(
      env,
      `/rest/v1/family_groups?invite_code=eq.${encodeURIComponent(code)}&select=id,name,owner_id`,
      { method: "GET" },
      null,
      true
    );
    if (!groupR.ok) return json({ error: await groupR.text() }, 500);

    const groups = await groupR.json();
    if (!groups.length) return json({ error: "Invalid invite code" }, 404);

    const r = await postgrestJson(
      env,
      "/rest/v1/family_members",
      { family_id: groups[0].id, user_id: userId, role: "member" },
      { service: true },
      null
    );

    return r.ok ? json({ message: `Joined ${groups[0].name}`, family_id: groups[0].id }) : json({ error: r.data }, 400);
  }

  if (action === "location") {
    const body = await readJson(request);
    const payload = {
      user_id: userId,
      latitude: Number(body.latitude),
      longitude: Number(body.longitude),
      accuracy_m: body.accuracy_m == null ? null : Number(body.accuracy_m),
      updated_at: new Date().toISOString()
    };
    if (!Number.isFinite(payload.latitude) || !Number.isFinite(payload.longitude)) {
      return json({ error: "Valid latitude and longitude are required" }, 400);
    }

    const r = await supabaseFetch(
      env,
      "/rest/v1/family_locations",
      {
        method: "POST",
        headers: { Prefer: "resolution=merge-duplicates,return=minimal" },
        body: JSON.stringify(payload)
      },
      null,
      true
    );
    return r.ok ? json({ ok: true, updated_at: payload.updated_at }) : json({ error: await r.text() }, 500);
  }

  if (action === "locations") {
    const membership = await supabaseFetch(
      env,
      `/rest/v1/family_members?user_id=eq.${encodeURIComponent(userId)}&select=family_id`,
      { method: "GET" },
      null,
      true
    );
    if (!membership.ok) return json({ error: await membership.text() }, 500);

    const memberRows = await membership.json();
    if (!memberRows.length) return json({ locations: [] });

    const familyIds = [...new Set(memberRows.map(x => x.family_id))];
    const members = await supabaseFetch(
      env,
      `/rest/v1/family_members?family_id=in.(${familyIds.map(encodeURIComponent).join(",")})&select=user_id,family_id`,
      { method: "GET" },
      null,
      true
    );
    const memberData = members.ok ? await members.json() : [];
    const ids = [...new Set(memberData.map(x => x.user_id))];
    if (!ids.length) return json({ locations: [] });

    const loc = await supabaseFetch(
      env,
      `/rest/v1/family_locations?user_id=in.(${ids.map(encodeURIComponent).join(",")})&select=user_id,latitude,longitude,accuracy_m,updated_at`,
      { method: "GET" },
      null,
      true
    );
    return loc.ok ? json({ locations: await loc.json() }) : json({ error: await loc.text() }, 500);
  }

  return json({ error: "Unknown family action" }, 404);
}

async function friends(request, env, action) {
  const auth = await requireUser(request, env);
  if (auth.error) return json(auth, 401);
  const userId = auth.user.id;

  if (action === "search") {
    const u = new URL(request.url);
    const q = String(u.searchParams.get("q") || "").trim();
    if (q.length < 2) return json({ results: [] });

    const r = await supabaseFetch(
      env,
      `/rest/v1/profiles?or=(username.ilike.*${encodeURIComponent(q)}*,display_name.ilike.*${encodeURIComponent(q)}*)&id=neq.${encodeURIComponent(userId)}&select=id,username,display_name,avatar_url&limit=10`,
      { method: "GET" },
      null,
      true
    );
    return r.ok ? json({ results: await r.json() }) : json({ error: await r.text() }, 500);
  }

  if (action === "list") {
    const sentR = await supabaseFetch(
      env,
      "/rest/v1/friend_requests?requester_id=eq." + encodeURIComponent(userId) + "&status=eq.accepted&select=id,recipient_id,created_at,status",
      { method: "GET" },
      null,
      true
    );
    const receivedR = await supabaseFetch(
      env,
      "/rest/v1/friend_requests?recipient_id=eq." + encodeURIComponent(userId) + "&status=eq.accepted&select=id,requester_id,created_at,status",
      { method: "GET" },
      null,
      true
    );
    const pendingR = await supabaseFetch(
      env,
      "/rest/v1/friend_requests?recipient_id=eq." + encodeURIComponent(userId) + "&status=eq.pending&select=id,requester_id,created_at,status",
      { method: "GET" },
      null,
      true
    );
    const sent = sentR.ok ? await sentR.json() : [];
    const received = receivedR.ok ? await receivedR.json() : [];
    const pending = pendingR.ok ? await pendingR.json() : [];
    const ids = [...sent.map(x => x.recipient_id), ...received.map(x => x.requester_id), ...pending.map(x => x.requester_id)].filter(Boolean);
    const uniqueIds = [...new Set(ids)];
    const profilesR = uniqueIds.length
      ? await supabaseFetch(env, "/rest/v1/profiles?id=in.(" + uniqueIds.join(",") + ")&select=id,username,display_name,avatar_url", { method: "GET" }, null, true)
      : null;
    const profiles = profilesR?.ok ? await profilesR.json() : [];
    const profileById = new Map(profiles.map(x => [x.id, x]));
    for (const row of sent) row.profile = profileById.get(row.recipient_id) || null;
    for (const row of received) row.profile = profileById.get(row.requester_id) || null;
    for (const row of pending) row.profiles = profileById.get(row.requester_id) || null;
    return json({ friends: { sent, received }, pending });
  }
  if (action === "request") {
    const body = await readJson(request);
    const username = String(body.username || "").trim();
    if (!username) return json({ error: "username required" }, 400);

    const p = await supabaseFetch(
      env,
      `/rest/v1/profiles?username=eq.${encodeURIComponent(username)}&select=id,username,display_name&limit=1`,
      { method: "GET" },
      null,
      true
    );
    const profiles = p.ok ? await p.json() : [];
    if (!profiles.length) return json({ error: "User not found" }, 404);
    const recipientId = profiles[0].id;
    if (recipientId === userId) return json({ error: "You cannot add yourself" }, 400);

    const block = await supabaseFetch(
      env,
      `/rest/v1/blocks?or=(and(blocker_id.eq.${encodeURIComponent(userId)},blocked_id.eq.${encodeURIComponent(recipientId)}),and(blocker_id.eq.${encodeURIComponent(recipientId)},blocked_id.eq.${encodeURIComponent(userId)}))&select=id&limit=1`,
      { method: "GET" },
      null,
      true
    );
    if (block.ok && (await block.json()).length) return json({ error: "Friend request unavailable" }, 403);

    const r = await postgrestJson(
      env,
      "/rest/v1/friend_requests",
      { requester_id: userId, recipient_id: recipientId, status: "pending" },
      { service: true },
      null
    );
    return r.ok ? json({ message: "Friend request sent" }, 201) : json({ error: r.data }, r.status);
  }

  if (action === "accept" || action === "reject") {
    const body = await readJson(request);
    const status = action === "accept" ? "accepted" : "rejected";
    const r = await supabaseFetch(
      env,
      `/rest/v1/friend_requests?id=eq.${encodeURIComponent(body.request_id)}&recipient_id=eq.${encodeURIComponent(userId)}&status=eq.pending`,
      {
        method: "PATCH",
        headers: { Prefer: "return=representation" },
        body: JSON.stringify({ status, responded_at: new Date().toISOString() })
      },
      null,
      true
    );
    return r.ok ? json({ message: status === "accepted" ? "Friend request accepted" : "Friend request rejected" }) : json({ error: await r.text() }, 400);
  }

  if (action === "block") {
    const body = await readJson(request);
    const username = String(body.username || "").trim();
    const p = await supabaseFetch(
      env,
      `/rest/v1/profiles?username=eq.${encodeURIComponent(username)}&select=id&limit=1`,
      { method: "GET" },
      null,
      true
    );
    const profiles = p.ok ? await p.json() : [];
    if (!profiles.length) return json({ error: "User not found" }, 404);

    const blockedId = profiles[0].id;
    const r = await postgrestJson(
      env,
      "/rest/v1/blocks",
      { blocker_id: userId, blocked_id: blockedId },
      { service: true },
      null
    );
    return r.ok ? json({ message: "User blocked" }) : json({ error: r.data }, r.status);
  }

  if (action === "report") {
    const body = await readJson(request);
    const targetUsername = String(body.username || "").trim();
    const p = await supabaseFetch(
      env,
      `/rest/v1/profiles?username=eq.${encodeURIComponent(targetUsername)}&select=id&limit=1`,
      { method: "GET" },
      null,
      true
    );
    const profiles = p.ok ? await p.json() : [];
    if (!profiles.length) return json({ error: "User not found" }, 404);

    const r = await postgrestJson(
      env,
      "/rest/v1/reports",
      {
        reporter_id: userId,
        reported_id: profiles[0].id,
        reason: String(body.reason || "Unspecified").slice(0, 500)
      },
      { service: true },
      null
    );
    return r.ok ? json({ message: "Report submitted" }) : json({ error: r.data }, r.status);
  }

  return json({ error: "Unknown friends action" }, 404);
}

async function getOrCreateConversation(env, userA, userB) {
  const a = await supabaseFetch(
    env,
    `/rest/v1/conversation_members?user_id=eq.${encodeURIComponent(userA)}&select=conversation_id`,
    { method: "GET" },
    null,
    true
  );
  const b = await supabaseFetch(
    env,
    `/rest/v1/conversation_members?user_id=eq.${encodeURIComponent(userB)}&select=conversation_id`,
    { method: "GET" },
    null,
    true
  );
  const aIds = new Set(a.ok ? (await a.json()).map(x => x.conversation_id) : []);
  const bIds = b.ok ? (await b.json()).map(x => x.conversation_id) : [];
  const shared = bIds.find(id => aIds.has(id));
  if (shared) return shared;

  const conv = await postgrestJson(
    env,
    "/rest/v1/conversations",
    { created_by: userA },
    { service: true },
    null
  );
  if (!conv.ok) throw new Error(JSON.stringify(conv.data));

  const conversation = Array.isArray(conv.data) ? conv.data[0] : conv.data;
  await postgrestJson(
    env,
    "/rest/v1/conversation_members",
    [
      { conversation_id: conversation.id, user_id: userA },
      { conversation_id: conversation.id, user_id: userB }
    ],
    { service: true },
    null
  );
  return conversation.id;
}

async function chat(request, env, action) {
  const auth = await requireUser(request, env);
  if (auth.error) return json(auth, 401);

  if (action === "send") {
    const body = await readJson(request);
    const message = String(body.message || "").trim();
    if (!message) return json({ error: "message required" }, 400);

    let conversationId = body.conversation_id || null;
    if (!conversationId && body.to_username) {
      const p = await supabaseFetch(
        env,
        `/rest/v1/profiles?username=eq.${encodeURIComponent(body.to_username)}&select=id&limit=1`,
        { method: "GET" },
        null,
        true
      );
      const profiles = p.ok ? await p.json() : [];
      if (!profiles.length) return json({ error: "Recipient not found" }, 404);
      conversationId = await getOrCreateConversation(env, auth.user.id, profiles[0].id);
    }

    if (!conversationId) return json({ error: "conversation_id or to_username required" }, 400);

    const members = await supabaseFetch(
      env,
      `/rest/v1/conversation_members?conversation_id=eq.${encodeURIComponent(conversationId)}&user_id=eq.${encodeURIComponent(auth.user.id)}&select=conversation_id`,
      { method: "GET" },
      null,
      true
    );
    if (!members.ok || !(await members.json()).length) return json({ error: "Conversation not found" }, 403);

    const r = await postgrestJson(
      env,
      "/rest/v1/chat_messages",
      { conversation_id: conversationId, sender_id: auth.user.id, body: message.slice(0, 4000) },
      { service: true },
      null
    );
    return r.ok ? json({ message: "Sent", conversation_id: conversationId }, 201) : json({ error: r.data }, r.status);
  }

  if (action === "list") {
    const u = new URL(request.url);
    const conversationId = String(u.searchParams.get("conversation_id") || "");
    if (!conversationId) return json({ error: "conversation_id required" }, 400);

    const members = await supabaseFetch(
      env,
      `/rest/v1/conversation_members?conversation_id=eq.${encodeURIComponent(conversationId)}&user_id=eq.${encodeURIComponent(auth.user.id)}&select=conversation_id`,
      { method: "GET" },
      null,
      true
    );
    if (!members.ok || !(await members.json()).length) return json({ error: "Conversation not found" }, 403);

    const r = await supabaseFetch(
      env,
      `/rest/v1/chat_messages?conversation_id=eq.${encodeURIComponent(conversationId)}&select=id,sender_id,body,created_at&order=created_at.asc&limit=200`,
      { method: "GET" },
      null,
      true
    );
    return r.ok ? json({ messages: await r.json() }) : json({ error: await r.text() }, 500);
  }

  return json({ error: "Unknown chat action" }, 404);
}

async function subscription(request, env) {
  const auth = await requireUser(request, env);
  if (auth.error) return json(auth, 401);

  const r = await supabaseFetch(
    env,
    `/rest/v1/subscriptions?user_id=eq.${encodeURIComponent(auth.user.id)}&select=plan,status,expires_at,provider,created_at&order=created_at.desc&limit=1`,
    { method: "GET" },
    null,
    true
  );
  return r.ok
    ? json({ subscription: (await r.json())[0] || { plan: "free", status: "active" } })
    : json({ error: await r.text() }, 500);
}

async function config(env) {
  return json({
    service: "wakeway-api",
    version: "0.2.0",
    providers: {
      supabase: Boolean(env.SUPABASE_URL && env.SUPABASE_PUBLISHABLE_KEY && env.SUPABASE_SERVICE_ROLE_KEY),
      gemini: Boolean(env.GEMINI_API_KEY),
      railradar: Boolean(env.RAILRADAR_API_KEY),
      weather: true,
      geocoding: true
    },
    endpoints: [
      "/health",
      "/api/config",
      "/api/auth/signup",
      "/api/auth/signin",
      "/api/profile",
      "/api/places",
      "/api/place-search",
      "/api/journeys",
      "/api/weather",
      "/api/train",
      "/api/train/stations",
      "/api/train/between",
      "/api/train/route",
      "/api/train/seats",
      "/api/train/coaches",
      "/api/train/station-live",
      "/api/train/station-board",
      "/api/train/stations-directory",
      "/api/train/stations-ntes",
      "/api/train/directory",
      "/api/train/directory-ntes",
      "/api/train/directory-compressed",
      "/api/train/filter",
      "/api/ai",
      "/api/family/*",
      "/api/friends/*",
      "/api/chat/*",
      "/api/subscription"
    ]
  });
}

export default {
  async fetch(request, env) {
    if (request.method === "OPTIONS") {
      return new Response(null, { status: 204, headers: cors });
    }

    try {
      const url = new URL(request.url);
      const p = url.pathname;

      if (p === "/health") {
        return json({
          ok: true,
          service: "wakeway-api",
          version: "0.2.0",
          time: new Date().toISOString()
        }, 200, { "cache-control": "no-store" });
      }

      if (p === "/api/config" && request.method === "GET") return config(env);

      if (p === "/api/auth/signup" && request.method === "POST") return authProxy(request, env, "signup");
      if (p === "/api/auth/signin" && request.method === "POST") return authProxy(request, env, "signin");

      if (p === "/api/profile" && ["GET", "POST"].includes(request.method)) return profile(request, env, request.method);

      if (p === "/api/places" && ["GET", "POST"].includes(request.method)) return places(request, env, request.method);
      if (p.startsWith("/api/places/") && request.method === "DELETE") {
        return places(request, env, request.method, p.split("/").pop());
      }

      if (p === "/api/place-search" && request.method === "GET") return placeSearch(request, env);
      if (p === "/api/weather" && request.method === "GET") return weather(request);

      if (p === "/api/train" && request.method === "GET") return trainLive(request, env);
      if (p === "/api/train/stations" && request.method === "GET") return trainStations(request, env);
      if (p === "/api/train/between" && request.method === "GET") return trainBetween(request, env);
      if (p === "/api/train/route" && request.method === "GET") return trainRoute(request, env);
      if (p === "/api/train/seats" && request.method === "GET") return trainSeats(request, env);
      if (p === "/api/train/coaches" && request.method === "GET") return trainCoach(request, env);
      if (p === "/api/train/station-live" && request.method === "GET") return trainStationLive(request, env);
      if (p === "/api/train/station-board" && request.method === "GET") return stationBoard(request, env);
      if (p === "/api/train/stations-directory" && request.method === "GET") return stationDirectory(request, env, false);
      if (p === "/api/train/stations-ntes" && request.method === "GET") return stationDirectory(request, env, true);
      if (p === "/api/train/directory" && request.method === "GET") return trainDirectory(request, env, "prs");
      if (p === "/api/train/directory-ntes" && request.method === "GET") return trainDirectory(request, env, "ntes");
      if (p === "/api/train/directory-compressed" && request.method === "GET") return trainDirectory(request, env, "compressed");
      if (p === "/api/train/filter" && request.method === "GET") return trainFilter(request, env);

      if (p === "/api/ai" && request.method === "POST") return ai(request, env);

      if (p === "/api/journeys" && ["GET", "POST"].includes(request.method)) {
        return journeys(request, env, request.method);
      }
      if (p.startsWith("/api/journeys/") && request.method === "POST") {
        return journeys(request, env, request.method, p.split("/").pop());
      }

      if (p === "/api/family" || p === "/api/family/list") return family(request, env, "list");
      if (p === "/api/family/create" && request.method === "POST") return family(request, env, "create");
      if (p === "/api/family/join" && request.method === "POST") return family(request, env, "join");
      if (p === "/api/family/location" && request.method === "POST") return family(request, env, "location");
      if (p === "/api/family/locations" && request.method === "GET") return family(request, env, "locations");

      if (p === "/api/friends/search" && request.method === "GET") return friends(request, env, "search");
      if (p === "/api/friends" && request.method === "GET") return friends(request, env, "list");
      if (p === "/api/friends/request" && request.method === "POST") return friends(request, env, "request");
      if (p === "/api/friends/accept" && request.method === "POST") return friends(request, env, "accept");
      if (p === "/api/friends/reject" && request.method === "POST") return friends(request, env, "reject");
      if (p === "/api/friends/block" && request.method === "POST") return friends(request, env, "block");
      if (p === "/api/friends/report" && request.method === "POST") return friends(request, env, "report");

      if (p === "/api/chat/send" && request.method === "POST") return chat(request, env, "send");
      if (p === "/api/chat" && request.method === "GET") return chat(request, env, "list");

      if (p === "/api/subscription" && request.method === "GET") return subscription(request, env);

      return json({ error: "Not found" }, 404);
    } catch (error) {
      return json({
        error: "Server error",
        detail: String(error?.message || error)
      }, 500);
    }
  }
};
