package com.mutuelle.mobille.service;

import com.mutuelle.mobille.enums.SolvencyStatus;
import com.mutuelle.mobille.models.Member;
import com.mutuelle.mobille.models.MutuelleConfig;
import com.mutuelle.mobille.models.account.AccountMember;
import com.mutuelle.mobille.repository.AccountMemberRepository;
import com.mutuelle.mobille.repository.MemberRepository;
import com.mutuelle.mobille.repository.MutuelleConfigRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class SolvencyService {

    private final AccountMemberRepository accountMemberRepository;
    private final MemberRepository memberRepository;
    private final MutuelleConfigRepository mutuelleConfigRepository;

    /**
     * Évalue et met à jour le statut de solvabilité d'un membre.
     * Si la dette totale dépasse le seuil, le membre devient INSOLVABLE.
     * Si la dette totale est en dessous du seuil, le membre devient SOLVABLE.
     * Le membre est désactivé si insolvable.
     */
    @Transactional
    public void evaluateMemberSolvency(Long accountMemberId) {
        AccountMember accountMember = accountMemberRepository.findById(accountMemberId)
                .orElseThrow(() -> new RuntimeException("AccountMember not found: " + accountMemberId));

        MutuelleConfig config = mutuelleConfigRepository.findAll()
                .stream()
                .findFirst()
                .orElseThrow(() -> new RuntimeException("MutuelleConfig not found"));

        BigDecimal totalDebt = accountMember.getTotalDebt();
        BigDecimal threshold = config.getDebtThresholdAmount();

        SolvencyStatus previousStatus = accountMember.getSolvencyStatus();
        SolvencyStatus newStatus = totalDebt.compareTo(threshold) > 0 
                ? SolvencyStatus.INSOLVABLE 
                : SolvencyStatus.SOLVABLE;

        // Mettre à jour le statut de solvabilité
        accountMember.setSolvencyStatus(newStatus);

        // Désactiver le membre si insolvable
        if (newStatus == SolvencyStatus.INSOLVABLE) {
            Member member = accountMember.getMember();
            if (member.isActive()) {
                member.setActive(false);
                memberRepository.save(member);
                log.info("Member {} deactivated due to insolvency. Total debt: {}", 
                         accountMember.getMember().getId(), totalDebt);
            }
            accountMember.setActive(false);
        } else {
            // Réactiver le membre si solvable
            Member member = accountMember.getMember();
            if (!member.isActive()) {
                member.setActive(true);
                memberRepository.save(member);
                log.info("Member {} reactivated due to solvency improvement. Total debt: {}", 
                         accountMember.getMember().getId(), totalDebt);
            }
            accountMember.setActive(true);
        }

        accountMemberRepository.save(accountMember);

        if (previousStatus != newStatus) {
            log.info("Member solvency status changed from {} to {}. Total debt: {} (threshold: {})",
                     previousStatus, newStatus, totalDebt, threshold);
        }
    }

    /**
     * Évalue et met à jour la solvabilité de tous les membres.
     */
    @Transactional
    public void evaluateAllMembersSolvency() {
        log.info("Starting evaluation of all members' solvency...");
        
        List<AccountMember> allMembers = accountMemberRepository.findAll();
        for (AccountMember accountMember : allMembers) {
            try {
                evaluateMemberSolvency(accountMember.getId());
            } catch (Exception e) {
                log.error("Error evaluating solvency for AccountMember {}", accountMember.getId(), e);
            }
        }
        
        log.info("Solvency evaluation completed for {} members", allMembers.size());
    }

    /**
     * Récupère la liste des membres insolvables.
     */
    public List<AccountMember> getInsolventMembers() {
        return accountMemberRepository.findBySolvencyStatus(SolvencyStatus.INSOLVABLE);
    }

    /**
     * Récupère la liste des membres à risque (dette >= 80% du seuil).
     */
    public List<AccountMember> getRiskMembers() {
        MutuelleConfig config = mutuelleConfigRepository.findAll()
                .stream()
                .findFirst()
                .orElseThrow(() -> new RuntimeException("MutuelleConfig not found"));

        BigDecimal threshold = config.getDebtThresholdAmount();
        BigDecimal riskThreshold = threshold.multiply(new BigDecimal("0.80"));

        return accountMemberRepository.findAll()
                .stream()
                .filter(member -> {
                    BigDecimal debt = member.getTotalDebt();
                    return debt.compareTo(riskThreshold) >= 0 && debt.compareTo(threshold) <= 0;
                })
                .toList();
    }
}
