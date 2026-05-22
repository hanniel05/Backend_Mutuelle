package com.mutuelle.mobille.service.notifications;

import com.mutuelle.mobille.dto.notifications.NotificationRequestDto;
import com.mutuelle.mobille.enums.NotificationChannel;
import com.mutuelle.mobille.enums.TemplateMailsName;
import com.mutuelle.mobille.models.Assistance;
import com.mutuelle.mobille.models.Member;
import com.mutuelle.mobille.models.Session;
import com.mutuelle.mobille.models.TypeAssistance;
import com.mutuelle.mobille.models.account.AccountMember;
import com.mutuelle.mobille.enums.Role;
import com.mutuelle.mobille.models.auth.AuthUser;
import com.mutuelle.mobille.repository.AuthUserRepository;
import com.mutuelle.mobille.service.AccountService;
import com.mutuelle.mobille.service.AdminService;
import com.mutuelle.mobille.service.notifications.config.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class SessionNotificationHelper {

    private final NotificationService notificationService;
    private final AccountService accountService;
    private final AuthUserRepository authUserRepo;

    private final AdminService adminService;

    // -------------------------------------------------------------------------
    //  Méthode de base : envoi à une liste arbitraire de membres
    // -------------------------------------------------------------------------
    public void notifyMembers(
            Collection<? extends Member> members,
            String title,
            TemplateMailsName templateName,
            Map<String, Object> variables,
            String fallbackMessage) {

        if (members == null || members.isEmpty()) {
            return;
        }

        // Récupération en une seule passe + filtrage
        List<AuthUser> validUsers = members.stream()
                .filter(Objects::nonNull)
                .filter(Member::isActive)
                .map(m -> authUserRepo.findByUserRefIdAndRole(m.getId(), Role.MEMBER))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .filter(u -> u.getEmail() != null && !u.getEmail().isBlank())
                .distinct()
                .collect(Collectors.toList());

        if (validUsers.isEmpty()) {
            return;
        }

        Map<String, Object> baseVars = variables != null ? new HashMap<>(variables) : new HashMap<>();

        int success = 0;
        for (AuthUser user : validUsers) {
            Map<String, Object> userVars = new HashMap<>(baseVars);
            userVars.putIfAbsent("memberId", user.getUserRefId());
            // Vous pouvez ajouter plus tard : userVars.put("firstName", ...)

            NotificationRequestDto request = NotificationRequestDto.builder()
                    .email(user.getEmail())
                    .title(title)
                    .templateName(templateName)
                    .variables(userVars)
                    .channels(Set.of(NotificationChannel.EMAIL, NotificationChannel.PUSH))
                    .message(fallbackMessage != null ? fallbackMessage : "Notification Mutuelle")
                    .build();

            try {
                notificationService.sendNotification(request);
                success++;
            } catch (Exception e) {
                log.error("Échec notification vers {} : {}", user.getEmail(), e.getMessage(), e);
            }
        }

        if (success > 0) {
            log.info("Notification '{}' envoyée à {} membres", title, success);
        }
    }

    public void notifyAccountMembers(
            Collection<AccountMember> accountMembers,
            String title,
            TemplateMailsName templateName,
            Map<String, Object> variables,
            String fallbackMessage) {

        List<Member> members = accountMembers.stream()
                .filter(Objects::nonNull)
                .map(AccountMember::getMember)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        notifyMembers(members, title, templateName, variables, fallbackMessage);
    }

    public void notifyAllActiveMembers(
            String title,
            TemplateMailsName templateName,
            Map<String, Object> variables,
            String fallbackMessage) {

        List<AccountMember> active = accountService.getAllMemberAccounts().stream()
                .filter(AccountMember::isActive)
                .collect(Collectors.toList());

        notifyAccountMembers(active, title, templateName, variables, fallbackMessage);
    }

    // -------------------------------------------------------------------------
    //  Notifications spécifiques aux événements de session
    // -------------------------------------------------------------------------

    public void notifySessionStarted(Session session) {
        Map<String, Object> vars = new HashMap<>();
        vars.put("sessionName", session.getName());
        vars.put("startDate", session.getStartDate());
        vars.put("endDate", session.getEndDate() != null ? session.getEndDate() : "Non définie");
        //vars.put("solidarityAmount", session.getSolidarityAmount());
        vars.put("agapeAmount", session.getAgapeAmountPerMember());

        notifyAllActiveMembers(
                "Début de la session " + session.getName(),
                TemplateMailsName.SESSION_STARTED,
                vars,
                "La session " + session.getName() + " est maintenant ouverte."
        );
    }

    public void notifySessionEnded(Session session) {
        Map<String, Object> vars = new HashMap<>();
        vars.put("sessionName", session.getName());
        vars.put("agapePerMember", session.getAgapeAmountPerMember());
        vars.put("totalAgapes", session.getHistory() != null ? session.getHistory().getAgapeAmount() : "—");
        vars.put("endDate", session.getEndDate());

        notifyAllActiveMembers(
                "Fin de la session " + session.getName(),
                TemplateMailsName.SESSION_ENDED,
                vars,
                "La session " + session.getName() + " est terminée."
        );
    }

    /*public void notifySolidarityApplied(Session session) {
        Map<String, Object> vars = new HashMap<>();
        vars.put("sessionName", session.getName());
        //vars.put("solidarityAmount", session.getSolidarityAmount());
        vars.put("currency", "XAF");

        notifyAllActiveMembers(
                "Ajout de la solidarité pour " + session.getName(),
                TemplateMailsName.SOLIDARITY_ADDED,
                vars,
                "Le montant de solidarité a été ajouté à votre compte pour la session " + session.getName()
        );
    }*/

    // -------------------------------------------------------------------------
    //  Alertes critiques administrateurs
    // -------------------------------------------------------------------------
    public void notifyAdminCritical(String title, String message, Throwable error) {
        String fullMsg = message;
        if (error != null) {
            fullMsg += "\n\nErreur : " + error.getClass().getSimpleName() + " - " + error.getMessage();
        }

        AuthUser admin=adminService.getAuthAdmin();

        NotificationRequestDto alert = NotificationRequestDto.builder()
                .email(admin.getEmail()) // ← À remplacer par vraie liste/service
                .title("[URGENT] " + title)
                .message(fullMsg)
                .channels(Set.of(NotificationChannel.EMAIL,NotificationChannel.PUSH))
                .build();

        try {
            notificationService.sendNotification(alert);
        } catch (Exception e) {
            log.error("ÉCHEC ENVOI ALERTE ADMIN CRITIQUE ! {} → {}", title, e.getMessage());
        }
    }

    public void notifyAssistanceCreated(Assistance assistance) {
        Member member = assistance.getMember();
        TypeAssistance type = assistance.getTypeAssistance();
        Session session = assistance.getSession();

        String title = "Assistance accordée : " + type.getName();

        Map<String, Object> variables = new HashMap<>();
        variables.put("memberName", member.getFirstname() != null ? member.getLastname() : "Cher membre");
        variables.put("assistanceType", type.getName());
        variables.put("amount", assistance.getAmountMove());
        variables.put("sessionName", session.getName());
        variables.put("date", assistance.getCreatedAt());
        variables.put("currency", "XAF"); // ou ta devise

        String fallbackMessage = String.format(
                "Vous avez bénéficié d'une assistance de %s (%s) pour la session %s.",
                assistance.getAmountMove(),
                type.getName(),
                session.getName()
        );

        // On notifie uniquement le membre concerné
        this.notifyMembers(
                List.of(member),
                title,
                TemplateMailsName.ASSISTANCE_GRANTED,
                variables,
                fallbackMessage
        );

//        log.info("Notification assistance envoyée au membre {} pour le type {}",
//                member.getId(), type.getName());
    }
}