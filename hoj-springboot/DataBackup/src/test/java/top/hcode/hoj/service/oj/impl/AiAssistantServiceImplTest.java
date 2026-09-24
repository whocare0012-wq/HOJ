package top.hcode.hoj.service.oj.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;
import top.hcode.hoj.validator.AiEndpointValidator;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AiAssistantServiceImplTest {

    private static final String OUTPUT_POLICY_REJECTION =
            "模型回答未通过安全检查，请稍后重新查询。";

    @Test
    void parsesAndSortsOpenAiCompatibleModelLists() {
        AiAssistantServiceImpl service = new AiAssistantServiceImpl();
        ReflectionTestUtils.setField(service, "objectMapper", new ObjectMapper());

        List<String> models = ReflectionTestUtils.invokeMethod(
                service,
                "parseModelIds",
                "{\"data\":[{\"id\":\"gpt-4o\"},{\"id\":\"deepseek-chat\"}," +
                        "{\"id\":\"gpt-4o\"},{\"name\":\"custom-model\"}]}");

        assertEquals(
                Arrays.asList("custom-model", "deepseek-chat", "gpt-4o"),
                models);
    }

    @Test
    void rejectsResponsesWithoutUsableModels() {
        AiAssistantServiceImpl service = new AiAssistantServiceImpl();
        ReflectionTestUtils.setField(service, "objectMapper", new ObjectMapper());

        assertThrows(
                IllegalStateException.class,
                () -> ReflectionTestUtils.invokeMethod(
                        service,
                        "parseModelIds",
                        "{\"data\":[]}"));
    }

    @Test
    void keepsValidCodeReviewResponse() {
        String response = "有效性：有效尝试\n\n"
                + "首要问题\n"
                + "- 位置：循环条件\n"
                + "- 现象：边界输入可能漏算\n"
                + "- 原因：最后一个元素没有进入循环\n"
                + "- 修改方向：重新核对循环终点，让最后一个有效下标也能被处理，但自行保留越界保护。\n"
                + "- 验证方法：分别测试一个元素和两个元素的输入。\n\n"
                + "其他问题\n"
                + "- 暂未发现其他确定问题\n\n"
                + "整体检查建议\n"
                + "- 使用只有一个元素、两个元素和普通规模输入检查边界。";

        assertEquals(response, sanitize("CODE_REVIEW", response));
    }

    @Test
    void keepsValidSolutionHintResponse() {
        String response = "题意拆解\n"
                + "- 目标是找出满足相邻关系的最优结果，注意单个元素也构成合法情况。\n\n"
                + "核心思路\n"
                + "- 按顺序处理元素，只保留延续到当前位置所必需的状态；"
                + "当相邻关系不再满足时，从当前位置重新开始统计。\n\n"
                + "伪代码提示\n"
                + "- 初始化当前连续长度和历史最优值\n"
                + "- 从第二个元素开始依次考察\n"
                + "- 若当前元素与前一个元素满足条件，则延长当前长度\n"
                + "- 否则从当前元素重新计算\n"
                + "- 每轮更新历史最优值\n\n"
                + "复杂度与边界\n"
                + "- 只遍历一次，时间复杂度为线性，额外空间为常数级。"
                + "自行检查空输入、单元素和关系始终不满足的情况。";

        assertEquals(response, sanitize("SOLUTION", response));
    }

    @Test
    void keepsDetailedCodeReviewBeyondFormerLengthLimit() {
        StringBuilder response = new StringBuilder();
        response.append("有效性：有效尝试\n\n")
                .append("首要问题\n")
                .append("- 位置：主循环中的状态更新区域\n")
                .append("- 现象：在连续出现多个相同值时，结果会比预期少一项。\n")
                .append("- 原因：当前状态在完成本轮比较前就被覆盖，后续判断读取到的是新值，")
                .append("因此丢失了用于比较的上一轮信息。\n")
                .append("- 修改方向：先基于上一轮状态完成本轮所有判断，再统一更新当前状态；")
                .append("可以增加一个只保存上一轮信息的独立状态，避免读写顺序互相影响。\n")
                .append("- 验证方法：构造包含连续相同值、交替值和单个值的输入，逐轮记录状态变化。\n\n")
                .append("其他问题\n");
        for (int index = 1; index <= 4; index++) {
            response.append("- 问题").append(index)
                    .append("：检查第").append(index)
                    .append("类边界场景中的条件判断、状态含义和更新时机；")
                    .append("修改时保持各状态职责单一，并用最小反例验证修改前后的差异。\n");
        }
        response.append("\n整体检查建议\n")
                .append("- 覆盖最小规模、全部相同、严格变化和在末尾触发边界的输入，")
                .append("重点观察循环前后状态是否一致。");
        for (int index = 1; index <= 6; index++) {
            response.append("补充场景").append(index)
                    .append("应分别记录输入特征、预期现象和关键状态变化，")
                    .append("用来确认修正没有影响原本正确的分支。");
        }

        assertTrue(response.length() > 700);
        assertEquals(
                response.toString(),
                sanitize("CODE_REVIEW", response.toString()));
    }

    @Test
    void keepsDetailedSolutionHintBeyondFormerLengthLimit() {
        StringBuilder response = new StringBuilder();
        response.append("题意拆解\n")
                .append("- 先区分输入中的有效信息、目标量和限制条件，再确认单元素、重复元素与极端范围是否需要单独处理。\n\n")
                .append("核心思路\n")
                .append("- 按题目给定顺序维护当前状态，只保留决定后续结果所需的信息；每处理一个元素，都比较延续当前状态与重新开始两种选择，")
                .append("并同步记录目前遇到的最优结果。这样不会重复枚举已经被状态概括的前缀，同时仍能覆盖最优区间从任意位置开始的情况。\n");
        for (int index = 1; index <= 12; index++) {
            response.append("- 补充观察").append(index)
                    .append("：明确这一阶段维护的状态含义、它与前一阶段的关系，以及在边界输入下为何仍然成立；")
                    .append("学生需要自行选择具体变量和数据表示，并通过小规模样例手工核对状态变化。\n");
        }
        response.append("\n伪代码提示\n")
                .append("- 读取并整理题目要求的输入信息\n")
                .append("- 建立表示当前局部结果与历史最优结果的状态\n")
                .append("- 按顺序考察每个元素是否能够延续当前状态\n")
                .append("- 不能延续时，从当前位置重新建立局部状态\n")
                .append("- 每轮比较并保留更优的历史结果\n")
                .append("- 最后输出题目要求的结果\n\n")
                .append("复杂度与边界\n")
                .append("- 只进行常数次顺序扫描时，时间复杂度为线性，额外空间为常数级。")
                .append("重点自查最小规模、全部相同、始终不能延续以及最优结果出现在末尾的情况。");

        assertTrue(response.length() > 1300);
        assertEquals(
                response.toString(),
                sanitize("SOLUTION", response.toString()));
    }

    @Test
    void allowsSafetyDisclaimerWithoutTreatingItAsSolutionDisclosure() {
        String response = "题意拆解\n"
                + "- 明确目标和限制，仍需学生自行处理输入细节。\n\n"
                + "核心思路\n"
                + "- 这里只说明关键观察，不提供完整算法或完整代码。\n\n"
                + "伪代码提示\n"
                + "- 整理输入\n- 维护必要状态\n- 更新候选结果\n- 自行补全边界处理\n\n"
                + "复杂度与边界\n"
                + "- 预期为线性时间；检查最小输入和极端范围。";

        assertEquals(response, sanitize("SOLUTION", response));
    }

    @Test
    void allowsFixedNoAttemptResponse() {
        String response = "当前代码未体现可供排错的有效解题尝试。请先根据题意完成核心逻辑，"
                + "再使用“查询代码问题”；AI 助手不会代写完整题解或代码。";

        assertEquals(response, sanitize("CODE_REVIEW", response));
    }

    @Test
    void rejectsFencedOrCompleteSourceCode() {
        String fenced = "理解重点\n- 条件\n\n思考方向\n- 提示\n\n复杂度提醒\n"
                + "- 线性\n\n自查问题\n- 问题\n```cpp\nint main() {}\n```";
        String plainProgram = "有效性：有效尝试\n首要问题\n建议验证\n"
                + "#include <iostream>\nusing namespace std;\nint main() { return 0; }";

        assertEquals(OUTPUT_POLICY_REJECTION, sanitize("SOLUTION", fenced));
        assertEquals(OUTPUT_POLICY_REJECTION, sanitize("CODE_REVIEW", plainProgram));
    }

    @Test
    void rejectsOverlongButAllowsNaturalResponsesWithoutLegacyHeadings() {
        StringBuilder overlong = new StringBuilder();
        overlong.append("解决方法\n- ");
        for (int index = 0; index < 6100; index++) {
            overlong.append('字');
        }

        assertEquals(
                OUTPUT_POLICY_REJECTION,
                sanitize("SOLUTION", overlong.toString()));
        String naturalResponse =
                "先按输入顺序检查每个元素，并维护当前已经得到的结果。"
                        + "遇到边界数据时要单独确认是否仍满足题目条件。";
        assertEquals(naturalResponse, sanitize("SOLUTION", naturalResponse));
    }

    @Test
    void allowsNaturalLanguageAboutPseudocodeWithoutFalsePositive() {
        String response = "这是一道只使用循环和基础数组的题目，可以先遍历所有元素，"
                + "按题意累积结果。核心伪代码只需表达“依次读取、判断、更新结果”三个动作，"
                + "具体变量、输入输出和边界处理需要学生自行完成。";

        assertEquals(response, sanitize("SOLUTION", response));
    }

    @Test
    void allowsCodeReviewToReferenceExistingCodeTokensInline() {
        String response = "编译错误位于容器声明附近：这里使用了 std::vector，"
                + "但相应类型在当前作用域中不可见。Java 版本中的 System.out.println"
                + "如果缺少右括号也会在这一行触发语法错误。请只核对原代码中的声明和括号配对。";

        assertEquals(response, sanitize("CODE_REVIEW", response));
    }

    @Test
    void repairsRejectedModelResponseOnce() throws Exception {
        String rejectedResponse =
                "下面给出完整参考实现：\n```cpp\nint main() { return 0; }\n```";
        String repairedResponse = validSolutionResponse();
        AiAssistantServiceImpl service = serviceWithModelResponses(
                rejectedResponse,
                repairedResponse);

        String result = ReflectionTestUtils.invokeMethod(
                service,
                "requestModel",
                "https://example.test/v1",
                "test-model",
                "test-api-key",
                "SOLUTION",
                "题目信息");

        assertEquals(repairedResponse, result);
        RestTemplate restTemplate =
                (RestTemplate) ReflectionTestUtils.getField(service, "restTemplate");
        verify(restTemplate, times(2)).exchange(
                anyString(),
                eq(HttpMethod.POST),
                any(HttpEntity.class),
                eq(String.class));
    }

    @Test
    void usesNeutralFallbackWhenRepairStillViolatesPolicy() throws Exception {
        String rejectedResponse =
                "下面给出完整参考实现：\n```cpp\nint main() { return 0; }\n```";
        AiAssistantServiceImpl service = serviceWithModelResponses(
                rejectedResponse,
                rejectedResponse);

        String result = ReflectionTestUtils.invokeMethod(
                service,
                "requestModel",
                "https://example.test/v1",
                "test-model",
                "test-api-key",
                "SOLUTION",
                "题目信息");

        assertEquals(OUTPUT_POLICY_REJECTION, result);
        RestTemplate restTemplate =
                (RestTemplate) ReflectionTestUtils.getField(service, "restTemplate");
        verify(restTemplate, times(2)).exchange(
                anyString(),
                eq(HttpMethod.POST),
                any(HttpEntity.class),
                eq(String.class));
    }

    private AiAssistantServiceImpl serviceWithModelResponses(
            String firstResponse,
            String secondResponse) throws Exception {
        AiAssistantServiceImpl service = new AiAssistantServiceImpl();
        ObjectMapper objectMapper = new ObjectMapper();
        RestTemplate restTemplate = mock(RestTemplate.class);
        AiEndpointValidator endpointValidator = mock(AiEndpointValidator.class);
        when(endpointValidator.validateAndNormalize("https://example.test/v1"))
                .thenReturn("https://example.test/v1");
        when(restTemplate.exchange(
                anyString(),
                eq(HttpMethod.POST),
                any(HttpEntity.class),
                eq(String.class)))
                .thenReturn(
                        ResponseEntity.ok(modelResponse(objectMapper, firstResponse)),
                        ResponseEntity.ok(modelResponse(objectMapper, secondResponse)));

        ReflectionTestUtils.setField(service, "objectMapper", objectMapper);
        ReflectionTestUtils.setField(service, "restTemplate", restTemplate);
        ReflectionTestUtils.setField(service, "aiEndpointValidator", endpointValidator);
        return service;
    }

    private String modelResponse(ObjectMapper objectMapper, String content)
            throws Exception {
        return objectMapper.writeValueAsString(
                java.util.Collections.singletonMap(
                        "choices",
                        java.util.Collections.singletonList(
                                java.util.Collections.singletonMap(
                                        "message",
                                        java.util.Collections.singletonMap(
                                                "content",
                                                content)))));
    }

    private String validSolutionResponse() {
        return "题意拆解\n"
                + "- 明确输入、目标和数据范围，先识别必须单独检查的最小规模情形。\n\n"
                + "核心思路\n"
                + "- 按顺序维护与后续决策有关的状态，每处理一个元素就更新候选结果，"
                + "并说明该状态为何足以描述已经处理的部分。\n\n"
                + "伪代码提示\n"
                + "- 整理输入\n"
                + "- 初始化必要状态\n"
                + "- 依次考察每个元素并更新状态\n"
                + "- 汇总并输出结果\n\n"
                + "复杂度与边界\n"
                + "- 预计只需一次遍历；重点检查最小输入、重复值和极端数据范围。";
    }

    private String sanitize(String requestType, String response) {
        AiAssistantServiceImpl service = new AiAssistantServiceImpl();
        return ReflectionTestUtils.invokeMethod(
                service,
                "sanitizeModelResponse",
                requestType,
                response);
    }
}
