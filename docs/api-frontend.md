# API Frontend — LinkedIn Agent

Documentation destinée au frontend pour brancher / mettre à jour les appels HTTP vers le backend.

**Base URL (dev)** : `http://localhost:8080`  
**Content-Type** : `application/json`  
**Auth** : JWT Bearer sur toutes les routes sauf celles marquées **Public**

> Dernière synchro : alignée sur les controllers fonctionnels (`auth`, `settings`, `ai`, `linkedin`, `publications`, `actuator/health`).  
> **Changelog** : subscribers retirés ; users/profil en DB ; identité org + **brief éditorial** + `PromptContextBuilder` unique sur tous les flux de génération.

---

## 1. Conventions globales

### Headers

| Header | Obligatoire | Valeur |
|--------|-------------|--------|
| `Content-Type` | Oui (POST/PUT avec body) | `application/json` |
| `Accept` | Recommandé | `application/json` |
| `Authorization` | Oui (routes protégées) | `Bearer <accessToken>` |
| `X-Request-Id` | Optionnel | UUID côté client (sinon généré serveur) |
| `X-Correlation-Id` | Optionnel | Corrélation logs |

### Authentification

1. `POST /api/auth/login` → récupérer `accessToken`
2. Envoyer `Authorization: Bearer <accessToken>` sur chaque requête protégée
3. Si `401` → rediriger vers login (token absent / invalide / expiré / session révoquée — ex. 4ᵉ connexion sur le même compte)

### Format d’erreur standard

```json
{
  "error": "Message lisible",
  "status": 401
}
```

| HTTP | Cas typiques |
|------|----------------|
| `400` | Erreur métier / génération IA (quota, clé invalide, etc.) |
| `401` | Non authentifié / mauvais identifiants |
| `403` | Interdit |
| `409` | Conflit (ex. limite comptes LinkedIn, publication déjà en cours) |
| `422` | Clé API manquante pour le provider IA |

### Enums

**`LlmProvider`**
```text
OPENAI | ANTHROPIC | GEMINI
```

### Coûts LLM (important pour l’UI)

| Provider | Free tier API ? | Défaut recommandé tests |
|----------|-----------------|-------------------------|
| `OPENAI` | Non (besoin crédit billing) | `gpt-4.1-nano` |
| `ANTHROPIC` | Non | `claude-haiku-4-5` |
| `GEMINI` | Oui (Google AI Studio) | `gemini-3.5-flash` |

Si OpenAI/Anthropic renvoient un quota `429` et qu’une clé Gemini est configurée, le backend peut **replier automatiquement** sur Gemini pour `/api/ai/generate-test`.

---

## 2. Auth — `/api/auth`

### 2.1 Login

| | |
|--|--|
| **Méthode** | `POST` |
| **URL** | `/api/auth/login` |
| **Auth** | Public |
| **Succès** | `200` |

**Body**

| Champ | Type | Requis | Contraintes |
|-------|------|--------|-------------|
| `email` | `string` | Oui | Email valide |
| `password` | `string` | Oui | Non vide |

```json
{
  "email": "admin@example.com",
  "password": "••••••••"
}
```

**Réponse `200`**

| Champ | Type | Description |
|-------|------|-------------|
| `accessToken` | `string` | JWT à stocker |
| `refreshToken` | `string` | Émis (pas d’endpoint refresh pour l’instant) |
| `expiresIn` | `number` | Durée de vie access token (ms) |
| `email` | `string` | Email connecté |

```json
{
  "accessToken": "eyJhbGciOiJIUzI1NiJ9...",
  "refreshToken": "eyJhbGciOiJIUzI1NiJ9...",
  "expiresIn": 86400000,
  "email": "admin@example.com"
}
```

**Erreurs** : `401` — identifiants invalides

**Sessions concurrentes** : max **3** connexions simultanées par compte. Un 4ᵉ login réussit mais **révoque la session la plus ancienne** (son JWT renvoie `401` ensuite).

---

### 2.2 Me (profil courant)

| | |
|--|--|
| **Méthode** | `GET` |
| **URL** | `/api/auth/me` |
| **Auth** | Bearer |
| **Succès** | `200` |
| **Body** | aucun |

**Réponse `200`**

| Champ | Type |
|-------|------|
| `id` | `string` |
| `email` | `string` |
| `username` | `string` |
| `role` | `string` (ex. `ADMIN`) |
| `createdAt` | `string` (ISO-8601) |
| `updatedAt` | `string` (ISO-8601) |

```json
{
  "id": "665f1a2b3c4d5e6f7a8b9c0d",
  "email": "admin@example.com",
  "username": "admin",
  "role": "ADMIN",
  "createdAt": "2026-07-18T10:00:00Z",
  "updatedAt": "2026-07-18T12:00:00Z"
}
```

Jamais de `password` / `passwordHash` dans la réponse.

---

### 2.3 Mettre à jour le profil

