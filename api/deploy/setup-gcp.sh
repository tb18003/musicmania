#!/usr/bin/env bash
# One-time Google Cloud setup and first deployment of the MusicMania API.
# Run it in Google Cloud Shell from a clone of the repository (see docs/setup/cloud-deploy.md).
# It is idempotent: running it again only creates what is missing and redeploys.
#
# Required environment variables:
#   PROJECT_ID              Google Cloud project id (must exist and have billing linked)
#   SUPABASE_PROJECT_REF    e.g. abcdefghijklmnopqrst (Supabase -> Project Settings -> General)
#   SUPABASE_POOLER_HOST    session pooler host, e.g. aws-1-eu-west-3.pooler.supabase.com
#   SUPABASE_CA_CERT        path to the Supabase CA certificate downloaded from the dashboard
# Optional:
#   BILLING_ACCOUNT         billing account id (XXXXXX-XXXXXX-XXXXXX) to create a budget alert
#   BUDGET_AMOUNT           alert threshold in the billing account currency (default 1)
#   REGION                  default europe-southwest1 (Madrid)
#   GITHUB_REPO             default tb18003/musicmania
set -euo pipefail

: "${PROJECT_ID:?Set PROJECT_ID}"
: "${SUPABASE_PROJECT_REF:?Set SUPABASE_PROJECT_REF}"
: "${SUPABASE_POOLER_HOST:?Set SUPABASE_POOLER_HOST}"
: "${SUPABASE_CA_CERT:?Set SUPABASE_CA_CERT (path to the downloaded certificate)}"
REGION="${REGION:-europe-southwest1}"
GITHUB_REPO="${GITHUB_REPO:-tb18003/musicmania}"
BUDGET_AMOUNT="${BUDGET_AMOUNT:-1}"

SERVICE="musicmania-api"
REPOSITORY="musicmania"
RUNTIME_SA="musicmania-api-runtime@${PROJECT_ID}.iam.gserviceaccount.com"
DEPLOYER_SA="musicmania-deployer@${PROJECT_ID}.iam.gserviceaccount.com"
DB_PASSWORD_SECRET="musicmania-db-password"
CA_SECRET="supabase-ca-cert"
IMAGE="${REGION}-docker.pkg.dev/${PROJECT_ID}/${REPOSITORY}/${SERVICE}"
WIF_POOL="github"
WIF_PROVIDER="github-oidc"

step() { printf '\n==> %s\n' "$*"; }

[[ -f "${SUPABASE_CA_CERT}" ]] || { echo "Certificate not found: ${SUPABASE_CA_CERT}" >&2; exit 1; }
[[ -f api/Dockerfile ]] || { echo "Run this from the repository root" >&2; exit 1; }

gcloud config set project "${PROJECT_ID}" >/dev/null
PROJECT_NUMBER="$(gcloud projects describe "${PROJECT_ID}" --format='value(projectNumber)')"

step "Enabling APIs"
gcloud services enable run.googleapis.com artifactregistry.googleapis.com secretmanager.googleapis.com \
  iam.googleapis.com iamcredentials.googleapis.com sts.googleapis.com billingbudgets.googleapis.com

step "Artifact Registry repository"
gcloud artifacts repositories describe "${REPOSITORY}" --location="${REGION}" >/dev/null 2>&1 ||
  gcloud artifacts repositories create "${REPOSITORY}" --repository-format=docker --location="${REGION}" \
    --description="MusicMania API images"

step "Service accounts"
for sa in musicmania-api-runtime musicmania-deployer; do
  gcloud iam service-accounts describe "${sa}@${PROJECT_ID}.iam.gserviceaccount.com" >/dev/null 2>&1 ||
    gcloud iam service-accounts create "${sa}" --display-name="${sa}"
done

step "Secrets (values never printed)"
if ! gcloud secrets describe "${DB_PASSWORD_SECRET}" >/dev/null 2>&1; then
  read -r -s -p "Password of the musicmania_api database role: " DB_PASSWORD; echo
  [[ -n "${DB_PASSWORD}" ]] || { echo "Empty password" >&2; exit 1; }
  printf '%s' "${DB_PASSWORD}" | gcloud secrets create "${DB_PASSWORD_SECRET}" --replication-policy=automatic --data-file=-
  unset DB_PASSWORD
fi
gcloud secrets describe "${CA_SECRET}" >/dev/null 2>&1 ||
  gcloud secrets create "${CA_SECRET}" --replication-policy=automatic --data-file="${SUPABASE_CA_CERT}"
for secret in "${DB_PASSWORD_SECRET}" "${CA_SECRET}"; do
  gcloud secrets add-iam-policy-binding "${secret}" --member="serviceAccount:${RUNTIME_SA}" \
    --role=roles/secretmanager.secretAccessor >/dev/null
done

