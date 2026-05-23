package com.mutuelle.mobille.service.schedules;

import com.mutuelle.mobille.service.SolvencyService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class SolvencySchedules {

    private final SolvencyService solvencyService;

    /**
     * Évalue la solvabilité de tous les membres toutes les heures.
     * Si un membre a une dette > 250k, son statut devient INSOLVABLE et il est désactivé.
     * Si sa dette baisse, il redevient SOLVABLE et est réactivé.
     */
    @Scheduled(fixedDelay = 3600000, initialDelay = 60000) // 1 heure
    public void evaluateMembersSolvency() {
        log.info("Starting scheduled solvency evaluation...");
        try {
            solvencyService.evaluateAllMembersSolvency();
            log.info("Solvency evaluation completed successfully");
        } catch (Exception e) {
            log.error("Error during solvency evaluation", e);
        }
    }
}
