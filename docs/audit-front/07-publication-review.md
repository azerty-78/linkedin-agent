# Audit — Étape 7 : Relecture humaine (awaitReview / confirm)

**Date** : 2026-07-19  
**Statut** : ✅ Code + tests + docs OK — validation humaine en attente  
**Fichier source** : `front-agent/docs/for-backend/backend-publication-review.md`  
**Réf.** : COMPLETE-SPEC §5

---

## Ce qui a changé

### Statut
- `PublicationStatus.AWAITING_REVIEW` ajouté

### `POST /api/publications/trigger`
```json
{ "llmProvider": "GEMINI", "awaitReview": true }
```
- `awaitReview: true` → génère, persiste `AWAITING_REVIEW`, **ne publie pas** LinkedIn
- `false` / omis → comportement précédent (générer + LinkedIn) — **scheduler inchangé**

### `POST /api/publications/{id}/confirm` (nouveau)
```json
{ "content": "texte final ≤ 3000 chars" }
```
- Prérequis : statut `AWAITING_REVIEW` sinon **409**
- Publie LinkedIn avec le contenu édité
- Réponse `{ publicationId, status }`

### Réponse détail
- `draftContent` + `llmProvider` sur `GET /api/publications/{id}`

### Tests
- Confirm mauvais statut → `PublicationInvalidStateException`
- Contenu vide / > 3000 → 400 métier

---

## Ce qui marche

- **BUILD SUCCESSFUL**
- Trigger + review → pause sans LinkedIn
- Scheduler sans `awaitReview` → publish direct
- Confirm hors review → 409

---

## Smoke à faire (humain)

1. `POST /trigger` `{ "awaitReview": true }` → `status: AWAITING_REVIEW`
2. Vérifier LinkedIn **pas** appelé ; `draftContent` présent
3. Éditer → `POST /{id}/confirm` `{ "content": "…" }` → SUCCESS + post LinkedIn
4. Re-confirm même id → **409**
5. Scheduler / trigger sans awaitReview → publish direct

---

## Checklist spec

- [x] Enum `AWAITING_REVIEW`
- [x] `trigger` honore `awaitReview`
- [x] Scheduler sans review
- [x] `POST …/confirm`
- [x] Validation 3000 chars
- [x] Pas de LinkedIn avant confirm
- [x] Tests état / validation
- [x] Doc `api-frontend.md`

---

## À valider par l’humain

- [ ] Smoke générer → relire → confirm OK
- [ ] OK pour passer à l’**étape 8** (publications COMPLETE)
