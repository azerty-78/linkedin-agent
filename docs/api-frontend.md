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
3. Si `401` → rediriger vers login (token absent / invalide / expiré)

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
| `linkedInOrganizationId` | `string \| null` | |
| `schedulerEnabled` | `boolean` | **Lecture seule** (pas dans le PUT) |
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
  "schedulerEnabled": false,
  "updatedAt": "2026-07-17T11:00:00Z"
}
```

**UI** : une clé masquée non vide = clé déjà configurée (ne jamais préremplir le champ clé avec la valeur masquée).

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
  "linkedInOrganizationId": null
}
```

**Réponse `200`** : même shape que `GET /api/settings`.

**Règles UI**
- Ne jamais préremplir les champs clé avec la valeur masquée.
- Champ clé vide au submit → envoyer `null` (ou omettre) pour ne pas écraser.
- `schedulerEnabled` n’est **pas** modifiable via cette API.

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

## 8. LinkedIn OAuth2 — `/api/linkedin` (connexion uniquement)

> **Pas de publication UGC pour l’instant** — seulement connecter le compte, stocker le token chiffré côté backend, saisir l’URN de Page.

### 8.1 Flow à implémenter (important)

```
[Dashboard LinkedIn]
    │
    ├─ mount → GET /api/linkedin/status  (JWT)
    │
    ├─ clic « Connecter LinkedIn »
    │     → GET /api/linkedin/auth-url  (JWT)
    │     → window.location.href = authorizationUrl
    │       (ou window.open en popup — voir §8.6)
    │
    │   ┌─ navigateur → LinkedIn (consentement)
    │   └─ LinkedIn → GET /api/linkedin/callback  (PUBLIC, sans JWT)
    │         → backend échange code + stocke token
    │         → 302 → {FRONTEND_URL}/dashboard/linkedin?connected=true
    │              ou ?connected=false&error=...
    │
    └─ page /dashboard/linkedin lit les query params
          → toast succès / erreur
          → GET /api/linkedin/status pour rafraîchir l’UI
          → nettoyer l’URL (replaceState) pour éviter un toast au refresh
```

**Le frontend ne voit jamais** `code`, `client_secret` ni `access_token`. Le callback est 100 % backend.

### 8.2 Types TypeScript

```ts
type LinkedInConnectionStatus =
  | "NOT_CONNECTED"
  | "CONNECTED"
  | "EXPIRED"
  | "ERROR";

type LinkedInStatusResponse = {
  status: LinkedInConnectionStatus;
  linkedInUserUrn: string | null;
  linkedInOrganizationUrn: string | null;
  connectedAt: string | null; // ISO-8601
  lastError: string | null;
};

type LinkedInAuthUrlResponse = {
  authorizationUrl: string;
};
```

### 8.3 Endpoints

#### `GET /api/linkedin/status`

| | |
|--|--|
| **Auth** | JWT obligatoire |
| **Succès** | `200` → `LinkedInStatusResponse` |

Afficher selon `status` :
- `NOT_CONNECTED` → bouton « Connecter LinkedIn »
- `CONNECTED` → badge vert + URN user + formulaire Page URN + bouton Déconnecter
- `EXPIRED` / `ERROR` → message + `lastError` + bouton « Reconnecter »

#### `GET /api/linkedin/auth-url`

| | |
|--|--|
| **Auth** | JWT obligatoire |
| **Succès** | `200` → `{ "authorizationUrl": "https://www.linkedin.com/oauth/v2/authorization?..." }` |

```ts
async function connectLinkedIn(token: string) {
  const res = await fetch(`${API_BASE}/api/linkedin/auth-url`, {
    headers: { Authorization: `Bearer ${token}` },
  });
  if (!res.ok) throw new Error(await res.text());
  const { authorizationUrl } = await res.json();
  // Redirection pleine page (recommandé en local) :
  window.location.assign(authorizationUrl);
}
```

#### `PUT /api/linkedin/organization-urn`

| | |
|--|--|
| **Auth** | JWT |
| **Body** | `{ "organizationUrn": "urn:li:organization:123456789" }` |
| **Succès** | `200` → `LinkedInStatusResponse` |
| **Erreur** | `400` si l’URN ne commence pas par `urn:li:organization:` |

