package com.echeo.service;

import com.echeo.dto.AddGroupMemberRequest;
import com.echeo.dto.GroupEventRequest;
import com.echeo.dto.GroupEventUpdateRequest;
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

    public GroupService(GroupRepository groupRepository,
                         GroupMemberRepository groupMemberRepository,
                         GroupEventRepository groupEventRepository,
                         EventMemberStatusRepository eventMemberStatusRepository,
                         UserRepository userRepository) {
        this.groupRepository = groupRepository;
        this.groupMemberRepository = groupMemberRepository;
        this.groupEventRepository = groupEventRepository;
        this.eventMemberStatusRepository = eventMemberStatusRepository;
        this.userRepository = userRepository;
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

    public Group getGroup(Long groupId) {
        return groupRepository.findById(groupId)
                .orElseThrow(() -> new EntityNotFoundException("Group", groupId));
    }

    /**
     * Tous les groupes visibles pour l'utilisateur connecté : ceux qu'il
     * possède et ceux dont il est membre INSCRIT (un membre externe sans
     * compte n'a évidemment pas de session pour appeler cet endpoint).
     */
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

    public GroupEvent getEvent(Long groupId, Long eventId) {
        GroupEvent event = groupEventRepository.findById(eventId)
                .orElseThrow(() -> new EntityNotFoundException("GroupEvent", eventId));
        if (!event.getGroup().getId().equals(groupId)) {
            throw new EntityNotFoundException("GroupEvent", eventId);
        }
        return event;
    }

    public List<EventMemberStatus> listMemberStatuses(Long eventId) {
        if (!groupEventRepository.existsById(eventId)) {
            throw new EntityNotFoundException("GroupEvent", eventId);
        }
        return eventMemberStatusRepository.findByEvent_IdAndStatusIn(eventId,
                List.of(PaymentStatus.PENDING, PaymentStatus.PARTIALLY_PAID, PaymentStatus.PAID,
                        PaymentStatus.SURPLUS, PaymentStatus.OVERDUE));
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
        event.setRepetitionType(request.getRepetitionType() != null ? request.getRepetitionType() : RepetitionType.NONE);
        event = groupEventRepository.save(event);

        Map<Long, BigDecimal> customAmounts = request.getRequiredAmountsByGroupMemberId();
        BigDecimal equalShare = request.getTargetAmount()
                .divide(BigDecimal.valueOf(request.getGroupMemberIds().size()), 2, RoundingMode.HALF_UP);

        for (Long groupMemberId : request.getGroupMemberIds()) {
            GroupMember groupMember = groupMemberRepository.findById(groupMemberId)
                    .orElseThrow(() -> new EntityNotFoundException("GroupMember", groupMemberId));

            if (!groupMember.getGroup().getId().equals(groupId)) {
                throw new InvalidArgumentException(
                        "Le membre " + groupMemberId + " n'appartient pas à ce groupe.");
            }

            BigDecimal required = (customAmounts != null && customAmounts.containsKey(groupMemberId))
                    ? customAmounts.get(groupMemberId)
                    : equalShare;

            EventMemberStatus status = new EventMemberStatus();
            status.setEvent(event);
            status.setGroupMember(groupMember);
            status.setRequiredAmount(required);
            status.setPaidAmount(BigDecimal.ZERO);
            status.setStatus(PaymentStatus.PENDING);
            eventMemberStatusRepository.save(status);
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

        event.setTitle(request.getTitle());
        event.setDescription(request.getDescription());
        event.setTargetAmount(request.getTargetAmount());
        event.setEventDate(request.getEventDate());
        event.setRepetitionType(request.getRepetitionType());
        return groupEventRepository.save(event);
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
        event.setPaused(false);
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
     * Passe en OVERDUE tout statut encore PENDING/PARTIALLY_PAID dont
     * l'événement est déjà passé. Les relances en rafale (NotificationSchedulerService)
     * s'appuient ensuite sur ce statut.
     */
    @Transactional
    public void markOverdueStatuses() {
        List<GroupEvent> pastEvents = groupEventRepository.findByEventDateBefore(LocalDate.now());
        for (GroupEvent event : pastEvents) {
            List<EventMemberStatus> unpaid = eventMemberStatusRepository.findByEvent_IdAndStatusIn(
                    event.getId(), List.of(PaymentStatus.PENDING, PaymentStatus.PARTIALLY_PAID));
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
        List<GroupEvent> due = groupEventRepository
                .findByRepetitionTypeNotAndPausedFalseAndNextOccurrenceIsNullAndEventDateLessThanEqual(
                        RepetitionType.NONE, LocalDate.now());

        for (GroupEvent event : due) {
            LocalDate nextDate = computeNextOccurrenceDate(event.getEventDate(), event.getRepetitionType());
            if (nextDate == null) {
                continue;
            }

            event.setNextOccurrence(nextDate);
            groupEventRepository.save(event);

            GroupEvent next = new GroupEvent();
            next.setGroup(event.getGroup());
            next.setTitle(event.getTitle());
            next.setDescription(event.getDescription());
            next.setTargetAmount(event.getTargetAmount());
            next.setEventDate(nextDate);
            next.setRepetitionType(event.getRepetitionType());
            next = groupEventRepository.save(next);

            List<EventMemberStatus> previousStatuses = eventMemberStatusRepository.findByEvent_Id(event.getId());

            for (EventMemberStatus previous : previousStatuses) {
                BigDecimal baseRequired = previous.getRequiredAmount();
                // Positif si surplus, négatif si encore dû.
                BigDecimal carry = previous.getPaidAmount().subtract(previous.getRequiredAmount());
                BigDecimal newRequired = baseRequired.subtract(carry);
                if (newRequired.compareTo(BigDecimal.ZERO) < 0) {
                    newRequired = BigDecimal.ZERO;
                }

                EventMemberStatus newStatus = new EventMemberStatus();
                newStatus.setEvent(next);
                newStatus.setGroupMember(previous.getGroupMember());
                newStatus.setRequiredAmount(newRequired);
                newStatus.setPaidAmount(BigDecimal.ZERO);
                newStatus.setStatus(newRequired.compareTo(BigDecimal.ZERO) == 0
                        ? PaymentStatus.PAID
                        : PaymentStatus.PENDING);
                eventMemberStatusRepository.save(newStatus);
            }

            log.info("Occurrence suivante générée pour l'événement récurrent {} -> nouvel événement {} ({})",
                    event.getId(), next.getId(), nextDate);
        }
    }

    private LocalDate computeNextOccurrenceDate(LocalDate currentDate, RepetitionType repetitionType) {
        return switch (repetitionType) {
            case DAILY -> currentDate.plusDays(1);
            case WEEKLY -> currentDate.plusWeeks(1);
            case MONTHLY -> currentDate.plusMonths(1);
            case YEARLY -> currentDate.plusYears(1);
            case NONE -> null;
        };
    }
}
