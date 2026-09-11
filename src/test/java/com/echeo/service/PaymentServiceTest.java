package com.echeo.service;

import com.echeo.model.entity.EventMemberStatus;
import com.echeo.model.entity.Group;
import com.echeo.model.entity.GroupEvent;
import com.echeo.model.entity.GroupMember;
import com.echeo.model.entity.User;
import com.echeo.model.enums.PaymentMethod;
import com.echeo.model.enums.PaymentStatus;
import com.echeo.repository.EventMemberStatusRepository;
import com.echeo.repository.NotificationLogRepository;
import com.echeo.repository.PaymentHistoryRepository;
import com.echeo.repository.PaymentTokenRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * Vérifie la règle métier centrale d'ÉCHÉO : le calcul du statut d'une
 * échéance après un versement (PARTIALLY_PAID / PAID / SURPLUS).
 * Les repositories sont mockés — aucune base de données réelle nécessaire,
 * ces tests s'exécutent en quelques millisecondes.
 */
@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    @Mock
    private EventMemberStatusRepository eventMemberStatusRepository;
    @Mock
    private PaymentHistoryRepository paymentHistoryRepository;
    @Mock
    private PaymentTokenRepository paymentTokenRepository;
    @Mock
    private NotificationLogRepository notificationLogRepository;
    @Mock
    private EmailService emailService;

    @InjectMocks
    private PaymentService paymentService;

    private EventMemberStatus status;

    @BeforeEach
    void setUp() {
        User owner = new User();
        owner.setId(1L);
        owner.setEmail("proprietaire@exemple.com");
        owner.setFullName("Propriétaire Test");

        Group group = new Group();
        group.setId(1L);
        group.setOwner(owner);
        group.setNotifyOwnerOnReminders(false); // évite l'envoi d'email dans ces tests

        GroupEvent event = new GroupEvent();
        event.setId(1L);
        event.setGroup(group);
        event.setTitle("Cotisation test");

        GroupMember member = new GroupMember();
        member.setId(1L);
        member.setExternalFullName("Awa Ngono");
        member.setExternalEmail("awa@exemple.com");

        status = new EventMemberStatus();
        status.setId(1L);
        status.setEvent(event);
        status.setGroupMember(member);
        status.setRequiredAmount(new BigDecimal("10000"));
        status.setPaidAmount(BigDecimal.ZERO);
        status.setStatus(PaymentStatus.PENDING);

        lenient().when(eventMemberStatusRepository.findById(1L)).thenReturn(Optional.of(status));
        lenient().when(eventMemberStatusRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        lenient().when(paymentHistoryRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        lenient().when(notificationLogRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void recordPayment_montantInferieurAuRequis_donneStatutPartiallyPaid() {
        EventMemberStatus result = paymentService.recordPayment(1L, new BigDecimal("4000"), PaymentMethod.CASH, null);

        assertThat(result.getStatus()).isEqualTo(PaymentStatus.PARTIALLY_PAID);
        assertThat(result.getPaidAmount()).isEqualByComparingTo("4000");
    }

    @Test
    void recordPayment_montantExact_donneStatutPaid() {
        EventMemberStatus result = paymentService.recordPayment(1L, new BigDecimal("10000"), PaymentMethod.MOBILE_MONEY, null);

        assertThat(result.getStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(result.getPaidAmount()).isEqualByComparingTo("10000");
    }

    @Test
    void recordPayment_montantSuperieur_donneStatutSurplus() {
        EventMemberStatus result = paymentService.recordPayment(1L, new BigDecimal("15000"), PaymentMethod.CARD, null);

        assertThat(result.getStatus()).isEqualTo(PaymentStatus.SURPLUS);
        assertThat(result.getPaidAmount()).isEqualByComparingTo("15000");
    }

    @Test
    void recordPayment_deuxVersementsPartiels_cumuleJusquauStatutPaid() {
        paymentService.recordPayment(1L, new BigDecimal("6000"), PaymentMethod.CASH, null);
        EventMemberStatus result = paymentService.recordPayment(1L, new BigDecimal("4000"), PaymentMethod.CASH, null);

        assertThat(result.getStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(result.getPaidAmount()).isEqualByComparingTo("10000");
    }

    @Test
    void recordPayment_montantNegatifOuNul_estRejete() {
        org.junit.jupiter.api.Assertions.assertThrows(
                com.echeo.exception.InvalidArgumentException.class,
                () -> paymentService.recordPayment(1L, BigDecimal.ZERO, PaymentMethod.CASH, null)
        );
    }
}
