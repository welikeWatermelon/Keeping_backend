package com.ssafy.keeping.domain.charge.service;

import com.ssafy.keeping.domain.charge.dto.ssafyapi.request.SsafyApiHeaderDto;
import com.ssafy.keeping.domain.charge.dto.ssafyapi.request.SsafyCardPaymentRequestDto;
import com.ssafy.keeping.domain.charge.dto.ssafyapi.request.SsafyCardCancelRequestDto;
import com.ssafy.keeping.domain.charge.dto.ssafyapi.request.SsafyAccountDepositRequestDto;
import com.ssafy.keeping.domain.charge.dto.ssafyapi.request.SsafyCardInquiryRequestDto;
import com.ssafy.keeping.domain.charge.dto.ssafyapi.response.SsafyCardPaymentResponseDto;
import com.ssafy.keeping.domain.charge.dto.ssafyapi.response.SsafyCardCancelResponseDto;
import com.ssafy.keeping.domain.charge.dto.ssafyapi.response.SsafyAccountDepositResponseDto;
import com.ssafy.keeping.domain.charge.dto.ssafyapi.response.SsafyCardInquiryResponseDto;
import com.ssafy.keeping.global.exception.CustomException;
import com.ssafy.keeping.global.exception.constants.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.client.HttpClientErrorException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.atomic.AtomicLong;

@Service
@RequiredArgsConstructor
@Slf4j
public class SsafyFinanceApiService {

    // RestTemplate : 외부 API 통신을 하기 위한 자료구조
    // GET, POST 등 다양한 방식의 HTTP 요청을 보내고, 그 응답을 받아오는 모든 과정을 담당
    private final RestTemplate restTemplate;
    
    @Value("${ssafy.finance.api.base-url}")
    private String baseUrl;
    
    @Value("${ssafy.finance.api.key}")
    private String apiKey;
    
    // 기관거래고유번호 생성을 위한 카운터 (날짜+시간+6자리 일련번호)
    // AtomicLong은 멀티스레드 환경에서 안전하게 하기 위한 자료구조
    // '읽고-수정하고-쓰는' 과정을 **절대 중간에 끊기지 않는 하나의 동작(원자적 연산)**으로 보장
    private final AtomicLong transactionCounter = new AtomicLong(0);

    /**
     * 카드 결제 API 호출
     */
    public SsafyCardPaymentResponseDto requestCardPayment(
            String userKey,
            String cardNo,
            String cvc,
            String merchantId,
            long paymentBalance) {
        
        // API 헤더 생성
        SsafyApiHeaderDto header = createCardPaymentHeader(userKey);
        
        // 요청 DTO 생성
        SsafyCardPaymentRequestDto requestDto = SsafyCardPaymentRequestDto.create(
                header, cardNo, cvc, merchantId, paymentBalance);
        
        // HTTP 요청 생성
        HttpEntity<SsafyCardPaymentRequestDto> requestEntity = createHttpEntity(requestDto);
        
        // API 호출
        String url = baseUrl + "/ssafy/api/v1/edu/creditCard/createCreditCardTransaction";
        
        ResponseEntity<SsafyCardPaymentResponseDto> response;

        try {
            response = restTemplate.postForEntity(url, requestEntity, SsafyCardPaymentResponseDto.class);
        } catch (HttpClientErrorException e) {
            String responseBody = e.getResponseBodyAsString();
            if (responseBody.contains("A1054")) {
                throw new CustomException(ErrorCode.INVALID_CARD_NUMBER);
            } else if (responseBody.contains("A1055")) {
                throw new CustomException(ErrorCode.INVALID_CVC);
            }
            throw new CustomException(ErrorCode.CARD_PAYMENT_FAILED);
        } catch (Exception e) {
            // 여기서 연결 처리를 해주는게 맞다 생각해서 try-catch로 잡았음
            log.error("카드 결제 API 통신 오류", e);
            throw new CustomException(ErrorCode.EXTERNAL_API_ERROR);
        }

        SsafyCardPaymentResponseDto responseDto = response.getBody();

        if (responseDto == null || !responseDto.isSuccess()) {
            String errorMessage = (responseDto != null && responseDto.getHeader() != null)
                    ? responseDto.getHeader().getResponseMessage()
                    : "응답 없음";
            log.error("카드 결제 실패 - {}", errorMessage);
            throw new CustomException(ErrorCode.CARD_PAYMENT_FAILED);
        }
        
        log.info("카드 결제 성공 - 거래고유번호: {}", responseDto.getRec().getTransactionUniqueNo());
        return responseDto;
    }

