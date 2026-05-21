package com.mutuelle.mobille.service;

import com.lowagie.text.*;
import com.lowagie.text.Font;
import com.lowagie.text.pdf.*;
import com.mutuelle.mobille.dto.bilan.MemberExerciceBilanDTO;
import com.mutuelle.mobille.dto.bilan.MemberSessionBilanDTO;
import com.mutuelle.mobille.dto.exercice.ExerciceHistoryDto;
import com.mutuelle.mobille.dto.sessionHistory.SessionHistoryResponseDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
@RequiredArgsConstructor
public class BilanPdfService {

    private final BilanService bilanService;

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final DateTimeFormatter DATE_SHORT = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final Color COLOR_HEADER    = new Color(30, 64, 110);
    private static final Color COLOR_SECTION   = new Color(52, 100, 160);
    private static final Color COLOR_ROW_ALT   = new Color(240, 245, 252);
    private static final Color COLOR_TOTAL_BG  = new Color(220, 235, 250);
    private static final Color COLOR_POSITIVE  = new Color(22, 120, 60);
    private static final Color COLOR_NEGATIVE  = new Color(180, 30, 30);

    // ─────────────────────────────────────────────────────────────────────────
    //  BILAN MEMBRE PAR SESSION
    // ─────────────────────────────────────────────────────────────────────────

    public byte[] generateMemberSessionBilanPdf(Long memberId, Long sessionId) {
        MemberSessionBilanDTO dto = bilanService.getMemberSessionBilan(memberId, sessionId);
        return buildMemberSessionPdf(dto);
    }

    public byte[] buildMemberSessionPdf(MemberSessionBilanDTO dto) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        Document doc = new Document(PageSize.A4, 40, 40, 60, 50);
        try {
            PdfWriter writer = PdfWriter.getInstance(doc, baos);
            addPageDecorator(writer);
            doc.open();

            addHeader(doc, "BILAN FINANCIER - MEMBRE / SESSION");
            addMemberInfoTable(doc, dto.getMemberFirstname(), dto.getMemberLastname(),
                    dto.getSessionName(), dto.getExerciceName(),
                    dto.getSessionStartDate(), dto.getSessionEndDate());

            addSectionTitle(doc, "VERSEMENTS DU MEMBRE");
            PdfPTable versements = buildTwoColumnTable();
            addRow(versements, "Cotisation solidarité",  dto.getSolidaritePaid(),   false, false);
            addRow(versements, "Épargne déposée",        dto.getEpargneDeposited(), false, false);
            addRow(versements, "Frais d'inscription",    dto.getRegistrationPaid(), false, false);
            addRow(versements, "Renfoulement payé",      dto.getRenfoulementPaid(), false, false);
            addRow(versements, "Remboursement emprunt",  dto.getRemboursementAmount(), false, false);
            addTotalRow(versements, "TOTAL VERSÉ",       dto.getTotalVerse());
            doc.add(versements);
            doc.add(Chunk.NEWLINE);

            addSectionTitle(doc, "DÉCAISSEMENTS / DETTES");
            PdfPTable decaissements = buildTwoColumnTable();
            addRow(decaissements, "Épargne retirée",    dto.getEpargneWithdrawn(),   false, true);
            addRow(decaissements, "Emprunt contracté",  dto.getEmpruntAmount(),      false, true);
            addRow(decaissements, "Intérêts calculés",  dto.getInteretAmount(),      false, true);
            addRow(decaissements, "Assistance reçue",   dto.getAssistanceReceived(), false, true);
            addRow(decaissements, "agape session",  dto.getAgapeShare(),        false, true);
            addTotalRow(decaissements, "TOTAL REÇU / DÛ", dto.getTotalRecu());
            doc.add(decaissements);
            doc.add(Chunk.NEWLINE);

            addSectionTitle(doc, "RÉCAPITULATIF DE LA SESSION");
            PdfPTable recap = buildTwoColumnTable();
            addRow(recap, "Total versé",    dto.getTotalVerse(), false, false);
            addRow(recap, "Total reçu/dû",  dto.getTotalRecu(),  false, true);
            addNetRow(recap, "SOLDE NET SESSION", dto.getNetSession());
            doc.add(recap);
            doc.add(Chunk.NEWLINE);

            addSectionTitle(doc, "SITUATION DU COMPTE À LA CLÔTURE");
            PdfPTable snapshot = buildTwoColumnTable();
            addRow(snapshot, "Épargne",                 dto.getSnapshotSavingAmount(),      false, false);
            addRow(snapshot, "Emprunt en cours",        dto.getSnapshotBorrowAmount(),       false, true);
            addRow(snapshot, "Solidarité impayée",      dto.getSnapshotUnpaidSolidarity(),   false, true);
            addRow(snapshot, "Inscription impayée",     dto.getSnapshotUnpaidRegistration(), false, true);
            addRow(snapshot, "Renfoulement impayé",     dto.getSnapshotUnpaidRenfoulement(), false, true);
            doc.add(snapshot);

            addFooter(doc);
        } catch (DocumentException e) {
            throw new RuntimeException("Erreur génération PDF bilan session membre", e);
        } finally {
            doc.close();
        }
        return baos.toByteArray();
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  BILAN MEMBRE PAR EXERCICE
    // ─────────────────────────────────────────────────────────────────────────

