package com.mutuelle.mobille.models;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "mutuelle_config")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MutuelleConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Registration / inscription fee
    @Column(name = "registration_fee_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal registrationFeeAmount = new BigDecimal("25000.00");

    // Solidarity fee (one-time contribution per member, payable in installments)
    @Column(name = "solidarity_fee_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal solidarityFeeAmount = new BigDecimal("150000.00");

    // Loan interest rate (%)
    @Column(name = "loan_interest_rate_percent", nullable = false, precision = 5, scale = 2)
    private BigDecimal loanInterestRatePercent = new BigDecimal("3.00");

//    // Taux de pénalité de remboursement (%)
//    @Column(name = "loan_penalty_rate_percent", nullable = false, precision = 5, scale = 2)
//    private BigDecimal loanPenaltyRatePercent = new BigDecimal("3.00");

    // Forfait fixe de pénalité majorée (en FCFA)
    @Column(name = "loan_penalty_fixed_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal loanPenaltyFixedAmount = new BigDecimal("15000.00");

    // Nombre de sessions fermées déclenchant la pénalité majorée
    @Column(name = "loan_penalty_session_threshold", nullable = false)
    private Integer loanPenaltySessionThreshold = 3;

    // Seuil de dette totale au-delà duquel un membre devient insolvable (en FCFA)
    @Column(name = "debt_threshold_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal debtThresholdAmount = new BigDecimal("250000.00");

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "updated_by", length = 100)
    private String updatedBy;

    @PrePersist
    protected void onCreate() {
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
