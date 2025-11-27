package com.ssafy.keeping.domain.charge.dto.request;

import lombok.*;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class CancelRequestDto {
    
    @NotBlank(message = "거래 고유번호는 필수입니다.")
    private String transactionUniqueNo;
    
    @NotBlank(message = "카드번호는 필수입니다.")
    @Size(min = 16, max = 16, message = "카드번호는 16자리여야 합니다.")
    private String cardNo;
    
    @NotBlank(message = "CVC번호는 필수입니다.")
    @Size(min = 3, max = 3, message = "CVC번호는 3자리여야 합니다.")
    private String cvc;
}