# Fonctionnalités, rôles et sécurité

Document de référence pour le backend **LinkedIn Agent** (`com.revolut.agent`).

---

## 1. Vue d’ensemble

L’application expose une API REST réactive (WebFlux) pour :

1. Authentifier un administrateur unique (JWT)
2. Configurer l’organisation et les clés LLM (chiffrées)
3. Gérer la liste d’abonnés newsletter
4. Générer un contenu LinkedIn (tip) via le provider LLM actif
5. Permettre un désabonnement public sans authentification

État actuel : **mono-admin** (un seul rôle applicatif). Les phases LinkedIn publish / scheduler / envoi mail réel peuvent être absentes ou partielles.

---

## 2. Rôles et permissions

### 2.1 Rôle applicatif

| Rôle | Enum | Authority Spring Security | Description |
|------|------|---------------------------|-------------|
| Administrateur | `AppRole.ADMIN` | `ROLE_ADMIN` | Seul rôle défini ; accès à toutes les routes authentifiées |

Fichier : `model/user/AppRole.kt`

### 2.2 Modèle de permissions (état actuel)

Il n’y a **pas** de matrice fine `hasRole` / `hasAuthority` par endpoint.

| Règle | Comportement |
|-------|--------------|
| Route publique | Accès sans JWT |
| Route protégée | JWT valide requis (`anyExchange().authenticated()`) |
| Après authentification | Accès complet aux APIs métier (équivalent admin) |

Le filtre JWT pose systématiquement l’authority `ROLE_ADMIN` à partir du claim `role` du token.

Fichier : `config/SecurityConfig.kt`, `config/JwtAuthenticationWebFilter.kt`

### 2.3 Routes publiques vs protégées

| Pattern | Accès |
|---------|--------|
| `POST /api/auth/login` | Public |
| `/api/public/**` | Public |
| `/actuator/health/**` | Public |
| Tout le reste (API + autres actuators exposés) | Authentifié |

### 2.4 Bootstrap du compte admin

Au démarrage (`AdminBootstrapRunner`) :

- Si la collection MongoDB `app_users` est **vide** → création d’un `AppUser` avec `role = ADMIN`
- Email / mot de passe issus de `app.bootstrap.admin-email` / `app.bootstrap.admin-password`  
  (variables d’env : `ADMIN_EMAIL`, `ADMIN_PASSWORD`)
- Mot de passe stocké en **BCrypt**
- Si des utilisateurs existent déjà → **aucun** nouveau compte n’est créé

Pour forcer un nouvel admin : vider `app_users` puis redémarrer l’application.

---

## 3. Authentification JWT

### 3.1 Flux

```
Client                     API                         MongoDB
  |                         |                             |
  |-- POST /api/auth/login ->|                             |
  |                         |-- findByEmail ------------->|
  |                         |-- BCrypt check              |
  |                         |-- JwtService (access+refresh)
  |<- tokens ---------------|                             |
  |                         |                             |
  |-- Authorization: Bearer accessToken ----------------->|
  |                         |-- JwtAuthenticationWebFilter|
  |                         |-- SecurityContext (ROLE_ADMIN)
```

### 3.2 Tokens

| Token | TTL | Usage |
|-------|-----|--------|
| Access | `jwt.expiration` (défaut 24 h) | Header `Authorization: Bearer …` |
| Refresh | 7 jours (hardcodé) | Émis au login ; **pas d’endpoint refresh** pour l’instant |

Claims typiques : `sub` (email), `role`, `type` (`access` | `refresh`). Algorithme : HS256 (`jwt.secret`).

### 3.3 Endpoints auth

| Méthode | Chemin | Auth | Description |
|---------|--------|------|-------------|
| `POST` | `/api/auth/login` | Non | Body `{ "email", "password" }` → tokens |
| `GET` | `/api/auth/me` | Oui | Email + rôle du principal |

Erreurs auth classiques → HTTP **401** (`BadCredentialsException` / `AuthenticationException`).

---

## 4. Catalogue des features

### 4.1 Paramètres organisation (`/api/settings`)

| Méthode | Chemin | Auth | Description |
|---------|--------|------|-------------|
| `GET` | `/api/settings` | Oui | Lecture (clés API **masquées**) |
| `PUT` | `/api/settings` | Oui | Mise à jour partielle / complète |

