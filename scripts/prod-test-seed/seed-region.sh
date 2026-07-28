#!/usr/bin/env bash
# Builds one full example season (league -> tier -> group -> 3 placed teams -> confirmed
# rosters) for each of the 5 seeded regions, by calling the REAL REST API as that region's
# just-bootstrapped region-admin account — not raw SQL. Run 00-bootstrap.sql first.
#
# Auth: ROPC (password grant) against the confidential `dtfb-api` Keycloak client, which
# already has directAccessGrantsEnabled + a provisioned secret — no Keycloak config changes
# needed. The resulting token authenticates as the real region-admin user, indistinguishable
# from what the SPA would get.
#
# Requires: curl, jq. Set env vars before running (see README.md):
#   KEYCLOAK_URL              e.g. https://sh-id-test.dtfb.de
#   API_BASE_PATH             e.g. https://sh-api-test.dtfb.de
#   DTFB_API_CLIENT_SECRET    the `dtfb-api` client's secret (dtfb-keycloak's .env, written
#                             there by scripts/setup-keycloak.mts)
#   REGION_ADMIN_PASSWORD     defaults to "region", matching 00-bootstrap.sql's accounts
#
# Usage: ./seed-region.sh              # seeds all 5 regions
#        ./seed-region.sh fed-tfvhh    # seeds just one (re-run/debugging a single region)

set -euo pipefail

: "${KEYCLOAK_URL:?KEYCLOAK_URL required, e.g. https://sh-id-test.dtfb.de}"
: "${API_BASE_PATH:?API_BASE_PATH required, e.g. https://sh-api-test.dtfb.de}"
: "${DTFB_API_CLIENT_SECRET:?DTFB_API_CLIENT_SECRET required (dtfb-api client secret)}"
REGION_ADMIN_PASSWORD="${REGION_ADMIN_PASSWORD:-region}"
KEYCLOAK_REALM="${KEYCLOAK_REALM:-dtfb}"

# Parallel arrays — must match 00-bootstrap.sql exactly.
FEDERATION_IDS=(fed-tfvhh fed-mtfv fed-nwtfv fed-stfv fed-tfvb)
FEDERATION_LABELS=("Hamburg (TFVHH)" "Mitteldeutschland (MTFV)" "NRW (NWTFV)" "Saarland (STFV)" "Berlin (TFVB)")
REGION_ADMIN_USERNAMES=(poppen goerlich engelhardt meyer fleischanderl)
TEAM1_IDS=(team-tfvhh-1 team-mtfv-1 team-nwtfv-1 team-stfv-1 team-tfvb-1)
TEAM2_IDS=(team-tfvhh-2 team-mtfv-2 team-nwtfv-2 team-stfv-2 team-tfvb-2)
TEAM3_IDS=(team-tfvhh-3 team-mtfv-3 team-nwtfv-3 team-stfv-3 team-tfvb-3)

# Shared filler-player pool (see 00-bootstrap.sql) — same 9 players reused across all 5
# regions' rosters; harmless for demo data, no uniqueness constraint prevents it.
TEAM1_ROSTER=(player-f1 player-f2 player-f3)
TEAM2_ROSTER=(player-f4 player-f5 player-f6)
TEAM3_ROSTER=(player-f7 player-f8 player-f9)

ONLY_FEDERATION="${1:-}"

login() {
  local username="$1"
  curl -sf -X POST "${KEYCLOAK_URL}/realms/${KEYCLOAK_REALM}/protocol/openid-connect/token" \
    -d "client_id=dtfb-api" -d "client_secret=${DTFB_API_CLIENT_SECRET}" \
    -d "grant_type=password" -d "username=${username}" -d "password=${REGION_ADMIN_PASSWORD}" \
    -d "scope=openid" | jq -r '.access_token'
}