    public byte[] generateMemberExerciceBilanPdf(Long memberId, Long exerciceId) {
        MemberExerciceBilanDTO dto = bilanService.getMemberExerciceBilan(memberId, exerciceId);
        return buildMemberExercicePdf(dto);
    }

    public byte[] buildMemberExercicePdf(MemberExerciceBilanDTO dto) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        Document doc = new Document(PageSize.A4, 40, 40, 60, 50);
        try {
            PdfWriter writer = PdfWriter.getInstance(doc, baos);
            addPageDecorator(writer);
            doc.open();

            addHeader(doc, "BILAN FINANCIER - MEMBRE / EXERCICE");
            addExerciceInfoTable(doc, dto.getMemberFirstname(), dto.getMemberLastname(),
                    dto.getExerciceName(), dto.getExerciceStartDate(), dto.getExerciceEndDate(),
                    dto.getSessionsCount());

            addSectionTitle(doc, "VERSEMENTS CUMULÉS SUR L'EXERCICE");
            PdfPTable versements = buildTwoColumnTable();
            addRow(versements, "Cotisation solidarité (total)",  dto.getTotalSolidaritePaid(),    false, false);
            addRow(versements, "Épargne déposée (total)",        dto.getTotalEpargneDeposited(),  false, false);
            addRow(versements, "Frais d'inscription (total)",    dto.getTotalRegistrationPaid(),  false, false);
            addRow(versements, "Renfoulement payé (total)",      dto.getTotalRenfoulementPaid(),  false, false);
            addRow(versements, "Remboursements emprunt (total)", dto.getTotalRemboursementAmount(), false, false);
            addTotalRow(versements, "TOTAL VERSÉ", dto.getTotalVerse());
            doc.add(versements);
            doc.add(Chunk.NEWLINE);

            addSectionTitle(doc, "DÉCAISSEMENTS / DETTES CUMULÉS");
            PdfPTable decaissements = buildTwoColumnTable();
            addRow(decaissements, "Épargne retirée (total)",       dto.getTotalEpargneWithdrawn(),   false, true);
            addRow(decaissements, "Emprunts contractés (total)",   dto.getTotalEmpruntAmount(),       false, true);
            addRow(decaissements, "Intérêts calculés (total)",     dto.getTotalInteretAmount(),       false, true);
            addRow(decaissements, "Assistances reçues (total)",    dto.getTotalAssistanceReceived(),  false, true);
            addRow(decaissements, "Parts agape (total sessions)",  dto.getTotalAgapeShare(),          false, true);
            addRow(decaissements, "Renfoulement distribué",        dto.getRenfoulementDistributed(),  false, true);
            addTotalRow(decaissements, "TOTAL REÇU / DÛ", dto.getTotalRecu());
            doc.add(decaissements);
            doc.add(Chunk.NEWLINE);

            addSectionTitle(doc, "RÉCAPITULATIF DE L'EXERCICE");
            PdfPTable recap = buildTwoColumnTable();
            addRow(recap, "Total versé",    dto.getTotalVerse(), false, false);
            addRow(recap, "Total reçu/dû",  dto.getTotalRecu(),  false, true);
            addNetRow(recap, "SOLDE NET EXERCICE", dto.getNetExercice());
            doc.add(recap);
            doc.add(Chunk.NEWLINE);

            addSectionTitle(doc, "SITUATION DU COMPTE À LA CLÔTURE");
            PdfPTable snapshot = buildTwoColumnTable();
            addRow(snapshot, "Épargne",                 dto.getSnapshotSavingAmount(),      false, false);
            addRow(snapshot, "Emprunt en cours",        dto.getSnapshotBorrowAmount(),       false, true);
            addRow(snapshot, "Solidarité impayée",      dto.getSnapshotUnpaidSolidarity(),   false, true);
            addRow(snapshot, "Inscription impayée",     dto.getSnapshotUnpaidRegistration(), false, true);
            addRow(snapshot, "Renfoulement impayé",     dto.getSnapshotUnpaidRenfoulement(), false, true);
            doc.add(snapshot);

            addFooter(doc);
        } catch (DocumentException e) {
            throw new RuntimeException("Erreur génération PDF bilan exercice membre", e);
        } finally {
            doc.close();
        }
        return baos.toByteArray();
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  BILAN MUTUELLE PAR SESSION
    // ─────────────────────────────────────────────────────────────────────────

