# -*- coding: utf-8 -*-
"""
generate_test_plan_docx.py
==========================
Generates the comprehensive E2E Test Plan for LedgerHub Mobile
as a professional Word (.docx) document.

Output: docs/testing/Plan_de_Test_Global_E2E_LedgerHub.docx

Usage:
    pip install python-docx
    python scripts/generate_test_plan_docx.py
"""

import os
import sys
from datetime import datetime

from docx import Document
from docx.shared import Inches, Pt, Cm, RGBColor, Emu
from docx.enum.text import WD_ALIGN_PARAGRAPH
from docx.enum.table import WD_TABLE_ALIGNMENT
from docx.enum.section import WD_ORIENT
from docx.oxml.ns import qn, nsdecls
from docx.oxml import parse_xml

# ──────────────────────────────────────────────
# COLOUR PALETTE
# ──────────────────────────────────────────────
BLUE_DARK   = RGBColor(0x1B, 0x3A, 0x5C)
BLUE_ACCENT = RGBColor(0x2E, 0x74, 0xB5)
GREY_DARK   = RGBColor(0x40, 0x40, 0x40)
GREY_LIGHT  = RGBColor(0xF2, 0xF2, 0xF2)
WHITE       = RGBColor(0xFF, 0xFF, 0xFF)
GREEN       = RGBColor(0x2D, 0x8B, 0x4E)
RED         = RGBColor(0xC0, 0x39, 0x2B)
AMBER       = RGBColor(0xE6, 0x7E, 0x22)

# ──────────────────────────────────────────────
# HELPER FUNCTIONS
# ──────────────────────────────────────────────

def set_cell_shading(cell, color_hex: str):
    """Apply background shading to a table cell."""
    shading = parse_xml(f'<w:shd {nsdecls("w")} w:fill="{color_hex}"/>')
    cell._tc.get_or_add_tcPr().append(shading)


def set_cell_text(cell, text, bold=False, color=None, size=None, alignment=None):
    """Set text in a cell with optional formatting."""
    cell.text = ""
    p = cell.paragraphs[0]
    if alignment:
        p.alignment = alignment
    run = p.add_run(text)
    run.bold = bold
    if color:
        run.font.color.rgb = color
    if size:
        run.font.size = Pt(size)
    run.font.name = "Calibri"
    # Reduce paragraph spacing in cells
    p.paragraph_format.space_before = Pt(2)
    p.paragraph_format.space_after = Pt(2)


def add_styled_heading(doc, text, level, color=None):
    """Add a heading with custom color."""
    heading = doc.add_heading(text, level=level)
    if color:
        for run in heading.runs:
            run.font.color.rgb = color


def add_scenario_block(doc, scenario_id, title, sc_type, preconditions, steps):
    """
    Add a complete scenario block:
    - Title line with badge
    - Preconditions paragraph
    - Steps table [Étape # | Action | Résultat Attendu | Statut | Commentaire]
    """
    # Badge emoji
    badge = "🟢" if sc_type == "Passant" else ("🔴" if "Non passant" in sc_type else "🟡")

    # Scenario heading
    h = doc.add_heading(level=3)
    run_badge = h.add_run(f"{badge}  ")
    run_id = h.add_run(f"[{scenario_id}] ")
    run_id.bold = True
    run_id.font.color.rgb = BLUE_ACCENT
    run_title = h.add_run(title)
    run_title.font.color.rgb = GREY_DARK

    # Type and preconditions
    p_type = doc.add_paragraph()
    run_type_label = p_type.add_run("Type : ")
    run_type_label.bold = True
    run_type_label.font.size = Pt(10)
    run_type_value = p_type.add_run(sc_type)
    run_type_value.font.size = Pt(10)
    if "Non passant" in sc_type:
        run_type_value.font.color.rgb = RED
    elif sc_type == "Passant":
        run_type_value.font.color.rgb = GREEN
    else:
        run_type_value.font.color.rgb = AMBER

    p_pre = doc.add_paragraph()
    run_pre_label = p_pre.add_run("Préconditions : ")
    run_pre_label.bold = True
    run_pre_label.font.size = Pt(10)
    run_pre_value = p_pre.add_run(preconditions)
    run_pre_value.font.size = Pt(10)

    # Steps table
    if steps:
        table = doc.add_table(rows=1, cols=5)
        table.alignment = WD_TABLE_ALIGNMENT.CENTER
        table.style = "Table Grid"

        # Header row
        headers = ["Étape #", "Action Utilisateur", "Résultat Attendu", "Statut (P/F/NA)", "Commentaire"]
        widths = [Cm(1.5), Cm(6), Cm(6), Cm(2.5), Cm(3)]
        hdr_cells = table.rows[0].cells
        for i, (hdr_text, width) in enumerate(zip(headers, widths)):
            set_cell_shading(hdr_cells[i], "1B3A5C")
            set_cell_text(hdr_cells[i], hdr_text, bold=True, color=WHITE, size=9,
                          alignment=WD_ALIGN_PARAGRAPH.CENTER)
            hdr_cells[i].width = width

        # Data rows
        for idx, (action, result) in enumerate(steps, 1):
            row = table.add_row()
            cells = row.cells
            bg = "FFFFFF" if idx % 2 == 1 else "F2F2F2"
            for c in cells:
                set_cell_shading(c, bg)

            set_cell_text(cells[0], str(idx), size=9, alignment=WD_ALIGN_PARAGRAPH.CENTER)
            set_cell_text(cells[1], action, size=9)
            set_cell_text(cells[2], result, size=9)
            set_cell_text(cells[3], "", size=9, alignment=WD_ALIGN_PARAGRAPH.CENTER)
            set_cell_text(cells[4], "", size=9)

    doc.add_paragraph("")  # spacer


def add_traceability_table(doc, data):
    """Add a traceability matrix table."""
    table = doc.add_table(rows=1, cols=3)
    table.alignment = WD_TABLE_ALIGNMENT.CENTER
    table.style = "Table Grid"
    headers = ["User Story", "Intitulé", "Scénarios E2E"]
    hdr = table.rows[0].cells
    for i, h_text in enumerate(headers):
        set_cell_shading(hdr[i], "1B3A5C")
        set_cell_text(hdr[i], h_text, bold=True, color=WHITE, size=9)

    for idx, (us, title, scenarios) in enumerate(data, 1):
        row = table.add_row()
        cells = row.cells
        bg = "FFFFFF" if idx % 2 == 1 else "F2F2F2"
        for c in cells:
            set_cell_shading(c, bg)
        set_cell_text(cells[0], us, bold=True, size=9)
        set_cell_text(cells[1], title, size=9)
        set_cell_text(cells[2], scenarios, size=9)


# ──────────────────────────────────────────────
# SCENARIO DATA
# ──────────────────────────────────────────────

SUITE_1_AUTH = [
    ("MOB-AUTH-10", "Inscription — le compte est réellement enregistré", "Passant",
     "Installation neuve (données effacées), écran d'authentification affiché.",
     [
         ("Onglet Inscription : saisir le SIRET démo, attendre le badge vert ✓, saisir email + mot de passe.",
          "Badge vert « ✓ Entreprise vérifiée via l'API SIRENE ». Raison sociale auto-remplie. Bouton actif."),
         ("Taper « Créer mon espace ».",
          "Le shell s'ouvre sur le Dashboard."),
         ("Fermer entièrement l'application et la relancer.",
          "L'application redemande une authentification."),
         ("Onglet Connexion : saisir la même adresse et mot de passe, taper « Se connecter ».",
          "Connexion aboutit sans réseau — le compte survit à la fermeture du processus."),
     ]),
    ("MOB-AUTH-05", "Inscription — vérification SIRENE automatique et réussie", "Passant",
     "Onglet Inscription actif, champ SIRET vide.",
     [
         ("Saisir progressivement les 14 chiffres du SIRET démo.",
          "Après 13 chiffres : aucune vérification. Dès le 14e : indicateur de chargement."),
         ("Observer le résultat de la vérification.",
          "Badge vert « ✓ Entreprise vérifiée via l'API SIRENE ». Raison sociale auto-remplie."),
     ]),
    ("MOB-AUTH-06", "Inscription — SIRET introuvable", "Non passant",
     "Onglet Inscription, champ SIRET vide.",
     [
         ("Saisir 14 chiffres ne correspondant à aucune entreprise.",
          "Message « SIRET introuvable au répertoire SIRENE. » — Raison sociale vide, bouton inerte."),
     ]),
    ("MOB-AUTH-07", "Inscription — répertoire SIRENE indisponible", "Non passant",
     "Service SIRENE simulé en panne.",
     [
         ("Saisir un SIRET à 14 chiffres.",
          "Message « Répertoire SIRENE indisponible, réessayez. » — distinct du SIRET introuvable."),
     ]),
    ("MOB-AUTH-08", "Inscription — correction du SIRET pendant vérification", "Non passant / cas limite",
     "Onglet Inscription.",
     [
         ("Saisir 14 chiffres, puis avant résultat, effacer et saisir un nouveau SIRET.",
          "Seul le résultat de la dernière saisie s'affiche. Annulation de la requête en vol."),
     ]),
    ("MOB-AUTH-09", "Inscription — nom auto-rempli puis modifié manuellement", "Passant",
     "SIRET vérifié avec succès, Raison sociale auto-remplie.",
     [
         ("Modifier le texte auto-rempli de la Raison sociale.",
          "Le texte modifié reste tel quel."),
         ("Effacer un chiffre du SIRET puis le ressaisir.",
          "Le nom modifié manuellement n'est pas effacé."),
     ]),
    ("MOB-AUTH-11", "Inscription — adresse déjà utilisée", "Non passant",
     "Un compte existe déjà (MOB-AUTH-10 exécuté).",
     [
         ("Refaire une inscription avec la même adresse email et un mot de passe différent.",
          "Message « Un compte existe déjà pour cette adresse — connectez-vous. »"),
         ("Vérifier que le compte d'origine est intact.",
          "La connexion avec le premier mot de passe fonctionne, celle avec le second échoue."),
     ]),
    ("MOB-AUTH-01", "Connexion nominale", "Passant",
     "Compte créé (MOB-AUTH-10). Écran de connexion, onglet Connexion actif.",
     [
         ("Saisir l'adresse email et le mot de passe du compte existant.",
          "Le bouton « Se connecter » devient actif."),
         ("Taper « Se connecter ».",
          "Indicateur de chargement bref, puis le shell s'ouvre sur le Dashboard."),
     ]),
    ("MOB-AUTH-02", "Connexion — champs vides", "Non passant",
     "Écran de connexion affiché, champs vides.",
     [
         ("Observer l'état du bouton « Se connecter » sans rien saisir.",
          "Le bouton reste inerte (grisé ou sans effet au tap)."),
     ]),
    ("MOB-AUTH-03", "Connexion — identifiants refusés", "Non passant",
     "Écran de connexion. (a) adresse inconnue, (b) mauvais mot de passe.",
     [
         ("Saisir email et mot de passe erronés, taper « Se connecter ».",
          "Message « Identifiants invalides ». Même message dans les deux passes."),
         ("Observer les champs après l'erreur.",
          "Les champs saisis restent affichés (rien n'est effacé)."),
     ]),
    ("MOB-AUTH-04", "Bascule vers l'inscription", "Passant",
     "Onglet Connexion actif.",
     [
         ("Taper « Pas encore de compte ? S'inscrire » ou l'onglet Inscription.",
          "Formulaire bascule : champs SIRET, Raison sociale, Email, Mot de passe. Titre « Créer votre espace LedgerHub »."),
     ]),
]

