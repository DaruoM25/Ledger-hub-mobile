# Master Test Plan — LedgerHub Mobile (US-01 à US-25)

**Application :** LedgerHub Mobile — facturation B2B conforme Factur-X 2026 (Compose Multiplatform, Android validé / iOS on hold)
**Périmètre :** parité fonctionnelle mobile, 25 User Stories
**Support de recette :** émulateur ou appareil physique Android (référence QA du projet : Pixel 5, API 35, 393×851 dp)
**Langue de rédaction des scénarios :** français — l'application démarre en français par défaut

## Comment lire ce document

- Chaque scénario est identifié `[MOB-<MODULE>-NN]`.
- **Type** : `Passant` (chemin nominal) ou `Non passant` (erreur / cas limite).
- **Préconditions** : état de l'application et des données avant de commencer le scénario.
- **Actions** : gestes numérotés (Tap, Scroll, Swipe, Saisie, Rotation…).
- **Résultat attendu** : décrit **après chaque action critique**, pas seulement à la fin — un testeur doit pouvoir arrêter le scénario au premier écart.
- Les identifiants entre backticks (ex. `theme_toggle_btn`) sont les tags techniques (`testTag`) posés sur les éléments — utiles si le testeur dispose d'un outil d'inspection d'accessibilité, mais chaque scénario reste lisible sans eux : ils ne remplacent jamais la description visuelle du geste.
- Sauf mention contraire, les scénarios s'exécutent en **portrait, largeur compacte** (téléphone). Les scénarios de largeur étendue (tablette / paysage ≥ 840 dp) sont regroupés dans le module 9.

## ⚠️ Point d'attention transversal — deux écrans non câblés dans la navigation

Les écrans de connexion/inscription (`login_screen`, module 1) et e-Reporting (`ereporting_screen`, module 11) **existent dans le code mais ne sont reliés à aucune navigation de l'application** : `App.kt` ne les référence nulle part, et aucun bouton du shell n'y mène. Un testeur qui lance l'application arrive directement sur le tableau de bord, base de démonstration déjà peuplée, sans jamais croiser ces deux écrans. Les modules 1 et 11 documentent les scénarios attendus **pour mémoire et pour une recette outillée** (harnais de test dédié), mais un testeur manuel sur l'APK standard doit les sauter et les signaler comme **non exécutables en l'état** plutôt que comme échec. Détail complet, avec les autres anomalies connues, dans l'encart **⚠️ Dette Technique & Anomalies Connues** en fin de document.

---

## 1. Inscription & Authentification (US-21)

> ⚠️ Non atteignable depuis le parcours normal de l'application — voir l'avertissement ci-dessus. Les scénarios suivants supposent un harnais de test qui monte `AuthScreen` directement (`login_screen`), ou une future intégration dans `App.kt`.

### [MOB-AUTH-01] Connexion nominale
**Type :** Passant
**Préconditions :** Écran de connexion affiché (`login_screen`), onglet « Connexion » actif par défaut.
**Actions :**
1. Taper dans le champ « Adresse email » (`login_email_field`) et saisir une adresse valide (ex. `demo@ledgerhub.app`).
2. Taper dans le champ « Mot de passe » (`login_password_field`) et saisir un mot de passe quelconque non vide.
3. Taper sur « Se connecter » (`login_submit_button`).