step "Deployer permissions (least privilege)"
gcloud projects add-iam-policy-binding "${PROJECT_ID}" --member="serviceAccount:${DEPLOYER_SA}" \
  --role=roles/run.developer --condition=None >/dev/null
gcloud iam service-accounts add-iam-policy-binding "${RUNTIME_SA}" --member="serviceAccount:${DEPLOYER_SA}" \
  --role=roles/iam.serviceAccountUser >/dev/null
gcloud artifacts repositories add-iam-policy-binding "${REPOSITORY}" --location="${REGION}" \
  --member="serviceAccount:${DEPLOYER_SA}" --role=roles/artifactregistry.writer >/dev/null

step "Workload Identity Federation for GitHub Actions (no JSON keys)"
gcloud iam workload-identity-pools describe "${WIF_POOL}" --location=global >/dev/null 2>&1 ||
  gcloud iam workload-identity-pools create "${WIF_POOL}" --location=global --display-name="GitHub Actions"
gcloud iam workload-identity-pools providers describe "${WIF_PROVIDER}" --location=global \
  --workload-identity-pool="${WIF_POOL}" >/dev/null 2>&1 ||
  gcloud iam workload-identity-pools providers create-oidc "${WIF_PROVIDER}" --location=global \
    --workload-identity-pool="${WIF_POOL}" --issuer-uri="https://token.actions.githubusercontent.com" \
    --attribute-mapping="google.subject=assertion.sub,attribute.repository=assertion.repository,attribute.environment=assertion.environment" \
    --attribute-condition="assertion.repository == '${GITHUB_REPO}' && assertion.environment == 'production'"
gcloud iam service-accounts add-iam-policy-binding "${DEPLOYER_SA}" --role=roles/iam.workloadIdentityUser \
  --member="principalSet://iam.googleapis.com/projects/${PROJECT_NUMBER}/locations/global/workloadIdentityPools/${WIF_POOL}/attribute.repository/${GITHUB_REPO}" \
  >/dev/null

if [[ -n "${BILLING_ACCOUNT:-}" ]]; then
  step "Budget alert (${BUDGET_AMOUNT} in the billing account currency)"
  if ! gcloud billing budgets list --billing-account="${BILLING_ACCOUNT}" --format='value(displayName)' | grep -qx "${SERVICE}"; then
    gcloud billing budgets create --billing-account="${BILLING_ACCOUNT}" --display-name="${SERVICE}" \
      --budget-amount="${BUDGET_AMOUNT}" --filter-projects="projects/${PROJECT_ID}" \
      --threshold-rule=percent=0.5 --threshold-rule=percent=0.9 --threshold-rule=percent=1.0
  fi
else
  echo "BILLING_ACCOUNT not set: skipping the budget alert (strongly recommended)."
fi

step "Building and pushing the image (Docker in Cloud Shell)"
TAG="${IMAGE}:initial-$(date +%Y%m%d%H%M%S)"
gcloud auth configure-docker "${REGION}-docker.pkg.dev" --quiet
docker build --tag "${TAG}" api
docker push "${TAG}"

step "Deploying to Cloud Run"
DATABASE_URL="jdbc:postgresql://${SUPABASE_POOLER_HOST}:5432/postgres?sslmode=verify-full&sslrootcert=/secrets/supabase/ca.crt"
gcloud run deploy "${SERVICE}" --image="${TAG}" --region="${REGION}" --service-account="${RUNTIME_SA}" \
  --allow-unauthenticated --min-instances=0 --max-instances=1 --cpu=1 --memory=512Mi --concurrency=40 \
  --cpu-boost --timeout=30s \
  --set-env-vars="^@^DATABASE_URL=${DATABASE_URL}@DATABASE_USER=musicmania_api.${SUPABASE_PROJECT_REF}" \
  --set-secrets="DATABASE_PASSWORD=${DB_PASSWORD_SECRET}:latest,/secrets/supabase/ca.crt=${CA_SECRET}:latest"

URL="$(gcloud run services describe "${SERVICE}" --region="${REGION}" --format='value(status.url)')"
step "Smoke test"
curl -fsS "${URL}/health" && echo

cat <<EOF

Done. Next steps (see docs/setup/cloud-deploy.md):
  * App: add to mobile/local.properties
      MUSICMANIA_API_URL=${URL}
  * GitHub -> Settings -> Environments: create "production" (required reviewer: you).
    Then Settings -> Secrets and variables -> Actions -> Variables:
      GCP_PROJECT_ID     = ${PROJECT_ID}
      GCP_REGION         = ${REGION}
      GCP_WIF_PROVIDER   = projects/${PROJECT_NUMBER}/locations/global/workloadIdentityPools/${WIF_POOL}/providers/${WIF_PROVIDER}
      GCP_DEPLOYER_SA    = ${DEPLOYER_SA}
EOF
