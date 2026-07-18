# =============================================================================
# Setup — LinkedIn Agent / Revolut
#
# Local :
#   docker compose -f docker-compose.yml --env-file .env up -d
#   → MongoDB seul sur localhost:27017
#
# Production (VPS) :
#   Répertoire : ~/kobe-corporation/revolut-agent/
#   Fichiers   : compose.prod.yaml + .env (depuis .env.prod.example)
#   Ports      : API 127.0.0.1:4005 → nginx agent-rev-api.kobecorporation.com
#                Front (hors compose) 4004 → agent-rev-front.kobecorporation.com
#
#   Mongo démarre et doit être healthy avant l’API (depends_on condition).
#
# CI/CD :
#   .github/workflows/cicd.yml — pas de trigger push/PR (workflow_dispatch only).
#   Push/deploy : variable repo ENABLE_CICD_DEPLOY=true + secrets ci-dessous.
#   Secrets : DOCKERHUB_USERNAME, DOCKERHUB_PASSWORD,
#             VPS_HOST, VPS_USERNAME, VPS_SSH_KEY,
#             VPS_DEPLOY_DIR (optionnel → ~/kobe-corporation/revolut-agent)
#   Nginx : voir nginx-agent-rev.conf.example
# =============================================================================
