# Audit — Étape 11 : Dashboard overview

**Date** : 2026-07-19  
**Statut** : ✅ Code + tests + docs OK — validation humaine en attente  
**Fichier source** : `front-agent/docs/for-backend/backend-dashboard-overview.md`  
**Réf.** : GUIDE-EXECUTION étape 11

---

## Ce qui a changé

### `GET /api/dashboard/overview`
- JWT + `Cache-Control: no-store`
- Counts exacts par statut (`success` = SUCCESS + PARTIAL_SUCCESS)
- `nextScheduledAt` via `SchedulerDecision.computeNextScheduledAt` (fréquence + fenêtre UTC)
- `nextOneShotAt` = prochain run `SCHEDULED`
- `recentPublications` (5) + `lastPublication`
- `readiness` = même service que `/api/publications/readiness`
- `linkedIn` + `settingsSummary` (providers configurés, sans secrets)

### Tests
- `nextScheduledAt` null si disabled
- DAILY aujourd’hui / lendemain après fenêtre
- WEEKDAYS saute le week-end

### Docs
- `docs/api-frontend.md` §8quater

---

## Checklist spec

- [x] Endpoint JWT
- [x] Counts exacts
- [x] `nextScheduledAt` réel
- [x] `nextOneShotAt`
- [x] readiness cohérent
- [x] Pas de secrets
- [ ] Smoke Accueil front (validation humaine)

---

## Validation humaine

1. Accueil → 1 seul appel `/api/dashboard/overview`  
2. KPIs + prochaine publication cohérents  
3. Scheduler off → `nextScheduledAt: null`  
