# Audit — Étape 10 : LinkedIn multi-apps

**Date** : 2026-07-19  
**Statut** : ✅ Code + tests + docs OK — validation humaine en attente  
**Fichier source** : `front-agent/docs/for-backend/backend-linkedin-multi-apps.md`  
**Réf.** : GUIDE-EXECUTION étape 10

---

## Ce qui a changé

### Nouvelle collection `linkedin_apps` (max 3)
- Entité `LinkedInApp` : label, clientId, secret chiffré, `isActive`
- Tenant : `organizationId = "default"` (single-tenant actuel, dérivé serveur)

### API `/api/linkedin/apps`
| Route | Rôle |
|-------|------|
| `GET/POST` | Liste / créer (1ʳᵉ → active auto) |
| `PUT /{id}` | Update label/clientId/secret |
| `PUT /{id}/activate` | Activation exclusive |
| `DELETE /{id}` | Auto-active la plus récente si besoin |
| `GET /audit` + `/audit/export` | Historique sans secrets |
| `GET /health` | Checks credentials / token / URN |
| `POST /{id}/verify` | Secret déchiffrable |

### Breaking
- `PUT /api/settings` **ignore** `linkedInClientId` / secret
- OAuth + publish → credentials de l’**app active**
- Migration boot : settings legacy → app « Default »

### Summary enrichi
- `hasActiveApp`, `activeAppId`, `appsCount`
- Blocker `NO_ACTIVE_APP`

### Tests
- Limite 4ᵉ app → 409
- Activate exclusif
- Settings n’écrit plus les credentials
- Overlay GET settings depuis app active

### Docs
- `docs/api-frontend.md` §8

---

## Checklist spec

- [x] Entité + repo LinkedInApp
- [x] Scope serveur (org default / JWT actor pour audit)
- [x] Defaults label / isActive / masked / timestamps
- [x] Migration legacy
- [x] CRUD + activate
- [x] Audit paginé
- [x] OAuth / publish sur app active
- [x] api-frontend.md
- [x] Tests limite / activate / secret absent de l’audit
- [x] Retrait écriture settings
- [x] Health / verify / audit export (front déjà branché)

---

## Validation humaine

1. Créer 1–3 apps, activer, supprimer l’active  
2. OAuth connect avec app active  
3. Vérifier que settings ne modifie plus Client ID/Secret  
4. Historique audit sous le tableau  
5. Health / verify  
