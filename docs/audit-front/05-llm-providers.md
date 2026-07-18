# Audit — Étape 5 : Fournisseurs LLM + trigger

**Date** : 2026-07-19  
**Statut** : ✅ Code + tests + docs OK — validation humaine en attente  
**Fichier source** : `front-agent/docs/for-backend/backend-llm-providers.md`  
**Réf.** : COMPLETE-SPEC §§5–6

---

## Ce qui a changé

### Déjà en place (confirmé, non modifié)
- Clés chiffrées + `*KeyMasked` (`****xxxx`)
- Modèles `openAiModel` / `anthropicModel` / `geminiModel`
- `activeLlmProvider` (scheduler + fallback)
- `GET /api/settings/llm-models`
- Write clés : null / `""` / omis = conserver

### Nouveau — override trigger
| Élément | Détail |
|---------|--------|
| `TriggerPublicationRequest` | `{ "llmProvider": "GEMINI" }` optionnel |
| `POST /api/publications/trigger` | body optionnel ; omis → `activeLlmProvider` |
| Pipeline | Controller → QueryService → Orchestrator → `generateDailyTip(override)` |
| Pré-check | `requireProviderReady` **avant** création du run → **422** si clé absente |
| Message FR | `Clé API {PROVIDER} manquante. Configurez-la dans Paramètres.` |

### Reporté à l’étape 7
- `awaitReview` / `AWAITING_REVIEW` / `confirm` — **pas implémenté** ici

### Tests
- `AiGenerationProviderOverrideTest` (override, fallback, clé manquante FR)
- Régression prompt + LinkedIn credentials OK

---

## Ce qui marche

- **BUILD SUCCESSFUL**
- Trigger avec `llmProvider` → provider forcé
- Trigger sans body → `activeLlmProvider`
- Clé absente → 422 FR (pas de publication orpheline créée)
- Scheduler inchangé (toujours `activeLlmProvider`)

---

## Smoke à faire (humain)

1. Settings : clé Gemini OK, OpenAI absente ; `activeLlmProvider=GEMINI`
2. `POST /trigger` `{}` → OK (Gemini)
3. `POST /trigger` `{ "llmProvider": "OPENAI" }` → **422** message FR
4. `POST /trigger` `{ "llmProvider": "GEMINI" }` → run OK
5. Enum invalide `{ "llmProvider": "FOO" }` → **400**

---

## Checklist spec

- [x] Accepter `llmProvider` sur trigger
- [x] Fallback `activeLlmProvider`
- [x] 422 clé manquante (message FR)
- [x] PromptContextBuilder déjà branché (étape 4)
- [x] Tests override / omit / clé absente
- [x] `api-frontend.md` (§8ter)
- [ ] `awaitReview` → étape 7

---

## À valider par l’humain

- [ ] Smoke trigger + 422 OK
- [ ] OK pour passer à l’**étape 6** (publication schedule)
