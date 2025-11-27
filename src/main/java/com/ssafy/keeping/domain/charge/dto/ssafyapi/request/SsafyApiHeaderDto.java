package com.ssafy.keeping.domain.charge.dto.ssafyapi.request;

import lombok.*;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class SsafyApiHeaderDto {

    private String apiName;
    private String transmissionDate;
    private String transmissionTime;
    private String institutionCode;
    private String fintechAppNo;
    private String apiServiceCode;
    private String institutionTransactionUniqueNo;
    private String apiKey;
    private String userKey;

    public static SsafyApiHeaderDto createCardPaymentHeader(
            String transmissionDate,
            String transmissionTime,
            String institutionTransactionUniqueNo,
            String apiKey,
            String userKey) {
        
        return SsafyApiHeaderDto.builder()
                .apiName("createCreditCardTransaction")
                .transmissionDate(transmissionDate)
                .transmissionTime(transmissionTime)
                .institutionCode("00100")
                .fintechAppNo("001")
                .apiServiceCode("createCreditCardTransaction")
                .institutionTransactionUniqueNo(institutionTransactionUniqueNo)
                .apiKey(apiKey)
                .userKey(userKey)
                .build();
    }

    public static SsafyApiHeaderDto createAccountDepositHeader(
            String transmissionDate,
            String transmissionTime,
            String institutionTransactionUniqueNo,
            String apiKey,
            String userKey) {
        
        return SsafyApiHeaderDto.builder()
                .apiName("updateDemandDepositAccountDeposit")
                .transmissionDate(transmissionDate)
                .transmissionTime(transmissionTime)
                .institutionCode("00100")
                .fintechAppNo("001")
                .apiServiceCode("updateDemandDepositAccountDeposit")
                .institutionTransactionUniqueNo(institutionTransactionUniqueNo)
                .apiKey(apiKey)
                .userKey(userKey)
                .build();
    }

    public static SsafyApiHeaderDto createCommonHeaderDto(
            String transmissionDate,
            String transmissionTime,
            String institutionTransactionUniqueNo,
            String apiKey,
            String userKey,
            String apiName) {

        return SsafyApiHeaderDto.builder()
                .apiName(apiName)
                .transmissionDate(transmissionDate)
                .transmissionTime(transmissionTime)
                .institutionCode("00100")
                .fintechAppNo("001")
                .apiServiceCode(apiName)
                .institutionTransactionUniqueNo(institutionTransactionUniqueNo)
                .apiKey(apiKey)
                .userKey(userKey)
                .build();
    }

    public static SsafyApiHeaderDto createCardInquiryHeader(
            String transmissionDate,
            String transmissionTime,
            String institutionTransactionUniqueNo,
            String apiKey,
            String userKey) {

        return SsafyApiHeaderDto.builder()
                .apiName("inquireSignUpCreditCardList")
                .transmissionDate(transmissionDate)
                .transmissionTime(transmissionTime)
                .institutionCode("00100")
                .fintechAppNo("001")
                .apiServiceCode("inquireSignUpCreditCardList")
                .institutionTransactionUniqueNo(institutionTransactionUniqueNo)
                .apiKey(apiKey)
                .userKey(userKey)
                .build();
    }

    public static SsafyApiHeaderDto createCommonHeaderWithoutUserKeyDto(
            String transmissionDate,
            String transmissionTime,
            String institutionTransactionUniqueNo,
            String apiKey,
            String apiName) {

        return SsafyApiHeaderDto.builder()
                .apiName(apiName)
                .transmissionDate(transmissionDate)
                .transmissionTime(transmissionTime)
                .institutionCode("00100")
                .fintechAppNo("001")
                .apiServiceCode(apiName)
                .institutionTransactionUniqueNo(institutionTransactionUniqueNo)
                .apiKey(apiKey)
                .build();
    }
}