SUITE_2_DASHBOARD = [
    ("MOB-DASH-01", "Affichage nominal au démarrage", "Passant",
     "Application lancée sur données de démonstration, onglet Vue d'ensemble actif.",
     [
         ("Observer l'écran Dashboard sans interaction.",
          "Titre « Vue d'ensemble » + sous-titre visibles. Grille 4 KPI (2×2). Montants cohérents. Aucun chargement ni erreur."),
     ]),
    ("MOB-DASH-02", "Défilement jusqu'au graphique et à la liste", "Passant",
     "Écran Dashboard affiché.",
     [
         ("Scroller vers le bas depuis la grille KPI.",
          "Section « Chiffre d'affaires — 6 derniers mois » avec graphique Canvas. Puis « Factures récentes ». Puis « Devis à relancer »."),
     ]),
    ("MOB-DASH-03", "Animation du graphique de revenu", "Passant",
     "Dashboard affiché, graphique visible.",
     [
         ("Naviguer vers un autre onglet puis revenir sur Vue d'ensemble.",
          "La courbe se redessine avec animation de révélation ~600 ms (pas instantané)."),
     ]),
    ("MOB-DASH-04", "Liste des factures récentes — état vide", "Non passant / cas limite",
     "Base sans aucune facture (compte neuf).",
     [
         ("Défiler jusqu'à la section « Factures récentes ».",
          "Message « Aucun document pour le moment » affiché."),
     ]),
    ("MOB-DASH-05", "Devis à relancer — urgence de couleur", "Passant",
     "Devis envoyé ≤ 3 jours, un expiré, un lointain.",
     [
         ("Défiler jusqu'à « Devis à relancer ». Observer les couleurs.",
          "Lointain : neutre « J-n ». ≤ 3 jours : ambre. Échu : « Expire aujourd'hui ». Expiré : rouge « Expiré »."),
     ]),
    ("MOB-DASH-06", "Devis à relancer — état vide", "Non passant / cas limite",
     "Aucun devis envoyé sans réponse.",
     [
         ("Défiler jusqu'à la section.",
          "Message « Aucun devis à relancer pour l'instant. » affiché."),
     ]),
    ("MOB-DASH-07", "Échec de chargement du Dashboard", "Non passant",
     "Backend/base local inaccessible.",
     [
         ("Lancer l'application ou forcer une erreur de lecture.",
          "Message d'erreur affiché. L'application ne plante pas et reste utilisable."),
     ]),
    ("MOB-DASH-08", "Absence de mise en page tablette dédiée", "Passant / non-régression",
     "Dashboard affiché.",
     [
         ("Pivoter l'appareil en paysage ou basculer sur émulateur tablette (≥ 840 dp).",
          "Même Column défilant. Aucune carte tronquée ni superposée. Pas de rupture 840 dp."),
     ]),
]

SUITE_3_CLIENTS = [
    ("MOB-CLI-01", "Liste des clients — état vide", "Non passant / cas limite",
     "Aucun client enregistré.",
     [
         ("Ouvrir l'onglet Clients.",
          "Message « Aucun client enregistré. » affiché."),
     ]),
    ("MOB-CLI-02", "Création d'un client — nominal", "Passant",
     "Onglet Clients affiché.",
     [
         ("Taper « Ajouter un client ».",
          "Boîte de dialogue « Nouveau client » s'ouvre."),
         ("Renseigner Raison sociale, SIRET 14 chiffres, Email.",
          "Champs remplis, bouton Enregistrer actif."),
         ("Taper « Enregistrer ».",
          "Message « Client ajouté ». Fiche visible dans la liste."),
     ]),
    ("MOB-CLI-03", "Création — SIRET invalide (longueur)", "Non passant",
     "Formulaire de nouveau client ouvert.",
     [
         ("Saisir un SIRET de 10 chiffres. Taper Enregistrer.",
          "Champ SIRET en rouge. Message « Le SIRET doit comporter exactement 14 chiffres »."),
     ]),
    ("MOB-CLI-04", "Création — raison sociale trop courte", "Non passant",
     "Formulaire ouvert.",
     [
         ("Saisir 1 caractère dans Raison sociale. Taper Enregistrer.",
          "Erreur « La raison sociale doit comporter au moins 2 caractères »."),
     ]),
    ("MOB-CLI-05", "Création — email invalide", "Non passant",
     "Formulaire ouvert.",
     [
         ("Saisir un email sans arobase. Taper Enregistrer.",
          "Erreur « Adresse email invalide »."),
     ]),
    ("MOB-CLI-06", "Création — SIRET déjà utilisé", "Non passant",
     "Un client avec le SIRET X existe déjà.",
     [
         ("Créer un nouveau client avec le même SIRET X. Taper Enregistrer.",
          "Erreur « Un client porte déjà ce SIRET ». Aucune deuxième fiche créée."),
     ]),
    ("MOB-CLI-07", "Édition — SIRET verrouillé", "Passant / non-régression",
     "Un client existant, liste affichée.",
     [
         ("Taper « Modifier » sur une fiche. Observer le champ SIRET.",
          "SIRET désactivé (non éditable). Aide « Le SIRET identifie la fiche et ne peut pas être modifié ». Nom/Email éditables."),
     ]),
    ("MOB-CLI-08", "Édition — nominal", "Passant",
     "Fiche client ouverte en édition.",
     [
         ("Modifier la Raison sociale. Taper Enregistrer.",
          "Message « Client mis à jour ». La carte reflète le nouveau nom."),
     ]),
    ("MOB-CLI-09", "Suppression — client non référencé", "Passant",
     "Un client existant, jamais utilisé sur aucune facture.",
     [
         ("Taper « Supprimer ». Confirmer dans la boîte de dialogue.",
          "Message « Client supprimé ». La fiche disparaît de la liste."),
     ]),
    ("MOB-CLI-10", "Suppression — client référencé sur une facture", "Non passant",
     "Un client déjà cité sur au moins une facture émise.",
     [
         ("Taper « Supprimer » puis confirmer.",
          "Erreur « Suppression impossible : ce client figure sur <n> facture(s) émise(s) »."),
     ]),
    ("MOB-CLI-11", "Sélection d'un client existant par suggestion", "Passant",
     "Formulaire de facture ouvert, au moins un client existant.",
     [
         ("Dans le champ client, taper « Bou ». Observer les suggestions.",
          "Suggestions filtrées affichées en ligne, chacune sur une carte cliquable."),
         ("Taper sur la suggestion correspondante.",
          "SIRET et Email se remplissent automatiquement."),
     ]),
    ("MOB-CLI-12", "Création rapide d'un nouveau client depuis le formulaire", "Passant",
     "Formulaire de facture ouvert, aucun client existant ne correspond.",
     [
         ("Taper un nom inexistant. Observer le bouton « + Ajouter comme nouveau client ».",
          "Le bouton apparaît uniquement quand aucune suggestion ne correspond."),
         ("Remplir Nom/SIRET/Email dans la boîte rapide, taper Enregistrer.",
          "Le client est sélectionné dans le formulaire ET créé dans l'annuaire."),
     ]),
    ("MOB-CLI-13", "Création rapide — SIRET sans clé de Luhn valide", "Non passant",
     "Boîte « Nouveau client » ouverte depuis un formulaire.",
     [
         ("Saisir un SIRET 14 chiffres avec clé de Luhn incorrecte. Taper Enregistrer.",
          "Erreur « SIRET invalide : 14 chiffres et clé de Luhn correcte » — règle plus stricte."),
     ]),
    # Annuaire DGFIP
    ("MOB-DIR-01", "Recherche — SIRET valide (clé de Luhn correcte)", "Passant",
     "Onglet Annuaire DGFIP.",
     [
         ("Saisir un SIRET 14 chiffres valide. Observer l'indicateur de clé.",
          "Indicateur vert « ✓ Clé de Luhn valide — SIREN/SIRET »."),
         ("Taper « Rechercher ».",
          "Carte résultat : raison sociale, SIREN/SIRET, TVA, badge routage PPF/PDP, statut, date sync."),
     ]),
    ("MOB-DIR-02", "Recherche — clé de Luhn invalide", "Non passant",
     "Écran Annuaire DGFIP.",
     [
         ("Saisir 14 chiffres avec clé de Luhn incorrecte.",
          "Indicateur rouge « ✗ Clé de Luhn invalide ». Pas de résultat fiable."),
     ]),
    ("MOB-DIR-03", "Recherche — identifiant introuvable", "Non passant",
     "Écran Annuaire DGFIP.",
     [
         ("Saisir un SIREN/SIRET valide mais absent. Taper Rechercher.",
          "Message « Aucune entreprise ne correspond à cet identifiant dans l'annuaire. »"),
     ]),
    ("MOB-DIR-04", "Entreprise en franchise en base (sans TVA)", "Passant / cas limite",
     "Résultat correspondant à une entreprise sans numéro de TVA.",
     [
         ("Rechercher cette entreprise.",
          "Le champ TVA affiche « Non assujettie à la TVA (franchise en base) »."),
     ]),
    ("MOB-DIR-05", "Badge de routage PPF vs PDP", "Passant",
     "Deux entreprises connues, une routée PPF, l'autre PDP.",
     [
         ("Rechercher chacune successivement.",
          "Badge bleu « PPF » pour la première, badge violet « PDP » avec identifiant pour la seconde."),
     ]),
    ("MOB-DIR-06", "L'Annuaire DGFIP ignore la bascule de langue", "Non passant / non-régression",
     "Écran Annuaire DGFIP affiché en français.",
     [
         ("Basculer sur EN via l'en-tête. Revenir sur l'Annuaire.",
          "L'écran reste entièrement en français — incohérence connue, pas un bug nouveau."),
     ]),
]

