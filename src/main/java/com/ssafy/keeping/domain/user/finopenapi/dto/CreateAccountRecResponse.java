package com.ssafy.keeping.domain.user.finopenapi.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateAccountRecResponse {

    private String bankCode;
    private String accountNo;

    @JsonProperty("currency")
    private CurrencyList currencyList;

}
