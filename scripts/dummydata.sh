#!/usr/bin/env bash
#
# dummydata.sh — dummydatas the Wallet API with dummy data by calling the real endpoints.
# This drives data through actual business logic (password hashing, ledger
# entries, idempotency) instead of inserting rows directly into Postgres,
# so the resulting data is exactly as valid as anything a real user creates.
#
# USAGE:
#   ./dummydata.sh
#
# CONFIG:
#   Set BASE_URL below (or export it before running) to point at local vs EKS.

set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:8080}"

# ── Matches AuthController / WalletController exactly ────────────────────
REGISTER_PATH="/api/auth/register"
LOGIN_PATH="/api/auth/login"
CREATE_WALLET_PATH="/api/v1/wallets"
DEPOSIT_PATH="/api/v1/wallets/deposit"
WITHDRAW_PATH="/api/v1/wallets/withdraw"
TRANSFER_PATH="/api/v1/wallets/transfer"
# GET_WALLET_PATH and TRANSACTIONS_PATH are built per-wallet-id below
# ───────────────────────────────────────────────────────────────────────

# Colors for readable output
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m'

log()  { echo -e "${GREEN}[dummydata]${NC} $1"; }
warn() { echo -e "${YELLOW}[dummydata]${NC} $1"; }
err()  { echo -e "${RED}[dummydata]${NC} $1"; }

require_jq() {
  if ! command -v jq &> /dev/null; then
    err "jq is required (used to parse JWT/response JSON). Install with: sudo apt install jq -y"
    exit 1
  fi
}
require_jq

# ── Dummy users to create ─────────────────────────────────────────────────
# email | password | fullName
USERS=(
  "alice@example.com|Passw0rd!23|Alice Wanjiru"
  "bob@example.com|Passw0rd!23|Bob Otieno"
  "carol@example.com|Passw0rd!23|Carol Njeri"
)

declare -A TOKENS
declare -A WALLET_IDS

register_and_login() {
  local email="$1" password="$2" fullName="$3"

  log "Registering $email ..."
  register_status=$(curl -s -o /tmp/dummydata_register_resp.json -w "%{http_code}" -X POST "$BASE_URL$REGISTER_PATH" \
    -H "Content-Type: application/json" \
    -d "{\"email\":\"$email\",\"password\":\"$password\",\"fullName\":\"$fullName\"}")

  if [[ "$register_status" == "201" || "$register_status" == "200" ]]; then
    log "  -> registered ($register_status)"
  elif [[ "$register_status" == "409" || "$register_status" == "400" ]]; then
    warn "  -> already exists ($register_status), continuing to login"
  else
    err "  -> unexpected status $register_status for $email:"
    cat /tmp/dummydata_register_resp.json
  fi

  log "Logging in $email ..."
  login_json=$(curl -s -X POST "$BASE_URL$LOGIN_PATH" \
    -H "Content-Type: application/json" \
    -d "{\"email\":\"$email\",\"password\":\"$password\"}")

  # Response shape: { "success": true, "message": "...", "data": { "token": "...", "tokenType": "...", "userId": "...", "email": "..." } }
  token=$(echo "$login_json" | jq -r '.data.token // empty')

  if [[ -z "$token" ]]; then
    err "  -> could not extract token from login response:"
    echo "$login_json" | jq '.' || echo "$login_json"
    exit 1
  fi

  log "  -> got token"
  TOKENS["$email"]="$token"
}

create_wallet() {
  local email="$1" ownerName="$2"
  local token="${TOKENS[$email]}"

  log "Creating wallet for $ownerName ($email) ..."
  wallet_json=$(curl -s -X POST "$BASE_URL$CREATE_WALLET_PATH" \
    -H "Authorization: Bearer $token" \
    -H "Content-Type: application/json" \
    -d "{\"ownerName\":\"$ownerName\"}")

  wallet_id=$(echo "$wallet_json" | jq -r '.data.id // empty')

  if [[ -z "$wallet_id" ]]; then
    err "  -> could not extract wallet id from response:"
    echo "$wallet_json" | jq '.' || echo "$wallet_json"
    exit 1
  fi

  log "  -> wallet id: $wallet_id"
  WALLET_IDS["$email"]="$wallet_id"
}

deposit() {
  local email="$1" amount="$2" description="$3"
  local token="${TOKENS[$email]}"
  local wallet_id="${WALLET_IDS[$email]}"

  log "Depositing $amount into $email's wallet ($wallet_id) ..."
  curl -s -X POST "$BASE_URL$DEPOSIT_PATH" \
    -H "Authorization: Bearer $token" \
    -H "Content-Type: application/json" \
    -d "{\"walletId\":\"$wallet_id\",\"amount\":$amount,\"description\":\"$description\"}" | jq '.'
}

withdraw() {
  local email="$1" amount="$2" description="$3"
  local token="${TOKENS[$email]}"
  local wallet_id="${WALLET_IDS[$email]}"

  log "Withdrawing $amount from $email's wallet ($wallet_id) ..."
  curl -s -X POST "$BASE_URL$WITHDRAW_PATH" \
    -H "Authorization: Bearer $token" \
    -H "Content-Type: application/json" \
    -d "{\"walletId\":\"$wallet_id\",\"amount\":$amount,\"description\":\"$description\"}" | jq '.'
}

transfer() {
  local from_email="$1" to_email="$2" amount="$3" description="$4"
  local token="${TOKENS[$from_email]}"
  local from_wallet_id="${WALLET_IDS[$from_email]}"
  local to_wallet_id="${WALLET_IDS[$to_email]}"

  log "Transferring $amount from $from_email's wallet to $to_email's wallet ..."
  curl -s -X POST "$BASE_URL$TRANSFER_PATH" \
    -H "Authorization: Bearer $token" \
    -H "Content-Type: application/json" \
    -d "{\"fromWalletId\":\"$from_wallet_id\",\"toWalletId\":\"$to_wallet_id\",\"amount\":$amount,\"description\":\"$description\"}" | jq '.'
}

check_balance() {
  local email="$1"
  local token="${TOKENS[$email]}"
  local wallet_id="${WALLET_IDS[$email]}"
  log "Wallet state for $email:"
  curl -s -X GET "$BASE_URL/api/v1/wallets/$wallet_id" \
    -H "Authorization: Bearer $token" | jq '.'
}

# ── Run the dummydata sequence ─────────────────────────────────────────────────
log "dummydataing against $BASE_URL"
log "----------------------------------------"

for entry in "${USERS[@]}"; do
  IFS='|' read -r email password fullName <<< "$entry"
  register_and_login "$email" "$password" "$fullName"
  create_wallet "$email" "$fullName"
done

log "----------------------------------------"
log "Users and wallets ready. Creating transaction history ..."

deposit "alice@example.com" 5000 "Initial deposit"
deposit "bob@example.com" 3000 "Initial deposit"
deposit "carol@example.com" 1000 "Initial deposit"

withdraw "alice@example.com" 500 "ATM withdrawal"

transfer "alice@example.com" "bob@example.com" 1200 "Rent split"
transfer "bob@example.com" "carol@example.com" 700 "Dinner payback"

log "----------------------------------------"
log "Final balances:"
check_balance "alice@example.com"
check_balance "bob@example.com"
check_balance "carol@example.com"

log "----------------------------------------"
log "Done. JWTs above can be pasted straight into Postman for further manual testing."
