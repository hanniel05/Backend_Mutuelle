package com.mutuelle.mobille.enums;

/**
 * Statut de solvabilité d'un membre basé sur sa dette totale.
 */
public enum SolvencyStatus {
    /**
     * Membre solvable : dette totale <= seuil configuré (250 000 FCFA par défaut)
     */
    SOLVABLE,

    /**
     * Membre insolvable : dette totale > seuil configuré
     */
    INSOLVABLE
}