| | |
|--|--|
| **Méthode** | `PUT` |
| **URL** | `/api/auth/me` |
| **Auth** | Bearer |
| **Succès** | `200` |

**Body**

| Champ | Type | Requis | Contraintes |
|-------|------|--------|-------------|
| `username` | `string` | Oui | 2–64, `[a-zA-Z0-9._-]+`, unique |
| `email` | `string` | Oui | Email valide, unique |

```json
{
  "username": "aline",
  "email": "aline@company.com"
}
```

**Réponse `200`** : même forme que `GET /api/auth/me`.

**Erreurs** : `400` validation · `409` username ou email déjà pris.

Le user ciblé est **uniquement** celui du JWT (pas d’`id` dans le body).  
Après changement d’email, le prochain login utilise le nouvel email.

---

### 2.4 Changer le mot de passe

| | |
|--|--|
| **Méthode** | `POST` |
| **URL** | `/api/auth/change-password` |
| **Auth** | Bearer |
| **Succès** | `204` (pas de body) |

**Body**

| Champ | Type | Requis | Contraintes |
|-------|------|--------|-------------|
| `currentPassword` | `string` | Oui | Doit matcher le hash en DB |
| `newPassword` | `string` | Oui | Min 8 caractères |

```json
{
  "currentPassword": "••••••••",
  "newPassword": "••••••••"
}
```

**Erreurs** : `400` — MDP actuel incorrect ou nouveau trop court.

Les autres sessions du compte sont révoquées ; la session courante reste valide.  
Source de vérité = hash en DB (`app_users`). `ADMIN_PASSWORD` / `.env` ne sert **qu’au seed initial**.

---

### 2.5 Logout

| | |
|--|--|
| **Méthode** | `POST` |
| **URL** | `/api/auth/logout` |
| **Auth** | Bearer |
| **Succès** | `204` |

Révoque la session courante (le JWT ne fonctionne plus). Appeler avant de supprimer le token côté front.

> **Bootstrap ADMIN** : au démarrage, si aucun `ADMIN` en base → création depuis `ADMIN_EMAIL` / `ADMIN_PASSWORD` / `ADMIN_USERNAME` (optionnel). Si l’ADMIN existe déjà → **aucune écrasement** du mot de passe ni du profil.

---

## 3. Settings organisation — `/api/settings`

### 3.1 Lire les settings

| | |
|--|--|
| **Méthode** | `GET` |
| **URL** | `/api/settings` |
| **Auth** | Bearer |
| **Succès** | `200` |
| **Body** | aucun |

**Réponse `200`**

| Champ | Type | Notes |
|-------|------|--------|
| `companyName` | `string` | |
| `companyTagline` | `string \| null` | Slogan (max 120) |
| `industry` | `string \| null` | Secteur (max 120) |
| `websiteUrl` | `string \| null` | Site `http(s)` (max 500) |
| `contentTopic` | `string` | Sujet des tips LinkedIn |
| `contentLanguage` | `fr` \| `en` \| `auto` | Défaut `auto` |
| `brandTone` | enum | Défaut `PROFESSIONAL` |
| `targetAudience` | `string \| null` | Public (max 2000) |
| `brandGuidelines` | `string \| null` | Consignes prioritaires (max 4000) |
| `defaultHashtags` | `string \| null` | Append si absents du post |
| `contentPillars` | `string \| null` | Thèmes récurrents |
| `topicsToAvoid` | `string \| null` | Interdits |
| `contentGoals` | `string \| null` | Objectifs |
| `preferredFormats` | `ContentFormat[]` | Max 6 ; rotation déterministe |
| `callToActionPreference` | `string \| null` | Style de CTA |
| `emojiPolicy` | `NONE` \| `SPARSE` \| `ALLOWED` | Défaut `SPARSE` |
| `postLengthPreference` | `SHORT` \| `MEDIUM` \| `LONG` | Défaut `MEDIUM` |
| `mustIncludeKeywords` | `string \| null` | Mots-clés |
| `examplePostStyle` | `string \| null` | Exemple de style |
| `publishHourUtc` | `number` | `0`–`23` |
| `timezone` | `string` | ex. `Africa/Douala` |
| `activeLlmProvider` | `LlmProvider` | Provider utilisé pour générer |
| `openAiModel` | `string` | Modèle OpenAI sélectionné |
| `anthropicModel` | `string` | Modèle Anthropic sélectionné |
| `geminiModel` | `string` | Modèle Gemini sélectionné |
| `openAiKeyMasked` | `string` | Masqué (`****xxxx`) ou `""` si absente |
| `anthropicKeyMasked` | `string` | Idem |
| `geminiKeyMasked` | `string` | Idem |
| `linkedInOrganizationId` | `string \| null` | Legacy / info |
| `linkedInClientId` | `string \| null` | Client ID app Developer (public) |
| `linkedInClientSecretMasked` | `string` | Secret masqué (jamais en clair) |
| `activeLinkedInAccountId` | `string \| null` | Compte utilisé pour publier (max 3) |
| `schedulerEnabled` | `boolean` | |
| `schedulerFrequency` | enum | Défaut `DAILY` — `DAILY`\|`WEEKDAYS`\|`WEEKLY`\|`CUSTOM_DAYS`\|`EVERY_N_DAYS` |
| `schedulerDaysOfWeek` | `number[]` | ISO 1=lun … 7=dim (WEEKLY / CUSTOM_DAYS) |
| `schedulerIntervalDays` | `number` | EVERY_N_DAYS (2–30), défaut `2` |
| `schedulerAnchorDate` | `string \| null` | `YYYY-MM-DD` ancre EVERY_N_DAYS |
| `updatedAt` | `string` (ISO-8601) | |

