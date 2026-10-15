-- FINAL DATABASE SCHEMA FOR DBP CHECKIN APP
-- This file contains all tables, functions, policies, and triggers required for the app.
-- Generated on: 2025-12-27

-- ==========================================
-- 1. TABLES
-- ==========================================

-- 1.1 Groups Table
CREATE TABLE IF NOT EXISTS public.groups (
  id uuid NOT NULL DEFAULT gen_random_uuid(),
  created_at timestamptz NOT NULL DEFAULT now(),
  name text NOT NULL,
  latitude double precision NOT NULL,
  longitude double precision NOT NULL,
  radius double precision DEFAULT 100.0,
  start_time text NULL,
  end_time text NULL,
  CONSTRAINT groups_pkey PRIMARY KEY (id)
);

-- 1.2 Profiles Table (Linked to Firebase Auth via ID)
CREATE TABLE IF NOT EXISTS public.profiles (
  id text NOT NULL, -- Firebase UID
  created_at timestamptz NOT NULL DEFAULT now(),
  name text NULL,
  email text NULL,
  phone text NULL,
  tehsil text NULL,
  image_url text NULL,
  role text NOT NULL DEFAULT 'employee'::text,
  group_id uuid NULL,
  requested_group_id uuid NULL,
  request_status text DEFAULT 'pending', -- pending, approved, rejected
  CONSTRAINT profiles_pkey PRIMARY KEY (id),
  CONSTRAINT profiles_group_id_fkey FOREIGN KEY (group_id) REFERENCES public.groups(id) ON DELETE SET NULL
);

-- 1.3 Attendance Table
CREATE TABLE IF NOT EXISTS public.attendance (
  id uuid NOT NULL DEFAULT gen_random_uuid(),
  created_at timestamptz NOT NULL DEFAULT now(),
  user_id text NOT NULL,
  name text NULL,
  tehsil text NULL,
  image_url text NULL,
  status text DEFAULT 'present',
  location text NULL,
  latitude double precision NULL,
  longitude double precision NULL,
  CONSTRAINT attendance_pkey PRIMARY KEY (id),
  CONSTRAINT attendance_user_id_fkey FOREIGN KEY (user_id) REFERENCES public.profiles(id) ON DELETE CASCADE
);

-- ==========================================
-- 2. STORAGE BUCKETS
-- ==========================================

INSERT INTO storage.buckets (id, name, public)
VALUES ('profile-pictures', 'profile-pictures', true)
ON CONFLICT (id) DO NOTHING;

INSERT INTO storage.buckets (id, name, public)
VALUES ('attendance-proofs', 'attendance-proofs', true)
ON CONFLICT (id) DO NOTHING;

-- Storage Policies (Open access for simplicity with Firebase)
DROP POLICY IF EXISTS "Public Access" ON storage.objects;
CREATE POLICY "Public Access" ON storage.objects FOR ALL USING (true) WITH CHECK (true);

-- ==========================================
-- 3. ROW LEVEL SECURITY (RLS)
-- ==========================================

-- Disable RLS for simplicity as we are using Firebase Auth and handling logic in app/functions
-- Ideally, you would use Supabase Auth or custom claims, but for this setup:
ALTER TABLE public.groups DISABLE ROW LEVEL SECURITY;
ALTER TABLE public.profiles DISABLE ROW LEVEL SECURITY;
ALTER TABLE public.attendance DISABLE ROW LEVEL SECURITY;

-- However, we keep this policy for the "Admin Update" logic if RLS is ever enabled
DROP POLICY IF EXISTS "Admins can update any profile" ON public.profiles;
CREATE POLICY "Admins can update any profile"
ON public.profiles
FOR UPDATE
USING (
  (SELECT role FROM public.profiles WHERE id::text = auth.uid()::text) = 'admin'
);

-- ==========================================
-- 4. FUNCTIONS (RPC)
-- ==========================================

-- 4.1 Assign User to Group (Admin) - Handles Text/UUID casting
CREATE OR REPLACE FUNCTION assign_user_to_group(user_id text, new_group_id text)
RETURNS void
LANGUAGE plpgsql
SECURITY DEFINER
AS $$
BEGIN
  UPDATE public.profiles
  SET group_id = new_group_id::uuid
  WHERE id = user_id;
END;
$$;

-- 4.2 Approve Group Request
CREATE OR REPLACE FUNCTION approve_group_request_v2(p_user_id text, p_group_id text)
RETURNS void
LANGUAGE plpgsql
SECURITY DEFINER
AS $$
BEGIN
  UPDATE public.profiles
  SET
    group_id = p_group_id::uuid,
    requested_group_id = NULL,
    request_status = 'approved'
  WHERE id = p_user_id;
END;
$$;

-- 4.3 Reject Group Request / Remove Member
CREATE OR REPLACE FUNCTION reject_group_request(p_user_id text)
RETURNS void
LANGUAGE plpgsql
SECURITY DEFINER
AS $$
BEGIN
  UPDATE public.profiles
  SET
    group_id = NULL,
    requested_group_id = NULL,
    request_status = 'rejected'
  WHERE id = p_user_id;
END;
$$;

-- 4.4 Admin Remove User (Alternative)
CREATE OR REPLACE FUNCTION admin_remove_user(p_user_id text)
RETURNS void
LANGUAGE plpgsql
SECURITY DEFINER
AS $$
BEGIN
  UPDATE public.profiles
  SET group_id = NULL
  WHERE id = p_user_id;
END;
$$;

-- ==========================================
-- 5. TRIGGERS & MAINTENANCE
-- ==========================================

-- 5.1 Auto-delete old attendance (Keep last 3 months)
CREATE INDEX IF NOT EXISTS idx_attendance_created_at ON public.attendance(created_at);

CREATE OR REPLACE FUNCTION delete_old_attendance()
RETURNS TRIGGER AS $$
BEGIN
  -- Delete records older than 3 months
  DELETE FROM public.attendance
  WHERE created_at < (now() - INTERVAL '3 months');
  RETURN NULL;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trigger_delete_old_attendance ON public.attendance;

CREATE TRIGGER trigger_delete_old_attendance
AFTER INSERT ON public.attendance
FOR EACH STATEMENT
EXECUTE FUNCTION delete_old_attendance();

