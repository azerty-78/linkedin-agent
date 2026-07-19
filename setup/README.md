# =============================================================================
# Setup — LinkedIn Agent / Revolut
#
# Local :
#   docker compose -f compose.yml --env-file .env up -d
#   → MongoDB seul sur localhost:27017
#   Fichier : setup/.env (depuis .env.example) — profil Spring default
#
# Production (VPS) :
#   Répertoire : ~/kobe-corporation/revolut-agent/api/
#   Fichiers   : compose.prod.yaml + .env (depuis .env.prod.example)
#   Spring     : SPRING_PROFILES_ACTIVE=prod → application-prod.properties
#   Ports      : API 127.0.0.1:4005 → nginx agent-rev-api.kobecorporation.com
#                Front (hors compose) 4004 → agent-rev-front.kobecorporation.com
#
#   Mongo démarre et doit être healthy avant l’API (depends_on condition).
#
#   Env obligatoires (.env VPS) : MONGODB_URI, JWT_SECRET, ENCRYPTION_KEY,
#   ADMIN_EMAIL, ADMIN_PASSWORD, REVOLUT_AGENT_API_IMAGE
#   Optionnels : ADMIN_USERNAME, MAIL_*, FRONTEND_URL, CORS_*, LINKEDIN_*, AI keys
# CI/CD :
#   .github/workflows/cicd.yml — push/PR/tags + workflow_dispatch actifs.
#   Push images + deploy VPS : ENABLE_CICD_DEPLOY=true (variable repo)
#     + secrets Docker Hub / VPS.
#   Secrets : DOCKERHUB_USERNAME, DOCKERHUB_PASSWORD,
#             VPS_HOST, VPS_USERNAME, VPS_SSH_KEY,
#             VPS_DEPLOY_DIR (optionnel → ~/kobe-corporation/revolut-agent/api)
#   Nginx : voir nginx-agent-rev.conf.example
# =============================================================================
