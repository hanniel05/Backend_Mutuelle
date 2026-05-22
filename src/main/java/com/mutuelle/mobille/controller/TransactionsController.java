package com.mutuelle.mobille.controller;

import com.mutuelle.mobille.dto.ApiResponseDto;
import com.mutuelle.mobille.dto.transaction.TransactionResponseDTO;
import com.mutuelle.mobille.enums.TransactionDirection;
import com.mutuelle.mobille.enums.TransactionType;
import com.mutuelle.mobille.service.TransactionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/transactions")
@RequiredArgsConstructor
@Tag(name = "Transactions", description = "Gestion des transactions (épargne, emprunt, solidarité, etc.)")
public class TransactionsController {

    private final TransactionService transactionService;

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Lister toutes les transactions avec filtres optionnels")
    public ResponseEntity<ApiResponseDto<List<TransactionResponseDTO>>> getAllTransactions(
            @RequestParam(required = false) TransactionType type,
            @RequestParam(required = false) TransactionDirection direction,
            @RequestParam(required = false) Long sessionId,
            @RequestParam(required = false) Long exerciceId,
            @RequestParam(required = false) Long accountMemberId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime toDate,

            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) int size,
            @RequestParam(defaultValue = "createdAt,desc") String sort) {

        String[] sortParams = sort.split(",");
        Sort.Direction directionSort = sortParams.length > 1 && sortParams[1].equalsIgnoreCase("asc")
                ? Sort.Direction.ASC : Sort.Direction.DESC;
        Pageable pageable = PageRequest.of(page, size, Sort.by(directionSort, sortParams[0]));

        Page<TransactionResponseDTO> result = transactionService.getTransactionsFiltered(
                type, direction, sessionId, exerciceId, accountMemberId, fromDate, toDate, pageable);

        ApiResponseDto<List<TransactionResponseDTO>> response = ApiResponseDto.okPaged(
                result.getContent(),
                "Liste des transactions récupérée avec succès",
                result.getTotalElements(),
                result.getTotalPages(),
                result.getNumber(),
                result.getSize()
        );

        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Récupérer une transaction par ID")
    public ResponseEntity<ApiResponseDto<TransactionResponseDTO>> getTransactionById(@PathVariable Long id) {
        TransactionResponseDTO transaction = transactionService.getTransactionById(id);
        return ResponseEntity.ok(ApiResponseDto.ok(transaction, "Transaction récupérée avec succès"));
    }

}