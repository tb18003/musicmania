# Deploying the friends leaderboard (Supabase + Cloud Run)

Everything runs from **Google Cloud Shell** (a browser terminal with `gcloud`, Docker and Node
preinstalled), so nothing has to be installed locally. Allow ~30 minutes the first time.

```
App ──Spotify token──► Cloud Run: musicmania-api ──TLS (session pooler, IPv4)──► Supabase Postgres
```

> Why the session pooler: on the Supabase free plan the direct database host is IPv6-only and
> Cloud Run egress is IPv4. The session pooler (port 5432) is IPv4 and keeps prepared statements.

## 0. What you need

- The Supabase project (Data API disabled, as recommended in `database/README.md`).
- A Google account with a billing account (a card is required; at this scale usage stays in the
  free tier, and the setup adds a budget alert).

## 1. Open Cloud Shell and clone the repository

Open <https://shell.cloud.google.com> and run:

```bash
git clone https://github.com/tb18003/musicmania.git
cd musicmania
```

## 2. Apply the database schema (Supabase CLI v3)

1. Create a Supabase access token at <https://supabase.com/dashboard/account/tokens>.
2. In Cloud Shell:
   ```bash
   cd database
   npx --yes supabase@3.1.0 login                       # paste the access token
   npx --yes supabase@3.1.0 link --project-ref <ref>    # <ref>: Project Settings -> General; asks the DB password
   npx --yes supabase@3.1.0 db push --remote            # shows the migration and asks for confirmation
   cd ..
   ```

## 3. Give the API role a password

Generate a strong password and keep it in your password manager:

```bash
openssl rand -base64 48 | tr -dc 'A-Za-z0-9' | head -c 40; echo
```

In the Supabase dashboard -> **SQL editor**:

```sql
alter role musicmania_api with password 'the-generated-password';
```

## 4. Collect the connection details

- **Pooler host:** dashboard -> **Connect** -> *Session pooler*, e.g. `aws-1-eu-west-3.pooler.supabase.com`.
- **CA certificate:** *Project Settings* -> *Database* -> *SSL configuration* -> **Download certificate**,
  then upload it to Cloud Shell (⋮ menu -> *Upload*). It lands in your home directory.

## 5. Create the Google Cloud project

```bash
PROJECT_ID="musicmania-api-$RANDOM"
gcloud projects create "$PROJECT_ID"
gcloud billing accounts list                                  # copy the ACCOUNT_ID
gcloud billing projects link "$PROJECT_ID" --billing-account=XXXXXX-XXXXXX-XXXXXX
```

## 6. Run the setup script

```bash
export PROJECT_ID BILLING_ACCOUNT=XXXXXX-XXXXXX-XXXXXX
export SUPABASE_PROJECT_REF=<ref>
export SUPABASE_POOLER_HOST=aws-1-eu-west-3.pooler.supabase.com
export SUPABASE_CA_CERT=~/prod-ca-2021.crt                     # the file you uploaded
bash api/deploy/setup-gcp.sh
```

It asks for the role password from step 3 (typed input is hidden and goes straight to Secret
Manager) and then:

- enables the needed APIs, creates an Artifact Registry repository and two service accounts
  (runtime: can only read the two secrets; deployer: can only push images and deploy);
- stores the password and the CA certificate in **Secret Manager**;
- creates a **budget alert** (default 1, in your billing currency, emails at 50/90/100 %);
- configures **Workload Identity Federation** so GitHub Actions can deploy without keys, and only
  from jobs in the `production` environment of this repository;
- builds the image, deploys `musicmania-api` to **Cloud Run in Madrid** (scale to zero,
  `max-instances=1`, 512 MiB) and calls `/health`.

It prints the service URL and the values for step 8.

## 7. Let yourself in and try it

1. Build the app with the URL in `mobile/local.properties`:
   ```properties
   MUSICMANIA_API_URL=https://musicmania-api-xxxxxxxx.europe-southwest1.run.app
   ```
2. Open **Settings -> Friends ranking** and copy your Spotify ID (or open the Ranking tab, which shows it).
3. Add yourself (and up to four friends) in the Supabase **SQL editor**:
   ```sql
   insert into musicmania.allowlist (spotify_user_id, email, display_name)
   values ('your_spotify_id', 'you@example.com', 'You');
   ```
4. In the app, turn on **Share my stats**: your stats are uploaded and you appear in the ranking.

## 8. Continuous deployment from GitHub

1. *Settings -> Environments -> New environment* `production`; add yourself as **required reviewer**
   and limit deployment branches/tags to `api-v*` tags.
2. *Settings -> Secrets and variables -> Actions -> Variables*: add `GCP_PROJECT_ID`, `GCP_REGION`,
   `GCP_WIF_PROVIDER` and `GCP_DEPLOYER_SA` with the values printed by the script (they are not secrets).
3. Deploy a new version by tagging a commit on `master`/`develop`:
   ```bash
   git tag api-v0.1.1 && git push origin api-v0.1.1
   ```
   or run *Actions -> Deploy API -> Run workflow*. You approve each run.

## Operations

| Task | Command (Cloud Shell) |
|---|---|
| Logs | `gcloud run services logs read musicmania-api --region=europe-southwest1 --limit=50` |
| Roll back | `gcloud run revisions list --service=musicmania-api --region=europe-southwest1`, then `gcloud run services update-traffic musicmania-api --region=europe-southwest1 --to-revisions=<revision>=100` |
| Rotate the DB password | `alter role ...` in Supabase, then `printf '%s' 'new' \| gcloud secrets versions add musicmania-db-password --data-file=-` and redeploy |
| Revoke a user | `update musicmania.allowlist set enabled = false where email = '...';` |
| New migration | `npx --yes supabase@3.1.0 db push --remote` from `database/` |

**Costs:** Cloud Run scales to zero and `max-instances=1` caps it; a handful of users stays within
the free tier. The free Supabase project pauses after 7 days without activity; the hourly sync
keeps it awake, and while paused the API answers 503 and the app keeps working locally.
