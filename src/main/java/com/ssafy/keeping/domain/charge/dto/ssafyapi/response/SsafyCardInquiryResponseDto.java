package com.ssafy.keeping.domain.charge.dto.ssafyapi.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;
import java.util.List;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class SsafyCardInquiryResponseDto {

    @JsonProperty("Header")
    private SsafyApiResponseHeaderDto header;

    @JsonProperty("REC")
    private List<SsafyCardInquiryRecDto> rec;

    public boolean isSuccess() {
        return header != null && "H0000".equals(header.getResponseCode());
    }

    public SsafyApiResponseHeaderDto getHeader() {
        return header;
    }

    public List<SsafyCardInquiryRecDto> getREC() {
        return rec;
    }
}