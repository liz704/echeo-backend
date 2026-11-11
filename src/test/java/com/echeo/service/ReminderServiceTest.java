package com.echeo.service;

import com.echeo.exception.InvalidArgumentException;
import com.echeo.model.entity.PersonalReminder;
import com.echeo.model.entity.User;
import com.echeo.model.enums.RepetitionType;
import com.echeo.repository.PersonalReminderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Vérifie que markAsCompleted() calcule correctement la prochaine occurrence
 * pour chaque type de récurrence, et qu'un rappel non récurrent (NONE)
 * n'en génère aucune.
 */
@ExtendWith(MockitoExtension.class)
class ReminderServiceTest {

    @Mock
    private PersonalReminderRepository reminderRepository;

    private ReminderService reminderService;

    @BeforeEach
    void setUp() {
        reminderService = new ReminderService(reminderRepository);
        lenient().when(reminderRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @ParameterizedTest
    @CsvSource({
            "DAILY, 2026-01-01, 2026-01-02",
            "WEEKLY, 2026-01-01, 2026-01-08",
            "MONTHLY, 2026-01-01, 2026-02-01",
            "YEARLY, 2026-01-01, 2027-01-01"
    })
    void markAsCompleted_genereLaBonneProchaineOccurrence(RepetitionType type, String dueDate, String expectedNext) {
        PersonalReminder reminder = buildReminder(1L, LocalDate.parse(dueDate), type);
        when(reminderRepository.findById(1L)).thenReturn(Optional.of(reminder));

        reminderService.markAsCompleted(1L);

        ArgumentCaptor<PersonalReminder> captor = ArgumentCaptor.forClass(PersonalReminder.class);
        verify(reminderRepository, times(2)).save(captor.capture());

        List<PersonalReminder> saved = captor.getAllValues();
        PersonalReminder completedOne = saved.stream().filter(PersonalReminder::isCompleted).findFirst().orElseThrow();
        PersonalReminder nextOne = saved.stream().filter(r -> !r.isCompleted()).findFirst().orElseThrow();

        assertThat(completedOne.getNextOccurrence()).isEqualTo(LocalDate.parse(expectedNext));
        assertThat(nextOne.getDueDate()).isEqualTo(LocalDate.parse(expectedNext));
        assertThat(nextOne.isCompleted()).isFalse();
    }

    @Test
    void markAsCompleted_repetitionNone_neGenereAucuneProchaineOccurrence() {
        PersonalReminder reminder = buildReminder(2L, LocalDate.of(2026, 1, 1), RepetitionType.NONE);
        when(reminderRepository.findById(2L)).thenReturn(Optional.of(reminder));

        PersonalReminder result = reminderService.markAsCompleted(2L);

        assertThat(result.isCompleted()).isTrue();
        assertThat(result.getNextOccurrence()).isNull();
        verify(reminderRepository, times(1)).save(any());
    }

    @Test
    void pauseReminder_marksTheReminderAsPaused() {
        PersonalReminder reminder = buildReminder(3L, LocalDate.of(2026, 1, 1), RepetitionType.DAILY);
        when(reminderRepository.findById(3L)).thenReturn(Optional.of(reminder));

        PersonalReminder result = reminderService.pauseReminder(3L);

        assertThat(result.isPaused()).isTrue();
    }

    @Test
    void pauseReminder_refusesACompletedReminder() {
        PersonalReminder reminder = buildReminder(4L, LocalDate.of(2026, 1, 1), RepetitionType.NONE);
        reminder.setCompleted(true);
        when(reminderRepository.findById(4L)).thenReturn(Optional.of(reminder));

        assertThatThrownBy(() -> reminderService.pauseReminder(4L)).isInstanceOf(InvalidArgumentException.class);
    }

    @Test
    void resumeReminder_recurring_jumpsToTheNextFutureOccurrence() {
        PersonalReminder reminder = buildReminder(5L, LocalDate.of(2026, 1, 1), RepetitionType.WEEKLY);
        reminder.setPaused(true);
        when(reminderRepository.findById(5L)).thenReturn(Optional.of(reminder));

        PersonalReminder result = reminderService.resumeReminder(5L, LocalDate.of(2026, 1, 20));

        assertThat(result.isPaused()).isFalse();
        assertThat(result.getDueDate()).isEqualTo(LocalDate.of(2026, 1, 22));
    }

    @Test
    void resumeReminder_nonRecurring_keepsItsDueDate() {
        PersonalReminder reminder = buildReminder(6L, LocalDate.of(2026, 1, 1), RepetitionType.NONE);
        reminder.setPaused(true);
        when(reminderRepository.findById(6L)).thenReturn(Optional.of(reminder));

        PersonalReminder result = reminderService.resumeReminder(6L, LocalDate.of(2026, 1, 20));

        assertThat(result.isPaused()).isFalse();
        assertThat(result.getDueDate()).isEqualTo(LocalDate.of(2026, 1, 1));
    }

    @Test
    void rollForwardOverdueRecurring_ignoresPausedReminders() {
        PersonalReminder reminder = buildReminder(7L, LocalDate.of(2026, 1, 1), RepetitionType.DAILY);
        reminder.setPaused(true);
        when(reminderRepository.findAll()).thenReturn(List.of(reminder));

        int rolled = reminderService.rollForwardOverdueRecurring(
                LocalDate.of(2026, 1, 10), LocalDateTime.of(2026, 1, 10, 12, 0));

        assertThat(rolled).isZero();
        verify(reminderRepository, never()).save(any());
    }

    private PersonalReminder buildReminder(Long id, LocalDate dueDate, RepetitionType type) {
        User user = new User();
        user.setId(1L);

        PersonalReminder reminder = new PersonalReminder();
        reminder.setId(id);
        reminder.setUser(user);
        reminder.setTitle("Test");
        reminder.setDueDate(dueDate);
        reminder.setRepetitionType(type);
        reminder.setCompleted(false);
        return reminder;
    }
}
