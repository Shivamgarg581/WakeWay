-- WakeWay cloud schema.
-- Run in Supabase SQL Editor.
-- RLS is enabled. Service-role calls from the Worker bypass RLS.
-- Do not expose SUPABASE_SERVICE_ROLE_KEY to Android.

create extension if not exists pgcrypto;

create table if not exists public.profiles (
  id uuid primary key references auth.users(id) on delete cascade,
  username text unique,
  display_name text,
  avatar_url text,
  created_at timestamptz not null default now()
);

create table if not exists public.saved_places (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null references auth.users(id) on delete cascade,
  name text not null,
  address text,
  latitude double precision not null,
  longitude double precision not null,
  created_at timestamptz not null default now()
);

create table if not exists public.journeys (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null references auth.users(id) on delete cascade,
  destination_name text not null,
  destination_address text,
  destination_lat double precision not null,
  destination_lon double precision not null,
  transport_mode text not null,
  started_at timestamptz not null default now(),
  ended_at timestamptz,
  status text not null default 'active'
);

create table if not exists public.family_groups (
  id uuid primary key default gen_random_uuid(),
  name text not null,
  owner_id uuid not null references auth.users(id) on delete cascade,
  invite_code text unique not null,
  created_at timestamptz not null default now()
);

create table if not exists public.family_members (
  family_id uuid not null references public.family_groups(id) on delete cascade,
  user_id uuid not null references auth.users(id) on delete cascade,
  role text not null default 'member',
  joined_at timestamptz not null default now(),
  primary key (family_id, user_id)
);

create table if not exists public.family_locations (
  user_id uuid primary key references auth.users(id) on delete cascade,
  latitude double precision not null,
  longitude double precision not null,
  accuracy_m double precision,
  updated_at timestamptz not null default now()
);

create table if not exists public.chat_messages (
  id uuid primary key default gen_random_uuid(),
  conversation_id uuid not null,
  sender_id uuid not null references auth.users(id) on delete cascade,
  body text not null,
  created_at timestamptz not null default now()
);

create table if not exists public.subscriptions (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null references auth.users(id) on delete cascade,
  plan text not null default 'free',
  status text not null default 'active',
  expires_at timestamptz,
  provider text,
  provider_subscription_id text,
  created_at timestamptz not null default now()
);

alter table public.profiles enable row level security;
alter table public.saved_places enable row level security;
alter table public.journeys enable row level security;
alter table public.family_groups enable row level security;
alter table public.family_members enable row level security;
alter table public.family_locations enable row level security;
alter table public.chat_messages enable row level security;
alter table public.subscriptions enable row level security;

create policy "profiles own read" on public.profiles for select using (auth.uid() = id);
create policy "profiles own update" on public.profiles for update using (auth.uid() = id);
create policy "profiles own insert" on public.profiles for insert with check (auth.uid() = id);

create policy "saved places own all" on public.saved_places for all using (auth.uid() = user_id) with check (auth.uid() = user_id);
create policy "journeys own all" on public.journeys for all using (auth.uid() = user_id) with check (auth.uid() = user_id);

create policy "family member read" on public.family_members for select using (auth.uid() = user_id);
create policy "family group owner read" on public.family_groups for select using (auth.uid() = owner_id);

create policy "subscription own read" on public.subscriptions for select using (auth.uid() = user_id);

-- Family and chat writes are intentionally brokered through the Worker,
-- which validates sessions server-side. Add fine-grained Realtime policies
-- before enabling direct client subscriptions in production.

create index if not exists family_locations_updated_idx on public.family_locations(updated_at);
create index if not exists chat_messages_conversation_idx on public.chat_messages(conversation_id, created_at);