Champs métier typiques :

- Identité : `companyName`, `contentTopic`
- Planning : `publishHourUtc`, `timezone`, `schedulerEnabled`
- LLM : `activeLlmProvider` (`OPENAI` | `ANTHROPIC` | `GEMINI`)
- Modèles par provider (style n8n) : `openAiModel`, `anthropicModel`, `geminiModel`
- Clés API chiffrées en base ; jamais renvoyées en clair
- LinkedIn org id (préparation intégration)

Catalogue des modèles proposés (pour UI / dropdown) :

| Méthode | Chemin | Auth | Description |
|---------|--------|------|-------------|
| `GET` | `/api/settings/llm-models` | Oui | Liste des modèles + flags `freeTier` / `recommendedForTests` |

Défauts recommandés pour les **tests** (éviter la facturation) :
- Provider : `GEMINI`
- Modèle : `gemini-2.0-flash` (free tier Google AI Studio)
- Fallbacks properties : `gpt-4o-mini`, `claude-haiku-4-5`, `gemini-2.0-flash`

**Chiffrement** : AES-256-GCM (`EncryptionService`), clé `ENCRYPTION_KEY` (Base64, 32 octets).  
Sans clé en dev : génération éphémère + warning (les données chiffrées ne survivent pas au redémarrage).

### 4.2 Abonnés newsletter (`/api/subscribers`)

| Méthode | Chemin | Auth | Description |
|---------|--------|------|-------------|
| `POST` | `/api/subscribers` | Oui | Créer ou **réactiver** un email désinscrit |
| `GET` | `/api/subscribers?status=` | Oui | Liste (`ACTIVE` / `UNSUBSCRIBED`) |
| `DELETE` | `/api/subscribers/{id}` | Oui | Suppression (`204`) |
| `GET` | `/api/public/unsubscribe/{token}` | Non | Page HTML Thymeleaf de confirmation |

Statuts : `SubscriberStatus.ACTIVE` | `UNSUBSCRIBED`.  
Conflit si email déjà actif → HTTP **409** (`SubscriberAlreadyActiveException`).  
Chaque abonné a un `unsubscribeToken` UUID unique.

### 4.3 Génération IA (`/api/ai`)

| Méthode | Chemin | Auth | Description |
|---------|--------|------|-------------|
| `POST` | `/api/ai/generate-test` | Oui | Génère un tip LinkedIn (endpoint de test) |

Comportement :

1. Lit les settings (provider actif + modèle choisi + topic)
2. Déchiffre la clé du provider
3. Construit le prompt (`PromptBuilderService`)
4. Délègue au générateur Strategy avec le modèle sélectionné
5. Retourne contenu + provider + `model` + `generatedAt` + prompt utilisé

| Provider | État | Défaut test |
|----------|------|-------------|
| OpenAI | Implémenté | `gpt-4o-mini` (économique, pas free) |
| Anthropic | Implémenté | `claude-haiku-4-5` (économique) |
| Gemini | Implémenté (API Google AI Studio) | `gemini-2.0-flash` (**free tier**) |

Clé manquante pour le provider → HTTP **422** avec message du type  
`No API key configured for provider …, please configure it in /api/settings`.

Les clés runtime viennent de Mongo (chiffrées), **pas** des placeholders `spring.ai.*.api-key`.

### 4.4 Observabilité & logs

- `RequestLoggingWebFilter` : requestId (`X-Request-Id`), méthode, path, query sanitisée, IP, user-agent, principal, rôles, status, durée
- Niveaux : INFO (succès), WARN (4xx), ERROR (5xx / exception)
- Actuator exposé : `health`, `info`, `metrics`, `scheduledtasks`  
  (seul `health/**` est public)

### 4.5 Fonctionnalités prévues / non finalisées

| Domaine | Statut |
|---------|--------|
| Publication LinkedIn | Config préparée (`linkedin.*`), pas d’API métier complète |
| Scheduler de publication | Pool configuré ; logique métier à brancher |
| Envoi mail SMTP réel | Starter Mail présent ; health mail désactivé en dev |
| Refresh token endpoint | Token émis, pas encore d’API dédiée |
| Endpoint AI de test | Temporaire — à retirer / protéger avant prod |

