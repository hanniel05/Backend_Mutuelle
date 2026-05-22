package com.mutuelle.mobille.controller;

import com.mutuelle.mobille.dto.ApiResponseDto;
import com.mutuelle.mobille.dto.sessionHistory.SessionHistoryResponseDTO;
import com.mutuelle.mobille.repository.ExerciceRepository;
import com.mutuelle.mobille.service.SessionHistoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/sessions/history")
@RequiredArgsConstructor
@Tag(name = "Historique des sessions (archives des sessions clôturées)")
public class SessionHistoryController {

    private final SessionHistoryService sessionHistoryService;
    private final ExerciceRepository exerciceRepository;

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'PRESIDENT', 'TRESORIER')")
    @Operation(summary = "Récupérer tout l'historique des sessions")
    public ResponseEntity<ApiResponseDto<List<SessionHistoryResponseDTO>>> getAllHistory() {
        List<SessionHistoryResponseDTO> history = sessionHistoryService.getAllHistory();
        return ResponseEntity.ok(ApiResponseDto.ok(history, "Historique récupéré avec succès"));
    }



    @GetMapping("/by-exercice/{exerciceId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'PRESIDENT', 'TRESORIER')")
    @Operation(summary = "Récupérer l'historique des sessions d'un exercice")
    public ResponseEntity<ApiResponseDto<List<SessionHistoryResponseDTO>>> getHistoryByExercice(
            @PathVariable Long exerciceId) {

        // 1. Vérifier d'abord si l'exercice existe
        if (!exerciceRepository.existsById(exerciceId)) {
            return ResponseEntity.status(404)
                    .body(ApiResponseDto.error("Exercice non trouvé avec l'id : " + exerciceId));
        }

        // 2. Récupérer l'historique
        List<SessionHistoryResponseDTO> history = sessionHistoryService.getHistoryByExerciceId(exerciceId);

        // 3. Message personnalisé selon le résultat
        String message = history.isEmpty()
                ? "Aucune session clôturée pour cet exercice"
                : "Historique des sessions de l'exercice récupéré";

        return ResponseEntity.ok(ApiResponseDto.ok(history, message));
    }

    @GetMapping("/by-session/{sessionId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'PRESIDENT', 'TRESORIER')")
    @Operation(summary = "Récupérer l'historique d'une session spécifique")
    public ResponseEntity<ApiResponseDto<List<SessionHistoryResponseDTO>>> getHistoryBySession(
            @PathVariable Long sessionId) {
        try {
            List<SessionHistoryResponseDTO> history = sessionHistoryService.getHistoryBySessionId(sessionId);
            String message = history.isEmpty()
                    ? "Aucun historique pour cette session (session non terminée)"
                    : "Historique de la session récupéré";
            return ResponseEntity.ok(ApiResponseDto.ok(history, message));
        } catch (RuntimeException e) {
            // Session non trouvée
            return ResponseEntity.status(404)
                    .body(ApiResponseDto.error(e.getMessage()));
        }
    }
}
