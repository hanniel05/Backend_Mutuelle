package com.mutuelle.mobille.service;

import com.mutuelle.mobille.dto.auth.EmailUpdateDTO;
import com.mutuelle.mobille.dto.auth.PasswordUpdateDTO;
import com.mutuelle.mobille.dto.auth.PinUpdateDTO;
import com.mutuelle.mobille.dto.member.MemberRegisterDTO;
import com.mutuelle.mobille.dto.member.MemberResponseDTO;
import com.mutuelle.mobille.dto.member.MemberUpdateDTO;
import com.mutuelle.mobille.enums.TransactionType;
import com.mutuelle.mobille.enums.Role;
import com.mutuelle.mobille.models.account.AccountMember;
import com.mutuelle.mobille.models.auth.AuthUser;
import com.mutuelle.mobille.models.Member;
import com.mutuelle.mobille.models.MutuelleConfig;
import com.mutuelle.mobille.repository.AuthUserRepository;
import com.mutuelle.mobille.repository.MemberRepository;
import com.mutuelle.mobille.utils.SecurityUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class MemberService {

    private final MutuelleConfigService mutuelleConfigService;
    private final MemberRepository memberRepository;
    private final AuthUserRepository authUserRepository;
    private final PasswordEncoder passwordEncoder;

    // ===========================================================================
    // INSCRIPTION MEMBRE → Crée Member + Account + AuthUser (comme le superadmin)
    // ===========================================================================
    @Transactional
    public MemberResponseDTO  registerMember(MemberRegisterDTO dto) {
        MutuelleConfig config = mutuelleConfigService.getCurrentConfig();
        if (!dto.getPhone().isBlank() && memberRepository.existsByPhone(dto.getPhone())) {
            throw new IllegalArgumentException("Ce numéro de téléphone est déjà utilisé");
        }
        if (authUserRepository.existsByEmail(dto.getEmail())) {
            throw new IllegalArgumentException("Cet email est déjà utilisé");
        }

        // 1. Création du compte financier
        AccountMember accountMember = AccountMember.builder()
                .unpaidRegistrationAmount(config.getRegistrationFeeAmount())
                .baseRegistrationAmount(config.getRegistrationFeeAmount())
                .savingAmount(BigDecimal.ZERO)
                .solidarityAmount(BigDecimal.ZERO)
                .borrowAmount(BigDecimal.ZERO)
                .unpaidRenfoulement(BigDecimal.ZERO)
                .unpaidSolidarityAmount(config.getSolidarityFeeAmount())
                .isActive(true)
                .build();

        // 2. Création du membre
        Member member = Member.builder()
                .firstname(dto.getFirstname().trim())
                .lastname(dto.getLastname().trim())
                .phone(dto.getPhone().trim())
                .isActive(true)
                .accountMember(accountMember)
                .build();

        accountMember.setMember(member); // bidirectionnel
        member = memberRepository.save(member); // cascade → compte sauvé aussi

        // 3. Création de l'AuthUser (EXACTEMENT comme ton superadmin)
        AuthUser authUser = new AuthUser();
        authUser.setEmail(dto.getEmail().toLowerCase().trim());
        authUser.setPasswordHash(passwordEncoder.encode(dto.getPassword()));
        authUser.setRole(Role.MEMBER);
        authUser.setUserRefId(member.getId());

        authUserRepository.save(authUser);

        return toResponseDTO(member);
    }

    // ===========================================================================
    // PROFIL DU MEMBRE CONNECTÉ
    // ===========================================================================
    @Transactional(readOnly = true)
    public MemberResponseDTO getCurrentMemberProfile() {
        Long currentMemberId = SecurityUtil.getCurrentUserRefId();
        return getMemberById(currentMemberId);
    }

    // ===========================================================================
    // RÉCUPÉRER UN MEMBRE PAR ID
    // ===========================================================================
    @Transactional(readOnly = true)
    public MemberResponseDTO getMemberById(Long id) {
        Member member = memberRepository.findByIdWithAccount(id)
                .orElseThrow(() -> new IllegalArgumentException("Membre non trouvé"));

        return toResponseDTO(member);
    }

    // ===========================================================================
    // MISE À JOUR AVATAR
    // ===========================================================================
    @Transactional
    public void updateAvatar(String avatarUrl) {
        Long memberId = SecurityUtil.getCurrentUserRefId();
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new IllegalStateException("Membre introuvable"));

        member.setAvatar(avatarUrl);
        memberRepository.save(member);
    }

    // ===========================================================================
    // MAPPER → DTO
    // ===========================================================================
    public MemberResponseDTO toResponseDTO(Member member) {
        AccountMember accountMember = member.getAccountMember();
        AuthUser authUser = authUserRepository.findByUserRefIdAndRole(member.getId(), Role.MEMBER)
                .orElse(null);

        String email = authUser != null ? authUser.getEmail() : null;
        Role role = authUser != null ? authUser.getRole() : null;

        return new MemberResponseDTO(
                authUser.getId(),
                member.getId(),
                member.getFirstname(),
                member.getLastname(),
                member.getPhone(),
                email,
                member.getAvatar(),
                role,
                member.isActive(),
                accountMember.getUnpaidRegistrationAmount(),
                accountMember.getBaseRegistrationAmount(),
                accountMember.getSolidarityAmount(),
                accountMember.getBorrowAmount(),
                accountMember.getUnpaidRenfoulement(),
                accountMember.getSavingAmount(),
                accountMember.getId(),
                member.getPin(),
                member.getCreatedAt(),
                member.getUpdatedAt()
        );
    }

    @Transactional(readOnly = true)
    public List<MemberResponseDTO> getAllActiveMembers() {
        return memberRepository.findAllActiveWithAccount()
                .stream()
                .map(this::toResponseDTO)
                .toList();
    }

    @Transactional(readOnly = true)
    public Page<MemberResponseDTO> getMembersFiltered(
            String search,
            Boolean active,
            LocalDate createdAfter,
            LocalDate createdBefore,
            Pageable pageable) {

        // Construction des prédicats dynamiques
        Specification<Member> spec = Specification.where(null);

        // Filtre recherche (prénom, nom, téléphone)
        if (search != null && !search.trim().isEmpty()) {
            String term = "%" + search.trim().toLowerCase() + "%";
            spec = spec.and((root, query, cb) ->
                    cb.or(
                            cb.like(cb.lower(root.get("firstname")), term),
                            cb.like(cb.lower(root.get("lastname")), term),
                            cb.like(root.get("phone"), term)
                    ));
        }

        // Filtre actif/inactif
        if (active != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("isActive"), active));
        } else {
            // Par défaut : seulement les actifs (comme ton findAllActiveWithAccount)
            spec = spec.and((root, query, cb) -> cb.isTrue(root.get("isActive")));
        }

        // Filtre date création après
        if (createdAfter != null) {
            LocalDateTime start = createdAfter.atStartOfDay();
            spec = spec.and((root, query, cb) -> cb.greaterThanOrEqualTo(root.get("createdAt"), start));
        }

        // Filtre date création avant
        if (createdBefore != null) {
            LocalDateTime end = createdBefore.plusDays(1).atStartOfDay();
            spec = spec.and((root, query, cb) -> cb.lessThan(root.get("createdAt"), end));
        }

        // Requête avec fetch join + pagination
        Page<Member> page = memberRepository.findAll(spec, pageable);

        return page.map(this::toResponseDTO);
    }

    @Transactional
    public MemberResponseDTO updateCurrentMemberProfile(MemberUpdateDTO dto) {
        Long currentMemberId = SecurityUtil.getCurrentUserRefId();

        Member member = memberRepository.findById(currentMemberId)
                .orElseThrow(() -> new IllegalStateException("Membre connecté introuvable"));

        boolean modified = false;

        if (dto.getFirstname() != null && !dto.getFirstname().trim().isEmpty()) {
            member.setFirstname(dto.getFirstname().trim());
            modified = true;
        }

        if (dto.getLastname() != null && !dto.getLastname().trim().isEmpty()) {
            member.setLastname(dto.getLastname().trim());
            modified = true;
        }

        if (dto.getPhone() != null && !dto.getPhone().trim().isEmpty()) {
            String newPhone = dto.getPhone().trim();

            // Vérification d'unicité (très fortement recommandé)
            if (!newPhone.equals(member.getPhone()) &&
                    memberRepository.existsByPhone(newPhone)) {
                throw new IllegalArgumentException("Ce numéro de téléphone est déjà utilisé par un autre membre");
            }

            member.setPhone(newPhone);
            modified = true;
        }

        // Si rien n'a été modifié → on peut renvoyer directement (optionnel)
        if (!modified) {
            return toResponseDTO(member);
        }

        // Mise à jour de la date
        member.setUpdatedAt(LocalDateTime.now());

        member = memberRepository.save(member);

        return toResponseDTO(member);
    }

    @Transactional
    public MemberResponseDTO updatePin(PinUpdateDTO dto) {
        Long memberId = SecurityUtil.getCurrentUserRefId();
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new IllegalStateException("Membre introuvable"));

        AuthUser authUser = authUserRepository.findByUserRefIdAndRole(memberId, Role.MEMBER)
                .orElseThrow(() -> new IllegalStateException("Utilisateur d'authentification introuvable"));

        // Vérification ancien PIN
        if (!dto.getOldPin().equals(authUser.getPin())) {
            throw new IllegalArgumentException("Ancien PIN incorrect");
        }

        if (dto.getNewPin().equals(dto.getOldPin())) {
            throw new IllegalArgumentException("Le nouveau PIN doit être différent de l'ancien");
        }

        // Mise à jour
        authUser.setPin(dto.getNewPin());
        member.setPin(dto.getNewPin());   // si tu le gardes synchronisé dans Member

        authUserRepository.save(authUser);
        memberRepository.save(member);

        return toResponseDTO(member);
    }

    @Transactional
    public void updatePassword(PasswordUpdateDTO dto) {
        Long memberId = SecurityUtil.getCurrentUserRefId();

        AuthUser authUser = authUserRepository.findByUserRefIdAndRole(memberId, Role.MEMBER)
                .orElseThrow(() -> new IllegalStateException("Utilisateur introuvable"));

        // Vérification mot de passe actuel
        if (!passwordEncoder.matches(dto.getCurrentPassword(), authUser.getPasswordHash())) {
            throw new IllegalArgumentException("Mot de passe actuel incorrect");
        }

        if (!dto.getNewPassword().equals(dto.getConfirmNewPassword())) {
            throw new IllegalArgumentException("Les mots de passe ne correspondent pas");
        }

        // Optionnel : vérifier complexité (majuscule, chiffre, etc.)
        // if (!isStrongPassword(dto.getNewPassword())) { ... }

        authUser.setPasswordHash(passwordEncoder.encode(dto.getNewPassword()));
        authUserRepository.save(authUser);
    }

    @Transactional
    public MemberResponseDTO updateEmail(EmailUpdateDTO dto) {
        Long memberId = SecurityUtil.getCurrentUserRefId();
        AuthUser authUser = authUserRepository.findByUserRefIdAndRole(memberId, Role.MEMBER)
                .orElseThrow();

        if (!passwordEncoder.matches(dto.getPassword(), authUser.getPasswordHash())) {
            throw new IllegalArgumentException("Mot de passe incorrect");
        }

        if (authUserRepository.existsByEmail(dto.getNewEmail().toLowerCase().trim())) {
            throw new IllegalArgumentException("Cet email est déjà utilisé");
        }

        String oldEmail = authUser.getEmail();
        authUser.setEmail(dto.getNewEmail().toLowerCase().trim());
        authUserRepository.save(authUser);

        // Option : logger le changement ou envoyer notification à l'ancien email

        return toResponseDTO(memberRepository.findById(memberId).orElseThrow());
    }

    /**
     * Vérifie si un membre est à jour : a payé tous les frais d'inscription et la solidarité.
     * (unpaidRegistrationAmount == 0 && unpaidSolidarityAmount == 0)
     *
     * @param memberId L'ID du membre à vérifier
     * @return true si le membre est à jour, false sinon
     */
    @Transactional(readOnly = true)
    public boolean isMemberAJour(Long memberId) {
        Optional<Member> optionalMember = memberRepository.findByIdWithAccount(memberId);
        if (optionalMember.isEmpty()) {
            throw new IllegalArgumentException("Membre non trouvé avec l'ID : " + memberId);
        }
        AccountMember account = optionalMember.get().getAccountMember();
        return account.getUnpaidRegistrationAmount().compareTo(BigDecimal.ZERO) == 0 &&
                account.getUnpaidSolidarityAmount().compareTo(BigDecimal.ZERO) == 0;
    }


    public Optional<AuthUser> getAuthMember(Member member) {
        return authUserRepository.findByUserRefIdAndRole(member.getId(), Role.MEMBER);
    }

    @Transactional
    public MemberResponseDTO toggleMemberStatus(Long memberId, boolean active) {

        Member member = memberRepository.findByIdWithAccount(memberId)
                .orElseThrow(() -> new IllegalArgumentException("Membre non trouvé avec l'ID : " + memberId));

        AccountMember account = member.getAccountMember();
        if (account == null) {
            throw new IllegalStateException("Compte associé introuvable pour ce membre");
        }

        // Mise à jour des deux entités (cohérence)
        boolean wasActive = member.isActive();

        member.setActive(active);
        account.setActive(active);

        // Option : si tu veux empêcher la désactivation d'un membre qui a un solde positif / dette / etc.
        // if (!active && account.getSavingAmount().compareTo(BigDecimal.ZERO) > 0) {
        //     throw new IllegalStateException("Impossible de désactiver : solde d'épargne positif");
        // }

        // Mise à jour de la date
        member.setUpdatedAt(LocalDateTime.now());

        // Sauvegarde (cascade devrait fonctionner si bien configuré)
        memberRepository.save(member);

        // Si tu veux être explicite sur le compte :
        // accountRepository.save(account);  ← si tu as un AccountMemberRepository

        // Logique additionnelle possible :
        // - bloquer futures transactions si !active
        // - envoyer notification email/SMS
        // - créer une entrée d'audit

        return toResponseDTO(member);
    }

    /**
     * Récupère le compte d'un membre par son ID de AccountMember
     */
    public AccountMember getMemberAccountId(Long accountMemberId) {
        return memberRepository.findAll()
                .stream()
                .filter(member -> member.getAccountMember() != null && 
                        member.getAccountMember().getId().equals(accountMemberId))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("Compte membre introuvable avec l'ID : " + accountMemberId))
                .getAccountMember();
    }

    /**
     * Récupère le seuil de dette configuré dans la mutuelle
     */
    public BigDecimal getDebtThreshold() {
        MutuelleConfig config = mutuelleConfigService.getCurrentConfig();
        return config.getDebtThresholdAmount();
    }

}
