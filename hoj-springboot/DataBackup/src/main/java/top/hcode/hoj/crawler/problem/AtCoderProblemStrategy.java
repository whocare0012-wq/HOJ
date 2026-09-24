package top.hcode.hoj.crawler.problem;

import cn.hutool.core.util.ReUtil;
import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import org.apache.commons.lang3.StringUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.jsoup.parser.Parser;
import top.hcode.hoj.pojo.entity.problem.Problem;
import top.hcode.hoj.pojo.entity.problem.ProblemCase;
import top.hcode.hoj.utils.Constants;

import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @Author: Himit_ZH
 * @Date: 2022/1/28 21:23
 * @Description:
 */
public class AtCoderProblemStrategy extends ProblemStrategy {

    public static final String JUDGE_NAME = "AC";
    public static final String HOST = "https://atcoder.jp";
    public static final String PROBLEM_URL = "/contests/%s/tasks/%s";
    private static final int REQUEST_TIMEOUT_MILLIS = 20000;
    private static final int DEFAULT_STACK_LIMIT_MB = 128;
    private static final Pattern PROBLEM_ID_PATTERN = Pattern.compile("^[a-z0-9]+(?:_[a-z0-9]+)+$");
    private static final Pattern LIMIT_PATTERN = Pattern.compile(
            "Time Limit:\\s*([0-9]+(?:\\.[0-9]+)?)\\s*sec\\s*/\\s*Memory Limit:\\s*(\\d+)\\s*M(?:i)?B",
            Pattern.CASE_INSENSITIVE);

    public String getJudgeName() {
        return JUDGE_NAME;
    }

    public String getProblemUrl(String problemId, String contestId) {
        return HOST + String.format(PROBLEM_URL, contestId, problemId);
    }

    public String getProblemSource(String problemId, String contestId) {
        return String.format("<a style='color:#1A5CC8' href='" + getProblemUrl(problemId, contestId) + "'>%s</a>", "AtCoder-" + problemId);
    }

    @Override
    public RemoteProblemInfo getProblemInfo(String problemId, String author) throws Exception {
        String normalizedProblemId = normalizeProblemId(problemId);
        String contestId = normalizedProblemId.substring(0, normalizedProblemId.indexOf('_'));
        String body = fetchProblemBody(normalizedProblemId, contestId);
        return parseProblemInfo(normalizedProblemId, author, body);
    }

    String normalizeProblemId(String problemId) {
        if (StringUtils.isBlank(problemId)) {
            throw new IllegalArgumentException("AtCoder: Incorrect problem id format! Must be like `abc110_a`");
        }
        String normalizedProblemId = problemId.trim().toLowerCase(Locale.ROOT);
        if (!PROBLEM_ID_PATTERN.matcher(normalizedProblemId).matches()) {
            throw new IllegalArgumentException("AtCoder: Incorrect problem id format! Must be like `abc110_a` or `dp_a`");
        }
        return normalizedProblemId;
    }

    private String fetchProblemBody(String problemId, String contestId) {
        String url = getProblemUrl(problemId, contestId);
        try (HttpResponse response = HttpRequest.get(url + "?lang=en")
                .header("User-Agent", "Mozilla/5.0 (compatible; HOJ-Problem-Importer/1.0)")
                .header("Accept-Language", "en-US,en;q=0.9")
                .timeout(REQUEST_TIMEOUT_MILLIS)
                .execute()) {
            int status = response.getStatus();
            if (status == 404) {
                throw new IllegalArgumentException(
                        String.format("AtCoder: Problem `%s` was not found. Please enter a task id such as `dp_a`.", problemId));
            }
            if (status < 200 || status >= 300) {
                throw new IllegalStateException(
                        String.format("AtCoder: Failed to fetch problem `%s` (HTTP %d).", problemId, status));
            }
            String body = response.body();
            if (StringUtils.isBlank(body)) {
                throw new IllegalStateException(
                        String.format("AtCoder: Received an empty page for problem `%s`.", problemId));
            }
            return body;
        } catch (IllegalArgumentException | IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException(
                    String.format("AtCoder: Failed to fetch problem `%s`.", problemId), e);
        }
    }

