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
public class SearchCategoriesRequest {

    @JsonProperty("Header")
    private SsafyApiHeaderDto header;

    public static SearchCategoriesRequest create(SsafyApiHeaderDto header) {
        return SearchCategoriesRequest.builder()
                .header(header)
                .build();
    }
}
