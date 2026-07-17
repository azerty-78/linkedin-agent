  "error": "Message lisible",
  "status": 401
}
```
| HTTP | Cas typiques |
|------|----------------|
| `400` | Erreur métier / génération IA |
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
---

      "freeTier": true,
      "recommendedForTests": true
    },
    {
      "id": "gpt-4o-mini",
      "label": "GPT-4o Mini (économique)",
      "provider": "OPENAI",
      "freeTier": false,
      "recommendedForTests": true
    }
  ],
  "defaultByProvider": {
    "OPENAI": "gpt-4o-mini",
    "ANTHROPIC": "claude-haiku-4-5",
    "GEMINI": "gemini-2.0-flash"
  },
  "recommendedTestByProvider": {
    "OPENAI": "gpt-4o-mini",
    "ANTHROPIC": "claude-haiku-4-5",
    "GEMINI": "gemini-2.0-flash"
  }
}
```
**Conseil tests** : préférer `GEMINI` + `gemini-2.0-flash` (free tier) pour éviter la facturation.
---


| Champ | Type | Description |
|-------|------|-------------|
| `content` | `string` | Tip généré |
| `provider` | `LlmProvider` | Provider utilisé |
| `model` | `string` | Modèle utilisé |
| `generatedAt` | `string` (ISO-8601) | |
| `promptUsed` | `string` | Prompt envoyé au LLM |
```json
{
  "content": "Voici un tip LinkedIn…",
  "provider": "GEMINI",
  "model": "gemini-2.0-flash",
  "generatedAt": "2026-07-17T12:00:00Z",
  "promptUsed": "..."
}
```
**Erreurs**
- `422` — pas de clé API pour le provider actif
- `400` — quota / clé invalide / autre erreur LLM
---

| Suppression | `DELETE /api/subscribers/{id}` |
| Bouton « Générer un tip » | `POST /api/ai/generate-test` |
### Exemple flux settings + génération (tests gratuits)
1. `GET /api/settings/llm-models` → options Gemini free
2. `PUT /api/settings` avec `activeLlmProvider: "GEMINI"`, `geminiModel: "gemini-2.0-flash"`, `geminiApiKey: "…"`
3. `POST /api/ai/generate-test`
---
## 9. Récap URLs
| Méthode | URL | Auth | Body |
|---------|-----|------|------|
| `POST` | `/api/auth/login` | Non | `{ email, password }` |
| `GET` | `/api/auth/me` | Oui | — |
| `GET` | `/api/settings` | Oui | — |
| `GET` | `/api/settings/llm-models` | Oui | — |
| `PUT` | `/api/settings` | Oui | settings (voir §3.3) |
| `POST` | `/api/subscribers` | Oui | `{ email, fullName? }` |
| `GET` | `/api/subscribers?status=` | Oui | — |
| `DELETE` | `/api/subscribers/{id}` | Oui | — |
| `GET` | `/api/public/unsubscribe/{token}` | Non | HTML |
| `POST` | `/api/ai/generate-test` | Oui | — |
| `GET` | `/actuator/health` | Non | — |
---