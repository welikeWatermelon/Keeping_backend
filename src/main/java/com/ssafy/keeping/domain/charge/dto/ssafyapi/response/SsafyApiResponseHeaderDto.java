package com.ssafy.keeping.domain.charge.dto.ssafyapi.response;

import lombok.*;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class SsafyApiResponseHeaderDto {

    private String responseCode;
    private String responseMessage;
    private String apiName;
    private String transmissionDate;
    private String transmissionTime;
    private String institutionCode;
    private String apiKey;
    private String apiServiceCode;
    private String institutionTransactionUniqueNo;

    public boolean isSuccess() {
        return "H0000".equals(responseCode);
    }
}