---

## 5. Référence API complète

| Méthode | Chemin | Auth | Code succès typique |
|---------|--------|------|---------------------|
| `POST` | `/api/auth/login` | Non | 200 |
| `GET` | `/api/auth/me` | Oui | 200 |
| `GET` | `/api/settings` | Oui | 200 |
| `GET` | `/api/settings/llm-models` | Oui | 200 |
| `PUT` | `/api/settings` | Oui | 200 |
| `POST` | `/api/subscribers` | Oui | 201 |
| `GET` | `/api/subscribers` | Oui | 200 |
| `DELETE` | `/api/subscribers/{id}` | Oui | 204 |
| `GET` | `/api/public/unsubscribe/{token}` | Non | 200 (HTML) |
| `POST` | `/api/ai/generate-test` | Oui | 200 |
| `GET` | `/actuator/health` | Non | 200 |

Header obligatoire sur les routes protégées :

```http
Authorization: Bearer <accessToken>
```

---

## 6. Modèle de données (MongoDB)

Base : `linkedin_agent_db`

| Collection | Modèle | Points clés |
|------------|--------|-------------|
| `app_users` | `AppUser` | `email` unique, `passwordHash`, `role`, `lastLoginAt` |
| `organization_settings` | `OrganizationSettings` | provider actif, clés chiffrées, topic, timezone |
| `subscribers` | `Subscriber` | `email` unique, `status`, `unsubscribeToken` unique |

Index auto : `spring.data.mongodb.auto-index-creation=true`.

---

## 7. Structure des packages

```
com.revolut.agent/
├── config/
├── controller/
│   ├── auth/
│   ├── settings/
│   ├── subscriber/
│   └── ai/
├── dto/          (auth | settings | subscriber | ai)
├── exception/    (ai | subscriber + GlobalExceptionHandler)
├── model/        (user | settings | subscriber)
├── repository/   (user | settings | subscriber)
└── service/      (auth | settings | subscriber | ai)
```

Organisation par **domaine métier** (pas seulement par couche technique).

---

## 8. Variables d’environnement

| Variable | Usage | Prod |
|----------|--------|------|
| `MONGODB_URI` | Connexion Mongo | Obligatoire |
| `JWT_SECRET` | Signature JWT (≥ 32 car.) | Obligatoire |
| `ADMIN_EMAIL` / `ADMIN_PASSWORD` | Bootstrap admin | Obligatoire si base vide |
| `ENCRYPTION_KEY` | AES-256 (Base64 32 bytes) | Obligatoire |
| `MAIL_HOST` / `MAIL_USERNAME` / `MAIL_PASSWORD` | SMTP | Obligatoire en prod |
| `ANTHROPIC_API_KEY` / `OPENAI_API_KEY` | Placeholders auto-config Spring AI | Présents ; runtime via settings |
| `LINKEDIN_*` | Préparation API LinkedIn | Optionnel |

Référence locale : `setup/.env` (ne pas committer de secrets réels sur un dépôt public).

---

## 9. Codes d’erreur métier utiles

| Situation | HTTP | Source |
|-----------|------|--------|
| Identifiants invalides / JWT manquant | 401 | Security / Auth |
| Accès refusé | 403 | Security |
| Abonné déjà actif | 409 | `SubscriberAlreadyActiveException` |
| Clé LLM absente | 422 | `AiGenerationException` |
| Échec génération LLM (autre) | 400 | `AiGenerationException` |

---

## 10. Checklist sécurité (prod)

- [ ] `spring.profiles.active=prod`
- [ ] Secrets uniquement via variables d’environnement (pas de defaults sensibles)
- [ ] `ENCRYPTION_KEY` stable et sauvegardée
- [ ] `JWT_SECRET` fort et unique
- [ ] Compte admin non trivial ; rotation du mot de passe
- [ ] Retirer ou restreindre `POST /api/ai/generate-test`
- [ ] Ne pas exposer Actuator au-delà de `health` si non nécessaire
- [ ] HTTPS devant l’API (reverse proxy)

---

*Dernière mise à jour : documentation alignée sur la structure par domaines (auth, settings, subscriber, ai, user) et le modèle de sécurité mono-admin JWT.*
