package com.echeo.service;

import com.echeo.dto.AddGroupMemberRequest;
import com.echeo.dto.GroupEventRequest;
import com.echeo.dto.GroupEventUpdateRequest;
import com.echeo.dto.GroupHistoryResponse;
import com.echeo.dto.EventMemberDetailItem;
import com.echeo.dto.EventDetailResponse;
import com.echeo.dto.GroupPaymentHistoryItem;
import com.echeo.model.entity.PaymentHistory;
import com.echeo.repository.PaymentHistoryRepository;
import com.echeo.exception.EntityNotFoundException;
import com.echeo.exception.InvalidArgumentException;
import com.echeo.model.entity.EventMemberStatus;
import com.echeo.model.entity.Group;
import com.echeo.model.entity.GroupEvent;
import com.echeo.model.entity.GroupMember;
import com.echeo.model.entity.User;
import com.echeo.model.enums.PaymentStatus;
import com.echeo.model.enums.RepetitionType;
import com.echeo.repository.EventMemberStatusRepository;
import com.echeo.repository.GroupEventRepository;
import com.echeo.repository.GroupMemberRepository;
import com.echeo.repository.GroupRepository;
import com.echeo.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Logique métier des groupes : création, adhésion des membres (inscrits ou
 * externes — voir GroupMember), création d'événements de cotisation avec
 * répartition automatique du montant cible entre les membres désignés
 * (création en cascade des EventMemberStatus), et cycle de vie des
 * cotisations récurrentes (génération d'occurrence, report de solde,
 * passage en retard).
 */
@Service
public class GroupService {

    private static final Logger log = LoggerFactory.getLogger(GroupService.class);

    private final GroupRepository groupRepository;
    private final GroupMemberRepository groupMemberRepository;
    private final GroupEventRepository groupEventRepository;
    private final EventMemberStatusRepository eventMemberStatusRepository;
    private final UserRepository userRepository;
    private final PaymentHistoryRepository paymentHistoryRepository;

    public GroupService(GroupRepository groupRepository,
                         GroupMemberRepository groupMemberRepository,
                         GroupEventRepository groupEventRepository,
                         EventMemberStatusRepository eventMemberStatusRepository,
                         UserRepository userRepository,
                         PaymentHistoryRepository paymentHistoryRepository) {
        this.groupRepository = groupRepository;
        this.groupMemberRepository = groupMemberRepository;
        this.groupEventRepository = groupEventRepository;
        this.eventMemberStatusRepository = eventMemberStatusRepository;
        this.userRepository = userRepository;
        this.paymentHistoryRepository = paymentHistoryRepository;
    }

    @Transactional
    public Group createGroup(String name, String description, Long ownerId) {
        User owner = userRepository.findById(ownerId)
                .orElseThrow(() -> new EntityNotFoundException("User", ownerId));

        Group group = new Group();
        group.setName(name);
        group.setDescription(description);
        group.setOwner(owner);
        return groupRepository.save(group);
    }

    @Transactional(readOnly = true)
    public Group getGroup(Long groupId) {
        return groupRepository.findByIdWithOwner(groupId)
                .orElseThrow(() -> new EntityNotFoundException("Group", groupId));
    }

    /**
     * Tous les groupes visibles pour l'utilisateur connecté : ceux qu'il
     * possède et ceux dont il est membre INSCRIT (un membre externe sans
     * compte n'a évidemment pas de session pour appeler cet endpoint).
     */
    @Transactional(readOnly = true)
    public List<Group> listMyGroups(Long userId) {
        Set<Group> groups = new LinkedHashSet<>(groupRepository.findByOwner_Id(userId));
        groupMemberRepository.findByUser_Id(userId).forEach(m -> groups.add(m.getGroup()));
        return List.copyOf(groups);
    }

    public List<GroupMember> listMembers(Long groupId) {
        getGroup(groupId);
        return groupMemberRepository.findByGroup_Id(groupId);
    }

