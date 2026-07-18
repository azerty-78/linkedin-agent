# Audit — Étape 6 : Planning publication auto

**Date** : 2026-07-19  
**Statut** : ✅ Code + tests + docs OK — validation humaine en attente  
**Fichier source** : `front-agent/docs/for-backend/backend-publication-schedule.md`  
**Réf.** : COMPLETE-SPEC §2.4 et §4

---

## Ce qui a changé

### Settings persistés
| Champ | Défaut |
|-------|--------|
| `schedulerFrequency` | `DAILY` |
| `schedulerDaysOfWeek` | `[]` |
| `schedulerIntervalDays` | `2` |
| `schedulerAnchorDate` | `null` (`YYYY-MM-DD`) |

Validation PUT : WEEKLY = 1 jour ; CUSTOM_DAYS ≥ 1 ; EVERY_N_DAYS interval 2–30 ; timezone IANA.

### Scheduler (`PublicationSchedulerService` + `SchedulerDecision`)
1. `schedulerEnabled`
2. Fenêtre `publishHourUtc` UTC ± 5 min
3. Fréquence (jour calendaire en `timezone` org)
4. EVERY_N_DAYS : ancre auto-persistée si absente
5. Idempotence : déjà un run `SCHEDULED` en `SUCCESS`/`PARTIAL_SUCCESS` aujourd’hui (TZ org) → skip
6. Provider = `activeLlmProvider` (via orchestrate, sans override)

### Tests
`SchedulerDecisionTest` : WEEKDAYS, CUSTOM_DAYS, EVERY_N_DAYS, fenêtre UTC

---

## Ce qui marche

- **BUILD SUCCESSFUL**
- Org sans nouveaux champs → `DAILY` (rétrocompat)
- Week-end + WEEKDAYS → pas de run
- EVERY_N_DAYS sans ancre → run + persist ancre

---

## Smoke à faire (humain)

1. `PUT` `schedulerEnabled=true`, `schedulerFrequency=WEEKDAYS`, `publishHourUtc` = heure UTC courante
2. Attendre le tick (≤ 5 min) un jour ouvré → run SCHEDULED
3. Même jour → pas de 2ᵉ SUCCESS
4. `CUSTOM_DAYS` avec uniquement demain → skip aujourd’hui
5. `EVERY_N_DAYS` interval 2 → vérifier ancre renseignée après 1er run

---

## Checklist spec

- [x] Persister frequency, days, interval, anchor
- [x] Validation PUT
- [x] Adapter cron / décision
- [x] Idempotence 1 SUCCESS SCHEDULED / jour (TZ)
- [x] Tests WEEKDAYS / CUSTOM / EVERY_N
- [x] Doc `api-frontend.md`

---

## À valider par l’humain

- [ ] Smoke fréquences OK
- [ ] OK pour passer à l’**étape 7** (publication review)
