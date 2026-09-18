-- WakeWay production-oriented Supabase schema.
-- Run this in Supabase SQL Editor for a new project, or migrate existing
-- deployments with the equivalent ALTER/CREATE statements.
-- The Worker uses the service-role key server-side. Never ship that key in Android.

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
  id uuid primary key,
  user_id uuid not null references auth.users(id) on delete cascade,
  destination_name text not null,
  destination_address text,
  destination_lat double precision not null,
  destination_lon double precision not null,
  transport_mode text not null,
  started_at timestamptz not null default now(),
  ended_at timestamptz,
  status text not null default 'active',
  updated_at timestamptz not null default now()
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

create table if not exists public.friend_requests (
  id uuid primary key default gen_random_uuid(),
  requester_id uuid not null references auth.users(id) on delete cascade,
  recipient_id uuid not null references auth.users(id) on delete cascade,
  status text not null default 'pending' check (status in ('pending','accepted','rejected','cancelled')),
  created_at timestamptz not null default now(),
  responded_at timestamptz,
  check (requester_id <> recipient_id),
  unique (requester_id, recipient_id)
);

create table if not exists public.blocks (
  id uuid primary key default gen_random_uuid(),
  blocker_id uuid not null references auth.users(id) on delete cascade,
  blocked_id uuid not null references auth.users(id) on delete cascade,
  created_at timestamptz not null default now(),
  check (blocker_id <> blocked_id),
  unique (blocker_id, blocked_id)
);

create table if not exists public.reports (
  id uuid primary key default gen_random_uuid(),
  reporter_id uuid not null references auth.users(id) on delete cascade,
  reported_id uuid not null references auth.users(id) on delete cascade,
  reason text not null,
  created_at timestamptz not null default now()
);

create table if not exists public.conversations (
  id uuid primary key default gen_random_uuid(),
  created_by uuid not null references auth.users(id) on delete cascade,
  created_at timestamptz not null default now()
);

create table if not exists public.conversation_members (
  conversation_id uuid not null references public.conversations(id) on delete cascade,
  user_id uuid not null references auth.users(id) on delete cascade,
  joined_at timestamptz not null default now(),
  primary key (conversation_id, user_id)
);

create table if not exists public.chat_messages (
  id uuid primary key default gen_random_uuid(),
  conversation_id uuid not null references public.conversations(id) on delete cascade,
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

create index if not exists saved_places_user_idx on public.saved_places(user_id, created_at desc);
create index if not exists journeys_user_idx on public.journeys(user_id, started_at desc);
create index if not exists family_members_user_idx on public.family_members(user_id);
create index if not exists family_locations_updated_idx on public.family_locations(updated_at);
create index if not exists friend_requests_recipient_idx on public.friend_requests(recipient_id, status);
create index if not exists friend_requests_requester_idx on public.friend_requests(requester_id, status);
create index if not exists blocks_blocker_idx on public.blocks(blocker_id);
create index if not exists conversation_members_user_idx on public.conversation_members(user_id);
create index if not exists chat_messages_conversation_idx on public.chat_messages(conversation_id, created_at);

alter table public.profiles enable row level security;
alter table public.saved_places enable row level security;
alter table public.journeys enable row level security;
alter table public.family_groups enable row level security;
alter table public.family_members enable row level security;
alter table public.family_locations enable row level security;
alter table public.friend_requests enable row level security;
alter table public.blocks enable row level security;
alter table public.reports enable row level security;
alter table public.conversations enable row level security;
alter table public.conversation_members enable row level security;
alter table public.chat_messages enable row level security;
alter table public.subscriptions enable row level security;

drop policy if exists "profiles own read" on public.profiles;
create policy "profiles own read" on public.profiles
  for select using (auth.uid() = id);

drop policy if exists "profiles own insert" on public.profiles;
create policy "profiles own insert" on public.profiles
  for insert with check (auth.uid() = id);

drop policy if exists "profiles own update" on public.profiles;
create policy "profiles own update" on public.profiles
  for update using (auth.uid() = id) with check (auth.uid() = id);

drop policy if exists "saved places own all" on public.saved_places;
create policy "saved places own all" on public.saved_places
  for all using (auth.uid() = user_id) with check (auth.uid() = user_id);

drop policy if exists "journeys own all" on public.journeys;
create policy "journeys own all" on public.journeys
  for all using (auth.uid() = user_id) with check (auth.uid() = user_id);

drop policy if exists "family membership own read" on public.family_members;
create policy "family membership own read" on public.family_members
  for select using (auth.uid() = user_id);

drop policy if exists "family group owner read" on public.family_groups;
create policy "family group owner read" on public.family_groups
  for select using (auth.uid() = owner_id);

drop policy if exists "friend requests own read" on public.friend_requests;
create policy "friend requests own read" on public.friend_requests
  for select using (auth.uid() = requester_id or auth.uid() = recipient_id);

drop policy if exists "chat member read" on public.conversation_members;
create policy "chat member read" on public.conversation_members
  for select using (auth.uid() = user_id);

drop policy if exists "chat messages member read" on public.chat_messages;
create policy "chat messages member read" on public.chat_messages
  for select using (
    exists (
      select 1 from public.conversation_members cm
      where cm.conversation_id = chat_messages.conversation_id
        and cm.user_id = auth.uid()
    )
  );

drop policy if exists "subscription own read" on public.subscriptions;
create policy "subscription own read" on public.subscriptions
  for select using (auth.uid() = user_id);

-- The Worker brokers writes involving relationships, family locations and chat.
-- Add direct-client realtime policies only after the final sharing/privacy model
-- has been reviewed.