    /**
     * Modifie le nom/la description d'un groupe. Seul le propriétaire peut le faire.
     */
    @Transactional
    public Group updateGroupInfo(Long groupId, String name, String description, Long requesterId) {
        Group group = getGroup(groupId);
        requireOwner(group, requesterId);
        group.setName(name);
        group.setDescription(description);
        return groupRepository.save(group);
    }

    /**
     * Supprime un groupe entier. Les membres, événements, statuts de paiement
     * et jetons associés sont supprimés en cascade au niveau base de données
     * (contraintes ON DELETE CASCADE — voir V1__init_schema.sql).
     */
    @Transactional
    public void deleteGroup(Long groupId, Long requesterId) {
        Group group = getGroup(groupId);
        requireOwner(group, requesterId);
        groupRepository.delete(group);
    }

    /**
     * Retire un membre du groupe. Supprime d'abord ses statuts de paiement
     * (par sécurité, au cas où la cascade DB ne couvrirait pas ce chemin),
     * puis le membre lui-même.
     */
    @Transactional
    public void removeMember(Long groupId, Long memberId, Long requesterId) {
        Group group = getGroup(groupId);
        requireOwner(group, requesterId);
        GroupMember member = groupMemberRepository.findById(memberId)
                .orElseThrow(() -> new EntityNotFoundException("GroupMember", memberId));
        if (!member.getGroup().getId().equals(groupId)) {
            throw new EntityNotFoundException("GroupMember", memberId);
        }
        eventMemberStatusRepository.deleteByGroupMember_Id(memberId);
        groupMemberRepository.delete(member);
    }

    /**
     * Supprime un événement de groupe (et ses statuts de paiement associés,
     * en cascade). Ne touche pas aux autres événements du groupe.
     */
    @Transactional
    public void deleteEvent(Long groupId, Long eventId, Long requesterId) {
        Group group = getGroup(groupId);
        requireOwner(group, requesterId);
        GroupEvent event = getEvent(groupId, eventId);
        groupEventRepository.delete(event);
    }

    private void requireOwner(Group group, Long requesterId) {
        if (!group.getOwner().getId().equals(requesterId)) {
            throw new InvalidArgumentException("Seul le propriétaire du groupe peut effectuer cette action.");
        }
    }

    /**
     * Marque un rappel de groupe sans argent comme "vu", via le jeton public
     * reçu par email (voir PublicAcknowledgmentController). Idempotent : si
     * déjà vu, ne fait que renvoyer l'état actuel sans le modifier — ça évite
     * qu'un clic répété sur le lien change la date de "vu" à chaque fois.
     */
    @Transactional
    public EventMemberStatus acknowledgeByToken(java.util.UUID token) {
        EventMemberStatus status = eventMemberStatusRepository.findByPublicAckToken(token)
                .orElseThrow(() -> new EntityNotFoundException("Lien invalide ou expiré."));

        if (status.getStatus() == PaymentStatus.NOT_SEEN || status.getStatus() == PaymentStatus.OVERDUE) {
            status.setStatus(PaymentStatus.SEEN);
            status.setSeenAt(java.time.OffsetDateTime.now());
            eventMemberStatusRepository.save(status);
        }
        return status;
    }

