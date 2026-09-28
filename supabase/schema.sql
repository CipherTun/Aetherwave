-- Run once in Supabase: SQL Editor > New query > paste > Run.
create table if not exists user_data (
  user_id uuid primary key references auth.users on delete cascade,
  data jsonb not null default '{}',
  updated_at timestamptz default now()
);
alter table user_data enable row level security;
create policy "own row" on user_data for all using (auth.uid() = user_id) with check (auth.uid() = user_id);

create table if not exists shared_playlists (
  id uuid primary key default gen_random_uuid(),
  owner uuid default auth.uid() references auth.users on delete set null,
  name text not null,
  tracks jsonb not null,
  created_at timestamptz default now()
);
alter table shared_playlists enable row level security;
create policy "anyone can read" on shared_playlists for select using (true);
create policy "signed-in can share" on shared_playlists for insert to authenticated with check (auth.uid() = owner);