> **Redirect OAuth + scopes** : gérés **uniquement côté serveur** (`linkedin.redirect-uri`, `linkedin.oauth-scopes`). **Pas d’UI**, non exposés dans `GET /api/settings`.

```json
{
  "companyName": "KOBE Corporation",
  "companyTagline": "Publish smarter",
  "industry": "SaaS",
  "websiteUrl": "https://example.com",
  "contentTopic": "fintech tips",
  "contentLanguage": "fr",
  "brandTone": "PROFESSIONAL",
  "targetAudience": "CMOs PME",
  "brandGuidelines": "Tutoiement. CTA clair.",
  "defaultHashtags": "#linkedin #growth",
  "contentPillars": "PLG, culture",
  "topicsToAvoid": "politique",
  "contentGoals": "notoriété",
  "preferredFormats": ["TIP", "STORY"],
  "callToActionPreference": "finir par une question",
  "emojiPolicy": "SPARSE",
  "postLengthPreference": "MEDIUM",
  "mustIncludeKeywords": "automation",
  "examplePostStyle": null,
  "publishHourUtc": 9,
  "timezone": "Africa/Douala",
  "activeLlmProvider": "GEMINI",
  "openAiModel": "gpt-4.1-nano",
  "anthropicModel": "claude-haiku-4-5",
  "geminiModel": "gemini-3.5-flash",
  "openAiKeyMasked": "****abcd",
  "anthropicKeyMasked": "",
  "geminiKeyMasked": "****wxyz",
  "linkedInOrganizationId": null,
  "linkedInClientId": "78xxxxxxxxxxxx",
  "linkedInClientSecretMasked": "****cret",
  "activeLinkedInAccountId": null,
  "schedulerEnabled": false,
  "updatedAt": "2026-07-17T11:00:00Z"
}
```

**UI** : une clé masquée non vide = clé déjà configurée (ne jamais préremplir le champ clé avec la valeur masquée). Même règle pour `linkedInClientSecretMasked`.

---

### 3.2 Catalogue des modèles LLM (dropdown style n8n)

| | |
|--|--|
| **Méthode** | `GET` |
| **URL** | `/api/settings/llm-models` |
| **Auth** | Bearer |
| **Succès** | `200` |
| **Body** | aucun |

**Usage frontend** : peupler les selects de modèles filtrés par `provider`.

**Réponse `200`**

| Champ | Type | Description |
|-------|------|-------------|
| `models` | `LlmModelOption[]` | Liste complète |
| `defaultByProvider` | `Record<LlmProvider, string>` | Défaut par provider |
| `recommendedTestByProvider` | `Record<LlmProvider, string>` | Recommandé pour tests |

**`LlmModelOption`**

| Champ | Type | Description |
|-------|------|-------------|
| `id` | `string` | Valeur à envoyer dans `openAiModel` / etc. |
| `label` | `string` | Label UI |
| `provider` | `LlmProvider` | Filtrer le dropdown |
| `freeTier` | `boolean` | Badge « gratuit » (Gemini Flash uniquement) |
| `recommendedForTests` | `boolean` | Badge « recommandé tests » |

```json
{
  "models": [
    {
      "id": "gemini-3.5-flash",
      "label": "Gemini 3.5 Flash (FREE tier)",
      "provider": "GEMINI",
      "freeTier": true,
      "recommendedForTests": true
    },
    {
      "id": "gpt-4.1-nano",
      "label": "GPT-4.1 Nano (moins cher — pas gratuit)",
      "provider": "OPENAI",
      "freeTier": false,
      "recommendedForTests": true
    }
  ],
  "defaultByProvider": {
    "OPENAI": "gpt-4.1-nano",
    "ANTHROPIC": "claude-haiku-4-5",
    "GEMINI": "gemini-3.5-flash"
  },
  "recommendedTestByProvider": {
    "OPENAI": "gpt-4.1-nano",
    "ANTHROPIC": "claude-haiku-4-5",
    "GEMINI": "gemini-3.5-flash"
  }
}
```

**Conseil tests** : préférer `GEMINI` + clé Gemini ; le test utilise `gemini-3.1-flash-lite` (free, léger).  
Les anciens modèles `gemini-2.x` indisponibles sont remappés automatiquement.