SUITE_4_QUOTES = [
    ("MOB-QUO-01", "Création d'un devis — nominal", "Passant",
     "Accès au formulaire Devis.",
     [
         ("Renseigner Numéro, Date d'émission, Date de validité.",
          "Champs acceptés sans erreur."),
         ("Renseigner Émetteur (si non pré-rempli) et Destinataire via sélecteur client.",
          "Données émetteur et destinataire remplies."),
         ("Ajouter une ligne (libellé, qté, PU HT, taux TVA). Taper « Créer le devis ».",
          "Message « Devis créé avec succès ». Aucun panneau de conformité ni Factur-X (par construction)."),
     ]),
    ("MOB-QUO-02", "Validation — champs obligatoires", "Non passant",
     "Formulaire Devis partiellement rempli.",
     [
         ("Laisser le Numéro de devis vide. Taper « Créer le devis ».",
          "Erreur « Le numéro de devis est requis ». Mêmes règles de dates et de lignes que la facture."),
     ]),
    ("MOB-QUO-03", "Le formulaire Devis ignore la bascule de langue", "Non passant / non-régression",
     "Formulaire Devis ouvert.",
     [
         ("Basculer la langue en EN.",
          "Les libellés restent en français — incohérence produit connue."),
     ]),
]

SUITE_5_INVOICES = [
    # 5.1 Formulaire classique
    ("MOB-INV-01", "Enregistrement d'un brouillon — nominal", "Passant",
     "Onglet Factures, formulaire de création ouvert (Mode Formulaire).",
     [
         ("Sélectionner/créer un client. Renseigner Nº facture, dates.",
          "Champs acceptés."),
         ("Renseigner une ligne : Libellé, Qté, PU HT, taux TVA.",
          "Récapitulatif Total HT / TVA / TTC mis à jour en temps réel."),
         ("Taper « 💾 Enregistrer le brouillon ».",
          "Message « Facture émise et persistée avec succès ». Statut « Brouillon » en liste."),
     ]),
    ("MOB-INV-02", "Émission d'une facture — nominal", "Passant",
     "Formulaire complet et valide.",
     [
         ("Compléter tous les champs. Taper « ☁ Valider et émettre ».",
          "Facture passe à « Déposée ». Le formulaire se ferme."),
     ]),
    ("MOB-INV-03", "Émission bloquée — numéro manquant", "Non passant",
     "Formulaire rempli sauf le Nº de facture.",
     [
         ("Taper « Valider et émettre » sans numéro.",
          "Champ en rouge, erreur « Le numéro de facture est requis »."),
     ]),
    ("MOB-INV-04", "Émission bloquée — format de date invalide", "Non passant",
     "Formulaire par ailleurs valide.",
     [
         ("Saisir une date au format JJ/MM/AAAA. Taper « Valider et émettre ».",
          "Erreur « Date attendue au format AAAA-MM-JJ »."),
     ]),
    ("MOB-INV-05", "Émission bloquée — SIRET client invalide", "Non passant",
     "Client saisi manuellement, SIRET de longueur incorrecte.",
     [
         ("Taper « Valider et émettre ».",
          "Erreur « Le SIRET doit comporter exactement 14 chiffres »."),
     ]),
    ("MOB-INV-06", "Émission bloquée — ligne invalide (quantité)", "Non passant",
     "Une ligne avec quantité à 0.",
     [
         ("Saisir « 0 » dans Qté. Taper « Valider et émettre ».",
          "Erreur « La quantité doit être un entier positif ». Le total ignore cette ligne."),
     ]),
    ("MOB-INV-07", "Ajout et suppression de lignes", "Passant",
     "Formulaire avec une seule ligne.",
     [
         ("Taper « + Ajouter une ligne » deux fois.",
          "3 blocs de saisie distincts, chacun avec bouton suppression."),
         ("Supprimer la 2e ligne. Continuer à supprimer jusqu'à 1 ligne.",
          "Numérotation cohérente. Bouton suppression de la dernière ligne désactivé."),
     ]),
    ("MOB-INV-08", "Bascule du taux de TVA sur une ligne", "Passant",
     "Ligne avec quantité et PU renseignés.",
     [
         ("Choisir un taux différent (ex: 5,5 % au lieu de 20 %).",
          "Total TVA et TTC recalculés immédiatement. Total HT inchangé."),
     ]),
    ("MOB-INV-09", "Bascule des pénalités B2B — mention légale", "Passant",
     "Formulaire ouvert, case B2B cochée par défaut.",
     [
         ("Défiler jusqu'au pied de page. Lire le texte.",
          "Texte « En cas de retard de paiement, une pénalité égale à 3× le taux d'intérêt légal… »."),
         ("Décocher la case (toucher la ligne complète). Relire.",
          "Texte remplacé par « Merci pour votre confiance. ». Cible tactile ≥ 48 dp."),
     ]),
    ("MOB-INV-10", "Bascule du mode de saisie sans perte de données", "Passant",
     "Formulaire classique partiellement rempli.",
     [
         ("Basculer sur « Mode Page Blanche » puis rebasculer sur « Mode Formulaire ».",
          "Aucune donnée perdue. Client, ligne, montants identiques dans les deux modes."),
     ]),
    # 5.2 Mode Canvas
    ("MOB-INV-12", "Édition directe sur la feuille A4", "Passant",
     "Mode Page Blanche affiché.",
     [
         ("Taper dans une cellule du tableau. Saisir un libellé.",
          "Contour bleu au focus. Cible tactile ≥ 48 dp."),
     ]),
    ("MOB-INV-13", "En-tête émetteur en lecture seule vs client éditable", "Passant / non-régression",
     "Mode Page Blanche affiché.",
     [
         ("Tenter de taper sur le bloc émetteur. Puis taper sur le bloc client.",
          "Émetteur ne réagit pas (lecture seule). Client éditable champ par champ."),
     ]),
    ("MOB-INV-14", "Totaux et ventilation TVA en temps réel", "Passant",
     "Une ligne en cours de saisie, PU vide.",
     [
         ("Observer le Total HT avec PU vide. Compléter le PU.",
          "Total affiche 0 tant que ligne incomplète. Puis totaux mis à jour immédiatement."),
     ]),
    ("MOB-INV-15", "Persistance du mode après rotation", "Passant / non-régression",
     "Mode Page Blanche sélectionné.",
     [
         ("Pivoter l'appareil (portrait → paysage → portrait).",
          "Mode Page Blanche reste sélectionné (rememberSaveable)."),
     ]),
    # 5.3 Filtres et liste
    ("MOB-INV-16", "Filtrage par statut", "Passant",
     "Onglet Factures, plusieurs statuts en base.",
     [
         ("Taper successivement les puces : Toutes, Brouillons, Déposées, Approuvées, Encaissées, En retard, Rejetées, Refusées, Annulées.",
          "La liste ne montre que les factures du statut sélectionné. « Toutes » réaffiche tout."),
     ]),
    ("MOB-INV-17", "Liste vide sur un filtre sans résultat", "Non passant / cas limite",
     "Aucune facture au statut « Rejetées ».",
     [
         ("Sélectionner le filtre « Rejetées ».",
          "Message d'état vide affiché, pas une liste blanche silencieuse."),
     ]),
    ("MOB-INV-18", "Cadenas sur une facture verrouillée", "Passant / non-régression",
     "Facture Brouillon et Déposée en liste.",
     [
         ("Observer les deux cartes.",
          "Carte Déposée : icône cadenas. Carte Brouillon : aucun cadenas."),
     ]),
    # 5.4 Cycle de vie
    ("MOB-INV-21", "Facture Brouillon — édition libre", "Passant",
     "Détail d'une facture Brouillon.",
     [
         ("Taper « Modifier ».",
          "Bouton actif. Formulaire s'ouvre pré-rempli, entièrement modifiable."),
     ]),
    ("MOB-INV-22", "Facture Déposée — édition bloquée", "Non passant / non-régression",
     "Détail d'une facture Déposée.",
     [
         ("Observer le bouton « Modifier ». Taper dessus.",
          "Bouton désactivé. Aide avec cadenas. Aucune navigation vers le formulaire."),
     ]),
    ("MOB-INV-25", "Transition DRAFT → DEPOSITED", "Passant",
     "Détail d'une facture Brouillon.",
     [
         ("Dans la section Cycle de vie, taper « Marquer déposée ».",
          "Seul ce bouton est proposé depuis Brouillon. Statut passe à Déposée sans motif requis."),
     ]),
    ("MOB-INV-26", "Transition DEPOSITED → REJECTED (motif obligatoire)", "Non passant",
     "Facture Déposée.",
     [
         ("Taper le bouton de transition vers « Rejetée ». Observer le bouton de confirmation sans motif.",
          "Bouton de confirmation désactivé tant que le motif est vide."),
         ("Saisir un motif, ex. « SIRET destinataire invalide ». Confirmer.",
          "Facture passe à Rejetée. Motif repris dans la piste d'audit."),
     ]),
    ("MOB-INV-27", "Transition DEPOSITED → PAID", "Passant",
     "Facture Déposée.",
     [
         ("Taper le bouton vers « Encaissée ». Confirmer sans motif.",
          "Confirmation aboutit sans texte — seules les transitions négatives imposent un motif."),
     ]),
    ("MOB-INV-28", "Réouverture d'une facture Rejetée", "Passant",
     "Facture au statut Rejetée.",
     [
         ("Observer les boutons disponibles. Taper « Reprendre en brouillon ».",
          "Unique action proposée. Statut repasse à Brouillon, facture redevient éditable."),
     ]),
    # 5.5 Avoir
    ("MOB-INV-32", "Émission d'un avoir — nominal", "Passant",
     "Facture Encaissée éligible, jamais créditée.",
     [
         ("Taper « Créer un avoir ».",
          "Badge « AVOIR EN BROUILLON ». Référence croisée à la facture d'origine visible."),
         ("Sélectionner un motif prédéfini. Taper « Valider l'avoir ».",
          "Avoir émis. Lignes en négatif. Total TTC négatif. Facture ne peut plus être créditée."),
     ]),
    ("MOB-INV-33", "Motif « Autre motif » — texte libre obligatoire", "Non passant",
     "Formulaire d'avoir ouvert.",
     [
         ("Sélectionner « Autre motif ». Laisser le champ vide. Taper Valider.",
          "Erreur bloquante — motif « Autre » sans texte non accepté."),
     ]),
    ("MOB-INV-34", "Double émission d'avoir bloquée", "Non passant",
     "Facture déjà créditée par un avoir.",
     [
         ("Tenter de créer un avoir pour cette même facture.",
          "Action « Créer un avoir » absente ou bannière de blocage."),
     ]),
    # 5.6 Piste d'audit
    ("MOB-INV-35", "Les quatre jalons sur une facture Encaissée", "Passant",
     "Facture ayant traversé Brouillon → Déposée → Encaissée.",
     [
         ("Défiler jusqu'à la piste d'audit.",
          "4 jalons : Créée (✓ vert), Scellée (empreinte SHA-256), PPF (fait), Décision (badge vert Encaissée)."),
     ]),
    ("MOB-INV-36", "Facture Rejetée — motif affiché sur le jalon Décision", "Passant",
     "Facture rejetée avec motif « SIRET destinataire invalide ».",
     [
         ("Ouvrir le détail, défiler jusqu'à la piste d'audit.",
          "Jalon Décision en rouge (« ! »). Motif exact affiché. Libellé « Rejetée par la plateforme »."),
     ]),
    ("MOB-INV-39", "Horodatage en UTC", "Passant / non-régression",
     "N'importe quel jalon avec horodatage.",
     [
         ("Comparer l'heure affichée à l'heure locale de l'appareil.",
          "Heure en UTC (suffixe « UTC »). Jamais convertie vers le fuseau local."),
     ]),
    # 5.7 Conformité 2026
    ("MOB-COMP-01", "Aucun contrôle effectué au départ", "Passant / cas limite",
     "Formulaire fraîchement ouvert, aucun scan lancé.",
     [
         ("Défiler jusqu'au panneau « Audit de conformité 2026 ».",
          "Message « Aucun contrôle effectué pour l'instant. ». Aucune checklist."),
     ]),
    ("MOB-COMP-02", "Scan sur une facture entièrement conforme", "Passant",
     "Formulaire rempli sans aucune anomalie.",
     [
         ("Taper « Scanner la conformité ».",
          "Checklist 4 contrôles verts : SIRET, TVA, Mentions légales, Factur-X. Message « Facture conforme »."),
     ]),
    ("MOB-COMP-03", "Scan avec avertissement — clé de Luhn fausse", "Non passant / cas limite",
     "SIRET client 14 chiffres, clé de Luhn incorrecte.",
     [
         ("Taper « Scanner la conformité ».",
          "Bandeau ambre « Conformité incomplète ». Contrôle SIRET en avertissement (pas en blocage)."),
     ]),
    ("MOB-COMP-07", "Revalidation dynamique — le rapport se périme à la frappe", "Passant / non-régression",
     "Un scan vient d'être exécuté, checklist affichée.",
     [
         ("Modifier n'importe quel champ du formulaire.",
          "La checklist et le bandeau disparaissent immédiatement. Il faut relancer un scan."),
     ]),
]

