package com.ssafy.keeping.domain.user.finopenapi.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DetailDto {

    private String categoryId;
    private String categoryName;
    private String merchantId;
    private String merchantName;
}