    /**
     * Ajoute un membre à un groupe. Deux cas, distingués par le contenu de
     * la requête (voir AddGroupMemberRequest) :
     * - userId renseigné → membre inscrit, lié à son compte ÉCHÉO existant.
     * - fullName + email renseignés → membre externe, sans compte. C'est le
     *   cas normal : un membre n'a jamais besoin de s'inscrire pour recevoir
     *   des rappels de paiement par email.
     */
    @Transactional
    public GroupMember addMember(Long groupId, AddGroupMemberRequest request) {
        Group group = getGroup(groupId);

        boolean hasUserId = request.getUserId() != null;
        boolean hasExternalIdentity = request.getFullName() != null && !request.getFullName().isBlank()
                && request.getEmail() != null && !request.getEmail().isBlank();

        if (hasUserId == hasExternalIdentity) {
            throw new InvalidArgumentException(
                    "Fournis soit un identifiant utilisateur (membre inscrit), soit un nom et un email (membre externe) — pas les deux, pas aucun des deux.");
        }

        GroupMember member = new GroupMember();
        member.setGroup(group);

        if (hasUserId) {
            User user = userRepository.findById(request.getUserId())
                    .orElseThrow(() -> new EntityNotFoundException("User", request.getUserId()));
            if (groupMemberRepository.existsByGroup_IdAndUser_Id(groupId, request.getUserId())) {
                throw new InvalidArgumentException("Cet utilisateur est déjà membre du groupe.");
            }
            member.setUser(user);
        } else {
            if (groupMemberRepository.existsByGroup_IdAndExternalEmail(groupId, request.getEmail())) {
                throw new InvalidArgumentException("Un membre avec cet email fait déjà partie du groupe.");
            }
            member.setExternalFullName(request.getFullName());
            member.setExternalEmail(request.getEmail());
            member.setExternalPhone(request.getPhone());
        }

        return groupMemberRepository.save(member);
    }

    public List<GroupEvent> listEvents(Long groupId) {
        getGroup(groupId);
        return groupEventRepository.findByGroup_IdOrderByEventDateAsc(groupId);
    }

    /**
     * Historique du groupe : événements dont la date est passée, plus tous
     * les versements enregistrés (du plus récent au plus ancien).
     */
    @Transactional(readOnly = true)
    public GroupHistoryResponse getGroupHistory(Long groupId) {
        getGroup(groupId);
        LocalDate today = LocalDate.now();
        List<GroupEvent> pastEvents =
                groupEventRepository.findByGroup_IdAndEventDateBeforeOrderByEventDateDesc(groupId, today);

        List<PaymentHistory> rawPayments =
                paymentHistoryRepository.findByGroupIdOrderByPaidAtDesc(groupId);
        List<GroupPaymentHistoryItem> payments = rawPayments.stream().map(h -> {
            GroupPaymentHistoryItem item = new GroupPaymentHistoryItem();
            item.setId(h.getId());
            item.setAmountPaid(h.getAmountPaid());
            item.setPaymentMethod(h.getPaymentMethod());
            item.setTransactionRef(h.getTransactionRef());
            item.setPaidAt(h.getPaidAt());
            if (h.getEventMemberStatus() != null) {
                item.setEventMemberStatusId(h.getEventMemberStatus().getId());
                if (h.getEventMemberStatus().getEvent() != null) {
                    item.setEventId(h.getEventMemberStatus().getEvent().getId());
                    item.setEventTitle(h.getEventMemberStatus().getEvent().getTitle());
                }
                if (h.getEventMemberStatus().getGroupMember() != null) {
                    item.setMemberFullName(h.getEventMemberStatus().getGroupMember().getContactFullName());
                }
            }
            return item;
        }).toList();

        return new GroupHistoryResponse(pastEvents, payments);
    }