---

### 3.3 Mettre à jour les settings

| | |
|--|--|
| **Méthode** | `PUT` |
| **URL** | `/api/settings` |
| **Auth** | Bearer |
| **Succès** | `200` |

**Body**

| Champ | Type | Requis | Notes |
|-------|------|--------|--------|
| `companyName` | `string` | Oui | Non vide (max 200) |
| `companyTagline` | `string \| null` | Non | Max 120 ; `null` / `""` = **vider** |
| `industry` | `string \| null` | Non | Max 120 ; `null` / `""` = **vider** |
| `websiteUrl` | `string \| null` | Non | Max 500 ; si non vide → URL `http(s)` absolue ; `null` / `""` = **vider** |
| `contentTopic` | `string` | Oui | Non vide |
| `contentLanguage` | enum | Non | `null` = conserver ; défaut lecture `auto` |
| `brandTone` | enum | Non | `null` = conserver ; défaut `PROFESSIONAL` |
| `targetAudience` | `string \| null` | Non | Max 2000 ; `null`/`""` = vider |
| `brandGuidelines` | `string \| null` | Non | Max 4000 ; priorité haute dans le prompt |
| `defaultHashtags` | `string \| null` | Non | Max 300 |
| `contentPillars` | `string \| null` | Non | Max 2000 |
| `topicsToAvoid` | `string \| null` | Non | Max 2000 |
| `contentGoals` | `string \| null` | Non | Max 2000 |
| `preferredFormats` | `ContentFormat[]` | Non | `null` = conserver ; `[]` = vider ; max 6 uniques |
| `callToActionPreference` | `string \| null` | Non | Max 500 |
| `emojiPolicy` | enum | Non | `null` = conserver |
| `postLengthPreference` | enum | Non | `null` = conserver |
| `mustIncludeKeywords` | `string \| null` | Non | Max 500 |
| `examplePostStyle` | `string \| null` | Non | Max 4000 |
| `publishHourUtc` | `number` | Oui | `0`–`23` |
| `timezone` | `string` | Oui | Non vide |
| `activeLlmProvider` | `LlmProvider` | Oui | |
| `openAiModel` | `string \| null` | Non | `null` / omis = **conserver** |
| `anthropicModel` | `string \| null` | Non | Idem |
| `geminiModel` | `string \| null` | Non | Idem |
| `openAiApiKey` | `string \| null` | Non | `null` / vide = **conserver** la clé existante |
| `anthropicApiKey` | `string \| null` | Non | Idem |
| `geminiApiKey` | `string \| null` | Non | Idem |
| `linkedInOrganizationId` | `string \| null` | Non | |
| `linkedInClientId` | `string \| null` | Non | `null` = conserver ; `""` = **effacer** ; valeur = remplacer |
| `linkedInClientSecret` | `string \| null` | Non | `null` / vide = **conserver** (chiffré en DB) |
| `clearLinkedInClientSecret` | `boolean \| null` | Non | `true` = **effacer** le secret (Delete) |
| `linkedInRedirectUri` | — | — | **Ignoré** (serveur : `linkedin.redirect-uri`) |
| `linkedInOauthScopes` | — | — | **Ignoré** (serveur : `linkedin.oauth-scopes`) |
| `activeLinkedInAccountId` | `string \| null` | Non | `null` = conserver ; `""` = aucun ; id = compte actif (**400** si inconnu) |
| `schedulerEnabled` | `boolean \| null` | Non | `null` = conserver |
| `schedulerFrequency` | enum | Non | `null` = conserver ; défaut `DAILY` |
| `schedulerDaysOfWeek` | `number[]` | Non | `null` = conserver ; WEEKLY = 1 jour ; CUSTOM_DAYS ≥ 1 |
| `schedulerIntervalDays` | `number` | Non | EVERY_N_DAYS ∈ [2,30] |
| `schedulerAnchorDate` | `string \| null` | Non | `YYYY-MM-DD` ; `""` = vider |

> **Scheduler** : fenêtre `publishHourUtc` UTC ± 5 min ; jours / idempotence 1 SUCCESS SCHEDULED / jour calendaire selon `timezone`. Provider auto = `activeLlmProvider`.  
> **Brief IA** : ton / emojis / formats via `PromptContextBuilder` (tous les flux de génération).

**Enums** : `SchedulerFrequency`, `BrandTone`, `EmojiPolicy`, `PostLengthPreference`, `ContentFormat`, `ContentLanguage` (`fr|en|auto`).