    public byte[] generateMutuelleSessionBilanPdf(Long sessionId) {
        SessionHistoryResponseDTO dto = bilanService.getMutuelleSessionBilan(sessionId);
        return buildMutuelleSessionPdf(dto);
    }

    public byte[] buildMutuelleSessionPdf(SessionHistoryResponseDTO dto) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        Document doc = new Document(PageSize.A4, 40, 40, 60, 50);
        try {
            PdfWriter writer = PdfWriter.getInstance(doc, baos);
            addPageDecorator(writer);
            doc.open();

            addHeader(doc, "BILAN FINANCIER - MUTUELLE / SESSION");
            addSessionInfoTable(doc, dto.getSessionName(), dto.getExerciceName(),
                    dto.getSessionStartDate(), dto.getSessionEndDate(), dto.getActiveMembersCount());

            addSectionTitle(doc, "FLUX COLLECTÉS DURANT LA SESSION");
            PdfPTable collectes = buildTwoColumnTable();
            addRow(collectes, "Solidarité collectée",      dto.getTotalSolidarityCollected(), false, false);
            addRow(collectes, "Épargne déposée",           dto.getTotalEpargneDeposited(),    false, false);
            addRow(collectes, "Frais d'inscription",       dto.getTotalRegistrationCollected(), false, false);
            addRow(collectes, "Renfoulement collecté",     dto.getTotalRenfoulementCollected(), false, false);
            addRow(collectes, "Remboursements emprunt",    dto.getTotalRemboursementAmount(),  false, false);
            doc.add(collectes);
            doc.add(Chunk.NEWLINE);

            addSectionTitle(doc, "FLUX DÉCAISSÉS DURANT LA SESSION");
            PdfPTable decaisses = buildTwoColumnTable();
            addRow(decaisses, "Assistances Faits",    dto.getTotalAssistanceAmount(), false, true);
            addRow(decaisses, "Agapes débitées",        dto.getAgapeAmount(),           false, true);
            addRow(decaisses, "Emprunts accordés",      dto.getTotalEmpruntAmount(),    false, true);
            addRow(decaisses, "Épargne retirée",        dto.getTotalEpargneWithdrawn(), false, true);
            doc.add(decaisses);
            doc.add(Chunk.NEWLINE);

            addSectionTitle(doc, "INTÉRÊTS & ACTIVITÉ");
            PdfPTable activite = buildTwoColumnTable();
            addRow(activite, "Intérêts calculés",       dto.getTotalInteretAmount(),    false, false);
            addRow(activite, "Nombre assistances",          BigDecimal.valueOf(dto.getTotalAssistanceCount()), false, false, "");
            addRow(activite, "Nombre cotisations solidarité", BigDecimal.valueOf(dto.getTotalSolidarityCount()), false, false, "");
            addRow(activite, "Nombre total transactions",   BigDecimal.valueOf(dto.getTotalTransactions()), false, false, "");
            addRow(activite, "Membres actifs",          BigDecimal.valueOf(dto.getActiveMembersCount()), false, false, "");
            doc.add(activite);
            doc.add(Chunk.NEWLINE);

            addSectionTitle(doc, "TRÉSORERIE MUTUELLE À LA CLÔTURE");
            PdfPTable tresorerie = buildTwoColumnTable();
            addRow(tresorerie, "Épargne globale",            dto.getMutuellesSavingAmount(),     false, false);
            addRow(tresorerie, "Fonds solidarité",           dto.getMutuelleSolidarityAmount(),  false, false);
            addRow(tresorerie, "Caisse inscription",         dto.getMutuelleRegistrationAmount(), false, false);
            addRow(tresorerie, "Emprunts en cours (total)",  dto.getMutuelleBorrowAmount(),       false, true);
            addTotalRow(tresorerie, "TRÉSORERIE TOTALE", dto.getMutuelleCash());
            doc.add(tresorerie);

            addFooter(doc);
        } catch (DocumentException e) {
            throw new RuntimeException("Erreur génération PDF bilan session mutuelle", e);
        } finally {
            doc.close();
        }
        return baos.toByteArray();
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  BILAN MUTUELLE PAR EXERCICE
    // ─────────────────────────────────────────────────────────────────────────

