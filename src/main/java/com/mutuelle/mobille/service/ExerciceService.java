package com.mutuelle.mobille.service;

import com.mutuelle.mobille.dto.exercice.ExerciceRequestDTO;
import com.mutuelle.mobille.dto.exercice.ExerciceResponseDTO;
import com.mutuelle.mobille.enums.StatusExercice;
import com.mutuelle.mobille.enums.TransactionDirection;
import com.mutuelle.mobille.enums.TransactionType;
import com.mutuelle.mobille.models.*;
import com.mutuelle.mobille.models.account.AccountMember;
import com.mutuelle.mobille.models.account.AccountMutuelle;
import com.mutuelle.mobille.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ExerciceService {

    private final ExerciceRepository exerciceRepository;
    private final ExerciceHistoryRepository exerciceHistoryRepository;
    private final SessionRepository sessionRepository;
    private final AccountService accountService;
    private final MemberRepository memberRepository;
    private final TransactionRepository transactionRepository;
    private final RenfoulementRepository renfoulementRepository;
    private final BilanService bilanService;


    private LocalDateTime now() {
        return LocalDateTime.now();
    }

    // ───────────────────────────────────────────────
    //              Validation centrale
    // ───────────────────────────────────────────────

    private void validateExerciceDates(Exercice exercice, Long excludeId) {
        LocalDateTime start = exercice.getStartDate();
        LocalDateTime end = exercice.getEndDate();

        if (end != null) {
            if (start.isAfter(end)) {
                throw new IllegalArgumentException("Date de début doit être ≤ date de fin");
            }

            if (start.toLocalDate().equals(end.toLocalDate())) {
                throw new IllegalArgumentException(
                        "L'exercice doit couvrir au moins deux jours " +
                                "(date de début et date de fin ne peuvent pas être le même jour)"
                );
            }
        }

        // Chevauchement avec un autre exercice
        boolean overlap = exerciceRepository.existsOverlapping(
                start,
                end != null ? end : LocalDateTime.MAX,
                excludeId != null ? excludeId : null
        );

        if (overlap) {
            throw new IllegalArgumentException("les plages de date de l'exercice coincide avec une autre");
        }

        // Unicité IN_PROGRESS
        boolean wouldBeInProgress = !start.isAfter(now()) && (end == null || !end.isBefore(now()));
        if (wouldBeInProgress && excludeId != null) {
            Optional<Exercice> other = exerciceRepository.findFirstByStatus(StatusExercice.IN_PROGRESS);
            if (other.isPresent() && !other.get().getId().equals(excludeId)) {
                throw new IllegalStateException("Un autre exercice est déjà IN_PROGRESS");
            }
        }

        // Interdire modification si historisé
        if (excludeId != null && exerciceHistoryRepository.existsByExerciceId(excludeId)) {
            throw new IllegalStateException("Exercice déjà clôturé (historique existant)");
        }
    }

    private void validateExerciceForCreation(Exercice exercice) {
        // Pas de validation de dates à la création car elles sont nulles
        if (exercice.getName() == null || exercice.getName().trim().isEmpty()) {
            throw new IllegalArgumentException("Le nom de l'exercice est obligatoire");
        }
    }

    private void validateExerciceForStart(Exercice exercice) {
        // Vérifier qu'aucun autre exercice n'est en cours
        Optional<Exercice> currentExercice = findCurrentExercice();
        if (currentExercice.isPresent()) {
            throw new IllegalStateException(
                    "Impossible de démarrer l'exercice '" + exercice.getName() +
                            "' car un exercice est déjà en cours : '" + currentExercice.get().getName() + "'"
            );
        }

        // Vérifier que l'exercice est bien au statut PLANNED
        if (exercice.getStatus() != StatusExercice.PLANNED) {
            throw new IllegalStateException(
                    "Seuls les exercices planifiés peuvent être démarrés. " +
                            "Statut actuel : " + exercice.getStatus()
            );
        }
    }

    private void validateExerciceForClose(Exercice exercice) {
        if (exercice.getStatus() != StatusExercice.IN_PROGRESS) {
            throw new IllegalStateException("Seuls les exercices en cours peuvent être clôturés");
        }

        if (exercice.getStartDate() == null) {
            throw new IllegalStateException("L'exercice n'a pas de date de début valide");
        }

        LocalDateTime now = now();
        if (now.isBefore(exercice.getStartDate())) {
            throw new IllegalStateException("L'exercice ne peut être clôturé avant sa date de début");
        }
    }

    // ───────────────────────────────────────────────
    //              CRUD
    // ───────────────────────────────────────────────

    public List<ExerciceResponseDTO> getAllExercices() {
        return exerciceRepository.findAll().stream()
                .map(this::toResponseDTO)
                .toList();
    }

    public ExerciceResponseDTO getExerciceById(Long id) {
        Exercice ex = exerciceRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Exercice non trouvé : " + id));
        return toResponseDTO(ex);
    }

    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public ExerciceResponseDTO createExercice(ExerciceRequestDTO request) {
        Exercice exercice = Exercice.builder()
                .name(request.getName())
                .startDate(null)
                .endDate(null)
                .status(StatusExercice.PLANNED)
                .build();

        //validateExerciceDates(exercice, null);
        validateExerciceForCreation(exercice);
        exercice = exerciceRepository.save(exercice);
        return toResponseDTO(exercice);
    }

    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public ExerciceResponseDTO updateExercice(Long id, ExerciceRequestDTO request) {
        Exercice exercice = exerciceRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Exercice non trouvé"));

        StatusExercice currentStatus = exercice.getStatus();
        if (currentStatus == StatusExercice.COMPLETED || currentStatus == StatusExercice.CANCELLED) {
            throw new IllegalStateException("Modification interdite sur exercice terminé ou annulé");
        }

        // Interdire modification si déjà historisé
        if (exerciceHistoryRepository.existsByExerciceId(id)) {
            throw new IllegalStateException("Exercice déjà clôturé (historique existant)");
        }

        // Mise à jour des champs modifiables
        if (request.getName() != null) exercice.setName(request.getName());

        /*/ Gestion stricte des dates
        if (currentStatus == StatusExercice.IN_PROGRESS) {
            // startDate interdit
            if (request.getStartDate() != null && !request.getStartDate().equals(exercice.getStartDate())) {
                throw new IllegalArgumentException("Modification de startDate interdite sur exercice en cours");
            }
            // endDate → prolongation uniquement
            if (request.getEndDate() != null) {
                LocalDateTime newEnd = request.getEndDate();
                if (newEnd.isBefore(exercice.getEndDate())) {
                    throw new IllegalArgumentException("Raccourcissement d'un exercice en cours interdit");
                }
                if (newEnd.isBefore(now())) {
                    throw new IllegalArgumentException("Nouvelle date de fin doit être dans le futur");
                }
                exercice.setEndDate(newEnd);
            }
        } else {
            if (request.getStartDate() != null) exercice.setStartDate(request.getStartDate());
            if (request.getEndDate() != null) exercice.setEndDate(request.getEndDate());
        }

        Exercice fakeClone = Exercice.builder()
                .startDate(request.getStartDate() != null ? request.getStartDate() : exercice.getStartDate())
                .endDate(request.getEndDate() != null ? request.getEndDate() : exercice.getEndDate())
                .build();*/

        //validateExerciceDates(fakeClone, id);
        exercice = exerciceRepository.save(exercice);
        return toResponseDTO(exercice);
    }

    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public void deleteExercice(Long id) {
        Exercice ex = exerciceRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Exercice non trouvé"));

        // Nouvelle règle : Interdire si en cours ou terminé
        if (ex.getStatus() == StatusExercice.IN_PROGRESS || ex.getStatus() == StatusExercice.COMPLETED) {
            throw new IllegalStateException("Impossible de supprimer un exercice en cours ou terminé");
        }

        // Sécurité supplémentaire existante (historique)
        if (exerciceHistoryRepository.existsByExerciceId(id)) {
            throw new IllegalStateException("Impossible de supprimer un exercice historisé");
        }

        exerciceRepository.delete(ex);
    }

    // ───────────────────────────────────────────────
    //              Méthodes lecture / état
    // ───────────────────────────────────────────────

    public Optional<Exercice> findCurrentExercice() {
        LocalDateTime n = now();
        return exerciceRepository.findFirstByStatus(StatusExercice.IN_PROGRESS);
    }

    public Optional<ExerciceResponseDTO> getCurrentExerciceDTO() {
        return findCurrentExercice().map(this::toResponseDTO);
    }

    // ───────────────────────────────────────────────
    //              Actions manuelles
    // ───────────────────────────────────────────────

    @Transactional
    public void startExerciceIfDue(Exercice exercice) {
        if (exercice.getStatus() != StatusExercice.PLANNED) return;

        LocalDateTime n = now();
        if (!exercice.getStartDate().isAfter(n)) {
            exercice.setStatus(StatusExercice.IN_PROGRESS);
            exerciceRepository.save(exercice);
            onExerciceStarted(exercice);
        }
    }

    @Transactional
    public void closeExerciceIfExpired(Exercice exercice) {
        if (exercice.getStatus() != StatusExercice.IN_PROGRESS) return;

        LocalDateTime n = now();
        if (exercice.getEndDate() != null && exercice.getEndDate().isBefore(n)) {
            exercice.setStatus(StatusExercice.COMPLETED);
            exerciceRepository.save(exercice);
            onExerciceEnded(exercice);
        }
    }

    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public ExerciceResponseDTO startExercice(Long exerciceId) {
        Exercice exercice = exerciceRepository.findById(exerciceId)
                .orElseThrow(() -> new RuntimeException("Exercice non trouvé : " + exerciceId));

        validateExerciceForStart(exercice);

        exercice.setStartDate(LocalDateTime.now());
        exercice.setStatus(StatusExercice.IN_PROGRESS);
        exercice = exerciceRepository.save(exercice);

        onExerciceStarted(exercice);

        log.info("Exercice '{}' démarré manuellement le {}", exercice.getName(), exercice.getStartDate());

        return toResponseDTO(exercice);
    }

    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public ExerciceResponseDTO closeExercice(Long exerciceId) {
        Exercice exercice = exerciceRepository.findById(exerciceId)
                .orElseThrow(() -> new RuntimeException("Exercice non trouvé : " + exerciceId));

        validateExerciceForClose(exercice);

        exercice.setEndDate(LocalDateTime.now());
        exercice.setStatus(StatusExercice.COMPLETED);
        exercice = exerciceRepository.save(exercice);

        onExerciceEnded(exercice);

        log.info("Exercice '{}' clôturé manuellement le {}", exercice.getName(), exercice.getEndDate());

        return toResponseDTO(exercice);
    }

    @Transactional
    public void onExerciceStarted(Exercice exercice) {
        // Logique métier au démarrage (notifications, initialisations, etc.)
        // Pour l'instant : trace simple
        log.info("Exercice démarré : {} (ID: {})", exercice.getName(), exercice.getId());
        // → Ajouter ici : envoi mail, création compteur, etc.
    }

    @Transactional
    public void onExerciceEnded(Exercice exercice) {
        if (exerciceHistoryRepository.existsByExerciceId(exercice.getId())) return;

        AccountMutuelle mutuelleacc = accountService.getMutuelleGlobalAccount();
        List<Session> sessions = sessionRepository.findByExerciceId(exercice.getId());

        // ── Agrégation depuis les SessionHistory ──────────────────────────────
        BigDecimal totalAssistanceAmount      = BigDecimal.ZERO;
        Long       totalAssistanceCount       = 0L;
        BigDecimal totalAgapeAmount           = BigDecimal.ZERO;
        Long       totalTransactions          = 0L;
        BigDecimal totalSolidarityCollected   = BigDecimal.ZERO;
        BigDecimal totalEpargneDeposited      = BigDecimal.ZERO;
        BigDecimal totalEpargneWithdrawn      = BigDecimal.ZERO;
        BigDecimal totalEmpruntAmount         = BigDecimal.ZERO;
        BigDecimal totalRemboursementAmount   = BigDecimal.ZERO;
        BigDecimal totalInteretAmount         = BigDecimal.ZERO;
        BigDecimal totalRenfoulementCollected = BigDecimal.ZERO;
        BigDecimal totalRegistrationCollected = BigDecimal.ZERO;

        for (Session session : sessions) {
            SessionHistory hist = session.getHistory();
            if (hist != null) {
                totalAssistanceAmount      = totalAssistanceAmount.add(orZero(hist.getTotalAssistanceAmount()));
                totalAssistanceCount       += orZeroL(hist.getTotalAssistanceCount());
                totalAgapeAmount           = totalAgapeAmount.add(orZero(hist.getAgapeAmount()));
                totalTransactions          += orZeroL(hist.getTotalTransactions());
                totalSolidarityCollected   = totalSolidarityCollected.add(orZero(hist.getTotalSolidarityCollected()));
                totalEpargneDeposited      = totalEpargneDeposited.add(orZero(hist.getTotalEpargneDeposited()));
                totalEpargneWithdrawn      = totalEpargneWithdrawn.add(orZero(hist.getTotalEpargneWithdrawn()));
                totalEmpruntAmount         = totalEmpruntAmount.add(orZero(hist.getTotalEmpruntAmount()));
                totalRemboursementAmount   = totalRemboursementAmount.add(orZero(hist.getTotalRemboursementAmount()));
                totalInteretAmount         = totalInteretAmount.add(orZero(hist.getTotalInteretAmount()));
                totalRenfoulementCollected = totalRenfoulementCollected.add(orZero(hist.getTotalRenfoulementCollected()));
                totalRegistrationCollected = totalRegistrationCollected.add(orZero(hist.getTotalRegistrationCollected()));
            }
        }

        long activeMembersCount = accountService.getAllMemberAccountsWithActive(true).size();

        ExerciceHistory history = ExerciceHistory.builder()
                .exercice(exercice)
                .totalAssistanceAmount(totalAssistanceAmount)
                .totalAssistanceCount(totalAssistanceCount)
                .totalAgapeAmount(totalAgapeAmount)
                .totalTransactions(totalTransactions)
                .totalSolidarityCollected(totalSolidarityCollected)
                .totalEpargneDeposited(totalEpargneDeposited)
                .totalEpargneWithdrawn(totalEpargneWithdrawn)
                .totalEmpruntAmount(totalEmpruntAmount)
                .totalRemboursementAmount(totalRemboursementAmount)
                .totalInteretAmount(totalInteretAmount)
                .totalRenfoulementCollected(totalRenfoulementCollected)
                .totalRegistrationCollected(totalRegistrationCollected)
                .sessionsCount(sessions.size())
                .activeMembersCount(activeMembersCount)
                .mutuelleCash(mutuelleacc.getSavingAmount().add(mutuelleacc.getSolidarityAmount()).add(mutuelleacc.getRegistrationAmount()))
                .mutuellesSavingAmount(mutuelleacc.getSavingAmount())
                .mutuelleSolidarityAmount(mutuelleacc.getSolidarityAmount())
                .mutuelleRegistrationAmount(mutuelleacc.getRegistrationAmount())
                .mutuelleBorrowAmount(mutuelleacc.getBorrowAmount())
                .build();

        ExerciceHistory savedHistory = exerciceHistoryRepository.save(history);

        // Synchronise le côté inverse @OneToOne en mémoire (JPA ne le fait pas automatiquement)
        exercice.setHistory(savedHistory);

        // ── Calcul du renfoulement ─────────────────────────────────────────────
        this.calculateAndAssignRenfoulementForExercice(exercice);

        // ── Mise à jour ExerciceHistory avec données renfoulement ─────────────
        BigDecimal renfoulementUnitaire = BigDecimal.ZERO;
        var renfoulementOpt = renfoulementRepository.findByExerciceId(exercice.getId());
        if (renfoulementOpt.isPresent()) {
            var r = renfoulementOpt.get();
            renfoulementUnitaire = r.getUnitAmount() != null ? r.getUnitAmount() : BigDecimal.ZERO;
            savedHistory.setTotalRenfoulementDistributed(r.getExpectedTotalAmount() != null ? r.getExpectedTotalAmount() : BigDecimal.ZERO);
            savedHistory.setRenfoulementUnitAmount(renfoulementUnitaire);
            exerciceHistoryRepository.save(savedHistory);
        }

        // ── Création des bilans membres par exercice ──────────────────────────
        List<Member> activeMembers = memberRepository.findByIsActiveTrue();
        bilanService.createMemberExerciceBilans(exercice, activeMembers, sessions, renfoulementUnitaire);
    }

    private BigDecimal orZero(BigDecimal v) {
        return v != null ? v : BigDecimal.ZERO;
    }

    private long orZeroL(Long v) {
        return v != null ? v : 0L;
    }


    /**
     * Calcule et assigne le renfoulement pour tous les membres actifs à la fin d'un exercice.
     * Le montant unitaire est calculé sur la base des membres à jour uniquement.
     * La dette est ensuite appliquée à tous les membres actifs.
     *
     * @param exercice l'exercice qui vient de se terminer
     */
    @Transactional
    public void calculateAndAssignRenfoulementForExercice(Exercice exercice) {

        ExerciceHistory history = exercice.getHistory();
        if (history == null) {
            log.warn("Impossible de calculer le renfoulement : aucun historique pour l'exercice {}", exercice.getId());
            return;
        }

        // 1. Calcul du total à répartir
        BigDecimal totalAssistances = history.getTotalAssistanceAmount() != null
                ? history.getTotalAssistanceAmount()
                : BigDecimal.ZERO;

        BigDecimal totalAgapes = history.getTotalAgapeAmount() != null
                ? history.getTotalAgapeAmount()
                : BigDecimal.ZERO;

        BigDecimal totalADistribuer = totalAssistances.add(totalAgapes);

        if (totalADistribuer.compareTo(BigDecimal.ZERO) <= 0) {
            log.info("Aucun renfoulement à calculer pour l'exercice {} (total = 0)", exercice.getId());
            return;
        }

        // 2. Membres à jour (ceux qui servent de base au calcul du montant unitaire)
        List<AccountMember> comptesAJour = memberRepository.findAllActiveWithAccount().stream()
                .map(Member::getAccountMember)
                .filter(compte -> compte != null
                        && compte.getUnpaidRegistrationAmount().compareTo(BigDecimal.ZERO) == 0
                        && compte.getUnpaidSolidarityAmount().compareTo(BigDecimal.ZERO) == 0)
                .toList();

        int nbMembresAJour = comptesAJour.size();
        if (nbMembresAJour == 0) {
            nbMembresAJour=1;
//            log.warn("Aucun membre à jour → impossible de calculer le renfoulement pour l'exercice {}", exercice.getId());
//            return;
        }

        // 3. Montant unitaire de renfoulement (arrondi commercial vers le bas à la tranche de 25)
        BigDecimal renfoulementUnitaire = com.mutuelle.mobille.util.MoneyUtil.floorToNearest25(
                totalADistribuer.divide(
                        BigDecimal.valueOf(nbMembresAJour),
                        2,
                        RoundingMode.HALF_UP
                )
        );

        if (renfoulementUnitaire.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }

        // 4. Tous les membres actifs (ceux qui vont recevoir la dette)
        List<Member> membresActifs = memberRepository.findByIsActiveTrue();

        if (membresActifs.isEmpty()) {
            log.info("Aucun membre actif pour assigner le renfoulement");
            return;
        }

        // Récupérer une session de référence (la plus récente par ex.)
        Set<Session> sessions = exercice.getSessions();
        if (sessions.isEmpty()) {
            log.error("Impossible de créer renfoulement : aucune session pour l'exercice {}", exercice.getId());
            return;
        }

        // Session la plus récente
        Session sessionRef = sessions.stream()
                .max(Comparator.comparing(Session::getStartDate))
                .orElse(null);

        BigDecimal totalAttribue=BigDecimal.ZERO;

        // 5. Création des transactions et mise à jour des comptes
        for (Member member : membresActifs) {
            AccountMember compte = member.getAccountMember();
            if (compte == null) {
                continue;
            }

            // Mise à jour de la dette de renfoulement
            BigDecimal detteActuelle = compte.getUnpaidRenfoulement() != null
                    ? compte.getUnpaidRenfoulement()
                    : BigDecimal.ZERO;

            BigDecimal nouvelleDette = detteActuelle.add(renfoulementUnitaire);
            compte.setUnpaidRenfoulement(nouvelleDette);

            // Création de la transaction
            Transaction transaction = Transaction.builder()
                    .amount(renfoulementUnitaire)
                    .transactionType(TransactionType.RENFOULEMENT)
                    .transactionDirection(TransactionDirection.DEBIT)
                    .description(String.format("Renfoulement exercice %s",
                            exercice.getName() != null ? exercice.getName() : exercice.getId()))
                    .accountMember(compte)
                    .session(sessionRef)
                    .build();

            transactionRepository.save(transaction);
            totalAttribue = totalAttribue.add(renfoulementUnitaire);
        }

        Renfoulement renfoulement=Renfoulement.builder()
                .baseMembersCount(nbMembresAJour)
                .distributedMembersCount(membresActifs.size())
                .totalToDistributeAmount(totalADistribuer)
                .unitAmount(renfoulementUnitaire)
                .exercice(exercice)
                .expectedTotalAmount(totalAttribue)
                .agapeAmount(totalAgapes)
                .renfoulementCollectedForInscription(BigDecimal.ZERO)
                .build();

        renfoulementRepository.save(renfoulement);

        log.info("Renfoulement calculé pour exercice {} : {} Fcfa par membre (base : {} membres à jour), appliqué à {} membres actifs",
                exercice.getId(), renfoulementUnitaire, nbMembresAJour, membresActifs.size());
    }

    public ExerciceResponseDTO toResponseDTO(Exercice ex) {
        return ExerciceResponseDTO.builder()
                .id(ex.getId())
                .name(ex.getName())
                .startDate(ex.getStartDate())
                .endDate(ex.getEndDate())
                .status(ex.getStatus())
                .createdAt(ex.getCreatedAt())
                .updatedAt(ex.getUpdatedAt())
                .build();
    }


}