    /**
     * Détail complet d'un événement : infos + suivi indépendant de chaque
     * membre (statut courant, montants, accusé de lecture, historique des
     * versements un par un).
     */
    @Transactional(readOnly = true)
    public EventDetailResponse getEventDetail(Long groupId, Long eventId) {
        GroupEvent event = getEvent(groupId, eventId);
        List<EventMemberStatus> statuses =
                eventMemberStatusRepository.findByEvent_IdWithDetails(eventId);

        // Indexe les paiements par status id pour les rattacher à chaque membre.
        List<PaymentHistory> allPayments =
                paymentHistoryRepository.findByEventIdOrderByPaidAtDesc(eventId);
        java.util.Map<Long, List<PaymentHistory>> paymentsByStatus = new java.util.HashMap<>();
        for (PaymentHistory h : allPayments) {
            if (h.getEventMemberStatus() == null) continue;
            paymentsByStatus
                    .computeIfAbsent(h.getEventMemberStatus().getId(), k -> new java.util.ArrayList<>())
                    .add(h);
        }

        EventDetailResponse detail = new EventDetailResponse();
        detail.setEventId(event.getId());
        detail.setGroupId(groupId);
        detail.setTitle(event.getTitle());
        detail.setDescription(event.getDescription());
        detail.setTargetAmount(event.getTargetAmount());
        detail.setWithdrawalFeeAmount(event.getWithdrawalFeeAmount());
        detail.setEventDate(event.getEventDate());
        detail.setEventTime(event.getEventTime());
        detail.setRepetitionType(event.getRepetitionType());
        detail.setPaused(event.isPaused());
        detail.setHasMoney(event.hasMoney());

        List<EventMemberDetailItem> members = new java.util.ArrayList<>();
        for (EventMemberStatus s : statuses) {
            EventMemberDetailItem item = new EventMemberDetailItem();
            item.setEventMemberStatusId(s.getId());
            if (s.getGroupMember() != null) {
                item.setGroupMemberId(s.getGroupMember().getId());
                item.setMemberFullName(s.getGroupMember().getContactFullName());
                item.setMemberEmail(s.getGroupMember().getContactEmail());
            }
            item.setStatus(s.getStatus());
            item.setRequiredAmount(s.getRequiredAmount());
            item.setPaidAmount(s.getPaidAmount());
            item.setSeenAt(s.getSeenAt());

            List<PaymentHistory> memberPayments =
                    paymentsByStatus.getOrDefault(s.getId(), List.of());
            List<EventMemberDetailItem.PaymentEntry> entries = new java.util.ArrayList<>();
            for (PaymentHistory h : memberPayments) {
                EventMemberDetailItem.PaymentEntry e = new EventMemberDetailItem.PaymentEntry();
                e.setId(h.getId());
                e.setAmountPaid(h.getAmountPaid());
                e.setPaymentMethod(h.getPaymentMethod());
                e.setTransactionRef(h.getTransactionRef());
                e.setPaidAt(h.getPaidAt());
                entries.add(e);
            }
            item.setPayments(entries);
            members.add(item);
        }
        detail.setMembers(members);
        return detail;
    }

    @Transactional(readOnly = true)
    public List<GroupEvent> listUpcomingEvents(Long groupId) {
        getGroup(groupId);
        return groupEventRepository.findByGroup_IdAndEventDateGreaterThanEqualOrderByEventDateAsc(
                groupId, LocalDate.now());
    }

    @Transactional(readOnly = true)
    public List<GroupEvent> listPastEvents(Long groupId) {
        getGroup(groupId);
        return groupEventRepository.findByGroup_IdAndEventDateBeforeOrderByEventDateDesc(
                groupId, LocalDate.now());
    }

    @Transactional(readOnly = true)
    public GroupEvent getEvent(Long groupId, Long eventId) {
        GroupEvent event = groupEventRepository.findByIdWithGroup(eventId)
                .orElseThrow(() -> new EntityNotFoundException("GroupEvent", eventId));
        if (!event.getGroup().getId().equals(groupId)) {
            throw new EntityNotFoundException("GroupEvent", eventId);
        }
        return event;
    }

    @Transactional(readOnly = true)
    public List<EventMemberStatus> listMemberStatuses(Long eventId) {
        if (!groupEventRepository.existsById(eventId)) {
            throw new EntityNotFoundException("GroupEvent", eventId);
        }
        // Tous les statuts : avec argent (PENDING…SURPLUS) ET sans argent
        // (NOT_SEEN / SEEN). Filtrer uniquement les "impayés" rendait les
        // événements sans argent invisibles côté UI.
        return eventMemberStatusRepository.findByEvent_IdWithDetails(eventId);
    }

