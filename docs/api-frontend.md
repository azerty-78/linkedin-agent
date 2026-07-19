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
| `linkedInClientId` | `string \| null` | Non | **Déprécié** — écriture **ignorée** (utiliser `/api/linkedin/apps`) |
| `linkedInClientSecret` | `string \| null` | Non | **Déprécié** — écriture **ignorée** |
| `clearLinkedInClientSecret` | `boolean \| null` | Non | **Déprécié** — écriture **ignorée** |
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

### 3.4 PATCH partiel (Paramètres par section) — **recommandé UI**

| | |
|--|--|
| **Méthode** | `PATCH` |
| **URL** | `/api/settings` |
| **Auth** | Bearer |
| **Succès** | `200` |

Body = **sous-ensemble** de champs (`PatchOrganizationSettingsRequest` — DTO concret, **pas** `JsonNode`).  
`null` / omis = **conserver**. Création lazy si aucune ligne settings.

| Présence | Effet |
|----------|--------|
| Champ omis / `null` | Conserver |
| Texte optionnel `""` | Vider |
| Clé API `null` / `""` / omis | Conserver le secret chiffré |
| Clé API non vide | Remplacer |
| `companyName` / `contentTopic` **non-null** | trim ; vide → `400` |
| `companyName` / `contentTopic` **omis** | ne pas toucher / **pas requis** |

```http
PATCH /api/settings
{ "geminiApiKey": "AIza…", "geminiModel": "gemini-2.0-flash" }
```

→ **200** même si `companyName` est encore vide.

```json
{ "companyName": "Revlut" }
```

→ **200** sans `contentTopic`.

`PUT` reste disponible (snapshot complet, compat).

---

## 4. Génération IA — `/api/ai`

### 4.1 Générer un tip de test

| | |
|--|--|
| **Méthode** | `POST` |
| **URL** | `/api/ai/generate-test` |
| **Auth** | Bearer |
| **Succès** | `200` |
| **Body** | optionnel (rétrocompat si absent / `{}`) |

Le backend **n’utilise pas** les modèles premium choisis dans les settings pour cet endpoint.

| Contexte | Modèles | Tokens |
|----------|---------|--------|
| `POST /api/ai/generate-test` | Forcés faibles (`ai.test.*`) | ~80 max |
| Génération réelle (scheduler / publications) | Modèles choisis dans `/api/settings` | ~1024 |

**Body optionnel**

| Champ | Type | Défaut | Notes |
|-------|------|--------|--------|
| `llmProvider` | `GEMINI` \| `OPENAI` \| `ANTHROPIC` | prefer Gemini / actif | Si clé absente → **422** message FR |
| `topic` | `string` | `settings.contentTopic` | Override ponctuel |
| `brandTone` | `BrandTone` | settings | Override ton |
| `contentLanguage` | `fr` \| `en` \| `auto` | settings | Override langue |
| `useOrgBrief` | `boolean` | `true` | `false` = tip minimal (topic + règles), sans brief org |

```json
{
  "llmProvider": "GEMINI",
  "topic": "Onboarding client",
  "brandTone": "PROFESSIONAL",
  "contentLanguage": "fr",
  "useOrgBrief": true
}
```

Comportement test :
- Prompt via `PromptContextBuilder` (compact) ; modèles `ai.test.*` uniquement
- Si une clé Gemini existe et pas d’override → priorité Gemini lite
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

## 8. LinkedIn — multi-apps (max 3) + multi-comptes Pages (max 3)

**Clarification modèles :**

| Niveau | Quoi | Où |
|--------|------|-----|
| **Application (credentials)** | Client ID / Secret (max 3) | `/api/linkedin/apps*` — **source de vérité** |
| **OAuth serveur** | Redirect URI + scopes | Properties `linkedin.redirect-uri` / `linkedin.oauth-scopes` — **pas d'UI** |
| **Compte / Page** | OAuth token + URN Page | `/api/linkedin/accounts*` |
| **App active** | Credentials OAuth / publish | `PUT /api/linkedin/apps/{id}/activate` |
| **Compte actif** | Page qui publie | `PUT /api/linkedin/accounts/{id}/activate` |

