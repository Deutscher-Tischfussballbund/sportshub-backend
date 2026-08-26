#!/usr/bin/env bash
# Builds one full example season (league -> tier -> group -> 3 placed teams -> confirmed
# rosters) for each of the 5 seeded regions, by calling the REAL REST API as that region's
# just-bootstrapped region-admin account — not raw SQL. Run 00-bootstrap.sql first. When run
# for all regions (no argument), also builds one root-level demo league ("Bundesliga", under
# fed-dtfb) as the global admin, so the root-federation feature (docs/16-root-federation.md) is
# actually exercisable on the deployment, not just present in the nav.
#
# Auth: ROPC (password grant) against the confidential `dtfb-api` Keycloak client, which
# already has directAccessGrantsEnabled + a provisioned secret — no Keycloak config changes
# needed. The resulting token authenticates as the real region-admin/global-admin user,
# indistinguishable from what the SPA would get.
#
# Requires: curl, jq. Set env vars before running (see README.md):
#   KEYCLOAK_URL              e.g. https://sh-id-test.dtfb.de
#   API_BASE_PATH             e.g. https://sh-api-test.dtfb.de
#   DTFB_API_CLIENT_SECRET    the `dtfb-api` client's secret (dtfb-keycloak's .env, written
#                             there by scripts/setup-keycloak.mts)
#   REGION_ADMIN_PASSWORD     defaults to "region", matching 00-bootstrap.sql's accounts
#   GLOBAL_ADMIN_PASSWORD     defaults to "admin", matching 00-bootstrap.sql's two admin accounts
#
# Usage: ./seed-region.sh              # seeds all 5 regions + the root Bundesliga demo
#        ./seed-region.sh fed-tfvhh    # seeds just one region (re-run/debugging), skips Bundesliga

set -euo pipefail

: "${KEYCLOAK_URL:?KEYCLOAK_URL required, e.g. https://sh-id-test.dtfb.de}"
: "${API_BASE_PATH:?API_BASE_PATH required, e.g. https://sh-api-test.dtfb.de}"
: "${DTFB_API_CLIENT_SECRET:?DTFB_API_CLIENT_SECRET required (dtfb-api client secret)}"
REGION_ADMIN_PASSWORD="${REGION_ADMIN_PASSWORD:-region}"
GLOBAL_ADMIN_PASSWORD="${GLOBAL_ADMIN_PASSWORD:-admin}"
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

# Prints the response body to stderr and returns 1 on a non-2xx, instead of curl -f's silent
# empty-stdout failure -- combined with set -e/pipefail, a silent failure here used to kill the
# whole script the instant a login/request failed, before ever reaching this script's own
# diagnostics (e.g. login()'s "Login failed" check never ran) -- hit live during the v0.3.0 VPS
# rollout with no indication of what actually went wrong.
http_request() {
  local method="$1" url="$2"
  shift 2
  local response http_code body
  response="$(curl -s -w '\n%{http_code}' -X "${method}" "${url}" "$@")"
  http_code="${response##*$'\n'}"
  body="${response%$'\n'*}"
  if [ "${http_code#2}" = "${http_code}" ]; then
    echo "!! ${method} ${url} failed (HTTP ${http_code}): ${body}" >&2
    return 1
  fi
  echo "${body}"
}

login() {
  local username="$1" password="${2:-${REGION_ADMIN_PASSWORD}}"
  http_request POST "${KEYCLOAK_URL}/realms/${KEYCLOAK_REALM}/protocol/openid-connect/token" \
    -d "client_id=dtfb-api" -d "client_secret=${DTFB_API_CLIENT_SECRET}" \
    -d "grant_type=password" -d "username=${username}" -d "password=${password}" \
    -d "scope=openid" | jq -r '.access_token'
}

# api <method> <path> <json-body> <token> — prints the response body on success.
api() {
  local method="$1" path="$2" body="$3" token="$4"
  http_request "${method}" "${API_BASE_PATH}${path}" \
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

  # RosterService#addPlayer now requires the player to already be an active member of the
  # team's club (see ClubMembershipService) -- every filler player used below belongs to this
  # region's one club (team1/2/3 all share it), so join them all to it once, up front. Idempotent
  # (POST .../members is a no-op if already a member), so safe to call every run.
  local club_id="club-${fed_id#fed-}"
  echo "   joining filler players to ${club_id}..."
  local filler_player
  for filler_player in "${TEAM1_ROSTER[@]}" "${TEAM2_ROSTER[@]}" "${TEAM3_ROSTER[@]}"; do
    api POST "/v1/admin/clubs/${club_id}/members" "$(jq -n --arg p "${filler_player}" '{playerId: $p}')" "${token}" > /dev/null
  done

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

# Root-level demo league under fed-dtfb (docs/16-root-federation.md), logged in as the global
# admin (not a region admin — root-level leagues are DTFB's own, not any one Landesverband's).
# Places one team from two different regions/clubs (team-tfvhh-1, team-mtfv-1) to demonstrate
# cross-federation placement under a root league; each club fields only one team here, which is
# exactly the "one team per club per root-level league" rule (TeamParticipationService
# #requireSingleRootLeagueTeamPerClub) in its simplest, always-satisfied form.
seed_root_league() {
  echo "== Bundesliga (root, fed-dtfb) — logging in as flock =="
  local token
  token="$(login flock "${GLOBAL_ADMIN_PASSWORD}")"
  if [ -z "${token}" ] || [ "${token}" = "null" ]; then
    echo "!! Login failed for flock — check GLOBAL_ADMIN_PASSWORD / that 00-bootstrap.sql ran." >&2
    return 1
  fi

  echo "   creating season..."
  local season_id
  season_id="$(api POST /v1/seasons "$(jq -n '{
    name: "Bundesliga 2026/27", federationId: "fed-dtfb",
    startDate: "2026-09-01", endDate: "2027-05-31", registrationOpensAt: "2026-06-01"
  }')" "${token}" | jq -r '.id')"

  echo "   creating league..."
  local league_id
  league_id="$(api POST /v1/leagues "$(jq -n --arg season "${season_id}" '{
    name: "Bundesliga", seasonId: $season, categoryId: "cat-herren", ruleSetId: "rs-dtfb-std"
  }')" "${token}" | jq -r '.id')"

  echo "   creating tier..."
  local tier_id
  tier_id="$(api POST /v1/tiers "$(jq -n --arg league "${league_id}" '{
    name: "1. Liga", leagueId: $league, level: 1
  }')" "${token}" | jq -r '.id')"

  echo "   creating group..."
  local group_id
  group_id="$(api POST /v1/groups "$(jq -n --arg tier "${tier_id}" '{
    name: "Gesamt", tierId: $tier, groupState: "RUNNING"
  }')" "${token}" | jq -r '.id')"

  echo "   placing teams..."
  local team_id
  for team_id in team-tfvhh-1 team-mtfv-1; do
    api POST /v1/team-participations "$(jq -n --arg team "${team_id}" --arg league "${league_id}" --arg group "${group_id}" '{
      teamId: $team, leagueId: $league, groupId: $group
    }')" "${token}" > /dev/null
    echo "     ${team_id} placed"
  done

  echo "== Bundesliga done =="
}

for i in "${!FEDERATION_IDS[@]}"; do
  if [ -n "${ONLY_FEDERATION}" ] && [ "${ONLY_FEDERATION}" != "${FEDERATION_IDS[$i]}" ]; then
    continue
  fi
  seed_one_region "${i}"
done

if [ -z "${ONLY_FEDERATION}" ]; then
  seed_root_league
fi

echo "All done."
