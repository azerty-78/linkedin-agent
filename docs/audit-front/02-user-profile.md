# Audit — Étape 2 : Users + Profil (AUTH)

**Date** : 2026-07-18  
**Statut** : ✅ Code + docs + compile OK — validation humaine en attente  
**Fichier source** : `front-agent/docs/for-backend/backend-user-profile.md`

---

## Ce qui a changé

### Modèle `AppUser` (`app_users`)
- Ajout `username` (unique), `updatedAt`
- Conservés : `email`, `passwordHash`, `role`, `createdAt`, `lastLoginAt`

### Bootstrap (`AdminBootstrapRunner`)
- Create-if-missing sur `role=ADMIN` (ou email seed)
- **Ne réécrit jamais** MDP / email / username si l’admin existe
- Backfill `username` pour documents anciens (sans toucher au MDP)
- Seed depuis `BootstrapProperties.kt` (`adminEmail` / `adminPassword` / `adminUsername`) — pas via `.env`

### API
| Endpoint | Comportement |
|----------|----------------|
| `GET /api/auth/me` | `id`, `email`, `username`, `role`, `createdAt`, `updatedAt` (depuis la DB) |
| `PUT /api/auth/me` | Update username + email ; 409 si conflit |
| `POST /api/auth/change-password` | Vérifie MDP actuel → hash BCrypt → `204` ; révoque les **autres** sessions |
| `POST /api/auth/login` | Toujours contre la DB (inchangé, email normalisé lowercase) |

### JWT / sessions
- Claim `uid` ajouté pour résoudre le user après changement d’email
- `renameUserEmail` sur sessions si email profil changé

### Docs
- `docs/api-frontend.md`, `docs/fonctionnalites-et-securite.md`, `setup/.env.prod.example` (sans `ADMIN_*`)

---

## Ce qui marche

- **Compile Kotlin** : `BUILD SUCCESSFUL`
- Login = table `app_users` uniquement (pas de comparaison aux valeurs de `BootstrapProperties` au login)
- Seed = create-if-missing only

---

## Smoke à faire (humain)

1. Redémarrer l’API → log `Admin user already present — skip seed` **ou** création si DB vide
2. `POST /api/auth/login` → token
3. `GET /api/auth/me` → `id` + `username` présents
4. `PUT /api/auth/me` avec nouveau username → 200
5. `POST /api/auth/change-password` → 204
6. Relogin avec **ancien** MDP → 401 ; **nouveau** → 200
7. Redémarrer l’API → le **nouveau** MDP reste valide (`.env` n’écrase pas)

---

## Checklist spec

- [x] Collection `app_users` (Mongo, pas SQL) — champs username / updatedAt
- [x] Bootstrap create-if-missing, never overwrite password
- [x] Login lit la DB
- [x] `GET/PUT /api/auth/me`
- [x] `POST /api/auth/change-password`
- [ ] Tests automatisés seed / change-password / 409 — **non ajoutés** (smoke manuel)
- [x] Doc `api-frontend.md` mise à jour
- [x] Doc ne dit plus « credentials only in application.properties » comme source de vérité runtime

---

## À valider par l’humain

- [ ] Smoke profil / MDP OK
- [ ] Restart conserve le MDP custom
- [ ] OK pour passer à l’**étape 3** (organization settings)
