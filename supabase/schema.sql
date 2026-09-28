-- Aetherwave Supabase schema.
-- Run once in Supabase SQL Editor. Guest playback/search works without this schema.

create table if not exists user_data (
  user_id uuid primary key references auth.users on delete cascade,
  data jsonb not null default '{}',
  updated_at timestamptz default now()
);
alter table user_data enable row level security;
drop policy if exists "own row" on user_data;
create policy "own row" on user_data for all using (auth.uid() = user_id) with check (auth.uid() = user_id);

create table if not exists shared_playlists (
  id uuid primary key default gen_random_uuid(),
  owner uuid default auth.uid() references auth.users on delete set null,
  name text not null,
  tracks jsonb not null,
  created_at timestamptz default now()
);
alter table shared_playlists enable row level security;
drop policy if exists "anyone can read" on shared_playlists;
drop policy if exists "signed-in can share" on shared_playlists;
create policy "anyone can read" on shared_playlists for select using (true);
create policy "signed-in can share" on shared_playlists for insert to authenticated with check (auth.uid() = owner);

create table if not exists profiles (
  id uuid primary key references auth.users on delete cascade,
  username text unique,
  display_name text,
  bio text,
  avatar_url text,
  created_at timestamptz default now()
);
alter table profiles enable row level security;
create policy "profiles readable" on profiles for select using (true);
create policy "own profile insert" on profiles for insert to authenticated with check (auth.uid() = id);
create policy "own profile update" on profiles for update to authenticated using (auth.uid() = id) with check (auth.uid() = id);

create table if not exists user_follows (
  follower_id uuid references auth.users on delete cascade,
  following_id uuid references auth.users on delete cascade,
  created_at timestamptz default now(),
  primary key (follower_id, following_id),
  check (follower_id <> following_id)
);
alter table user_follows enable row level security;
create policy "follows readable" on user_follows for select using (true);
create policy "follow own" on user_follows for insert to authenticated with check (auth.uid() = follower_id);
create policy "unfollow own" on user_follows for delete to authenticated using (auth.uid() = follower_id);

create table if not exists track_comments (
  id bigint generated always as identity primary key,
  track_id text not null,
  user_id uuid references auth.users on delete cascade not null,
  body text not null check (char_length(body) between 1 and 2000),
  created_at timestamptz default now()
);
alter table track_comments enable row level security;
create policy "comments readable" on track_comments for select using (true);
create policy "own comment insert" on track_comments for insert to authenticated with check (auth.uid() = user_id);
create policy "own comment delete" on track_comments for delete to authenticated using (auth.uid() = user_id);

create table if not exists notifications (
  id bigint generated always as identity primary key,
  user_id uuid references auth.users on delete cascade not null,
  type text not null,
  payload jsonb not null default '{}',
  read boolean not null default false,
  created_at timestamptz default now()
);
alter table notifications enable row level security;
create policy "own notifications" on notifications for select using (auth.uid() = user_id);
create policy "own notifications update" on notifications for update using (auth.uid() = user_id) with check (auth.uid() = user_id);

create table if not exists creator_releases (
  id bigint generated always as identity primary key,
  owner_id uuid references auth.users on delete cascade not null,
  title text not null,
  description text not null default '',
  audio_path text not null,
  created_at timestamptz default now()
);
alter table creator_releases enable row level security;
create policy "releases readable" on creator_releases for select using (true);
create policy "own release insert" on creator_releases for insert to authenticated with check (auth.uid() = owner_id);
create policy "own release update" on creator_releases for update to authenticated using (auth.uid() = owner_id) with check (auth.uid() = owner_id);
create policy "own release delete" on creator_releases for delete to authenticated using (auth.uid() = owner_id);

-- Storage bucket for creator uploads.
insert into storage.buckets (id, name, public)
values ('creator-audio', 'creator-audio', true)
on conflict (id) do nothing;

create policy "creator audio public read" on storage.objects for select using (bucket_id = 'creator-audio');
create policy "creator audio own upload" on storage.objects for insert to authenticated with check (bucket_id = 'creator-audio' and (storage.foldername(name))[1] = auth.uid()::text);
create policy "creator audio own update" on storage.objects for update to authenticated using (bucket_id = 'creator-audio' and (storage.foldername(name))[1] = auth.uid()::text);
create policy "creator audio own delete" on storage.objects for delete to authenticated using (bucket_id = 'creator-audio' and (storage.foldername(name))[1] = auth.uid()::text);