    public byte[] generateMutuelleExerciceBilanPdf(Long exerciceId) {
        ExerciceHistoryDto dto = bilanService.getMutuelleExerciceBilan(exerciceId);
        return buildMutuelleExercicePdf(dto);
    }

    public byte[] buildMutuelleExercicePdf(ExerciceHistoryDto dto) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        Document doc = new Document(PageSize.A4, 40, 40, 60, 50);
        try {
            PdfWriter writer = PdfWriter.getInstance(doc, baos);
            addPageDecorator(writer);
            doc.open();

            addHeader(doc, "BILAN FINANCIER - MUTUELLE / EXERCICE");
            addExerciceGlobalInfoTable(doc, dto.getExerciceName(), dto.getExerciceStartDate(),
                    dto.getExerciceEndDate(), dto.getSessionsCount(), dto.getActiveMembersCount());

            addSectionTitle(doc, "FLUX COLLECTÉS SUR L'EXERCICE");
            PdfPTable collectes = buildTwoColumnTable();
            addRow(collectes, "Solidarité collectée (total)",   dto.getTotalSolidarityCollected(),  false, false);
            addRow(collectes, "Épargne déposée (total)",        dto.getTotalEpargneDeposited(),      false, false);
            addRow(collectes, "Frais d'inscription (total)",    dto.getTotalRegistrationCollected(), false, false);
            addRow(collectes, "Renfoulement collecté (total)",  dto.getTotalRenfoulementCollected(), false, false);
            addRow(collectes, "Remboursements emprunt (total)", dto.getTotalRemboursementAmount(),   false, false);
            doc.add(collectes);
            doc.add(Chunk.NEWLINE);

            addSectionTitle(doc, "FLUX DÉCAISSÉS SUR L'EXERCICE");
            PdfPTable decaisses = buildTwoColumnTable();
            addRow(decaisses, "Assistances Faits (total)", dto.getTotalAssistanceAmount(), false, true);
            addRow(decaisses, "Agapes débitées (total)",    dto.getTotalAgapeAmount(),       false, true);
            addRow(decaisses, "Emprunts accordés (total)",  dto.getTotalEmpruntAmount(),     false, true);
            addRow(decaisses, "Épargne retirée (total)",    dto.getTotalEpargneWithdrawn(),  false, true);
            doc.add(decaisses);
            doc.add(Chunk.NEWLINE);

            addSectionTitle(doc, "RENFOULEMENT DE L'EXERCICE");
            PdfPTable renfoulement = buildTwoColumnTable();
            addRow(renfoulement, "Total distribué",         dto.getTotalRenfoulementDistributed(), false, false);
            addRow(renfoulement, "Montant unitaire/membre", dto.getRenfoulementUnitAmount(),       false, false);
            addRow(renfoulement, "Total collecté",          dto.getTotalRenfoulementCollected(),   false, false);
            doc.add(renfoulement);
            doc.add(Chunk.NEWLINE);

            addSectionTitle(doc, "INTÉRÊTS & ACTIVITÉ");
            PdfPTable activite = buildTwoColumnTable();
            addRow(activite, "Intérêts calculés (total)",  dto.getTotalInteretAmount(),    false, false);
            addRow(activite, "Nombre assistances (total)",     BigDecimal.valueOf(dto.getTotalAssistanceCount()), false, false, "");
            addRow(activite, "Nombre total transactions",      BigDecimal.valueOf(dto.getTotalTransactions()),    false, false, "");
            addRow(activite, "Nombre sessions",                BigDecimal.valueOf(dto.getSessionsCount()),        false, false, "");
            addRow(activite, "Membres actifs",             BigDecimal.valueOf(dto.getActiveMembersCount()),   false, false);
            doc.add(activite);
            doc.add(Chunk.NEWLINE);

            addSectionTitle(doc, "TRÉSORERIE MUTUELLE À LA CLÔTURE");
            PdfPTable tresorerie = buildTwoColumnTable();
            addRow(tresorerie, "Épargne globale",            dto.getMutuellesSavingAmount(),     false, false);
            addRow(tresorerie, "Fonds solidarité",           dto.getMutuelleSolidarityAmount(),  false, false);
            addRow(tresorerie, "Caisse inscription",         dto.getMutuelleRegistrationAmount(), false, false);
            addRow(tresorerie, "Emprunts en cours (total)",  dto.getMutuelleBorrowAmount(),       false, true);
            addTotalRow(tresorerie, "TRÉSORERIE TOTALE", dto.getMutuelleCash());
            doc.add(tresorerie);

            addFooter(doc);
        } catch (DocumentException e) {
            throw new RuntimeException("Erreur génération PDF bilan exercice mutuelle", e);
        } finally {
            doc.close();
        }
        return baos.toByteArray();
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  PDF SYNTHÈSE - TOUS LES MEMBRES D'UN EXERCICE
    // ─────────────────────────────────────────────────────────────────────────

