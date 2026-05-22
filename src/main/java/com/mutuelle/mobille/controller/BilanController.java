package com.mutuelle.mobille.controller;

import com.mutuelle.mobille.dto.ApiResponseDto;
import com.mutuelle.mobille.dto.bilan.MemberExerciceBilanDTO;
import com.mutuelle.mobille.dto.bilan.MemberSessionBilanDTO;
import com.mutuelle.mobille.dto.exercice.ExerciceHistoryDto;
import com.mutuelle.mobille.dto.sessionHistory.SessionHistoryResponseDTO;
import com.mutuelle.mobille.service.BilanPdfService;
import com.mutuelle.mobille.service.BilanService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/bilan")
@RequiredArgsConstructor
@Tag(name = "Bilans financiers", description = "Bilans par session/exercice pour chaque membre et pour la mutuelle")
public class BilanController {

    private final BilanService bilanService;
    private final BilanPdfService bilanPdfService;

    // ─────────────────────────────────────────────────────────────────────────
    //  BILAN MEMBRE PAR SESSION
    // ─────────────────────────────────────────────────────────────────────────

    @GetMapping("/membre/{memberId}/session/{sessionId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'PRESIDENT', 'TRESORIER', 'MEMBER')")
    @Operation(summary = "Bilan financier d'un membre pour une session (JSON)")
    public ResponseEntity<ApiResponseDto<MemberSessionBilanDTO>> getMemberSessionBilan(
            @PathVariable Long memberId,
            @PathVariable Long sessionId) {
        try {
            MemberSessionBilanDTO dto = bilanService.getMemberSessionBilan(memberId, sessionId);
            return ResponseEntity.ok(ApiResponseDto.ok(dto, "Bilan session membre récupéré"));
        } catch (RuntimeException e) {
            return ResponseEntity.status(404).body(ApiResponseDto.notFound(e.getMessage()));
        }
    }

    @GetMapping("/membre/{memberId}/session/{sessionId}/pdf")
    @PreAuthorize("hasAnyRole('ADMIN', 'PRESIDENT', 'TRESORIER', 'MEMBER')")
    @Operation(summary = "Télécharger le bilan d'un membre pour une session (PDF)")
    public ResponseEntity<byte[]> getMemberSessionBilanPdf(
            @PathVariable Long memberId,
            @PathVariable Long sessionId) {
        byte[] pdf = bilanPdfService.generateMemberSessionBilanPdf(memberId, sessionId);
        return pdfResponse(pdf, "bilan-membre-session-" + memberId + "-" + sessionId + ".pdf");
    }

    @GetMapping("/session/{sessionId}/membres")
    @PreAuthorize("hasAnyRole('ADMIN', 'PRESIDENT', 'TRESORIER')")
    @Operation(summary = "Bilans de tous les membres pour une session (JSON)")
    public ResponseEntity<ApiResponseDto<List<MemberSessionBilanDTO>>> getAllMemberBilansBySession(
            @PathVariable Long sessionId) {
        List<MemberSessionBilanDTO> dtos = bilanService.getAllMemberBilansBySession(sessionId);
        String msg = dtos.isEmpty() ? "Aucun bilan disponible pour cette session" : "Bilans récupérés";
        return ResponseEntity.ok(ApiResponseDto.ok(dtos, msg));
    }