```json
{
  "companyName": "KOBE Corporation",
  "companyTagline": "Publish smarter",
  "industry": "SaaS",
  "websiteUrl": "https://example.com",
  "contentTopic": "fintech tips",
  "contentLanguage": "fr",
  "brandTone": "CASUAL",
  "emojiPolicy": "NONE",
  "postLengthPreference": "SHORT",
  "preferredFormats": ["TIP", "QUESTION"],
  "topicsToAvoid": "politique",
  "defaultHashtags": "#fintech",
  "publishHourUtc": 9,
  "timezone": "Africa/Douala",
  "schedulerEnabled": true,
  "schedulerFrequency": "WEEKDAYS",
  "schedulerDaysOfWeek": [1, 2, 3, 4, 5],
  "schedulerIntervalDays": 2,
  "schedulerAnchorDate": null,
  "activeLlmProvider": "GEMINI",
  "openAiModel": "gpt-4.1-nano",
  "anthropicModel": "claude-haiku-4-5",
  "geminiModel": "gemini-3.5-flash",
  "geminiApiKey": "AIza...",
  "openAiApiKey": null,
  "anthropicApiKey": null,
  "linkedInOrganizationId": null,
  "linkedInClientId": "78xxxxxxxxxxxx",
  "linkedInClientSecret": "WPL_AP1....",
  "activeLinkedInAccountId": null
}
```

**Réponse `200`** : même shape que `GET /api/settings`.

**Règles UI**
- Ne jamais préremplir les champs clé/secret avec la valeur masquée.
- Champ clé/secret vide au submit → envoyer `null` (ou omettre) pour ne pas écraser.
- Choisir le compte LinkedIn de publication via `activeLinkedInAccountId` (liste = `GET /api/linkedin/accounts`).

---

## 4. Génération IA — `/api/ai`

### 4.1 Générer un tip de test

| | |
|--|--|
| **Méthode** | `POST` |
| **URL** | `/api/ai/generate-test` |
| **Auth** | Bearer |
| **Succès** | `200` |
| **Body** | aucun |

Le backend **n’utilise pas** les modèles premium choisis dans les settings pour cet endpoint.

| Contexte | Modèles | Tokens |
|----------|---------|--------|
| `POST /api/ai/generate-test` | Forcés faibles (`ai.test.*`) | ~80 max |
| Génération réelle (scheduler / futur) | Modèles choisis dans `/api/settings` | ~1024 |

Comportement test :
- Prompt ultra-court
- Si une clé Gemini existe → priorité à `gemini-3.1-flash-lite` (free, léger)
- Sinon provider actif + modèle de test (ex. `gpt-4.1-nano`)
- gpt-4o / sonnet / etc. configurés en UI ne sont **pas** appelés ici

**Réponse `200`**

| Champ | Type | Description |
|-------|------|-------------|
| `content` | `string` | Tip généré |
| `provider` | `LlmProvider` | Provider réellement utilisé |
| `model` | `string` | Modèle de test réellement utilisé |
| `generatedAt` | `string` (ISO-8601) | |
| `promptUsed` | `string` | Prompt envoyé au LLM |

```json
{
  "content": "Tip court…",
  "provider": "GEMINI",
  "model": "gemini-3.1-flash-lite",
  "generatedAt": "2026-07-17T12:00:00Z",
  "promptUsed": "..."
}
```

**Erreurs**
- `422` — pas de clé API pour le provider de test
- `400` — quota / clé invalide / autre erreur LLM

---

## 7. Health (optionnel)

| | |
|--|--|
| **Méthode** | `GET` |
| **URL** | `/actuator/health` |
| **Auth** | Public |
| **Succès** | `200` |

Utile pour un indicateur « API up » dans le frontend / CI.

---

## 8. LinkedIn — credentials org + multi-comptes (max 3)

**Clarification modèles :**

| Niveau | Quoi | Où |
|--------|------|-----|
| **App (org)** | Client ID / Secret | `PUT /api/settings` (secret chiffré, jamais en clair) |
| **OAuth serveur** | Redirect URI + scopes | Properties `linkedin.redirect-uri` / `linkedin.oauth-scopes` — **pas d’UI** |
| **Compte** | OAuth token + URN Page (+ métadonnées Page) | `/api/linkedin/accounts*` |
| **Actif** | Compte qui publie | `PUT …/activate` (recommandé) ou `activeLinkedInAccountId` |

Auth Bearer sur toutes les routes ci-dessous sauf `/api/linkedin/callback` (public).

### 8.0 Credentials app (Settings) — UI

Settings expose seulement Client ID + Secret (masqué). **Pas** de redirect / scopes.

```json
{
  "linkedInClientId": "78xxxxxxxxxxxx",
  "linkedInClientSecret": "WPL_AP1....",
  "activeLinkedInAccountId": "mongoObjectIdDuCompteActif"
}
```

`GET /api/settings` → `linkedInClientId`, `linkedInClientSecretMasked`, `activeLinkedInAccountId` (pas de `linkedInRedirectUri` / `linkedInOauthScopes`).

### 8.1 Tableau comptes (Réglages)

`GET /api/linkedin/accounts` — chaque compte est autonome pour le tableau :

