Set-Content -Path "C:\Projets Personnels\Ledger-hub-mobile\AGENTS.md" -Encoding UTF8 -Value @'
# Directives Générales & Workflow de Travail — LedgerHub Mobile

## ⛔ RÈGLE D'OR : AUCUN CODE SANS PLAN VALIDÉ
Avant toute création ou modification de fichier de code, l'agent DOIT impérativement exécuter l'Étape 1 et s'arrêter pour attendre une réponse humaine. Toute action de développement anticipée constitue une violation critique.

---

## 📚 BIBLIOTHÈQUE DE RÉFÉRENCE (LECTURE OBLIGATOIRE)
L'agent doit impérativement baser ses analyses et ses plans sur les documents du dossier `docs/library/` :
1. `docs/library/reforme-2026/` : Conformité fiscale, règles Factur-X, mentions légales et arrondis.
2. `docs/library/project/` : Architecture KMP, conventions et parité fonctionnelle avec ledgerhub-web.
3. `docs/library/qa/` : Cahier de recette manuel, scénarios d'acceptation et critères de validation.

---

## Workflow Obligatoire en 3 Étapes

### Étape 1 : Diagnostic, Étude Documentaire & Plan d'action
1. Consultation documentaire : analyse des fichiers pertinents dans `docs/library/` et des compétences dans `.skills/`.
2. Diagnostic du code : inspection en lecture seule du code source et des schémas SQLDelight.
3. Présentation du plan :
   - Rappel des règles fiscales et critères de recette applicables.
   - Liste exhaustive des fichiers à modifier ou créer.
   - Plan de tests unitaires et scénarios du cahier de recette manuel associés.
4. Clôture obligatoire par :
   « Monsieur le PO, validez-vous ce plan d'action ? »
5. **ARRÊT COMPLET : Attendre la validation formelle avant d'écrire le moindre code.**

### Étape 2 : Développement & Recette Automatisée
- Implémentation du code selon les compétences `.skills/` et les spécifications `docs/library/`.
- Respect strict du multithreading (SQLDelight hors Dispatchers.Main).
- Exécution des tests unitaires (`./gradlew :composeApp:testDebugUnitTest`).
- Compilation de l'artéfact (`./gradlew :composeApp:assembleDebug`).

### Étape 3 : Clôture, Audit & Recette
- Mise à jour systématique de `logs/audit.md` (décisions, choix techniques, matrice RCA).
- Déroulement du scénario de test manuel défini dans `docs/library/qa/`.
- Rédaction du post de partage technique selon `tech-evangelist-build-in-public`.
'@