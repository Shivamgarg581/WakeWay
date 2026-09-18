const json = (data, status = 200) =>
  new Response(JSON.stringify(data), {
    status,
    headers: {
      "content-type": "application/json; charset=utf-8",
      "access-control-allow-origin": "*",
      "access-control-allow-headers": "authorization, content-type",
      "access-control-allow-methods": "GET,POST,OPTIONS"
    }
  });

const supabaseHeaders = (env, token) => ({
  "apikey": env.SUPABASE_PUBLISHABLE_KEY || "",
  "Authorization": `Bearer ${token || env.SUPABASE_SERVICE_ROLE_KEY || env.SUPABASE_PUBLISHABLE_KEY || ""}`,
  "Content-Type": "application/json"
});

async function supabaseFetch(env, path, options = {}, token = null) {
  return fetch(`${env.SUPABASE_URL}${path}`, {
    ...options,
    headers: { ...supabaseHeaders(env, token), ...(options.headers || {}) }
  });
}

async function requireUser(request, env) {
  const auth = request.headers.get("authorization") || "";
  const token = auth.replace(/^Bearer\s+/i, "").trim();
  if (!token) return { error: "Missing bearer token" };
  const r = await supabaseFetch(env, "/auth/v1/user", { method: "GET" }, token);
  if (!r.ok) return { error: "Invalid session" };
  return { user: await r.json(), token };
}

async function authProxy(request, env, action) {
  const body = await request.json();
  if (!env.SUPABASE_URL || !env.SUPABASE_PUBLISHABLE_KEY) return json({ error: "Supabase is not configured" }, 503);

  if (action === "signup") {
    const r = await fetch(`${env.SUPABASE_URL}/auth/v1/signup`, {
      method: "POST",
      headers: { "apikey": env.SUPABASE_PUBLISHABLE_KEY, "Content-Type": "application/json" },
      body: JSON.stringify({ email: body.email, password: body.password })
    });
    return json(await r.json(), r.status);
  }

  const r = await fetch(`${env.SUPABASE_URL}/auth/v1/token?grant_type=password`, {
    method: "POST",
    headers: { "apikey": env.SUPABASE_PUBLISHABLE_KEY, "Content-Type": "application/json" },
    body: JSON.stringify({ email: body.email, password: body.password })
  });
  return json(await r.json(), r.status);
}

async function handleFamily(request, env, action) {
  const auth = await requireUser(request, env);
  if (auth.error) return json(auth, 401);
  const body = request.method === "POST" ? await request.json() : {};
  const userId = auth.user.id;

  if (action === "create") {
    const inviteCode = crypto.randomUUID().replaceAll("-", "").slice(0, 8).toUpperCase();
    const r = await supabaseFetch(
      env,
      "/rest/v1/family_groups",
      {
        method: "POST",
        headers: { "Prefer": "return=representation" },
        body: JSON.stringify({ name: body.name || "My Family", owner_id: userId, invite_code: inviteCode })
      },
      env.SUPABASE_SERVICE_ROLE_KEY
    );
    if (!r.ok) return json({ error: await r.text() }, 500);
    const group = (await r.json())[0];
    await supabaseFetch(
      env,
      "/rest/v1/family_members",
      {
        method: "POST",
        headers: { "Prefer": "return=minimal" },
        body: JSON.stringify({ family_id: group.id, user_id: userId, role: "owner" })
      },
      env.SUPABASE_SERVICE_ROLE_KEY
    );
    return json({ family_id: group.id, invite_code: inviteCode });
  }

  if (action === "join") {
    const groupR = await supabaseFetch(
      env,
      `/rest/v1/family_groups?invite_code=eq.${encodeURIComponent(body.invite_code)}&select=id,name`
    );
    if (!groupR.ok) return json({ error: "Unable to find family" }, 404);
    const groups = await groupR.json();
    if (!groups.length) return json({ error: "Invalid invite code" }, 404);
    const group = groups[0];
    const r = await supabaseFetch(
      env,
      "/rest/v1/family_members",
      {
        method: "POST",
        headers: { "Prefer": "return=minimal" },
        body: JSON.stringify({ family_id: group.id, user_id: userId, role: "member" })
      },
      env.SUPABASE_SERVICE_ROLE_KEY
    );
    if (!r.ok) return json({ error: await r.text() }, 400);
    return json({ message: `Joined ${group.name}` });
  }

  if (action === "location") {
    const r = await supabaseFetch(
      env,
      "/rest/v1/family_locations",
      {
        method: "POST",
        headers: { "Prefer": "resolution=merge-duplicates,return=minimal" },
        body: JSON.stringify({
          user_id: userId,
          latitude: body.latitude,
          longitude: body.longitude,
          accuracy_m: body.accuracy_m,
          updated_at: new Date().toISOString()
        })
      },
      env.SUPABASE_SERVICE_ROLE_KEY
    );
    return r.ok ? json({ ok: true }) : json({ error: await r.text() }, 500);
  }

  return json({ error: "Unknown family action" }, 404);
}

