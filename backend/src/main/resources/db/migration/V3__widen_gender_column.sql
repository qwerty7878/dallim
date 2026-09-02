-- V1's users.gender was VARCHAR(16), but the Android client's Gender enum
-- (app/onboarding/profile/ProfileOptions.kt) sends "PREFER_NOT_TO_SAY" for its "선택 안함"
-- option, which is 17 characters -- one over the limit. Every signup that picked that option
-- failed POST /users/me/profile with a DB-level "Value exceeds length" error (found via live
-- end-to-end signup testing 2026-09-03). Widen to 32 to match the other free-form profile
-- columns (avatar_id, running_experience, comfortable_pace) instead of hand-fitting one value.
ALTER TABLE users ALTER COLUMN gender TYPE VARCHAR(32);
