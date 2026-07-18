# Audit — Étape 3 : Identité organisation

**Date** : 2026-07-19  
**Statut** : ✅ Code + docs + compile/tests OK — validation humaine en attente  
**Fichier source** : `front-agent/docs/for-backend/backend-organization-settings.md`  
**Réf.** : COMPLETE-SPEC §2.2

---

## Ce qui a changé

### Modèle / API `GET|PUT /api/settings`
Nouveaux champs optionnels (rétrocompat : docs Mongo sans ces champs → `null`) :

| Champ | Max | Validation |
|-------|-----|------------|
| `companyTagline` | 120 | optionnel ; `null`/`""` = vider |
| `industry` | 120 | idem |
| `websiteUrl` | 500 | si non vide → URL absolue `http`/`https` + host |

`companyName` : `@Size(max=200)` ajouté.

### Fichiers
- `OrganizationSettings.kt`
- `OrganizationSettingsResponse.kt`
- `UpdateOrganizationSettingsRequest.kt`
- `OrganizationSettingsService.kt` (normalize + `validateWebsiteUrl`)
- `docs/api-frontend.md`

---

## Ce qui marche

- **Compile + test** `LinkedInCredentialsCrudTest` : `BUILD SUCCESSFUL`
- Org avec seulement `companyName` + `contentTopic` : OK (nouveaux champs `null`)
- PUT avec tagline / industry / website valides : persistés
- PUT `websiteUrl: "ftp://…"` ou sans scheme → `400`

---

## Hors scope volontaire (étape 4)

- Injection dans `PromptContextBuilder` — **pas encore** (fiche étape 4 / content-context). Les champs sont persistés et exposés ; l’effet prompt vient à l’étape suivante.

---

## Smoke à faire (humain)

1. `GET /api/settings` → `companyTagline` / `industry` / `websiteUrl` présents (souvent `null`)
2. `PUT` avec les 3 champs remplis → `200` + relecture OK
3. `PUT` avec `websiteUrl: "not-a-url"` → `400`
4. `PUT` avec seulement `companyName` + `contentTopic` (+ champs requis existants) et `companyTagline: null` → tagline vidée ; reste OK

---

## Checklist spec

- [x] Persister tagline / industry / websiteUrl
- [x] Validation URL
- [ ] Injecter dans PromptContextBuilder → **étape 4**
- [x] Rétrocompat orgs minimales

---

## À valider par l’humain

- [ ] Smoke settings identité OK
- [ ] OK pour passer à l’**étape 4** (content context / PromptContextBuilder)
