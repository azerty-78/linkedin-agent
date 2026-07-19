# Audit — LinkedIn OAuth profil personnel

**Date** : 2026-07-19  
**Statut** : ✅  
**Cible** : publier sur le **compte perso** (`w_member_social`), pas la Page org.

---

## Livré

| Item | Détail |
|------|--------|
| Scopes | `openid profile email w_member_social` (properties + compose + .env*) |
| Callback | Stocke `linkedInUserUrn` via `/userinfo` ou `/v2/me` |
| Publish | Auteur = person URN ; UGC `/v2/ugcPosts` |
| Health | `AUTHOR_URN_SET` ; `PAGE_URN_SET` = **warn** seulement |
| Readiness | `personUrn` + `publishTarget: PERSON` ; plus de hard fail org |

## Checklist

- [x] Scopes OAuth member
- [x] Callback → person URN
- [x] Publish person par défaut
- [x] Health AUTHOR_URN_SET + PAGE warn
- [x] Readiness personUrn
- [x] Redirect URI = API publique (inchangé)

## Hors scope (plus tard)

Community Management / `w_organization_social` / gate Page.

## Validation humaine

1. LinkedIn Developers : produits OpenID + Share on LinkedIn ; scopes member  
2. Login OAuth → compte avec `linkedInUserUrn`  
3. Health : AUTHOR ok, PAGE warn OK  
4. Readiness `ready: true` sans URN Page  
5. Publish-test sur le feed perso  
