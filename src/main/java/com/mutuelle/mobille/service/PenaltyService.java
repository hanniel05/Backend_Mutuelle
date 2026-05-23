package com.mutuelle.mobille.service;

import com.mutuelle.mobille.enums.TransactionDirection;
import com.mutuelle.mobille.enums.TransactionType;
import com.mutuelle.mobille.models.MutuelleConfig;
import com.mutuelle.mobille.models.Session;
import com.mutuelle.mobille.models.Transaction;
import com.mutuelle.mobille.models.account.AccountMember;
import com.mutuelle.mobille.repository.AccountMemberRepository;
import com.mutuelle.mobille.repository.MutuelleConfigRepository;
import com.mutuelle.mobille.repository.SessionRepository;
import com.mutuelle.mobille.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class PenaltyService {

    private final AccountMemberRepository accountMemberRepository;
    private final SessionRepository sessionRepository;
    private final MutuelleConfigRepository mutuelleConfigRepository;
    private final TransactionRepository transactionRepository;
    private final AccountService accountService;
    private final SessionService sessionService;

    /**
     * Applique la pénalité d'accumulation tous les 3 sessions fermées depuis l'emprunt.
     * Pénalité = 15 000F + 3% du montant emprunté (montant initial)
     * Cette méthode doit être appelée après la fermeture de chaque session.
     */
    @Transactional
    public void applyAccumulatedPenaltyIfNeeded(Long accountMemberId) {
        AccountMember accountMember = accountMemberRepository.findById(accountMemberId)
                .orElseThrow(() -> new RuntimeException("AccountMember not found: " + accountMemberId));

        BigDecimal borrowAmount = accountMember.getBorrowAmount();
        if (borrowAmount == null || borrowAmount.compareTo(BigDecimal.ZERO) <= 0) {
            return; // Pas d'emprunt actif
        }

        Long borrowSessionId = accountMember.getBorrowSessionId();
        if (borrowSessionId == null) {
            return; // Pas de session d'emprunt enregistrée
        }

        Session borrowSession = sessionRepository.findById(borrowSessionId)
                .orElseThrow(() -> new RuntimeException("Borrow session not found: " + borrowSessionId));

        // Compter le nombre de sessions fermées depuis l'emprunt
        long completedSessionsCount = sessionRepository.countCompletedSessionsAfter(borrowSession.getStartDate());

        MutuelleConfig config = mutuelleConfigRepository.findAll()
                .stream()
                .findFirst()
                .orElseThrow(() -> new RuntimeException("MutuelleConfig not found"));

        int threshold = config.getLoanPenaltySessionThreshold() != null 
                ? config.getLoanPenaltySessionThreshold() 
                : 3;

        // Vérifier combien de cycles de pénalité doivent être appliqués
        Integer penaltyCyclesApplied = accountMember.getPenaltyCyclesApplied() != null 
                ? accountMember.getPenaltyCyclesApplied() 
                : 0;

        long cyclesNeeded = completedSessionsCount / threshold;
        long newCyclesToApply = cyclesNeeded - penaltyCyclesApplied;

        if (newCyclesToApply <= 0) {
            return; // Pas de nouvelle pénalité à appliquer
        }

        // Récupérer la session courante
        Optional<Session> currentSessionOpt = sessionService.findCurrentSession();
        Session currentSession = currentSessionOpt.orElseThrow(
                () -> new RuntimeException("No active session found")
        );

        // Obtenir le montant d'emprunt initial (stocké quelque part ou recalculé)
        // Pour l'instant, on utilise le solde actuel comme approximation
        BigDecimal initialBorrowAmount = accountMember.getBorrowAmount();

        // Calculer la pénalité
        BigDecimal tauxPenalite = config.getLoanInterestRatePercent()
                .divide(BigDecimal.valueOf(100), 10, RoundingMode.HALF_UP);
        BigDecimal penaliteInteret = initialBorrowAmount.multiply(tauxPenalite)
                .setScale(2, RoundingMode.HALF_UP);

        BigDecimal penaliteFixe = config.getLoanPenaltyFixedAmount();
        BigDecimal penaliteTotale = penaliteInteret.add(penaliteFixe)
                .multiply(BigDecimal.valueOf(newCyclesToApply))
                .setScale(2, RoundingMode.HALF_UP);

        // Appliquer la pénalité à la dette du membre
        accountService.addToUnpaidRenfoulement(accountMember, penaliteTotale);

        // Créer une transaction pour tracer la pénalité
        transactionRepository.save(
                Transaction.builder()
                        .accountMember(accountMember)
                        .amount(penaliteTotale)
                        .transactionType(TransactionType.PENALITE)
                        .transactionDirection(TransactionDirection.DEBIT)
                        .session(currentSession)
                        .description("Pénalité d'accumulation (" + newCyclesToApply + " cycles)")
                        .build()
        );

        // Mettre à jour le nombre de cycles appliqués
        accountMember.setPenaltyCyclesApplied((int) cyclesNeeded);
        accountMemberRepository.save(accountMember);

        log.info("Penalty applied to AccountMember {} | Cycles: {} → {} | Amount: {}",
                 accountMemberId, penaltyCyclesApplied, cyclesNeeded, penaliteTotale);
    }

    /**
     * Applique les pénalités d'accumulation pour tous les membres ayant un emprunt actif.
     * À appeler après la fermeture de chaque session.
     */
    @Transactional
    public void applyPenaltiesToAllBorrowers() {
        log.info("Starting penalty accumulation check for all borrowers...");

        List<AccountMember> borrowers = accountMemberRepository.findByBorrowAmountGreaterThan(BigDecimal.ZERO);

        for (AccountMember borrower : borrowers) {
            try {
                applyAccumulatedPenaltyIfNeeded(borrower.getId());
            } catch (Exception e) {
                log.error("Error applying penalty to AccountMember {}", borrower.getId(), e);
            }
        }

        log.info("Penalty accumulation check completed for {} borrowers", borrowers.size());
    }
}
