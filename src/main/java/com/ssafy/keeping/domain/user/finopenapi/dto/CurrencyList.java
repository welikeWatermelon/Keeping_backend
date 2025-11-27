package com.ssafy.keeping.domain.user.finopenapi.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class CurrencyList {
    private String currency;
    private String currencyName;
}
