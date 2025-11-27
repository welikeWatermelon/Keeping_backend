package com.ssafy.keeping.domain.user.finopenapi.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.ssafy.keeping.domain.charge.dto.ssafyapi.request.SsafyApiHeaderDto;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import org.springframework.beans.factory.annotation.Value;

@Data
@Builder
@AllArgsConstructor
public class CreateAccountRequest {

    @JsonProperty("Header")
    private SsafyApiHeaderDto header;

    private String accountTypeUniqueNo;
}