Auth Bearer sauf `/api/linkedin/callback` (public).

> `PUT /api/settings` **n'écrit plus** `linkedInClientId` / secret. `GET /api/settings` peut renvoyer ceux de l'**app active** (overlay lecture).

### 8.0 Applications LinkedIn — `/api/linkedin/apps`

| Méthode | Route | Succès | Notes |
|---------|-------|--------|--------|
| `GET` | `/api/linkedin/apps` | `200` | Liste + `maxApps=3` + `activeAppId` |
| `POST` | `/api/linkedin/apps` | `201` | 1re app → active auto ; `409` si max 3 ou `clientId` dupliqué |
| `PUT` | `/api/linkedin/apps/{id}` | `200` | Secret omis = conserver ; `clearClientSecret: true` = effacer |
| `PUT` | `/api/linkedin/apps/{id}/activate` | `200` | Une seule active |
| `DELETE` | `/api/linkedin/apps/{id}` | `204` | Active supprimée → auto-active la plus récente |
| `GET` | `/api/linkedin/apps/audit?page&size` | `200` | Historique (sans secrets) |
| `GET` | `/api/linkedin/apps/audit/export` | `200` | `{ events: [...] }` |
| `GET` | `/api/linkedin/apps/health` | `200` | Checks credentials / token / URN |
| `POST` | `/api/linkedin/apps/{id}/verify` | `200` | Credentials déchiffrables ? |

**Create** : `{ "label": "Prod", "clientId": "78xxxxxxxxxxxx", "clientSecret": "…" }` — `clientId` regex `^[a-zA-Z0-9]{8,64}$`.

Migration boot : settings legacy → app « Default » active si 0 apps.

### 8.1 Comptes Pages (OAuth)

Inchangé : `/api/linkedin/accounts*` (max 3). Les champs `linkedInClientId` / masked reflètent l'**app active**.

### 8.2 Summary

`GET /api/linkedin/summary` ajoute `hasActiveApp`, `activeAppId`, `appsCount` + blocker `NO_ACTIVE_APP`.

OAuth / publish-test utilisent l'**app active** (`getActiveCredentials`).

### 8.3 Checklist frontend

- [ ] Tableau apps CRUD + activate + audit
- [ ] Ne plus écrire Client ID/Secret via settings
- [ ] `publishBlockers` dont `NO_ACTIVE_APP`
- [ ] Comptes Pages inchangés
- [ ] Aucun appel à `/callback`

### 8.4 Scopes

Défaut : `w_organization_social r_organization_social`.

---

## 8bis. Publication LinkedIn — `/api/linkedin/publish-test` & historique

> Publie sur la Page du **compte actif**, avec credentials de l’**app active**.

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

## 8ter. Orchestration Publications

### Statuts

`PENDING` · `IN_PROGRESS` · `AWAITING_REVIEW` · `SCHEDULED` · `CANCELLED` · `SUCCESS` · `PARTIAL_SUCCESS` · `FAILURE`

Verrou **409** sur `POST /trigger` si un run actif existe (`IN_PROGRESS` | `AWAITING_REVIEW` | `SCHEDULED`). `cancel` libère le verrou.

### `POST /api/publications/trigger`

| | |
|--|--|
| **Auth** | Bearer |
| **Succès** | `200` |

**Body** (optionnel)

| Champ | Type | Requis | Notes |
|-------|------|--------|--------|
| `llmProvider` | `OPENAI` \| `ANTHROPIC` \| `GEMINI` | Non | Sinon fallback `activeLlmProvider` |
| `awaitReview` | `boolean` | Non | `true` = pause `AWAITING_REVIEW` ; `false`/omis = publier LinkedIn |

```json
{ "llmProvider": "GEMINI", "awaitReview": true }
```

**Erreurs** : `400` enum · `409` run actif · `422` clé LLM manquante (FR).

### `POST /api/publications/{id}/confirm`

Body `{ "content": "…" }` (1–3000). Prérequis `AWAITING_REVIEW` sinon **409**. Publie LinkedIn.

### `POST /api/publications/{id}/cancel`