    /**
     * Crée un événement de groupe et génère automatiquement un EventMemberStatus
     * pour chaque GroupMember listé dans la requête (l'événement peut concerner
     * un seul membre, plusieurs, ou tout le groupe — au choix de l'appelant).
     * Le montant requis par membre est :
     * - celui fourni dans requiredAmountsByGroupMemberId si présent pour ce membre,
     * - sinon targetAmount réparti également entre les membres listés.
     */
    @Transactional
    public GroupEvent createEvent(Long groupId, GroupEventRequest request) {
        Group group = getGroup(groupId);

        if (request.getGroupMemberIds() == null || request.getGroupMemberIds().isEmpty()) {
            throw new InvalidArgumentException("Au moins un membre doit être associé à l'événement.");
        }

        GroupEvent event = new GroupEvent();
        event.setGroup(group);
        event.setTitle(request.getTitle());
        event.setDescription(request.getDescription());
        event.setTargetAmount(request.getTargetAmount());
        event.setEventDate(request.getEventDate());
        event.setEventTime(request.getEventTime());
        event.setWithdrawalFeeAmount(request.getWithdrawalFeeAmount());
        event.setRepetitionType(request.getRepetitionType() != null ? request.getRepetitionType() : RepetitionType.NONE);
        event = groupEventRepository.save(event);

        Map<Long, BigDecimal> customAmounts = request.getRequiredAmountsByGroupMemberId();
        boolean hasMoney = event.hasMoney();
        List<Long> memberIds = request.getGroupMemberIds();
        int memberCount = memberIds.size();

        // Pré-calcule les parts égales avec reste d'arrondi sur le dernier
        // membre (uniquement si pas de montants personnalisés).
        boolean useEqualShare = hasMoney && (customAmounts == null || customAmounts.isEmpty());
        BigDecimal equalShare = null;
        BigDecimal remainderForLast = null;
        if (useEqualShare) {
            equalShare = request.getTargetAmount().divide(BigDecimal.valueOf(memberCount), 2, RoundingMode.HALF_UP);
            BigDecimal assigned = equalShare.multiply(BigDecimal.valueOf(memberCount - 1));
            remainderForLast = request.getTargetAmount().subtract(assigned);
        }

        int index = 0;
        for (Long groupMemberId : memberIds) {
            GroupMember groupMember = groupMemberRepository.findById(groupMemberId)
                    .orElseThrow(() -> new EntityNotFoundException("GroupMember", groupMemberId));

            if (!groupMember.getGroup().getId().equals(groupId)) {
                throw new InvalidArgumentException(
                        "Le membre " + groupMemberId + " n'appartient pas à ce groupe.");
            }

            EventMemberStatus status = new EventMemberStatus();
            status.setEvent(event);
            status.setGroupMember(groupMember);

            if (hasMoney) {
                BigDecimal required;
                if (customAmounts != null && customAmounts.containsKey(groupMemberId)) {
                    required = customAmounts.get(groupMemberId);
                } else if (useEqualShare && index == memberCount - 1) {
                    required = remainderForLast;
                } else {
                    required = equalShare;
                }
                status.setRequiredAmount(required);
                status.setPaidAmount(BigDecimal.ZERO);
                status.setStatus(PaymentStatus.PENDING);
            } else {
                // Événement sans argent : suivi par accusé de lecture.
                // Un jeton public est généré pour permettre à un membre
                // externe (sans compte) de marquer le rappel comme vu.
                status.setStatus(PaymentStatus.NOT_SEEN);
                status.setPublicAckToken(java.util.UUID.randomUUID());
            }

            eventMemberStatusRepository.save(status);
            index++;
        }

        return event;
    }

