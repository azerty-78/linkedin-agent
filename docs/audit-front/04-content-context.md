# Audit — Étape 4 : Brief IA + PromptContextBuilder

**Date** : 2026-07-19  
**Statut** : ✅ Code + tests + docs OK — validation humaine en attente  
**Fichier source** : `front-agent/docs/for-backend/backend-content-context.md`  
**Réf.** : COMPLETE-SPEC §§2–3 et §5

---

## Ce qui a changé

### Persistance brief (`OrganizationSettings` + GET/PUT `/api/settings`)
Nouveaux champs (defaults rétrocompat) :
- `contentLanguage`, `brandTone`, `emojiPolicy`, `postLengthPreference`
- `preferredFormats[]`, `targetAudience`, `brandGuidelines`, `defaultHashtags`
- `contentPillars`, `topicsToAvoid`, `contentGoals`, `callToActionPreference`
- `mustIncludeKeywords`, `examplePostStyle`

Enums : `BrandTone`, `EmojiPolicy`, `PostLengthPreference`, `ContentFormat`, `ContentLanguage`

### `PromptContextBuilder` (unique)
- Compose IDENTITÉ + BRIEF + STYLE + RÈGLES
- Langue `auto` (heuristique), rotation format déterministe, longueurs SHORT/MEDIUM/LONG
- Branché via `AiGenerationService` sur **tous** les flux :
  - `generateDailyTip()` → trigger + scheduler + publish-test
  - `generateDailyTipForTest()` → generate-test (mode compact)
- Settings **relus à chaque run** (`getSettings()`)

### Post-traitement (`ContentPostProcessor`)
- Trim, strip emojis si `NONE`, warn si hors longueur, append `defaultHashtags` si aucun `#`

### Autres
- `PromptBuilderService` **supprimé** (remplacé)
- Gemini : `systemInstruction` + message user séparés
- Tests : `PromptContextBuilderTest` + `LinkedInCredentialsCrudTest` OK

---

## Ce qui marche

- **BUILD SUCCESSFUL** (compile + tests ciblés)
- Org topic-seul → prompt minimal avec defaults
- `topicsToAvoid` / identité / ton présents dans le system prompt
- Hashtags append + strip emoji couverts par test unitaire

---

## Smoke à faire (humain)

1. `PUT /api/settings` avec `brandTone=CASUAL`, `emojiPolicy=NONE`, `topicsToAvoid=politique`, `defaultHashtags=#test`
2. `POST /api/ai/generate-test` → contenu sans emoji, consignes visibles dans `promptUsed` (ou effet sur le texte)
3. Changer le ton → re-generate → style différent
4. `POST /api/publications/trigger` (si clé + LinkedIn OK) → même builder

---

## Checklist spec

- [x] Migration Mongo (champs optionnels, defaults Kotlin — pas de Flyway)
- [x] Entity + DTO GET/PUT
- [x] Defaults lecture si null
- [x] `PromptContextBuilder`
- [x] trigger / scheduler / generate-test / publish-test (via `AiGenerationService`)
- [x] Tests unitaires prompt / hashtags / avoid
- [x] Doc `api-frontend.md`

---

## À valider par l’humain

- [ ] Smoke consignes → texte généré différent
- [ ] OK pour passer à l’**étape 5** (LLM providers)