    public byte[] generateAllMembersExercicePdf(Long exerciceId) {
        List<MemberExerciceBilanDTO> bilans = bilanService.getAllMemberBilansByExercice(exerciceId);
        if (bilans.isEmpty()) throw new RuntimeException("Aucun bilan disponible pour cet exercice");

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        Document doc = new Document(PageSize.A4.rotate(), 30, 30, 50, 40);
        try {
            PdfWriter writer = PdfWriter.getInstance(doc, baos);
            addPageDecorator(writer);
            doc.open();

            MemberExerciceBilanDTO first = bilans.get(0);
            addHeader(doc, "SYNTHÈSE MEMBRES - EXERCICE : " + first.getExerciceName());

            Font headerFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8, Color.WHITE);
            Font cellFont   = FontFactory.getFont(FontFactory.HELVETICA, 7.5f, Color.BLACK);
            Font totalFont  = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 7.5f, Color.BLACK);

            PdfPTable table = new PdfPTable(new float[]{2.5f, 1f, 1f, 1f, 1f, 1f, 1f, 1f, 1f, 1f, 1.2f});
            table.setWidthPercentage(100);

            String[] headers = {"Membre", "Solidarité", "Épargne (net)", "Inscription", "Renfoul. payé",
                    "Remboursement", "Emprunt", "Intérêts", "Assistance", "Agape", "Solde Net"};
            for (String h : headers) {
                PdfPCell cell = new PdfPCell(new Phrase(h, headerFont));
                cell.setBackgroundColor(COLOR_HEADER);
                cell.setPadding(5);
                cell.setHorizontalAlignment(Element.ALIGN_CENTER);
                table.addCell(cell);
            }

