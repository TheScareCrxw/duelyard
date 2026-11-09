#!/usr/bin/env bash
# Adds (or updates) Google as a federated identity provider on the Duelyard user pool, then
# re-runs setup-cognito.sh so the app client allows it.
#
# Usage:  infra/cognito/add-google.sh [stage]
# Reads GOOGLE_CLIENT_ID and GOOGLE_CLIENT_SECRET from the environment, or from
# infra/cognito/.env.google (gitignored), so the secret never has to be typed on a command line.
# Env:    AWS_REGION (default us-east-1)
set -euo pipefail

STAGE="${1:-dev}"
SECRETS_FILE="$(dirname "$0")/.env.google"
if [[ -f "$SECRETS_FILE" ]]; then
  # shellcheck disable=SC1090
  set -a; source <(tr -d '\r' < "$SECRETS_FILE"); set +a
fi
REGION="${AWS_REGION:-us-east-1}"
: "${GOOGLE_CLIENT_ID:?Set GOOGLE_CLIENT_ID}"
: "${GOOGLE_CLIENT_SECRET:?Set GOOGLE_CLIENT_SECRET}"

AWS="aws"
command -v aws >/dev/null 2>&1 || AWS="/c/Program Files/Amazon/AWSCLIV2/aws.exe"
aws_() { "$AWS" --region "$REGION" --output text "$@"; }

POOL_ID="$(aws_ cognito-idp list-user-pools --max-results 60 \
  --query "UserPools[?Name=='duelyard-${STAGE}'].Id | [0]")"
[[ -n "$POOL_ID" && "$POOL_ID" != "None" ]] || { echo "User pool duelyard-${STAGE} not found; run setup-cognito.sh first" >&2; exit 1; }

DETAILS="client_id=${GOOGLE_CLIENT_ID},client_secret=${GOOGLE_CLIENT_SECRET},authorize_scopes=openid email profile"
MAPPING="email=email,email_verified=email_verified,name=name,username=sub"

if aws_ cognito-idp describe-identity-provider --user-pool-id "$POOL_ID" --provider-name Google >/dev/null 2>&1; then
  echo "Updating Google identity provider..."
  aws_ cognito-idp update-identity-provider --user-pool-id "$POOL_ID" --provider-name Google \
    --provider-details "$DETAILS" --attribute-mapping "$MAPPING" >/dev/null
else
  echo "Creating Google identity provider..."
  aws_ cognito-idp create-identity-provider --user-pool-id "$POOL_ID" --provider-name Google \
    --provider-type Google --provider-details "$DETAILS" --attribute-mapping "$MAPPING" >/dev/null
fi

"$(dirname "$0")/setup-cognito.sh" "$STAGE"