```ts
// Validation UI avant envoi
const URN_RE = /^urn:li:organization:.+/;
if (!URN_RE.test(value.trim())) {
  setError("Doit commencer par urn:li:organization:");
  return;
}
```

L’admin récupère l’ID Page dans l’URL admin LinkedIn  
(`https://www.linkedin.com/company/XXXXX/admin/...` → `urn:li:organization:XXXXX`).

#### `DELETE /api/linkedin/disconnect`

| | |
|--|--|
| **Auth** | JWT |
| **Succès** | `204` (pas de body) |

Puis rappeler `GET /status` → doit être `NOT_CONNECTED`.

#### `GET /api/linkedin/callback` — **ne pas appeler depuis le front**

Public. LinkedIn y redirige le navigateur. Le backend renvoie un **302** vers :

| Résultat | URL |
|----------|-----|
| OK | `{FRONTEND_URL}/dashboard/linkedin?connected=true` |
| KO / refus | `{FRONTEND_URL}/dashboard/linkedin?connected=false&error=...` |

`FRONTEND_URL` côté backend = `http://localhost:3000` en local (variable `FRONTEND_URL` / `app.frontend-url`).

### 8.4 Page obligatoire : `/dashboard/linkedin`

Au mount :

```ts
useEffect(() => {
  const params = new URLSearchParams(window.location.search);
  const connected = params.get("connected");
  const error = params.get("error");

  if (connected === "true") {
    toast.success("Compte LinkedIn connecté");
  } else if (connected === "false") {
    toast.error(error ? decodeURIComponent(error) : "Connexion LinkedIn refusée");
  }

  if (connected !== null) {
    // Évite de re-afficher le toast au F5
    window.history.replaceState({}, "", "/dashboard/linkedin");
  }

  void refreshStatus(); // GET /api/linkedin/status
}, []);
```

Cas d’erreur fréquents dans `error` :
- `user_cancelled_login` / `access_denied` → l’utilisateur a annulé
- `missing_code_or_state` / messages OAuth → problème technique

### 8.5 UI suggérée (états)

| Zone | Contenu |
|------|---------|
| Header | Titre « LinkedIn » + badge statut |
| Non connecté | Texte court + CTA « Connecter avec LinkedIn » |
| Connecté | `linkedInUserUrn`, date `connectedAt`, champ URN Page + Enregistrer, CTA Déconnecter |
| Erreur | Afficher `lastError` + CTA Reconnecter |

**Ne jamais** afficher ni stocker de token LinkedIn côté front.

### 8.6 Popup vs redirection pleine page

| Approche | Avantage | Inconvénient |
|----------|----------|--------------|
| `window.location.assign(url)` | Simple, fiable en local | Quitte le dashboard pendant le consent |
| `window.open(url, "linkedin-oauth", "popup")` | Garde le dashboard ouvert | Faut écouter le retour (polling `status` ou `postMessage`) — plus complexe |

**Recommandation MVP :** redirection pleine page. Après retour sur `/dashboard/linkedin?connected=...`, l’utilisateur est déjà sur la bonne page.

Si popup plus tard : après redirect backend vers le front, la page popup lit `connected` et fait `window.opener.postMessage(...)` puis `window.close()` ; le parent écoute et rafraîchit `status`.

### 8.7 Checklist frontend LinkedIn

- [ ] Route `/dashboard/linkedin` existe (même sans design final)
- [ ] `FRONTEND_URL` backend = origine du front (`http://localhost:3000`)
- [ ] Connexion : `auth-url` → redirect LinkedIn
- [ ] Retour : lire `connected` / `error`, toast, `replaceState`, `GET status`
- [ ] Formulaire URN Page avec validation `urn:li:organization:`
- [ ] Déconnexion : `DELETE` + refresh status
- [ ] Aucun appel direct à `/api/linkedin/callback`

### 8.8 Scopes actuels (backend)

L’URL d’auth demande : `openid profile email w_organization_social`  
(`w_organization_social` = publication **en tant que la Page** — produit Community Management / permissions org).  
Le profil perso n’est **pas** la cible de publication.

---

## 8bis. Publication LinkedIn — `/api/linkedin/publish-test` & historique

> Publie **uniquement sur la Page entreprise** (`linkedInOrganizationUrn`).  
> Pas de post sur le compte perso, pas de repost automatique.