Sans body. `AWAITING_REVIEW` ou `SCHEDULED` → `CANCELLED`. Sinon **409**.

### `POST /api/publications/{id}/regenerate`

Body optionnel `{ "llmProvider": "OPENAI" }`. Relance le LLM (brief frais), reste `AWAITING_REVIEW`. Prérequis `AWAITING_REVIEW`.

### `POST /api/publications/{id}/schedule`

```json
{ "content": "texte final", "publishAt": "2026-07-20T09:00:00Z" }
```

`publishAt` doit être dans le futur. Statut → `SCHEDULED` ; worker (~1 min) publie à l’heure. Annulable via `cancel`.

### `GET /api/publications/readiness`

```json
{
  "ready": true,
  "linkedInConnected": true,
  "linkedInAccountLabel": "Acme Page",
  "organizationUrn": "urn:li:organization:…",
  "activeAppConfigured": true,
  "hasLlmKey": true,
  "reasons": []
}
```

`reasons` possibles : `LINKEDIN_NOT_CONNECTED`, `ORGANIZATION_URN_MISSING`, `NO_ACTIVE_APP`, `NO_LLM_KEY`, `TOKEN_EXPIRED`.

### `GET /api/publications`

Query : `page`, `size`, `status?`, `triggerType?` (`MANUAL`|`SCHEDULED`), `from?`, `to?` (ISO Instant).

Réponse : `{ "publications": [ PublicationResponse ], "total": N }`.

`PublicationResponse` inclut `draftContent`, `llmProvider`, `scheduledPublishAt`, `steps[]`.

### `GET /api/publications/{id}`

Détail + `draftContent` + steps.

### `GET /api/publications/export`

Query : `format=csv|json` (défaut `csv`) + mêmes filtres que la liste.

CSV : `id,triggeredAt,completedAt,triggerType,status,triggeredBy,contentExcerpt,linkedInPostId`.

**Providers (Paramètres)** — déjà en place : clés chiffrées + `*KeyMasked`, modèles, `activeLlmProvider` pour le scheduler auto.

---

## 8quater. Dashboard Accueil — `GET /api/dashboard/overview`

| | |
|--|--|
| **Auth** | Bearer |
| **Cache** | `Cache-Control: no-store` |
| **Succès** | `200` |

Agrège en **1 round-trip** : counts publications, prochaine tick scheduler, prochain one-shot, readiness, LinkedIn, résumé settings, 5 dernières runs.

```json
{
  "fromBackend": true,
  "counts": {
    "total": 42,
    "success": 30,
    "failure": 3,
    "awaitingReview": 2,
    "scheduled": 1,
    "inProgress": 0
  },
  "nextScheduledAt": "2026-07-19T09:00:00Z",
  "nextOneShotAt": "2026-07-18T18:30:00Z",
  "lastPublication": null,
  "recentPublications": [],
  "readiness": { "ready": true, "reasons": [] },
  "linkedIn": {
    "connected": true,
    "accountsCount": 1,
    "activeAccountsCount": 1,
    "appsCount": 2,
    "activeAppsCount": 1,
    "maxApps": 3,
    "activeLabel": "Page"
  },
  "settingsSummary": {
    "companyName": "KOBE",
    "schedulerEnabled": true,
    "publishHourUtc": 9,
    "timezone": "Europe/Paris",
    "activeLlmProvider": "GEMINI",
    "hasAnyLlmKey": true,
    "configuredProvidersCount": 1,
    "configuredProviders": ["GEMINI"]
  }
}
```

| Champ | Règle |
|-------|--------|
| `counts.success` | `SUCCESS` + `PARTIAL_SUCCESS` |
| `nextScheduledAt` | Prochaine tick réelle (`null` si scheduler off) |
| `nextOneShotAt` | Min `scheduledPublishAt` des runs `SCHEDULED` futurs |
| `readiness` | Identique à `GET /api/publications/readiness` |

---

## 9. Checklist intégration frontend

