# API Frontend — LinkedIn Agent

Documentation destinée au frontend pour brancher / mettre à jour les appels HTTP vers le backend.

**Base URL (dev)** : `http://localhost:8080`  
**Content-Type** : `application/json` (sauf désabonnement public HTML)  
**Auth** : JWT Bearer sur toutes les routes sauf celles marquées **Public**

> Dernière synchro : alignée sur les controllers fonctionnels (`auth`, `settings`, `subscribers`, `ai`, `public`, `actuator/health`).

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
| `409` | Conflit (abonné déjà actif) |
| `422` | Clé API manquante pour le provider IA |
| `204` | Succès sans body (DELETE abonné) |

### Enums

**`LlmProvider`**
```text
OPENAI | ANTHROPIC | GEMINI
```

**`SubscriberStatus`**
```text
ACTIVE | UNSUBSCRIBED
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
| `email` | `string` |
| `role` | `string` (ex. `ADMIN`) |

```json
{
  "email": "admin@example.com",
  "role": "ADMIN"
}
```

---

### 2.3 Logout

| | |
|--|--|
| **Méthode** | `POST` |
| **URL** | `/api/auth/logout` |
| **Auth** | Bearer |
| **Succès** | `204` |

Révoque la session courante (le JWT ne fonctionne plus). Appeler avant de supprimer le token côté front.

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
| `contentTopic` | `string` | Sujet des tips LinkedIn |
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
| `linkedInRedirectUri` | `string \| null` | Sinon défaut backend |
| `linkedInOauthScopes` | `string \| null` | Sinon défaut backend |
| `activeLinkedInAccountId` | `string \| null` | Compte utilisé pour publier (max 3) |
| `schedulerEnabled` | `boolean` | |
| `updatedAt` | `string` (ISO-8601) | |

```json
{
  "companyName": "KOBE Corporation",
  "contentTopic": "fintech tips",
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
  "linkedInRedirectUri": "http://localhost:8080/api/linkedin/callback",
  "linkedInOauthScopes": "w_organization_social r_organization_social",
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
| `companyName` | `string` | Oui | Non vide |
| `contentTopic` | `string` | Oui | Non vide |
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
| `linkedInRedirectUri` | `string \| null` | Non | `null` = conserver ; `""` = défaut backend |
| `linkedInOauthScopes` | `string \| null` | Non | `null` = conserver ; `""` = défaut backend |
| `activeLinkedInAccountId` | `string \| null` | Non | `null` = conserver ; `""` = aucun ; id = compte actif (**400** si inconnu) |
| `schedulerEnabled` | `boolean \| null` | Non | `null` = conserver |

```json
{
  "companyName": "KOBE Corporation",
  "contentTopic": "fintech tips",
  "publishHourUtc": 9,
  "timezone": "Africa/Douala",
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

## 4. Abonnés — `/api/subscribers`

### 4.1 Créer / réactiver un abonné

| | |
|--|--|
| **Méthode** | `POST` |
| **URL** | `/api/subscribers` |
| **Auth** | Bearer |
| **Succès** | `201` |

**Body**

| Champ | Type | Requis | Contraintes |
|-------|------|--------|-------------|
| `email` | `string` | Oui | Email valide |
| `fullName` | `string \| null` | Non | |

```json
{
  "email": "user@example.com",
  "fullName": "Jane Doe"
}
```

**Réponse `201`**

| Champ | Type |
|-------|------|
| `id` | `string` |
| `email` | `string` |
| `fullName` | `string \| null` |
| `status` | `SubscriberStatus` |
| `subscribedAt` | `string` (ISO-8601) |
| `unsubscribedAt` | `string \| null` |

**Erreurs** : `409` — email déjà `ACTIVE`

---

### 4.2 Lister les abonnés

| | |
|--|--|
| **Méthode** | `GET` |
| **URL** | `/api/subscribers` |
| **Auth** | Bearer |
| **Succès** | `200` |

**Query params**

| Param | Type | Requis | Description |
|-------|------|--------|-------------|
| `status` | `ACTIVE` \| `UNSUBSCRIBED` | Non | Filtre ; omis = tous |

Exemples :
- `GET /api/subscribers`
- `GET /api/subscribers?status=ACTIVE`
- `GET /api/subscribers?status=UNSUBSCRIBED`

**Réponse `200`**

| Champ | Type | Description |
|-------|------|-------------|
| `subscribers` | `SubscriberResponse[]` | Liste |
| `totalActive` | `number` | Nombre d’abonnés `ACTIVE` (global) |

```json
{
  "subscribers": [
    {
      "id": "665f1a2b3c4d5e6f7a8b9c0d",
      "email": "user@example.com",
      "fullName": "Jane Doe",
      "status": "ACTIVE",
      "subscribedAt": "2026-07-17T10:00:00Z",
      "unsubscribedAt": null
    }
  ],
  "totalActive": 1
}
```

---

### 4.3 Supprimer un abonné

| | |
|--|--|
| **Méthode** | `DELETE` |
| **URL** | `/api/subscribers/{id}` |
| **Auth** | Bearer |
| **Succès** | `204` (pas de body) |

**Path params**

| Param | Type | Description |
|-------|------|-------------|
| `id` | `string` | ID Mongo de l’abonné |

Exemple : `DELETE /api/subscribers/665f1a2b3c4d5e6f7a8b9c0d`

---

## 5. Désabonnement public — `/api/public`

### 5.1 Page de désabonnement

| | |
|--|--|
| **Méthode** | `GET` |
| **URL** | `/api/public/unsubscribe/{token}` |
| **Auth** | Public |
| **Réponse** | HTML (`text/html`) — page Thymeleaf |

**Path params**

| Param | Type | Description |
|-------|------|-------------|
| `token` | `string` | `unsubscribeToken` de l’abonné |

Pas d’usage JSON côté SPA admin : lien mail → navigateur.

---

## 6. Génération IA — `/api/ai`

### 6.1 Générer un tip de test

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
| **App (org)** | Client ID / Secret / redirect / scopes | `PUT /api/settings` (secret chiffré, jamais en clair) |
| **Compte** | OAuth token + URN Page (+ métadonnées Page) | `/api/linkedin/accounts*` |
| **Actif** | Compte utilisé pour publier | `activeLinkedInAccountId` (settings **ou** `PUT …/activate`) |

Auth Bearer sur toutes les routes ci-dessous sauf `/api/linkedin/callback` (public).

### 8.0 Credentials app (Settings)

```json
{
  "linkedInClientId": "78xxxxxxxxxxxx",
  "linkedInClientSecret": "WPL_AP1....",
  "linkedInRedirectUri": "https://agent-rev-api.kobecorporation.com/api/linkedin/callback",
  "linkedInOauthScopes": "w_organization_social r_organization_social",
  "activeLinkedInAccountId": "mongoObjectIdDuCompteActif"
}
```

`GET /api/settings` → `linkedInClientId`, `linkedInClientSecretMasked`, … (secret jamais en clair).

### 8.1 Endpoints comptes / résumé

| Méthode | Route | Succès | Erreurs |
|---------|-------|--------|---------|
| `GET` | `/api/linkedin/summary` | `200` | `401` |
| `GET` | `/api/linkedin/accounts` | `200` | `401` |
| `GET` | `/api/linkedin/accounts/active` | `200` | `401`, **`404`** si aucun actif |
| `POST` | `/api/linkedin/accounts/connect?label=` | `200` | `401`, **`409`** si max 3, `422` si credentials absents |
| `GET` | `/api/linkedin/accounts/{id}` | `200` | `401`, `404` |
| `GET` | `/api/linkedin/accounts/{id}/auth-url` | `200` | `401`, `404` |
| `PUT` | `/api/linkedin/accounts/{id}/activate` | `200` liste | `401`, `404` |
| `PUT` | `/api/linkedin/accounts/{id}` | `200` | `401`, `404` |
| `PUT` | `/api/linkedin/accounts/{id}/organization-urn` | `200` | `401`, `404`, `400` |
| `DELETE` | `/api/linkedin/accounts/{id}` | `204` | `401`, `404` |

**Switch actif recommandé** : `PUT /api/linkedin/accounts/{id}/activate` (pas besoin de renvoyer tout le body settings).

Compat legacy : `GET /auth-url`, `GET /status`, `PUT /organization-urn`, `DELETE /disconnect`.

#### `GET /api/linkedin/accounts/active`

Compte explicitement sélectionné — **pas** de fallback « premier connecté ».

```json
{
  "id": "665f…",
  "label": "Page Prod",
  "status": "CONNECTED",
  "linkedInUserUrn": "urn:li:person:…",
  "linkedInOrganizationUrn": "urn:li:organization:123",
  "connectedAt": "2026-07-17T12:00:00Z",
  "lastError": null,
  "isActive": true,
  "organizationName": "KOBE Corporation",
  "organizationLogoUrl": "https://…",
  "organizationVanityName": "kobe-corporation"
}
```

`organizationName` / `logoUrl` / `vanityName` : best-effort au `PUT …/organization-urn` via Organizations API ; sinon `null` (front = label + pageId).

**Scopes pour name/logo Page** : Community Management + `r_organization_social` (lecture org). Sans scopes → métadonnées `null`, publication possible quand même.

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

Réponse = `LinkedInAccountListResponse`.

```ts
await fetch(`${API_BASE}/api/linkedin/accounts/${id}/activate`, {
  method: "PUT",
  headers: { Authorization: `Bearer ${token}` },
});
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
  isActive: boolean;
  organizationName: string | null;
  organizationLogoUrl: string | null;
  organizationVanityName: string | null;
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

## 9. Checklist intégration frontend

| Écran | Endpoints |
|-------|-----------|
| Login | `POST /api/auth/login` |
| Logout | `POST /api/auth/logout` |
| Guard / header user | `GET /api/auth/me` |
| Settings org (+ LinkedIn app) | `GET` + `PUT /api/settings` |
| Dropdowns modèles | `GET /api/settings/llm-models` |
| Liste abonnés + filtre | `GET /api/subscribers?status=` |
| Ajout abonné | `POST /api/subscribers` |
| Suppression | `DELETE /api/subscribers/{id}` |
| Bouton « Générer un tip » | `POST /api/ai/generate-test` |
| LinkedIn résumé / Test | `GET /api/linkedin/summary` |
| LinkedIn compte actif | `GET /api/linkedin/accounts/active` |
| LinkedIn comptes + activate | `GET/POST/PUT/DELETE /api/linkedin/accounts…` |
| Publier (test LinkedIn) | `POST /api/linkedin/publish-test` |
| Historique LinkedIn records | `GET /api/linkedin/publications?limit=` |
| Orchestration manuelle | `POST /api/publications/trigger` |
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
| `GET` | `/api/settings` | Oui | — | `200` |
| `GET` | `/api/settings/llm-models` | Oui | — | `200` |
| `PUT` | `/api/settings` | Oui | settings (§3.3) | `200` |
| `POST` | `/api/subscribers` | Oui | `{ email, fullName? }` | `201` |
| `GET` | `/api/subscribers` | Oui | query `status?` | `200` |
| `DELETE` | `/api/subscribers/{id}` | Oui | path `id` | `204` |
| `GET` | `/api/public/unsubscribe/{token}` | Non | path `token` → HTML | `200` |
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
| `POST` | `/api/publications/trigger` | Oui | — | `200` / `409` |
| `GET` | `/api/publications` | Oui | `page`, `size` | `200` |
| `GET` | `/api/publications/{id}` | Oui | path id | `200` / `404` |
| `GET` | `/api/linkedin/callback` | Non (LinkedIn only) | `code`/`state`/`error` → 302 front | `302` |
| `GET` | `/actuator/health` | Non | — | `200` |

---

*Document frontend — synchronisé avec l’API backend LinkedIn Agent.*
