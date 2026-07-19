# Audit — PATCH /api/settings (mises à jour partielles)

**Date** : 2026-07-19  
**Statut** : ✅ Code + tests + docs OK  
**Scope** : endpoint settings uniquement

---

## Checklist

- [x] `PATCH /api/settings` (merge partiel, JWT)
- [x] Champs omis = no-op (`JsonNode.has`)
- [x] Clés API null/vide/omis = conserver
- [x] `companyName` / `contentTopic` requis **seulement** s’ils sont dans le body
- [x] Création lazy (`findOrCreateSettings`)
- [x] Réponse = settings complets + masques
- [x] Smoke unitaire : clé Gemini seule → OK
- [x] Smoke unitaire : `companyName` seul → OK

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