| Écran | Endpoints |
|-------|-----------|
| Login | `POST /api/auth/login` |
| Logout | `POST /api/auth/logout` |
| Guard / header user | `GET /api/auth/me` |
| Profil (édition) | `PUT /api/auth/me` |
| Changement MDP | `POST /api/auth/change-password` |
| Settings org | `GET` + `PATCH` (sections) / `PUT` (compat) `/api/settings` |
| Dropdowns modèles | `GET /api/settings/llm-models` |
| Bouton « Générer un tip » | `POST /api/ai/generate-test` |
| LinkedIn apps | `GET/POST/PUT/DELETE /api/linkedin/apps…` + audit / health / verify |
| LinkedIn résumé / Test | `GET /api/linkedin/summary` |
| LinkedIn compte actif | `GET /api/linkedin/accounts/active` |
| LinkedIn comptes + activate | `GET/POST/PUT/DELETE /api/linkedin/accounts…` |
| Publier (test LinkedIn) | `POST /api/linkedin/publish-test` |
| Historique LinkedIn records | `GET /api/linkedin/publications?limit=` |
| Préflight publications | `GET /api/publications/readiness` |
| Accueil dashboard | `GET /api/dashboard/overview` |
| Orchestration manuelle | `POST /api/publications/trigger` `{ llmProvider?, awaitReview? }` |
| Confirmer / annuler / régénérer / planifier | `POST …/confirm|cancel|regenerate|schedule` |
| Historique filtré | `GET /api/publications?status&triggerType&from&to&page&size` |
| Export | `GET /api/publications/export?format=csv\|json` |
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
| `PUT` | `/api/settings` | Oui | settings complets (§3.3) | `200` |
| `PATCH` | `/api/settings` | Oui | sous-ensemble (§3.4) | `200` / `400` |
| `POST` | `/api/ai/generate-test` | Oui | `{ llmProvider?, topic?, brandTone?, contentLanguage?, useOrgBrief? }` | `200` |
| `GET` | `/api/linkedin/summary` | Oui | — | `200` |
| `GET` | `/api/linkedin/apps` | Oui | — | `200` |
| `POST` | `/api/linkedin/apps` | Oui | `{ label?, clientId, clientSecret }` | `201` / `409` |
| `PUT` | `/api/linkedin/apps/{id}` | Oui | update | `200` / `404` |
| `PUT` | `/api/linkedin/apps/{id}/activate` | Oui | — | `200` |
| `DELETE` | `/api/linkedin/apps/{id}` | Oui | — | `204` |
| `GET` | `/api/linkedin/apps/audit` | Oui | `page`, `size` | `200` |
| `GET` | `/api/linkedin/apps/audit/export` | Oui | — | `200` |
| `GET` | `/api/linkedin/apps/health` | Oui | — | `200` |
| `POST` | `/api/linkedin/apps/{id}/verify` | Oui | — | `200` |
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
| `GET` | `/api/publications/readiness` | Oui | — | `200` |
| `GET` | `/api/dashboard/overview` | Oui | — | `200` (`Cache-Control: no-store`) |
| `GET` | `/api/publications/export` | Oui | `format`, filtres | `200` |
| `POST` | `/api/publications/trigger` | Oui | `{ llmProvider?, awaitReview? }` | `200` / `409` / `422` |
| `POST` | `/api/publications/{id}/confirm` | Oui | `{ content }` | `200` / `400` / `404` / `409` / `422` |
| `POST` | `/api/publications/{id}/cancel` | Oui | — | `200` / `404` / `409` |
| `POST` | `/api/publications/{id}/regenerate` | Oui | `{ llmProvider? }` | `200` / `404` / `409` / `422` |
| `POST` | `/api/publications/{id}/schedule` | Oui | `{ content, publishAt }` | `200` / `400` / `404` / `409` |
| `GET` | `/api/publications` | Oui | `page`, `size`, `status?`, `triggerType?`, `from?`, `to?` | `200` |
| `GET` | `/api/publications/{id}` | Oui | path id | `200` / `404` |
| `GET` | `/api/linkedin/callback` | Non (LinkedIn only) | `code`/`state`/`error` → 302 front | `302` |
| `GET` | `/actuator/health` | Non | — | `200` |

---

*Document frontend — synchronisé avec l’API backend LinkedIn Agent.*
