# Audit — Étape 0 : Lecture COMPLETE-SPEC

**Date** : 2026-07-18  
**Statut** : ✅ Fait (lecture seule, aucun code)  
**Fichier source** : `front-agent/docs/for-backend/backend-COMPLETE-SPEC.md` §§0–1

---

## Ce qui a été compris

### Principes transverses (§0)
- Tout scopé `userId` (JWT) — jamais d’userId/orgId dans le body client
- Redirect LinkedIn auto (pas de `.env` user)
- Secrets chiffrés at-rest ; réponses masquées `****xxxx`
- Rétrocompat : champs absents → defaults
- Erreurs `{ "error": "…", "status": N }`
- Idempotence scheduler : 1 succès / jour calendaire
- Un seul `PromptContextBuilder` pour generate-test, publish-test, trigger, scheduler

### Carte zones (§1)
| Zone | Backend cible |
|------|---------------|
| Settings org / brief / schedule / LLM | `GET/PUT /api/settings` |
| Publications | trigger + review + CRUD étendu |
| LinkedIn | multi-apps max 3 |
| Test IA | generate-test + publish-test |
| Profil | users en DB + seed ADMIN |
| Abonnés | **à supprimer** (étape 1) |

### Points critiques notés pour plus tard
- Brief IA sans `PromptContextBuilder` = UI sans effet (étape 4)
- `awaitReview` / `AWAITING_REVIEW` / `confirm` (étape 7)
- Auth users hors `.env` (étape 2)

---

## Ce qui a changé
Rien (lecture seule).

## Ce qui marche
N/A.

## À valider par l’humain
- [ ] Prise de connaissance OK — passer à l’étape 1