SUITE_6_PARAMS_FISCAL = [
    # 6.1 Paramètres Fiscaux
    ("MOB-SET-01", "Affichage des paramètres existants", "Passant",
     "Onglet Paramètres, sauvegarde antérieure existante.",
     [
         ("Ouvrir l'onglet Paramètres.",
          "Raison sociale, SIRET, SIREN (déduit, lecture seule), TVA, taux par défaut, bascule Factur-X — tous préremplis."),
     ]),
    ("MOB-SET-02", "SIREN dérivé automatiquement", "Passant / non-régression",
     "Onglet Paramètres.",
     [
         ("Modifier le champ SIRET. Observer le champ SIREN.",
          "SIREN = 9 premiers chiffres du SIRET. Non éditable directement."),
     ]),
    ("MOB-SET-03", "Enregistrement — SIRET invalide", "Non passant",
     "Onglet Paramètres.",
     [
         ("Saisir un SIRET de longueur incorrecte. Taper Enregistrer.",
          "Erreur « Le SIRET doit comporter exactement 14 chiffres ». Enregistrement bloqué."),
     ]),
    ("MOB-SET-04", "Enregistrement — numéro de TVA mal formé", "Non passant",
     "Onglet Paramètres.",
     [
         ("Saisir « FR12 ». Taper Enregistrer.",
          "Erreur « Le numéro de TVA doit être au format FRXX999999999 »."),
     ]),
    ("MOB-SET-05", "Numéro de TVA vide accepté (franchise en base)", "Passant / cas limite",
     "Onglet Paramètres.",
     [
         ("Vider le champ TVA. Taper Enregistrer.",
          "Aucune erreur — franchise en base légitime."),
     ]),
    ("MOB-SET-06", "Auto-majuscule du numéro de TVA", "Passant / non-régression",
     "Onglet Paramètres.",
     [
         ("Saisir « fr12345678901 ».",
          "Le champ affiche automatiquement en majuscules au fur et à mesure de la frappe."),
     ]),
    ("MOB-SET-07", "Enregistrement nominal", "Passant",
     "Formulaire valide.",
     [
         ("Modifier la Raison sociale. Taper Enregistrer.",
          "Snackbar « Paramètres fiscaux mis à jour avec succès ». Rotation : Snackbar ne rejoue pas."),
     ]),
    ("MOB-SET-08", "Répercussion sur un nouveau formulaire de facture", "Passant / parcours croisé",
     "Paramètres fiscaux modifiés (nouvelle Raison, nouveau taux TVA).",
     [
         ("Enregistrer. Ouvrir un nouveau formulaire facture. Ajouter une ligne.",
          "Émetteur reflète la nouvelle Raison. Taux TVA par défaut = nouveau taux choisi."),
     ]),

    # 6.2 DGFIP Directory — Recherche Routage
    ("MOB-DIR-R01", "Recherche de l'acheteur et vérification du routage", "Passant",
     "Onglet Annuaire DGFIP. SIRET de l'acheteur de la facture émise en Suite 5.",
     [
         ("Saisir le SIRET de l'acheteur. Taper Rechercher.",
          "Carte résultat avec raison sociale, SIREN/SIRET, numéro de TVA, badge PPF/PDP."),
         ("Vérifier la cohérence de l'adresse de routage avec la facture émise.",
          "L'adresse de routage (PPF ou identifiant PDP) correspond à la plateforme déclarée de l'acheteur."),
         ("Observer la date de dernière synchronisation.",
          "La date affichée est récente et cohérente. Le statut de synchronisation est « Actif »."),
     ]),
    ("MOB-DIR-R02", "Recherche routage — plateforme PDP avec identifiant", "Passant",
     "Acheteur routé vers un PDP (Plateforme de Dématérialisation Partenaire).",
     [
         ("Rechercher le SIRET de l'acheteur PDP.",
          "Badge violet « PDP » avec identifiant de plateforme. Adresse de routage spécifique."),
         ("Vérifier que l'identifiant PDP est exploitable pour l'émission de la facture.",
          "L'identifiant correspond au format attendu et peut être utilisé dans le flux d'émission."),
     ]),

    # 6.3 Rapprochement Bancaire (Lettrage)
    ("MOB-REC-01", "Mise en page tablette — deux colonnes", "Passant / non-régression",
     "Largeur ≥ 840 dp, au moins une transaction et une facture en attente.",
     [
         ("Ouvrir l'onglet Rapprochement.",
          "Deux colonnes côte à côte : « Transactions bancaires » à gauche, « Factures en attente » à droite."),
     ]),
    ("MOB-REC-02", "Mise en page téléphone — onglets", "Passant / non-régression",
     "Largeur compacte (< 840 dp).",
     [
         ("Ouvrir Rapprochement. Sélectionner une transaction. Basculer onglet Factures.",
          "Bannière d'aide visible. Point indicateur sur l'onglet Transactions (sélection conservée)."),
     ]),
    ("MOB-REC-03", "Sélection en bascule (toggle)", "Passant",
     "Onglet Rapprochement, au moins une transaction.",
     [
         ("Taper une carte de transaction. Retaper la même carte.",
          "Premier tap : sélection (mise en évidence). Second tap : désélection."),
     ]),
    ("MOB-REC-04", "Bouton d'association — apparition conditionnelle", "Passant",
     "Onglet Rapprochement.",
     [
         ("Sélectionner uniquement une transaction. Observer le bas de l'écran.",
          "Aucun bouton flottant (une seule sélection ne suffit pas)."),
         ("Sélectionner également une facture.",
          "Bouton flottant « Associer (Lettrage) » apparaît avec animation."),
     ]),
    ("MOB-REC-05", "Seules les factures légalement encaissables sont listées", "Passant / non-régression",
     "Facture Brouillon et Déposée, toutes deux impayées.",
     [
         ("Observer la colonne « Factures en attente de paiement ».",
          "Facture Déposée présente. Facture Brouillon absente."),
     ]),
    ("MOB-REC-06", "Association nominale — montants identiques", "Passant",
     "Transaction et facture impayée de même montant.",
     [
         ("Sélectionner la transaction, puis la facture. Taper « Associer (Lettrage) ».",
          "Sélections vidées. Badges verts « Rapprochée ». Facture → Encaissée."),
         ("Vérifier l'impact sur le Dashboard (KPI « En attente de paiement »).",
          "Le montant « En attente de paiement » diminue du montant de la facture rapprochée."),
     ]),
    ("MOB-REC-07", "Association avec écart de montant", "Non passant / cas limite",
     "Transaction et facture de montants différents.",
     [
         ("Sélectionner les deux. Observer la carte facture.",
          "Badge ambre « Écart de montant ». Ligne « Écart : <montant> ». Association reste possible."),
     ]),
    ("MOB-REC-09", "États vides", "Non passant / cas limite",
     "Aucune transaction ou aucune facture en attente.",
     [
         ("Ouvrir Rapprochement sur données vides.",
          "Messages « Aucune transaction bancaire à rapprocher. » et/ou « Aucune facture en attente. »"),
     ]),

    # 6.4 DGFIP e-Reporting
    ("MOB-ERP-01", "Bandeau de conformité permanent", "Passant",
     "Écran e-Reporting affiché (depuis Paramètres).",
     [
         ("Observer l'en-tête de l'écran.",
          "Bandeau fixe « Conformité DGFIP 2026 — e-Reporting » présent quel que soit le contenu."),
     ]),
    ("MOB-ERP-02", "Liste des déclarations — contenu d'une carte", "Passant",
     "Au moins une déclaration existante, statut Brouillon.",
     [
         ("Observer une carte de déclaration.",
          "Période, statut « Brouillon », type (B2C/International/Encaissements), totaux HT/TVA/TTC, nb transactions."),
     ]),
    ("MOB-ERP-03", "Transmission PPF — nominal", "Passant",
     "Déclaration au statut Brouillon.",
     [
         ("Taper « Transmettre PPF ».",
          "Bouton → « Transmission… » désactivé (anti double-tap)."),
         ("Observer le résultat final.",
          "Snackbar « Déclaration transmise au PPF — accusé <numéro> ». Statut → « Acquittée ». Ligne accusé en gras."),
     ]),
    ("MOB-ERP-04", "Blocage de la transmission concurrente", "Non passant / cas limite",
     "Transmission en cours sur une déclaration.",
     [
         ("Taper la transmission d'une autre déclaration Brouillon.",
          "Le bouton reste inerte — une seule transmission à la fois."),
     ]),
    ("MOB-ERP-05", "Retransmission d'une déclaration acquittée", "Non passant",
     "Déclaration déjà transmise et acquittée.",
     [
         ("Déclencher une nouvelle transmission.",
          "Message « Cette déclaration a déjà été transmise et acquittée (HTTP 409) »."),
     ]),
    ("MOB-ERP-06", "Échec générique de transmission", "Non passant",
     "Backend PPF simulé en panne.",
     [
         ("Taper « Transmettre PPF ».",
          "Snackbar « Transmission au PPF impossible ». Déclaration reste Brouillon. Bouton redevient actif."),
     ]),
    ("MOB-ERP-07", "Liste vide", "Non passant / cas limite",
     "Aucune déclaration e-Reporting en base.",
     [
         ("Ouvrir l'écran.",
          "État vide affiché, pas une liste blanche silencieuse."),
     ]),

    # 6.5 Received Invoices (Inbox) — Factures d'achat
    ("MOB-INBOX-01", "Dépôt d'une facture d'achat — nominal", "Passant",
     "Écran Inbox / Factures reçues accessible depuis Paramètres ou navigation dédiée.",
     [
         ("Ouvrir la section « Factures reçues ». Taper « + Déposer une facture ».",
          "Formulaire de dépôt s'ouvre avec champs : Fournisseur, Nº facture, Date, Montant TTC, fichier PDF/XML."),
         ("Renseigner les métadonnées et joindre le fichier de la facture d'achat.",
          "Les champs sont remplis. Le fichier est chargé avec indicateur de progression."),
         ("Valider le dépôt.",
          "Message « Facture d'achat enregistrée ». La facture apparaît dans la liste Inbox avec statut « À vérifier »."),
     ]),
    ("MOB-INBOX-02", "Contrôle OCR — extraction automatique des métadonnées", "Passant",
     "Dépôt d'une facture PDF contenant des données structurées.",
     [
         ("Déposer un fichier PDF. Observer l'extraction automatique.",
          "Les champs Fournisseur, Nº, Date et Montant TTC sont pré-remplis par OCR/extraction XML."),
         ("Vérifier la cohérence des données extraites avec le document source.",
          "Les valeurs correspondent au PDF original. L'utilisateur peut corriger manuellement si nécessaire."),
     ]),
    ("MOB-INBOX-03", "Blocage des doublons — même Nº de facture fournisseur", "Non passant",
     "Une facture d'achat avec le Nº « FA-2026-042 » du fournisseur X déjà en base.",
     [
         ("Tenter de déposer une nouvelle facture avec le même Nº du même fournisseur.",
          "Erreur « Doublon détecté : une facture portant ce numéro existe déjà pour ce fournisseur ». Dépôt bloqué."),
         ("Vérifier que le dépôt avec un Nº différent du même fournisseur est accepté.",
          "Dépôt réussit — le contrôle porte sur le couple (Fournisseur SIRET, Nº facture), pas le fournisseur seul."),
     ]),
    ("MOB-INBOX-04", "Consultation du détail d'une facture reçue", "Passant",
     "Au moins une facture d'achat déposée.",
     [
         ("Taper sur une carte de facture dans la liste Inbox.",
          "Écran de détail : métadonnées complètes, aperçu du document, statut de vérification, historique de traitement."),
     ]),

    # 6.6 Digital Vault & Audit — Coffre-fort numérique
    ("MOB-VAULT-01", "Consultation des empreintes cryptographiques", "Passant",
     "Au moins une facture émise et scellée (statut ≥ Déposée).",
     [
         ("Ouvrir le coffre-fort numérique (Digital Vault) depuis Paramètres.",
          "Liste des documents archivés avec pour chacun : Nº de pièce, date de scellement, empreinte SHA-256 tronquée."),
         ("Taper sur une entrée pour voir le détail de l'empreinte.",
          "Empreinte SHA-256 complète affichée (64 caractères hex). Date/heure UTC du scellement. Statut « Intègre »."),
     ]),
    ("MOB-VAULT-02", "Vérification du sceau d'archivage légal", "Passant",
     "Facture archivée dans le coffre-fort.",
     [
         ("Taper « Vérifier l'intégrité » sur une entrée du coffre.",
          "Indicateur de vérification bref. Résultat : badge vert « Sceau valide — document non altéré depuis le scellement »."),
         ("Observer les métadonnées du sceau.",
          "Algorithme (SHA-256), date de scellement UTC, taille du document original, certificat de conformité."),
     ]),
    ("MOB-VAULT-03", "Détection d'une altération (sceau invalide)", "Non passant",
     "Harnais de test simulant une modification post-scellement.",
     [
         ("Déclencher la vérification d'intégrité sur un document altéré.",
          "Badge rouge « Sceau invalide — le document a été modifié après scellement ». Alerte critique affichée."),
     ]),
    ("MOB-VAULT-04", "État vide du coffre-fort", "Non passant / cas limite",
     "Aucune pièce archivée (compte neuf).",
     [
         ("Ouvrir le coffre-fort numérique.",
          "Message « Aucun document archivé pour le moment. Les pièces seront archivées automatiquement lors de leur émission. »"),
     ]),

    # 6.7 VAT Pilot & CA3 — Déclaration TVA 3310-CA3
    ("MOB-VAT-01", "Calcul de la déclaration TVA CA3 — nominal", "Passant",
     "Au moins 3 factures émises avec différents taux de TVA sur la période courante.",
     [
         ("Ouvrir le module « Pilote TVA / CA3 » depuis Paramètres.",
          "Écran de synthèse avec période fiscale courante, ventilation par taux de TVA."),
         ("Observer la ventilation ligne par ligne.",
          "Chaque taux (20 %, 10 %, 5,5 %) avec : base HT, montant TVA collectée, nombre de factures concernées."),
         ("Vérifier le total TVA collectée.",
          "Somme cohérente avec les factures émises sur la période."),
     ]),
    ("MOB-VAT-02", "Cohérence CA3 avec le Chiffre d'Affaires du Dashboard", "Passant / parcours croisé",
     "Même période que le Dashboard KPI « Chiffre d'affaires ».",
     [
         ("Comparer le total HT de la CA3 avec le KPI « Chiffre d'affaires » du Dashboard.",
          "Les deux montants sont cohérents (la CA3 ventile ce que le Dashboard agrège)."),
         ("Vérifier après émission d'une nouvelle facture.",
          "Les deux écrans se mettent à jour de façon cohérente après l'émission."),
     ]),
    ("MOB-VAT-03", "CA3 — période sans activité", "Non passant / cas limite",
     "Période sans aucune facture émise.",
     [
         ("Sélectionner une période vide.",
          "Message « Aucune opération taxable sur cette période. » Tous les totaux à 0,00 €."),
     ]),
    ("MOB-VAT-04", "CA3 — TVA déductible (factures d'achat)", "Passant",
     "Factures d'achat déposées en Inbox (Suite 6.5) avec TVA.",
     [
         ("Observer la section « TVA déductible » de la CA3.",
          "Montant de TVA déductible calculé à partir des factures d'achat vérifiées."),
         ("Vérifier le solde net (TVA collectée − TVA déductible).",
          "Solde affiché : positif = TVA à reverser, négatif = crédit de TVA. Cohérent avec les pièces."),
     ]),
]