    /**
     * 카드 결제용 API 헤더 생성
     */
    private SsafyApiHeaderDto createCardPaymentHeader(String userKey) {
        LocalDateTime now = LocalDateTime.now();
        String transmissionDate = now.format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String transmissionTime = now.format(DateTimeFormatter.ofPattern("HHmmss"));
        String institutionTransactionUniqueNo = generateInstitutionTransactionUniqueNo(now);
        
        return SsafyApiHeaderDto.createCardPaymentHeader(
                transmissionDate,
                transmissionTime,
                institutionTransactionUniqueNo,
                apiKey,
                userKey
        );
    }

    /**
     * 기관거래고유번호 생성 (YYYYMMDDHHMMSS + 6자리 일련번호)
     */
    private String generateInstitutionTransactionUniqueNo(LocalDateTime dateTime) {
        String dateTimeStr = dateTime.format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        long counter = transactionCounter.incrementAndGet() % 1000000; // 6자리로 제한
        return String.format("%s%06d", dateTimeStr, counter);
    }

    /**
     * 계좌 입금 API 호출 (정산용)
     */
    public SsafyAccountDepositResponseDto requestAccountDeposit(
            String userKey,
            String accountNo,
            Long transactionBalance,
            String transactionSummary) {
        
        // API 헤더 생성
        SsafyApiHeaderDto header = createAccountDepositHeader(userKey);
        
        // 요청 DTO 생성
        SsafyAccountDepositRequestDto requestDto = SsafyAccountDepositRequestDto.create(
                header, accountNo, transactionBalance, transactionSummary);
        
        // HTTP 요청 생성
        HttpEntity<SsafyAccountDepositRequestDto> requestEntity = createHttpEntity(requestDto);
        
        // API 호출
        String url = baseUrl + "/ssafy/api/v1/edu/demandDeposit/updateDemandDepositAccountDeposit";
        
        ResponseEntity<SsafyAccountDepositResponseDto> response;
        try {
            response = restTemplate.postForEntity(url, requestEntity, SsafyAccountDepositResponseDto.class);
        } catch (Exception e) {
            // 여기서 연결 처리를 해주는게 맞다 생각해서 try-catch로 잡았음
            log.error("계좌 입금 API 통신 오류", e);
            throw new CustomException(ErrorCode.EXTERNAL_API_ERROR);
        }
        
        SsafyAccountDepositResponseDto responseDto = response.getBody();
        
        if (responseDto == null || !responseDto.isSuccess()) {
            String errorMessage = (responseDto != null && responseDto.getHeader() != null) 
                    ? responseDto.getHeader().getResponseMessage() 
                    : "응답 없음";
            log.error("계좌 입금 실패 - {}", errorMessage);
            throw new CustomException(ErrorCode.ACCOUNT_DEPOSIT_FAILED);
        }
        
        log.info("계좌 입금 성공 - 거래고유번호: {}, 계좌번호: {}, 금액: {}", 
                responseDto.getRec().getTransactionUniqueNo(), accountNo, transactionBalance);
        return responseDto;
    }