| Colonne UI | Champ API | Notes |
|------------|-----------|--------|
| ID LinkedIn | `linkedInClientId` | Copie credentials **org** (même valeur sur chaque ligne) |
| Secret | `linkedInClientSecretMasked` | Toujours masqué (`****xxxx`) ou `null` |
| Nom de la page | `organizationName` | Sinon fallback `label` / id dans `linkedInOrganizationUrn` |
| Statut publication | `isActive` | `true` → **Actif** ; `false` → **En veille** |

> Ne pas confondre `isActive` (qui publie) avec `status` OAuth (`CONNECTED` / `EXPIRED` / …).

```json
{
  "id": "665f…",
  "label": "Page Prod",
  "status": "CONNECTED",
  "isActive": true,
  "organizationName": "KOBE Corporation",
  "linkedInOrganizationUrn": "urn:li:organization:123",
  "linkedInClientId": "78xxxxxxxxxxxx",
  "linkedInClientSecretMasked": "****cret",
  "organizationLogoUrl": null,
  "organizationVanityName": null
}
```

`organizationName` : résolu best-effort au `PUT …/organization-urn` (et au reconnect OAuth si URN déjà présent) via Organizations API. Scopes : `r_organization_social` (+ CMA). Sinon `null`.

### 8.2 Endpoints comptes / résumé

| Méthode | Route | Succès | Erreurs |
|---------|-------|--------|---------|
| `GET` | `/api/linkedin/summary` | `200` | `401` |
| `GET` | `/api/linkedin/accounts` | `200` | `401` |
| `GET` | `/api/linkedin/accounts/active` | `200` | `401`, **`404`** si aucun actif |
| `POST` | `/api/linkedin/accounts/connect?label=` | `200` | `401`, **`409`** si max 3, `422` si credentials absents |
| `GET` | `/api/linkedin/accounts/{id}` | `200` | `401`, `404` |
| `GET` | `/api/linkedin/accounts/{id}/auth-url` | `200` | `401`, `404` |
| `PUT` | `/api/linkedin/accounts/{id}/activate` | `200` `LinkedInAccountListResponse` | `401`, `404` |
| `PUT` | `/api/linkedin/accounts/{id}` | `200` | `401`, `404` |
| `PUT` | `/api/linkedin/accounts/{id}/organization-urn` | `200` | `401`, `404`, `400` |
| `DELETE` | `/api/linkedin/accounts/{id}` | `204` | `401`, `404` |

**Switch actif** : `PUT /api/linkedin/accounts/{id}/activate` → `200` liste rafraîchie (`isActive` mis à jour).

Compat legacy : `GET /auth-url`, `GET /status`, `PUT /organization-urn`, `DELETE /disconnect`.

#### `GET /api/linkedin/accounts/active`

Compte explicitement sélectionné — **pas** de fallback « premier connecté ». Même shape que ci-dessus (`isActive: true`).

#### `GET /api/linkedin/summary`

```json
{
  "hasAppCredentials": true,
  "accountsCount": 2,
  "maxAccounts": 3,
  "activeAccount": null,
  "canPublish": false,
  "publishBlockers": ["NO_ACTIVE_ACCOUNT", "MISSING_ORG_URN"]
}
```

| Code `publishBlockers` (i18n front) | Signification |
|-------------------------------------|---------------|
| `MISSING_CLIENT_ID` | Pas de Client ID |
| `MISSING_CLIENT_SECRET` | Pas de secret |
| `NO_ACTIVE_ACCOUNT` | Pas d’actif |
| `NOT_CONNECTED` | Actif ≠ `CONNECTED` |
| `MISSING_ORG_URN` | URN Page manquant |
| `EXPIRED` | Token / statut expiré |
| `ERROR_STATUS` | Statut `ERROR` |

`canPublish === true` ⇔ `publishBlockers` vide.

#### `PUT /api/linkedin/accounts/{id}/activate`

```ts
await fetch(`${API_BASE}/api/linkedin/accounts/${id}/activate`, {
  method: "PUT",
  headers: { Authorization: `Bearer ${token}` },
});
// → 200 LinkedInAccountListResponse
```

#### `POST /api/linkedin/accounts/connect?label=`

```ts
async function addLinkedInAccount(token: string, label?: string) {
  const q = label ? `?label=${encodeURIComponent(label)}` : "";
  const res = await fetch(`${API_BASE}/api/linkedin/accounts/connect${q}`, {
    method: "POST",
    headers: { Authorization: `Bearer ${token}` },
  });
  if (res.status === 409) throw new Error("Maximum 3 comptes LinkedIn");
  if (!res.ok) throw new Error(await res.text());
  const { authorizationUrl } = await res.json();
  window.location.assign(authorizationUrl);
}
```

#### `GET /api/linkedin/callback` — **ne pas appeler depuis le front**

302 → `{FRONTEND_URL}/dashboard/linkedin?connected=true|false&error=…`.

### 8.2 Types TypeScript