    /**
     * Modifie un événement qui n'a pas encore eu lieu (event_date future).
     * Ne touche pas aux membres ni aux montants déjà en cours de paiement —
     * seulement aux champs descriptifs et à la planification.
     */
    @Transactional
    public GroupEvent updateUpcomingEvent(Long groupId, Long eventId, GroupEventUpdateRequest request) {
        GroupEvent event = getEvent(groupId, eventId);

        if (!event.getEventDate().isAfter(LocalDate.now())) {
            throw new InvalidArgumentException(
                    "Cet événement a déjà eu lieu ou a lieu aujourd'hui — seul un événement à venir peut être modifié.");
        }

        boolean wasWithMoney = event.hasMoney();
        boolean staysWithMoney = request.getTargetAmount() != null;
        if (wasWithMoney != staysWithMoney) {
            throw new InvalidArgumentException(
                    "Impossible de changer un événement avec argent en événement sans argent (ou l'inverse) : "
                            + "les statuts déjà générés pour les membres ne correspondraient plus. "
                            + "Crée un nouvel événement à la place.");
        }

        BigDecimal previousTarget = event.getTargetAmount();

        event.setTitle(request.getTitle());
        event.setDescription(request.getDescription());
        event.setTargetAmount(request.getTargetAmount());
        event.setEventDate(request.getEventDate());
        event.setEventTime(request.getEventTime());
        event.setWithdrawalFeeAmount(request.getWithdrawalFeeAmount());
        if (request.getRepetitionType() != null) {
            event.setRepetitionType(request.getRepetitionType());
        }
        event = groupEventRepository.save(event);

        // Si le montant cible change et qu'aucun paiement n'a encore été
        // enregistré, on répartit à nouveau equitably entre les membres.
        // Dès qu'un membre a payé quelque chose, on ne touche plus aux
        // required_amount (évite de casser un suivi déjà en cours).
        if (staysWithMoney
                && previousTarget != null
                && request.getTargetAmount() != null
                && previousTarget.compareTo(request.getTargetAmount()) != 0) {
            redistributeRequiredAmountsIfNoPayments(event);
        }

        return event;
    }

    /**
     * Répartit targetAmount de façon égale entre les membres de l'événement,
     * uniquement si personne n'a encore payé (paid_amount == 0 pour tous).
     * Le reste d'arrondi est attribué au dernier membre pour que la somme
     * des required_amount soit exactement égale au montant cible.
     */
    private void redistributeRequiredAmountsIfNoPayments(GroupEvent event) {
        List<EventMemberStatus> statuses = eventMemberStatusRepository.findByEvent_Id(event.getId());
        if (statuses.isEmpty()) {
            return;
        }
        boolean anyPayment = statuses.stream()
                .anyMatch(s -> s.getPaidAmount() != null && s.getPaidAmount().compareTo(BigDecimal.ZERO) > 0);
        if (anyPayment) {
            log.info("Montant cible de l'événement {} modifié mais des paiements existent déjà — required_amount non redistribués.",
                    event.getId());
            return;
        }

        BigDecimal target = event.getTargetAmount();
        int n = statuses.size();
        BigDecimal equalShare = target.divide(BigDecimal.valueOf(n), 2, RoundingMode.HALF_UP);
        BigDecimal assigned = BigDecimal.ZERO;

        for (int i = 0; i < n; i++) {
            EventMemberStatus status = statuses.get(i);
            BigDecimal required;
            if (i == n - 1) {
                // Dernier membre : prend le reste pour absorber l'arrondi.
                required = target.subtract(assigned);
            } else {
                required = equalShare;
                assigned = assigned.add(equalShare);
            }
            status.setRequiredAmount(required);
            status.setStatus(required.compareTo(BigDecimal.ZERO) == 0
                    ? PaymentStatus.PAID
                    : PaymentStatus.PENDING);
            eventMemberStatusRepository.save(status);
        }
    }

    /**
     * Suspend la reconduction automatique d'un événement récurrent, sans le
     * supprimer ni toucher aux occurrences déjà générées.
     */
    @Transactional
    public GroupEvent pauseEvent(Long groupId, Long eventId) {
        GroupEvent event = getEvent(groupId, eventId);
        if (event.getRepetitionType() == RepetitionType.NONE) {
            throw new InvalidArgumentException("Cet événement n'est pas récurrent, rien à mettre en pause.");
        }
        event.setPaused(true);
        return groupEventRepository.save(event);
    }