    /**
     * 카드 결제 취소 API 호출
     */
    public SsafyCardCancelResponseDto requestCardCancel(
            String userKey,
            String cardNo,
            String cvc,
            String transactionUniqueNo) {
        
        // API 헤더 생성
        SsafyApiHeaderDto header = createCardCancelHeader(userKey);
        
        // 요청 DTO 생성
        SsafyCardCancelRequestDto requestDto = SsafyCardCancelRequestDto.builder()
                .header(header)
                .cardNo(cardNo)
                .cvc(cvc)
                .transactionUniqueNo(transactionUniqueNo)
                .build();
        
        // HTTP 요청 생성
        HttpEntity<SsafyCardCancelRequestDto> requestEntity = createHttpEntity(requestDto);
        
        // API 호출
        String url = baseUrl + "/ssafy/api/v1/edu/creditCard/deleteTransaction";
        
        ResponseEntity<SsafyCardCancelResponseDto> response;
        try {
            response = restTemplate.postForEntity(url, requestEntity, SsafyCardCancelResponseDto.class);
        } catch (Exception e) {
            log.error("카드 결제 취소 API 통신 오류", e);
            throw new CustomException(ErrorCode.EXTERNAL_API_ERROR);
        }
        
        SsafyCardCancelResponseDto responseDto = response.getBody();
        
        if (responseDto == null || !responseDto.isSuccess()) {
            String errorMessage = (responseDto != null && responseDto.getHeader() != null) 
                    ? responseDto.getHeader().getResponseMessage() 
                    : "응답 없음";
            log.error("카드 결제 취소 실패 - {}", errorMessage);
            throw new CustomException(ErrorCode.CARD_PAYMENT_FAILED);
        }
        
        log.info("카드 결제 취소 성공 - 거래고유번호: {}, 취소금액: {}", 
                responseDto.getRec().getTransactionUniqueNo(), responseDto.getRec().getTransactionBalance());
        return responseDto;
    }

    /**
     * 카드 취소용 API 헤더 생성
     */
    private SsafyApiHeaderDto createCardCancelHeader(String userKey) {
        LocalDateTime now = LocalDateTime.now();
        String transmissionDate = now.format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String transmissionTime = now.format(DateTimeFormatter.ofPattern("HHmmss"));
        String institutionTransactionUniqueNo = generateInstitutionTransactionUniqueNo(now);
        
        return SsafyApiHeaderDto.builder()
                .apiName("deleteTransaction")
                .transmissionDate(transmissionDate)
                .transmissionTime(transmissionTime)
                .institutionCode("00100")
                .fintechAppNo("001")
                .apiServiceCode("deleteTransaction")
                .institutionTransactionUniqueNo(institutionTransactionUniqueNo)
                .apiKey(apiKey)
                .userKey(userKey)
                .build();
    }

    /**
     * 계좌 입금용 API 헤더 생성
     */
    public SsafyApiHeaderDto createAccountDepositHeader(String userKey) {
        LocalDateTime now = LocalDateTime.now();
        String transmissionDate = now.format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String transmissionTime = now.format(DateTimeFormatter.ofPattern("HHmmss"));
        String institutionTransactionUniqueNo = generateInstitutionTransactionUniqueNo(now);
        
        return SsafyApiHeaderDto.createAccountDepositHeader(
                transmissionDate,
                transmissionTime,
                institutionTransactionUniqueNo,
                apiKey,
                userKey
        );
    }