# api <method> <path> <json-body> <token> — prints the response body; fails the script on
# a non-2xx (curl -f), so a broken step stops the run instead of silently cascading.
api() {
  local method="$1" path="$2" body="$3" token="$4"
  curl -sf -X "${method}" "${API_BASE_PATH}${path}" \
    -H "Authorization: Bearer ${token}" -H "Content-Type: application/json" \
    -d "${body}"
}

seed_one_region() {
  local i="$1"
  local fed_id="${FEDERATION_IDS[$i]}"
  local label="${FEDERATION_LABELS[$i]}"
  local admin_user="${REGION_ADMIN_USERNAMES[$i]}"
  local team1="${TEAM1_IDS[$i]}" team2="${TEAM2_IDS[$i]}" team3="${TEAM3_IDS[$i]}"

  echo "== ${label} (${fed_id}) — logging in as ${admin_user} =="
  local token
  token="$(login "${admin_user}")"
  if [ -z "${token}" ] || [ "${token}" = "null" ]; then
    echo "!! Login failed for ${admin_user} — check REGION_ADMIN_PASSWORD / that 00-bootstrap.sql ran / that the Keycloak user exists with a non-temporary password." >&2
    return 1
  fi

  echo "   creating season..."
  local season_id
  season_id="$(api POST /v1/seasons "$(jq -n --arg fed "${fed_id}" '{
    name: "Test-Saison 2026/27", federationId: $fed,
    startDate: "2026-09-01", endDate: "2027-05-31", registrationOpensAt: "2026-06-01"
  }')" "${token}" | jq -r '.id')"

  echo "   creating league..."
  local league_id
  league_id="$(api POST /v1/leagues "$(jq -n --arg season "${season_id}" '{
    name: "Herren", seasonId: $season, categoryId: "cat-herren", ruleSetId: "rs-dtfb-std"
  }')" "${token}" | jq -r '.id')"

  echo "   creating tier..."
  local tier_id
  tier_id="$(api POST /v1/tiers "$(jq -n --arg league "${league_id}" '{
    name: "1. Liga", leagueId: $league, level: 1
  }')" "${token}" | jq -r '.id')"

  echo "   creating group..."
  local group_id
  group_id="$(api POST /v1/groups "$(jq -n --arg tier "${tier_id}" '{
    name: "Gruppe A", tierId: $tier, groupState: "RUNNING"
  }')" "${token}" | jq -r '.id')"

  echo "   placing teams + building rosters..."
  local teams=("${team1}" "${team2}" "${team3}")
  local rosters_json=("$(printf '%s\n' "${TEAM1_ROSTER[@]}" | jq -R . | jq -s .)"
                      "$(printf '%s\n' "${TEAM2_ROSTER[@]}" | jq -R . | jq -s .)"
                      "$(printf '%s\n' "${TEAM3_ROSTER[@]}" | jq -R . | jq -s .)")

  local t
  for t in 0 1 2; do
    local team_id="${teams[$t]}"
    local participation_id
    participation_id="$(api POST /v1/team-participations "$(jq -n --arg team "${team_id}" --arg league "${league_id}" --arg group "${group_id}" '{
      teamId: $team, leagueId: $league, groupId: $group
    }')" "${token}" | jq -r '.id')"

    local player_id
    for player_id in $(echo "${rosters_json[$t]}" | jq -r '.[]'); do
      api POST "/v1/team-participations/${participation_id}/roster" "$(jq -n --arg p "${player_id}" '{playerId: $p}')" "${token}" > /dev/null
    done

    api POST "/v1/team-participations/${participation_id}/roster/submit" "{}" "${token}" > /dev/null
    api POST "/v1/team-participations/${participation_id}/roster/confirm" "{}" "${token}" > /dev/null
    echo "     ${team_id} placed, roster confirmed"
  done

  echo "== ${label} done =="
}

for i in "${!FEDERATION_IDS[@]}"; do
  if [ -n "${ONLY_FEDERATION}" ] && [ "${ONLY_FEDERATION}" != "${FEDERATION_IDS[$i]}" ]; then
    continue
  fi
  seed_one_region "${i}"
done

echo "All done."
