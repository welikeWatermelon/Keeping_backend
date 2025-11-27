package com.ssafy.keeping.domain.user.finopenapi.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.ssafy.keeping.domain.charge.dto.ssafyapi.request.SsafyApiHeaderDto;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InsertMerchantRequest {

    @JsonProperty("Header")
    private SsafyApiHeaderDto header;

    private String categoryId;

    private String merchantName;

    public static InsertMerchantRequest create(
            SsafyApiHeaderDto header,
            String categoryId,
            String merchantName
    ) {
        return InsertMerchantRequest.builder()
                .header(header)
                .categoryId(categoryId)
                .merchantName(merchantName)
                .build();
    }
}
