package top.hcode.hoj.pojo.dto;

import lombok.Data;

@Data
public class AiAssistantRequestDTO {

    private Long problemId;

    private Long trainingId;

    private String sourceType;

    private String requestType;

    private String language;

    private String code;

    private String problemTitle;

    private String problemDescription;

    private String problemInput;

    private String problemOutput;

    private String problemExamples;

    private String problemHint;
}