SUITE_7_TRANSVERSAL = [
    # 7.1 Export Comptable
    ("MOB-EXP-01", "Ouverture de la modale d'export", "Passant",
     "N'importe quel écran avec le déclencheur 📤.",
     [
         ("Taper l'icône 📤 dans l'en-tête.",
          "Feuille modale : « Période d'export » (Du/Au) + « Format de sortie » (3 cartes)."),
     ]),
    ("MOB-EXP-02", "Sélection de chaque format", "Passant",
     "Modale ouverte.",
     [
         ("Taper successivement : FEC Officiel, Archive Factur-X, Synthèse Excel.",
          "Sélection exclusive. Sous-titres correspondants."),
     ]),
    ("MOB-EXP-03", "Période invalide — dates inversées", "Non passant",
     "Modale ouverte.",
     [
         ("Saisir une date « Du » postérieure à « Au ». Taper Générer.",
          "Erreur « La date de fin doit suivre la date de début ». Aucune génération."),
     ]),
    ("MOB-EXP-04", "Génération — animation et téléchargement", "Passant",
     "Période et format valides.",
     [
         ("Taper « Générer l'archive ». Observer la barre de progression.",
          "Barre progresse de 0 à 100 %. Puis bouton « Télécharger l'archive (.zip) »."),
     ]),
    # 7.2 Intégrations
    ("MOB-INT-01", "Ouverture du hub", "Passant",
     "N'importe quel écran avec 🧩.",
     [
         ("Taper l'icône Intégrations.",
          "Hub avec titre, sous-titre, grille 4 cartes (2×2)."),
     ]),
    ("MOB-INT-02", "Modules BETA", "Passant",
     "Hub d'intégrations affiché.",
     [
         ("Observer « Paiement en ligne (Stripe) » et « Export FEC Expert-Comptable ».",
          "Badge BETA saturé indigo. Opacité plus élevée que les cartes verrouillées."),
     ]),
    ("MOB-INT-03", "Modules COMING SOON", "Passant / cas limite",
     "Hub d'intégrations affiché.",
     [
         ("Observer « Notifications Slack » et « Synchronisation Bancaire API ». Taper dessus.",
          "Badge ambre « Coming Soon ». Cartes désaturées (~70 %). Tap sans action."),
     ]),
    # 7.3 Navigation, Langue, Thème
    ("MOB-NAV-01", "Cinq commandes visibles en en-tête compact", "Passant / non-régression",
     "Pixel 5 (393 dp), portrait.",
     [
         ("Observer l'en-tête.",
          "5 commandes visibles (≥ 48 dp) : Export, Intégrations, Palette, Thème, Langue. « LedgerHub » lisible."),
     ]),
    ("MOB-NAV-03", "Barre de navigation basse — 6 destinations", "Passant",
     "Largeur compacte.",
     [
         ("Taper chacun des 6 onglets.",
          "Chaque tap navigue vers l'écran correspondant. Onglet actif en surbrillance."),
     ]),
    ("MOB-NAV-05", "Bascule FR → EN → FR sur le Dashboard", "Passant",
     "Application en français, Dashboard affiché.",
     [
         ("Taper segment EN. Observer titres, montants.",
          "« Vue d'ensemble » → « Dashboard ». « 1 234,56 € » → « €1,234.56 ». Instantané."),
         ("Retaper FR.",
          "Retour identique à l'état initial, sans reliquat anglais."),
     ]),
    ("MOB-NAV-08", "Cycle complet DARK → LIGHT → DARK", "Passant",
     "Thème sombre par défaut.",
     [
         ("Taper bascule de thème (☀️). Observer le repeint.",
          "Palette claire sur tout l'écran. Icône → lune."),
         ("Retaper (🌙).",
          "Retour identique au thème sombre initial."),
     ]),
    ("MOB-NAV-09", "Coexistence thème + langue en en-tête compact", "Passant / non-régression",
     "En-tête compact (Pixel 5).",
     [
         ("Observer bascule de thème et sélecteur de langue.",
          "Les deux entièrement visibles, cible tactile pleine (≥ 48 dp chacune)."),
     ]),
    ("MOB-NAV-10", "Persistance du thème après redémarrage", "Passant",
     "Thème basculé en Clair.",
     [
         ("Fermer complètement l'application. Relancer.",
          "L'application démarre en Clair — préférence persistée en base."),
     ]),
]

