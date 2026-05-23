package com.mutuelle.mobille.dto.member;

import com.mutuelle.mobille.enums.SolvencyStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RiskMemberDTO {

    private Long memberId;
    private Long accountMemberId;
    private String memberName;
    private String memberPhone;
    private BigDecimal totalDebt;
    private BigDecimal debtThreshold;
    private BigDecimal riskPercentage; // Pourcentage de la dette par rapport au seuil
    private SolvencyStatus solvencyStatus;
    private Boolean isActive;
    private BigDecimal borrowAmount;
    private BigDecimal unpaidRenfoulement;
    private BigDecimal unpaidRegistrationAmount;
    private BigDecimal unpaidSolidarityAmount;
}