**Prérequis front :**
1. Connexion OAuth admin (`CONNECTED`)
2. `PUT /api/linkedin/organization-urn` avec `urn:li:organization:{idPage}`
3. Compte LinkedIn = admin de cette Page
4. Scope `w_organization_social` visible dans le portail + reconnexion après activation

Si URN Page manquant → `422` avec message clair.

### `POST /api/linkedin/publish-test` (JWT, temporaire)

| Body | Comportement |
|------|----------------|
| `{}` ou omis | Génère via IA (`generateDailyTip`) puis publie |
| `{ "content": "Mon texte…" }` | Publie ce texte tel quel |

**Réponse `200`** (même si LinkedIn a échoué HTTP — alors `status: "FAILED"`) :

```json
{
  "id": "...",
  "content": "...",
  "aiProvider": "GEMINI",
  "targetUrn": "urn:li:person:…",
  "linkedInPostId": "urn:li:share:…",
  "status": "PUBLISHED",
  "errorMessage": null,
  "createdAt": "…",
  "publishedAt": "…"
}
```

`status` : `PENDING` | `PUBLISHED` | `FAILED`

**`422`** si LinkedIn non connecté / token expiré (reconnecter via auth-url).

### `GET /api/linkedin/publications?limit=20` (JWT)

Liste triée du plus récent au plus ancien (max 100).

### UI suggérée

1. Vérifier `GET /api/linkedin/status` → `CONNECTED`
2. Bouton « Publier un tip test » → `POST /publish-test`
3. Afficher badge selon `status` + `linkedInPostId` / `errorMessage`
4. Écran historique → `GET /publications`

---

## 9. Checklist intégration frontend

| Écran | Endpoints |
|-------|-----------|
| Login | `POST /api/auth/login` |
| Guard / header user | `GET /api/auth/me` |
| Settings org | `GET` + `PUT /api/settings` |
| Dropdowns modèles | `GET /api/settings/llm-models` |
| Liste abonnés + filtre | `GET /api/subscribers?status=` |
| Ajout abonné | `POST /api/subscribers` |
| Suppression | `DELETE /api/subscribers/{id}` |
| Bouton « Générer un tip » | `POST /api/ai/generate-test` |
| Connexion LinkedIn | `GET /api/linkedin/auth-url` + page `/dashboard/linkedin` |
| Statut LinkedIn | `GET /api/linkedin/status` |
| URN Page | `PUT /api/linkedin/organization-urn` |
| Déconnexion LinkedIn | `DELETE /api/linkedin/disconnect` |
| Publier (test LinkedIn) | `POST /api/linkedin/publish-test` |
| Historique LinkedIn records | `GET /api/linkedin/publications?limit=` |
| Orchestration manuelle | `POST /api/publications/trigger` |
| Historique orchestration | `GET /api/publications?page=&size=` |
| Détail run | `GET /api/publications/{id}` |

### Exemple flux settings + génération (tests gratuits)

1. `GET /api/settings/llm-models` → options Gemini free
2. `PUT /api/settings` avec `activeLlmProvider: "GEMINI"`, `geminiApiKey: "…"` (le modèle UI peut être premium ; le test n’en tient pas compte)
3. `POST /api/ai/generate-test` → utilise `gemini-3.1-flash-lite` + ~80 tokens

---

## 10. Récap URLs (toutes les routes fonctionnelles)

| Méthode | URL | Auth | Body / params | Succès |
|---------|-----|------|---------------|--------|
| `POST` | `/api/auth/login` | Non | `{ email, password }` | `200` |
| `GET` | `/api/auth/me` | Oui | — | `200` |
| `GET` | `/api/settings` | Oui | — | `200` |
| `GET` | `/api/settings/llm-models` | Oui | — | `200` |
| `PUT` | `/api/settings` | Oui | settings (§3.3) | `200` |
| `POST` | `/api/subscribers` | Oui | `{ email, fullName? }` | `201` |
| `GET` | `/api/subscribers` | Oui | query `status?` | `200` |
| `DELETE` | `/api/subscribers/{id}` | Oui | path `id` | `204` |
| `GET` | `/api/public/unsubscribe/{token}` | Non | path `token` → HTML | `200` |
| `POST` | `/api/ai/generate-test` | Oui | — | `200` |
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
