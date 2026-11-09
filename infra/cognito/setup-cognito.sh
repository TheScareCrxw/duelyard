#!/usr/bin/env bash
# Creates the Duelyard Cognito user pool, domain and web app client, then writes local env files.
#
# Usage:  infra/cognito/setup-cognito.sh [stage]        (stage defaults to "dev")
# Env:    AWS_REGION (default us-east-1), APP_URL (default http://localhost:3000)
#
# Requires AWS credentials allowed to manage Cognito (cognito-idp:*). Safe to re-run: it reuses a
# pool that already has the expected name instead of creating a duplicate.
set -euo pipefail

STAGE="${1:-dev}"
REGION="${AWS_REGION:-us-east-1}"
APP_URL="${APP_URL:-http://localhost:3000}"
POOL_NAME="duelyard-${STAGE}"
CLIENT_NAME="duelyard-web-${STAGE}"
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"

AWS="aws"
command -v aws >/dev/null 2>&1 || AWS="/c/Program Files/Amazon/AWSCLIV2/aws.exe"
aws_() { "$AWS" --region "$REGION" --output text "$@"; }

echo "Account: $(aws_ sts get-caller-identity --query Account)  Region: $REGION  Stage: $STAGE"

# --- 1. User pool -----------------------------------------------------------------------------
POOL_ID="$(aws_ cognito-idp list-user-pools --max-results 60 \
  --query "UserPools[?Name=='${POOL_NAME}'].Id | [0]")"
if [[ -z "$POOL_ID" || "$POOL_ID" == "None" ]]; then
  echo "Creating user pool ${POOL_NAME}..."
  POOL_ID="$(aws_ cognito-idp create-user-pool \
    --pool-name "$POOL_NAME" \
    --user-pool-tier ESSENTIALS \
    --deletion-protection ACTIVE \
    --alias-attributes email \
    --auto-verified-attributes email \
    --username-configuration CaseSensitive=false \
    --schema 'Name=email,AttributeDataType=String,Required=true,Mutable=true' \
    --policies 'PasswordPolicy={MinimumLength=8,RequireUppercase=true,RequireLowercase=true,RequireNumbers=true,RequireSymbols=true,TemporaryPasswordValidityDays=7}' \
    --account-recovery-setting 'RecoveryMechanisms=[{Priority=1,Name=verified_email}]' \
    --admin-create-user-config AllowAdminCreateUserOnly=false \
    --verification-message-template '{"DefaultEmailOption":"CONFIRM_WITH_CODE","EmailSubject":"Your Duelyard verification code","EmailMessage":"Your Duelyard verification code is {####}"}' \
    --query UserPool.Id)"
else
  echo "Reusing user pool ${POOL_NAME} (${POOL_ID})"
fi

# --- 2. Domain (classic hosted UI; used for Google sign-in, token refresh, revoke, logout) --------
DOMAIN_PREFIX="$(aws_ cognito-idp describe-user-pool --user-pool-id "$POOL_ID" --query UserPool.Domain)"
if [[ -z "$DOMAIN_PREFIX" || "$DOMAIN_PREFIX" == "None" ]]; then
  DOMAIN_PREFIX="duelyard-${STAGE}-$(printf '%04x%04x' "$RANDOM" "$RANDOM")"
  echo "Creating domain ${DOMAIN_PREFIX}..."
  aws_ cognito-idp create-user-pool-domain --user-pool-id "$POOL_ID" --domain "$DOMAIN_PREFIX" \
    --managed-login-version 1 >/dev/null
fi
COGNITO_DOMAIN="https://${DOMAIN_PREFIX}.auth.${REGION}.amazoncognito.com"

# --- 3. App client ------------------------------------------------------------------------------
IDPS="COGNITO"
if aws_ cognito-idp describe-identity-provider --user-pool-id "$POOL_ID" --provider-name Google >/dev/null 2>&1; then
  IDPS="COGNITO Google"
fi

CLIENT_ARGS=(
  --explicit-auth-flows ALLOW_USER_PASSWORD_AUTH ALLOW_REFRESH_TOKEN_AUTH
  --prevent-user-existence-errors ENABLED
  --enable-token-revocation
  --access-token-validity 60 --id-token-validity 60 --refresh-token-validity 30
  --token-validity-units AccessToken=minutes,IdToken=minutes,RefreshToken=days
  --supported-identity-providers $IDPS
  --callback-urls "${APP_URL}/auth/callback"
  --logout-urls "${APP_URL}/"
  --allowed-o-auth-flows code
  --allowed-o-auth-scopes openid email profile aws.cognito.signin.user.admin
  --allowed-o-auth-flows-user-pool-client
)

CLIENT_ID="$(aws_ cognito-idp list-user-pool-clients --user-pool-id "$POOL_ID" --max-results 60 \
  --query "UserPoolClients[?ClientName=='${CLIENT_NAME}'].ClientId | [0]")"
if [[ -z "$CLIENT_ID" || "$CLIENT_ID" == "None" ]]; then
  echo "Creating app client ${CLIENT_NAME}..."
  CLIENT_ID="$(aws_ cognito-idp create-user-pool-client --user-pool-id "$POOL_ID" \
    --client-name "$CLIENT_NAME" --generate-secret "${CLIENT_ARGS[@]}" --query UserPoolClient.ClientId)"
else
  echo "Updating app client ${CLIENT_NAME} (${CLIENT_ID})..."
  aws_ cognito-idp update-user-pool-client --user-pool-id "$POOL_ID" --client-id "$CLIENT_ID" \
    --client-name "$CLIENT_NAME" "${CLIENT_ARGS[@]}" >/dev/null
fi
CLIENT_SECRET="$(aws_ cognito-idp describe-user-pool-client --user-pool-id "$POOL_ID" --client-id "$CLIENT_ID" \
  --query UserPoolClient.ClientSecret)"

# --- 4. Local env files (gitignored) --------------------------------------------------------------
if [[ "$STAGE" == "dev" ]]; then
  umask 077
  cat > "$ROOT/frontend/.env.local" <<EOF
API_BASE_URL=http://localhost:8080
APP_URL=${APP_URL}
COGNITO_REGION=${REGION}
COGNITO_CLIENT_ID=${CLIENT_ID}
COGNITO_CLIENT_SECRET=${CLIENT_SECRET}
COGNITO_DOMAIN=${COGNITO_DOMAIN}
EOF
  cat > "$ROOT/backend/.env" <<EOF
COGNITO_REGION=${REGION}
COGNITO_USER_POOL_ID=${POOL_ID}
COGNITO_CLIENT_IDS=${CLIENT_ID}
EOF
  echo "Wrote frontend/.env.local and backend/.env"
fi

echo
echo "User pool:      ${POOL_ID}"
echo "App client:     ${CLIENT_ID}"
echo "Cognito domain: ${COGNITO_DOMAIN}"
echo "Identity providers on client: ${IDPS}"
echo
echo "Google OAuth client settings (Google Cloud Console > APIs & Services > Credentials):"
echo "  Authorized JavaScript origin: ${COGNITO_DOMAIN}"
echo "  Authorized redirect URI:      ${COGNITO_DOMAIN}/oauth2/idpresponse"
