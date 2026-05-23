package com.mutuelle.mobille.repository;

import com.mutuelle.mobille.enums.SolvencyStatus;
import com.mutuelle.mobille.models.account.AccountMember;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface AccountMemberRepository extends JpaRepository<AccountMember, Long> {
    Optional<AccountMember> findByMemberId(Long memberId);
    List<AccountMember> findByBorrowAmountGreaterThan(BigDecimal zero);
    List<AccountMember> findByLastInterestDateBefore(LocalDateTime date);
    List<AccountMember> findAllByIsActive(boolean isActive);
    List<AccountMember> findByUnpaidRenfoulementGreaterThan(BigDecimal amount);
    List<AccountMember> findBySolvencyStatus(SolvencyStatus solvencyStatus);
}