async function handleChat(request, env, action) {
  const auth = await requireUser(request, env);
  if (auth.error) return json(auth, 401);
  if (action === "send") {
    const body = await request.json();
    const r = await supabaseFetch(
      env,
      "/rest/v1/chat_messages",
      {
        method: "POST",
        headers: { "Prefer": "return=minimal" },
        body: JSON.stringify({ sender_id: auth.user.id, conversation_id: body.conversation_id, body: body.message })
      },
      env.SUPABASE_SERVICE_ROLE_KEY
    );
    return r.ok ? json({ message: "Sent" }) : json({ error: await r.text() }, 500);
  }
  return json({ error: "Unknown chat action" }, 404);
}

async function weather(request) {
  const u = new URL(request.url);
  const lat = u.searchParams.get("lat");
  const lon = u.searchParams.get("lon");
  if (!lat || !lon) return json({ error: "lat/lon required" }, 400);
  const r = await fetch(
    `https://api.open-meteo.com/v1/forecast?latitude=${encodeURIComponent(lat)}&longitude=${encodeURIComponent(lon)}&current=temperature_2m,relative_humidity_2m,apparent_temperature,is_day,precipitation,rain,weather_code,wind_speed_10m&hourly=temperature_2m,precipitation_probability,weather_code&forecast_days=2`
  );
  return json(await r.json(), r.status);
}

async function placeSearch(request) {
  const u = new URL(request.url);
  const q = u.searchParams.get("q");
  if (!q) return json({ error: "q required" }, 400);
  const r = await fetch(`https://nominatim.openstreetmap.org/search?format=jsonv2&limit=6&q=${encodeURIComponent(q)}`, {
    headers: { "User-Agent": "WakeWay prototype/0.1 (contact: configure-your-support-email)" }
  });
  return json(await r.json(), r.status);
}

async function train(request, env) {
  const u = new URL(request.url);
  const train = u.searchParams.get("train");
  if (!train) return json({ error: "train required" }, 400);
  if (!env.RAILRADAR_API_KEY) {
    return json({
      live: false,
      source: "fallback",
      message: "Rail API key not configured. Phone GPS destination alarm remains available.",
      train_number: train
    });
  }

  const r = await fetch(`https://api.railradar.in/v1/train/${encodeURIComponent(train)}/live`, {
    headers: { "Authorization": `Bearer ${env.RAILRADAR_API_KEY}` }
  });
  return json(await r.json(), r.status);
}

async function ai(request, env) {
  const body = await request.json();
  const prompt = String(body.prompt || "").trim();
  if (!prompt) return json({ error: "prompt required" }, 400);

  if (!env.GEMINI_API_KEY) {
    return json({
      answer: "AI is not configured yet. You can still use WakeWay's core destination alarm without AI."
    });
  }

  const endpoint = `https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=${encodeURIComponent(env.GEMINI_API_KEY)}`;
  const r = await fetch(endpoint, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({
      contents: [{ parts: [{ text: `You are WakeWay, a travel safety assistant. Never invent live location, train, weather, or ETA data. Help with this user request:\n${prompt}` }] }]
    })
  });
  const data = await r.json();
  const answer = data?.candidates?.[0]?.content?.parts?.[0]?.text;
  return json({ answer: answer || "AI response unavailable." }, r.status);
}

export default {
  async fetch(request, env) {
    if (request.method === "OPTIONS") return json({ ok: true });

    try {
      const url = new URL(request.url);
      const p = url.pathname;

      if (p === "/api/auth/signup" && request.method === "POST") return authProxy(request, env, "signup");
      if (p === "/api/auth/signin" && request.method === "POST") return authProxy(request, env, "signin");

      if (p === "/api/weather") return weather(request);
      if (p === "/api/place-search") return placeSearch(request);
      if (p === "/api/train") return train(request, env);
      if (p === "/api/ai" && request.method === "POST") return ai(request, env);

      if (p === "/api/family/create" && request.method === "POST") return handleFamily(request, env, "create");
      if (p === "/api/family/join" && request.method === "POST") return handleFamily(request, env, "join");
      if (p === "/api/family/location" && request.method === "POST") return handleFamily(request, env, "location");

      if (p === "/api/chat/send" && request.method === "POST") return handleChat(request, env, "send");

      if (p === "/health") return json({ ok: true, service: "wakeway-api" });

      return json({ error: "Not found" }, 404);
    } catch (e) {
      return json({ error: "Server error", detail: String(e) }, 500);
    }
  }
};