    /**
     * Reprend la reconduction automatique d'un événement récurrent mis en
     * pause. La prochaine occurrence sera générée au prochain passage du job
     * quotidien, calculée à partir de la dernière échéance connue.
     */
    @Transactional
    public GroupEvent resumeEvent(Long groupId, Long eventId) {
        GroupEvent event = getEvent(groupId, eventId);
        if (event.getRepetitionType() == RepetitionType.NONE) {
            throw new InvalidArgumentException("Cet événement n'est pas récurrent, rien à reprendre.");
        }
        event.setPaused(false);
        // Si l'échéance est déjà passée et qu'aucune occurrence n'a encore
        // été générée (nextOccurrence null), le prochain job quotidien
        // rattrapera automatiquement via generateRecurringOccurrences.
        return groupEventRepository.save(event);
    }

    // ========================================================================
    // Cycle de vie automatique (job quotidien) : passage en retard, puis
    // génération des occurrences récurrentes dues.
    // ========================================================================

    @Scheduled(cron = "0 0 7 * * *")
    public void runDailyGroupEventLifecycle() {
        markOverdueStatuses();
        generateRecurringOccurrences();
    }

    /**
     * Passe en OVERDUE tout statut encore PENDING/PARTIALLY_PAID (avec argent)
     * ou NOT_SEEN (sans argent, jamais vu) dont l'événement est déjà passé.
     * Les relances en rafale (NotificationSchedulerService) s'appuient
     * ensuite sur ce statut.
     */
    @Transactional
    public void markOverdueStatuses() {
        List<GroupEvent> pastEvents = groupEventRepository.findByEventDateBefore(LocalDate.now());
        for (GroupEvent event : pastEvents) {
            List<EventMemberStatus> unpaid = eventMemberStatusRepository.findByEvent_IdAndStatusIn(
                    event.getId(), List.of(PaymentStatus.PENDING, PaymentStatus.PARTIALLY_PAID, PaymentStatus.NOT_SEEN));
            for (EventMemberStatus status : unpaid) {
                status.setStatus(PaymentStatus.OVERDUE);
                eventMemberStatusRepository.save(status);
            }
        }
    }

    /**
     * Génère l'occurrence suivante de chaque événement récurrent dont
     * l'échéance est atteinte, non en pause, et pas encore reconduit.
     * Le solde de chaque membre est reporté intelligemment :
     * - s'il restait dû (impayé/partiel), la dette s'ajoute au montant requis
     *   de la nouvelle occurrence ;
     * - s'il y avait un surplus, il vient en déduction du montant requis
     *   de la nouvelle occurrence (jamais en dessous de zéro).
     */
    @Transactional
    public void generateRecurringOccurrences() {
        LocalDate today = LocalDate.now();
        List<GroupEvent> due = groupEventRepository
                .findByRepetitionTypeNotAndPausedFalseAndNextOccurrenceIsNullAndEventDateLessThanEqual(
                        RepetitionType.NONE, today);

        for (GroupEvent seed : due) {
            // Rattrapage : si plusieurs échéances ont été manquées (app arrêtée,
            // etc.), on enchaîne les occurrences jusqu'à dépasser aujourd'hui,
            // avec report de solde à chaque pas. Garde-fou pour éviter une
            // boucle infinie en cas de données incohérentes.
            GroupEvent current = seed;
            int safety = 0;
            final int maxCatchUp = 400; // ~1 an de DAILY

            while (safety++ < maxCatchUp
                    && !current.isPaused()
                    && current.getRepetitionType() != RepetitionType.NONE
                    && current.getNextOccurrence() == null
                    && !current.getEventDate().isAfter(today)) {

                LocalDate nextDate = computeNextOccurrenceDate(current.getEventDate(), current.getRepetitionType());
                if (nextDate == null) {
                    break;
                }

                current.setNextOccurrence(nextDate);
                groupEventRepository.save(current);

                GroupEvent next = buildNextOccurrenceEvent(current, nextDate);
                next = groupEventRepository.save(next);
                copyMemberStatusesToNextOccurrence(current, next);

                log.info("Occurrence suivante générée pour l'événement récurrent {} -> nouvel événement {} ({})",
                        current.getId(), next.getId(), nextDate);

                // La nouvelle occurrence devient le "courant" pour un éventuel
                // rattrapage supplémentaire si sa date est encore passée.
                current = next;
            }

            if (safety >= maxCatchUp) {
                log.warn("Rattrapage de récurrence stoppé (plafond {}) pour l'événement seed {}",
                        maxCatchUp, seed.getId());
            }
        }
    }