# ──────────────────────────────────────────────
# TRACEABILITY MATRIX
# ──────────────────────────────────────────────

TRACEABILITY = [
    ("US-01", "Socle réseau Ktor & modèles Factur-X", "Transversal (infrastructure)"),
    ("US-02", "ViewModel, gestion d'état, écrans Factur-X", "Suite 2 (Dashboard), Suite 5 (Factures)"),
    ("US-02 S2", "Support multilingue FR/EN", "Suite 7 (NAV-05, NAV-06)"),
    ("US-03", "Thème sombre premium, shell responsive", "Suite 7 (NAV-01, NAV-08)"),
    ("US-04", "CRUD Clients & Paramètres fiscaux", "Suite 3 (Clients), Suite 6 (Paramètres)"),
    ("US-05", "Cycle d'annulation comptable (avoirs)", "Suite 5 (INV-32→34)"),
    ("US-06", "Export Factur-X (CII/BASIC)", "Suite 5 (bascule Factur-X), Suite 7 (Export)"),
    ("US-07", "Cycle de vie DGFIP & piste d'audit", "Suite 5 (INV-25→39)"),
    ("US-08", "e-Reporting mobile", "Suite 6 (ERP-01→07)"),
    ("US-09", "Annuaire DGFIP (PPF/PDP)", "Suite 3 (DIR-01→06), Suite 6 (DIR-R01→R02)"),
    ("US-10", "Avoir (formulaire dédié)", "Suite 5 (INV-32→34)"),
    ("US-11", "Sélecteur de client (ClientPicker)", "Suite 3 (CLI-11→13)"),
    ("US-12", "Activité commerciale des devis", "Suite 2 (DASH-05/06)"),
    ("US-13", "Statuts réglementaires PPF", "Suite 5 (INV-16→18, INV-25→31)"),
    ("US-14", "Aperçu PDF de facture", "Hors périmètre"),
    ("US-15", "Mode Page Blanche / Canvas A4", "Suite 5 (INV-12→15)"),
    ("US-16", "Pénalités légales B2B", "Suite 5 (INV-09)"),
    ("US-17", "Piste d'audit fiable (timeline)", "Suite 5 (INV-35→39)"),
    ("US-18", "Rapprochement bancaire", "Suite 6 (REC-01→09)"),
    ("US-19", "Palette de commandes", "Suite 7 (NAV-04)"),
    ("US-20", "Hub d'intégrations", "Suite 7 (INT-01→03)"),
    ("US-21", "Inscription intelligente par SIRET", "Suite 1 (AUTH-05→11)"),
    ("US-22", "Modale d'export comptable", "Suite 7 (EXP-01→04)"),
    ("US-23", "Mode Canvas A4", "Suite 5 (INV-12→15)"),
    ("US-24", "Panneau d'audit de conformité", "Suite 5 (COMP-01→07)"),
    ("US-25", "Bascule Thème Sombre/Clair", "Suite 7 (NAV-08→12)"),
    ("US-26", "Release candidate : auth, SIRENE, compte local", "Suite 1 (AUTH-01/10/11), Suite 6 (ERP)"),
    ("—", "Received Invoices (Inbox)", "Suite 6 (INBOX-01→04)"),
    ("—", "Digital Vault & Audit", "Suite 6 (VAULT-01→04)"),
    ("—", "VAT Pilot & CA3", "Suite 6 (VAT-01→04)"),
]


