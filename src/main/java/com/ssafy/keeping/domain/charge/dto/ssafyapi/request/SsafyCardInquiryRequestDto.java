package com.ssafy.keeping.domain.charge.dto.ssafyapi.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class SsafyCardInquiryRequestDto {

    @JsonProperty("Header")
    private SsafyApiHeaderDto header;

    public static SsafyCardInquiryRequestDto create(SsafyApiHeaderDto header) {
        return SsafyCardInquiryRequestDto.builder()
                .header(header)
                .build();
    }
}