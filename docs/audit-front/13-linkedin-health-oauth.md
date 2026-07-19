# Audit — LinkedIn health / verify / OAuth token

**Date** : 2026-07-19  
**Statut** : ✅ Aligné contrat front  
**Scope** : `GET …/apps/health`, `POST …/apps/{id}/verify`, OAuth connect/callback (déjà présents)

---

## Clarification

| UI | Endpoint | Effet |
|----|----------|--------|
| Rechecker | `GET /apps/health` | Diagnostic uniquement |
| Vérifier | `POST /apps/{id}/verify` | Soft-check Client ID + Secret |
| Login / Ajouter compte | `POST /accounts/connect` | Obtient le token OAuth |

---

## Corrections livrées

### Health
- Toujours 5 checks : `ACTIVE_APP`, `CREDENTIALS_PRESENT`, `CLIENT_ID_FORMAT`, `TOKEN_VALID`, `PAGE_URN_SET`
- Status en **minuscules** (`ok` / `warn` / `fail`) — le front filtre `fail`
- Champs `activeAppId`, `activeAppLabel`, `tokenExpiresAt`, `tokenExpiringSoon`
- Secret jamais exposé

### Verify
- Soft-check déchiffrement + format Client ID
- `422` si secret manquant (`IllegalStateException` LinkedIn)
- `404` si app hors scope

### OAuth (déjà OK)
- `POST /accounts/connect` → app active → `authorizationUrl`
- Callback → token chiffré → redirect `?connected=1`

---

## Checklist

- [x] `GET /api/linkedin/apps/health` réel (pas stub)
- [x] `POST /api/linkedin/apps/{id}/verify` réel
- [x] `POST /api/linkedin/accounts/connect` → app active
- [x] Callback code → token chiffré
- [x] Redirect URI = properties serveur (= LinkedIn Developers)
- [x] `GET /accounts` après OAuth
- [x] Health reflète token + URN
- [x] Tests unitaires health / verify
- [x] `docs/api-frontend.md` §8

---

## Validation humaine

1. App active + secret → Rechecker : credentials `ok`, token/page `fail`
2. Vérifier → `ok: true`
3. Login LinkedIn → callback → accounts non vide
4. Rechecker → `TOKEN_VALID` + `PAGE_URN_SET` `ok` (si URN renseigné)
