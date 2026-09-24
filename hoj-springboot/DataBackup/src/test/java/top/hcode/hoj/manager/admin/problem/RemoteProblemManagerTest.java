package top.hcode.hoj.manager.admin.problem;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import top.hcode.hoj.crawler.problem.ProblemStrategy;
import top.hcode.hoj.dao.judge.RemoteJudgeAccountEntityService;
import top.hcode.hoj.dao.problem.LanguageEntityService;
import top.hcode.hoj.dao.problem.ProblemCaseEntityService;
import top.hcode.hoj.dao.problem.ProblemEntityService;
import top.hcode.hoj.dao.problem.ProblemLanguageEntityService;
import top.hcode.hoj.dao.problem.ProblemTagEntityService;
import top.hcode.hoj.dao.problem.TagEntityService;
import top.hcode.hoj.pojo.entity.problem.Language;
import top.hcode.hoj.pojo.entity.problem.Problem;
import top.hcode.hoj.pojo.entity.problem.ProblemCase;
import top.hcode.hoj.pojo.entity.problem.ProblemTag;
import top.hcode.hoj.pojo.entity.problem.Tag;
import top.hcode.hoj.utils.Constants;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RemoteProblemManagerTest {

    @InjectMocks
    private RemoteProblemManager manager;

    @Mock
    private ProblemEntityService problemEntityService;

    @Mock
    private ProblemTagEntityService problemTagEntityService;

    @Mock
    private TagEntityService tagEntityService;

    @Mock
    private LanguageEntityService languageEntityService;

    @Mock
    private ProblemLanguageEntityService problemLanguageEntityService;

    @Mock
    private ProblemCaseEntityService problemCaseEntityService;

    @Mock
    private RemoteJudgeAccountEntityService remoteJudgeAccountEntityService;

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void savesImportedAtCoderProblemAsLocalProblemWithSampleTestCases() {
        Problem problem = new Problem()
                .setProblemId("AC-dp_a")
                .setTitle("Frog 1")
                .setType(0)
                .setIsRemote(true);
        List<ProblemCase> problemCases = Arrays.asList(
                new ProblemCase().setInput("4\n10 30 40 20").setOutput("30"),
                new ProblemCase().setInput("2\n10 10").setOutput("0"));
        ProblemStrategy.RemoteProblemInfo info = new ProblemStrategy.RemoteProblemInfo()
                .setProblem(problem)
                .setProblemCaseList(problemCases)
                .setRemoteOJ(Constants.RemoteOJ.ATCODER);

        Language localLanguage = new Language().setId(7L).setName("C++ 17").setOj("ME");
        Tag atCoderTag = new Tag().setId(9L).setName("AC").setOj("AC");

        when(problemEntityService.save(problem)).thenAnswer(invocation -> {
            problem.setId(1001L);
            return true;
        });
        when(languageEntityService.list(any(QueryWrapper.class)))
                .thenReturn(Collections.singletonList(localLanguage));
        when(problemLanguageEntityService.saveOrUpdateBatch(anyList())).thenReturn(true);
        when(problemCaseEntityService.saveBatch(problemCases)).thenReturn(true);
        when(tagEntityService.getOne(any(QueryWrapper.class), eq(false))).thenReturn(atCoderTag);
        when(problemTagEntityService.saveOrUpdate(any(ProblemTag.class))).thenReturn(true);

        Problem result = manager.adminAddOtherOJProblem(info, "AC");

        assertEquals(problem, result);
        assertFalse(result.getIsRemote());
        assertFalse(result.getIsUploadCase());
        assertEquals(Constants.JudgeMode.DEFAULT.getMode(), result.getJudgeMode());
        assertEquals(Constants.JudgeCaseMode.DEFAULT.getMode(), result.getJudgeCaseMode());
        assertNotNull(result.getCaseVersion());
        problemCases.forEach(problemCase -> assertEquals(1001L, problemCase.getPid()));

        ArgumentCaptor<QueryWrapper> languageQueryCaptor = ArgumentCaptor.forClass(QueryWrapper.class);
        verify(languageEntityService).list(languageQueryCaptor.capture());
        languageQueryCaptor.getValue().getSqlSegment();
        assertTrue(languageQueryCaptor.getValue().getParamNameValuePairs().containsValue("ME"));
        verify(problemCaseEntityService).saveBatch(problemCases);
        verify(problemEntityService).initHandTestCaseSynchronously(
                result.getJudgeMode(),
                result.getJudgeCaseMode(),
                result.getCaseVersion(),
                result.getId(),
                problemCases);
    }
}
