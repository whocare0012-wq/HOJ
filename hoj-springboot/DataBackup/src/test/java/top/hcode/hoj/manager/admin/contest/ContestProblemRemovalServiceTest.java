package top.hcode.hoj.manager.admin.contest;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import top.hcode.hoj.common.exception.StatusFailException;
import top.hcode.hoj.dao.contest.*;
import top.hcode.hoj.dao.judge.JudgeEntityService;
import top.hcode.hoj.pojo.entity.contest.*;
import java.util.Date;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.any;

@ExtendWith(MockitoExtension.class)
class ContestProblemRemovalServiceTest {
    @InjectMocks ContestProblemRemovalService service;
    @Mock ContestEntityService contests;
    @Mock ContestProblemEntityService problems;
    @Mock ContestRecordEntityService records;
    @Mock JudgeEntityService judges;

    private void endedContest() {
        when(contests.getOne(any())).thenReturn(new Contest().setId(1L)
                .setStartTime(new Date(0)).setEndTime(new Date(1)));
        when(problems.getOne(any())).thenReturn(new ContestProblem().setId(9L).setPid(2L).setCid(1L));
    }

    @Test void rejectsRunningContestBeforeTouchingHistory() {
        when(contests.getOne(any())).thenReturn(new Contest().setStartTime(new Date(0))
                .setEndTime(new Date(System.currentTimeMillis() + 60000)));
        assertThrows(StatusFailException.class, () -> service.remove(2L, 1L));
        verifyNoInteractions(problems, judges, records);
    }

    @Test void preservesSubmissionsAndTheirCascadingChildrenAfterContest() {
        endedContest();
        when(judges.count(any())).thenReturn(1);
        assertThrows(StatusFailException.class, () -> service.remove(2L, 1L));
        verify(problems, never()).removeById(any(java.io.Serializable.class));
        verify(judges, never()).remove(any());
        verifyNoInteractions(records);
    }

    @Test void preservesCompetitionRecordsEvenWithoutJudgeRows() {
        endedContest();
        when(records.count(any())).thenReturn(1);
        assertThrows(StatusFailException.class, () -> service.remove(2L, 1L));
        verify(problems, never()).removeById(any(java.io.Serializable.class));
        verify(judges, never()).remove(any());
    }

    @Test void removesOnlyUnusedAssociation() throws Exception {
        endedContest();
        when(problems.removeById(9L)).thenReturn(true);
        service.remove(2L, 1L);
        verify(problems).removeById(9L);
        verify(judges, never()).remove(any());
        verify(records, never()).remove(any());
    }

    @Test void rejectsMissingContest() {
        assertThrows(StatusFailException.class, () -> service.remove(2L, 1L));
        verifyNoInteractions(problems, judges, records);
    }
}
