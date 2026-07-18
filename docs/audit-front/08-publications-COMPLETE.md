# Audit — Étape 8 : Publications COMPLETE

**Date** : 2026-07-19  
**Statut** : ✅ Code + tests + docs OK — validation humaine en attente  
**Fichier source** : `front-agent/docs/for-backend/backend-publications-COMPLETE.md`  
**Réf.** : COMPLETE-SPEC / GUIDE étape 8

---

## Ce qui a changé

### Statuts
- `SCHEDULED` (one-shot planifié) + `CANCELLED`
- Verrou 409 élargi : `IN_PROGRESS` | `AWAITING_REVIEW` | `SCHEDULED` (`ACTIVE_PUBLICATION_STATUSES`)
- Champ `scheduledPublishAt` sur le document `Publication`

### Endpoints nouveaux / enrichis

| Endpoint | Rôle |
|----------|------|
| `POST /{id}/cancel` | `AWAITING_REVIEW` ou `SCHEDULED` → `CANCELLED` |
| `POST /{id}/regenerate` | LLM frais, reste `AWAITING_REVIEW` |
| `POST /{id}/schedule` | `{ content, publishAt }` → `SCHEDULED` |
| `GET /readiness` | Préflight LinkedIn + app + clé LLM |
| `GET /export?format=csv\|json` | Export audit (mêmes filtres) |
| `GET /` | Filtres `status`, `triggerType`, `from`, `to` |

### Worker one-shot
- Tick 60 s dans `PublicationSchedulerService.processDueOneShotPublications`
- Exécute les runs `SCHEDULED` dus via `executeScheduledPublication`

### Filtres
- `PublicationCriteriaRepository` (ReactiveMongoTemplate + Criteria)

### Tests
- `PublicationCancelScheduleTest` : cancel, schedule, publishAt passé, cancel SCHEDULED
- Enums `CANCELLED` / `SCHEDULED` dans `PublicationConfirmStateTest`

### Docs
- `docs/api-frontend.md` §8ter mis à jour

---

## Ce qui marche

- Flux review : trigger → cancel libère le verrou → nouveau trigger possible
- Schedule one-shot + cancel avant exécution
- Readiness avec `reasons` stables pour i18n front
- Liste filtrée + export CSV/JSON

---

## Checklist spec

### Socle P0
- [x] Enums `AWAITING_REVIEW`, `CANCELLED`, `SCHEDULED`
- [x] `trigger` + `awaitReview` (étape 7)
- [x] `confirm` (étape 7)
- [x] `cancel`
- [x] `GET` liste avec filtres
- [x] `GET/{id}` avec `draftContent`
- [x] PromptContextBuilder (étape 4)
- [x] Verrou 409 actif

### P1
- [x] `regenerate`
- [x] `schedule` + worker one-shot
- [x] `readiness`
- [ ] Tests E2E LinkedIn réels (hors scope unitaire)
- [x] Tests cancel libère le verrou (unitaire)

### P2
- [x] `export` CSV/JSON
- [ ] Rate-limit publish 429 (non demandé en implémentation minimale)
- [x] Doc `api-frontend.md`

---

## Points d’attention

1. Le scheduler **cron org** (étape 6) reste distinct du **schedule one-shot**.
2. `regenerate` en échec LLM passe le run en `FAILURE` (libère le verrou).
3. Rate-limit 429 LinkedIn non ajouté ici (P2 optionnel).

---

## Validation humaine

Merci de valider côté front :
1. Préflight readiness
2. Cancel d’un brouillon review
3. Regenerate
4. Schedule + apparition dans l’historique
5. Filtres + export CSV
