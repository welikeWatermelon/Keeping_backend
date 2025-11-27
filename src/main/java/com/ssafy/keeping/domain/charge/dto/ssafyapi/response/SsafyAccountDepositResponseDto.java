package com.ssafy.keeping.domain.charge.dto.ssafyapi.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class SsafyAccountDepositResponseDto {

    @JsonProperty("Header")
    private SsafyApiResponseHeaderDto header;

    @JsonProperty("REC")
    private SsafyAccountDepositRecDto rec;

    public boolean isSuccess() {
        return header != null && header.isSuccess();
    }
}