```ts
type LinkedInConnectionStatus =
  | "NOT_CONNECTED"
  | "CONNECTED"
  | "EXPIRED"
  | "ERROR";

type LinkedInStatusResponse = {
  id: string | null;
  label: string;
  status: LinkedInConnectionStatus;
  linkedInUserUrn: string | null;
  linkedInOrganizationUrn: string | null;
  connectedAt: string | null;
  lastError: string | null;
  /** Publication : true = Actif, false = En veille */
  isActive: boolean;
  organizationName: string | null;
  organizationLogoUrl: string | null;
  organizationVanityName: string | null;
  /** Credentials org (copie) — secret toujours masqué */
  linkedInClientId: string | null;
  linkedInClientSecretMasked: string | null;
};

type LinkedInAccountListResponse = {
  accounts: LinkedInStatusResponse[];
  maxAccounts: number;
  activeLinkedInAccountId: string | null;
};

type LinkedInSummaryResponse = {
  hasAppCredentials: boolean;
  accountsCount: number;
  maxAccounts: number;
  activeAccount: LinkedInStatusResponse | null;
  canPublish: boolean;
  publishBlockers: string[];
};

type PublicationRecordResponse = {
  id: string | null;
  content: string;
  aiProvider: string;
  targetUrn: string;
  linkedInPostId: string | null;
  status: "PENDING" | "PUBLISHED" | "FAILED";
  errorMessage: string | null;
  createdAt: string;
  publishedAt: string | null;
  accountId: string | null;
  accountLabel: string | null;
  linkedInPostUrl: string | null;
};
```

### 8.3 UI suggérée

| Écran | Appel |
|-------|--------|
| Page LinkedIn | `GET /accounts/active` (`404` → empty state) |
| Page Test / bandeaux | `GET /summary` + i18n `publishBlockers` |
| Réglages | liste + `PUT …/activate` + credentials settings |

**Ne jamais** afficher ni stocker de token LinkedIn côté front.

### 8.4 Checklist frontend LinkedIn

- [ ] `GET /accounts/active` sur page LinkedIn
- [ ] `GET /summary` sur Test / bandeaux
- [ ] Actif via `PUT /accounts/{id}/activate`
- [ ] Fallback label si `organizationName` null
- [ ] Historique : lien `linkedInPostUrl`
- [ ] Max 3 → `409`
- [ ] Aucun appel à `/callback`

### 8.5 Scopes

Défaut : `w_organization_social r_organization_social`.  
Publication = **Page org**. Lecture name/logo = `r_organization_social` (+ produit CMA).

---

## 8bis. Publication LinkedIn — `/api/linkedin/publish-test` & historique

> Publie sur la Page du **compte actif**.

**Prérequis** : `GET /summary` → `canPublish: true`.

### `POST /api/linkedin/publish-test` (JWT)

| Body | Comportement |
|------|----------------|
| `{}` ou omis | Génère via IA puis publie |
| `{ "content": "Mon texte…" }` | Publie tel quel |

**Réponse `200`** enrichie : `accountId`, `accountLabel`, `linkedInPostUrl` (si URN post dérivable).  
Échec LinkedIn → toujours `200` avec `status: "FAILED"` (+ `errorMessage`). Prérequis manquants → `422`.

### `GET /api/linkedin/publications?limit=10` (JWT)

Même shape enrichie, du plus récent au plus ancien.

---

## 8ter. Orchestration Publications — `POST /api/publications/trigger`

| | |
|--|--|
| **Méthode** | `POST` |
| **URL** | `/api/publications/trigger` |
| **Auth** | Bearer |
| **Succès** | `200` |

**Body** (optionnel)

| Champ | Type | Requis | Notes |
|-------|------|--------|--------|
| `llmProvider` | `OPENAI` \| `ANTHROPIC` \| `GEMINI` | Non | Sinon fallback `activeLlmProvider` |
| `awaitReview` | `boolean` | Non | `true` = pause `AWAITING_REVIEW` (UI manuelle) ; `false`/omis = publier LinkedIn (scheduler) |

```json
{ "llmProvider": "GEMINI", "awaitReview": true }
```

Body omis / `{}` → `activeLlmProvider`, publication directe (comme le scheduler).

**Réponse `200` (avec review)**

```json
{ "publicationId": "665f…", "status": "AWAITING_REVIEW" }
```

**Réponse `200` (sans review)** : `status` final `SUCCESS` / `PARTIAL_SUCCESS` / `FAILURE`.

**Erreurs**

| HTTP | Cas |
|------|-----|
| `400` | Enum `llmProvider` invalide |
| `409` | Une publication `IN_PROGRESS` existe déjà |
| `422` | Clé API du provider manquante — message FR : `Clé API GEMINI manquante. Configurez-la dans Paramètres.` |

### `POST /api/publications/{id}/confirm`

| | |
|--|--|
| **Méthode** | `POST` |
| **URL** | `/api/publications/{id}/confirm` |
| **Auth** | Bearer |
| **Succès** | `200` |

**Body**

| Champ | Type | Requis | Notes |
|-------|------|--------|--------|
| `content` | `string` | Oui | Texte final (éditable), 1–3000 chars |