            boolean alt = false;
            for (MemberExerciceBilanDTO b : bilans) {
                Color rowColor = alt ? COLOR_ROW_ALT : Color.WHITE;
                alt = !alt;
                BigDecimal epargneNet = b.getTotalEpargneDeposited().subtract(b.getTotalEpargneWithdrawn());

                addSummaryCell(table, b.getMemberLastname() + " " + b.getMemberFirstname(), cellFont, rowColor, Element.ALIGN_LEFT);
                addSummaryCell(table, fmt(b.getTotalSolidaritePaid()),    cellFont, rowColor, Element.ALIGN_RIGHT);
                addSummaryCell(table, fmt(epargneNet),                    cellFont, rowColor, Element.ALIGN_RIGHT);
                addSummaryCell(table, fmt(b.getTotalRegistrationPaid()),  cellFont, rowColor, Element.ALIGN_RIGHT);
                addSummaryCell(table, fmt(b.getTotalRenfoulementPaid()),  cellFont, rowColor, Element.ALIGN_RIGHT);
                addSummaryCell(table, fmt(b.getTotalRemboursementAmount()), cellFont, rowColor, Element.ALIGN_RIGHT);
                addSummaryCell(table, fmt(b.getTotalEmpruntAmount()),     cellFont, rowColor, Element.ALIGN_RIGHT);
                addSummaryCell(table, fmt(b.getTotalInteretAmount()),     cellFont, rowColor, Element.ALIGN_RIGHT);
                addSummaryCell(table, fmt(b.getTotalAssistanceReceived()), cellFont, rowColor, Element.ALIGN_RIGHT);
                addSummaryCell(table, fmt(b.getTotalAgapeShare()),        cellFont, rowColor, Element.ALIGN_RIGHT);
                BigDecimal net = b.getNetExercice();
                Font netFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 7.5f,
                        net.compareTo(BigDecimal.ZERO) >= 0 ? COLOR_POSITIVE : COLOR_NEGATIVE);
                addSummaryCell(table, fmt(net), netFont, rowColor, Element.ALIGN_RIGHT);
            }
            doc.add(table);
            addFooter(doc);
        } catch (DocumentException e) {
            throw new RuntimeException("Erreur génération PDF synthèse membres", e);
        } finally {
            doc.close();
        }
        return baos.toByteArray();
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Helpers de construction PDF
    // ─────────────────────────────────────────────────────────────────────────

    private void addHeader(Document doc, String title) throws DocumentException {
        Font titleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 16, Color.WHITE);
        Font subFont   = FontFactory.getFont(FontFactory.HELVETICA, 9, new Color(180, 200, 230));

        PdfPTable headerTable = new PdfPTable(1);
        headerTable.setWidthPercentage(100);
        PdfPCell headerCell = new PdfPCell();
        headerCell.setBackgroundColor(COLOR_HEADER);
        headerCell.setPadding(12);
        headerCell.setBorder(Rectangle.NO_BORDER);
        headerCell.addElement(new Phrase("MUTUELLE MOBILE", subFont));
        headerCell.addElement(new Phrase(title, titleFont));
        headerCell.addElement(new Phrase("Généré le : " + LocalDateTime.now().format(DATE_FMT), subFont));
        headerTable.addCell(headerCell);
        doc.add(headerTable);
        doc.add(Chunk.NEWLINE);
    }

    private void addMemberInfoTable(Document doc, String firstname, String lastname,
                                     String sessionName, String exerciceName,
                                     LocalDateTime start, LocalDateTime end) throws DocumentException {
        Font labelFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9, COLOR_SECTION);
        Font valueFont = FontFactory.getFont(FontFactory.HELVETICA, 9, Color.BLACK);

        PdfPTable table = new PdfPTable(new float[]{1.5f, 3f, 1.5f, 3f});
        table.setWidthPercentage(100);
        addInfoRow(table, "Membre :", lastname + " " + firstname, "Session :", sessionName, labelFont, valueFont);
        addInfoRow(table, "Exercice :", exerciceName, "Période :", fmt(start) + " → " + fmt(end), labelFont, valueFont);
        doc.add(table);
        doc.add(Chunk.NEWLINE);
    }

    private void addExerciceInfoTable(Document doc, String firstname, String lastname,
                                       String exerciceName, LocalDateTime start, LocalDateTime end,
                                       Integer sessionsCount) throws DocumentException {
        Font labelFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9, COLOR_SECTION);
        Font valueFont = FontFactory.getFont(FontFactory.HELVETICA, 9, Color.BLACK);

        PdfPTable table = new PdfPTable(new float[]{1.5f, 3f, 1.5f, 3f});
        table.setWidthPercentage(100);
        addInfoRow(table, "Membre :", lastname + " " + firstname, "Exercice :", exerciceName, labelFont, valueFont);
        addInfoRow(table, "Période :", fmt(start) + " → " + fmt(end), "Sessions :", sessionsCount + " session(s)", labelFont, valueFont);
        doc.add(table);
        doc.add(Chunk.NEWLINE);
    }

    private void addSessionInfoTable(Document doc, String sessionName, String exerciceName,
                                      LocalDateTime start, LocalDateTime end, Long members) throws DocumentException {
        Font labelFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9, COLOR_SECTION);
        Font valueFont = FontFactory.getFont(FontFactory.HELVETICA, 9, Color.BLACK);

        PdfPTable table = new PdfPTable(new float[]{1.5f, 3f, 1.5f, 3f});
        table.setWidthPercentage(100);
        addInfoRow(table, "Session :", sessionName, "Exercice :", exerciceName, labelFont, valueFont);
        addInfoRow(table, "Période :", fmt(start) + " → " + fmt(end), "Membres actifs :", members + " membre(s)", labelFont, valueFont);
        doc.add(table);
        doc.add(Chunk.NEWLINE);
    }

    private void addExerciceGlobalInfoTable(Document doc, String exerciceName, LocalDateTime start,
                                             LocalDateTime end, Integer sessions, Long members) throws DocumentException {
        Font labelFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9, COLOR_SECTION);
        Font valueFont = FontFactory.getFont(FontFactory.HELVETICA, 9, Color.BLACK);

        PdfPTable table = new PdfPTable(new float[]{1.5f, 3f, 1.5f, 3f});
        table.setWidthPercentage(100);
        addInfoRow(table, "Exercice :", exerciceName, "Période :", fmt(start) + " → " + fmt(end), labelFont, valueFont);
        addInfoRow(table, "Sessions :", sessions + " session(s)", "Membres actifs :", members + " membre(s)", labelFont, valueFont);
        doc.add(table);
        doc.add(Chunk.NEWLINE);
    }

    private void addInfoRow(PdfPTable table, String l1, String v1, String l2, String v2, Font lf, Font vf) {
        addInfoCell(table, l1, lf);
        addInfoCell(table, v1, vf);
        addInfoCell(table, l2, lf);
        addInfoCell(table, v2, vf);
    }

    private void addInfoCell(PdfPTable table, String text, Font font) {
        PdfPCell cell = new PdfPCell(new Phrase(text, font));
        cell.setBorder(Rectangle.BOTTOM);
        cell.setBorderColor(new Color(200, 215, 235));
        cell.setPadding(5);
        table.addCell(cell);
    }

    private void addSectionTitle(Document doc, String title) throws DocumentException {
        Font f = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10, Color.WHITE);
        PdfPTable t = new PdfPTable(1);
        t.setWidthPercentage(100);
        PdfPCell cell = new PdfPCell(new Phrase("  " + title, f));
        cell.setBackgroundColor(COLOR_SECTION);
        cell.setPadding(5);
        cell.setBorder(Rectangle.NO_BORDER);
        t.addCell(cell);
        doc.add(t);
    }

    private PdfPTable buildTwoColumnTable() throws DocumentException {
        PdfPTable table = new PdfPTable(new float[]{4f, 2f});
        table.setWidthPercentage(100);
        return table;
    }

    private void addRow(PdfPTable table, String label, BigDecimal value, boolean isHeader, boolean debit) {
        addRow(table, label, value, isHeader, debit, "Fcfa");
    }

    private void addRow(PdfPTable table, String label, BigDecimal value, boolean isHeader, boolean debit, String unit ) {
        Font labelFont = FontFactory.getFont(FontFactory.HELVETICA, 9, Color.BLACK);
        Color amountColor = debit ? COLOR_NEGATIVE : COLOR_POSITIVE;
        Font amountFont = FontFactory.getFont(FontFactory.HELVETICA, 9, amountColor);

        PdfPCell labelCell = new PdfPCell(new Phrase(label, labelFont));
        labelCell.setBorder(Rectangle.BOTTOM);
        labelCell.setBorderColor(new Color(220, 230, 245));
        labelCell.setPadding(5);
        table.addCell(labelCell);

        PdfPCell valueCell = new PdfPCell(new Phrase(fmt(value) + " "+unit, amountFont));
        valueCell.setBorder(Rectangle.BOTTOM);
        valueCell.setBorderColor(new Color(220, 230, 245));
        valueCell.setPadding(5);
        valueCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
        table.addCell(valueCell);
    }

    private void addTotalRow(PdfPTable table, String label, BigDecimal value) {
        Font f = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9.5f, Color.BLACK);

        PdfPCell labelCell = new PdfPCell(new Phrase(label, f));
        labelCell.setBackgroundColor(COLOR_TOTAL_BG);
        labelCell.setPadding(6);
        table.addCell(labelCell);

        PdfPCell valueCell = new PdfPCell(new Phrase(fmt(value) + " Fcfa", f));
        valueCell.setBackgroundColor(COLOR_TOTAL_BG);
        valueCell.setPadding(6);
        valueCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
        table.addCell(valueCell);
    }

    private void addNetRow(PdfPTable table, String label, BigDecimal value) {
        boolean positive = value.compareTo(BigDecimal.ZERO) >= 0;
        Color bg  = positive ? new Color(210, 240, 220) : new Color(250, 215, 215);
        Color fg  = positive ? COLOR_POSITIVE : COLOR_NEGATIVE;
        Font  f   = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10, fg);

        PdfPCell labelCell = new PdfPCell(new Phrase(label, f));
        labelCell.setBackgroundColor(bg);
        labelCell.setPadding(7);
        table.addCell(labelCell);

        PdfPCell valueCell = new PdfPCell(new Phrase(fmt(value) + " Fcfa", f));
        valueCell.setBackgroundColor(bg);
        valueCell.setPadding(7);
        valueCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
        table.addCell(valueCell);
    }

    private void addSummaryCell(PdfPTable table, String text, Font font, Color bg, int align) {
        PdfPCell cell = new PdfPCell(new Phrase(text, font));
        cell.setBackgroundColor(bg);
        cell.setPadding(4);
        cell.setHorizontalAlignment(align);
        cell.setBorderColor(new Color(200, 215, 235));
        table.addCell(cell);
    }

    private void addFooter(Document doc) throws DocumentException {
        doc.add(Chunk.NEWLINE);
        Font f = FontFactory.getFont(FontFactory.HELVETICA_OBLIQUE, 7.5f, new Color(120, 130, 150));
        Paragraph footer = new Paragraph("Document généré automatiquement par le système Mutuelle Mobile. " +
                "Ce document est un état financier officiel.", f);
        footer.setAlignment(Element.ALIGN_CENTER);
        doc.add(footer);
    }

    private void addPageDecorator(PdfWriter writer) {
        writer.setPageEvent(new PdfPageEventHelper() {
            @Override
            public void onEndPage(PdfWriter w, Document d) {
                PdfContentByte cb = w.getDirectContent();
                Font f = FontFactory.getFont(FontFactory.HELVETICA, 7, new Color(150, 160, 175));
                ColumnText.showTextAligned(cb, Element.ALIGN_CENTER,
                        new Phrase("Page " + w.getPageNumber(), f),
                        (d.right() - d.left()) / 2 + d.leftMargin(), d.bottom() - 15, 0);
            }
        });
    }

    private String fmt(BigDecimal value) {
        if (value == null) return "0";
        return String.format("%,.0f", value);
    }

    private String fmt(LocalDateTime dt) {
        if (dt == null) return "—";
        return dt.format(DATE_SHORT);
    }
}