# ──────────────────────────────────────────────
# MAIN GENERATION
# ──────────────────────────────────────────────

def main():
    doc = Document()

    # ── Global font defaults ──
    style = doc.styles["Normal"]
    font = style.font
    font.name = "Calibri"
    font.size = Pt(10)

    # Configure heading styles
    for level, (size, color) in enumerate(
        [(18, BLUE_DARK), (14, BLUE_ACCENT), (12, GREY_DARK)], 1
    ):
        h_style = doc.styles[f"Heading {level}"]
        h_font = h_style.font
        h_font.size = Pt(size)
        h_font.color.rgb = color
        h_font.bold = True
        h_font.name = "Calibri"

    # ── PAGE DE GARDE ──
    for _ in range(6):
        doc.add_paragraph("")

    title = doc.add_paragraph()
    title.alignment = WD_ALIGN_PARAGRAPH.CENTER
    run = title.add_run("Plan de Test Global E2E")
    run.font.size = Pt(28)
    run.font.color.rgb = BLUE_DARK
    run.bold = True
    run.font.name = "Calibri"

    subtitle = doc.add_paragraph()
    subtitle.alignment = WD_ALIGN_PARAGRAPH.CENTER
    run = subtitle.add_run("LedgerHub Mobile — Facturation B2B conforme Factur-X 2026")
    run.font.size = Pt(16)
    run.font.color.rgb = BLUE_ACCENT
    run.font.name = "Calibri"

    doc.add_paragraph("")

    meta = doc.add_paragraph()
    meta.alignment = WD_ALIGN_PARAGRAPH.CENTER
    for label, value in [
        ("Version : ", "1.0"),
        ("Date : ", datetime.now().strftime("%d/%m/%Y")),
        ("Auteur : ", "QA Automation Team — LedgerHub"),
        ("Application : ", "Compose Multiplatform (Android validé / iOS on hold)"),
        ("Référence : ", "MASTER_TEST_PLAN_MOBILE.md"),
    ]:
        run_label = meta.add_run(f"\n{label}")
        run_label.bold = True
        run_label.font.size = Pt(11)
        run_label.font.color.rgb = GREY_DARK
        run_value = meta.add_run(value)
        run_value.font.size = Pt(11)

    doc.add_page_break()

    # ── TABLE DES MATIÈRES (placeholder) ──
    add_styled_heading(doc, "Table des Matières", 1, BLUE_DARK)
    toc = doc.add_paragraph()
    toc_entries = [
        "1. Introduction & Périmètre",
        "2. Matrice de Traçabilité US → Scénarios",
        "3. Suite E2E #1 : Auth & Onboarding (11 scénarios)",
        "4. Suite E2E #2 : Dashboard & Empty States (8 scénarios)",
        "5. Suite E2E #3 : Clients & Annuaire DGFIP (19 scénarios)",
        "6. Suite E2E #4 : Devis — Quotes (3 scénarios)",
        "7. Suite E2E #5 : Factures, Conformité & Avoir (32 scénarios)",
        "8. Suite E2E #6 : Modules Paramètres, Conformité Fiscale & Rapprochement (31 scénarios)",
        "9. Suite E2E #7 : Export, Intégrations & Navigation Transversale (14 scénarios)",
        "10. Anomalies Connues & Dette Technique",
        "Annexes",
    ]
    for entry in toc_entries:
        run = toc.add_run(f"{entry}\n")
        run.font.size = Pt(11)
        run.font.color.rgb = BLUE_ACCENT

    doc.add_page_break()

    # ── 1. INTRODUCTION ──
    add_styled_heading(doc, "1. Introduction & Périmètre", 1, BLUE_DARK)

    add_styled_heading(doc, "1.1 Objectifs", 2, BLUE_ACCENT)
    doc.add_paragraph(
        "Ce document formalise le plan de test fonctionnel et End-to-End (E2E) exhaustif "
        "pour l'application LedgerHub Mobile. Il couvre les 26 User Stories du périmètre de "
        "parité fonctionnelle mobile, organisées en 7 suites E2E séquentielles cumulatives : "
        "chaque suite s'appuie sur l'état laissé par la précédente (State Machine E2E)."
    )

    add_styled_heading(doc, "1.2 Environnement de test", 2, BLUE_ACCENT)
    env_items = [
        "Terminal de référence QA : Samsung Galaxy S23+ / Pixel 5 (API 35, 393×851 dp)",
        "Architecture : Compose Multiplatform (Kotlin/JVM, SQLDelight, Ktor)",
        "Langue par défaut : Français",
        "Thème par défaut : Sombre",
        "Authentification : autonome (compte SQLDelight local, aucun backend requis)",
    ]
    for item in env_items:
        doc.add_paragraph(item, style="List Bullet")

    add_styled_heading(doc, "1.3 Conventions de lecture", 2, BLUE_ACCENT)
    doc.add_paragraph(
        "🟢 Passant — chemin nominal\n"
        "🔴 Non passant — erreur / cas d'échec\n"
        "🟡 Cas limite — boundary / edge case\n\n"
        "Les identifiants entre backticks (ex. login_email_field) sont les testTags "
        "Compose posés sur les éléments UI."
    )

    add_styled_heading(doc, "1.4 Modèle séquentiel cumulatif", 2, BLUE_ACCENT)
    doc.add_paragraph(
        "Les 7 suites s'exécutent dans l'ordre. La Suite 1 (Auth) crée le compte utilisé "
        "par toutes les suites suivantes. La Suite 3 (Clients) crée les fiches utilisées "
        "par les Suites 4 et 5 (Devis, Factures). La Suite 5 génère les factures émises "
        "exploitées par la Suite 6 (Paramètres, Rapprochement, e-Reporting, Coffre-fort, TVA). "
        "La Suite 7 valide les parcours transversaux (Export, Intégrations, Navigation)."
    )

    doc.add_page_break()

    # ── 2. MATRICE DE TRAÇABILITÉ ──
    add_styled_heading(doc, "2. Matrice de Traçabilité US → Scénarios E2E", 1, BLUE_DARK)
    add_traceability_table(doc, TRACEABILITY)
    doc.add_page_break()

    # ── 3. SUITE 1 : AUTH ──
    add_styled_heading(doc, "3. Suite E2E #1 : Auth & Onboarding", 1, BLUE_DARK)
    doc.add_paragraph(
        "Prérequis initial : installation neuve de l'application (données effacées). "
        "Cette suite crée le compte utilisateur qui sera réutilisé dans toutes les suites suivantes."
    )
    for sc in SUITE_1_AUTH:
        add_scenario_block(doc, *sc)
    doc.add_page_break()

    # ── 4. SUITE 2 : DASHBOARD ──
    add_styled_heading(doc, "4. Suite E2E #2 : Dashboard & Empty States", 1, BLUE_DARK)
    doc.add_paragraph(
        "Prérequis : authentifié (Suite 1). État initial : base vide (Empty States) puis "
        "données de démonstration pour les scénarios nominaux."
    )
    for sc in SUITE_2_DASHBOARD:
        add_scenario_block(doc, *sc)
    doc.add_page_break()

    # ── 5. SUITE 3 : CLIENTS ──
    add_styled_heading(doc, "5. Suite E2E #3 : Clients & Annuaire DGFIP", 1, BLUE_DARK)
    doc.add_paragraph(
        "Prérequis : session active (Suite 1). Cette suite crée les fiches clients "
        "utilisées par les Suites 4 (Devis) et 5 (Factures)."
    )
    for sc in SUITE_3_CLIENTS:
        add_scenario_block(doc, *sc)
    doc.add_page_break()

    # ── 6. SUITE 4 : DEVIS ──
    add_styled_heading(doc, "6. Suite E2E #4 : Devis — Quotes", 1, BLUE_DARK)
    doc.add_paragraph(
        "Prérequis : client créé en Suite 3. Les devis créés ici alimentent la section "
        "« Devis à relancer » du Dashboard (Suite 2, MOB-DASH-05/06)."
    )
    for sc in SUITE_4_QUOTES:
        add_scenario_block(doc, *sc)
    doc.add_page_break()

    # ── 7. SUITE 5 : FACTURES & CONFORMITÉ ──
    add_styled_heading(doc, "7. Suite E2E #5 : Factures, Conformité & Avoir", 1, BLUE_DARK)
    doc.add_paragraph(
        "Prérequis : client créé (Suite 3), paramètres fiscaux configurés. "
        "Cette suite couvre le formulaire classique, le mode Canvas, les filtres, "
        "le cycle de vie DGFIP, les avoirs, la piste d'audit et le panneau de conformité 2026."
    )
    for sc in SUITE_5_INVOICES:
        add_scenario_block(doc, *sc)
    doc.add_page_break()

    # ── 8. SUITE 6 : PARAMS, FISCAL, RAPPROCHEMENT ──
    add_styled_heading(doc, "8. Suite E2E #6 : Modules Paramètres, Conformité Fiscale & Rapprochement", 1, BLUE_DARK)
    doc.add_paragraph(
        "Suite dédiée aux modules opérationnels et fiscaux complets accessibles depuis l'onglet "
        "Paramètres et les écrans spécialisés. Cette suite exploite les factures émises en Suite 5 "
        "et couvre 6 sous-modules critiques :"
    )
    sub_modules = [
        "6.1 Paramètres Fiscaux (MOB-SET) — Configuration émetteur, SIRET, TVA, Factur-X",
        "6.2 DGFIP Directory — Recherche routage acheteur/plateforme (MOB-DIR-R)",
        "6.3 Rapprochement Bancaire — Lettrage et impact sur statut facture (MOB-REC)",
        "6.4 DGFIP e-Reporting — Flux B2C et transmission PPF (MOB-ERP)",
        "6.5 Received Invoices (Inbox) — Dépôt, OCR, blocage doublons (MOB-INBOX)",
        "6.6 Digital Vault & Audit — Empreintes SHA-256, sceau d'archivage (MOB-VAULT)",
        "6.7 VAT Pilot & CA3 — Déclaration TVA 3310, cohérence Dashboard (MOB-VAT)",
    ]
    for item in sub_modules:
        doc.add_paragraph(item, style="List Bullet")
    doc.add_paragraph("")

    for sc in SUITE_6_PARAMS_FISCAL:
        add_scenario_block(doc, *sc)
    doc.add_page_break()

    # ── 9. SUITE 7 : TRANSVERSAL ──
    add_styled_heading(doc, "9. Suite E2E #7 : Export, Intégrations & Navigation Transversale", 1, BLUE_DARK)
    doc.add_paragraph(
        "Prérequis : factures émises (Suite 5). Cette suite couvre les parcours transversaux : "
        "export comptable, hub d'intégrations, navigation, bascule de langue et de thème."
    )
    for sc in SUITE_7_TRANSVERSAL:
        add_scenario_block(doc, *sc)
    doc.add_page_break()

    # ── 10. ANOMALIES CONNUES ──
    add_styled_heading(doc, "10. Anomalies Connues & Dette Technique", 1, BLUE_DARK)

    doc.add_paragraph(
        "Cet encart consolide les écarts constatés — à ne pas rouvrir comme des anomalies "
        "nouvelles à chaque campagne de recette."
    )

    add_styled_heading(doc, "10.1 Écrans hors périmètre i18n", 2, BLUE_ACCENT)
    table = doc.add_table(rows=1, cols=3)
    table.style = "Table Grid"
    hdr = table.rows[0].cells
    for i, h in enumerate(["Écran", "Bascule FR/EN", "Constat"]):
        set_cell_shading(hdr[i], "1B3A5C")
        set_cell_text(hdr[i], h, bold=True, color=WHITE, size=9)

    i18n_data = [
        ("Annuaire DGFIP", "❌ reste en FR", "0 appel à tr()"),
        ("Formulaire Devis", "❌ reste en FR", "0 appel à tr()"),
        ("e-Reporting", "❌ reste en FR", "0 appel à tr()"),
        ("Rapprochement bancaire", "✅ traduit", "13 appels à tr() — contre-exemple"),
    ]
    for idx, (screen, toggle, comment) in enumerate(i18n_data, 1):
        row = table.add_row()
        cells = row.cells
        bg = "FFFFFF" if idx % 2 == 1 else "F2F2F2"
        for c in cells:
            set_cell_shading(c, bg)
        set_cell_text(cells[0], screen, size=9)
        set_cell_text(cells[1], toggle, size=9)
        set_cell_text(cells[2], comment, size=9)

    add_styled_heading(doc, "10.2 Deux implémentations de l'écran de détail facture", 2, BLUE_ACCENT)
    doc.add_paragraph(
        "presentation/invoices/InvoiceDetailScreen.kt (cible de ce plan) coexiste avec "
        "presentation/invoicedetail/InvoiceDetailScreen.kt (réduite). Toujours viser le "
        "paquet presentation.invoices."
    )

    add_styled_heading(doc, "10.3 Divergences de convention de tag", 2, BLUE_ACCENT)
    doc.add_paragraph(
        "Les tags de ClientPicker sont en UPPER_SNAKE_CASE alors que le reste utilise "
        "snake_case. Sans conséquence pour un testeur manuel."
    )

    doc.add_page_break()

    # ── ANNEXES ──
    add_styled_heading(doc, "Annexes", 1, BLUE_DARK)

    add_styled_heading(doc, "A. Récapitulatif de couverture", 2, BLUE_ACCENT)
    summary_table = doc.add_table(rows=1, cols=3)
    summary_table.style = "Table Grid"
    hdr = summary_table.rows[0].cells
    for i, h in enumerate(["Suite E2E", "Nb Scénarios", "Modules couverts"]):
        set_cell_shading(hdr[i], "1B3A5C")
        set_cell_text(hdr[i], h, bold=True, color=WHITE, size=9)

    summary_data = [
        ("Suite #1 : Auth & Onboarding", str(len(SUITE_1_AUTH)), "AUTH"),
        ("Suite #2 : Dashboard", str(len(SUITE_2_DASHBOARD)), "DASH"),
        ("Suite #3 : Clients & Annuaire", str(len(SUITE_3_CLIENTS)), "CLI, DIR"),
        ("Suite #4 : Devis", str(len(SUITE_4_QUOTES)), "QUO"),
        ("Suite #5 : Factures & Conformité", str(len(SUITE_5_INVOICES)), "INV, COMP"),
        ("Suite #6 : Paramètres, Fiscal & Rapprochement", str(len(SUITE_6_PARAMS_FISCAL)), "SET, DIR-R, REC, ERP, INBOX, VAULT, VAT"),
        ("Suite #7 : Transversal", str(len(SUITE_7_TRANSVERSAL)), "EXP, INT, NAV"),
    ]
    total = 0
    for idx, (suite, count, modules) in enumerate(summary_data, 1):
        total += int(count)
        row = summary_table.add_row()
        cells = row.cells
        bg = "FFFFFF" if idx % 2 == 1 else "F2F2F2"
        for c in cells:
            set_cell_shading(c, bg)
        set_cell_text(cells[0], suite, size=9)
        set_cell_text(cells[1], count, size=9, alignment=WD_ALIGN_PARAGRAPH.CENTER)
        set_cell_text(cells[2], modules, size=9)

    # Total row
    row = summary_table.add_row()
    cells = row.cells
    for c in cells:
        set_cell_shading(c, "1B3A5C")
    set_cell_text(cells[0], "TOTAL", bold=True, color=WHITE, size=9)
    set_cell_text(cells[1], str(total), bold=True, color=WHITE, size=9,
                  alignment=WD_ALIGN_PARAGRAPH.CENTER)
    set_cell_text(cells[2], "26 User Stories + 3 modules étendus", bold=True, color=WHITE, size=9)

    add_styled_heading(doc, "B. Glossaire", 2, BLUE_ACCENT)
    glossary = [
        ("PPF", "Portail Public de Facturation (plateforme publique d'échange de factures)"),
        ("PDP", "Plateforme de Dématérialisation Partenaire (opérateur privé agréé)"),
        ("Factur-X", "Norme hybride franco-allemande combinant un PDF lisible et un fichier XML structuré"),
        ("FEC", "Fichier des Écritures Comptables (format d'export comptable opposable)"),
        ("CA3", "Formulaire de déclaration TVA mensuelle/trimestrielle (CERFA 3310)"),
        ("Lettrage", "Rapprochement d'une écriture bancaire avec une pièce comptable"),
        ("OCR", "Reconnaissance optique de caractères (extraction automatique de données)"),
        ("SHA-256", "Algorithme de hachage cryptographique utilisé pour le sceau d'intégrité"),
        ("e-Reporting", "Obligation de transmission des données de transaction B2C à la DGFIP"),
    ]
    g_table = doc.add_table(rows=1, cols=2)
    g_table.style = "Table Grid"
    hdr = g_table.rows[0].cells
    set_cell_shading(hdr[0], "1B3A5C")
    set_cell_text(hdr[0], "Terme", bold=True, color=WHITE, size=9)
    set_cell_shading(hdr[1], "1B3A5C")
    set_cell_text(hdr[1], "Définition", bold=True, color=WHITE, size=9)
    for idx, (term, definition) in enumerate(glossary, 1):
        row = g_table.add_row()
        bg = "FFFFFF" if idx % 2 == 1 else "F2F2F2"
        set_cell_shading(row.cells[0], bg)
        set_cell_shading(row.cells[1], bg)
        set_cell_text(row.cells[0], term, bold=True, size=9)
        set_cell_text(row.cells[1], definition, size=9)

    # ── SAVE ──
    output_dir = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))),
                              "docs", "testing")
    os.makedirs(output_dir, exist_ok=True)
    output_path = os.path.join(output_dir, "Plan_de_Test_Global_E2E_LedgerHub.docx")
    doc.save(output_path)
    print(f"[OK] Document genere avec succes : {output_path}")
    print(f"   -> {total} scenarios repartis en 7 suites E2E sequentielles")
    print(f"   -> Taille : {os.path.getsize(output_path) / 1024:.1f} Ko")


if __name__ == "__main__":
    main()