    @GetMapping("/membre/{memberId}/sessions")
    @PreAuthorize("hasAnyRole('ADMIN', 'PRESIDENT', 'TRESORIER', 'MEMBER')")
    @Operation(summary = "Tous les bilans session d'un membre (JSON)")
    public ResponseEntity<ApiResponseDto<List<MemberSessionBilanDTO>>> getMemberAllSessionBilans(
            @PathVariable Long memberId) {
        List<MemberSessionBilanDTO> dtos = bilanService.getMemberAllSessionBilans(memberId);
        return ResponseEntity.ok(ApiResponseDto.ok(dtos, "Bilans sessions du membre récupérés"));
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  BILAN MEMBRE PAR EXERCICE
    // ─────────────────────────────────────────────────────────────────────────

    @GetMapping("/membre/{memberId}/exercice/{exerciceId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'PRESIDENT', 'TRESORIER', 'MEMBER')")
    @Operation(summary = "Bilan financier d'un membre pour un exercice (JSON)")
    public ResponseEntity<ApiResponseDto<MemberExerciceBilanDTO>> getMemberExerciceBilan(
            @PathVariable Long memberId,
            @PathVariable Long exerciceId) {
        try {
            MemberExerciceBilanDTO dto = bilanService.getMemberExerciceBilan(memberId, exerciceId);
            return ResponseEntity.ok(ApiResponseDto.ok(dto, "Bilan exercice membre récupéré"));
        } catch (RuntimeException e) {
            return ResponseEntity.status(404).body(ApiResponseDto.notFound(e.getMessage()));
        }
    }

    @GetMapping("/membre/{memberId}/exercice/{exerciceId}/pdf")
    @PreAuthorize("hasAnyRole('ADMIN', 'PRESIDENT', 'TRESORIER', 'MEMBER')")
    @Operation(summary = "Télécharger le bilan d'un membre pour un exercice (PDF)")
    public ResponseEntity<byte[]> getMemberExerciceBilanPdf(
            @PathVariable Long memberId,
            @PathVariable Long exerciceId) {
        byte[] pdf = bilanPdfService.generateMemberExerciceBilanPdf(memberId, exerciceId);
        return pdfResponse(pdf, "bilan-membre-exercice-" + memberId + "-" + exerciceId + ".pdf");
    }

    @GetMapping("/exercice/{exerciceId}/membres")
    @PreAuthorize("hasAnyRole('ADMIN', 'PRESIDENT', 'TRESORIER')")
    @Operation(summary = "Bilans de tous les membres pour un exercice (JSON)")
    public ResponseEntity<ApiResponseDto<List<MemberExerciceBilanDTO>>> getAllMemberBilansByExercice(
            @PathVariable Long exerciceId) {
        List<MemberExerciceBilanDTO> dtos = bilanService.getAllMemberBilansByExercice(exerciceId);
        String msg = dtos.isEmpty() ? "Aucun bilan disponible pour cet exercice" : "Bilans récupérés";
        return ResponseEntity.ok(ApiResponseDto.ok(dtos, msg));
    }

    @GetMapping("/exercice/{exerciceId}/membres/pdf")
    @PreAuthorize("hasAnyRole('ADMIN', 'PRESIDENT', 'TRESORIER')")
    @Operation(summary = "Télécharger la synthèse de tous les membres pour un exercice (PDF)")
    public ResponseEntity<byte[]> getAllMembersExercicePdf(@PathVariable Long exerciceId) {
        byte[] pdf = bilanPdfService.generateAllMembersExercicePdf(exerciceId);
        return pdfResponse(pdf, "synthese-membres-exercice-" + exerciceId + ".pdf");
    }

    @GetMapping("/membre/{memberId}/exercices")
    @PreAuthorize("hasAnyRole('ADMIN', 'PRESIDENT', 'TRESORIER', 'MEMBER')")
    @Operation(summary = "Tous les bilans exercice d'un membre (JSON)")
    public ResponseEntity<ApiResponseDto<List<MemberExerciceBilanDTO>>> getMemberAllExerciceBilans(
            @PathVariable Long memberId) {
        List<MemberExerciceBilanDTO> dtos = bilanService.getMemberAllExerciceBilans(memberId);
        return ResponseEntity.ok(ApiResponseDto.ok(dtos, "Bilans exercices du membre récupérés"));
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  BILAN MUTUELLE PAR SESSION
    // ─────────────────────────────────────────────────────────────────────────

    @GetMapping("/mutuelle/session/{sessionId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'PRESIDENT', 'TRESORIER')")
    @Operation(summary = "Bilan financier de la mutuelle pour une session (JSON)")
    public ResponseEntity<ApiResponseDto<SessionHistoryResponseDTO>> getMutuelleSessionBilan(
            @PathVariable Long sessionId) {
        try {
            SessionHistoryResponseDTO dto = bilanService.getMutuelleSessionBilan(sessionId);
            return ResponseEntity.ok(ApiResponseDto.ok(dto, "Bilan session mutuelle récupéré"));
        } catch (RuntimeException e) {
            return ResponseEntity.status(404).body(ApiResponseDto.notFound(e.getMessage()));
        }
    }

    @GetMapping("/mutuelle/session/{sessionId}/pdf")
    @PreAuthorize("hasAnyRole('ADMIN', 'PRESIDENT', 'TRESORIER')")
    @Operation(summary = "Télécharger le bilan de la mutuelle pour une session (PDF)")
    public ResponseEntity<byte[]> getMutuelleSessionBilanPdf(@PathVariable Long sessionId) {
        byte[] pdf = bilanPdfService.generateMutuelleSessionBilanPdf(sessionId);
        return pdfResponse(pdf, "bilan-mutuelle-session-" + sessionId + ".pdf");
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  BILAN MUTUELLE PAR EXERCICE
    // ─────────────────────────────────────────────────────────────────────────

    @GetMapping("/mutuelle/exercice/{exerciceId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'PRESIDENT', 'TRESORIER')")
    @Operation(summary = "Bilan financier de la mutuelle pour un exercice (JSON)")
    public ResponseEntity<ApiResponseDto<ExerciceHistoryDto>> getMutuelleExerciceBilan(
            @PathVariable Long exerciceId) {
        try {
            ExerciceHistoryDto dto = bilanService.getMutuelleExerciceBilan(exerciceId);
            return ResponseEntity.ok(ApiResponseDto.ok(dto, "Bilan exercice mutuelle récupéré"));
        } catch (RuntimeException e) {
            return ResponseEntity.status(404).body(ApiResponseDto.notFound(e.getMessage()));
        }
    }

    @GetMapping("/mutuelle/exercice/{exerciceId}/pdf")
    @PreAuthorize("hasAnyRole('ADMIN', 'PRESIDENT', 'TRESORIER')")
    @Operation(summary = "Télécharger le bilan de la mutuelle pour un exercice (PDF)")
    public ResponseEntity<byte[]> getMutuelleExerciceBilanPdf(@PathVariable Long exerciceId) {
        byte[] pdf = bilanPdfService.generateMutuelleExerciceBilanPdf(exerciceId);
        return pdfResponse(pdf, "bilan-mutuelle-exercice-" + exerciceId + ".pdf");
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Helper
    // ─────────────────────────────────────────────────────────────────────────

    private ResponseEntity<byte[]> pdfResponse(byte[] pdf, String filename) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_PDF_VALUE)
                .body(pdf);
    }
}