    /**
     * HTTP 요청 엔터티 생성
     */
    private <T> HttpEntity<T> createHttpEntity(T requestDto) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON); // JSON으로 보낼 것이다
        return new HttpEntity<>(requestDto, headers);
        // 여기서의 headers는 진짜 header이고,
        // requestDto에 들어있는 header는 단지 이름이 header임
    }

    // 공통 헤더 생성
    public SsafyApiHeaderDto createCommonHeader(String userKey, String apiName) {
        LocalDateTime now = LocalDateTime.now();
        String transmissionDate = now.format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String transmissionTime = now.format(DateTimeFormatter.ofPattern("HHmmss"));
        log.debug("현재 시각 : {}", transmissionTime);

        String institutionTransactionUniqueNo = generateInstitutionTransactionUniqueNo(now);

        return SsafyApiHeaderDto.createCommonHeaderDto(
                transmissionDate,
                transmissionTime,
                institutionTransactionUniqueNo,
                apiKey,
                userKey,
                apiName

        );
    }

    public SsafyApiHeaderDto createCommonHeaderWithoutUserKey(String apiName) {
        LocalDateTime now = LocalDateTime.now();
        String transmissionDate = now.format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String transmissionTime = now.format(DateTimeFormatter.ofPattern("HHmmss"));
        log.debug("현재 시각 : {}", transmissionTime);

        String institutionTransactionUniqueNo = generateInstitutionTransactionUniqueNo(now);

        return SsafyApiHeaderDto.createCommonHeaderWithoutUserKeyDto(
                transmissionDate,
                transmissionTime,
                institutionTransactionUniqueNo,
                apiKey,
                apiName
        );
    }

    /**
     * 카드 조회용 API 헤더 생성
     */
    private SsafyApiHeaderDto createCardInquiryHeader(String userKey) {
        LocalDateTime now = LocalDateTime.now();
        String transmissionDate = now.format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String transmissionTime = now.format(DateTimeFormatter.ofPattern("HHmmss"));
        String institutionTransactionUniqueNo = generateInstitutionTransactionUniqueNo(now);

        return SsafyApiHeaderDto.createCardInquiryHeader(
                transmissionDate,
                transmissionTime,
                institutionTransactionUniqueNo,
                apiKey,
                userKey
        );
    }

    /**
     * 카드 조회 API 호출
     */
    public SsafyCardInquiryResponseDto inquireCreditCardList(String userKey) {
        // API 헤더 생성
        SsafyApiHeaderDto header = createCardInquiryHeader(userKey);

        // 요청 DTO 생성
        SsafyCardInquiryRequestDto requestDto = SsafyCardInquiryRequestDto.create(header);

        // HTTP 요청 생성
        HttpEntity<SsafyCardInquiryRequestDto> requestEntity = createHttpEntity(requestDto);

        // 디버그: 실제 JSON 요청 로깅
        try {
            com.fasterxml.jackson.databind.ObjectMapper objectMapper = new com.fasterxml.jackson.databind.ObjectMapper();
            String jsonRequest = objectMapper.writeValueAsString(requestDto);
            log.info("=== 실제 전송되는 JSON ===");
            log.info("{}", jsonRequest);
        } catch (Exception e) {
            log.warn("JSON 변환 실패", e);
        }

        // 디버그: 요청 데이터 로깅
        log.info("=== 카드 조회 API 요청 데이터 ===");
        log.info("userKey: {}", userKey);
        log.info("apiName: {}", header.getApiName());
        log.info("transmissionDate: {}", header.getTransmissionDate());
        log.info("transmissionTime: {}", header.getTransmissionTime());
        log.info("institutionTransactionUniqueNo: {}", header.getInstitutionTransactionUniqueNo());
        log.info("apiKey: {}", header.getApiKey());
        log.info("userKey in header: {}", header.getUserKey());
        log.info("institutionCode: {}", header.getInstitutionCode());
        log.info("fintechAppNo: {}", header.getFintechAppNo());
        log.info("apiServiceCode: {}", header.getApiServiceCode());
        log.info("URL: {}", baseUrl + "/ssafy/api/v1/edu/creditCard/inquireSignUpCreditCardList");

        // API 호출
        String url = baseUrl + "/ssafy/api/v1/edu/creditCard/inquireSignUpCreditCardList";

        ResponseEntity<SsafyCardInquiryResponseDto> response;
        try {
            response = restTemplate.postForEntity(url, requestEntity, SsafyCardInquiryResponseDto.class);
        } catch (Exception e) {
            log.error("카드 조회 API 통신 오류", e);
            throw new CustomException(ErrorCode.EXTERNAL_API_ERROR);
        }

        SsafyCardInquiryResponseDto responseDto = response.getBody();

        if (responseDto == null || !responseDto.isSuccess()) {
            String errorMessage = (responseDto != null && responseDto.getHeader() != null)
                    ? responseDto.getHeader().getResponseMessage()
                    : "응답 없음";
            log.error("카드 조회 실패 - {}", errorMessage);
            throw new CustomException(ErrorCode.EXTERNAL_API_ERROR);
        }

        log.info("카드 조회 성공 - 카드 수: {}",
                responseDto.getREC() != null ? responseDto.getREC().size() : 0);
        return responseDto;
    }
}