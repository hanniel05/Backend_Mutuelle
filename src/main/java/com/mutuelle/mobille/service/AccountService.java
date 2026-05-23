    package com.mutuelle.mobille.service;

    import com.mutuelle.mobille.models.Renfoulement;
    import com.mutuelle.mobille.models.account.AccountMember;
    import com.mutuelle.mobille.models.account.AccountMutuelle;
    import com.mutuelle.mobille.repository.AccountMemberRepository;
    import com.mutuelle.mobille.repository.AccountMutuelleRepository;
    import com.mutuelle.mobille.repository.RenfoulementRepository;
    import jakarta.annotation.PostConstruct;
    import jakarta.transaction.Transactional;
    import lombok.RequiredArgsConstructor;
    import org.springframework.stereotype.Service;

    import java.time.LocalDateTime;
    import java.util.List;

    import java.math.BigDecimal;

    @Service
    @RequiredArgsConstructor
    public class AccountService {

        private final AccountMutuelleRepository globalRepo;
        private final AccountMemberRepository memberRepo;
        private final AuthService authService;
        private final AccountMemberRepository accountMemberRepository;
        private final RenfoulementRepository renfoulementRepository;

        @PostConstruct
        @Transactional
        public void initGlobalAccount() {
            if (globalRepo.count() == 0) {
                AccountMutuelle global = AccountMutuelle.builder()
                        .savingAmount(BigDecimal.ZERO)
                        .solidarityAmount(BigDecimal.ZERO)
                        .borrowAmount(BigDecimal.ZERO)
                        .isActive(true)
                        .build();
                globalRepo.save(global);
            }
        }

        // Récupérer le compte global (unique)
        public AccountMutuelle getMutuelleGlobalAccount() {
            return globalRepo.findAll().stream()
                    .findFirst()
                    .orElseThrow(() -> new RuntimeException("Compte global mutuelle introuvable"));
        }

        // Récupérer le compte d'un membre
        public AccountMember getMemberAccount(Long memberId) {
            return memberRepo.findByMemberId(memberId)
                    .orElseThrow(() -> new RuntimeException("Compte membre introuvable pour l'ID : " + memberId));
        }

        /**
         * Récupère tous les membres ayant un emprunt en cours (> 0)
         */
        public List<AccountMember> findMembersWithBorrowGreaterThanZero() {
            return memberRepo.findByBorrowAmountGreaterThan(BigDecimal.ZERO);
        }

        /**
         * Met à jour uniquement la date de dernier calcul d'intérêt trimestriel
         */
        @Transactional
        public void updateLastInterestDate(Long memberId, LocalDateTime newDate) {
            AccountMember account = getMemberAccount(memberId);
            account.setLastInterestDate(newDate);
            memberRepo.save(account);
        }

        // ──────────────────────────────────────────────────────────────
        // ─────────────── OPÉRATIONS FINANCIÈRES (avec mise à jour globale)
        // ──────────────────────────────────────────────────────────────

        /**
         * augmenter la dette d'un membre
         */
        @Transactional
        public void addBorrowAmount(AccountMember accountMember, BigDecimal amount) {
            if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
                throw new IllegalArgumentException("Le a ajouter doit être positif");
            }

            AccountMutuelle globalAccount = getMutuelleGlobalAccount();

            BigDecimal currentAmount = accountMember.getBorrowAmount();
            if (currentAmount == null) {
                currentAmount = BigDecimal.ZERO;
            }

            // Mise à jour compte membre
            accountMember.setBorrowAmount(currentAmount.add(amount));

            // Mise à jour compte global
            globalAccount.setBorrowAmount(currentAmount.add(amount));

            memberRepo.save(accountMember);
            globalRepo.save(globalAccount);
        }


        /**
         * Un membre fait une épargne
         */
        @Transactional
        public void addSaving(Long memberId, BigDecimal amount) {
            if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
                throw new IllegalArgumentException("Le montant d'épargne doit être positif");
            }

            AccountMember memberAccount = getMemberAccount(memberId);
            AccountMutuelle globalAccount = getMutuelleGlobalAccount();

            BigDecimal currentAmount = memberAccount.getSavingAmount();
            if (currentAmount == null) {
                currentAmount = BigDecimal.ZERO;
            }

            // Mise à jour compte membre
            memberAccount.setSavingAmount(currentAmount.add(amount));

            // Mise à jour compte global (la mutuelle reçoit aussi cette épargne)
            globalAccount.setSavingAmount(globalAccount.getSavingAmount().add(amount));

            memberRepo.save(memberAccount);
            globalRepo.save(globalAccount);
        }

        /**
         * Retrait d'épargne par un membre
         */
        @Transactional
        public void withdrawSaving(Long memberId, BigDecimal amount) {
            if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
                throw new IllegalArgumentException("Le montant du retrait doit être positif");
            }

            AccountMember memberAccount = getMemberAccount(memberId);
            AccountMutuelle globalAccount = getMutuelleGlobalAccount();

            BigDecimal currentAmount = memberAccount.getSavingAmount();
            if (currentAmount == null) {
                currentAmount = BigDecimal.ZERO;
            }


            // Vérifie que le membre a assez d'épargne
            if (currentAmount.compareTo(amount) < 0) {
                throw new IllegalStateException("Solde insuffisant pour effectuer le retrait");
            }

            // Mise à jour du compte membre
            memberAccount.setSavingAmount(currentAmount.subtract(amount));

            // Mise à jour du compte global
            globalAccount.setSavingAmount(globalAccount.getSavingAmount().subtract(amount));

            // Sauvegarde
            memberRepo.save(memberAccount);
            globalRepo.save(globalAccount);
        }

        /**
         * Un membre paie les frais d'inscription
         */
        @Transactional
        public void payRegistrationFee(Long memberId, BigDecimal amount) {
            if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
                throw new IllegalArgumentException("Le montant des frais doit être positif");
            }

            AccountMember memberAccount = getMemberAccount(memberId);
            AccountMutuelle globalAccount = getMutuelleGlobalAccount();

            BigDecimal currentUnpaid = memberAccount.getUnpaidRegistrationAmount();

            if (currentUnpaid.compareTo(BigDecimal.ZERO) == 0) {
                throw new IllegalStateException("Aucuns frais d'inscription impayés");
            }

            if (amount.compareTo(currentUnpaid) > 0) {
                throw new IllegalArgumentException("Le paiement dépasse le montant dû");
            }

            // Mise à jour compte membre
            memberAccount.setUnpaidRegistrationAmount(currentUnpaid.subtract(amount));

            // La mutuelle reçoit l'argent payé → augmente son épargne globale
            globalAccount.setRegistrationAmount(globalAccount.getRegistrationAmount().add(amount));

            memberRepo.save(memberAccount);
            globalRepo.save(globalAccount);
        }

        /**
         * Retourne tous les comptes membres
         */
        public List<AccountMember> getAllMemberAccounts() {
            return memberRepo.findAll();
        }

        public List<AccountMember> getAllMemberAccountsWithActive(boolean isActive) {
            return memberRepo.findAllByIsActive(isActive);
        }

        /**
         * Sauvegarde un compte membre (après redistribution)
         */
        public void saveMemberAccount(AccountMember account) {
            memberRepo.save(account);
        }

        /**
         * Ajoute un montant à la caisse de la mutuelle
         */
        @Transactional
        public void addToMutuelleCaisse(BigDecimal amount) {
            if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
                return;
            }

            AccountMutuelle global = getMutuelleGlobalAccount();
            global.setSavingAmount(global.getSavingAmount().add(amount));
            globalRepo.save(global);
        }

        /**
         * retirer un montant à la caisse de la mutuelle
         */
        @Transactional
        public void removeToSolidarityMutuelleCaisse(BigDecimal amount) {
            if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
                return;
            }

            AccountMutuelle global = getMutuelleGlobalAccount();
            global.setSolidarityAmount(global.getSolidarityAmount().subtract(amount));
            globalRepo.save(global);
        }

        /**
         * retirer un montant à la caisse de la mutuelle
         */
        @Transactional
        public void removeToRegistrationMutuelleCaisse(BigDecimal amount) {
            if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
                return;
            }

            AccountMutuelle global = getMutuelleGlobalAccount();
            global.setRegistrationAmount(global.getRegistrationAmount().subtract(amount));
            globalRepo.save(global);
        }

        /**
         * Un membre emprunte de l'argent à la mutuelle
         */
        @Transactional
        public void borrowMoney(Long memberId, BigDecimal amount, Long sessionId) {
            LocalDateTime now = LocalDateTime.now();

            if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
                throw new IllegalArgumentException("Le montant emprunté doit être positif");
            }

            AccountMember memberAccount = getMemberAccount(memberId);
            AccountMutuelle globalAccount = getMutuelleGlobalAccount();

            // Vérifie que la mutuelle a assez d'épargne globale
            if (globalAccount.getSavingAmount().compareTo(amount) < 0) {
                throw new IllegalStateException("Fonds insuffisants dans la mutuelle");
            }
            memberAccount.setInitialBorrowAmount(amount);
            memberAccount.setLastInterestDate(now);
            memberAccount.setBorrowSessionId(sessionId);

            memberAccount.setBorrowAmount(memberAccount.getBorrowAmount().add(amount));
            globalAccount.setBorrowAmount(globalAccount.getBorrowAmount().add(amount));
            globalAccount.setSavingAmount(globalAccount.getSavingAmount().subtract(amount));

            memberRepo.save(memberAccount);
            globalRepo.save(globalAccount);
        }

        /**
         * Remboursement d'un emprunt par un membre
         */
        @Transactional
        public void repayBorrowedAmount(Long memberId, BigDecimal amount) {
            if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
                throw new IllegalArgumentException("Le montant remboursé doit être positif");
            }

            AccountMember memberAccount = getMemberAccount(memberId);
            AccountMutuelle globalAccount = getMutuelleGlobalAccount();

            if (memberAccount.getBorrowAmount().compareTo(amount) < 0) {
                throw new IllegalArgumentException("Le remboursement dépasse l'emprunt en cours");
            }

            memberAccount.setBorrowAmount(memberAccount.getBorrowAmount().subtract(amount));
            globalAccount.setSavingAmount(globalAccount.getSavingAmount().add(amount));
            globalAccount.setBorrowAmount(globalAccount.getBorrowAmount().subtract(amount));

            // Emprunt soldé → réinitialiser la session d'origine
            if (memberAccount.getBorrowAmount().compareTo(BigDecimal.ZERO) == 0) {
                memberAccount.setBorrowSessionId(null);
            }

            memberRepo.save(memberAccount);
            globalRepo.save(globalAccount);
        }


        /**
         * Paiement des frais d'inscription par un membre (partiel ou total)
         */
        @Transactional
        public void payFeeInscriptionAmount(Long memberId, BigDecimal amount) {
            if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
                throw new IllegalArgumentException("Le montant payé doit être positif");
            }

            AccountMember memberAccount = getMemberAccount(memberId);
            AccountMutuelle globalAccount = getMutuelleGlobalAccount();

            BigDecimal unpaid = memberAccount.getUnpaidRegistrationAmount();

            if (unpaid.compareTo(BigDecimal.ZERO) <= 0) {
                throw new IllegalStateException("Aucuns frais d'inscription impayés");
            }

            if (amount.compareTo(unpaid) > 0) {
                throw new IllegalArgumentException("Le paiement dépasse les frais d'inscription dus");
            }

            // Réduire la dette du membre
            memberAccount.setUnpaidRegistrationAmount(unpaid.subtract(amount));

            // L'argent entre dans la caisse inscription de la mutuelle (  )
            globalAccount.setRegistrationAmount(globalAccount.getRegistrationAmount().add(amount));

            memberRepo.save(memberAccount);
            globalRepo.save(globalAccount);
        }

        /**
         * Paiement du renfoulement par un membre avec ventilation automatique :
         * - jusqu'au seuil agape de l'exercice → Caisse Inscription
         * - au-delà → Caisse Solidarité
         */
        @Transactional
        public record RenfoulementSplit(BigDecimal partInscription, BigDecimal partSolidarite) {}

        public RenfoulementSplit payRenfoulementAmount(Long memberId, BigDecimal amount, Long exerciceId) {
            if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
                throw new IllegalArgumentException("Le montant payé doit être positif");
            }

            AccountMember memberAccount = getMemberAccount(memberId);
            AccountMutuelle globalAccount = getMutuelleGlobalAccount();

            BigDecimal unpaid = memberAccount.getUnpaidRenfoulement();

            if (unpaid.compareTo(BigDecimal.ZERO) <= 0) {
                throw new IllegalStateException("Aucun renfoulement impayé");
            }

            if (amount.compareTo(unpaid) > 0) {
                throw new IllegalArgumentException("Le paiement dépasse le renfoulement dû");
            }

            // Réduire la dette du membre
            memberAccount.setUnpaidRenfoulement(unpaid.subtract(amount));

            // Charger le Renfoulement de l'exercice
            Renfoulement renfoulement = renfoulementRepository.findByExerciceId(exerciceId)
                    .orElseThrow(() -> new IllegalStateException(
                            "Aucun renfoulement trouvé pour l'exercice ID : " + exerciceId));

            // Seuil = total des agapes de l'exercice (stocké dans Renfoulement à la clôture)
            BigDecimal seuilAgape = renfoulement.getAgapeAmount();

            // Montant déjà versé en Caisse Inscription pour cet exercice
            BigDecimal dejaCollecte = renfoulement.getRenfoulementCollectedForInscription();

            // Capacité restante dans Caisse Inscription avant d'atteindre le seuil agape
            BigDecimal capaciteInscription = seuilAgape.subtract(dejaCollecte).max(BigDecimal.ZERO);

            BigDecimal partInscription;
            BigDecimal partSolidarite;

            if (capaciteInscription.compareTo(BigDecimal.ZERO) == 0) {
                // Seuil agape déjà atteint → tout va en Caisse Solidarité
                partInscription = BigDecimal.ZERO;
                partSolidarite = amount;
            } else if (amount.compareTo(capaciteInscription) <= 0) {
                // Tout le paiement tient dans la capacité inscription restante
                partInscription = amount;
                partSolidarite = BigDecimal.ZERO;
            } else {
                // Paiement hybride : partie en Inscription pour atteindre le seuil, reste en Solidarité
                partInscription = capaciteInscription;
                partSolidarite = amount.subtract(capaciteInscription);
            }

            if (partInscription.compareTo(BigDecimal.ZERO) > 0) {
                globalAccount.setRegistrationAmount(globalAccount.getRegistrationAmount().add(partInscription));
                renfoulement.setRenfoulementCollectedForInscription(dejaCollecte.add(partInscription));
            }
            if (partSolidarite.compareTo(BigDecimal.ZERO) > 0) {
                globalAccount.setSolidarityAmount(globalAccount.getSolidarityAmount().add(partSolidarite));
            }

            renfoulementRepository.save(renfoulement);

            memberRepo.save(memberAccount);
            globalRepo.save(globalAccount);

            return new RenfoulementSplit(partInscription, partSolidarite);
        }

        /**
         * Récupère un compte membre par son ID (ID de la table accounts_member)
         */
        public AccountMember getMemberAccountById(Long accountId) {
            return memberRepo.findById(accountId)
                    .orElseThrow(() -> new RuntimeException("Compte membre introuvable avec l'ID : " + accountId));
        }

        /**
         * Récupère le compte d'un membre à partir de l'ID du membre
         */
        public AccountMember getMemberAccountByMemberId(Long memberId) {
            return memberRepo.findByMemberId(memberId)
                    .orElseThrow(() -> new RuntimeException("Compte membre introuvable pour le membre ID : " + memberId));
        }

        /**
         * Ajoute un montant au renfoulement impayé du membre
         */
        @Transactional
        public void addToUnpaidRenfoulement(AccountMember memberAccount, BigDecimal amount) {
            if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
                throw new IllegalArgumentException("Le montant à ajouter doit être positif");
            }

            BigDecimal currentUnpaid = memberAccount.getUnpaidRenfoulement();
            if (currentUnpaid == null) {
                currentUnpaid = BigDecimal.ZERO;
            }

            memberAccount.setUnpaidRenfoulement(currentUnpaid.add(amount));
            memberRepo.save(memberAccount);
        }
    }
