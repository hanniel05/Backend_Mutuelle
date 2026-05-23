package com.mutuelle.mobille.models.account;

import com.fasterxml.jackson.annotation.JsonManagedReference;
import com.mutuelle.mobille.enums.SolvencyStatus;
import com.mutuelle.mobille.models.Member;
import jakarta.persistence.*;
import jakarta.validation.constraints.DecimalMin;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "accounts_member")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AccountMember {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Frais d'inscription impayés
    @DecimalMin(value = "0.00", inclusive = true, message = "Le montant ne peut pas être négatif")
    @Column(name = "unpaid_registration_amount", precision = 12, scale = 2)
    private BigDecimal unpaidRegistrationAmount = BigDecimal.ZERO;

    // Frais d'inscription de base
    @DecimalMin(value = "0.00", inclusive = true, message = "Le montant de base ne peut pas être négatif")
    @Column(name = "base_registration_amount", precision = 12, scale = 2)
    private BigDecimal baseRegistrationAmount = BigDecimal.ZERO;

    // Cotisation solidarité
    @DecimalMin(value = "0.00", inclusive = true, message = "Le montant  ne peut pas être négatif")
    @Column(name = "solidarity_amount", precision = 12, scale = 2)
    private BigDecimal solidarityAmount = BigDecimal.ZERO;

    // Cotisation solidarité impayée
    @DecimalMin(value = "0.00", inclusive = true, message = "Le montant  ne peut pas être négatif")
    @Column(name = "unpaid_solidarity_amount", precision = 12, scale = 2)
    private BigDecimal unpaidSolidarityAmount = BigDecimal.ZERO;

    // epargne
    @Builder.Default
    @Column(name = "saving_amount", precision = 12, scale = 2)
    private BigDecimal savingAmount = BigDecimal.ZERO;

    // Montant emprunté
    @DecimalMin(value = "0.00", inclusive = true, message = "Le montant  ne peut pas être négatif")
    @Column(name = "borrow_amount", precision = 12, scale = 2)
    private BigDecimal borrowAmount = BigDecimal.ZERO;

    @Column(name = "last_interest_date" )
    private LocalDateTime lastInterestDate;

    @Column(name = "initial_borrow_amount" )
    private BigDecimal initialBorrowAmount = BigDecimal.ZERO;

    // Renflouement impayé
    @Column(name = "unpaid_renfoulement", precision = 12, scale = 2)
    private BigDecimal unpaidRenfoulement = BigDecimal.ZERO;

    // Session dans laquelle l'emprunt courant a été accordé (pour calcul pénalité)
    @Column(name = "borrow_session_id")
    private Long borrowSessionId;

    // Nombre de cycles de pénalité déjà appliqués sur l'emprunt courant
    @Column(name = "penalty_cycles_applied")
    private Integer penaltyCyclesApplied = 0;

    // Statut de solvabilité du membre
    @Enumerated(EnumType.STRING)
    @Column(name = "solvency_status", length = 20)
    private SolvencyStatus solvencyStatus = SolvencyStatus.SOLVABLE;

    @Column(name = "is_active")
    private boolean isActive = true;

    @OneToOne(fetch = FetchType.LAZY)
    @JsonManagedReference
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    /**
     * Calcule la dette totale du membre.
     * Dette = borrowAmount + unpaidRenfoulement + unpaidRegistrationAmount + unpaidSolidarityAmount
     */
    public BigDecimal getTotalDebt() {
        BigDecimal debt = BigDecimal.ZERO;
        
        if (borrowAmount != null) {
            debt = debt.add(borrowAmount);
        }
        if (unpaidRenfoulement != null) {
            debt = debt.add(unpaidRenfoulement);
        }
        if (unpaidRegistrationAmount != null) {
            debt = debt.add(unpaidRegistrationAmount);
        }
        if (unpaidSolidarityAmount != null) {
            debt = debt.add(unpaidSolidarityAmount);
        }
        
        return debt;
    }

    /**
     * Vérifie si le membre est solvable selon le seuil donné.
     */
    public boolean isSolvable(BigDecimal threshold) {
        return getTotalDebt().compareTo(threshold) <= 0;
    }
}