**Résultat attendu :**
- Après l'étape 1-2 : le bouton « Se connecter » devient actif (il était grisé/inerte tant qu'un des deux champs était vide).
- Après l'étape 3 : un indicateur de chargement (`login_loading_indicator`) apparaît brièvement pendant l'appel réseau.
- Résultat final : aucune erreur affichée ; le drapeau interne de succès est levé (à vérifier via le harnais, aucune navigation visible n'existe encore).

### [MOB-AUTH-02] Connexion — champs vides
**Type :** Non passant
**Préconditions :** Écran de connexion affiché, champs vides.
**Actions :**
1. Observer l'état du bouton « Se connecter » sans rien saisir.

**Résultat attendu :** le bouton reste inerte (grisé ou sans effet au tap) : `email.isNotBlank() && password.isNotBlank()` doit être faux.

### [MOB-AUTH-03] Connexion — échec réseau / identifiants refusés
**Type :** Non passant
**Préconditions :** Écran de connexion, email/mot de passe saisis, backend configuré pour renvoyer une erreur (ou réseau coupé).
**Actions :**
1. Saisir email et mot de passe.
2. Taper « Se connecter ».

**Résultat attendu :**
- L'indicateur de chargement disparaît.
- Un message d'erreur apparaît dans `login_error_message` (message du serveur, ou repli « Erreur inconnue lors de la connexion » si le serveur ne fournit aucun détail).
- Les champs saisis restent affichés (rien n'est effacé) pour permettre une nouvelle tentative.

### [MOB-AUTH-04] Bascule vers l'inscription
**Type :** Passant
**Préconditions :** Onglet « Connexion » actif.
**Actions :**
1. Taper « Pas encore de compte ? S'inscrire » (`login_register_link`) ou l'onglet « Inscription » (`auth_tab_register`).

**Résultat attendu :** le formulaire bascule sur les champs d'inscription : SIRET, Raison sociale (auto-complétée), Email, Mot de passe. Le titre affiche « Créer votre espace LedgerHub » / sous-titre « Votre SIRET suffit, nous récupérons le reste ».

### [MOB-AUTH-05] Inscription — vérification SIRENE automatique et réussie
**Type :** Passant
**Préconditions :** Onglet « Inscription » actif, champ SIRET vide.
**Actions :**
1. Taper dans le champ SIRET (`auth_siret_input`) et saisir progressivement les 14 chiffres du SIRET de démonstration.
2. Ne rien taper d'autre : observer le déclenchement automatique de la vérification dès le 14ᵉ chiffre.

**Résultat attendu :**
- Après le 13ᵉ chiffre : aucune vérification ne démarre.
- Dès le 14ᵉ chiffre (aucun bouton à presser) : un indicateur de chargement (`auth_siret_loader`) apparaît avec le message « Vérification auprès de l'API SIRENE… ».
- Sous ~1 seconde : le badge vert « ✓ Entreprise vérifiée via l'API SIRENE » (`auth_sirene_verified_badge`) s'affiche.
- Le champ « Raison sociale » (`auth_company_name_input`) se remplit automatiquement avec le nom retourné.
- Le bouton « Créer mon espace » devient actif (il était inerte tant que la vérification n'avait pas abouti).

### [MOB-AUTH-06] Inscription — SIRET introuvable
**Type :** Non passant
**Préconditions :** Onglet « Inscription », champ SIRET vide.
**Actions :**
1. Saisir 14 chiffres ne correspondant à aucune entreprise connue du répertoire SIRENE simulé.

**Résultat attendu :** le message « SIRET introuvable au répertoire SIRENE. » remplace le badge vert ; le champ Raison sociale reste vide ; le bouton « Créer mon espace » reste inerte.

### [MOB-AUTH-07] Inscription — répertoire SIRENE indisponible
**Type :** Non passant
**Préconditions :** Service de vérification SIRENE simulé en panne (harnais de test).
**Actions :**
1. Saisir un SIRET à 14 chiffres.

**Résultat attendu :** message distinct des deux précédents — « Répertoire SIRENE indisponible, réessayez. » — pour ne pas laisser croire à tort que le SIRET est invalide.

### [MOB-AUTH-08] Inscription — correction du SIRET pendant une vérification en cours
**Type :** Non passant / cas limite
**Préconditions :** Onglet « Inscription ».
**Actions :**
1. Saisir rapidement 14 chiffres, puis avant l'affichage du résultat, effacer un chiffre et le remplacer par un autre (nouveau SIRET à 14 chiffres).

**Résultat attendu :** seul le résultat de la **dernière** saisie s'affiche ; aucun résultat obsolète de la première vérification n'apparaît après coup (annulation de la requête en vol).

### [MOB-AUTH-09] Inscription — nom auto-rempli puis modifié manuellement
**Type :** Passant
**Préconditions :** SIRET vérifié avec succès, champ Raison sociale auto-rempli.
**Actions :**
1. Taper dans le champ Raison sociale et modifier le texte auto-rempli.
2. Effacer un chiffre du SIRET (le faisant repasser sous 14 chiffres) puis le ressaisir.

**Résultat attendu :**
- Après l'étape 1 : le texte modifié reste tel quel, sans être écrasé par une future réponse SIRENE.
- Après l'étape 2 : le nom modifié manuellement **n'est pas effacé** (seul un nom encore auto-rempli serait vidé quand le SIRET redescend sous 14 chiffres).

---

## 2. Dashboard / Vue d'ensemble

### [MOB-DASH-01] Affichage nominal au démarrage
**Type :** Passant
**Préconditions :** Application lancée sur données de démonstration (semis initial), onglet « Vue d'ensemble » actif par défaut.
**Actions :**
1. Observer l'écran (`dashboard_screen`) sans interaction.

**Résultat attendu :**
- Titre « Vue d'ensemble » et sous-titre « Pilotez votre activité et votre conformité 2026 » visibles.
- Grille de 4 cartes KPI (`dashboard_kpi_grid`) en 2×2 : « Chiffre d'affaires » / « En attente de paiement » en première ligne, « Factures émises » / « Devis en attente » en seconde.
- Chaque carte affiche un montant cohérent avec les données de démonstration (ex. « 25 263,60 € » sur Chiffre d'affaires).
- Aucun indicateur de chargement (`dashboard_loading_indicator`) ni message d'erreur (`dashboard_load_error`) visible.

### [MOB-DASH-02] Défilement jusqu'au graphique et à la liste
**Type :** Passant
**Préconditions :** Écran Dashboard affiché.
**Actions :**
1. Scroller verticalement vers le bas depuis la grille KPI.

**Résultat attendu :**
- La section « Chiffre d'affaires — 6 derniers mois — montants TTC encaissés » apparaît avec le graphique (`dashboard_revenue_chart`, courbe/aire dessinée en Canvas, sans bibliothèque tierce) et les libellés Min/Max.
- En poursuivant le défilement : la section « Factures récentes » (`dashboard_recent_activity_list`), puis « Devis à relancer » (`dashboard_quotes_to_followup_section`).

### [MOB-DASH-03] Animation du graphique de revenu
**Type :** Passant
**Préconditions :** Dashboard affiché, graphique visible à l'écran.
**Actions :**
1. Naviguer vers un autre onglet puis revenir sur « Vue d'ensemble » (ou déclencher un rechargement des données).

**Résultat attendu :** la courbe se redessine avec une animation de révélation d'environ 600 ms (pas un affichage instantané) — vérifier visuellement l'effet de tracé progressif.

### [MOB-DASH-04] Liste des factures récentes — état vide
**Type :** Non passant / cas limite
**Préconditions :** Base sans aucune facture (compte neuf, avant tout semis de démonstration).
**Actions :**
1. Défiler jusqu'à la section « Factures récentes ».

**Résultat attendu :** le message « Aucun document pour le moment » (`dashboard_recent_activity_empty`) s'affiche à la place de la liste.

### [MOB-DASH-05] Devis à relancer — urgence de couleur
**Type :** Passant
**Préconditions :** Au moins un devis envoyé dont la date de validité est à ≤ 3 jours (seuil `URGENT_THRESHOLD_DAYS`), un autre expiré, un autre à échéance lointaine.
**Actions :**
1. Défiler jusqu'à « Devis à relancer ».
2. Observer la couleur du libellé d'échéance de chaque devis listé.

**Résultat attendu :**
- Devis à échéance lointaine : libellé neutre « J-<n> ».
- Devis à ≤ 3 jours : libellé en **ambre**.
- Devis échu (0 jour restant) : libellé « Expire aujourd'hui » ; devis expiré (passé) : libellé « Expiré » en **rouge**.

### [MOB-DASH-06] Devis à relancer — état vide
**Type :** Non passant / cas limite
**Préconditions :** Aucun devis envoyé sans réponse.
**Actions :**
1. Défiler jusqu'à la section.

**Résultat attendu :** message « Aucun devis à relancer pour l'instant. » (`dashboard_quotes_to_followup_empty`).

### [MOB-DASH-07] Échec de chargement du tableau de bord
**Type :** Non passant
**Préconditions :** Backend/base local inaccessible au lancement.
**Actions :**
1. Lancer l'application (ou forcer une erreur de lecture).

**Résultat attendu :** message d'erreur affiché dans `dashboard_load_error` (toast/texte « Erreur lors du chargement du tableau de bord » ou équivalent) ; l'application ne plante pas et reste utilisable.

### [MOB-DASH-08] Absence de mise en page tablette dédiée
**Type :** Passant / non-régression
**Préconditions :** Dashboard affiché.
**Actions :**
1. Faire pivoter l'appareil en paysage, ou basculer sur un émulateur tablette (≥ 840 dp).

**Résultat attendu :** contrairement au Rapprochement bancaire, le Dashboard **n'a pas** de rupture de mise en page à 840 dp — c'est le même `Column` défilant qui se réorganise par simple retour à la ligne. Vérifier qu'aucune carte ne se retrouve tronquée ou superposée.

---

## 3. Annuaire — Clients & DGFIP

### 3.1 Fiches clients (CRUD)

### [MOB-CLI-01] Liste des clients — état vide
**Type :** Non passant / cas limite
**Préconditions :** Aucun client enregistré.
**Actions :**
1. Ouvrir l'onglet « Clients » (`clients_screen`).

**Résultat attendu :** message « Aucun client enregistré. » (`clients_empty_state`).

### [MOB-CLI-02] Création d'un client — nominal
**Type :** Passant
**Préconditions :** Onglet Clients affiché.
**Actions :**
1. Taper « Ajouter un client » (`clients_add_button`).
2. Renseigner Raison sociale (`client_form_name`), SIRET 14 chiffres valides (`client_form_siret`), Email (`client_form_email`).
3. Taper « Enregistrer » (`client_form_save`).

**Résultat attendu :**
- Après l'étape 1 : la boîte de dialogue « Nouveau client » (`client_form_dialog`) s'ouvre.
- Après l'étape 3 : la boîte se ferme, un message « Client ajouté » (`clients_feedback`) apparaît, et la nouvelle fiche est visible dans la liste (`card(siret)`).

### [MOB-CLI-03] Création — SIRET invalide (longueur)
**Type :** Non passant
**Préconditions :** Formulaire de nouveau client ouvert.
**Actions :**
1. Saisir un SIRET de 10 chiffres.
2. Taper « Enregistrer ».

**Résultat attendu :** le champ SIRET s'entoure de rouge, message « Le SIRET doit comporter exactement 14 chiffres » ; la boîte de dialogue reste ouverte ; le bouton Enregistrer reste cliquable (il ne se grise jamais — l'erreur se révèle au clic, jamais un bouton mort sans explication).

### [MOB-CLI-04] Création — raison sociale trop courte
**Type :** Non passant
**Préconditions :** Formulaire ouvert.
**Actions :**
1. Saisir un seul caractère dans Raison sociale, compléter le reste valablement.
2. Taper « Enregistrer ».

**Résultat attendu :** erreur « La raison sociale doit comporter au moins 2 caractères ».

### [MOB-CLI-05] Création — email invalide
**Type :** Non passant
**Préconditions :** Formulaire ouvert.
**Actions :**
1. Saisir un email sans arobase ou sans domaine (ex. « client-exemple »).
2. Taper « Enregistrer ».

**Résultat attendu :** erreur « Adresse email invalide ».

### [MOB-CLI-06] Création — SIRET déjà utilisé
**Type :** Non passant
**Préconditions :** Un client avec le SIRET `X` existe déjà.
**Actions :**
1. Créer un nouveau client avec le même SIRET `X`.
2. Taper « Enregistrer ».

**Résultat attendu :** erreur sur le champ SIRET « Un client porte déjà ce SIRET » ; aucune deuxième fiche créée.

### [MOB-CLI-07] Édition — SIRET verrouillé
**Type :** Passant / non-régression
**Préconditions :** Un client existant, liste affichée.
**Actions :**
1. Taper « Modifier » (`editButton(siret)`) sur une fiche.
2. Observer le champ SIRET.

**Résultat attendu :** le champ SIRET est **désactivé** (non éditable) ; un texte d'aide indique « Le SIRET identifie la fiche et ne peut pas être modifié ». Nom et email restent éditables.

### [MOB-CLI-08] Édition — nominal
**Type :** Passant
**Préconditions :** Fiche client ouverte en édition.
**Actions :**
1. Modifier la Raison sociale.
2. Taper « Enregistrer ».

**Résultat attendu :** message « Client mis à jour » ; la carte de la liste reflète le nouveau nom.

### [MOB-CLI-09] Suppression — client non référencé
**Type :** Passant
**Préconditions :** Un client existant, jamais utilisé sur aucune facture.
**Actions :**
1. Taper « Supprimer » (`deleteButton(siret)`).
2. Confirmer dans la boîte de dialogue « Supprimer ce client ? » (`client_delete_dialog` / `client_delete_confirm`).

**Résultat attendu :**
- Après l'étape 1 : la boîte affiche « Cette fiche sera retirée de la liste. Les factures déjà émises ne sont pas modifiées. »
- Après l'étape 2 : message « Client supprimé » ; la fiche disparaît de la liste.

### [MOB-CLI-10] Suppression — client référencé sur une facture (bloquée)
**Type :** Non passant
**Préconditions :** Un client déjà cité sur au moins une facture émise.
**Actions :**
1. Taper « Supprimer » puis confirmer.

**Résultat attendu :** bannière d'erreur « Suppression impossible : ce client figure sur <n> facture(s) émise(s) » (`clients_error`) ; la fiche reste dans la liste.

### 3.2 Annuaire DGFIP (recherche SIREN/SIRET, routage PPF/PDP)

### [MOB-DIR-01] Recherche — SIRET valide (clé de Luhn correcte)
**Type :** Passant
**Préconditions :** Onglet « Annuaire DGFIP » (`directory_screen`).
**Actions :**
1. Saisir un SIRET à 14 chiffres dont la clé de Luhn est valide dans le champ de recherche (`directory_search_field`).
2. Observer l'indicateur de clé (`directory_luhn_indicator`) avant de lancer la recherche.
3. Taper « Rechercher » (`directory_search_button`).

**Résultat attendu :**
- Après l'étape 2 : indicateur vert « ✓ Clé de Luhn valide — SIREN/SIRET ».
- Après l'étape 3 : indicateur de chargement (`directory_loading`) bref, puis une carte résultat (`directory_result_card`) avec raison sociale, SIREN/SIRET, numéro de TVA (ou mention franchise en base), badge de routage PPF ou PDP, statut et date de dernière synchronisation.

### [MOB-DIR-02] Recherche — clé de Luhn invalide
**Type :** Non passant
**Préconditions :** Écran Annuaire DGFIP.
**Actions :**
1. Saisir 14 chiffres dont la clé de Luhn est incorrecte.

**Résultat attendu :** indicateur rouge « ✗ Clé de Luhn invalide » ; le bouton Rechercher peut rester actif mais un tap n'aboutit qu'à un résultat « non trouvé » ou une désactivation du bouton selon `isSearchEnabled` — vérifier que la recherche n'aboutit jamais à une fausse fiche.

### [MOB-DIR-03] Recherche — identifiant introuvable
**Type :** Non passant
**Préconditions :** Écran Annuaire DGFIP.
**Actions :**
1. Saisir un SIREN/SIRET valide en clé mais absent de l'annuaire simulé.
2. Taper Rechercher.

**Résultat attendu :** bannière « Aucune entreprise ne correspond à cet identifiant dans l'annuaire. » (`directory_not_found`).

### [MOB-DIR-04] Entreprise en franchise en base (sans TVA)
**Type :** Passant / cas limite
**Préconditions :** Résultat correspondant à une entreprise sans numéro de TVA.
**Actions :**
1. Rechercher cette entreprise.

**Résultat attendu :** le champ TVA affiche « Non assujettie à la TVA (franchise en base) » plutôt qu'un champ vide ou une erreur.

### [MOB-DIR-05] Badge de routage PPF vs PDP
**Type :** Passant
**Préconditions :** Deux entreprises connues, l'une routée PPF, l'autre via un PDP.
**Actions :**
1. Rechercher chacune successivement.

**Résultat attendu :** badge bleu « PPF » pour la première, badge violet « PDP » avec un identifiant de plateforme (`directory_pdp_identifier`) pour la seconde.

### [MOB-DIR-06] Non-régression — l'Annuaire DGFIP ignore la bascule de langue
**Type :** Non passant / non-régression
**Préconditions :** Écran Annuaire DGFIP affiché en français, résultat visible.
**Actions :**
1. Ouvrir le sélecteur de langue dans l'en-tête et basculer sur EN.
2. Revenir sur l'onglet Annuaire DGFIP (ou l'observer si déjà visible).

**Résultat attendu :** **l'écran reste entièrement en français** — ce module n'utilise pas le système d'internationalisation (`tr()`), contrairement au reste de l'application. Ce n'est pas une anomalie à signaler comme bloquante, mais un point de vigilance produit à consigner (incohérence connue).

### 3.3 Sélecteur de client dans un formulaire (US-11)

### [MOB-CLI-11] Sélection d'un client existant par suggestion
**Type :** Passant
**Préconditions :** Formulaire de facture ouvert, au moins un client existant dont le nom commence par « Bou ».
**Actions :**
1. Dans le champ client du formulaire, taper « Bou ».
2. Observer la liste de suggestions apparaissant **sous le champ** (pas un menu déroulant flottant).
3. Taper sur la suggestion correspondante.

**Résultat attendu :**
- Après l'étape 2 : suggestions filtrées affichées en ligne, chacune sur une carte cliquable.
- Après l'étape 3 : les champs SIRET et Email du formulaire se remplissent automatiquement avec les données de la fiche sélectionnée.

### [MOB-CLI-12] Création rapide d'un nouveau client depuis le formulaire
**Type :** Passant
**Préconditions :** Formulaire de facture ouvert, aucun client existant ne correspond à la saisie.
**Actions :**
1. Taper un nom de société inexistant dans le champ client.
2. Observer l'apparition du bouton vert « + Ajouter comme nouveau client » (`ADD_NEW_CLIENT_BTN`).
3. Taper ce bouton, remplir Nom/SIRET/Email dans la boîte « Nouveau client » (`QUICK_CLIENT_DIALOG`), taper « Enregistrer ».

**Résultat attendu :**
- Le bouton n'apparaît **que** lorsque : le formulaire est actif, aucun client n'est déjà sélectionné, la saisie n'est pas vide et aucune suggestion ne correspond.
- Après l'enregistrement : la boîte se ferme, le nouveau client est sélectionné dans le formulaire (SIRET/email remplis) **et** créé dans l'annuaire Clients.

### [MOB-CLI-13] Création rapide — SIRET sans clé de Luhn valide
**Type :** Non passant
**Préconditions :** Boîte « Nouveau client » ouverte depuis un formulaire.
**Actions :**
1. Saisir un SIRET de 14 chiffres dont la clé de Luhn est incorrecte.
2. Taper « Enregistrer ».

**Résultat attendu :** erreur « SIRET invalide : 14 chiffres et clé de Luhn correcte » — **règle plus stricte** que la création classique côté onglet Clients (qui ne vérifie que la longueur) : ici, la clé de Luhn est également contrôlée.

---

## 4. Saisie des Factures & Devis

### 4.1 Formulaire classique de facture

### [MOB-INV-01] Enregistrement d'un brouillon — nominal
**Type :** Passant
**Préconditions :** Onglet Factures, formulaire de création ouvert (mode « Mode Formulaire » sélectionné par défaut).
**Actions :**
1. Sélectionner ou créer un client (voir 3.3).
2. Renseigner Nº de facture, Date d'émission et Date d'échéance au format AAAA-MM-JJ.
3. Renseigner une ligne : Libellé, Qté, PU HT ; sélectionner un taux de TVA.
4. Taper « 💾 Enregistrer le brouillon ».

**Résultat attendu :**
- Après l'étape 3 : la section « Récapitulatif » (Total HT / Total TVA / Total TTC) se met à jour en temps réel.
- Après l'étape 4 : message « Facture émise et persistée avec succès » (ou équivalent brouillon) ; la facture apparaît en liste avec le statut « Brouillon ».

### [MOB-INV-02] Émission d'une facture — nominal
**Type :** Passant
**Préconditions :** Formulaire complet et valide.
**Actions :**
1. Compléter tous les champs obligatoires.
2. Taper « ☁ Valider et émettre ».

**Résultat attendu :** la facture passe directement au statut « Déposée » ; le formulaire se ferme ou navigue vers le détail/la liste.

### [MOB-INV-03] Émission bloquée — numéro de facture manquant
**Type :** Non passant
**Préconditions :** Formulaire rempli sauf le Nº de facture.
**Actions :**
1. Taper « Valider et émettre » sans avoir renseigné le numéro.

**Résultat attendu :** le champ Nº de facture s'entoure de rouge, message « Le numéro de facture est requis » ; le formulaire reste ouvert. Le bouton n'était **pas** désactivé au préalable — l'erreur se révèle au clic.

### [MOB-INV-04] Émission bloquée — format de date invalide
**Type :** Non passant
**Préconditions :** Formulaire par ailleurs valide.
**Actions :**
1. Saisir une date d'émission au format JJ/MM/AAAA (ex. « 24/06/2026 ») au lieu d'AAAA-MM-JJ.
2. Taper « Valider et émettre ».

**Résultat attendu :** erreur « Date attendue au format AAAA-MM-JJ » sous le champ concerné.

### [MOB-INV-05] Émission bloquée — SIRET client invalide
**Type :** Non passant
**Préconditions :** Formulaire par ailleurs valide, client saisi manuellement (pas via suggestion).
**Actions :**
1. Saisir un SIRET client de longueur incorrecte.
2. Taper « Valider et émettre ».

**Résultat attendu :** erreur « Le SIRET doit comporter exactement 14 chiffres » sur le champ SIRET client.

### [MOB-INV-06] Émission bloquée — ligne invalide (quantité)
**Type :** Non passant
**Préconditions :** Une ligne avec une quantité à 0 ou négative/non entière.
**Actions :**
1. Saisir « 0 » dans le champ Qté d'une ligne.
2. Taper « Valider et émettre ».

**Résultat attendu :** erreur « La quantité doit être un entier positif » sur la ligne concernée ; le total ne prend pas en compte cette ligne tant qu'elle est invalide.

### [MOB-INV-07] Ajout et suppression de lignes
**Type :** Passant
**Préconditions :** Formulaire avec une seule ligne.
**Actions :**
1. Taper « + Ajouter une ligne » deux fois (3 lignes au total).
2. Taper le bouton de suppression sur la 2ᵉ ligne.
3. Continuer à supprimer jusqu'à revenir à 1 seule ligne.
4. Observer le bouton de suppression sur la dernière ligne restante.

**Résultat attendu :**
- Après l'étape 1 : 3 blocs de saisie de ligne distincts, chacun avec son propre bouton de suppression.
- Après l'étape 2 : 2 lignes restantes, la numérotation/l'ordre des lignes restantes est cohérent.
- Après l'étape 4 : le bouton de suppression de la **dernière** ligne est **désactivé** — une facture doit toujours avoir au moins une ligne.

### [MOB-INV-08] Bascule du taux de TVA sur une ligne
**Type :** Passant
**Préconditions :** Une ligne avec quantité et prix unitaire renseignés.
**Actions :**
1. Ouvrir le sélecteur de taux de TVA de la ligne.
2. Choisir un taux différent (ex. 5,5 % au lieu de 20 %).

**Résultat attendu :** le Total TVA et le Total TTC du récapitulatif se recalculent immédiatement avec le nouveau taux ; le Total HT reste inchangé.

### [MOB-INV-09] Bascule des pénalités B2B — mention légale
**Type :** Passant
**Préconditions :** Formulaire ouvert, case « Appliquer les pénalités de retard légales (B2B) » cochée par défaut.
**Actions :**
1. Défiler jusqu'au pied de page légal et lire le texte affiché.
2. Décocher la case (toucher n'importe où sur la ligne complète, pas seulement la case à cocher).
3. Relire le pied de page.

**Résultat attendu :**
- Avant décochage : texte « En cas de retard de paiement, une pénalité égale à 3 fois le taux d'intérêt légal sera appliquée, ainsi qu'une indemnité forfaitaire de 40€ pour frais de recouvrement conformément à l'article L.441-10 du Code de commerce. »
- Après décochage : texte remplacé par « Merci pour votre confiance. » — le pied de page **n'est jamais vide**, seul le texte change, au même endroit.
- La cible tactile de la case couvre toute la ligne (pas seulement le petit carré), hauteur ≥ 48 dp.

### [MOB-INV-10] Bascule du mode de saisie sans perte de données
**Type :** Passant
**Préconditions :** Formulaire classique partiellement rempli (client, une ligne).
**Actions :**
1. Ouvrir le sélecteur « Mode de saisie » en haut du formulaire.
2. Basculer sur « Mode Page Blanche » (`invoice_mode_canvas_btn`).
3. Rebasculer sur « Mode Formulaire ».

**Résultat attendu :** aucune donnée saisie n'est perdue lors des deux bascules — client, ligne, montants identiques dans les deux modes. Le mode choisi n'est **pas** persisté sur le fond de données, seulement affiché.

### [MOB-INV-11] Panneau de conformité absent en mode Page Blanche
**Type :** Passant / non-régression
**Préconditions :** Formulaire en Mode Page Blanche.
**Actions :**
1. Défiler jusqu'en bas de la feuille A4 simulée.

**Résultat attendu :** contrairement au Mode Formulaire, **aucun panneau d'audit de conformité n'apparaît** en mode Page Blanche — c'est un choix délibéré pour ne pas casser l'illusion de « feuille de papier ».

### 4.2 Mode Canvas / Page Blanche (US-15, US-23)

### [MOB-INV-12] Édition directe sur la feuille A4
**Type :** Passant
**Préconditions :** Formulaire en Mode Page Blanche (`invoice_canvas_container` → `invoice_canvas_page`).
**Actions :**
1. Taper directement dans une cellule du tableau de lignes (`invoice_canvas_items_table`) et saisir un libellé.
2. Observer la bordure du champ pendant la saisie.

**Résultat attendu :** le champ, transparent au repos, affiche un discret contour bleu au focus ; la cible tactile de chaque cellule fait au moins 48 dp.

### [MOB-INV-13] En-tête émetteur en lecture seule vs client éditable
**Type :** Passant / non-régression
**Préconditions :** Mode Page Blanche affiché.
**Actions :**
1. Tenter de taper sur le bloc émetteur (nom/SIRET, à gauche de l'en-tête).
2. Taper sur le bloc client (à droite).

**Résultat attendu :** le bloc émetteur ne réagit à aucune saisie (lecture seule — vient des Paramètres fiscaux) ; le bloc client est éditable en place, champ par champ.

### [MOB-INV-14] Totaux et ventilation TVA en temps réel
**Type :** Passant
**Préconditions :** Une ligne en cours de saisie, prix unitaire non encore valide (ex. champ vide).
**Actions :**
1. Observer le Total HT pendant que le prix unitaire est vide/invalide.
2. Compléter un prix unitaire valide.
3. Observer la ventilation par taux de TVA (`vatBreakdownTag(rate)`) en bas de la feuille.

**Résultat attendu :**
- Étape 1 : le total affiche 0 tant que la ligne est incomplète (pas d'erreur brute, un montant neutre).
- Étape 2-3 : totaux HT/TVA/TTC et ventilation par taux mis à jour immédiatement, alignés à droite au format « Libellé : montant ».

### [MOB-INV-15] Persistance du mode après rotation
**Type :** Passant / non-régression
**Préconditions :** Mode Page Blanche sélectionné.
**Actions :**
1. Faire pivoter l'appareil (portrait → paysage → portrait), ou provoquer une recomposition (changement de fenêtre).

**Résultat attendu :** le mode Page Blanche reste sélectionné après la rotation (état conservé via `rememberSaveable`).

### 4.3 Devis

> ⚠️ Non-régression à vérifier : l'écran Devis utilise des libellés **français codés en dur**, hors système i18n — il ne bascule pas en anglais avec le sélecteur de langue (contrairement au formulaire de facture).

### [MOB-QUO-01] Création d'un devis — nominal
**Type :** Passant
**Préconditions :** Accès au formulaire Devis (`quote_form_screen`), en général depuis la relance d'un devis existant ou une action de création dédiée.
**Actions :**
1. Renseigner Numéro de devis, Date d'émission, Date de validité.
2. Renseigner Émetteur (nom/SIREN/SIRET) si non déjà pré-rempli.
3. Sélectionner un destinataire via le sélecteur de client, puis SIREN/SIRET destinataire.
4. Ajouter une ligne (libellé, qté, PU HT, taux de TVA).
5. Taper « Créer le devis ».

**Résultat attendu :** message « Devis créé avec succès » ; aucun panneau de conformité, aucune bascule Factur-X ni B2B n'existe sur cet écran (absents par construction — le devis n'est pas une pièce Factur-X).

### [MOB-QUO-02] Validation — champs obligatoires
**Type :** Non passant
**Préconditions :** Formulaire Devis partiellement rempli.
**Actions :**
1. Laisser le Numéro de devis vide, taper « Créer le devis ».

**Résultat attendu :** erreur « Le numéro de devis est requis ». Mêmes règles de dates (« Date attendue au format AAAA-MM-JJ ») et de lignes que le formulaire de facture (« Le libellé est requis », « La quantité doit être un entier positif », « Le prix unitaire HT doit être un montant positif »).

### [MOB-QUO-03] Non-régression — le formulaire Devis ignore la bascule de langue
**Type :** Non passant / non-régression
**Préconditions :** Formulaire Devis ouvert.
**Actions :**
1. Basculer la langue en EN via l'en-tête.

**Résultat attendu :** les libellés du formulaire Devis (« Devis », « Numéro de devis », « Émetteur », « Destinataire »…) **restent en français** — à consigner comme incohérence produit connue, pas comme un bug bloquant nouveau.

### 4.4 Liste des factures & filtres

### [MOB-INV-16] Filtrage par statut
**Type :** Passant
**Préconditions :** Onglet Factures, plusieurs factures de statuts variés en base.
**Actions :**
1. Taper successivement les puces de filtre : Toutes, Brouillons, Déposées, Approuvées, Encaissées, En retard, Rejetées, Refusées, Annulées.

**Résultat attendu :** la liste ne montre que les factures dont le statut correspond au filtre actif à chaque tap ; « Toutes » réaffiche l'ensemble.

### [MOB-INV-17] Liste vide sur un filtre sans résultat
**Type :** Non passant / cas limite
**Préconditions :** Aucune facture au statut « Rejetées ».
**Actions :**
1. Sélectionner le filtre « Rejetées ».

**Résultat attendu :** message d'état vide (`invoice_list_empty`), pas une liste blanche silencieuse.

### [MOB-INV-18] Cadenas sur une facture verrouillée
**Type :** Passant / non-régression
**Préconditions :** Une facture au statut Brouillon et une autre au statut Déposée.
**Actions :**
1. Observer les deux cartes dans la liste.

**Résultat attendu :** la carte de la facture Déposée affiche une icône de cadenas ; la carte Brouillon n'en affiche aucune.

### [MOB-INV-19] Action « Créer un avoir » proposée sous une facture éligible
**Type :** Passant
**Préconditions :** Une facture finalisée (Déposée/Approuvée/Encaissée) non encore créditée.
**Actions :**
1. Repérer la carte de cette facture dans la liste.

**Résultat attendu :** un bouton/lien « Créer un avoir » est visible sous cette carte (`creditNoteAction(number)`) ; il est **absent** sous une facture déjà créditée ou encore au statut Brouillon.

### [MOB-INV-20] Échec de chargement — nouvelle tentative
**Type :** Non passant
**Préconditions :** Backend local indisponible.
**Actions :**
1. Ouvrir l'onglet Factures.
2. Taper le bouton de nouvelle tentative (`invoice_list_retry_button`).

**Résultat attendu :** message « Impossible de joindre le serveur Ledger local. Vérifiez que le backend de développement est démarré, puis réessayez. » ; le bouton relance le chargement sans redémarrer l'application.

### 4.5 Détail d'une facture — cycle de vie & verrouillage

### [MOB-INV-21] Facture Brouillon — édition libre
**Type :** Passant
**Préconditions :** Ouvrir le détail d'une facture au statut Brouillon.
**Actions :**
1. Taper « Modifier ».

**Résultat attendu :** le bouton est actif (non grisé) ; le formulaire de facture s'ouvre pré-rempli, entièrement modifiable.

### [MOB-INV-22] Facture Déposée — édition bloquée
**Type :** Non passant / non-régression
**Préconditions :** Détail d'une facture au statut Déposée.
**Actions :**
1. Observer le bouton « Modifier ».
2. Taper dessus malgré tout.

**Résultat attendu :** le bouton est visuellement **désactivé** ; un texte d'aide avec un cadenas rappelle qu'elle est verrouillée ; un tap sur le bouton désactivé ne déclenche **aucune** navigation vers le formulaire.

### [MOB-INV-23] Facture Encaissée (PAID) — édition bloquée
**Type :** Non passant / non-régression
**Préconditions :** Détail d'une facture au statut Encaissée.
**Actions :**
1. Observer le bouton « Modifier ».

**Résultat attendu :** identique au cas Déposée — bouton désactivé, aide contextuelle affichée.

### [MOB-INV-24] Facture Annulée — lecture seule totale
**Type :** Non passant / non-régression
**Préconditions :** Détail d'une facture au statut Annulée.
**Actions :**
1. Observer l'écran dans son ensemble.

**Résultat attendu :** une bannière rouge de lecture seule s'affiche ; la section de cycle de vie (boutons de transition) **n'existe pas du tout** dans l'écran — pas seulement désactivée.

### [MOB-INV-25] Transition DRAFT → DEPOSITED
**Type :** Passant
**Préconditions :** Détail d'une facture Brouillon.
**Actions :**
1. Dans la section « Cycle de vie », taper « Marquer déposée ».

**Résultat attendu :** seul ce bouton est proposé depuis Brouillon (aucun bouton « Encaissée »/« Rejetée » n'existe à ce stade) ; après confirmation, le statut passe à Déposée sans exiger de motif.

### [MOB-INV-26] Transition DEPOSITED → REJECTED (motif obligatoire)
**Type :** Non passant / cas limite processus
**Préconditions :** Facture Déposée.
**Actions :**
1. Taper le bouton de transition vers « Rejetée ».
2. Observer le bouton de confirmation sans rien saisir dans le champ motif.
3. Saisir un motif, ex. « SIRET destinataire invalide ».
4. Confirmer.

**Résultat attendu :**
- Étape 2 : le bouton de confirmation (`TRANSITION_CONFIRM`) reste désactivé tant que le motif est vide.
- Étape 4 : la facture passe au statut Rejetée ; le motif saisi sera repris dans la piste d'audit (voir 4.7).

### [MOB-INV-27] Transition DEPOSITED → PAID (aucun motif requis)
**Type :** Passant
**Préconditions :** Facture Déposée.
**Actions :**
1. Taper le bouton de transition vers « Encaissée ».
2. Confirmer immédiatement sans saisir de motif.

**Résultat attendu :** la confirmation aboutit sans exiger de texte — seules les transitions **négatives** (Rejetée, Refusée) imposent un motif.

### [MOB-INV-28] Réouverture d'une facture Rejetée
**Type :** Passant
**Préconditions :** Facture au statut Rejetée.
**Actions :**
1. Observer les boutons de cycle de vie disponibles.
2. Taper « Reprendre en brouillon ».

**Résultat attendu :** c'est **l'unique** action proposée depuis Rejetée ; après confirmation, le statut repasse à Brouillon et la facture redevient éditable (voir MOB-INV-21).

### [MOB-INV-29] Facture Refusée — aucune action directe
**Type :** Non passant / non-régression
**Préconditions :** Facture au statut Refusée.
**Actions :**
1. Observer l'écran de détail.

**Résultat attendu :** la section « Cycle de vie » **n'existe pas** — la seule sortie possible est un avoir (voir 4.6), jamais un bouton de transition directe.

### [MOB-INV-30] Transition interdite refusée par le système
**Type :** Non passant
**Préconditions :** Situation forcée (harnais ou séquence d'actions) tentant PAID → DRAFT.
**Actions :**
1. Tenter cette transition si un chemin d'accès existe dans l'UI (normalement impossible depuis l'interface normale).

**Résultat attendu :** message d'erreur explicite type « Transition interdite : une facture PAID ne peut pas passer à DRAFT » (`TRANSITION_ERROR`), jamais un plantage silencieux.

### [MOB-INV-31] CANCELLED n'est jamais un bouton direct
**Type :** Passant / non-régression
**Préconditions :** Facture à n'importe quel statut permettant théoriquement une annulation (Déposée, Approuvée, Encaissée, Refusée).
**Actions :**
1. Parcourir tous les boutons de cycle de vie proposés à ces statuts.

**Résultat attendu :** **aucun bouton « Annulée » n'existe jamais** dans la section cycle de vie — l'annulation ne passe que par l'émission d'un avoir (transaction atomique dédiée, voir 4.6).

### 4.6 Avoir (annulation comptable par avoir, US-05/US-10)

### [MOB-INV-32] Émission d'un avoir — nominal
**Type :** Passant
**Préconditions :** Facture Encaissée éligible, jamais créditée.
**Actions :**
1. Depuis le détail de la facture (ou la liste, MOB-INV-19), taper « Créer un avoir ».
2. Vérifier le badge « AVOIR EN BROUILLON » et la référence croisée à la facture d'origine (`credit_note_form_cross_reference`).
3. Sélectionner un motif prédéfini (Erreur de facturation / Remise commerciale / Retour de marchandise).
4. Taper « Valider l'avoir ».

**Résultat attendu :**
- Étape 2 : le bloc destinataire, le numéro de facture d'origine (`credit_note_form_invoice_id`) sont visibles et non modifiables.
- Étape 4 : l'avoir est émis, ses lignes recopient (en négatif) celles de la facture, le total TTC de l'avoir est affiché négativement, et la facture d'origine ne peut plus être créditée une seconde fois.

### [MOB-INV-33] Motif « Autre motif » — texte libre obligatoire
**Type :** Non passant
**Préconditions :** Formulaire d'avoir ouvert.
**Actions :**
1. Sélectionner le motif « Autre motif » (`reasonChip(OTHER)`).
2. Laisser le champ de texte libre (`credit_note_form_reason_free_text`) vide, taper Valider.

**Résultat attendu :** erreur bloquante — un motif « Autre » sans texte n'est pas accepté (le motif légal est un champ obligatoire).

### [MOB-INV-34] Double émission d'avoir bloquée
**Type :** Non passant
**Préconditions :** Une facture déjà créditée par un avoir précédent.
**Actions :**
1. Tenter d'accéder à nouveau au flux de création d'avoir pour cette même facture.

**Résultat attendu :** l'action « Créer un avoir » n'est plus proposée dans la liste/détail (voir MOB-INV-19), ou une bannière de blocage (`credit_note_form_blocked_banner`) apparaît si l'écran est atteint malgré tout.

### 4.7 Piste d'audit (US-17)

### [MOB-INV-35] Les quatre jalons sur une facture Encaissée
**Type :** Passant
**Préconditions :** Détail d'une facture ayant traversé Brouillon → Déposée → Encaissée.
**Actions :**
1. Défiler jusqu'à la piste d'audit (`invoice_audit_trail_panel`).

**Résultat attendu :** quatre jalons dans l'ordre : **Créée** (toujours fait, ✓ vert), **Scellée** (toujours fait, avec une empreinte SHA-256 tronquée affichée), **PPF** (fait — la facture a bien été transmise), **Décision** (fait, badge vert conforme au statut Encaissée).

### [MOB-INV-36] Facture Rejetée — motif affiché sur le jalon Décision
**Type :** Passant
**Préconditions :** Facture rejetée avec le motif « SIRET destinataire invalide » (voir MOB-INV-26).
**Actions :**
1. Ouvrir le détail, défiler jusqu'à la piste d'audit.

**Résultat attendu :** le jalon Décision est en **rouge** (« ! »), affiche le motif exact saisi lors du rejet et le libellé de statut « Rejetée par la plateforme ».

### [MOB-INV-37] Rejet puis réouverture — le jalon PPF reste acquis
**Type :** Passant / cas limite important
**Préconditions :** Facture Déposée puis Rejetée puis reprise en Brouillon (voir MOB-INV-28), puis ré-émise (Déposée à nouveau).
**Actions :**
1. Observer le jalon PPF après la réémission.

**Résultat attendu :** le jalon **PPF reste marqué « fait »** — il ne redevient pas « en attente » lors de la correction, car la transmission a réellement eu lieu la première fois. Une piste d'audit ne réécrit jamais l'histoire.

### [MOB-INV-38] Facture Brouillon jamais soumise
**Type :** Passant / cas limite
**Préconditions :** Facture au statut Brouillon, jamais déposée.
**Actions :**
1. Ouvrir la piste d'audit.

**Résultat attendu :** Créée = fait, Scellée = fait, PPF = **en attente** (gris), Décision = **en attente** (gris) — cohérent avec un document jamais transmis.

### [MOB-INV-39] Horodatage en UTC
**Type :** Passant / non-régression
**Préconditions :** N'importe quel jalon avec horodatage.
**Actions :**
1. Comparer l'heure affichée à l'heure locale de l'appareil (si différente du fuseau UTC).

**Résultat attendu :** l'heure affichée est explicitement en UTC (suffixe « UTC » visible), **jamais convertie** vers le fuseau local — c'est l'heure UTC qui fait foi dans une piste d'audit.

---

## 5. Conformité 2026 (audit dans le formulaire de facture)

### [MOB-COMP-01] Aucun contrôle effectué au départ
**Type :** Passant / cas limite
**Préconditions :** Formulaire de facture fraîchement ouvert, aucun scan lancé.
**Actions :**
1. Défiler jusqu'au panneau « Audit de conformité 2026 » (`compliance_panel`), après la section B2B et avant les boutons d'action.

**Résultat attendu :** message « Aucun contrôle effectué pour l'instant. » ; aucune checklist ni bandeau visible.

### [MOB-COMP-02] Scan sur une facture entièrement conforme
**Type :** Passant
**Préconditions :** Formulaire rempli sans aucune anomalie : SIRET émetteur et client valides (clé de Luhn correcte), TVA valide, mentions B2B activées, Factur-X activé, au moins une ligne valide.
**Actions :**
1. Taper « Scanner la conformité » (`compliance_scan_btn`).

**Résultat attendu :** checklist des 4 contrôles (`compliance_checklist`) tous verts : Identifiants SIRET, Numéro de TVA intracommunautaire, Mentions légales obligatoires, Structure Factur-X 2026 ; message global « Facture conforme à la norme Factur-X 2026. », aucun bandeau d'alerte.

### [MOB-COMP-03] Scan avec avertissement — clé de Luhn du SIRET client fausse
**Type :** Non passant / cas limite
**Préconditions :** SIRET client à 14 chiffres mais dont la clé de Luhn est incorrecte, reste conforme.
**Actions :**
1. Taper « Scanner la conformité ».

**Résultat attendu :** bandeau **ambre** « Conformité incomplète » (`compliance_alert`, titre `COMPLIANCE_ALERT_WARNING_TITLE`) — pas rouge ; le contrôle SIRET affiche un avertissement (texte de contrôle SIRET) plutôt qu'un blocage — un identifiant plausible mais douteux reste un avertissement, pas une erreur.

### [MOB-COMP-04] Scan avec TVA absente (franchise en base)
**Type :** Passant / cas limite
**Préconditions :** Numéro de TVA émetteur non renseigné (franchise en base légitime).
**Actions :**
1. Taper « Scanner la conformité ».

**Résultat attendu :** le contrôle TVA passe en **avertissement**, pas en échec — la franchise en base est un cas légal, pas une anomalie.

### [MOB-COMP-05] Scan avec mentions légales manquantes
**Type :** Non passant
**Préconditions :** Case « Appliquer les pénalités de retard légales (B2B) » décochée.
**Actions :**
1. Taper « Scanner la conformité ».

**Résultat attendu :** contrôle « Mentions légales obligatoires » en avertissement/échec selon la gravité définie ; le bandeau global reflète la présence de cette anomalie.

### [MOB-COMP-06] Scan avec Factur-X désactivé
**Type :** Non passant
**Préconditions :** Bascule « Générer au format légal Factur-X » décochée.
**Actions :**
1. Taper « Scanner la conformité ».

**Résultat attendu :** le contrôle « Structure Factur-X 2026 » signale que la génération est désactivée sur cette facture.

### [MOB-COMP-07] Revalidation dynamique — le rapport se périme à la frappe
**Type :** Passant / non-régression **critique**
**Préconditions :** Un scan vient d'être exécuté, la checklist est affichée.
**Actions :**
1. Modifier n'importe quel champ du formulaire (ex. rebasculer la case B2B, changer une ligne).

**Résultat attendu :** la checklist et le bandeau **disparaissent immédiatement** — un rapport de conformité ne doit jamais rester affiché comme actuel après une modification qui pourrait l'invalider. Il faut relancer un scan pour obtenir un nouveau rapport.

### [MOB-COMP-08] Cible tactile du bouton de scan
**Type :** Passant / non-régression
**Préconditions :** Panneau de conformité visible.
**Actions :**
1. Mesurer/observer la zone tactile du bouton « Scanner la conformité ».

**Résultat attendu :** hauteur ≥ 48 dp, joignable après défilement même en bas d'un long formulaire.

---

## 6. Export Comptable

### [MOB-EXP-01] Ouverture de la modale d'export
**Type :** Passant
**Préconditions :** N'importe quel écran du shell portant le déclencheur d'export dans l'en-tête (`export_modal_trigger`).
**Actions :**
1. Taper l'icône 📤 dans l'en-tête.

**Résultat attendu :** une feuille modale (`export_modal_dialog`) s'ouvre depuis le bas, avec une section « Période d'export » (champs « Du » / « Au ») et une section « Format de sortie » listant 3 cartes.

### [MOB-EXP-02] Sélection de chaque format
**Type :** Passant
**Préconditions :** Modale ouverte.
**Actions :**
1. Taper successivement les 3 cartes : « Format FEC Officiel » (`export_format_fec`), « Archive Factur-X complète » (`export_format_facturx`), « Synthèse Excel » (`export_format_excel`).

**Résultat attendu :** chaque tap sélectionne visuellement une seule carte à la fois (état exclusif) ; les sous-titres affichés correspondent : « Écritures comptables opposables », « Toutes les pièces de la période », « Récapitulatif pour votre tableur ».

### [MOB-EXP-03] Période invalide — dates inversées
**Type :** Non passant
**Préconditions :** Modale ouverte.
**Actions :**
1. Saisir dans « Du » une date postérieure à celle saisie dans « Au ».
2. Taper « Générer l'archive » (`export_generate_btn`).

**Résultat attendu :** erreur « La date de fin doit suivre la date de début » (`EXPORT_PERIOD_INVALID`) ; aucune génération ne démarre.

### [MOB-EXP-04] Génération — animation et téléchargement
**Type :** Passant
**Préconditions :** Période et format valides sélectionnés.
**Actions :**
1. Taper « Générer l'archive ».
2. Observer la barre de progression (`export_progress_bar`) pendant la génération.
3. Une fois terminé, taper « Télécharger l'archive (.zip) » (`export_download_btn`).

**Résultat attendu :**
- Étape 2 : la barre progresse visuellement de 0 à 100 % (pas un saut instantané).
- Étape 3 : le bouton « Générer l'archive » a été remplacé par « Télécharger l'archive (.zip) » ; le fichier est proposé à l'enregistrement/au partage par le système.

### [MOB-EXP-05] Fermeture pendant une génération en cours
**Type :** Non passant / cas limite
**Préconditions :** Génération en cours (barre de progression visible).
**Actions :**
1. Fermer la modale (croix, geste retour, ou tap en dehors) avant la fin de la génération.

**Résultat attendu :** la génération en vol est annulée proprement ; aucune archive partielle ni notification erronée de succès n'apparaît après la fermeture.

### [MOB-EXP-06] Aucune facture sur la période
**Type :** Non passant / cas limite
**Préconditions :** Période sélectionnée sans aucune facture émise.
**Actions :**
1. Générer l'archive sur cette période.

**Résultat attendu :** l'export aboutit sans planter (archive vide ou message informatif — vérifier qu'aucune exception n'est visible côté UI).

### [MOB-EXP-07] Feuille joignable sur un écran compact (non-régression US-22)
**Type :** Passant / non-régression
**Préconditions :** Émulateur Pixel 5 (851 dp de hauteur).
**Actions :**
1. Ouvrir la modale, défiler jusqu'au bouton de génération tout en bas.

**Résultat attendu :** le bouton « Générer l'archive » est atteignable par défilement, jamais masqué sous la barre système — historique du bug corrigé en US-22 (feuille ancrée en bas d'une fenêtre débordant sous les barres système, sans défilement possible).

---

## 7. Paramètres Fiscaux

### [MOB-SET-01] Affichage des paramètres existants
**Type :** Passant
**Préconditions :** Onglet Paramètres (`settings_screen`), au moins une sauvegarde antérieure existante.
**Actions :**
1. Ouvrir l'onglet.

**Résultat attendu :** Raison sociale (`settings_issuer_name`), SIRET (`settings_issuer_siret`), SIREN déduit en lecture seule (`settings_derived_siren`), TVA intracommunautaire (`settings_vat_number`), taux de TVA par défaut, bascule Factur-X — tous préremplis avec les valeurs sauvegardées.

### [MOB-SET-02] SIREN dérivé automatiquement
**Type :** Passant / non-régression
**Préconditions :** Onglet Paramètres.
**Actions :**
1. Modifier le champ SIRET.
2. Observer le champ SIREN.

**Résultat attendu :** le SIREN se met à jour automatiquement (9 premiers chiffres du SIRET saisi), **sans être éditable directement**.

### [MOB-SET-03] Enregistrement — SIRET invalide
**Type :** Non passant
**Préconditions :** Onglet Paramètres.
**Actions :**
1. Saisir un SIRET de longueur incorrecte.
2. Taper « Enregistrer les paramètres » (`settings_save_button`).

**Résultat attendu :** erreur « Le SIRET doit comporter exactement 14 chiffres » ; l'enregistrement est bloqué.

### [MOB-SET-04] Enregistrement — numéro de TVA mal formé
**Type :** Non passant
**Préconditions :** Onglet Paramètres.
**Actions :**
1. Saisir un numéro de TVA qui ne respecte pas le format `FRXX999999999` (ex. « FR12 »).
2. Taper Enregistrer.

**Résultat attendu :** erreur « Le numéro de TVA doit être au format FRXX999999999 ».

### [MOB-SET-05] Numéro de TVA vide accepté (franchise en base)
**Type :** Passant / cas limite
**Préconditions :** Onglet Paramètres.
**Actions :**
1. Vider entièrement le champ TVA.
2. Taper Enregistrer.

**Résultat attendu :** aucune erreur — le champ TVA est optionnel (franchise en base légitime).

### [MOB-SET-06] Auto-majuscule du numéro de TVA
**Type :** Passant / non-régression
**Préconditions :** Onglet Paramètres.
**Actions :**
1. Saisir un numéro de TVA en minuscules (ex. « fr12345678901 »).

**Résultat attendu :** le champ affiche automatiquement le texte en majuscules au fur et à mesure de la frappe.

### [MOB-SET-07] Enregistrement nominal
**Type :** Passant
**Préconditions :** Formulaire valide.
**Actions :**
1. Modifier la Raison sociale.
2. Taper « Enregistrer les paramètres ».

**Résultat attendu :** message de confirmation « Paramètres fiscaux mis à jour avec succès » (Snackbar) ; en faisant pivoter l'écran juste après, la Snackbar **ne rejoue pas**.

### [MOB-SET-08] Répercussion sur un nouveau formulaire de facture
**Type :** Passant / parcours croisé
**Préconditions :** Paramètres fiscaux modifiés (nouvelle Raison sociale, nouveau taux de TVA par défaut).
**Actions :**
1. Enregistrer les nouveaux paramètres.
2. Ouvrir un nouveau formulaire de facture.
3. Ajouter une ligne sans changer le taux de TVA proposé par défaut.

**Résultat attendu :** l'émetteur affiché dans le formulaire (et en mode Canvas) reflète la nouvelle Raison sociale ; le taux de TVA pré-sélectionné sur la nouvelle ligne correspond au nouveau taux par défaut choisi dans les Paramètres.

---

## 8. Intégrations

### [MOB-INT-01] Ouverture du hub
**Type :** Passant
**Préconditions :** N'importe quel écran portant le déclencheur 🧩 dans l'en-tête (`integrations_hub_trigger`).
**Actions :**
1. Taper l'icône Intégrations.

**Résultat attendu :** écran « Hub d'intégrations » (`integrations_hub_container`) avec titre « Hub d'intégrations » et sous-titre « Connectez LedgerHub à vos outils du quotidien », grille de 4 cartes (2×2 sur téléphone).

### [MOB-INT-02] Modules en accès anticipé (BETA)
**Type :** Passant
**Préconditions :** Hub d'intégrations affiché.
**Actions :**
1. Observer les cartes « Paiement en ligne par CB (Stripe) » et « Export FEC Expert-Comptable ».

**Résultat attendu :** ces deux cartes portent un badge BETA saturé (couleur indigo des devis, réutilisée), et sont rendues avec une opacité plus élevée que les cartes verrouillées — perçues comme plus « ouvertes ».

### [MOB-INT-03] Modules annoncés (COMING SOON)
**Type :** Passant / cas limite
**Préconditions :** Hub d'intégrations affiché.
**Actions :**
1. Observer les cartes « Notifications Slack » et « Synchronisation Bancaire API ».
2. Taper sur l'une de ces cartes.

**Résultat attendu :** badge ambre « Coming Soon », carte visuellement désaturée (fond et titre atténués à ~70 % d'opacité) ; un tap ne déclenche **aucune action** — aucun module n'est réellement branché, l'écran ne promet rien qu'il ne tienne.

### [MOB-INT-04] Retour depuis le hub
**Type :** Passant
**Préconditions :** Hub d'intégrations ouvert.
**Actions :**
1. Taper le bouton de retour (« Retour »).

**Résultat attendu :** retour à l'écran précédent, sans perte d'état sur celui-ci (ex. formulaire de facture en cours resterait rempli si le hub avait été ouvert depuis l'en-tête pendant une saisie — à vérifier si l'en-tête est atteignable en surimpression).

---

## 9. Transversal — Navigation, Langue, Thème

### 9.1 En-tête compact vs Sidebar tablette

### [MOB-NAV-01] Cinq commandes visibles en en-tête compact
**Type :** Passant / non-régression **critique**
**Préconditions :** Émulateur Pixel 5 (393 dp de large), portrait.
**Actions :**
1. Observer l'en-tête sur n'importe quel onglet.

**Résultat attendu :** cinq commandes toutes visibles et joignables (≥ 48 dp chacune) : Export 📤, Intégrations 🧩, Palette de commandes 🔍 (réduite à la loupe seule, sans le libellé « Recherche ⌘K »), bascule de thème 🌙/☀️, sélecteur de langue FR/EN — **aucune n'est masquée ni compressée**. Le nom « LedgerHub » reste lisible en entier à gauche.

### [MOB-NAV-02] Bascule vers la sidebar tablette
**Type :** Passant / non-régression
**Préconditions :** Application affichée en largeur ≥ 840 dp (tablette ou émulateur redimensionné, ou rotation paysage sur un grand écran).
**Actions :**
1. Observer la mise en page.

**Résultat attendu :** une sidebar permanente à gauche (248 dp) remplace la barre de navigation basse ; elle porte le nom complet de l'app, les cinq mêmes commandes (avec, cette fois, le badge « ⌘K » visible sur la palette de commandes, réservé à un contexte où un clavier peut être branché), et les six destinations en toutes lettres (Vue d'ensemble, Factures, Clients, Annuaire DGFIP, Rapprochement, Paramètres).

### [MOB-NAV-03] Barre de navigation basse — 6 destinations
**Type :** Passant
**Préconditions :** Largeur compacte.
**Actions :**
1. Taper chacun des 6 onglets de la barre basse : Vue d'ensemble, Factures, Clients, Annuaire DGFIP, Rapprochement, Paramètres.

**Résultat attendu :** chaque tap navigue vers l'écran correspondant sans passer par une transition intermédiaire visible ; l'onglet actif est mis en évidence.

### [MOB-NAV-04] Palette de commandes — raccourci clavier
**Type :** Passant
**Préconditions :** Écran quelconque, en-tête visible.
**Actions :**
1. Taper l'icône loupe de la palette de commandes.

**Résultat attendu :** une boîte de dialogue plein écran (`command_palette_dialog`) s'ouvre avec un champ de recherche et éventuellement des actions rapides (créer une facture, relancer une facture en retard, exporter la comptabilité).

### 9.2 Bascule de langue (US-02)

### [MOB-NAV-05] Bascule FR → EN → FR sur le tableau de bord
**Type :** Passant
**Préconditions :** Application en français par défaut, Dashboard affiché.
**Actions :**
1. Taper le segment « EN » du sélecteur de langue (`lang_toggle_en`).
2. Observer titres, libellés de KPI et formats de montant/date.
3. Retaper le segment « FR » (`lang_toggle_fr`).

**Résultat attendu :**
- Après l'étape 1 : « Vue d'ensemble » devient « Dashboard », les montants passent du format « 1 234,56 € » au format « €1,234.56 » **instantanément**, sans redémarrer l'écran.
- Après l'étape 3 : retour identique à l'état initial (aller-retour complet, sans reliquat anglais).

### [MOB-NAV-06] Réactivité de la langue en profondeur (formulaire de facture)
**Type :** Passant / non-régression
**Préconditions :** Formulaire de facture ouvert avec des données saisies.
**Actions :**
1. Basculer la langue pendant que le formulaire est ouvert.

**Résultat attendu :** tous les libellés du formulaire (sections, boutons, messages d'erreur déjà affichés) changent de langue sans perdre les valeurs saisies par l'utilisateur.

### [MOB-NAV-07] Non-régression — écrans hors périmètre i18n
**Type :** Non passant / non-régression
**Préconditions :** Voir MOB-DIR-06 et MOB-QUO-03.
**Actions :**
1. Basculer en anglais, visiter l'Annuaire DGFIP et le formulaire Devis.

**Résultat attendu :** ces deux écrans restent en français — comportement **attendu et documenté**, à ne pas remonter comme un nouveau défaut à chaque campagne de recette.

### 9.3 Bascule de thème Sombre/Clair (US-25)

### [MOB-NAV-08] Cycle complet DARK → LIGHT → DARK
**Type :** Passant
**Préconditions :** Application au thème sombre par défaut (premier lancement).
**Actions :**
1. Taper le bouton de bascule de thème (`theme_toggle_btn`) — icône ☀️ visible en thème sombre.
2. Observer le repeint de l'écran entier.
3. Taper à nouveau le bouton — icône 🌙 visible en thème clair.

**Résultat attendu :**
- Étape 1-2 : fond, cartes, textes de **tout l'écran** (pas seulement l'en-tête) basculent vers la palette claire ; l'icône du bouton devient une lune (annonce la destination du prochain appui, pas l'état courant).
- Étape 3 : retour identique au thème sombre initial.

### [MOB-NAV-09] Coexistence avec le sélecteur de langue
**Type :** Passant / non-régression **critique**
**Préconditions :** En-tête compact (Pixel 5).
**Actions :**
1. Observer simultanément la bascule de thème et le sélecteur de langue dans l'en-tête.

**Résultat attendu :** les deux commandes sont **entièrement visibles**, avec leur cible tactile pleine (≥ 48 dp chacune) — historique d'un bug corrigé en cours d'US-25 où la 5ᵉ commande comprimait le sélecteur de langue hors de l'écran.

### [MOB-NAV-10] Persistance du thème après redémarrage
**Type :** Passant
**Préconditions :** Thème basculé en Clair.
**Actions :**
1. Fermer complètement l'application (pas seulement mise en arrière-plan).
2. Relancer l'application.

**Résultat attendu :** l'application démarre directement en thème **Clair** — la préférence est persistée en base, pas seulement en mémoire.

### [MOB-NAV-11] Cohérence du thème sur l'écran d'avoir
**Type :** Passant / non-régression
**Préconditions :** Thème basculé en Clair, écran d'avoir ouvert (thème violet/indigo local).
**Actions :**
1. Ouvrir un formulaire d'avoir en thème Clair.

**Résultat attendu :** la dominante violet/indigo de l'écran d'avoir se décline elle aussi en version claire (fond clair, texte sombre) — elle ne reste pas figée en sombre pendant que le reste de l'application est clair.

### [MOB-NAV-12] Thème clair et lisibilité des badges de statut
**Type :** Passant / non-régression
**Préconditions :** Thème Clair actif, liste de factures avec plusieurs statuts visibles.
**Actions :**
1. Observer les badges de statut (Payé/En attente/Brouillon) sur la liste des factures.

**Résultat attendu :** chaque badge garde un contraste net (fond clair, texte saturé — pas l'inverse du thème sombre simplement éclairci) ; aucun badge n'est illisible sur fond blanc.

---

## 10. Rapprochement Bancaire (US-18)

### [MOB-REC-01] Mise en page tablette — deux colonnes permanentes
**Type :** Passant / non-régression
**Préconditions :** Largeur ≥ 840 dp (tablette ou émulateur redimensionné), au moins une transaction et une facture en attente de paiement.
**Actions :**
1. Ouvrir l'onglet « Rapprochement » (`bank_reconciliation_screen`).

**Résultat attendu :** deux colonnes côte à côte, **toutes deux visibles en permanence** — « Transactions bancaires récentes » à gauche, « Factures en attente de paiement » à droite. Contrairement au Dashboard, ce module a bien une rupture de mise en page dédiée à 840 dp.

### [MOB-REC-02] Mise en page téléphone — onglets avec indicateur de sélection
**Type :** Passant / non-régression
**Préconditions :** Largeur compacte (< 840 dp).
**Actions :**
1. Ouvrir l'onglet Rapprochement.
2. Sélectionner une transaction (voir MOB-REC-03), puis basculer sur l'onglet « Factures » (`bank_reconciliation_tab_invoices`).

**Résultat attendu :**
- Une bannière persistante « Sélectionnez une transaction et une facture pour les associer. » (`bank_reconciliation_hint`) reste visible quel que soit l'onglet actif.
- Après la sélection côté Transactions puis le passage à l'onglet Factures : l'onglet « Transactions » (`bank_reconciliation_tab_transactions`) porte désormais un petit point indicateur — la sélection en cours n'est pas perdue en changeant d'onglet, et rien ne le rappelle sauf ce point.

### [MOB-REC-03] Sélection en bascule (toggle)
**Type :** Passant
**Préconditions :** Onglet Rapprochement affiché, au moins une transaction listée.
**Actions :**
1. Taper une carte de transaction (`transactionCard(id)`).
2. Retaper la **même** carte.

**Résultat attendu :** au premier tap, la carte apparaît sélectionnée (mise en évidence) ; au second tap sur la même carte, elle se désélectionne — aucun bouton « Annuler » séparé n'est nécessaire.

### [MOB-REC-04] Bouton d'association — apparition conditionnelle
**Type :** Passant
**Préconditions :** Onglet Rapprochement affiché.
**Actions :**
1. Sélectionner uniquement une transaction (aucune facture).
2. Observer le bas de l'écran.
3. Sélectionner également une facture.

**Résultat attendu :**
- Après l'étape 2 : aucun bouton flottant d'association n'apparaît (une seule des deux sélections ne suffit pas).
- Après l'étape 3 : le bouton flottant « Associer (Lettrage) » (`btn_reconcile_match`) apparaît avec une animation d'apparition (fondu + glissement).

### [MOB-REC-05] Seules les factures légalement encaissables sont listées
**Type :** Passant / non-régression
**Préconditions :** Une facture au statut Brouillon et une autre au statut Déposée, toutes deux impayées.
**Actions :**
1. Observer la colonne/l'onglet « Factures en attente de paiement » (`unpaid_invoices_list`).

**Résultat attendu :** la facture **Déposée** apparaît (DEPOSITED → PAID est une transition légale) ; la facture **Brouillon** n'apparaît **jamais** dans cette liste — la règle n'est pas codée en dur, elle dérive de la machine à états des statuts (`InvoiceStatusTransition.isAllowed`).

### [MOB-REC-06] Association nominale — montants identiques
**Type :** Passant
**Préconditions :** Une transaction et une facture impayée de même montant.
**Actions :**
1. Sélectionner la transaction, puis la facture.
2. Taper « Associer (Lettrage) ».

**Résultat attendu :** les deux sélections se vident après l'association ; les deux cartes portent désormais un badge vert « Rapprochée » ; la facture bascule au statut Encaissée (vérifiable dans l'onglet Factures du shell principal).

### [MOB-REC-07] Association avec écart de montant — avertissement non bloquant
**Type :** Non passant / cas limite
**Préconditions :** Une transaction et une facture de montants différents.
**Actions :**
1. Sélectionner les deux.
2. Observer la carte de la facture avant de confirmer.

**Résultat attendu :** un badge ambre « Écart de montant » apparaît sur la carte de la facture, accompagné d'une ligne « Écart : <montant> ». L'association reste **possible** malgré l'écart — ce n'est qu'un avertissement, pas un blocage.

### [MOB-REC-08] Échec de l'association
**Type :** Non passant
**Préconditions :** Situation provoquant un échec côté dépôt (ex. conflit de concurrence).
**Actions :**
1. Sélectionner une paire valide et taper « Associer (Lettrage) ».

**Résultat attendu :** message d'erreur « Lettrage impossible » (ou détail du dépôt) affiché dans `bank_reconciliation_error` ; les sélections ne sont pas perdues, permettant de réessayer.

### [MOB-REC-09] États vides
**Type :** Non passant / cas limite
**Préconditions :** Aucune transaction bancaire, ou aucune facture en attente de paiement.
**Actions :**
1. Ouvrir l'onglet Rapprochement sur un jeu de données vide.

**Résultat attendu :** « Aucune transaction bancaire à rapprocher. » et/ou « Aucune facture en attente de paiement. » selon le côté vide.

---

## 11. e-Reporting (US-08)

> ⚠️ **Non atteignable depuis le parcours normal de l'application** — comme l'écran de connexion (module 1), `EReportingScreen` et `EReportingViewModel` ne sont référencés nulle part dans `App.kt` ni dans aucun autre écran de production : aucun onglet de la barre basse, aucune sidebar, aucune surimpression n'y mène. Un testeur manuel sur l'APK standard ne peut pas l'atteindre. Les scénarios ci-dessous supposent un harnais de test montant `EReportingScreen` directement, ou une intégration future dans la navigation. Voir l'encart Dette Technique ci-après.

### [MOB-ERP-01] Bandeau de conformité permanent
**Type :** Passant
**Préconditions :** Écran e-Reporting affiché (`ereporting_screen`), harnais de test.
**Actions :**
1. Observer l'en-tête de l'écran.

**Résultat attendu :** bandeau fixe « Conformité DGFIP 2026 — e-Reporting » (`ereporting_badge`), présent quel que soit le contenu de la liste en dessous.

### [MOB-ERP-02] Liste des déclarations — contenu d'une carte
**Type :** Passant
**Préconditions :** Au moins une déclaration existante, statut Brouillon.
**Actions :**
1. Observer une carte de déclaration (`card(id)`) dans la liste (`ereporting_list`).

**Résultat attendu :** la carte affiche la période, le statut (« Brouillon »), le type (« Ventes B2C » / « Opérations internationales » / « Encaissements »), les totaux HT/TVA/TTC et le nombre de transactions. Aucune ligne « Accusé PPF » n'apparaît tant que la déclaration n'est pas acquittée.

### [MOB-ERP-03] Transmission — nominal
**Type :** Passant
**Préconditions :** Une déclaration au statut Brouillon.
**Actions :**
1. Taper « Transmettre PPF » (`transmitButton(id)`) sur cette carte.
2. Observer le libellé du bouton pendant l'appel.

**Résultat attendu :**
- Étape 2 : le bouton passe à « Transmission… » et se désactive (empêche un double-tap).
- Résultat final : Snackbar (`ereporting_snackbar`) « Déclaration transmise au PPF — accusé <numéro> » ; la carte affiche désormais le statut « Acquittée » et une ligne « Accusé PPF : <numéro> » (`ack(id)`) en gras.

### [MOB-ERP-04] Blocage de la transmission concurrente
**Type :** Non passant / cas limite
**Préconditions :** Une transmission en cours sur une déclaration (bouton affichant « Transmission… »).
**Actions :**
1. Taper le bouton de transmission d'une **autre** déclaration Brouillon pendant que la première est en vol.

**Résultat attendu :** le second bouton reste inerte tant que la première transmission n'est pas terminée — une seule transmission à la fois, quelle que soit la déclaration visée.

### [MOB-ERP-05] Nouvelle tentative sur une déclaration déjà acquittée
**Type :** Non passant
**Préconditions :** Une déclaration déjà transmise et acquittée, dont le bouton de transmission serait sollicité à nouveau (ex. via un état incohérent du harnais).
**Actions :**
1. Déclencher une nouvelle transmission sur cette déclaration.

**Résultat attendu :** message explicite « Cette déclaration a déjà été transmise et acquittée (HTTP 409) » — distinct du message d'échec générique, pour ne pas laisser croire à une anomalie réseau.

### [MOB-ERP-06] Échec générique de transmission
**Type :** Non passant
**Préconditions :** Backend PPF simulé en panne.
**Actions :**
1. Taper « Transmettre PPF » sur une déclaration Brouillon.

**Résultat attendu :** Snackbar « Transmission au PPF impossible » ; la déclaration reste au statut Brouillon, le bouton redevient actif pour une nouvelle tentative.

### [MOB-ERP-07] Liste vide
**Type :** Non passant / cas limite
**Préconditions :** Aucune déclaration e-Reporting en base.
**Actions :**
1. Ouvrir l'écran.

**Résultat attendu :** état vide affiché (`ereporting_empty`) plutôt qu'une liste blanche silencieuse.

### [MOB-ERP-08] Non-régression — l'écran ignore la bascule de langue
**Type :** Non passant / non-régression
**Préconditions :** Écran e-Reporting affiché.
**Actions :**
1. Basculer la langue en EN.

**Résultat attendu :** l'écran reste entièrement en français, comme l'Annuaire DGFIP et le formulaire Devis (module non câblé au système `tr()`) — comportement à consigner, pas un nouveau défaut à chaque campagne.

---

## ⚠️ Dette Technique & Anomalies Connues

Cet encart consolide les écarts constatés en explorant le code source pendant la rédaction de ce plan de test — à ne **pas** rouvrir comme des anomalies « nouvelles » à chaque campagne de recette, mais à garder sous les yeux d'un testeur pour qu'il n'y perde pas de temps en diagnostic, et sous ceux d'un product owner pour arbitrage.

### 1. Deux écrans complets, entièrement développés et testés unitairement, sont injoignables depuis l'application
| Écran | Statut du code | Statut de la navigation |
|---|---|---|
| Connexion / Inscription (`login_screen`, US-21) | Implémenté, testé (N1/N2/N3a/N3b) | **Aucune référence dans `App.kt`** — aucun bouton, aucun onglet, aucune surimpression n'y mène |
| e-Reporting (`ereporting_screen`, US-08) | Implémenté, testé | **Aucune référence dans `App.kt`** — même constat |

Vérifié par recherche exhaustive du nom de la classe/fonction dans `commonMain`, `androidMain` et `iosMain` : en dehors de leur propre paquet, aucune occurrence en dehors de mentions en commentaire KDoc. Ce n'est donc pas un oubli d'un seul bouton mais une **absence totale de câblage** — ces deux US sont fonctionnellement complètes et invisibles pour un utilisateur final comme pour un testeur manuel sur l'APK standard.

**Impact recette :** les modules 1 (Auth) et 11 (e-Reporting) de ce plan ne sont exécutables qu'avec un harnais de test dédié (montage direct du composable), jamais en parcourant l'application normalement. Un testeur qui ne trouve pas ces écrans en explorant l'app **ne doit pas conclure à une régression** — c'est l'état actuel, documenté ici.

### 2. Quatre écrans ignorent le système d'internationalisation (`tr()` / `StringKey`)
| Écran | Bascule FR/EN | Constat |
|---|---|---|
| Annuaire DGFIP (`directory_screen`) | ❌ reste en français | 0 appel à `tr()` dans `DirectoryScreen.kt` |
| Formulaire Devis (`quote_form_screen`) | ❌ reste en français | 0 appel à `tr()` dans `QuoteFormScreen.kt` |
| e-Reporting (`ereporting_screen`) | ❌ reste en français | 0 appel à `tr()` dans `EReportingScreen.kt` |
| Rapprochement bancaire (`bank_reconciliation_screen`) | ✅ traduit | 13 appels à `tr()` — **contre-exemple qui confirme que les trois autres sont bien un oubli**, pas une contrainte technique |

La présence du Rapprochement bancaire dans cette liste comme cas conforme élimine l'hypothèse d'une limitation générale : chaque écran non traduit l'est parce que son développement n'a pas suivi la convention `tr(StringKey.X)`, écran par écran, pas parce que l'internationalisation serait impossible à y ajouter.

**Impact recette :** un testeur qui bascule la langue en EN et observe du français sur ces trois écrans **ne doit pas ouvrir une anomalie par campagne** — un seul ticket de dette technique suffit, référençant les trois écrans.

### 3. Deux implémentations parallèles de l'écran de détail de facture
`presentation/invoices/InvoiceDetailScreen.kt` (`InvoiceDetailScreenTags`, largement testée — c'est elle que ce plan cible dans le module 4.5) coexiste avec `presentation/invoicedetail/InvoiceDetailScreen.kt` (`InvoiceDetailTags`, plus réduite). Un testeur qui inspecterait le code sans connaître cette convention pourrait tester la mauvaise implémentation. **Toujours viser le paquet `presentation.invoices`**, jamais `presentation.invoicedetail`.

### 4. Divergences mineures de convention de tag, sans impact fonctionnel
- Les tags de `ClientPicker` (`CLIENT_SEARCH_INPUT`, `ADD_NEW_CLIENT_BTN`…) sont en `UPPER_SNAKE_CASE`, alors que tout le reste de l'application utilise du `snake_case` (`theme_toggle_btn`, `dashboard_screen`…). Sans conséquence pour un testeur manuel — mentionné pour qui utiliserait ces tags dans un outil d'automatisation.
- `InvoiceCanvasTags` (US-23) et `InvoicePaperCanvasTags` (US-15) exposent parfois des noms de constantes différents pour le **même** nœud Compose (un nœud ne porte qu'un seul `testTag`) — pure question de nommage, pas deux éléments distincts à l'écran.

---

## Annexe — Récapitulatif de couverture par User Story

| US | Intitulé | Module(s) de ce plan |
|---|---|---|
| US-01 | Socle réseau Ktor & modèles Factur-X | Transversal (aucun scénario UI dédié — infrastructure) |
| US-02 | ViewModel, gestion d'état, écrans Factur-X | 2, 4.4, 4.5 |
| US-02 (Sprint 2) | Support multilingue FR/EN | 9.2 |
| US-03 | Thème sombre premium, shell responsive | 9.1 |
| US-04 | CRUD Clients & Paramètres fiscaux | 3.1, 7 |
| US-05 | Cycle d'annulation comptable (avoirs) | 4.6 |
| US-06 | Export Factur-X (CII/BASIC) | 4.1 (bascule Factur-X), 6 |
| US-07 | Cycle de vie DGFIP & piste d'audit fiable | 4.5, 4.7 |
| US-08 | e-Reporting mobile | 11 (⚠️ écran non câblé dans la navigation — voir Dette Technique) |
| US-09 | Annuaire DGFIP (PPF/PDP) | 3.2 |
| US-10 | Avoir (formulaire dédié) | 4.6 |
| US-11 | Sélecteur de client (ClientPicker) | 3.3 |
| US-12 | Activité commerciale des devis (dashboard) | 2 (MOB-DASH-05/06) |
| US-13 | Statuts réglementaires PPF | 4.4, 4.5 |
| US-14 | Aperçu PDF de facture | *(non couvert explicitement — hors du périmètre des 9 modules demandés)* |
| US-15 | Mode page Notion / saisie alternative | 4.1, 4.2 |
| US-16 | Pénalités légales B2B | 4.1 (MOB-INV-09) |
| US-17 | Piste d'audit fiable (timeline) | 4.7 |
| US-18 | Rapprochement bancaire | 10 |
| US-19 | Palette de commandes | 9.1 (MOB-NAV-04) |
| US-20 | Hub d'intégrations | 8 |
| US-21 | Inscription intelligente par SIRET | 1 |
| US-22 | Modale d'export comptable | 6 |
| US-23 | Mode Canvas A4 | 4.2 |
| US-24 | Panneau d'audit de conformité | 5 |
| US-25 | Bascule Thème Sombre/Clair | 9.3 |

**Note sur le périmètre :** ce plan couvre désormais 11 modules — les 9 initialement demandés, complétés par Rapprochement bancaire (US-18, module 10) et e-Reporting (US-08, module 11). Seule l'US-14 (aperçu PDF de facture) reste hors périmètre, n'ayant été demandée dans aucune des deux passes de rédaction.
