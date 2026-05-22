package com.mutuelle.mobille.config;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mutuelle.mobille.dto.member.MemberRegisterDTO;
import com.mutuelle.mobille.enums.Role;
import com.mutuelle.mobille.models.Admin;
import com.mutuelle.mobille.models.TypeAssistance;
import com.mutuelle.mobille.models.auth.AuthUser;
import com.mutuelle.mobille.repository.AdminRepository;
import com.mutuelle.mobille.repository.AuthUserRepository;
import com.mutuelle.mobille.repository.MemberRepository;
import com.mutuelle.mobille.repository.TypeAssistanceRepository;
import com.mutuelle.mobille.service.MemberService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.io.ResourceLoader;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;
import java.math.BigDecimal;
import java.util.List;

@Component
@Slf4j
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private final AuthUserRepository authUserRepository;
    private final AdminRepository adminRepository;
    private final PasswordEncoder passwordEncoder;

    private final MemberService memberService;
    private final MemberRepository memberRepository;
    private final ObjectMapper objectMapper;
    private final ResourceLoader resourceLoader;
    private final TypeAssistanceRepository typeAssistanceRepository;

    @Override
    public void run(String... args) {
        createSuperAdminIfNotExists();
        createDefaultAdminIfNotExists();
        createDefaultPresidentIfNotExists();
        createDefaultTresorierIfNotExists();
        initializeMembersIfNeeded();
        initializeTypeAssistancesIfNeeded();
    }

    @Transactional // Très important pour que les deux saves soient dans la même transaction
    private void createSuperAdminIfNotExists() {
        String email = "superadmin@mutuelle.com";

        if (authUserRepository.existsByEmail(email)) {
            return;
        }

        // 1. Créer et sauvegarder l'Admin d'abord
        Admin superAdmin = new Admin();
        superAdmin.setFullName("Super Administrateur");
        superAdmin.setIsActive(true);
        superAdmin = adminRepository.saveAndFlush(superAdmin);

        // 2. Créer l'AuthUser avec la référence correcte
        AuthUser authUser = new AuthUser();
        authUser.setEmail(email);
        authUser.setPasswordHash(passwordEncoder.encode("admin123"));
        authUser.setRole(Role.SUPER_ADMIN);
        authUser.setUserRefId(superAdmin.getId());

        authUserRepository.save(authUser);

        System.out.println("Super Admin créé avec succès : " + email + " (mot de passe : admin123)");
    }

    @Transactional
    private void createDefaultAdminIfNotExists() {
        String email = "admin@mutuelle.com";

        if (authUserRepository.existsByEmail(email)) {
            return;
        }

        Admin admin = new Admin();
        admin.setFullName("Administrateur");
        admin.setIsActive(true);
        admin = adminRepository.saveAndFlush(admin);

        AuthUser authUser = new AuthUser();
        authUser.setEmail(email);
        authUser.setPasswordHash(passwordEncoder.encode("admin123"));
        authUser.setRole(Role.ADMIN);
        authUser.setUserRefId(admin.getId());

        authUserRepository.save(authUser);

        System.out.println("Admin classique créé avec succès : " + email + " (mot de passe : admin123)");
    }

    // Ajoute ces deux méthodes dans la classe DataInitializer

    @Transactional
    private void createDefaultPresidentIfNotExists() {
        String email = "president@mutuelle.com";

        if (authUserRepository.existsByEmail(email)) {
            return;
        }

        Admin president = new Admin();           // Tu utilises toujours la classe Admin ?
        president.setFullName("Président de la Mutuelle");
        president.setIsActive(true);
        president = adminRepository.saveAndFlush(president);

        AuthUser authUser = new AuthUser();
        authUser.setEmail(email);
        authUser.setPasswordHash(passwordEncoder.encode("admin123"));
        authUser.setRole(Role.PRESIDENT);
        authUser.setUserRefId(president.getId());

        authUserRepository.save(authUser);

        System.out.println("Président créé avec succès : " + email + " (mot de passe : admin123)");
    }

    @Transactional
    private void createDefaultTresorierIfNotExists() {
        String email = "tresorier@mutuelle.com";

        if (authUserRepository.existsByEmail(email)) {
            return;
        }

        Admin tresorier = new Admin();
        tresorier.setFullName("Trésorier de la Mutuelle");
        tresorier.setIsActive(true);
        tresorier = adminRepository.saveAndFlush(tresorier);

        AuthUser authUser = new AuthUser();
        authUser.setEmail(email);
        authUser.setPasswordHash(passwordEncoder.encode("admin123"));
        authUser.setRole(Role.TRESORIER);
        authUser.setUserRefId(tresorier.getId());

        authUserRepository.save(authUser);

        System.out.println("Trésorier créé avec succès : " + email + " (mot de passe : admin123)");
    }

    // Nouvelle méthode pour les membres
    private void initializeMembersIfNeeded() {
        // Vérification rapide : si des membres existent déjà, on skippe tout
        if (memberRepository.count() >= 59) {
            log.info("Des membres existent déjà en base ({}). Initialisation des membres ignorée.",
                    memberRepository.count());
            return;
        }

        log.info("Aucun membre trouvé. Initialisation des 60 membres existants...");

        try (InputStream inputStream = resourceLoader
                .getResource("classpath:data/members.json")
                .getInputStream()) {

            List<MemberRegisterDTO> members = objectMapper.readValue(
                    inputStream,
                    new TypeReference<List<MemberRegisterDTO>>() {}
            );

            for (MemberRegisterDTO dto : members) {
                try {
                    memberService.registerMember(dto);
                    log.info("Membre initialisé : {} {}", dto.getFirstname(), dto.getLastname());
                } catch (Exception e) {
                    log.warn("Échec création membre {} {} : {}",
                            dto.getFirstname(), dto.getLastname(), e.getMessage());
                    // On continue avec les autres membres même si un échoue
                }
            }

            log.info("Initialisation des 60 membres terminée.");
        } catch (Exception e) {
            log.error("Erreur lors du chargement du fichier initial-members.json", e);
        }
    }

    private void initializeTypeAssistancesIfNeeded() {
        if (typeAssistanceRepository.count() > 7) {
            log.info("Au moins un type d'assistance existe déjà ({}). Initialisation ignorée.",
                    typeAssistanceRepository.count());
            return;
        }

        log.info("Aucun type d'assistance trouvé. Chargement depuis type-assistances.json...");

        try (InputStream inputStream = resourceLoader
                .getResource("classpath:data/types_assistances.json")
                .getInputStream()) {

            // On lit directement la liste de TypeAssistance (pas besoin de DTO ici)
            List<TypeAssistance> assistances = objectMapper.readValue(
                    inputStream,
                    new TypeReference<List<TypeAssistance>>() {}
            );

            int count = 0;
            for (TypeAssistance type : assistances) {
                // Sécurité supplémentaire : éviter les doublons par nom
                if (typeAssistanceRepository.existsByNameIgnoreCase(type.getName())) {
                    log.warn("Type d'assistance déjà existant (skipped) : {}", type.getName());
                    continue;
                }

                // On force les bonnes valeurs au cas où le JSON serait approximatif
                type.setAmount(type.getAmount() != null ? type.getAmount() : BigDecimal.ZERO);

                typeAssistanceRepository.save(type);
                count++;
            }

            log.info("Initialisation terminée : {} types d'assistance créés.", count);

        } catch (Exception e) {
            log.error("Erreur lors du chargement de type-assistances.json", e);
        }
    }
}