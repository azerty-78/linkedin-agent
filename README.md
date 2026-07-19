# LinkedIn Agent — Revolut Consulting

Backend Spring Boot (WebFlux / Kotlin) qui automatise la génération de contenus LinkedIn via LLM et la configuration organisationnelle.

Documentation détaillée (rôles, permissions, features, API) : [`docs/fonctionnalites-et-securite.md`](docs/fonctionnalites-et-securite.md)

---

## Stack

| Élément | Détail |
|---------|--------|
| Langage | Kotlin 2.3 / Java 21 |
| Framework | Spring Boot 4.1 (WebFlux réactif) |
| Base de données | MongoDB 7 (réactif) |
| Sécurité | JWT (jjwt) + Spring Security WebFlux |
| IA | Spring AI 2.0 (OpenAI, Anthropic) |
| Build | Gradle Kotlin DSL |

---

## Prérequis

- JDK 21+
- Docker (MongoDB local)
- Gradle Wrapper (`./gradlew` / `.\gradlew.bat`)

---

## Démarrage rapide

### 1. MongoDB

```bash
cd setup
docker compose -f compose.yml --env-file .env up -d
```

### 2. Variables d’environnement

Copier / renseigner `setup/.env` (Mongo, JWT, admin, chiffrement, mail…).  
Spring Boot lit surtout les variables système ; exporter celles dont vous avez besoin, par exemple sous PowerShell :

```powershell
$env:MONGODB_URI="mongodb://devroot:devpass@localhost:27017/linkedin_agent_db?authSource=admin"
$env:ADMIN_EMAIL="votre-admin@email.com"
$env:ADMIN_PASSWORD="votre-mot-de-passe"
# Optionnel : openssl rand -base64 32
$env:ENCRYPTION_KEY="..."
$env:JWT_SECRET="dev-only-secret-change-me-min-32-characters-long"
```

### 3. Lancer l’application

```bash
.\gradlew.bat bootRun
```

- API : `http://localhost:8080`
- Health : `http://localhost:8080/actuator/health`

### 4. Première connexion

Si la collection `app_users` n’a pas encore d’ADMIN, un compte est créé au démarrage (`ADMIN_EMAIL` / `ADMIN_PASSWORD` / `ADMIN_USERNAME` optionnel).  
Si l’ADMIN existe déjà, le seed **ne touche pas** au mot de passe ni au profil.

```http
POST http://localhost:8080/api/auth/login
Content-Type: application/json

{
  "email": "<ADMIN_EMAIL>",
  "password": "<ADMIN_PASSWORD>"
}
```

Utiliser ensuite le header `Authorization: Bearer <accessToken>` sur les routes protégées.

---

## Structure du projet

```
src/main/kotlin/com/revolut/agent/
├── config/          # Security, JWT, bootstrap, chiffrement, logs
├── controller/      # auth | settings | ai | linkedin | publications
├── dto/             # DTOs par domaine
├── exception/
├── model/           # user | settings | linkedin | publication
├── repository/
└── service/         # auth | settings | ai | linkedin | publication
```

---

## Fonctionnalités principales

- **Auth JWT** — login, profil `/me`, bootstrap admin
- **Paramètres organisation** — topic, timezone, provider LLM, clés API chiffrées (AES-256-GCM)
- **Génération IA** — tip LinkedIn via OpenAI / Anthropic (Gemini stub)
- **Observabilité** — logs structurés (requestId, durée, principal), Actuator

Voir le détail dans [`docs/fonctionnalites-et-securite.md`](docs/fonctionnalites-et-securite.md).

---

## Profils

| Profil | Fichier | Usage |
|--------|---------|--------|
| défaut (dev) | `application.properties` | Defaults locaux / placeholders |
| `prod` | `application-prod.properties` | Secrets uniquement via variables d’env |

```bash
.\gradlew.bat bootRun --args='--spring.profiles.active=prod'
```

---

## Compilation & tests

```bash
.\gradlew.bat compileKotlin
.\gradlew.bat test
```

---

## Licence / contexte

Projet interne — Revolut Consulting / Kobe Corporation.
