package com.ssafy.keeping.domain.user.finopenapi.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.ssafy.keeping.domain.charge.dto.ssafyapi.request.SsafyApiHeaderDto;
import com.ssafy.keeping.domain.charge.dto.ssafyapi.response.SsafyApiResponseHeaderDto;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class IssueCardResponse {

    @JsonProperty("Header")
    private SsafyApiResponseHeaderDto header;

    @JsonProperty("REC")
    private IssueCardRecResponse recResponse;
}