    private GroupEvent buildNextOccurrenceEvent(GroupEvent source, LocalDate nextDate) {
        GroupEvent next = new GroupEvent();
        next.setGroup(source.getGroup());
        next.setTitle(source.getTitle());
        next.setDescription(source.getDescription());
        next.setTargetAmount(source.getTargetAmount());
        next.setEventDate(nextDate);
        next.setEventTime(source.getEventTime());
        next.setWithdrawalFeeAmount(source.getWithdrawalFeeAmount());
        next.setRepetitionType(source.getRepetitionType());
        next.setPaused(false);
        // nextOccurrence reste null : l'occurrence suivante sera générée
        // quand cette date sera atteinte (et non en pause).
        return next;
    }

    /**
     * Recopie les membres de l'occurrence précédente vers la suivante, avec
     * report intelligent du solde (avec argent) ou reset de l'accusé de
     * lecture (sans argent).
     */
    private void copyMemberStatusesToNextOccurrence(GroupEvent previousEvent, GroupEvent nextEvent) {
        List<EventMemberStatus> previousStatuses =
                eventMemberStatusRepository.findByEvent_Id(previousEvent.getId());
        boolean hasMoney = previousEvent.hasMoney();

        for (EventMemberStatus previous : previousStatuses) {
            EventMemberStatus newStatus = new EventMemberStatus();
            newStatus.setEvent(nextEvent);
            newStatus.setGroupMember(previous.getGroupMember());

            if (hasMoney) {
                BigDecimal baseRequired = previous.getRequiredAmount() != null
                        ? previous.getRequiredAmount() : BigDecimal.ZERO;
                BigDecimal paid = previous.getPaidAmount() != null
                        ? previous.getPaidAmount() : BigDecimal.ZERO;
                // Positif si surplus, négatif si encore dû.
                BigDecimal carry = paid.subtract(baseRequired);
                BigDecimal newRequired = baseRequired.subtract(carry);
                if (newRequired.compareTo(BigDecimal.ZERO) < 0) {
                    newRequired = BigDecimal.ZERO;
                }
                newStatus.setRequiredAmount(newRequired);
                newStatus.setPaidAmount(BigDecimal.ZERO);
                newStatus.setStatus(newRequired.compareTo(BigDecimal.ZERO) == 0
                        ? PaymentStatus.PAID
                        : PaymentStatus.PENDING);
            } else {
                // Sans argent : chaque occurrence repart avec un accusé
                // de lecture neuf et son propre jeton public.
                newStatus.setStatus(PaymentStatus.NOT_SEEN);
                newStatus.setPublicAckToken(java.util.UUID.randomUUID());
            }

            eventMemberStatusRepository.save(newStatus);
        }
    }

    private LocalDate computeNextOccurrenceDate(LocalDate currentDate, RepetitionType repetitionType) {
        if (currentDate == null || repetitionType == null) {
            return null;
        }
        return switch (repetitionType) {
            case DAILY -> currentDate.plusDays(1);
            case WEEKLY -> currentDate.plusWeeks(1);
            case MONTHLY -> currentDate.plusMonths(1);
            case YEARLY -> currentDate.plusYears(1);
            case NONE -> null;
        };
    }
}
