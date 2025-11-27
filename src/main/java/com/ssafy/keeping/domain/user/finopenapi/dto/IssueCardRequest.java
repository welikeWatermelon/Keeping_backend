package com.ssafy.keeping.domain.user.finopenapi.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.ssafy.keeping.domain.charge.dto.ssafyapi.request.SsafyApiHeaderDto;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class IssueCardRequest {
    @JsonProperty("Header")
    private SsafyApiHeaderDto header;
    private String cardUniqueNo;
    private String withdrawalAccountNo;
    private String withdrawalDate;
}
