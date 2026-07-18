# Audit — Étape 1 : REMOVE subscribers

**Date** : 2026-07-18  
**Statut** : ✅ Code + docs faits — validation humaine en attente  
**Fichier source** : `front-agent/docs/for-backend/backend-REMOVE-subscribers.md`

---

## Ce qui a changé

### Code supprimé (10 fichiers Kotlin + 1 template)
- `controller/subscriber/*` (`SubscriberController`, `PublicUnsubscribeController`)
- `service/subscriber/SubscriberService`
- `repository/subscriber/SubscriberRepository`
- `model/subscriber/*` (`Subscriber`, `SubscriberStatus`)
- `dto/subscriber/*`
- `exception/subscriber/SubscriberAlreadyActiveException`
- `templates/unsubscribe-result.html`

### Code modifié
- `GlobalExceptionHandler` — handler 409 abonnés retiré
- `SecurityConfig` — `permitAll` sur `/api/public/**` retiré
- `build.gradle.kts` — dépendances Thymeleaf retirées (seul usage = unsubscribe)

### Docs mises à jour
- `README.md` — feature abonnés retirée de la description / structure
- `docs/api-frontend.md` — sections `/api/subscribers` + `/api/public/unsubscribe` retirées + changelog
- `docs/fonctionnalites-et-securite.md` — idem + note drop collection Mongo

---

## Ce qui marche

- **Compile Kotlin** : `BUILD SUCCESSFUL` (Gradle 9.5.1 via wrapper jar + JBR IntelliJ)
- Routes `/api/subscribers*` et `/api/public/unsubscribe*` : **absentes** → 404 attendu au runtime
- Plus de référence Thymeleaf / Subscriber dans le code source

---

## À faire manuellement (humain)

1. **Drop Mongo** sur chaque environnement :
   ```js
   db.subscribers.drop()
   ```
2. Smoke runtime (app démarrée) :
   - `GET /api/subscribers` → 404 (ou 401 si JWT manquant avant le 404 selon filtre security — route absente)
   - `GET /api/public/unsubscribe/fake` → 404
   - Login + `GET /api/settings` toujours OK
3. Nettoyer dossiers packages vides `**/subscriber/` s’ils restent (fichiers `.kt` déjà partis)
4. `clean` Gradle conseillé pour purger les `.class` anciens dans `build/`

---

## Checklist spec

- [x] Controllers + routes `/api/subscribers*` absents
- [x] Services / repositories / mappers `Subscriber*` absents
- [ ] Migration drop table(s) + purge données — **manuel Mongo** `db.subscribers.drop()`
- [x] Tests abonnés — aucun existait
- [x] Docs / OpenAPI textuel sans `Subscriber*`
- [x] Aucune référence dans prompt builder / settings
- [x] Aucun cron `Subscriber*`
- [x] Changelog documenté (api-frontend + fonctionnalites-et-securite)

### Hors scope volontaire
- Stub `MAIL_NOTIFICATION` dans l’orchestration publications : **conservé** (notif interne future, pas la feature Abonnés)

---

## À valider par l’humain

- [ ] Drop `subscribers` OK sur Mongo local/prod
- [ ] Smoke 404 sur anciennes routes
- [ ] Reste de l’API (auth, settings, AI) intact
- [ ] OK pour passer à l’**étape 2** (user profile)
