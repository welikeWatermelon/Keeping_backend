package com.ssafy.keeping.domain.charge.dto.request;

import lombok.*;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class PrepaymentRequestDto {


    @NotBlank(message = "카드 번호는 필수입니다.")
    @Pattern(regexp = "\\d{16}", message = "카드 번호는 16자리 숫자여야 합니다.")
    private String cardNo;

    @NotBlank(message = "CVC는 필수입니다.")
    @Pattern(regexp = "\\d{3}", message = "CVC는 3자리 숫자여야 합니다.")
    private String cvc;

    @NotNull(message = "결제 금액은 필수입니다.")
    private Long paymentBalance;
}