```json
{ "content": "texte final éventuellement édité" }
```

**Réponse** : `{ "publicationId", "status" }` (SUCCESS / PARTIAL_SUCCESS / FAILURE).

**Erreurs** : `400` contenu invalide · `404` inconnu · `409` pas en `AWAITING_REVIEW` / autre run en cours · `422` LinkedIn.

> Statut `AWAITING_REVIEW` : contenu généré, **pas** de post LinkedIn tant que `confirm` n’est pas appelé. `draftContent` disponible sur `GET /api/publications/{id}`.

**Providers (Paramètres)** — déjà en place : clés chiffrées + `*KeyMasked`, modèles par provider, `GET /api/settings/llm-models`, `activeLlmProvider` pour le scheduler.

---

## 9. Checklist intégration frontend

| Écran | Endpoints |
|-------|-----------|
| Login | `POST /api/auth/login` |
| Logout | `POST /api/auth/logout` |
| Guard / header user | `GET /api/auth/me` |
| Profil (édition) | `PUT /api/auth/me` |
| Changement MDP | `POST /api/auth/change-password` |
| Settings org (+ LinkedIn app) | `GET` + `PUT /api/settings` |
| Dropdowns modèles | `GET /api/settings/llm-models` |
| Bouton « Générer un tip » | `POST /api/ai/generate-test` |
| LinkedIn résumé / Test | `GET /api/linkedin/summary` |
| LinkedIn compte actif | `GET /api/linkedin/accounts/active` |
| LinkedIn comptes + activate | `GET/POST/PUT/DELETE /api/linkedin/accounts…` |
| Publier (test LinkedIn) | `POST /api/linkedin/publish-test` |
| Historique LinkedIn records | `GET /api/linkedin/publications?limit=` |
| Orchestration manuelle | `POST /api/publications/trigger` `{ llmProvider?, awaitReview? }` |
| Confirmer relecture | `POST /api/publications/{id}/confirm` `{ content }` |
| Historique orchestration | `GET /api/publications?page=&size=` |
| Détail run | `GET /api/publications/{id}` |

### Exemple flux settings + génération (tests gratuits)

1. `GET /api/settings/llm-models` → options Gemini free
2. `PUT /api/settings` avec `activeLlmProvider: "GEMINI"`, `geminiApiKey: "…"`
3. `POST /api/ai/generate-test` → modèle de test faible

---

## 10. Récap URLs (toutes les routes fonctionnelles)

| Méthode | URL | Auth | Body / params | Succès |
|---------|-----|------|---------------|--------|
| `POST` | `/api/auth/login` | Non | `{ email, password }` | `200` |
| `POST` | `/api/auth/logout` | Oui | — | `204` |
| `GET` | `/api/auth/me` | Oui | — | `200` |
| `PUT` | `/api/auth/me` | Oui | `{ username, email }` | `200` |
| `POST` | `/api/auth/change-password` | Oui | `{ currentPassword, newPassword }` | `204` |
| `GET` | `/api/settings` | Oui | — | `200` |
| `GET` | `/api/settings/llm-models` | Oui | — | `200` |
| `PUT` | `/api/settings` | Oui | settings (§3.3) | `200` |
| `POST` | `/api/ai/generate-test` | Oui | — | `200` |
| `GET` | `/api/linkedin/summary` | Oui | — | `200` |
| `GET` | `/api/linkedin/accounts` | Oui | — | `200` |
| `GET` | `/api/linkedin/accounts/active` | Oui | — | `200` / `404` |
| `POST` | `/api/linkedin/accounts/connect` | Oui | query `label?` | `200` / `409` |
| `PUT` | `/api/linkedin/accounts/{id}/activate` | Oui | — | `200` |
| `GET` | `/api/linkedin/auth-url` | Oui | — | `200` |
| `GET` | `/api/linkedin/status` | Oui | — | `200` |
| `PUT` | `/api/linkedin/organization-urn` | Oui | `{ organizationUrn }` | `200` |
| `DELETE` | `/api/linkedin/disconnect` | Oui | — | `204` |
| `POST` | `/api/linkedin/publish-test` | Oui | `{ content? }` | `200` (ou `422`) |
| `GET` | `/api/linkedin/publications` | Oui | query `limit?` | `200` |
| `POST` | `/api/publications/trigger` | Oui | `{ llmProvider?, awaitReview? }` | `200` / `409` / `422` |
| `POST` | `/api/publications/{id}/confirm` | Oui | `{ content }` | `200` / `400` / `404` / `409` / `422` |
| `GET` | `/api/publications` | Oui | `page`, `size` | `200` |
| `GET` | `/api/publications/{id}` | Oui | path id | `200` / `404` |
| `GET` | `/api/linkedin/callback` | Non (LinkedIn only) | `code`/`state`/`error` → 302 front | `302` |
| `GET` | `/actuator/health` | Non | — | `200` |

---

*Document frontend — synchronisé avec l’API backend LinkedIn Agent.*
