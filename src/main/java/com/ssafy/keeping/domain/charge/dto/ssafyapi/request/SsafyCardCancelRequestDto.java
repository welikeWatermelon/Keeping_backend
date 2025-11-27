package com.ssafy.keeping.domain.charge.dto.ssafyapi.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class SsafyCardCancelRequestDto {

    @JsonProperty("Header")
    private SsafyApiHeaderDto header;

    private String cardNo;
    private String cvc;
    private String transactionUniqueNo;
}