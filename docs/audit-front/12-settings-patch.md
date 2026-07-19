# Audit — PATCH /api/settings (mises à jour partielles)

**Date** : 2026-07-19  
**Statut** : ✅ Code + tests + docs OK  
**Scope** : endpoint settings uniquement

---

## Checklist

- [x] `PATCH /api/settings` (JWT) — DTO concret `PatchOrganizationSettingsRequest` (**pas** `JsonNode`)
- [x] Champs null/omis = no-op
- [x] Clés API null/vide = conserver
- [x] `companyName` / `contentTopic` requis **seulement** si non-null
- [x] Création lazy (`findOrCreateSettings`)
- [x] Réponse = settings complets + masques
- [x] Smoke unitaire : clé Gemini seule → OK
- [x] Smoke unitaire : `companyName` seul → OK

## Fix 500 Jackson 3

`@RequestBody JsonNode` → `CodecException` sous WebFlux/Jackson 3.  
Remplacé par DTO Kotlin avec `@JsonIgnoreProperties(ignoreUnknown = true)`.

## Comment tester

```http
PATCH /api/settings
Authorization: Bearer <jwt>
Content-Type: application/json

{ "geminiApiKey": "AIza…", "geminiModel": "gemini-2.0-flash" }
```

Attendu : **200**, `geminiKeyMasked` renseigné, pas d’erreur `companyName`.

```http
PATCH /api/settings
{ "companyName": "Revlut" }
```

Attendu : **200** sans exiger `contentTopic`.

`PUT /api/settings` inchangé (compat snapshot complet).
