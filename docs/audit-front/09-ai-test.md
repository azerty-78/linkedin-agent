# Audit — Étape 9 : Test IA (`generate-test`)

**Date** : 2026-07-19  
**Statut** : ✅ Code + tests + docs OK — validation humaine en attente  
**Fichier source** : `front-agent/docs/for-backend/backend-ai-test.md`  
**Réf.** : GUIDE-EXECUTION étape 9

---

## Ce qui a changé

### `POST /api/ai/generate-test`
Body JSON **optionnel** (absent / `{}` = comportement historique) :

| Champ | Effet |
|-------|--------|
| `llmProvider` | Force le provider de test (modèle lite) ; clé absente → erreur FR (`Clé API … manquante`) |
| `topic` | Override ponctuel du sujet |
| `brandTone` | Override ton |
| `contentLanguage` | Override `fr` / `en` / `auto` |
| `useOrgBrief` | Défaut `true` ; `false` = prompt minimal (topic + règles LinkedIn) |

### PromptContextBuilder
- Nouveau paramètre `useOrgBrief`
- Mode minimal : pas d’IDENTITÉ / BRIEF org

### Inchangé
- Modèles **toujours** `ai.test.*` (jamais les modèles premium Paramètres)
- Réponse `{ content, provider, model, generatedAt, promptUsed }`

### Tests
- `useOrgBrief false` ommet IDENTITÉ/BRIEF
- Override topic visible dans le user prompt
- Provider demandé sans clé → `AiGenerationException`

### Docs
- `docs/api-frontend.md` §4.1

---

## Checklist spec

- [x] Body JSON optionnel (rétrocompat)
- [x] PromptContextBuilder + overrides topic/ton/langue
- [x] `useOrgBrief: false`
- [x] Toujours modèle test / lite

---

## Validation humaine

1. `POST /api/ai/generate-test` sans body → tip comme avant  
2. Body avec `topic` + `useOrgBrief: false` → tip court sans identité org  
3. `llmProvider` sans clé → erreur lisible  
