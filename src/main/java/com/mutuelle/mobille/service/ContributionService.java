package com.mutuelle.mobille.service;

import com.mutuelle.mobille.dto.contribution.ContributionPaymentRequestDto;
import com.mutuelle.mobille.dto.contribution.ContributionPaymentResponseDto;
import com.mutuelle.mobille.enums.TransactionDirection;
import com.mutuelle.mobille.enums.TransactionType;
import com.mutuelle.mobille.models.Transaction;
import com.mutuelle.mobille.models.account.AccountMember;
import com.mutuelle.mobille.models.Session;
import com.mutuelle.mobille.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class ContributionService {

    private final AccountService accountService;
    private final SessionService sessionService;
    private final TransactionRepository transactionRepository;

    @Transactional
    public ContributionPaymentResponseDto processContributionPayment(ContributionPaymentRequestDto request) {

        TransactionType type = request.contributionType();
        BigDecimal amount = request.amount();
        Long memberId = request.memberId();

        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Le montant payé doit être strictement positif");
        }

        AccountMember memberAccount = accountService.getMemberAccount(memberId);

        Session currentSession = sessionService.findCurrentSession()
                .orElseThrow(() -> new IllegalStateException("Aucune session en cours trouvée"));

        BigDecimal unpaidBefore = getUnpaidAmount(memberAccount, type);
        String contributionName = getContributionName(type);

        if (unpaidBefore.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalStateException("Le membre n'a plus de " + contributionName + " impayés");
        }

        if (amount.compareTo(unpaidBefore) > 0) {
            throw new IllegalArgumentException(
                    String.format("Le montant payé (%s) dépasse le montant dû (%s) pour le %s",
                            amount, unpaidBefore, contributionName)
            );
        }

        switch (type) {
            case INSCRIPTION -> accountService.payFeeInscriptionAmount(memberId, amount);
            case RENFOULEMENT -> {
                Long exerciceId = currentSession.getExercice().getId();
                accountService.payRenfoulementAmount(memberId, amount, exerciceId);
            }
            default -> throw new IllegalArgumentException("Type non supporté");
        }

        BigDecimal remaining = unpaidBefore.subtract(amount);

        // Création transaction
        Transaction transaction = Transaction.builder()
                .accountMember(memberAccount)
                .session(currentSession)
                .amount(amount)
                .transactionType(type)
                .transactionDirection(TransactionDirection.CREDIT)
                .description("Paiement " + contributionName)
                .build();

        Transaction savedTransaction = transactionRepository.save(transaction);

        return new ContributionPaymentResponseDto(
                savedTransaction.getId(),
                memberAccount.getMember().getId(),
                memberAccount.getMember().getFirstname() + " " + memberAccount.getMember().getLastname(),
                amount,
                type,
                TransactionDirection.CREDIT,
                remaining,
                LocalDateTime.now(),
                "Paiement de " + amount + " FCFA enregistré pour le " + contributionName +
                        ". Reste à payer : " + remaining + " FCFA"
        );
    }
    
    private BigDecimal getUnpaidAmount(AccountMember account, TransactionType type) {
        return switch (type) {
            case INSCRIPTION -> account.getUnpaidRegistrationAmount();
            case RENFOULEMENT -> account.getUnpaidRenfoulement();
            default -> throw new IllegalArgumentException("Type non supporté : " + type);
        };
    }

    private String getContributionName(TransactionType type) {
        return switch (type) {
            case INSCRIPTION -> "frais d'inscription";
            case RENFOULEMENT -> "renfoulement";
            default -> "contribution";
        };
    }
}