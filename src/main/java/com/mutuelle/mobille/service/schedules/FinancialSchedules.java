package com.mutuelle.mobille.service.schedules;

import com.mutuelle.mobille.service.EmpruntService;
import com.mutuelle.mobille.service.PenaltyService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@EnableScheduling
@RequiredArgsConstructor
public class FinancialSchedules {

    private final EmpruntService empruntService;
    private final PenaltyService penaltyService;

    /**
     * Vérifie et applique les intérêts trimestriels sur les emprunts en cours
     * Exécuté tous les jours à 6h00 du matin
     */
    @Scheduled(cron = "0 0 6 * * ?")
    public void processTrimestrialInterests() {
        log.info("Début du traitement quotidien des intérêts trimestriels");

        try {
            empruntService.calculerEtRedistribuerInteretsTrimestriels();
            log.info("Traitement des intérêts trimestriels terminé avec succès");
        } catch (Exception e) {
            log.error("Erreur lors du calcul / redistribution des intérêts trimestriels", e);
            // Option : alerte admin (email, Slack, Sentry, etc.)
            // alertService.sendCriticalError("Échec intérêts trimestriels", e);
        }
    }

    /**
     * Applique les pénalités d'accumulation tous les 3 sessions fermées
     * Exécuté quotidiennement à 8h00 du matin
     */
    @Scheduled(cron = "0 0 8 * * ?")
    public void processPenaltyAccumulation() {
        log.info("Début du traitement des pénalités d'accumulation");

        try {
            penaltyService.applyPenaltiesToAllBorrowers();
            log.info("Traitement des pénalités d'accumulation terminé avec succès");
        } catch (Exception e) {
            log.error("Erreur lors de l'application des pénalités d'accumulation", e);
        }
    }

}