    RemoteProblemInfo parseProblemInfo(String problemId, String author, String body) {
        problemId = normalizeProblemId(problemId);
        String contestId = problemId.substring(0, problemId.indexOf('_'));

        Matcher matcher = LIMIT_PATTERN.matcher(body);
        if (!matcher.find()) {
            throw new IllegalStateException(
                    String.format("AtCoder: Unable to parse time or memory limit for problem `%s`.", problemId));
        }
        String timeLimit = matcher.group(1).trim();
        String memoryLimit = matcher.group(2).trim();
        String title = ReUtil.get("<title>[\\s\\S]*? - ([\\s\\S]*?)</title>", body, 1);
        if (StringUtils.isBlank(title)) {
            throw new IllegalStateException(
                    String.format("AtCoder: Unable to parse the title for problem `%s`.", problemId));
        }


        Problem problem = new Problem();
        problem.setProblemId(getJudgeName() + "-" + problemId)
                .setAuthor(author)
                .setTitle(title)
                .setType(0)
                .setTimeLimit((int) Math.round(Double.parseDouble(timeLimit) * 1000))
                .setMemoryLimit(Integer.parseInt(memoryLimit))
                .setStackLimit(DEFAULT_STACK_LIMIT_MB)
                .setJudgeMode(Constants.JudgeMode.DEFAULT.getMode())
                .setJudgeCaseMode(Constants.JudgeCaseMode.DEFAULT.getMode())
                .setIsRemote(false)
                .setIsUploadCase(false)
                .setSource(getProblemSource(problemId, contestId))
                .setAuth(1)
                .setOpenCaseResult(false)
                .setIsRemoveEndBlank(false)
                .setIsGroup(false)
                .setDifficulty(1); // 默认为中等

        if (body.contains("Problem Statement")) {
            String desc = ReUtil.get("<h3>Problem Statement</h3>([\\s\\S]*?)</section>[\\s\\S]*?</div>", body, 1);
            if (StringUtils.isBlank(desc)) {
                throw new IllegalStateException(
                        String.format("AtCoder: Unable to parse the statement for problem `%s`.", problemId));
            }

            desc = desc.replaceAll("<var>", "\\$").replaceAll("</var>", "\\$");
            desc = desc.replaceAll("<pre>", "<pre style=\"padding:9px!important;background-color: #f5f5f5!important\">");
            desc = desc.replaceAll("src=\"/img", "src=\"" + HOST + "/img");

            StringBuilder sb = new StringBuilder();
            String rawInput = ReUtil.get("<h3>Input</h3>([\\s\\S]*?)</section>[\\s\\S]*?</div>", body, 1);
            if (StringUtils.isBlank(rawInput)) {
                throw new IllegalStateException(
                        String.format("AtCoder: Unable to parse the input section for problem `%s`.", problemId));
            }
            sb.append(rawInput);
            String constrains = ReUtil.get("<h3>Constraints</h3>([\\s\\S]*?)</section>[\\s\\S]*?</div>", body, 1);
            if (StringUtils.isNotBlank(constrains)) {
                sb.append(constrains);
            }
            String input = sb.toString().replaceAll("<var>", "\\$").replaceAll("</var>", "\\$");
            input = input.replaceAll("<pre>", "<pre style=\"padding:9px!important;background-color: #f5f5f5!important\">");


            String rawOutput = ReUtil.get("<h3>Output</h3>([\\s\\S]*?)</section>[\\s\\S]*?</div>", body, 1);
            if (StringUtils.isBlank(rawOutput)) {
                throw new IllegalStateException(
                        String.format("AtCoder: Unable to parse the output section for problem `%s`.", problemId));
            }
            String output = rawOutput.replaceAll("<var>", "\\$").replaceAll("</var>", "\\$");
            output = output.replaceAll("<pre>", "<pre style=\"padding:9px!important;background-color: #f5f5f5!important\">");

            List<String> sampleInput = ReUtil.findAll("<h3>Sample Input \\d+</h3><pre>([\\s\\S]*?)</pre>[\\s\\S]*?</section>[\\s\\S]*?</div>", body, 1);
            List<String> sampleOutput = ReUtil.findAll("<h3>Sample Output \\d+</h3><pre>([\\s\\S]*?)</pre>[\\s\\S]*?</section>[\\s\\S]*?</div>", body, 1);

            if (sampleInput.isEmpty() || sampleInput.size() != sampleOutput.size()) {
                throw new IllegalStateException(
                        String.format("AtCoder: Unable to build local test cases for problem `%s`: "
                                + "sample input/output pairs are missing or incomplete.", problemId));
            }


            StringBuilder examples = new StringBuilder();
            List<ProblemCase> problemCaseList = new LinkedList<>();

            for (int i = 0; i < sampleInput.size(); i++) {
                examples.append("<input>");
                String exampleInput = sampleInput.get(i).trim();
                examples.append(exampleInput).append("</input>");
                examples.append("<output>");
                String exampleOutput = sampleOutput.get(i).trim();
                examples.append(exampleOutput).append("</output>");
                problemCaseList.add(new ProblemCase()
                        .setInput(normalizeSampleData(sampleInput.get(i)))
                        .setOutput(normalizeSampleData(sampleOutput.get(i)))
                        .setStatus(0)
                        .setGroupNum(1));
            }

            problem.setInput(input.trim())
                    .setOutput(output.trim())
                    .setDescription(desc.trim())
                    .setExamples(examples.toString());

            return new RemoteProblemInfo()
                    .setProblem(problem)
                    .setProblemCaseList(problemCaseList)
                    .setTagList(null)
                    .setLangIdList(null)
                    .setRemoteOJ(Constants.RemoteOJ.ATCODER);


        } else {
            Element element = Jsoup.parse(body).getElementById("task-statement");
            if (element == null) {
                throw new IllegalStateException(
                        String.format("AtCoder: Unable to locate the statement for problem `%s`.", problemId));
            }
            String desc = element.html();
            desc = desc.replaceAll("src=\"/img", "src=\"https://atcoder.jp/img");
            desc = desc.replaceAll("<pre>", "<pre style=\"padding:9px!important;background-color: #f5f5f5!important\">");
            desc = desc.replaceAll("<var>", "\\$").replaceAll("</var>", "\\$");
            desc = desc.replaceAll("<hr>", "");
            problem.setDescription(desc);
            throw new IllegalStateException(
                    String.format("AtCoder: Unable to build local test cases for problem `%s`: "
                            + "the English sample sections could not be parsed.", problemId));
        }
    }

    private String normalizeSampleData(String sampleHtml) {
        String normalized = sampleHtml
                .replaceAll("(?i)<br\\s*/?>", "\n")
                .replaceAll("<[^>]+>", "");
        normalized = Parser.unescapeEntities(normalized, false)
                .replace("\r\n", "\n")
                .replace('\r', '\n');
        return StringUtils.strip(normalized, "\n");
    }
}
